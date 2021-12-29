// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import static uk.co.farowl.vsj3.evo1.PyFloatMethods.toDouble;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.base.InterpreterError;
import uk.co.farowl.vsj3.evo1.stringlib.FloatFormatter;
import uk.co.farowl.vsj3.evo1.stringlib.IntegerFormatter;
import uk.co.farowl.vsj3.evo1.stringlib.InternalFormat;
import uk.co.farowl.vsj3.evo1.stringlib.InternalFormat.FormatError;
import uk.co.farowl.vsj3.evo1.stringlib.InternalFormat.FormatOverflow;
import uk.co.farowl.vsj3.evo1.stringlib.InternalFormat.Formatter;
import uk.co.farowl.vsj3.evo1.stringlib.InternalFormat.Spec;

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

    /** A constant for the Python {@code float} zero. */
    static Double ZERO = Double.valueOf(0.0);

    /** A constant for the Python {@code float} one. */
    static Double ONE = Double.valueOf(1.0);

    /** Value of this {@code float} object. */
    final double value;

    /**
     * Constructor for Python sub-class specifying {@link #type}.
     *
     * @param type actual type
     * @param value of the {@code float}
     */
    PyFloat(PyType type, double value) {
        super(type);
        this.value = value;
    }

    // Instance methods on PyFloat ------------------------------------

    @Override
    public String toString() { return Py.defaultToString(this); }

    // @Override
    // public boolean equals(Object obj) {
    // return PyDict.pythonEquals(this, obj);
    // }

    @Override
    public boolean equals(Object obj) {
        // XXX Use Dict.pythonEquals when available
        if (obj instanceof PyFloat) {
            PyFloat other = (PyFloat)obj;
            return other.value == this.value;
        } else
            // XXX should try more accepted types. Or __eq__?
            return false;
    }

    // @Override
    // public int hashCode() throws PyException {
    // return PyDict.pythonHash(this);
    // }

    // Constructor from Python ----------------------------------------

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
            return new PyFloat(TYPE,
                    Double.valueOf(x.toString()).doubleValue());
        else
            return PyNumber.toFloat(x);
    }

    // Special methods ------------------------------------------------

    @SuppressWarnings("unused")
    private static Object __repr__(Object self) {
        assert TYPE.check(self);
        try {
            // XXX not really what Python needs (awaits formatting
            // methods)
            return Double.toString(toDouble(self));
        } catch (NoConversion nc) {
            throw Abstract.impossibleArgumentError("float", self);
        }
    }

    // __str__: let object.__str__ handle it (calls __repr__)

    static Object __pow__(Object left, Object right, Object modulus) {
        try {
            if (modulus == null || modulus == Py.None) {
                return pow(toDouble(left), toDouble(right));
            } else {
                // Note that we also call __pow__ from PyLong.__pow__
                throw new TypeError(POW_3RD_ARGUMENT);
            }
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    static Object __rpow__(Object right, Object left) {
        try {
            return pow(toDouble(left), toDouble(right));
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    private static final String POW_3RD_ARGUMENT =
            "pow() 3rd argument not allowed "
                    + "unless all arguments are integers";

    // float methods ------------------------------------------------

    /*
    @ExposedMethod(doc = BuiltinDocs.float___format___doc)
     */
    @PythonMethod
    static final Object __format__(Object self, Object formatSpec) {

        String stringFormatSpec = PyUnicode.coerceToString(formatSpec,
                () -> Abstract.argumentTypeError("__format__",
                        "specification", "str", formatSpec));

        try {
            // Parse the specification
            Spec spec = InternalFormat.fromText(stringFormatSpec);

            // Get a formatter for the specification
            FloatFormatter f = new FloatFormatter2(spec);

            /*
             * Format, pad and return a result according to as the
             * specification argument.
             */
            return f.format(self).pad().getResult();

        } catch (FormatOverflow fe) {
            throw new OverflowError(fe.getMessage());
        } catch (FormatError fe) {
            throw new ValueError(fe.getMessage());
        } catch (NoConversion e) {
            throw Abstract.impossibleArgumentError(TYPE.name, self);
        }
    }


    // Non-slot API -------------------------------------------------

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
     * @throws OverflowError if v is an {@code int} out of range
     */
    static double convertToDouble(Object v)
            throws NoConversion, OverflowError {
        if (v instanceof Double)
            return ((Double)v).doubleValue();
        else if (v instanceof PyUnicode)
            return ((PyFloat)v).value;
        else
            // BigInteger, PyLong, Boolean, etc. or throw
            return PyLong.convertToDouble(v);
    }

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


    // formatter ------------------------------------------------------

    /**
     * A {@link FloatFormatter}, constructed from a {@link Spec}, with
     * specific validations for {@code int.__format__}.
     */
    private static class FloatFormatter2 extends FloatFormatter {

        /**
         * Prepare a {@link FloatFormatter2} in support of
         * {@link PyFloat#__format__(Object, Object) float.__format__}.
         *
         * @param spec a parsed PEP-3101 format specification.
         * @return a formatter ready to use.
         * @throws FormatOverflow if a value is out of range
         *  (including the
         *     precision)
         * @throws FormatError if an unsupported format character is
         *     encountered
         */
        FloatFormatter2(Spec spec) throws FormatError {
            super(validated(spec));
        }

        /**
         * Validations and defaults specific to {@code float}.
         *
         * @param spec to validate
         * @return validated spec with defaults filled
         * @throws FormatError on failure to validate
         */
        private static Spec validated(Spec spec) throws FormatError {
            String type = TYPE.name;

            switch (spec.type) {

                case 'n':
                    if (spec.grouping) {
                        throw notAllowed("Grouping", type, spec.type);
                    }
                    //$FALL-THROUGH$

                case Spec.NONE:
                case 'e':
                case 'f':
                case 'g':
                case 'E':
                case 'F':
                case 'G':
                case '%':
                    // Check for disallowed parts of the specification
                    if (spec.alternate) {
                        throw alternateFormNotAllowed(type);
                    }
                    break;

                default:
                    // The type code was not recognised
                    throw unknownFormat(spec.type, type);
            }

            /*
             * spec may be incomplete. The defaults are those commonly
             * used for numeric formats.
             */
            return spec.withDefaults(Spec.NUMERIC);
        }

        @Override
        public FloatFormatter format(Object o)
                throws NoConversion, FormatError {
            return format(convertToDouble(o));
        }
    }

    // plumbing ------------------------------------------------------

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

    private static OverflowError cannotConvertInf(String to) {
        String msg = String.format(CANNOT_CONVERT, "infinity", to);
        return new OverflowError(msg);
    }

    private static ValueError cannotConvertNaN(String to) {
        String msg = String.format(CANNOT_CONVERT, "NaN", to);
        return new ValueError(msg);
    }

    private static final String CANNOT_CONVERT =
            "cannot convert float %s to %s";

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

    /** Used as error message text for division by zero. */
    static final String DIV_ZERO = "float division by zero";
    /** Used as error message text for modulo zero. */
    static final String MOD_ZERO = "float modulo zero";

    /**
     * Convenience function to throw a {@link ZeroDivisionError} if the
     * argument is zero. (Java float arithmetic does not throw whatever
     * the arguments.)
     *
     * @param v value to check is not zero
     * @param msg for exception if {@code v==0.0}
     * @return {@code v}
     */
    static double nonzero(double v, String msg) {
        if (v == 0.0) { throw new ZeroDivisionError(msg); }
        return v;
    }

    /**
     * Convenience function to throw a {@link ZeroDivisionError} if the
     * argument is zero. (Java float arithmetic does not throw whatever
     * the arguments.)
     *
     * @param v value to check is not zero
     * @return {@code v}
     */
    static double nonzero(double v) {
        if (v == 0.0) { throw new ZeroDivisionError(DIV_ZERO); }
        return v;
    }

    /**
     * Test that two {@code double}s have the same sign.
     *
     * @param u a double
     * @param v another double
     * @return if signs equal (works for signed zeros, etc.)
     */
    private static boolean sameSign(double u, double v) {
        long uBits = Double.doubleToRawLongBits(u);
        long vBits = Double.doubleToRawLongBits(v);
        return ((uBits ^ vBits) & SIGN) == 0L;
    }

    /**
     * Inner method for {@code __floordiv__} and {@code __rfloordiv__}.
     *
     * @param x operand
     * @param y operand
     * @return {@code x//y}
     */
    static final double floordiv(double x, double y) {
        // Java and Python agree a lot of the time (after floor()).
        // Also, Java / never throws: it just returns nan or inf.
        // So we ask Java first, then adjust the answer.
        double z = x / y;
        if (Double.isFinite(z)) {
            // Finite result: only need floor ...
            if (Double.isInfinite(y) && x != 0.0 && !sameSign(x, y))
                // ... except in this messy corner case :(
                return -1.;
            return Math.floor(z);
        } else {
            // Non-finite result: Java & Python differ
            if (y == 0.) {
                throw new ZeroDivisionError(DIV_ZERO);
            } else {
                return Double.NaN;
            }
        }
    }

    /**
     * Inner method for {@code __mod__} and {@code __rmod__}.
     *
     * @param x operand
     * @param y operand
     * @return {@code x%y}
     */
    static final double mod(double x, double y) {
        // Java and Python agree a lot of the time.
        // Also, Java % never throws: it just returns nan.
        // So we ask Java first, then adjust the answer.
        double z = x % y;
        if (Double.isNaN(z)) {
            if (y == 0.) { throw new ZeroDivisionError(MOD_ZERO); }
            // Otherwise nan is fine
        } else if (!sameSign(z, y)) {
            // z is finite (and x), but only correct if signs match
            if (z == 0.) {
                z = Math.copySign(z, y);
            } else {
                z = z + y;
            }
        }
        return z;
    }

    /**
     * Inner method for {@code __divmod__} and {@code __rdivmod__}.
     *
     * @param x operand
     * @param y operand
     * @return {@code tuple} of {@code (x//y, x%y)}
     */
    static final PyTuple divmod(double x, double y) {
        // Possibly not the most efficient
        return new PyTuple(floordiv(x, y), mod(x, y));
    }

    /**
     * We received an argument that should be impossible in a correct
     * interpreter. We use this when conversion of an
     * {@code Object self} argument may theoretically fail, but we know
     * that we should only reach that point by paths that guarantee
     * {@code self`} to be some kind on {@code float}.
     *
     * @param o actual argument
     * @return exception to throw
     */
    private static InterpreterError impossible(Object o) {
        return Abstract.impossibleArgumentError("float", o);
    }
}
