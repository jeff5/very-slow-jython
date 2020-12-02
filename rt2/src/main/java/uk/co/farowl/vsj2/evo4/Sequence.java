package uk.co.farowl.vsj2.evo4;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}. */
class Sequence extends Abstract {

    private Sequence() {} // only static methods here

    /**
     * {@code true} iff {@code s} is of sequence type.
     *
     * @param s possible sequence
     * @return {@code true} iff is a sequence
     */
    static boolean check(PyObject s) {
        return !(s instanceof PyDict)
                && Slot.op_getitem.isDefinedFor(s.getType());
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
        try {
            PyObject k = Py.val(i);
            return (PyObject) s.getType().op_getitem.invokeExact(s, k);
        } catch (EmptyException e) {
            throw typeError(NOT_INDEXING, s);
        }
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
        try {
            PyObject k = Py.val(i);
            s.getType().op_setitem.invokeExact(s, k, value);
            return;
        } catch (EmptyException e) {
            throw typeError(DOES_NOT_SUPPORT_ITEM, s, "assignment");
        }
    }

    /**
     * Python {@code del s[i]} with Python sequence semantics.
     *
     * @param s the sequence to operate on
     * @param i index
     * @throws TypeError when {@code s} does not support indexing
     * @throws Throwable from invoked method implementations
     */
    static void delItem(PyObject s, int i) throws Throwable {
        try {
            PyObject k = Py.val(i);
            s.getType().op_delitem.invokeExact(s, k);
            return;
        } catch (EmptyException e) {
            throw typeError(DOES_NOT_SUPPORT_ITEM, s, "deletion");
        }
    }

    private static final String NOT_INDEXING =
            // XXX is this different from Abstract.NOT_SUBSCRIPTABLE?
            "'%.200s' object does not support indexing";
}
