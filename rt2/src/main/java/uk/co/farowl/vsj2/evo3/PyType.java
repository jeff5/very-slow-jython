package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** The Python {@code type} object. */
class PyType implements PyObject {

    /** Holds each type as it is defined. (Not used in this version.) */
    static final TypeRegistry TYPE_REGISTRY = new TypeRegistry();

    // *** The order of these initialisations is critical
    static final PyType[] EMPTY_TYPE_ARRAY = new PyType[0];
    static final PyType OBJECT_TYPE = new PyType("object",
            PyBaseObject.class, null, Spec.DEFAULT_FLAGS);
    private static final PyType[] ONLY_OBJECT =
            new PyType[] {OBJECT_TYPE};
    static final PyType TYPE =
            PyType.fromSpec(new Spec("type", PyType.class));
    // *** End critical ordered section

    @Override
    public PyType getType() { return TYPE; }
    final String name;
    private final Class<? extends PyObject> implClass;
    EnumSet<Flag> flags;

    // Support for class hierarchy
    private PyType base;
    private PyType[] bases;
    private PyType[] mro;

    // Standard type slots table see CPython PyTypeObject

    MethodHandle tp_vectorcall;
    MethodHandle tp_repr;
    MethodHandle tp_hash;
    MethodHandle tp_call;
    MethodHandle tp_str;
    MethodHandle tp_richcompare;
    MethodHandle tp_iter;

    // Number slots table see CPython PyNumberMethods

    MethodHandle nb_negative;
    MethodHandle nb_absolute;
    MethodHandle nb_add;
    MethodHandle nb_subtract;
    MethodHandle nb_multiply;
    MethodHandle nb_and;
    MethodHandle nb_or;
    MethodHandle nb_xor;

    MethodHandle nb_bool;

    MethodHandle nb_index;

    // Sequence slots table see CPython PySequenceMethods

    MethodHandle sq_length;
    // MethodHandle concat;
    MethodHandle sq_repeat;
    MethodHandle sq_item;
    MethodHandle sq_ass_item;
    // MethodHandle contains;

    // MethodHandle inplace_concat;
    // MethodHandle inplace_repeat;

    // Mapping slots table see CPython PyMappingMethods

    MethodHandle mp_length;
    MethodHandle mp_subscript;
    MethodHandle mp_ass_subscript;

    /** Construct a type with given name and {@code object} as base. */
    PyType(String name, Class<? extends PyObject> implClass) {
        this(name, implClass, EMPTY_TYPE_ARRAY, Spec.DEFAULT_FLAGS);
    }

    /** Construct a type {@code object} exactly. */
    static PyType forObject() {
        return new PyType("object", PyBaseObject.class, null,
                Spec.DEFAULT_FLAGS);
    }

    /** Construct a type from the given specification. */
    static PyType fromSpec(Spec spec) {
        return new PyType(spec.name, spec.implClass, spec.getBases(),
                spec.flags);
    }

    /**
     * Construct a type object with given name, provided the
     * {@code *Methods} and other values in a long-form constructor.
     * This constructor is a helper to factory methods.
     */
    private PyType(String name, Class<? extends PyObject> implClass,
            PyType[] declaredBases, EnumSet<Flag> flags) {
        this.name = name;
        this.implClass = implClass;
        this.flags = flags;

        // Fix-up base and MRO from bases array
        setMROfromBases(declaredBases);

        // Fill slots from implClass or bases
        setAllSlots();
    }

    /** Set the MRO, but at present only single base. */// XXX note may retain a reference to declaredBases
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

    /** True iff b is a sub-type (on the MRO of) this type. */
    boolean isSubTypeOf(PyType b) {
        // Only crudely supported. Later, search the MRO of this for b.
        // Awaits PyType.forClass() factory method.
        PyType t = this;
        while (t != b) {
            t = t.base;
            if (t == null) { return false; }
        }
        return true;
    }

    boolean isMutable() { return flags.contains(Flag.MUTABLE); }

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

        @Override
        public String toString() {
            String fmt = "%s %s, flags=%s impl=%s";
            return String.format(fmt, name, bases, flags,
                    implClass.getSimpleName());
        }

    }

}
