package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.util.function.Function;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** Compare CPython {@code abstract.h}: {@code Py_Number_*}. */
public class Number extends Abstract {

    private Number() {} // only static methods here

    /**
     * {@code -v}: unary negative with Python semantics.
     *
     * @param v operand
     * @return {@code -v}
     * @throws Throwable from invoked implementations
     */
    public static PyObject negative(PyObject v) throws Throwable {
        try {
            return (PyObject) v.getType().op_neg.invokeExact(v);
        } catch (Slot.EmptyException e) {
            throw operandError(Slot.op_neg, v);
        }
    }

    /**
     * {@code abs(v)}: absolute value with Python semantics.
     *
     * @param v operand
     * @return {@code -v}
     * @throws Throwable from invoked implementations
     */
    public static PyObject absolute(PyObject v) throws Throwable {
        try {
            return (PyObject) v.getType().op_abs.invokeExact(v);
        } catch (Slot.EmptyException e) {
            throw operandError(Slot.op_abs, v);
        }
    }

    /**
     * Create a {@code TypeError} for a named unary operation, along the
     * lines "bad operand type for OP: 'T'".
     *
     * @param op operation to report
     * @param v actual operand (only {@code v.getType()} is used)
     * @return exception to throw
     */
    static PyException operandError(Slot op, PyObject v) {
        return new TypeError("bad operand type for %s: '%.200s'",
                op.opName, v.getType().getName());
    }

    /**
     * {@code v + w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v + w}
     * @throws Throwable from invoked implementations
     */
    public static PyObject add(PyObject v, PyObject w)
            throws Throwable {
        return binary_op(v, w, Slot.op_add);
    }

    /**
     * {@code v - w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v - w}
     * @throws Throwable from invoked implementations
     */
    public static PyObject subtract(PyObject v, PyObject w)
            throws Throwable {
        return binary_op(v, w, Slot.op_sub);
    }

    /**
     * {@code v * w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v * w}
     * @throws Throwable from invoked implementations
     */
    public static PyObject multiply(PyObject v, PyObject w)
            throws Throwable {
        return binary_op(v, w, Slot.op_mul);
    }

    /**
     * {@code v | w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v | w}
     * @throws Throwable from invoked implementations
     */
    static final PyObject or(PyObject v, PyObject w) throws Throwable {
        return binary_op(v, w, Slot.op_or);
    }

    /**
     * {@code v & w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v & w}
     * @throws Throwable from invoked implementations
     */
    static final PyObject and(PyObject v, PyObject w) throws Throwable {
        return binary_op(v, w, Slot.op_and);
    }

    /**
     * {@code v ^ w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v ^ w}
     * @throws Throwable from invoked implementations
     */
    static final PyObject xor(PyObject v, PyObject w) throws Throwable {
        return binary_op(v, w, Slot.op_xor);
    }

    /**
     * Helper for implementing a binary operation that has one,
     * slot-based interpretation.
     *
     * @param v left operand
     * @param w right operand
     * @param binop operation to apply
     * @return result of operation
     * @throws TypeError if neither operand implements the operation
     * @throws Throwable from the implementation of the operation
     */
    private static PyObject binary_op(PyObject v, PyObject w,
            Slot binop) throws TypeError, Throwable {
        try {
            PyObject r = binary_op1(v, w, binop);
            if (r != Py.NotImplemented)
                return r;
        } catch (Slot.EmptyException e) {}
        throw operandError(binop, v, w);
    }

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
            Slot binop) throws Slot.EmptyException, Throwable {
        PyType vtype = v.getType();
        PyType wtype = w.getType();

        MethodHandle slotv = binop.getSlot(vtype);
        MethodHandle slotw;

        /*
         * CPython would also test: (slotw = rbinop.getSlot(wtype)) ==
         * slotv as an optimisiation , but that's never the case since
         * we use distinct binop and rbinop slots.
         */
        if (wtype == vtype)
            // Same types so only try the binop slot
            return (PyObject) slotv.invokeExact(v, w);

        else if (!wtype.isSubTypeOf(vtype)) {
            // Ask left (if not empty) then right.
            if (slotv != BINARY_EMPTY) {
                PyObject r = (PyObject) slotv.invokeExact(v, w);
                if (r != Py.NotImplemented)
                    return r;
            }
            slotw = binop.getAltSlot(wtype);
            return (PyObject) slotw.invokeExact(w, v);

        } else {
            // Right is sub-class: ask first (if not empty).
            slotw = binop.getAltSlot(wtype);
            if (slotw != BINARY_EMPTY) {
                PyObject r = (PyObject) slotw.invokeExact(w, v);
                if (r != Py.NotImplemented)
                    return r;
            }
            return (PyObject) slotv.invokeExact(v, w);
        }
    }

    private static final MethodHandle BINARY_EMPTY =
            Slot.Signature.BINARY.empty;

    /**
     * Return a Python {@code int} (or subclass) from the object
     * {@code o}. Raise {@code TypeError} if the result is not a Python
     * {@code int} subclass, or if the object {@code o} cannot be
     * interpreted as an index (it does not fill {@link Slot#op_index}).
     * This method makes no guarantee about the <i>range</i> of the
     * result.
     *
     * @param o operand
     * @return {@code o} coerced to a Python {@code int}
     * @throws TypeError if {@code o} cannot be interpreted as an
     *             {@code int}
     * @throws Throwable otherwise from invoked implementations
     */
    static PyLong index(PyObject o) throws TypeError, Throwable {

        PyType itemType = o.getType();
        PyObject result;

        if (itemType.isSubTypeOf(PyLong.TYPE))
            return (PyLong) o;
        else {
            try {
                result = (PyObject) itemType.op_index.invokeExact(o);
                // Enforce expectations on the return type
                PyType resultType = result.getType();
                if (resultType == PyLong.TYPE)
                    return (PyLong) result;
                else if (resultType.isSubTypeOf(PyLong.TYPE))
                    // ... Sub-types not implemented yet
                    // CPython issues DeprecationWarning on sub-type.
                    return (PyLong) result;
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
     * @throws Throwable on other errors
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
                // ... Sub-types not implemented: maybe can't cast
                return ((PyLong) value).asSize();
            else
                // Number.index guarantees we never reach here
                return 0;
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

    /**
     * Returns the {@code o} converted to an integer object. This is the
     * equivalent of the Python expression {@code int(o)}.
     *
     * @param o operand
     * @return {@code int(o)}
     * @throws TypeError if {@code o} cannot be converted to a Python
     *             {@code int}
     * @throws Throwable on other errors
     */
    // Compare with CPython abstract.h :: PyNumber_Long
    static PyObject asLong(PyObject o) throws TypeError, Throwable {
        PyObject result;
        PyType oType = o.getType();

        if (oType == PyLong.TYPE) {
            // Fast path for the case that we already have an int.
            return o;
        }

        else if (Slot.op_int.isDefinedFor(oType)) {
            // Normalise away subclasses of int
            result = PyLong.fromIntOf(o);
            return PyLong.from((PyLong) result);
        }

        else if (Slot.op_index.isDefinedFor(oType)) {
            // Normalise away subclasses of int
            result = PyLong.fromIndexOrIntOf(o);
            return PyLong.from((PyLong) result);
        }

        // ... Not implemented: else try the __trunc__ method

        if ((o instanceof PyUnicode))
            return PyLong.fromUnicode((PyUnicode) o, 10);

        // else if ... support for bytes-like objects
        else
            throw argumentTypeError("int", 0,
                    "a string, a bytes-like object or a number", o);
    }

    private static final String CANNOT_INTERPRET_AS_INT =
            "'%.200s' object cannot be interpreted as an integer";
    private static final String CANNOT_FIT =
            "cannot fit '%.200s' into an index-sized integer";

    /**
     * Convert an object to a Python {@code float}, This is the
     * equivalent of the Python expression {@code float(o)}.
     *
     * @param o to convert
     * @return converted value
     * @throws TypeError if {@code __float__} is defined but does nor
     *             return a {@code float}
     * @throws Throwable on other errors
     */
    // Compare CPython abstract.c: PyNumber_Float
    public static PyFloat toFloat(PyObject o) throws TypeError, Throwable {
        /*
         * Ever so similar to PyFloat.asDouble, but returns always
         * exactly a PyFloat, constructed if necessary from the value in
         * a sub-type of PyFloat, or a from string.
         */
        PyType oType = o.getType();

        if (oType == PyFloat.TYPE) {
            return (PyFloat) o;

        } else {
            try {
                // Try __float__ (if defined)
                PyObject res = (PyObject) oType.op_float.invokeExact(o);
                if (res.getType() == PyFloat.TYPE) // Exact type
                    return (PyFloat) res;
                else if (res instanceof PyFloat) { // Sub-class
                    // Warn about this and make a clean PyFloat
                    returnDeprecation("__float__", "float", res);
                    return Py.val(((PyFloat) res).value);
                } else
                    // Slot defined but not a PyFloat at all
                    throw returnTypeError("__float__", "float", res);
            } catch (Slot.EmptyException e) {}

            // Fall out here if op_float was not defined
            if (Slot.op_index.isDefinedFor(oType))
                return Py.val(index(o).doubleValue());
            else
                return PyFloat.fromString(o);
        }
    }

    /**
     * Throw a {@code TypeError} for the named binary operation, along
     * the lines "unsupported operand type(s) for OP: 'V' and 'W'".
     *
     * @param op operation to report
     * @param v left operand (only {@code v.getType()} is used)
     * @param w right operand (only {@code w.getType()} is used)
     * @return exception to throw
     */
    // ... Possibly move to Slot so may bind early.
    static PyException operandError(Slot op, PyObject v, PyObject w) {
        return new TypeError(UNSUPPORTED_TYPES, op.opName,
                v.getType().getName(), w.getType().getName());
    }

    private static final String UNSUPPORTED_TYPES =
            "unsupported operand type(s) for %s: '%.100s' and '%.100s'";
}
