package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo2.Slot.EmptyException;

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
        if (v == PyBool.True)
            return true;
        else if (v == PyBool.False || v == Py.None)
            return false;
        else {
            // Ask the object type through the bool or length slots
            PyType t = v.getType();
            if (Slot.NB.bool.isDefinedFor(t))
                return (boolean) t.number.bool.invokeExact(v);
            else if (Slot.MP.length.isDefinedFor(t))
                return 0 != (int) t.mapping.length.invokeExact(v);
            else if (Slot.SQ.length.isDefinedFor(t))
                return 0 != (int) t.mapping.length.invokeExact(v);
            else
                // No bool and no length: claim everything is True.
                return true;
        }
    }

    /** Python size of {@code o} */
    static PyObject size(PyObject o) throws Throwable {
        // Note that the slot is called length but this method, size.
        try {
            MethodHandle mh = o.getType().sequence.length;
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
            MethodHandle mh = oType.mapping.subscript;
            return (PyObject) mh.invokeExact(o, key);
        } catch (EmptyException e) {}

        if (Slot.SQ.item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.NB.index.isDefinedFor(key.getType())) {
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
            MethodHandle mh = oType.mapping.ass_subscript;
            mh.invokeExact(o, key, value);
            return;
        } catch (EmptyException e) {}

        if (Slot.SQ.ass_item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.NB.index.isDefinedFor(key.getType())) {
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
     * @return whether {@code obj} has non-empty {@link Slot.NB#index}
     */
    static boolean indexCheck(PyObject obj) {
        return Slot.NB.index.isDefinedFor(obj.getType());
    }

}
