// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * Abstract API for operations on numeric types, corresponding to
 * CPython methods defined in {@code abstract.h} and with names like:
 * {@code PyNumber_*}.
 */
public class PyNumber extends Abstract {

    private PyNumber() {} // only static methods here

    /**
     * {@code -v}: unary negative with Python semantics.
     *
     * @param v operand
     * @return {@code -v}
     * @throws Throwable from invoked implementations
     */
    public static Object negative(Object v) throws Throwable {
        try {
            return PyType.representationOf(v).op_neg.invokeExact(v);
        } catch (EmptyException e) {
            throw SpecialMethod.op_neg.operandError(v);
        }
    }

    /**
     * {@code ~v}: unary negative with Python semantics.
     *
     * @param v operand
     * @return {@code ~v}
     * @throws Throwable from invoked implementations
     */
    public static Object invert(Object v) throws Throwable {
        try {
            return PyType.representationOf(v).op_invert.invokeExact(v);
        } catch (EmptyException e) {
            throw SpecialMethod.op_invert.operandError(v);
        }
    }

    /**
     * {@code abs(v)}: absolute value with Python semantics.
     *
     * @param v operand
     * @return {@code -v}
     * @throws Throwable from invoked implementations
     */
    public static Object absolute(Object v) throws Throwable {
        try {
            return PyType.representationOf(v).op_abs.invokeExact(v);
        } catch (EmptyException e) {
            throw SpecialMethod.op_abs.operandError(v);
        }
    }

    /**
     * {@code v + w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v + w}
     * @throws Throwable from invoked implementations
     */
    public static Object add(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_add);
    }

    /**
     * {@code v - w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v - w}
     * @throws Throwable from invoked implementations
     */
    public static Object subtract(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_sub);
    }

    /**
     * {@code v * w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v * w}
     * @throws Throwable from invoked implementations
     */
    public static Object multiply(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_mul);
    }

    /**
     * {@code v | w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v | w}
     * @throws Throwable from invoked implementations
     */
    static final Object or(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_or);
    }

    /**
     * {@code v & w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v & w}
     * @throws Throwable from invoked implementations
     */
    static final Object and(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_and);
    }

    /**
     * {@code v ^ w} with Python semantics.
     *
     * @param v left operand
     * @param w right operand
     * @return {@code v ^ w}
     * @throws Throwable from invoked implementations
     */
    static final Object xor(Object v, Object w) throws Throwable {
        return binary_op(v, w, SpecialMethod.op_xor);
    }

    /**
     * Helper for implementing a binary operation that has one,
     * slot-based interpretation.
     *
     * @param v left operand
     * @param w right operand
     * @param binop operation to apply
     * @return result of operation
     * @throws PyBaseException (TypeError) if neither operand implements
     *     the operation
     * @throws Throwable from the implementation of the operation
     */
    private static Object binary_op(Object v, Object w,
            SpecialMethod binop) throws PyBaseException, Throwable {
        try {
            Object r = binary_op1(v, w, binop);
            if (r != Py.NotImplemented) { return r; }
        } catch (EmptyException e) {}
        throw binop.operandError(v, w);
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
     * @throws EmptyException when an empty slot is invoked
     * @throws Throwable from the implementation of the operation
     */
    private static Object binary_op1(Object v, Object w,
            SpecialMethod binop) throws EmptyException, Throwable {

        Representation vOps = PyType.representationOf(v);
        PyType vType = vOps.pythonType(v);

        Representation wOps = PyType.representationOf(w);
        PyType wType = wOps.pythonType(w);

        MethodHandle slotv, slotw;

        /*
         * CPython would also test: (slotw = rbinop.handle(wtype)) ==
         * slotv as an optimisation , but that's never the case since we
         * use distinct binop and rbinop slots.
         */
        if (wType == vType) {
            // Same types so only try the binop slot
            slotv = binop.handle(vOps);
            return slotv.invokeExact(v, w);

        } else if (!wType.isSubTypeOf(vType)) {
            // Ask left (if not empty) then right.
            slotv = binop.handle(vOps);
            if (slotv != BINARY_EMPTY) {
                Object r = slotv.invokeExact(v, w);
                if (r != Py.NotImplemented) { return r; }
            }
            slotw = binop.getAltSlot(wOps);
            return slotw.invokeExact(w, v);

        } else {
            // Right is sub-class: ask first (if not empty).
            slotw = binop.getAltSlot(wOps);
            if (slotw != BINARY_EMPTY) {
                Object r = slotw.invokeExact(w, v);
                if (r != Py.NotImplemented) { return r; }
            }
            slotv = binop.handle(vOps);
            return slotv.invokeExact(v, w);
        }
    }

    private static final MethodHandle BINARY_EMPTY =
            SpecialMethod.Signature.BINARY.empty;

    /**
     * True iff the object has a slot for conversion to the index type.
     *
     * @param obj to test
     * @return whether {@code obj} has non-empty
     *     {@link SpecialMethod#op_index}
     */
    // Compare CPython PyIndex_Check in abstract.c
    protected static boolean indexCheck(Object obj) {
        return SpecialMethod.op_index
                .isDefinedFor(PyType.representationOf(obj));
    }

// /**
// * Return a Python {@code int} (or subclass) from the object
// * {@code o}. Raise {@code TypeError} if the result is not a Python
// * {@code int} subclass, or if the object {@code o} cannot be
// * interpreted as an index (it does not fill
// * {@link SpecialMethod#op_index}). This method makes no guarantee
// * about the <i>range</i> of the result.
// *
// * @param o operand
// * @return {@code o} coerced to a Python {@code int}
// * @throws PyBaseException (TypeError) if {@code o} cannot be
// * interpreted as an {@code int}
// * @throws Throwable otherwise from invoked implementations
// */
// // Compare with CPython abstract.c :: PyNumber_Index
// static Object index(Object o) throws PyBaseException, Throwable {
//
// Representation rep = PyType.representationOf(o);
// Object res;
//
// if (rep.isIntExact())
// return o;
// else {
// try {
// res = rep.op_index.invokeExact(o);
// // Enforce expectations on the return type
// Representation resOps = PyType.representationOf(res);
// if (resOps.isIntExact())
// return res;
// else if (resOps.pythonType(res).isSubTypeOf(PyLong.TYPE))
// return returnDeprecation("__index__", "int", res);
// else
// throw returnTypeError("__index__", "int", res);
// } catch (EmptyException e) {
// throw typeError(CANNOT_INTERPRET_AS_INT, o);
// }
// }
// }

// /**
// * Returns {@code o} converted to a Java {@code int} if {@code o}
// * can be interpreted as an integer. If the call fails, an exception
// * is raised, which may be a {@link PyBaseException TypeError} or
// * anything thrown by {@code o}'s implementation of
// * {@code __index__}. In the special case of {@link OverflowError},
// * a replacement may be made where the message is formulated by this
// * method and the type of exception by the caller. (Arcane, but it's
// * what CPython does.) A recommended idiom for this is<pre>
// * int k = PyNumber.asSize(key, IndexError::new);
// * </pre>
// *
// * @param o the object to convert to an {@code int}
// * @param exc {@code null} or function of {@code String} returning
// * the exception to use for overflow.
// * @return {@code int} value of {@code o}
// * @throws PyBaseException (TypeError) if {@code o} cannot be
// * converted to a Python {@code int}
// * @throws Throwable on other errors
// */
// // Compare with CPython abstract.c :: PyNumber_AsSsize_t
// static int asSize(Object o, Function<String, PyBaseException> exc)
// throws PyBaseException, Throwable {
//
// // Convert to Python int or sub-class. (May raise TypeError.)
// Object value = PyNumber.index(o);
//
// try {
// // We're done if PyLong.asSize() returns without error.
// return PyLong.asSize(value);
// } catch (OverflowError e) {
// // Caller may replace overflow with own type of exception
// if (exc == null) {
// // No handler: default clipping is sufficient.
// assert PyType.of(value).isSubTypeOf(PyLong.TYPE);
// if (PyLong.signum(value) < 0)
// return Integer.MIN_VALUE;
// else
// return Integer.MAX_VALUE;
// } else {
// // Throw an exception of the caller's preferred type.
// String msg = String.format(CANNOT_FIT,
// PyType.of(o).getName());
// throw exc.apply(msg);
// }
// }
// }
//
// /**
// * Extract a slice index from a Python {@code int} or an object
// * defining {@code __index__}, and return it as a Java {@code int}.
// * So that the call need not be guarded by {@code v!=Py.None}, which
// * is a common occurrence in the contexts where it is used, we
// * special-case {@code None} to return a supplied default value. We
// * silently reduce values larger than {@link Integer#MAX_VALUE} to
// * {@code Integer.MAX_VALUE}, and silently boost values less than
// * {@link Integer#MIN_VALUE} to {@code Integer.MIN_VALUE}.
// *
// * @param v to convert
// * @param defaultValue to return when {@code v==Py.None}
// * @return normalised value as a Java {@code int}
// * @throws PyBaseException (TypeError) if {@code v!=None} has no
// * {@code __index__}
// * @throws Throwable from the implementation of {@code __index__}
// */
// // Compare CPython _PyEval_SliceIndex in eval.c and where called
// static int sliceIndex(Object v, int defaultValue)
// throws PyBaseException, Throwable {
// if (v == Py.None) {
// return defaultValue;
// } else {
// if (PyNumber.indexCheck(v)) {
// return asSize(v, null);
// } else {
// throw PyErr.format(PyExc.TypeError,
// "slice indices must be integers or "
// + "None or have an __index__ method");
// }
// }
// }
//
// /**
// * Returns the {@code o} converted to an integer object. This is the
// * equivalent of the Python expression {@code int(o)}. It will refer
// * to the {@code __int__}, {@code __index_} and {@code __trunc__}
// * special methods of {@code o}, in that order, and then (if
// * {@code o} is string or bytes-like) attempt a conversion from text
// * assuming decimal base.
// *
// * @param o operand
// * @return {@code int(o)}
// * @throws PyBaseException (TypeError) if {@code o} cannot be
// * converted to a Python {@code int}
// * @throws Throwable on other errors
// */
// // Compare with CPython abstract.h :: PyNumber_Long
// static Object asLong(Object o) throws PyBaseException, Throwable {
// Object result;
// PyType oType = PyType.of(o);
//
// if (oType == PyLong.TYPE) {
// // Fast path for the case that we already have an int.
// return o;
// }
//
// else if (SpecialMethod.op_int.isDefinedFor(oType)) {
// // XXX Need test of intiness and indexiness?
// // Normalise away subclasses of int
// result = PyLong.fromIntOf(o);
// return PyLong.from(result);
// }
//
// else if (SpecialMethod.op_index.isDefinedFor(oType)) {
// // Normalise away subclasses of int
// result = PyLong.fromIndexOrIntOf(o);
// return PyLong.from(result);
// }
//
// // XXX Not implemented: else try the __trunc__ method
//
// if (PyUnicode.TYPE.check(o))
// return PyLong.fromUnicode(o, 10);
//
// // else if ... support for bytes-like objects
// else
// throw argumentTypeError("int", 0,
// "a string, a bytes-like object or a number", o);
// }

    private static final String CANNOT_INTERPRET_AS_INT =
            "'%.200s' object cannot be interpreted as an integer";
    private static final String CANNOT_FIT =
            "cannot fit '%.200s' into an index-sized integer";

// /**
// * Convert an object to a Python {@code float}, This is the
// * equivalent of the Python expression {@code float(o)}.
// *
// * @param o to convert
// * @return converted value
// * @throws PyBaseException (TypeError) if {@code __float__} is
// * defined but does nor return a {@code float}
// * @throws Throwable on other errors
// */
// // Compare CPython abstract.c: PyNumber_Float
// public static Object toFloat(Object o)
// throws PyBaseException, Throwable {
// /*
// * Ever so similar to PyFloat.asDouble, but returns always
// * exactly a PyFloat, constructed if necessary from the value in
// * a sub-type of PyFloat, or a from string.
// */
// Representation rep = PyType.representationOf(o);
//
// if (PyFloat.TYPE.checkExact(o)) {
// return o;
//
// } else {
// try {
// // Try __float__ (if defined)
// Object res = rep.op_float.invokeExact(o);
// PyType resType = PyType.of(res);
// if (resType == PyFloat.TYPE) // Exact type
// return PyFloat.doubleValue(res);
// else if (resType.isSubTypeOf(PyFloat.TYPE)) {
// // Warn about this and make a clean Python float
// PyFloat.asDouble(returnDeprecation("__float__",
// "float", res));
// } else
// // SpecialMethod defined but not a Python float at
// // all
// throw returnTypeError("__float__", "float", res);
// } catch (EmptyException e) {}
//
// // Fall out here if op_float was not defined
// if (SpecialMethod.op_index.isDefinedFor(rep))
// return PyLong.asDouble(PyNumber.index(o));
// else
// return PyFloat.fromString(o);
// }
// }
}
