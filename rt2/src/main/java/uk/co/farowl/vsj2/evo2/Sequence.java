package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo2.Slot.EmptyException;

/** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}. */
class Sequence extends Abstract {

    /**
     * {@code len(s)} with Python sequence semantics.
     *
     * @param s the sequence to operate on
     * @return {@code len(s)}
     * @throws Throwable from invoked method implementations
     */
    static PyObject size(PyObject s) throws Throwable {
        // Note that the slot is called length but this method, size.
        PyType sType = s.getType();

        try {
            MethodHandle mh = sType.sequence.length;
            return (PyObject) mh.invokeExact(s);
        } catch (Slot.EmptyException e) {}

        if (Slot.MP.length.isDefinedFor(sType))
            // Caller should have tried Abstract.size
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(HAS_NO_LEN, s);
    }

    /**
     * {@code s[i]} with Python sequence semantics.
     *
     * @param s the sequence to operate on
     * @param i index
     * @return {@code s[i]}
     * @throws TypeError when {@code s} does not support indexing
     * @throws Throwable from invoked method implementations
     */
    static PyObject getItem(PyObject s, int i) throws Throwable {
        PyType sType = s.getType();
        PyType.SequenceMethods sq = sType.sequence;

        if (i < 0) {
            // Index from the end of the sequence (if it has one)
            try {
                i += (int) sq.length.invokeExact(s);
            } catch (EmptyException e) {}
        }

        try {
            return (PyObject) sq.item.invokeExact(s, i);
        } catch (EmptyException e) {}

        if (Slot.MP.subscript.isDefinedFor(sType))
            // Caller should have tried Abstract.getItem
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(NOT_INDEXING, s);
    }

    /**
     * Python {@code s[i] = value} with Python sequence semantics.
     *
     * @param s the sequence to operate on
     * @param i index
     * @param value to set at {@code s[i]}
     * @throws TypeError when {@code s} does not support indexing
     * @throws Throwable from invoked method implementations
     */
    static void setItem(PyObject s, int i, PyObject value)
            throws Throwable {
        PyType sType = s.getType();
        PyType.SequenceMethods sq = sType.sequence;

        if (i < 0) {
            // Index from the end of the sequence (if it has one)
            try {
                i += (int) sq.length.invokeExact(s);
            } catch (EmptyException e) {}
        }

        try {
            sq.ass_item.invokeExact(s, i, value);
            return;
        } catch (EmptyException e) {}

        if (Slot.MP.ass_subscript.isDefinedFor(sType))
            // Caller should have tried Abstract.setItem
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(NOT_ITEM_ASSIGNMENT, s);
    }

    private static final String NOT_SEQUENCE =
            "%.200s is not a sequence";
    private static final String NOT_INDEXING =
            // is this different from Abstract.NOT_SUBSCRIPTABLE?
            "'%.200s' object does not support indexing";
}
