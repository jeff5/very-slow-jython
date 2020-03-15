package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo3.Slot.EmptyException;

/** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}. */
class Sequence extends Abstract {

    /** Python size of {@code s} */
    static PyObject size(PyObject s) throws Throwable {
        // Note that the slot is called sq_length but this method, size.
        PyType sType = s.getType();

        try {
            return (PyObject) sType.sq_length.invokeExact(s);
        } catch (Slot.EmptyException e) {}

        if (Slot.mp_length.isDefinedFor(sType))
            // Caller should have tried Abstract.size
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(HAS_NO_LEN, s);
    }

    /** Python {@code s[i]} */
    static PyObject getItem(PyObject s, int i) throws Throwable {
        PyType sType = s.getType();

        if (i < 0) {
            // Index from the end of the sequence (if it has one)
            try {
                i += (int) sType.sq_length.invokeExact(s);
            } catch (EmptyException e) {}
        }

        try {
            return (PyObject) sType.sq_item.invokeExact(s, i);
        } catch (EmptyException e) {}

        if (Slot.mp_subscript.isDefinedFor(sType))
            // Caller should have tried Abstract.getItem
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(NOT_INDEXING, s);
    }

    static void setItem(PyObject s, int i, PyObject o)
            throws Throwable {
        PyType sType = s.getType();

        if (i < 0) {
            // Index from the end of the sequence (if it has one)
            try {
                i += (int) sType.sq_length.invokeExact(s);
            } catch (EmptyException e) {}
        }

        try {
            sType.sq_ass_item.invokeExact(s, i, o);
            return;
        } catch (EmptyException e) {}

        if (Slot.mp_ass_subscript.isDefinedFor(sType))
            // Caller should have tried Abstract.setItem
            throw typeError(NOT_SEQUENCE, s);
        throw typeError(NOT_ITEM_ASSIGNMENT, s);
    }

    private static final String NOT_SEQUENCE =
            "%.200s is not a sequence";
    private static final String NOT_INDEXING =
            // XXX is this different from Abstract.NOT_SUBSCRIPTABLE?
            "'%.200s' object does not support indexing";
}
