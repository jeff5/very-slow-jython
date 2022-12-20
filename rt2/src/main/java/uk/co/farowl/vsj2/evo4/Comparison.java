package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/**
 * Selects a particular "rich comparison" operation from the repertoire
 * supported by {@link Opcode#COMPARE_OP}, the argument to which is the
 * {@code code} attribute of the name in this {@code enum}.
 *
 * @apiNote The order matches CPython's enumeration of operations used
 *          in the argument to {@code COMPARE_OP}, so that we can rely
 *          on it in the CPython byte code interpreter.
 */
enum Comparison {
    // Order and number must be reflected in swap[].

    /** The {@code __lt__} operation. */
    LT("<", Slot.op_lt) {

        @Override
        PyBool toBool(int c) { return c < 0 ? Py.True : Py.False; }
    },

    /** The {@code __le__} operation. */
    LE("<=", Slot.op_le) {

        @Override
        PyBool toBool(int c) { return c <= 0 ? Py.True : Py.False; }
    },

    /** The {@code __eq__} operation. */
    EQ("==", Slot.op_eq) {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }
    },

    /** The {@code __ne__} operation. */
    NE("!=", Slot.op_ne) {

        @Override
        PyBool toBool(int c) { return c != 0 ? Py.True : Py.False; }
    },

    /** The {@code __gt__} operation. */
    GT(">", Slot.op_gt) {

        @Override
        PyBool toBool(int c) { return c > 0 ? Py.True : Py.False; }
    },

    /** The {@code __ge__} operation. */
    GE(">=", Slot.op_ge) {

        @Override
        PyBool toBool(int c) { return c >= 0 ? Py.True : Py.False; }
    },

    /** The (reflected) {@code __contains__} operation. */
    IN("in", Slot.op_contains) {

        @Override
        PyBool toBool(int c) { return c >= 0 ? Py.True : Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            PyType vType = v.getType();
            try {
                MethodHandle contains = slot.getSlot(vType);
                boolean r = (boolean) contains.invokeExact(w, v);
                return r ? PyBool.True : PyBool.False;
            } catch (Slot.EmptyException e) {
                throw new TypeError(NOT_CONTAINER, vType.getName());
            }
        }
    },

    /** The inverted (reflected) {@code __contains__} operation. */
    NOT_IN("not in", Slot.op_contains) {

        @Override
        PyBool toBool(int c) { return c < 0 ? Py.True : Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            PyType vType = v.getType();
            try {
                MethodHandle contains = slot.getSlot(vType);
                boolean r = (boolean) contains.invokeExact(w, v);
                return r ? PyBool.False : PyBool.True;
            } catch (Slot.EmptyException e) {
                throw new TypeError(NOT_CONTAINER, vType.getName());
            }
        }
    },

    /** The identity operation. */
    IS("is") {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            return v == w ? PyBool.True : PyBool.False;
        }

    },

    /** The inverted identity operation. */
    IS_NOT("is not") {

        @Override
        PyBool toBool(int c) { return c != 0 ? Py.True : Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            return v != w ? PyBool.True : PyBool.False;
        }
    },

    /** The exception matching operation. */
    EXC_MATCH("matches") {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            return Py.NotImplemented; // ... implement me!
        }
    },

    /** A dummy operation representing an invalid comparison. */
    BAD("?") {

        @Override
        PyBool toBool(int c) { return Py.False; }

        @Override
        PyObject apply(PyObject v, PyObject w) throws Throwable {
            return Py.NotImplemented;
        }
    };

    final String text;
    final Slot slot;

    Comparison(String text, Slot slot) {
        this.text = text;
        this.slot = slot;
    }

    Comparison(String text) { this(text, null); }

    /**
     * The text corresponding to the value, e.g. "!=" for {@code NE},
     * "is not" for {@code IS_NOT}. Mostly for error messages.
     *
     * @return text corresponding
     */
    @Override
    public String toString() { return text; }

    /**
     * Translate CPython {@link Opcode#COMPARE_OP} opcode argument to
     * Comparison constant.
     *
     * @param oparg opcode argument
     * @return equivalent {@code Comparison} object
     */
    static Comparison from(int oparg) {
        return oparg >= 0 && oparg < from.length ? from[oparg] : BAD;
    }

    private static final Comparison[] from = values();

    /**
     * The swapped version of this comparison, e.g. LT with GT.
     *
     * @return swapped version of this comparison
     */
    Comparison swapped() { return swap[this.ordinal()]; }

    private static final Comparison[] swap =
            {GT, GE, EQ, NE, LT, LE, BAD, BAD, IS, IS_NOT, BAD, BAD};

    /**
     * Translate a comparison result into the appropriate boolean, for
     * example {@code GE.toBool(1)} is {@link Py#True}. For the the six
     * operations LT to GE inclusive, this is typically wrapped onto a
     * call to {@code Comparable.compareTo()}). For the others we assume
     * c==0 indicates equality.
     *
     * @param c comparison result
     * @return boolean equivalent for this operation
     */
    // Compare CPython object.h::Py_RETURN_RICHCOMPARE
    abstract PyBool toBool(int c);

    /**
     * Perform this comparison, raising {@code TypeError} when the
     * requested comparison operator is not supported.
     */
    // Compare CPython PyObject_RichCompare, do_richcompare in object.c
    PyObject apply(PyObject v, PyObject w) throws Throwable {
        PyType vType = v.getType();
        PyType wType = w.getType();
        Slot swappedSlot = null;

        // Try the swapped operation first if w is a sub-type of v

        if (vType != wType && wType.isSubTypeOf(vType)) {
            swappedSlot = swapped().slot;
            try {
                PyObject r = (PyObject) swappedSlot.getSlot(wType)
                        .invokeExact(w, v);
                if (r != Py.NotImplemented) { return r; }
            } catch (EmptyException e) {}
        }

        // Try the forward operation
        try {
            PyObject r =
                    (PyObject) slot.getSlot(wType).invokeExact(v, w);
            if (r != Py.NotImplemented) { return r; }
        } catch (EmptyException e) {}

        // Try the swapped operation if we haven't already
        if (swappedSlot == null) {
            swappedSlot = swapped().slot;
            try {
                PyObject r = (PyObject) swappedSlot.getSlot(wType)
                        .invokeExact(w, v);
                if (r != Py.NotImplemented) { return r; }
            } catch (EmptyException e) {}
        }

        /// Neither object implements this. Base == and != on identity.
        switch (this) {
            case EQ:
                return Py.val(v == w);
            case NE:
                return Py.val(v != w);
            default:
                throw comparisonTypeError(v, w);
        }
    }

    /**
     * Create a TypeError along the lines "OP not supported between
     * instances of V and W"
     *
     * @param v left arg
     * @param w right arg
     * @return the exception
     */
    PyException comparisonTypeError(PyObject v, PyObject w) {
        return new TypeError(NOT_SUPPORTED, this, v.getType().getName(),
                w.getType().getName());
    }
    private static String NOT_SUPPORTED =
            "'%s' not supported between instances of '%.100s' and '%.100s'";
    private static String NOT_CONTAINER =
            "'%.200s' object is not a container";
}