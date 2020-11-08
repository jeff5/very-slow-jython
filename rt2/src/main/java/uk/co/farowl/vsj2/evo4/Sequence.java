package uk.co.farowl.vsj2.evo4;

import java.util.List;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}. */
class Sequence extends Abstract {

    /** {@code true} iff {@code s} is a sequence type. */
    static boolean check(PyObject s) {
        return !(s instanceof PyDict)
                && Slot.op_getitem.isDefinedFor(s.getType());
    }

    /** Python size of {@code s} */
    static int size(PyObject s) throws Throwable {
        // Note that the slot is called sq_length but this method, size.
        try {
            PyType sType = s.getType();
            return (int) sType.op_len.invokeExact(s);
        } catch (Slot.EmptyException e) {
            throw typeError(HAS_NO_LEN, s);
        }
    }

    /** Python {@code s[i]} */
    static PyObject getItem(PyObject s, int i) throws Throwable {
        try {
            PyObject k = Py.val(i);
            return (PyObject) s.getType().op_getitem.invokeExact(s, k);
        } catch (EmptyException e) {
            throw typeError(NOT_INDEXING, s);
        }
    }

    /** Python {@code s[i] = value} */
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

    /** Python {@code del s[i]} */
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
