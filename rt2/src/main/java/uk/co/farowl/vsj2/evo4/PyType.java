package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;
import uk.co.farowl.vsj2.evo4.Slot.Signature;

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

    MethodHandle op_repr;
    MethodHandle op_hash;
    MethodHandle op_call;
    MethodHandle op_str;

    MethodHandle op_getattribute;
    MethodHandle op_getattr;
    MethodHandle op_setattr;
    MethodHandle op_delattr;

    MethodHandle tp_richcompare;

    MethodHandle op_iter;

    MethodHandle op_get;
    MethodHandle op_set;
    MethodHandle op_delete;

    MethodHandle op_init;
    MethodHandle op_new;

    MethodHandle op_vectorcall;

    // Number slots table see CPython PyNumberMethods

    MethodHandle op_add;
    MethodHandle op_radd;
    MethodHandle op_sub;
    MethodHandle op_rsub;
    MethodHandle op_mul;
    MethodHandle op_rmul;

    MethodHandle op_neg;

    MethodHandle op_abs;
    MethodHandle op_bool;

    MethodHandle op_and;
    MethodHandle op_rand;
    MethodHandle op_xor;
    MethodHandle op_rxor;
    MethodHandle op_or;
    MethodHandle op_ror;

    MethodHandle op_int;
    MethodHandle op_float;

    MethodHandle op_index;

    // Sequence slots table see CPython PySequenceMethods
    // Mapping slots table see CPython PyMappingMethods

    MethodHandle op_len;
    MethodHandle sq_item;
    MethodHandle sq_ass_item;
    // MethodHandle sq_contains;

    MethodHandle op_getitem;
    MethodHandle op_setitem;
    MethodHandle op_delitem;

    /** Construct a type with given name and {@code object} as base. */
    PyType(String name, Class<? extends PyObject> implClass) {
        this(name, implClass, EMPTY_TYPE_ARRAY, Spec.DEFAULT_FLAGS);
    }

    /** Construct a type from the given specification. */
    static PyType fromSpec(Spec spec) {
        PyType type = new PyType(spec.name, spec.implClass,
                spec.getBases(), spec.flags);
        type.processNamespace(spec.namespace);
        return type;
    }

    /** Copy a name space into the dictionary of this type. */
    private void processNamespace(Map<PyObject, PyObject> namespace) {
        for (Map.Entry<PyObject, PyObject> e : namespace.entrySet()) {
            PyObject key = e.getKey();
            if (key instanceof PyUnicode) {
                dict.put((PyUnicode) key, e.getValue());
            }
        }
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

    /** name has the form __A__ where A is one or more characters. */
    private static boolean isDunderName(PyUnicode name) {
        String n = name.value;
        final int L = n.length();
        return L > 4 && n.charAt(1) == '_' && n.charAt(0) == '_'
                && n.charAt(L - 2) == '_' && n.charAt(L - 1) == '_';
    }

    /**
     * Called from {@link #__setattr__(PyType, PyUnicode, PyObject)}
     * after an attribute has been set or deleted. This gives the type
     * the opportunity to recompute slots and perform any other actions.
     *
     * @param name of the attribute modified
     */
    protected void updateAfterSetAttr(PyUnicode name) {

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

    /**
     * {@inheritDoc}
     * <p>
     * The dictionary of a {@code type} always exists, but is presented
     * externally only in read-only form.
     */
    @Override
    public Map<PyObject, PyObject> getDict(boolean create) {
        return Collections.unmodifiableMap(dict);
    }

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
        Class<? extends PyObject> implClass;
        private final List<PyType> bases = new LinkedList<>();
        Map<PyObject, PyObject> namespace = Collections.emptyMap();
        EnumSet<Flag> flags = EnumSet.copyOf(DEFAULT_FLAGS);

        /**
         * Create (begin) a specification for a {@link PyType} based on
         * a specific implementation class. This is the beginning
         * normally made by built-in classes.
         */
        Spec(String name, Class<? extends PyObject> implClass) {
            this.name = name;
            this.implClass = implClass;
        }

        /**
         * Create (begin) a specification for a {@link PyType} deferring
         * the choice of implementation class. This is the beginning
         * made by {@code type.__new__}.
         */
        Spec(String name) {
            this.name = name;
            this.implClass = PyBaseObject.class;
        }

        /** Specify a base for the type. */
        Spec base(PyType b) {
            bases.add(b);
            return this;
        }

        /**
         * Specify a name space for the type. Normally this is the
         * result of processing the body of a class declaration. It is
         * copied into the type dictionary during class creation. Until
         * then, it is ok to update it.
         */
        Spec namespace(Map<PyObject, PyObject> ns) {
            namespace = ns;
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
            MethodHandle n = type.op_new;
            PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
            // Check for special case type enquiry: yes afterwards!
            // (PyType.__new__ performs both functions.)
            if (isTypeEnquiry(type, args, kwargs)) { return o; }
            // As __new__ may be user-defined, check type as expected.
            PyType oType = o.getType();
            if (oType.isSubTypeOf(type)) {
                // Initialise the object just returned (if necessary).
                if (Slot.op_init.isDefinedFor(oType))
                    oType.op_init.invokeExact(o, args, kwargs);
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
        if (isTypeEnquiry(metatype, args, kwds)) {
            return args.get(0).getType();
        }

        // Type creation call
        PyObject oBases, oName, oNamespace;

        if (args.size() != 3) {
            throw new TypeError("type() takes 1 or 3 arguments");
        } else if (!PyUnicode.TYPE.check(oName = args.get(0))) {
            throw new TypeError(NEW_ARG_MUST_BE, 0, PyUnicode.TYPE,
                    oName.getType());
        } else if (!PyTuple.TYPE.check(oBases = args.get(1))) {
            throw new TypeError(NEW_ARG_MUST_BE, 1, PyTuple.TYPE.name,
                    oBases.getType());
        } else if (!PyDict.TYPE.check(oNamespace = args.get(2))) {
            throw new TypeError(NEW_ARG_MUST_BE, 2, PyDict.TYPE.name,
                    oNamespace.getType());
        }

        String name = oName.toString();
        PyObject[] bases = ((PyTuple) oBases).value;
        PyDict namespace = (PyDict) oNamespace;

        // Specify using provided material
        Spec spec =
                new Spec(name).namespace(namespace).flag(Flag.MUTABLE);

        for (PyObject t : bases) {
            if (t instanceof PyType)
                spec.base((PyType) t);
        }

        return PyType.fromSpec(spec);
    }

    private static final String NEW_ARG_MUST_BE =
            "type.__new__() argument %d must be %s, not %s";

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

    /**
     * {@link Slot#op_getattribute} has signature
     * {@link Signature#GETATTR} and provides attribute read access on
     * this type object and its metatype. This is very like
     * {@code object.__getattribute__}
     * ({@link PyBaseObject#__getattribute__(PyObject, PyUnicode)}), but
     * the instance is replaced by a type object, and that object's type
     * is a meta-type (which is also a {@code type}).
     * <p>
     * The behavioural difference is that in looking for attributes on a
     * type:
     * <ul>
     * <li>we use {@link #lookup(PyUnicode)} to search along along the
     * MRO, and</li>
     * <li>if we find a descriptor, we use it.
     * ({@code object.__getattribute__} does not check for descriptors
     * on the instance.)</li>
     * </ul>
     * <p>
     * The following order of precedence applies when looking for the
     * value of an attribute:
     * <ol>
     * <li>a data descriptor from the dictionary of the meta-type</li>
     * <li>a descriptor or value in the dictionary of {@code type}</li>
     * <li>a non-data descriptor or value from dictionary of the meta
     * type</li>
     * </ol>
     *
     * @param type the target of the get
     * @param name of the attribute
     * @return attribute value
     * @throws AttributeError if no such attribute
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_getattro in typeobject.c
    static PyObject __getattribute__(PyType type, PyUnicode name)
            throws AttributeError, Throwable {

        PyType metatype = type.getType();
        MethodHandle descrGet = null;

        // Look up the name in the type (null if not found).
        PyObject metaAttr = metatype.lookup(name);
        if (metaAttr != null) {
            // Found in the metatype, it might be a descriptor
            PyType metaAttrType = metaAttr.getType();
            descrGet = metaAttrType.op_get;
            if (metaAttrType.isDataDescr()) {
                // metaAttr is a data descriptor so call its __get__.
                try {
                    return (PyObject) descrGet.invokeExact(metaAttr,
                            type, metatype);
                } catch (AttributeError | Slot.EmptyException e) {
                    /*
                     * Not found via descriptor: fall through to try the
                     * instance dictionary, but not the descriptor
                     * again.
                     */
                    descrGet = null;
                }
            }
        }

        /*
         * At this stage: metaAttr is the value from the meta-type, or a
         * non-data descriptor, or null if the attribute was not found.
         * It's time to give the type's instance dictionary a chance.
         */
        PyObject attr = type.lookup(name);
        if (attr != null) {
            // Found in this type. Try it as a descriptor.
            try {
                /*
                 * Note the args are (null, this): we respect
                 * descriptors in this step, but have not forgotten we
                 * are dereferencing a type.
                 */
                return (PyObject) attr.getType().op_get
                        .invokeExact(attr, (PyObject) null, type);
            } catch (Slot.EmptyException e) {
                // We do not catch AttributeError: it's definitive.
                // Not a descriptor: the attribute itself.
                return attr;
            }
        }

        /*
         * The name wasn't in the type dictionary. We are now left with
         * the results of look-up on the meta-type.
         */
        if (descrGet != null) {
            // metaAttr may be a non-data descriptor: call __get__.
            try {
                return (PyObject) descrGet.invokeExact(metaAttr, type,
                        metatype);
            } catch (Slot.EmptyException e) {}
        }

        if (metaAttr != null) {
            // The attribute obtained from the meta-type is the value.
            return metaAttr;
        }

        // All the look-ups and descriptors came to nothing :(
        throw Abstract.noAttributeError(type, name);
    }

    /**
     * {@link Slot#op_setattr} has signature {@link Signature#SETATTR}
     * and provides attribute write access on this type object. The
     * behaviour is very like the default {@code object.__setattr__}
     * except that it has write access to the type dictionary that is
     * denied through {@link #getDict(boolean)}.
     *
     * @param type the target of the set
     * @param name of the attribute
     * @param value to give the attribute
     * @throws AttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    static void __setattr__(PyType type, PyUnicode name, PyObject value)
            throws AttributeError, Throwable {

        // Accommodate CPython idiom that set null means delete.
        if (value == null) {
            // Do this to help porting. Really this is an error.
            __delattr__(type, name);
            return;
        }

        // Trap immutable types
        if (!type.flags.contains(Flag.MUTABLE))
            throw Abstract.cantSetAttributeError(type);

        // Force name to actual str , not just a sub-class
        if (name.getClass() != PyUnicode.class) {
            name = Py.str(name.toString());
        }

        // Check to see if this is a special name
        boolean special = isDunderName(name);

        // Look up the name in the meta-type (null if not found).
        PyObject metaAttr = type.getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            PyType metaAttrType = metaAttr.getType();
            if (metaAttrType.isDataDescr()) {
                // Try descriptor __set__
                try {
                    metaAttrType.op_set.invokeExact(metaAttr, type,
                            value);
                    if (special) { type.updateAfterSetAttr(name); }
                    return;
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Descriptor but no __set__: do not fall through.
                    throw Abstract.readonlyAttributeError(type, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will place the value in
         * the object instance dictionary directly.
         */
        // Use the privileged put
        type.dict.put(name, value);
        if (special) { type.updateAfterSetAttr(name); }
    }

    /**
     * {@link Slot#op_delattr} has signature {@link Signature#DELATTR}
     * and provides attribute deletion on this type object. The
     * behaviour is very like the default {@code object.__delattr__}
     * except that it has write access to the type dictionary that is
     * denied through {@link #getDict(boolean)}.
     *
     * @param type the target of the set
     * @param name of the attribute
     * @throws AttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    static void __delattr__(PyType type, PyUnicode name)
            throws AttributeError, Throwable {

        // Trap immutable types
        if (!type.flags.contains(Flag.MUTABLE))
            throw Abstract.cantSetAttributeError(type);

        // Force name to actual str , not just a sub-class
        if (name.getClass() != PyUnicode.class) {
            name = Py.str(name.toString());
        }

        // Check to see if this is a special name
        boolean special = isDunderName(name);

        // Look up the name in the meta-type (null if not found).
        PyObject metaAttr = type.getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            PyType metaAttrType = metaAttr.getType();
            if (metaAttrType.isDataDescr()) {
                // Try descriptor __delete__
                try {
                    metaAttrType.op_delete.invokeExact(metaAttr, type);
                    if (special) { type.updateAfterSetAttr(name); }
                    return;
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Data descriptor but no __delete__.
                    throw Abstract.mandatoryAttributeError(type, name);
                }
            }
        }

        /*
         * There was no data descriptor, so it's time to give the type
         * instance dictionary a chance to receive. A type always has a
         * dictionary so type.dict can't be null.
         */
        // Use the privileged remove
        PyObject previous = type.dict.remove(name);
        if (previous == null) {
            // A null return implies it didn't exist
            throw Abstract.noAttributeError(type, name);
        }

        if (special) { type.updateAfterSetAttr(name); }
        return;
    }

    // plumbing --------------------------------------------------

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
