package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

/** The Python {@code float} object. */
public class PyFloat extends AbstractPyObject {
    /** The type {@code float}. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("float", MethodHandles.lookup())
                    .adopt(Double.class)
                    .operand(Integer.class, BigInteger.class,
                            PyLong.class, Boolean.class)
                    .methods(PyFloatMethods.class)
                    .binops(PyFloatBinops.class));

    /** A handy constant for the Python {@code float} zero. */
    static Double ZERO = Double.valueOf(0.0);

    /** A handy constant for the Python {@code float} one. */
    static Double ONE = Double.valueOf(1.0);

    /** Value of this {@code float} object. */
    protected final double value;

    /**
     * Constructor for Python sub-class specifying {@link #type}.
     *
     * @param type actual type
     * @param value of the {@code float}
     */
    protected PyFloat(PyType type, double value) {
        super(type);
        this.value = value;
    }

    // XXX not needed
    PyFloat(double value) {
        this(TYPE, value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyFloat) {
            PyFloat other = (PyFloat) obj;
            return other.value == this.value;
        } else
            // XXX should try more accepted types. Or __eq__?
            return false;
    }

    // special methods ------------------------------------------------

    @SuppressWarnings({"unused", "fallthrough"})
    private static Object __new__(PyType type, PyTuple args,
            PyDict kwargs) throws Throwable {
        Object x = null;
        int argsLen = args.size();
        switch (argsLen) {
            case 1:
                x = args.get(0); // fall through
            case 0:
                break;
            default:
                throw new TypeError(
                        "float() takes at most %d argument (%d given)",
                        1, argsLen);
        }

        // XXX This does not yet deal correctly with the type argument

        if (x == null)
            return ZERO;
        else if (x instanceof PyFloat)
            return x;
        else if (x instanceof PyUnicode)
            return new PyFloat(
                    Double.valueOf(x.toString()).doubleValue());
        else
            return PyNumber.toFloat(x);
    }

    static Object __pow__(Object left, Object right, Object modulus) {
        try {
            if (modulus == null || modulus == Py.None) {
                return pow(convert(left), convert(right));
            } else {
                // Note that we also call __pow__ from PyLong.__pow__
                throw new TypeError(POW_3RD_ARGUMENT);
            }
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    private static final String POW_3RD_ARGUMENT =
            "pow() 3rd argument not allowed "
                    + "unless all arguments are integers";

    static Object __rpow__(Object right, Object left) {
        try {
            return pow(convert(left), convert(right));
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    // Non-slot API -------------------------------------------------

    /**
     * Present the value as a Java {@code double} when the argument is
     * expected to be a Python {@code float} or a sub-class of it.
     *
     * @param v claimed {@code float}
     * @return {@code double} value
     * @throws TypeError if {@code v} is not a Python {@code float}
     */
    // Compare CPython floatobject.h: PyFloat_AS_DOUBLE
    public static double doubleValue(Object v) throws TypeError {
        if (v instanceof Double)
            return ((Double) v).doubleValue();
        else if (v instanceof PyFloat)
            return ((PyFloat) v).value;
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
     * @throws TypeError if o cannot be interpreted as a {@code float}
     * @throws Throwable from {@code __float__)} or {@code __index__}
     */
    // Compare CPython floatobject.c: PyFloat_AsDouble
    static double asDouble(Object o) throws TypeError, Throwable {
        /*
         * Ever so similar to Number.toFloat, but returns the double
         * value extracted from (potentially) a sub-type of PyFloat, and
         * does not try to convert from strings.
         */

        if (TYPE.check(o)) {
            return doubleValue(o);

        } else {
            Operations ops = Operations.of(o);
            try {
                // Try __float__ (if defined)
                Object res = ops.op_float.invokeExact(o);
                PyType resType = PyType.of(res);
                if (resType == PyFloat.TYPE) // Exact type
                    return doubleValue(res);
                else if (resType.isSubTypeOf(PyFloat.TYPE)) {
                    // Warn about this and make a clean Python float
                    PyFloat.asDouble(Abstract.returnDeprecation(
                            "__float__", "float", res));
                } else
                    // Slot defined but not a Python float at all
                    throw Abstract.returnTypeError("__float__", "float",
                            res);
            } catch (Slot.EmptyException e) {}

            // Fall out here if __float__ was not defined
            if (Slot.op_index.isDefinedFor(ops))
                return PyLong.asDouble(PyNumber.index(o));
            else
                throw Abstract.requiredTypeError("a real number", o);
        }
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
     * @throws OverflowError when this is a floating infinity
     * @throws ValueError when this is a floating NaN
     */
    // Somewhat like CPython longobject.c :: PyLong_FromDouble
    static BigInteger bigIntegerFromDouble(double value)
            throws OverflowError, ValueError {

        long raw = Double.doubleToRawLongBits(value);
        long e = (raw & EXPONENT) >>> (SIGNIFICAND_BITS - 1);
        int exponent = ((int) e) - EXPONENT_BIAS;

        if (exponent < 63)
            // Give the job to the hardware.
            return BigInteger.valueOf((long) value);

        else if (exponent > 1023) {
            // raw exponent was 0x7ff
            if ((raw & FRACTION) == 0)
                throw cannotConvertInf("integer");
            else
                throw cannotConvertNaN("integer");

        } else {
            // Get the signed version of the significand
            long significand = IMPLIED_ONE | raw & FRACTION;
            long v = (raw & SIGN) == 0L ? significand : -significand;
            // Shift (left or right) according to the exponent
            return BigInteger.valueOf(v)
                    .shiftLeft(exponent - (SIGNIFICAND_BITS - 1));
        }
    }

    // Python sub-class -----------------------------------------------

    /**
     * Instances in Python of sub-classes of 'float', are represented in
     * Java by instances of this class.
     */
    static class Derived extends PyFloat implements DictPyObject {

        protected Derived(PyType subType, double value) {
            super(subType, value);
        }

        /** The instance dictionary {@code __dict__}. */
        protected PyDict dict = new PyDict();

        @Override
        public Map<Object, Object> getDict() { return dict; }
    }

    // plumbing ------------------------------------------------------

    // IEE-754 64-bit floating point parameters
    private static final int SIGNIFICAND_BITS = 53; // inc. implied 1
    private static final int EXPONENT_BITS = 11;
    private static final int EXPONENT_BIAS = 1023;

    // Masks derived from the 64-bit floating point parameters
    private static final long IMPLIED_ONE =
            1L << (SIGNIFICAND_BITS - 1);
    // = 0x0010000000000000L
    private static final long FRACTION = IMPLIED_ONE - 1;
    // = 0x000fffffffffffffL
    private static final long SIGN = IMPLIED_ONE << EXPONENT_BITS;
    // = 0x8000000000000000L;
    private static final long EXPONENT = SIGN - IMPLIED_ONE;
    // = 0x7ff0000000000000L;

    static OverflowError cannotConvertInf(String to) {
        String msg = String.format(CANNOT_CONVERT, "infinity", to);
        return new OverflowError(msg);
    }

    static ValueError cannotConvertNaN(String to) {
        String msg = String.format(CANNOT_CONVERT, "NaN", to);
        return new ValueError(msg);
    }

    private static final String CANNOT_CONVERT =
            "cannot convert float %s to %s";

    // XXX in PyFloatMethods. Is it well-named?
    private static double convert(Object v)
            throws NoConversion, OverflowError {
        // Check against supported types, most likely first
        if (v instanceof Double)
            return ((Double) v).doubleValue();
        else if (v instanceof PyFloat)
            return ((PyFloat) v).value;
        else
            // BigInteger, PyLong, Boolean, etc.
            // or throw PyObjectUtil.NO_CONVERSION;
            return PyLong.convertToDouble(v);
    }

    /**
     * Exponentiation with Python semantics.
     *
     * @param v base value
     * @param w exponent
     * @return {@code v ** w}
     */
    static double pow(double v, double w) {
        /*
         * This code was translated from the CPython implementation at
         * v2.7.8 by progressively removing cases that could be
         * delegated to Java. Jython differs from CPython in that where
         * C pow() overflows, Java pow() returns inf (observed on
         * Windows). This is not subject to regression tests, so we take
         * it as an allowable platform dependency. All other differences
         * in Java Math.pow() are trapped below and Python behaviour is
         * enforced.
         */
        if (w == 0) {
            // v**0 is 1, even 0**0
            return 1.0;

        } else if (Double.isNaN(v)) {
            // nan**w = nan, unless w == 0
            return Double.NaN;

        } else if (Double.isNaN(w)) {
            // v**nan = nan, unless v == 1; 1**nan = 1
            return v == 1.0 ? v : Double.NaN;

        } else if (Double.isInfinite(w)) {
            /*
             * In Java Math pow(1,inf) = pow(-1,inf) = pow(1,-inf) =
             * pow(-1,-inf) = nan, but in Python they are all 1.
             */
            if (v == 1.0 || v == -1.0) { return 1.0; }

        } else if (v == 0.0) {
            // 0**w is an error if w is negative.
            if (w < 0.0) {
                throw new ZeroDivisionError(
                        "0.0 cannot be raised to a negative power");
            }

        } else if (!Double.isInfinite(v) && v < 0.0) {
            if (w != Math.floor(w)) {
                throw new ValueError(
                        "negative number cannot be raised to a fractional power");
            }
        }

        // In all other cases we can entrust the calculation to Java.
        return Math.pow(v, w);
    }
}
