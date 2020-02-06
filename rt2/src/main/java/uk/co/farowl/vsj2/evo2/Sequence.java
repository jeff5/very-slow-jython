package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code abstract.h}: {@code Py_Sequence_*}. */
class Sequence {

    /** Python size of {@code s} */
    static PyObject size(PyObject u) throws Throwable {
        // Note that the slot is called sq_length but the method size.
        try {
            // NPE if u (or type?) is null
            MethodHandle mh = u.getType().sequence.length;
            // Could throw anything
            return (PyObject) mh.invokeExact(u);
        } catch (Slot.EmptyException e) {
            throw typeError("-", u);
        }
    }

    static PyException typeError(String op, PyObject o) {
        return new PyException("bad operand type for unary %s: '%.200s'",
                op, o.getType().getName());
    }
}
