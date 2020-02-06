package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code abstract.h}: {@code Py_Number_*}. */
class Number {

    /** Python {@code -v} */
    static PyObject negative(PyObject v) throws Throwable {
        try {
            MethodHandle mh = v.getType().number.negative;
            return (PyObject) mh.invokeExact(v);
        } catch (Slot.EmptyException e) {
            throw typeError("-", v);
        }
    }

    /** Create a {@code TypeError} for the named unary op. */
    static PyException typeError(String op, PyObject v) {
        return new TypeError("bad operand type for unary %s: '%.200s'", op,
                v.getType().getName());
    }

    /** Python {@code v+w} */
    static PyObject add(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.add);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw typeError("+", v, w);
    }

    /** Python {@code v-w} */
    static PyObject subtract(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.subtract);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw typeError("-", v, w);
    }

    /** Python {@code v*w} */
    static PyObject multiply(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.multiply);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw typeError("*", v, w);
    }

    /**
     * Helper for implementing binary operation. If neither the left type
     * nor the right type implements the operation, it will either return
     * {@link Py#NotImplemented} or throw {@link EmptyException}. Both mean
     * the same thing.
     *
     * @param v left operand
     * @param w right oprand
     * @param binop operation to apply
     * @return result or {@code Py.NotImplemented}
     * @throws Slot.EmptyException when an empty slot is invoked
     * @throws Throwable from the implementation of the operation
     */
    private static PyObject binary_op1(PyObject v, PyObject w,
            Slot.NB binop) throws Slot.EmptyException, Throwable {
        PyType vtype = v.getType();
        PyType wtype = w.getType();

        MethodHandle slotv = binop.getSlot(vtype);
        MethodHandle slotw;

        if (wtype == vtype || (slotw = binop.getSlot(wtype)) == slotv)
            // Both types give the same result
            return (PyObject) slotv.invokeExact(v, w);

        else if (!wtype.isSubTypeOf(vtype)) {
            // Ask left (if not empty) then right.
            if (slotv != Slot.BINARY_EMPTY) {
                PyObject r = (PyObject) slotv.invokeExact(v, w);
                if (r != Py.NotImplemented)
                    return r;
            }
            return (PyObject) slotw.invokeExact(v, w);

        } else {
            // Right is sub-class: ask first (if not empty).
            if (slotw != Slot.BINARY_EMPTY) {
                PyObject r = (PyObject) slotw.invokeExact(v, w);
                if (r != Py.NotImplemented)
                    return r;
            }
            return (PyObject) slotv.invokeExact(v, w);
        }
    }

    /** Create a {@code TypeError} for the named binary op. */
    static PyException typeError(String op, PyObject v, PyObject w) {
        return new TypeError(
                "unsupported operand type(s) for %.100s: "
                        + "'%.100s' and '%.100s'",
                op, v.getType().getName(), w.getType().getName());
    }
}
