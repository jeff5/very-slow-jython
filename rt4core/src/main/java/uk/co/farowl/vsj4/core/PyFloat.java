// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static uk.co.farowl.vsj4.core.Abstract.returnDeprecation;
import static uk.co.farowl.vsj4.core.Abstract.returnTypeError;
import static uk.co.farowl.vsj4.core.ClassShorthand.T;
import static uk.co.farowl.vsj4.core.PyFloatMethods.toDouble;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

import uk.co.farowl.vsj4.core.PyUtil.NoConversion;
import uk.co.farowl.vsj4.internal.EmptyException;
import uk.co.farowl.vsj4.kernel.KernelTypeFlag;
import uk.co.farowl.vsj4.kernel.Representation;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.types.Exposed;
import uk.co.farowl.vsj4.types.Feature;
import uk.co.farowl.vsj4.types.TypeSpec;
import uk.co.farowl.vsj4.types.WithClass;

/**
 * The implementation of the Python {@code float} type.
 * <p>
 * We only actually need instances of this class as a base for Python
 * subclasses of {@code float}. Actual float values are represented by
 * {@code double} or {@code java.lang.Double} when boxed as an object.
 */
public class PyFloat implements WithClass {

    // TODO Make 'float' adopt Float.class
    /** Only referenced during bootstrap by {@link TypeSystem}. */
    static class Spec {
        /** @return the type specification. */
        static TypeSpec get() {
            return new TypeSystem.BootstrapSpec("float",
                    MethodHandles.lookup(), PyFloat.class)
                            .add(Feature.BASETYPE)
                            .methodImpls(PyFloatMethods.class)
                            // .binops(PyFloatBinops.class)
                            .adopt(Double.class);
        }
    }

    /** The Python type of {@code float} objects. */
    // Bootstrap type so ask the type system to resolve it.
    public static final PyType TYPE = TypeSystem.typeOf(0.0);

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
     * expected to be a Python exactly {@code float} or a sub-class of
     * it that represents its value in the same field.
     *
     * @param v claimed {@code float}
     * @return {@code double} value
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code v} is not a Python {@code float}
     */
    // Compare CPython floatobject.h: PyFloat_AS_DOUBLE
    static double doubleValue(Object v) throws PyBaseException {
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
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if o
     *     cannot be interpreted as a {@code float}
     * @throws Throwable from {@code __float__)} or {@code __index__}
     */
    // Compare CPython floatobject.c: PyFloat_AsDouble
    public static double asDouble(Object o)
            throws PyBaseException, Throwable {
        /*
         * Ever so similar to PyNumber.toFloat, but returns the double
         * value extracted from (potentially) a sub-type of PyFloat, and
         * does not try to convert from strings.
         */
        Representation rep = Abstract.representation(o);

        if (rep.isFloatExact()) { return doubleValue(o); }

        // Try __float__ (if defined)
        try {
            Object flt = rep.op_float().invokeExact(o);
            Representation resRep = Abstract.representation(flt);
            if (!resRep.isFloatExact()) {
                PyType resType = resRep.pythonType(flt);
                if (resType.isSubTypeOf(PyFloat.TYPE))
                    // Warn about this and return value field
                    returnDeprecation("__float__", "float", flt);
                else
                    // Not a float at all.
                    throw returnTypeError("__float__", "float", flt);
            }
            return doubleValue(flt);
        } catch (EmptyException e) {}

        // o.__float__ was not defined try o.__index__
        if (rep.hasFeature(o, KernelTypeFlag.HAS_INDEX))
            return PyLong.asDouble(PyNumber.index(o));
        else
            throw Abstract.requiredTypeError("a real number", o);
    }

    // Constructor from Python ----------------------------------------

    /**
     * Create a new instance of Python {@code float}, or of a subclass.
     *
     * @param cls actual Python sub-class being created
     * @param x the value
     * @return object with that type and value
     */
    @Exposed.PythonNewMethod
    public static Object __new__(PyType cls, double x) {
        /*
         * We normally arrive here from PyType.__call__, where this/self
         * is the the type we're asked to construct, and gets passed
         * here as the 'cls' argument.
         */
        if (cls == TYPE) {
            // A basic float can be represented by a java.lang.Double
            return Double.valueOf(x);

        } else {
            /*
             * We need an instance of a Python subclass C, which means
             * creating an instance of C's Java representation.
             */
            try {
                // Look up a constructor with the right parameters
                MethodHandle cons =
                        cls.constructor(T, double.class).handle();
                return cons.invokeExact(cls, x);
            } catch (Throwable e) {
                throw PyUtil.cannotConstructInstance(cls, TYPE, e);
            }
        }
    }

    // special methods -----------------------------------------------

    // TODO: implement __format__ and (revised) stringlib
    // @SuppressWarnings("unused")
    // private static String __repr__(Object self) {
    // assert TYPE.check(self);
    // return formatDouble(doubleValue(self), SPEC_REPR);
    // }
    //
    /// ** Format specification used by repr(). */
    // private static final Spec SPEC_REPR = InternalFormat.fromText("
    // >r");

    // __str__: let object.__str__ handle it (calls __repr__)

    static Object __pow__(Object left, Object right, Object modulus) {
        try {
            if (modulus == null || modulus == Py.None) {
                return pow(toDouble(left), toDouble(right));
            } else {
                // Note that we also call __pow__ from PyLong.__pow__
                throw PyErr.format(PyExc.TypeError, POW_3RD_ARGUMENT);
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

    // TODO: implement __format__ and (revised) stringlib
    // @PythonMethod
    // static final Object __format__(Object self, Object formatSpec) {
    //
    // String stringFormatSpec = PyUnicode.asString(formatSpec,
    // o -> Abstract.argumentTypeError("__format__",
    // "specification", "str", o));
    //
    // try {
    // // Parse the specification
    // Spec spec = InternalFormat.fromText(stringFormatSpec);
    //
    // // Get a formatter for the specification
    // Formatter f = new Formatter(spec);
    //
    // /*
    // * Format, pad and return a result according to as the
    // * specification argument.
    // */
    // return f.format(self).pad().getResult();
    //
    // } catch (FormatOverflow fe) {
    // throw PyErr.format(PyExc.OverflowError, fe.getMessage());
    // } catch (FormatError fe) {
    // throw PyErr.format(PyExc.ValueError, fe.getMessage());
    // } catch (NoConversion e) {
    // throw Abstract.impossibleArgumentError(TYPE.name, self);
    // }
    // }
    //
    /// **
    // * Format this float according to the specification passed in.
    // * Supports {@code __format__}, {@code __str__} and
    // * {@code __repr__}.
    // *
    // * @param value to format
    // * @param spec parsed format specification string
    // * @return formatted value
    // */
    // private static String formatDouble(double value, Spec spec) {
    // try {
    // FloatFormatter f = new Formatter(spec, true);
    // return f.format(value).getResult();
    // } catch (FormatOverflow fe) {
    // throw PyErr.format(PyExc.OverflowError, fe.getMessage());
    // } catch (FormatError fe) {
    // throw PyErr.format(PyExc.ValueError, fe.getMessage());
    // }
    // }

    // formatter ------------------------------------------------------

    // TODO: implement __format__ and (revised) stringlib
    /// **
    // * A {@link Formatter}, constructed from a {@link Spec}, with
    // * specific validations for {@code int.__format__}.
    // */
    // static class Formatter extends FloatFormatter {
    //
    // /**
    // * If {@code true}, give {@code printf}-style meanings to
    // * {@link Spec#type}.
    // */
    // final boolean printf;
    //
    // /**
    // * Prepare a {@link Formatter} in support of
    // * {@code str.__mod__}, that is, traditional
    // * {@code printf}-style formatting.
    // *
    // * @param spec a parsed format specification.
    // * @param printf f {@code true}, interpret {@code spec}
    // * {@code printf}-style, otherwise as
    // * {@link Formatter#Formatter(Spec) Formatter(Spec)}
    // * @throws FormatOverflow if a value is out of range (including
    // * the precision)
    // * @throws FormatError if an unsupported format character is
    // * encountered
    // */
    // Formatter(Spec spec, boolean printf) throws FormatError {
    // super(validated(spec, printf));
    // this.printf = printf;
    // }
    //
    // /**
    // * Prepare a {@link Formatter} in support of
    // * {@link PyFloat#__format__(Object, Object) float.__format__}.
    // *
    // * @param spec a parsed PEP-3101 format specification.
    // * @throws FormatOverflow if a value is out of range (including
    // * the precision)
    // * @throws FormatError if an unsupported format character is
    // * encountered
    // */
    // Formatter(Spec spec) throws FormatError {
    // this(spec, false);
    // }
    //
    // /**
    // * Validations and defaults specific to {@code float}.
    // *
    // * @param spec to validate
    // * @return validated spec with defaults filled
    // * @throws FormatError on failure to validate
    // */
    // private static Spec validated(Spec spec, boolean printf)
    // throws FormatError {
    // String type = TYPE.name;
    //
    // switch (spec.type) {
    //
    // case 'n':
    // if (spec.grouping) {
    // throw notAllowed("Grouping", type, spec.type);
    // }
    // //$FALL-THROUGH$
    //
    // case Spec.NONE:
    // case 'e':
    // case 'f':
    // case 'g':
    // case 'E':
    // case 'F':
    // case 'G':
    // case '%':
    // // Check for disallowed parts of the specification
    // if (spec.alternate) {
    // throw alternateFormNotAllowed(type);
    // }
    // break;
    //
    // case 'r':
    // case 's':
    // // Only allow for printf-style formatting
    // if (printf) { break; }
    // //$FALL-THROUGH$
    //
    // default:
    // // The type code was not recognised
    // throw unknownFormat(spec.type, type);
    // }
    //
    // /*
    // * spec may be incomplete. The defaults are those commonly
    // * used for numeric formats.
    // */
    // return spec.withDefaults(Spec.NUMERIC);
    // }
    //
    // @Override
    // public FloatFormatter format(Object o)
    // throws NoConversion, FormatError {
    // return format(convertToDouble(o));
    // }
    // }

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
     * @throws PyBaseException ({@link PyExc#OverflowError
     *     OverflowError}) if v is an {@code int} out of range
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
     * @throws PyBaseException ({@link PyExc#OverflowError
     *     OverflowError}) when this is a floating infinity
     * @throws PyBaseException ({@link PyExc#ValueError ValueError})
     *     when this is a floating NaN
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
                throw PyErr.format(PyExc.ZeroDivisionError,
                        "0.0 cannot be raised to a negative power");
            }

        } else if (!Double.isInfinite(v) && v < 0.0) {
            if (w != Math.floor(w)) {
                throw PyErr.format(PyExc.ValueError,
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
     * Convenience function to raise a {@link PyExc#ZeroDivisionError
     * ZeroDivisionError} if the argument is zero. (Java float
     * arithmetic does not throw whatever the arguments.)
     *
     * @param v value to check is not zero
     * @param msg for exception if {@code v==0.0}
     * @return {@code v}
     */
    static double nonzero(double v, String msg) {
        if (v == 0.0) {
            throw PyErr.format(PyExc.ZeroDivisionError, msg);
        }
        return v;
    }

    /**
     * Convenience function to raise a {@link PyExc#ZeroDivisionError
     * ZeroDivisionError} if the argument is zero. (Java float
     * arithmetic does not throw whatever the arguments.)
     *
     * @param v value to check is not zero
     * @return {@code v}
     */
    static double nonzero(double v) {
        if (v == 0.0) {
            throw PyErr.format(PyExc.ZeroDivisionError, DIV_ZERO);
        }
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
            nonzero(y);
            return Double.NaN;
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
            nonzero(y, MOD_ZERO);   // Otherwise nan is fine
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
        return PyTuple.of(floordiv(x, y), mod(x, y));
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
