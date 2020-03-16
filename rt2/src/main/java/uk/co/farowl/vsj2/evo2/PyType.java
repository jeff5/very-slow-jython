package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

/** The Python {@code type} object. */
class PyType implements PyObject {

    /** Holds each type as it is defined. (Not used in this version.) */
    static final TypeRegistry TYPE_REGISTRY = new TypeRegistry();

    static final PyType TYPE = new PyType("type", PyType.class);
    static final PyType OBJECT_TYPE =
            new PyType("object", PyBaseObject.class);

    @Override
    public PyType getType() { return TYPE; }
    final String name;
    private final Class<? extends PyObject> implClass;

    // Support for class hierarchy
    private PyType base = OBJECT_TYPE;

    // Method suites for standard abstract types.
    final NumberMethods number;
    final SequenceMethods sequence;
    final MappingMethods mapping;

    // Methods to implement standard operations.
    MethodHandle hash;
    MethodHandle repr;
    MethodHandle str;
    MethodHandle richcompare;

    /** Construct a type object with given name and implementation. */
    PyType(String name, Class<? extends PyObject> impl) {
        this.name = name;
        this.implClass = impl;

        // Initialise slots to implement standard operations.
        hash = Slot.TP.hash.findInClass(implClass);
        repr = Slot.TP.repr.findInClass(implClass);
        str = Slot.TP.str.findInClass(implClass);
        richcompare = Slot.TP.richcompare.findInClass(implClass);

        // If immutable, could use NumberMethods.EMPTY, etc.
        (number = new NumberMethods()).fillFromClass(implClass);
        (sequence = new SequenceMethods()).fillFromClass(implClass);
        (mapping = new MappingMethods()).fillFromClass(implClass);

        TYPE_REGISTRY.put(name, this);
    }

    /**
     * Construct a type object with given name, base and implementation.
     */
    PyType(String name, PyType base,
            Class<? extends PyObject> implClass) {
        this(name, implClass);
        this.base = base;
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

    /** Holds each type as it is defined. (Not used in this version.) */
    static class TypeRegistry {

        private static Map<String, PyType> registry = new HashMap<>();

        void put(String name, PyType type) { registry.put(name, type); }
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
         * Fill the slots in this {@code NumberMethods} with entries
         * that are method handles to the correspondingly named static
         * methods in a given target class, or if no such method is
         * defined by the class, leave the slot as it is (normally the
         * default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (Slot.NB s : Slot.NB.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) { s.setSlot(this, mh); }
            }
        }
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
         * Fill the slots in this {@code SequenceMethods} with entries
         * that are method handles to the correspondingly named static
         * methods in a given target class, or if no such method is
         * defined by the class, leave the slot as it is (normally the
         * default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (Slot.SQ s : Slot.SQ.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) { s.setSlot(this, mh); }
            }
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
         * Fill the slots in this {@code MappingMethods} with entries
         * that are method handles to the correspondingly named static
         * methods in a given target class, or if no such method is
         * defined by the class, leave the slot as it is (normally the
         * default).
         *
         * @param c class to reflect
         */
        void fillFromClass(Class<? extends PyObject> c) {
            assert this != EMPTY;
            for (Slot.MP s : Slot.MP.values()) {
                MethodHandle mh = s.findInClass(c);
                if (mh != s.empty) { s.setSlot(this, mh); }
            }
        }
    }

}
