// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.math.BigInteger;

import uk.co.farowl.vsj4.runtime.PyUtil.NoConversion;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * The implementation of the Python {@code float} type.
 * <p>
 * We only actually need instances of this class as a base for Python
 * subclasses of {@code float}. Actual float values are represented by
 * {@code double} or {@code java.lang.Double} when boxed as an object.
 */
public class PyFloat implements WithClass {
    /** The type object {@code float}. */
    // Bootstrap type so ask the type system to resolve it.
    public static final PyType TYPE = PyType.of(0.0);

    /** Value of this Python {@code float} as a Java primitive. */
    final double value;

    /**
     * Construct from primitive.
     *
     * @param value of the {@code float}
     */
    protected PyFloat(double value) { this.value = value; }

    @Override
    public PyType getType() { return TYPE; }

    // Representations of the value -----------------------------------

    /*
     * These conversion methods are public API. They convert a Python
     * float, or a Java float-like, to a specified Java type.
     */

    /**
     * Present the value as a Java {@code double} when the argument is
     * expected to be a Python {@code float} or a sub-class of it.
     *
     * @param v claimed {@code float}
     * @return {@code double} value
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code float}
     */
    // Compare CPython floatobject.h: PyFloat_AS_DOUBLE
    public static double doubleValue(Object v) throws PyBaseException {
        if (v instanceof Double)
            return ((Double)v).doubleValue();
        else if (v instanceof PyFloat)
            return ((PyFloat)v).value;
        else
            throw Abstract.requiredTypeError("a float", v);
    }

    /**
     * Convert the argument to a Java {@code double} value. If {@code o}
     * is not a Python {@code float} try the {@code __float__()} method,
     * then {@code __index__()}.
     *
     * @param o to convert
     * @return converted value
     * @throws PyBaseException (TypeError) if o cannot be interpreted as
     *     a {@code float}
     * @throws Throwable from {@code __float__)} or {@code __index__}
     */
    // Compare CPython floatobject.c: PyFloat_AsDouble
    public static double asDouble(Object o)
            throws PyBaseException, Throwable {
        /*
         * Ever so similar to Number.toFloat, but returns the double
         * value extracted from (potentially) a sub-type of PyFloat, and
         * does not try to convert from strings.
         */

        if (TYPE.check(o)) {
            return doubleValue(o);

        } else {
            Representation rep = PyType.getRepresentation(o);
            try {
                // Try __float__ (if defined)
                Object res = rep.op_float().invokeExact(o);
                PyType resType = PyType.of(res);
                if (resType == PyFloat.TYPE) // Exact type
                    return doubleValue(res);
                else if (resType.isSubTypeOf(PyFloat.TYPE)) {
                    // Warn about this and make a clean Python float
                    PyFloat.asDouble(Abstract.returnDeprecation(
                            "__float__", "float", res));
                } else
                    /*
                     * SpecialMethod defined but did not return a Python
                     * float at all.
                     */
                    throw Abstract.returnTypeError("__float__", "float",
                            res);
            } catch (EmptyException e) {}

            // Fall out here if __float__ was not defined
            if (SpecialMethod.op_index.isDefinedFor(rep))
                return PyLong.asDouble(PyNumber.index(o));
            else
                throw Abstract.requiredTypeError("a real number", o);
        }
    }

    // special methods -----------------------------------------------

    // plumbing -------------------------------------------------------

    // Convert between float and other types (core use) ---------------

    /*
     * These conversion methods are for use internal to the core, in the
     * implementation of special functions: they may throw NoConversion
     * on failure, which must be caught by those implementations. They
     * convert a Python float, or a specific Java float-like, to a
     * specified Java type, or convert from a range of inputs to a
     * Python float.
     */

    /**
     * Convert a Python {@code float}, {@code int} or {@code bool} to a
     * Java {@code double} (or throw {@link NoConversion}). Conversion
     * from an {@code int} may overflow.
     * <p>
     * If the method throws the special non-Python exception
     * {@link NoConversion}, the caller must deal with it by throwing an
     * appropriate Python exception or taking an alternative course of
     * action. OverlowError could be allowed to propagate since it is a
     * Python exception.
     *
     * @param v to convert
     * @return converted to {@code double}
     * @throws NoConversion if v is not a {@code float}, {@code int} or
     *     {@code bool}
     * @throws PyBaseException (OverflowError) if v is an {@code int}
     *     out of range
     */
    static double convertToDouble(Object v)
            throws NoConversion, PyBaseException {
        if (v instanceof Double)
            return ((Double)v).doubleValue();
        else if (v instanceof PyUnicode)
            return ((PyFloat)v).value;
        else
            // BigInteger, PyLong, Boolean, etc. or throw
            return PyLong.convertToDouble(v);
    }

    /**
     * Convert a string-like object to a Python {@code float}.
     *
     * @param v to convert
     * @return converted value
     */
    // Compare CPython floatobject.c :: PyFloat_FromString
    static Double fromString(Object v) {
        // Keep it simple (a lot simpler than in CPython)
        return Double.valueOf(v.toString());
    }

    /**
     * Convert a Java {@code double} to Java {@code BigInteger} by
     * truncation.
     *
     * @param value to convert
     * @return BigInteger equivalent.
     * @throws PyBaseException (OverflowError) when this is a floating
     *     infinity
     * @throws PyBaseException (ValueError) when this is a floating NaN
     */
    // Somewhat like CPython longobject.c :: PyLong_FromDouble
    static BigInteger bigIntegerFromDouble(double value)
            throws PyBaseException {

        long raw = Double.doubleToRawLongBits(value);
        long e = (raw & EXPONENT) >>> SIGNIFICAND_BITS;
        int exponent = ((int)e) - EXPONENT_BIAS;

        if (exponent < 63)
            // Give the job to the hardware.
            return BigInteger.valueOf((long)value);

        else if (exponent > 1023) {
            // raw exponent was 0x7ff
            if ((raw & SIGNIFICAND) == 0)
                throw cannotConvertInf("integer");
            else
                throw cannotConvertNaN("integer");

        } else {
            // Get the signed version of the significand
            long significand = IMPLIED_ONE | raw & SIGNIFICAND;
            long v = (raw & SIGN) == 0L ? significand : -significand;
            // Shift (left or right) according to the exponent
            return BigInteger.valueOf(v)
                    .shiftLeft(exponent - SIGNIFICAND_BITS);
        }
    }

    // IEE-754 64-bit floating point parameters
    private static final int SIGNIFICAND_BITS = 52; // exc. implied 1
    private static final int EXPONENT_BITS = 11;
    private static final int EXPONENT_BIAS = 1023;

    // Masks derived from the 64-bit floating point parameters
    private static final long IMPLIED_ONE = 1L << SIGNIFICAND_BITS;
    // = 0x0010000000000000L
    private static final long SIGNIFICAND = IMPLIED_ONE - 1;
    // = 0x000fffffffffffffL
    private static final long SIGN = IMPLIED_ONE << EXPONENT_BITS;
    // = 0x8000000000000000L;
    private static final long EXPONENT = SIGN - IMPLIED_ONE;
    // = 0x7ff0000000000000L;

    private static PyBaseException cannotConvertInf(String to) {
        String msg = String.format(CANNOT_CONVERT, "infinity", to);
        return PyErr.format(PyExc.OverflowError, msg);
    }

    private static PyBaseException cannotConvertNaN(String to) {
        String msg = String.format(CANNOT_CONVERT, "NaN", to);
        return PyErr.format(PyExc.ValueError, msg);
    }

    private static final String CANNOT_CONVERT =
            "cannot convert float %s to %s";
}
