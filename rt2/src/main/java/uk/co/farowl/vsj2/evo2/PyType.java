package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

/** The Python {@code type} object. */
class PyType implements PyObject {

    /** Holds each type as it is defined. (Not used in this version.) */
    static final TypeRegistry TYPE_REGISTRY = new TypeRegistry();

    static final PyType TYPE = new PyType("type", PyType.class);
    @Override
    public PyType getType() { return TYPE; }
    final String name;
    private final Class<? extends PyObject> implClass;

    // Method suites for standard abstract types.
    final NumberMethods number;
    final SequenceMethods sequence;

    // Methods to implement standard operations.
    MethodHandle hash;
    MethodHandle repr;
    MethodHandle str;

    PyType(String name, Class<? extends PyObject> implClass) {
        this.name = name;
        this.implClass = implClass;

        // Initialise slots to implement standard operations.
        hash = Slot.TP.hash.findInClass(implClass);
        repr = Slot.TP.repr.findInClass(implClass);
        str = Slot.TP.str.findInClass(implClass);

        // If immutable, could use NumberMethods.EMPTY, etc.
        (number = new NumberMethods()).fillFromClass(implClass);
        (sequence = new SequenceMethods()).fillFromClass(implClass);

        TYPE_REGISTRY.put(name, this);
    }

    @Override
    public String toString() { return "<class '" + name + "'>"; }

    public String getName() { return name; }

    void setSlot(Slot.Any slot, MethodHandle mh) {
        slot.setSlot(this, mh);
    }

    /** True iff b is a sub-type (on the MRO of) this type. */
    boolean isSubTypeOf(PyType b) {
        /*
         * Not supported yet. Later, search the MRO (or base-chain) of this
         * for b, and if it is found, then this is a sub-type.
         */
        return false;
    }

    /** Holds each type as it is defined. (Not used in this version.) */
    static class TypeRegistry {
        private static Map<String, PyType> registry = new HashMap<>();
        void put(String name, PyType type) { registry.put(name, type); }
    }

    /** Tabulate the number methods (slots) of a particular type. */
    static class NumberMethods {

        MethodHandle negative = Slot.UNARY_EMPTY;
        MethodHandle add = Slot.BINARY_EMPTY;
        MethodHandle subtract = Slot.BINARY_EMPTY;
        MethodHandle multiply = Slot.BINARY_EMPTY;

        /** An instance in which every slot has its default value. */
        static final NumberMethods EMPTY = new NumberMethods();

        /**
         * Fill the slots in this {@code NumberMethods} with entries that
         * are method handles to the correspondingly named static methods
         * in a given target class, or if no such method is defined by the
         * class, leave the slot as it is (normally the default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (Slot.NB s : Slot.NB.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.signature.empty) { s.setSlot(this, mh); }
            }
        }
    }

    /** Tabulate the sequence methods (slots) of a particular type. */
    static class SequenceMethods {

        MethodHandle length = Slot.UNARY_EMPTY;

        /** An instance in which every slot has its default value. */
        static final SequenceMethods EMPTY = new SequenceMethods();

        /**
         * Fill the slots in this {@code NumberMethods} with entries that
         * are method handles to the correspondingly named static methods
         * in a given target class, or if no such method is defined by the
         * class, leave the slot as it is (normally the default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (Slot.SQ s : Slot.SQ.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.signature.empty) { s.setSlot(this, mh); }
            }
        }
    }


}
