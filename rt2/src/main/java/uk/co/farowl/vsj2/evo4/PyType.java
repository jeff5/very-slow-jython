package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** The Python {@code type} object. */
class PyType implements PyObject {

    /** Holds each type as it is defined. (Not used in this version.) */
    static final TypeRegistry TYPE_REGISTRY = new TypeRegistry();

    // *** The order of these initialisations is critical
    static final PyType[] EMPTY_TYPE_ARRAY = new PyType[0];
    /** The type object of {@code type} objects. */
    static final PyType TYPE = new PyType();
    /** The type object of {@code object} objects. */
    static final PyType OBJECT_TYPE = TYPE.getBase();
    private static final PyType[] ONLY_OBJECT =
            new PyType[] {OBJECT_TYPE};
    // *** End critical ordered section

    private final PyType type;
    final String name;
    private final Class<? extends PyObject> implClass;
    EnumSet<Flag> flags;

    // Support for class hierarchy
    private PyType base;
    private PyType[] bases;
    private PyType[] mro;

    /**
     * The dictionary of the type is always an ordered {@code Map}. It
     * is only accessible (outside the core) through a
     * {@code mappingproxy} that renders it read-only.
     */
    private final Map<PyUnicode, PyObject> dict = new LinkedHashMap<>();

    // Standard type slots table see CPython PyType

    MethodHandle tp_repr;
    MethodHandle tp_hash;
    MethodHandle tp_call;
    MethodHandle tp_str;

    MethodHandle tp_getattribute;
    MethodHandle tp_getattro;
    MethodHandle tp_setattro;

    MethodHandle tp_richcompare;

    MethodHandle tp_iter;

    MethodHandle tp_descr_get;
    MethodHandle tp_descr_set;

    MethodHandle tp_init;
    MethodHandle tp_new;

    MethodHandle tp_vectorcall;

    // Number slots table see CPython PyNumberMethods

    MethodHandle nb_add;
    MethodHandle nb_radd;
    MethodHandle nb_sub;
    MethodHandle nb_rsub;
    MethodHandle nb_mul;
    MethodHandle nb_rmul;

    MethodHandle nb_negative;

    MethodHandle nb_absolute;
    MethodHandle nb_bool;

    MethodHandle nb_and;
    MethodHandle nb_rand;
    MethodHandle nb_xor;
    MethodHandle nb_rxor;
    MethodHandle nb_or;
    MethodHandle nb_ror;

    MethodHandle nb_int;
    MethodHandle nb_float;

    MethodHandle nb_index;

    // Sequence slots table see CPython PySequenceMethods

    MethodHandle sq_length;
    // MethodHandle concat;
    // MethodHandle sq_repeat;
    MethodHandle sq_item;
    MethodHandle sq_ass_item;
    // MethodHandle sq_contains;

    // MethodHandle inplace_concat;
    // MethodHandle inplace_repeat;

    // Mapping slots table see CPython PyMappingMethods

    // MethodHandle mp_length;
    MethodHandle mp_subscript;
    MethodHandle mp_ass_subscript;

    /** Construct a type with given name and {@code object} as base. */
    PyType(String name, Class<? extends PyObject> implClass) {
        this(name, implClass, EMPTY_TYPE_ARRAY, Spec.DEFAULT_FLAGS);
    }

    /** Construct a type from the given specification. */
    static PyType fromSpec(Spec spec) {
        return new PyType(spec.name, spec.implClass, spec.getBases(),
                spec.flags);
    }

    /**
     * Construct a {@code type} object with given sub-type and name,
     * This constructor is a helper to factory methods.
     *
     * @param metatype the sub-type of type we are constructing
     * @param name of that type (with the given metatype)
     * @param implClass implementation class of the type being defined
     * @param declaredBases of the type being defined
     * @param flags characteristics of the type being defined
     */
    private PyType(PyType metatype, String name,
            Class<? extends PyObject> implClass, PyType[] declaredBases,
            EnumSet<Flag> flags) {
        this.type = metatype;
        this.name = name;
        this.implClass = implClass;
        this.flags = flags;

        // Fix-up base and MRO from bases array
        setMROfromBases(declaredBases);

        // Fill slots from implClass or bases
        setAllSlots();
    }

    /**
     * Construct a {@code type} object with given name, provided other
     * values in a long-form constructor. This constructor is a helper
     * to factory methods.
     */
    private PyType(String name, Class<? extends PyObject> implClass,
            PyType[] declaredBases, EnumSet<Flag> flags) {
        this(TYPE, name, implClass, declaredBases, flags);
    }

    /**
     * Construct a {@code type} object for {@code type}, and by
     * side-effect the type object of its base {@code object}. The
     * special constructor solves the problem that each of these has to
     * exist in order properly to create the other. This constructor is
     * <b>only</b> used, once, during the static initialisation of
     * {@code PyType}, after which these objects are constants.
     */
    private PyType() {
        // We are creating the PyType for "type"
        this.type = this;
        this.name = "type";
        this.implClass = PyType.class;
        this.flags = Spec.DEFAULT_FLAGS;
        /*
         * Now we need the type object for "object", which references
         * this one as its type. Note that PyType.TYPE is not yet set
         * because we have not returned from this constructor.
         */
        PyType objectType = new PyType(this, "object",
                PyBaseObject.class, null, Spec.DEFAULT_FLAGS);

        // The only base of type is object
        setMROfromBases(new PyType[] {objectType});

        // Fill slots from implClass or bases
        setAllSlots();
    }

    @Override
    public PyType getType() { return type; }

    /** Set the MRO, but at present only single base. */
    // XXX note may retain a reference to declaredBases
    private void setMROfromBases(PyType[] declaredBases) {
        int n;

        if (declaredBases == null) {
            // Special case for object (or other ultimate type?)
            base = null;
            bases = EMPTY_TYPE_ARRAY;
            mro = new PyType[] {this};

        } else if ((n = declaredBases.length) == 0) {
            // Default base is object
            base = OBJECT_TYPE;
            bases = ONLY_OBJECT;
            mro = new PyType[] {this, OBJECT_TYPE};

        } else if (n == 1) {
            // Just one base: short-cut
            base = declaredBases[0];
            bases = declaredBases;
            // mro = (this,) + this.mro
            PyType[] baseMRO = base.getMRO();
            int m = baseMRO.length;
            PyType[] mro = new PyType[m + 1];
            mro[0] = this;
            System.arraycopy(baseMRO, 0, mro, 1, m);
            this.mro = mro;

        } else if (n > 1) {
            // Need the proper C3 algorithm to set MRO
            String fmt =
                    "multiple inheritance not supported yet (type `%s`)";
            throw new InterpreterError(fmt, name);
        }
    }

    /**
     * Set all the slots ({@code tp_*}, {@code nb_*}, etc.) from the
     * {@link #implClass} and {@link #bases}.
     */
    private void setAllSlots() {
        for (Slot s : Slot.values()) {
            final MethodHandle empty = s.getEmpty();
            MethodHandle mh = s.findInClass(implClass);
            for (int i = 0; mh == empty && i < bases.length; i++) {
                mh = s.getSlot(bases[i]);
            }
            s.setSlot(this, mh);
        }
    }

    @Override
    public String toString() { return "<class '" + name + "'>"; }

    public String getName() { return name; }

    void setSlot(Slot slot, MethodHandle mh) {
        if (isMutable())
            slot.setSlot(this, mh);
        else
            throw new TypeError("cannot update slots of %s", name);
    }

    /**
     * {@code true} iff the type of {@code o} is {@code this} or a
     * Python sub-type of {@code this}.
     */
    boolean check(PyObject o) {
        PyType t = o.getType();
        return t == this || t.isSubTypeOf(this);
    }

    /**
     * {@code true} iff the Python type of {@code o} is exactly
     * {@code this}, not a Python sub-type of {@code this}, nor just any
     * Java sub-class of {@code PyType}. {@code o} will also be
     * assignable in Java to the implementation class of this type.
     */
    // Multiple acceptable implementations would invalidate last stmt.
    boolean checkExact(PyObject o) {
        return o.getType() == TYPE;
    }

    /** True iff b is a sub-type (on the MRO of) this type. */
    // Compare CPython PyType_IsSubtype in typeobject.c
    boolean isSubTypeOf(PyType b) {
        if (mro != null) {
            /*
             * Deal with multiple inheritance without recursion by
             * walking the MRO tuple
             */
            for (PyType base : mro) {
                if (base == b)
                    return true;
            }
            return false;
        } else
            // a is not completely initilized yet; follow base
            return type_is_subtype_base_chain(b);
    }

    // Compare CPython type_is_subtype_base_chain in typeobject.c
    private boolean type_is_subtype_base_chain(PyType b) {
        // Only crudely supported. Later, search the MRO of this for b.
        // Awaits PyType.forClass() factory method.
        PyType t = this;
        while (t != b) {
            t = t.base;
            if (t == null) { return b == OBJECT_TYPE; }
        }
        return true;
    }

    boolean isMutable() { return flags.contains(Flag.MUTABLE); }

    boolean isDataDescr() {
        // XXX Base on the Trait & that on something in construction.
        return false;
    }

    boolean isNonDataDescr() {
        // XXX Base on the Trait & that on something in construction.
        return false;
    }

    /** Return the base (core use only). */
    PyType getBase() {
        return base;
    }

    /** Return the bases as an array (core use only). */
    PyType[] getBases() {
        return bases;
    }

    /** Return the MRO as an array (core use only). */
    PyType[] getMRO() {
        return mro;
    }

    // /** Return the dictionary of the type. */
    // @Override
    // PyObject.Mapping getDict() {
    // XXX // Need a wrapper that is a PyObject (mappingproxy)
    // return dict;
    // }

    /**
     * Look for a name, returning the entry directly from the first
     * dictionary along the MRO containing key {@code name}. This may be
     * a descriptor, but no {@code __get__} takes place on it: the
     * descriptor itself will be returned. This method does not throw an
     * exception if the name is not found, but returns {@code null} like
     * a {@code Map.get}
     *
     * @param name to look up, must be exactly a {@code str}
     * @return dictionary entry or null
     */
    // Compare CPython _PyType_Lookup in typeobject.c
    // and find_name_in_mro in typeobject.c
    PyObject lookup(PyUnicode name) {

        /*
         * CPython wraps this in a cache keyed by (type, name) and
         * sensitive to the "version" of this type. (Version changes
         * when any change occurs, even in a super-class, that would
         * alter the result of a look-up. We do not reproduce that at
         * present.
         */

        // Look in dictionaries of types in MRO
        PyType[] mro = getMRO();

        // CPython checks here to see in this type is "ready".
        // Could we be "not ready" in some loop of types?

        for (PyType base : mro) {
            PyObject res;
            if ((res = base.dict.get(name)) != null)
                return res;
        }
        return null;
    }



    /** Holds each type as it is defined. (Not used in this version.) */
    static class TypeRegistry {

        private static Map<String, PyType> registry = new HashMap<>();

        void put(String name, PyType type) { registry.put(name, type); }
    }

    enum Flag {
        /**
         * Special methods may be assigned new meanings in the
         * {@code type}, after creation.
         */
        MUTABLE,
        /**
         * An object with this type can change to another type (within
         * "layout" constraints).
         */
        REMOVABLE,
        /**
         * This type the type allows sub-classing (is acceptable as a
         * base).
         */
        BASETYPE
    }

    /** Specification for a type. A data structure with mutators. */
    static class Spec {

        final static EnumSet<Flag> DEFAULT_FLAGS =
                EnumSet.of(Flag.BASETYPE);

        final String name;
        final Class<? extends PyObject> implClass;
        final List<PyType> bases = new LinkedList<>();
        EnumSet<Flag> flags = EnumSet.copyOf(DEFAULT_FLAGS);

        /** Create (begin) a specification for a {@link PyType}. */
        Spec(String name, Class<? extends PyObject> implClass) {
            this.name = name;
            this.implClass = implClass;
        }

        /** Specify a base for the type. */
        Spec base(PyType b) {
            bases.add(b);
            return this;
        }

        /** Specify a characteristic (type flag) to be added. */
        Spec flag(Flag f) {
            flags.add(f);
            return this;
        }

        /** Specify a characteristic (type flag) to be removed. */
        Spec flagNot(Flag f) {
            flags.remove(f);
            return this;
        }

        /** Return the cumulative bases as an array. */
        PyType[] getBases() {
            if (bases.isEmpty()) // XXX return empty array (see uses)?
                return ONLY_OBJECT; // None specified: default
            else
                return bases.toArray(new PyType[bases.size()]);
        }

        // Something more helpful than the standard repr()
        @Override
        public String toString() {
            String fmt = "%s %s, flags=%s impl=%s";
            return String.format(fmt, name, bases, flags,
                    implClass.getSimpleName());
        }
    }

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyType self) throws Throwable {
        return PyUnicode.fromFormat("<class '%s'>", self.name);
    }

    /**
     * Handle calls to a type object, which will normally be for
     * construction of an object of that type, except for the special
     * case {@code type(obj)}, which enquires the Python type of the
     * object.
     *
     * @param type of which an instance is required.
     * @param args argument tuple (length 1 in a type enquiry).
     * @param kwargs keyword arguments (empty or {@code null} in a type
     *            enquiry).
     * @return new object (or a type if an enquiry).
     * @throws Throwable
     */
    static PyObject __call__(PyType type, PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {
        try {
            // Create the instance with given arguments.
            MethodHandle n = type.tp_new;
            PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
            // Check for special case type enquiry: yes afterwards!
            // (PyType.__new__ performs both functions.)
            if (isTypeEnquiry(type, args, kwargs)) { return o; }
            // As __new__ may be user-defined, check type as expected.
            PyType oType = o.getType();
            if (oType.isSubTypeOf(type)) {
                // Initialise the object just returned (if necessary).
                if (Slot.tp_init.isDefinedFor(oType))
                    oType.tp_init.invokeExact(o, args, kwargs);
            }
            return o;
        } catch (EmptyException e) {
            // type.tp_new is empty (not TYPE.tp_new)
            throw new TypeError("cannot create '%.100s' instances",
                    type.name);
        }
    }

    static PyObject __new__(PyType metatype, PyTuple args, PyDict kwds)
            throws Throwable {
        // Special case: type(x) should return type(x)
        int nargs = args.size();
        if (isTypeEnquiry(metatype, args, kwds)) {
            return args.get(0).getType();
        }

        if (nargs != 3) {
            throw new TypeError("type() takes 1 or 3 arguments");
        }

        // Type creation call
        throw new NotImplementedError("type creation");
    }

    /**
     * Helper for {@link #__call__} and {@link #__new__}. This is a type
     * enquiry if {@code type} is {@link PyType#TYPE} and there is just
     * one argument.
     */
    private static boolean isTypeEnquiry(PyType type, PyTuple args,
            PyDict kwargs) {
        return type == TYPE && args.size() == 1
                && (kwargs == null || kwargs.isEmpty());
    }

    // Compare CPython _PyType_GetDocFromInternalDoc
    // in typeobject.c
    static PyObject getDocFromInternalDoc(String name, String doc) {
        // TODO Auto-generated method stub
        return null;
    }

    // Compare CPython: PyType_GetTextSignatureFromInternalDoc
    // in typeobject.c
    static PyObject getTextSignatureFromInternalDoc(String name,
            String doc) {
        // TODO Auto-generated method stub
        return null;
    }
}
