package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;
import java.util.function.Function;

import uk.co.farowl.vsj2.evo3.Slot.EmptyException;
import uk.co.farowl.vsj2.evo3.Slot.NB;

/** Compare CPython {@code abstract.h}: {@code Py_Number_*}. */
class Number extends Abstract {

    /** Python {@code -v} */
    static PyObject negative(PyObject v) throws Throwable {
        try {
            MethodHandle mh = v.getType().number.negative;
            return (PyObject) mh.invokeExact(v);
        } catch (Slot.EmptyException e) {
            throw operandError("-", v);
        }
    }

    /** Create a {@code TypeError} for the named unary op. */
    static PyException operandError(String op, PyObject v) {
        return new TypeError("bad operand type for unary %s: '%.200s'",
                op, v.getType().getName());
    }

    /** Python {@code v+w} */
    static PyObject add(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.add);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw operandError("+", v, w);
    }

    /** Python {@code v-w} */
    static PyObject subtract(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.subtract);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw operandError("-", v, w);
    }

    /** Python {@code v*w} */
    static PyObject multiply(PyObject v, PyObject w) throws Throwable {
        try {
            PyObject r = binary_op1(v, w, Slot.NB.multiply);
            if (r != Py.NotImplemented) { return r; }
        } catch (Slot.EmptyException e) {}

        // Try the sequence interpretations ...
        MethodHandle mh = v.getType().sequence.repeat;
        if (mh != SQ_INDEX_EMPTY) { return sequence_repeat(mh, v, w); }
        mh = w.getType().sequence.repeat;
        if (mh != SQ_INDEX_EMPTY) { return sequence_repeat(mh, w, v); }

        // Nothing worked
        throw operandError("*", v, w);
    }

    private static final MethodHandle SQ_INDEX_EMPTY =
            Slot.Signature.SQ_INDEX.empty;

    /**
     * Helper for implementing binary operation. If neither the left
     * type nor the right type implements the operation, it will either
     * return {@link Py#NotImplemented} or throw {@link EmptyException}.
     * Both mean the same thing.
     *
     * @param v left operand
     * @param w right operand
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
            if (slotv != BINARY_EMPTY) {
                PyObject r = (PyObject) slotv.invokeExact(v, w);
                if (r != Py.NotImplemented)
                    return r;
            }
            return (PyObject) slotw.invokeExact(v, w);

        } else {
            // Right is sub-class: ask first (if not empty).
            if (slotw != BINARY_EMPTY) {
                PyObject r = (PyObject) slotw.invokeExact(v, w);
                if (r != Py.NotImplemented)
                    return r;
            }
            return (PyObject) slotv.invokeExact(v, w);
        }
    }

    private static PyObject sequence_repeat(MethodHandle repeat,
            PyObject seq, PyObject n) throws TypeError, Throwable {
        if (indexCheck(n)) {
            int count = asSize(n, IndexError::new);
            return (PyObject) repeat.invokeExact(seq, count);
        } else {
            throw typeError(CANT_MULTIPLY, n);
        }
    }

    private static final MethodHandle BINARY_EMPTY =
            Slot.Signature.BINARY.empty;

    /**
     * Return a Python {@code int} (or subclass) from the object
     * {@code o}. Raise {@code TypeError} if the result is not a Python
     * {@code int} subclass, or if the object {@code o} cannot be
     * interpreted as an index (it does not fill {@link NB#index}). This
     * method makes no guarantee about the <i>range</i> of the result.
     */
    static PyObject index(PyObject o) throws Throwable {

        PyType itemType = o.getType();
        PyObject result;

        if (itemType.isSubTypeOf(PyLong.TYPE))
            return o;
        else {
            try {
                result = (PyObject) itemType.number.index
                        .invokeExact(o);
                // Enforce expectations on the return type
                PyType resultType = result.getType();
                if (resultType == PyLong.TYPE)
                    return result;
                else if (resultType.isSubTypeOf(PyLong.TYPE))
                    // XXX Sub-types not implemented yet
                    // CPython issues DeprecationWarning on sub-type.
                    return result;
                else
                    throw returnTypeError("__index__", "int", result);
            } catch (EmptyException e) {
                throw typeError(CANNOT_INTERPRET_AS_INT, o);
            }
        }
    }

    /**
     * Returns {@code o} converted to a Java {@code int} if {@code o}
     * can be interpreted as an integer. If the call fails, an exception
     * is raised, which may be a {@link TypeError} or anything thrown by
     * {@code o}'s implementation of {@code __index__}. In the special
     * case of {@link OverflowError}, a replacement may be made where
     * the message is formulated by this method and the type of
     * exception by the caller. (Arcane, but it's what CPython does.) A
     * recommended idiom for this is<pre>
     *      int k = Number.asSize(key, IndexError::new);
     * </pre>
     *
     * @param o the object to convert to an {@code int}
     *
     * @param exc {@code null} or function of {@code String} returning
     *            the exception to use for overflow.
     * @return {@code int} value of {@code o}
     * @throws TypeError if {@code o} cannot be converted to a Python
     *             {@code int}
     * @throws Throwable for all sorts of reasons
     */
    static int asSize(PyObject o, Function<String, PyException> exc)
            throws TypeError, Throwable {

        // Convert to Python int or sub-class. (May raise TypeError.)
        PyObject value = Number.index(o);
        PyType valueType = value.getType();

        try {
            // We're done if PyLong.asSize() returns without error.
            if (valueType == PyLong.TYPE)
                return ((PyLong) value).asSize();
            else if (valueType.isSubTypeOf(PyLong.TYPE))
                // XXX Sub-types not implemented: maybe can't cast
                return ((PyLong) value).asSize();
            else
                return 0;   // Number.index guarantees we never reach
                            // here
        } catch (OverflowError e) {
            // Caller may replace overflow with own type of exception
            if (exc == null) {
                // No handler: default clipping is sufficient.
                assert valueType.isSubTypeOf(PyLong.TYPE);
                if (((PyLong) value).signum() < 0)
                    return Integer.MIN_VALUE;
                else
                    return Integer.MAX_VALUE;
            } else {
                // Throw an exception of the caller's preferred type.
                String msg = String.format(CANNOT_FIT,
                        o.getType().getName());
                throw exc.apply(msg);
            }
        }
    }

    private static final String CANT_MULTIPLY =
            "can't multiply sequence by non-int of type '%.200s'";
    private static final String CANNOT_INTERPRET_AS_INT =
            "'%.200s' object cannot be interpreted as an integer";
    private static final String CANNOT_FIT =
            "cannot fit '%.200s' into an index-sized integer";

    /** Create a {@code TypeError} for the named binary op. */
    static PyException operandError(String op, PyObject v, PyObject w) {
        return new TypeError(UNSUPPORTED_TYPES, op,
                v.getType().getName(), w.getType().getName());
    }

    private static final String UNSUPPORTED_TYPES =
            "unsupported operand type(s) for %s: '%.100s' and '%.100s'";
}
