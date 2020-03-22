package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo3.Slot.EmptyException;

/**
 * The "abstract interface" to operations on Python objects. Methods
 * here execute the slot functions of the type definition of the objects
 * passed in. A primary application is to the CPython byte code
 * interpreter. (Methods here often correspond closely to a CPython
 * opcode.)
 * <p>
 * See also {@link Number}, {@link Sequence} and {@link Mapping} which
 * contain the abstract interface to the corresponding type families. In
 * CPython, the methods of all these classes are found in
 * {@code Objects/abstract.c}
 */
class Abstract {

    /**
     * Test a value used as condition in a {@code for} or {@code if}
     * statement.
     */
    static boolean isTrue(PyObject v) throws Throwable {
        // Begin with common special cases
        if (v == Py.True)
            return true;
        else if (v == Py.False || v == Py.None)
            return false;
        else {
            // Ask the object type through the nb_bool or sq_length slots
            PyType t = v.getType();
            if (Slot.nb_bool.isDefinedFor(t))
                return (boolean) t.nb_bool.invokeExact(v);
            else if (Slot.mp_length.isDefinedFor(t))
                return 0 != (int) t.mp_length.invokeExact(v);
            else if (Slot.sq_length.isDefinedFor(t))
                return 0 != (int) t.sq_length.invokeExact(v);
            else
                // No nb_bool and no sq_length: claim everything is True.
                return true;
        }
    }

    /**
     * Perform a rich comparison, raising {@code TypeError} when the
     * requested comparison operator is not supported.
     */
    static PyObject do_richcompare(PyObject v, PyObject w,
            Comparison op) throws Throwable {
        PyType vType = v.getType();
        PyType wType = w.getType();

        boolean checkedReverse = false;
        MethodHandle f;

        if (vType != wType && wType.isSubTypeOf(vType)
                && (f = wType.tp_richcompare) != RICH_EMPTY) {
            checkedReverse = true;
            PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
            if (r != Py.NotImplemented) { return r; }
        }

        if ((f = vType.tp_richcompare) != RICH_EMPTY) {
            PyObject r = (PyObject) f.invokeExact(v, w, op);
            if (r != Py.NotImplemented) { return r; }
        }

        if (!checkedReverse && (f = wType.tp_richcompare) != RICH_EMPTY) {
            PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
            if (r != Py.NotImplemented) { return r; }
        }

        /// Neither object implements op: base == and != on identity.
        switch (op) {
            case EQ:
                return Py.val(v == w);
            case NE:
                return Py.val(v != w);
            default:
                throw comparisonTypeError(v, w, op);
        }
    }

    private static final MethodHandle RICH_EMPTY =
            Slot.Signature.RICHCMP.empty;

    static PyException comparisonTypeError(PyObject v, PyObject w,
            Comparison op) {
        String fmt =
                "'%s' not supported between instances of '%.100s' and '%.100s'";
        return new TypeError(fmt, op, v.getType().getName(),
                w.getType().getName());
    }

    static PyObject richCompare(PyObject v, PyObject w, Comparison op)
            throws Throwable {
        PyObject res;
        res = do_richcompare(v, w, op);
        return res;
    }

    /*
     * Perform a rich comparison with integer result. This wraps
     * PyObject_RichCompare(), returning -1 for error, 0 for false, 1
     * for true.
     */
    static boolean richCompareBool(PyObject v, PyObject w,
            Comparison op) throws Throwable {
        /*
         * Quick result when objects are the same. Guarantees that
         * identity implies equality.
         */
        if (v == w) {
            if (op == Comparison.EQ)
                return true;
            else if (op == Comparison.NE)
                return false;
        }
        return isTrue(richCompare(v, w, op));
    }

    /** Python size of {@code o} */
    static PyObject size(PyObject o) throws Throwable {
        // Note that the slot is called sq_length but this method, size.
        try {
            MethodHandle mh = o.getType().sq_length;
            return (PyObject) mh.invokeExact(o);
        } catch (Slot.EmptyException e) {}

        return Mapping.size(o);
    }

    /**
     * Python {@code o[key]} where {@code o} may be a mapping or a
     * sequence.
     */
    static PyObject getItem(PyObject o, PyObject key) throws Throwable {
        // Corresponds to abstract.c : PyObject_GetItem
        // Decisions are based on types of o and key
        PyType oType = o.getType();

        try {
            return (PyObject) oType.mp_subscript.invokeExact(o, key);
        } catch (EmptyException e) {}

        if (Slot.sq_item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.nb_index.isDefinedFor(key.getType())) {
                int k = Number.asSize(key, IndexError::new);
                return Sequence.getItem(o, k);
            } else
                throw typeError(MUST_BE_INT_NOT, key);
        } else
            throw typeError(NOT_SUBSCRIPTABLE, o);
    }

    static void setItem(PyObject o, PyObject key, PyObject value)
            throws Throwable {
        // Corresponds to abstract.c : PyObject_SetItem
        // Decisions are based on types of o and key
        PyType oType = o.getType();

        try {
            oType.mp_ass_subscript.invokeExact(o, key, value);
            return;
        } catch (EmptyException e) {}

        if (Slot.sq_ass_item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.nb_index.isDefinedFor(key.getType())) {
                int k = Number.asSize(key, IndexError::new);
                Sequence.setItem(o, k, value);
            } else
                throw typeError(MUST_BE_INT_NOT, key);
        } else
            throw typeError(NOT_ITEM_ASSIGNMENT, o);
    }

    protected static final String HAS_NO_LEN =
            "object of type '%.200s' has no len()";
    private static final String MUST_BE_INT_NOT =
            "sequence index must be integer, not '%.200s'";
    private static final String NOT_SUBSCRIPTABLE =
            "'%.200s' object is not subscriptable";
    protected static final String NOT_ITEM_ASSIGNMENT =
            "'%.200s' object does not support item assignment";

    /**
     * Create a {@link TypeError} with a message involving the type of
     * {@code o}.
     *
     * @param fmt format string for message (with one {@code %s}
     * @param o object whose type name will substitute for {@code %s}
     * @return exception to throw
     */
    static TypeError typeError(String fmt, PyObject o) {
        return new TypeError(fmt, o.getType().getName());
    }

    /**
     * Convenience function to create a {@link TypeError} with a message
     * along the lines "T indices must be integers or slices, not X"
     * involving the type name T of a target and the type X of {@code o}
     * presented as an index, e.g. "list indices must be integers or
     * slices, not str".
     *
     * @param t target of function or operation
     * @param o actual object presented as an index
     * @return exception to throw
     */
    static TypeError indexTypeError(PyObject t, PyObject o) {
        String fmt =
                "%.200s indices must be integers or slices, not %.200s";
        return new TypeError(fmt, t.getType().getName(),
                o.getType().getName());
    }

    /**
     * Convenience function to create a {@link TypeError} with a message
     * along the lines "F returned non-T (type X)" involving a function
     * name, an expected type T and the type X of {@code o}, e.g.
     * "__int__ returned non-int (type str)".
     *
     * @param f name of function or operation
     * @param t expected type of return
     * @param o actual object returned
     * @return exception to throw
     */
    static TypeError returnTypeError(String f, String t, PyObject o) {
        String fmt = "%.200s returned non-%.200s (type %.200s)";
        return new TypeError(fmt, f, t, o.getType().getName());
    }

    /**
     * True iff the object has a slot for conversion to the index type.
     *
     * @param obj to test
     * @return whether {@code obj} has non-empty {@link Slot#nb_index}
     */
    static boolean indexCheck(PyObject obj) {
        return Slot.nb_index.isDefinedFor(obj.getType());
    }

}
