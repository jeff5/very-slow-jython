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

    // Method suites for standard abstract types.
    final NumberMethods number;
    final SequenceMethods sequence;
    final MappingMethods mapping;

    // Methods to implement standard operations.
    MethodHandle hash;
    MethodHandle repr;
    MethodHandle str;
    MethodHandle richcompare;

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
            PyType[] bases, EnumSet<Flag> flags) {
        this.name = name;
        this.implClass = implClass;
        this.flags = flags;

        // Fix-up base and MRO from bases array
        setMROfromBases(bases);

        // Initialise slots to implement standard operations.
        hash = Slot.TP.hash.findInClass(implClass);
        repr = Slot.TP.repr.findInClass(implClass);
        str = Slot.TP.str.findInClass(implClass);
        richcompare = Slot.TP.richcompare.findInClass(implClass);

        // If immutable, could use NumberMethods.EMPTY, etc.
        number = NumberMethods.forType(this);
        sequence = SequenceMethods.forType(this);
        mapping = MappingMethods.forType(this);
    }

    /** Set the MRO, but at present only single base. */
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

    @Override
    public String toString() { return "<class '" + name + "'>"; }

    public String getName() { return name; }

    void setSlot(Slot.Any slot, MethodHandle mh) {
        slot.setSlot(this, mh);
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
            if (bases.isEmpty())
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

    /** Tabulate the number methods (slots) of a particular type. */
    static class NumberMethods {

        MethodHandle negative = Slot.NB.negative.empty;
        MethodHandle add = Slot.NB.add.empty;
        MethodHandle subtract = Slot.NB.subtract.empty;
        MethodHandle multiply = Slot.NB.multiply.empty;

        MethodHandle bool = Slot.NB.bool.empty;

        MethodHandle index = Slot.NB.index.empty;

        /** An instance in which every slot has its default value. */
        static final NumberMethods EMPTY = new NumberMethods();

        /**
         * Return a {@code NumberMethods} with entries that are method
         * handles to the correspondingly named static methods in a
         * given target class. If no such methods are is defined by the
         * class, return {@link #EMPTY}.
         *
         * @param c class to reflect
         * @return a new {@code NumberMethods} or {@link #EMPTY}
         */
        static NumberMethods fromClass(Class<? extends PyObject> c) {
            NumberMethods methods = null;
            for (Slot.NB s : Slot.NB.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) {
                    // This slot is defined by class c
                    if (methods == null) {
                        methods = new NumberMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined, re-use EMPTY
            return methods == null ? EMPTY : methods;
        }

        static NumberMethods fromClass(Class<? extends PyObject> c,
                PyType base) {
            NumberMethods methods = null;
            for (Slot.NB s : Slot.NB.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh == s.empty) { mh = s.getSlot(base); }
                if (mh != s.empty) {
                    // This slot is defined by class c
                    if (methods == null) {
                        methods = new NumberMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined, re-use EMPTY
            return methods == null ? EMPTY : methods;
        }

        /**
         * Return a {@code NumberMethods} with entries that are method
         * handles to the correspondingly named static methods in the
         * implementation class of the given type, or inherited from any
         * base. If no such methods are is defined for the type, return
         * {@link #EMPTY}.
         *
         * @param t type to reflect
         * @return a new {@code NumberMethods} or {@link #EMPTY}
         */
        static NumberMethods forType(PyType t) {
            NumberMethods methods =
                    t.isMutable() ? new NumberMethods() : null;
            for (Slot.NB s : Slot.NB.values()) {
                MethodHandle mh = t.findSlotInBases(s);
                if (mh != s.empty) {
                    // This slot is defined by t.implClass or a base
                    if (methods == null) {
                        methods = new NumberMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined (and immutable), re-use EMPTY
            return methods == null ? EMPTY : methods;
        }
    }

    /**
     * Return the {@code MethodHandle} for the slot {@code s}, looking
     * in the implementation class for the corresponding method and in
     * the slot tables of the bases.
     *
     * @param s slot to interrogate
     * @return method handle found or {@code s.getEmpty()}
     */
    private MethodHandle findSlotInBases(Slot.Any s) {
        final MethodHandle empty = s.getEmpty();
        MethodHandle mh = s.findInClass(implClass);
        for (int i = 0; mh == empty && i < bases.length; i++) {
            mh = s.getSlot(bases[i]);
        }
        return mh;
    }

    /** Tabulate the sequence methods (slots) of a particular type. */
    static class SequenceMethods {

        MethodHandle length = Slot.SQ.length.empty;
        // MethodHandle concat = Slot.SQ.concat.empty;
        MethodHandle repeat = Slot.SQ.repeat.empty;
        MethodHandle item = Slot.SQ.item.empty;
        MethodHandle ass_item = Slot.SQ.ass_item.empty;
        // MethodHandle contains = Slot.SQ.contains.empty;

        // MethodHandle inplace_concat = Slot.SQ.inplace_concat.empty;
        // MethodHandle inplace_repeat = Slot.SQ.inplace_repeat.empty;

        /** An instance in which every slot has its default value. */
        static final SequenceMethods EMPTY = new SequenceMethods();

        /**
         * Return a {@code SequenceMethods} with entries that are method
         * handles to the correspondingly named static methods in a
         * given target class. If no such methods are is defined by the
         * class, return {@link #EMPTY}.
         *
         * @return a new {@code SequenceMethods} or {@link #EMPTY}
         * @param c class to reflect
         */
        static SequenceMethods fromClass(Class<? extends PyObject> c) {
            SequenceMethods methods = null;
            for (Slot.SQ s : Slot.SQ.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) {
                    // This slot is defined by class c
                    if (methods == null) {
                        methods = new SequenceMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined, re-use EMPTY
            return methods == null ? EMPTY : methods;
        }

        /**
         * Return a {@code SequenceMethods} with entries that are method
         * handles to the correspondingly named static methods in the
         * implementation class of the given type, or inherited from any
         * base. If no such methods are is defined for the type, return
         * {@link #EMPTY}.
         *
         * @param t type to reflect
         * @return a new {@code SequenceMethods} or {@link #EMPTY}
         */
        static SequenceMethods forType(PyType t) {
            SequenceMethods methods =
                    t.isMutable() ? new SequenceMethods() : null;
            for (Slot.SQ s : Slot.SQ.values()) {
                MethodHandle mh = t.findSlotInBases(s);
                if (mh != s.empty) {
                    // This slot is defined by t.implClass or a base
                    if (methods == null) {
                        methods = new SequenceMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined (and immutable), re-use EMPTY
            return methods == null ? EMPTY : methods;
        }
    }

    /** Tabulate the sequence methods (slots) of a particular type. */
    static class MappingMethods {

        MethodHandle length = Slot.MP.length.empty;
        MethodHandle subscript = Slot.MP.subscript.empty;
        MethodHandle ass_subscript = Slot.MP.ass_subscript.empty;

        /** An instance in which every slot has its default value. */
        static final MappingMethods EMPTY = new MappingMethods();

        /**
         * Return a {@code MappingMethods} with entries that are method
         * handles to the correspondingly named static methods in a
         * given target class. If no such methods are is defined by the
         * class, return {@link #EMPTY}.
         *
         * @param c class to reflect
         * @return a new {@code MappingMethods} or {@link #EMPTY}
         */
        static MappingMethods fromClass(Class<? extends PyObject> c) {
            MappingMethods methods = null;
            for (Slot.MP s : Slot.MP.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) {
                    // This slot is defined by class c
                    if (methods == null) {
                        methods = new MappingMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined, re-use EMPTY
            return methods == null ? EMPTY : methods;
        }

        /**
         * Return a {@code MappingMethods} with entries that are method
         * handles to the correspondingly named static methods in the
         * implementation class of the given type, or inherited from any
         * base. If no such methods are is defined for the type, return
         * {@link #EMPTY}.
         *
         * @param t type to reflect
         * @return a new {@code MappingMethods} or {@link #EMPTY}
         */
        static MappingMethods forType(PyType t) {
            MappingMethods methods =
                    t.isMutable() ? new MappingMethods() : null;
            for (Slot.MP s : Slot.MP.values()) {
                MethodHandle mh = t.findSlotInBases(s);
                if (mh != s.empty) {
                    // This slot is defined by t.implClass or a base
                    if (methods == null) {
                        methods = new MappingMethods();
                    }
                    s.setSlot(methods, mh);
                }
            }
            // If no slots defined (and immutable), re-use EMPTY
            return methods == null ? EMPTY : methods;
        }
    }

}
