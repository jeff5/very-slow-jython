// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;

import java.math.BigInteger;

import uk.co.farowl.vsj4.runtime.Exposed.Default;
import uk.co.farowl.vsj4.runtime.Exposed.DocString;
import uk.co.farowl.vsj4.runtime.Exposed.PositionalOnly;
import uk.co.farowl.vsj4.runtime.Exposed.PythonNewMethod;
import uk.co.farowl.vsj4.runtime.PyUtil.NoConversion;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.MissingFeature;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/** Placeholder until implemented. */
// FIXME implement me
public class PyLong implements WithClass {
    /** The type {@code int}. */
    // Bootstrap type so ask the type system to resolve it.
    public static final PyType TYPE = PyType.of(42);

    /** The value of this Python {@code int} (sub-class instances). */
    // Has to be package visible for method implementations to see.
    final BigInteger value;

    /**
     * Constructor for subclass use.
     *
     * @param value of the {@code int}
     */
    protected PyLong(BigInteger value) { this.value = value; }

    @Override
    public PyType getType() { return TYPE; }

    // Constructor from Python ----------------------------------------

    /**
     * @param cls actual sub-type of {@code int} to produce
     * @param x {@code int}-like or {@code str}-like value or
     *     {@code None}.
     * @param obase number base ({@code x} must be {@code str}-like) or
     *     {@code None}
     * @return an {@code int} or sub-class with the right value
     * @throws Throwable on argument type or other errors
     */
    @PythonNewMethod
    @DocString("""
            int([x]) -> integer
            int(x, base=10) -> integer

            Convert a number or string to an integer, or return 0 if no arguments
            are given.  If x is a number, return x.__int__().  For floating point
            numbers, this truncates towards zero.
            If x is not a number or if base is given, then x must be a string,
            bytes, or bytearray instance representing an integer literal in the
            given base.  The literal can be preceded by '+' or '-' and be surrounded
            by whitespace.  The base defaults to 10.  Valid bases are 0 and 2-36.
            Base 0 means interpret the base from the string as an integer literal.
            >>> int('0b100', base=0)
            4
            """)
    static Object __new__(PyType cls,
            @Default("None") @PositionalOnly Object x,
            @Default("None") Object base) throws Throwable {
        Object v = intImpl(x, base);
        if (cls == TYPE)
            return v;
        else
            // TODO Support subclass constructor
            throw new MissingFeature("subclasses in __new__");
        // return new PyLong.Derived(cls, PyLong.asBigInteger(v));
    }

    // Special methods ------------------------------------------------

    @SuppressWarnings("unused")
    static Object __repr__(Object self) {
        assert TYPE.check(self);
        return asBigInteger(self).toString();
    }

    // __str__: let object.__str__ handle it (calls __repr__)

    /**
     * Create an int from the arguments (not a sub-type).
     *
     * @param x {@code int}-like or {@code str}-like value or
     *     {@code None}.
     * @param base number base ({@code x} must be {@code str}-like) or
     *     {@code None}
     * @return an {@code int} with the right value
     * @throws Throwable on argument type or other errors
     */
    private static Object intImpl(Object x, Object base)
            throws Throwable {

        if (x == Py.None) {
            // Zero-arg int() ... unless invalidly like int(base=10)
            if (base != Py.None) {
                throw PyErr.format(PyExc.TypeError,
                        "int() missing string argument");
            }
            return 0;

        } else if (base == Py.None) {
            return PyNumber.asLong(x);

        } else {
            int b = PyNumber.asSize(base, null);
            if (b != 0 && (b < 2 || b > 36)) {
                throw PyErr.format(PyExc.ValueError,
                        "int() base must be >= 2 and <= 36, or 0");
            } else if (PyUnicode.TYPE.check(x)) {
                return PyLong.fromUnicode(x, b);
                // else if ... support for bytes-like objects
            } else {
                throw PyErr.format(PyExc.TypeError,
                        NON_STR_EXPLICIT_BASE);
            }
        }
    }

    private static final String NON_STR_EXPLICIT_BASE =
            "int() can't convert non-string with explicit base";

    // Representations of the value -----------------------------------

    /*
     * These conversion methods are public API. They convert a Python
     * int, or a specific Java implementation of int, to a specified
     * Java type.
     */

    /**
     * Present the argument as a Java {@code int} when it is expected to
     * be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code int} value
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     * @throws PyBaseException (OverflowError) if {@code v} is out of
     *     Java range
     */
    public static int asInt(Object v) throws PyBaseException {
        try {
            return convertToInt(v);
        } catch (NoConversion nc) {
            throw Abstract.requiredTypeError("an integer", v);
        }
    }

    /**
     * Present the argument as a Java {@code int} when it is expected to
     * be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code int} value
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     * @throws PyBaseException (OverflowError) if {@code v} is out of
     *     Java range
     */
    public static int asSize(Object v) throws PyBaseException {
        return asInt(v);
    }

    /**
     * Present the argument as a Java {@code BigInteger} when it is
     * expected to be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code BigInteger} value
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     */
    public static BigInteger asBigInteger(Object v)
            throws PyBaseException {
        try {
            return convertToBigInteger(v);
        } catch (NoConversion nc) {
            throw Abstract.requiredTypeError("an integer", v);
        }
    }

    /**
     * Present the argument as a Java {@code double} when it is expected
     * to be a Python {@code int} or a sub-class of it, using the
     * round-half-to-even rule.
     *
     * @param v to convert
     * @return nearest double
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     * @throws PyBaseException (OverflowError) if out of double range
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    static double asDouble(Object v) throws PyBaseException {
        try {
            return convertToDouble(v);
        } catch (NoConversion nc) {
            throw Abstract.requiredTypeError("an integer", v);
        }
    }

    /**
     * {@code signum(v)} <i>= -1, 0, 1</i> as <i>v<0</i>, <i>v=0</i>,
     * <i>v>0</i>.
     *
     * @param v of which the sign/sense is required
     * @return {@code signum(v)}
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code int}
     * @throws PyBaseException (TypeError) if not an {@code int}
     */
    static int signum(Object v) throws PyBaseException {
        if (v instanceof BigInteger)
            return ((BigInteger)v).signum();
        else if (v instanceof Integer)
            return Integer.signum((Integer)v);
        else if (v instanceof PyLong)
            return ((PyLong)v).value.signum();
        else if (v instanceof Boolean)
            return (Boolean)v ? 1 : 0;
        else
            throw Abstract.requiredTypeError("an integer", v);
    }

    // Factories ------------------------------------------------------

    /*
     * These methods create Python int from other Python objects, or
     * from specific Java types. The methods make use of special methods
     * on the argument and produce Python exceptions when that goes
     * wrong. Note that they never produce a PyLong, but always Java
     * Integer or BigInteger. The often correspond to CPython public or
     * internal API.
     */
    /**
     * Convert the given object to a Python {@code int} using the
     * {@code op_int} slot, if available. Raise {@code TypeError} if
     * either the {@code op_int} slot is not available or the result of
     * the call to {@code op_int} returns something not of type
     * {@code int}.
     * <p>
     * The return is not always exactly an {@code int}.
     * {@code integral.__int__}, which this method wraps, may return any
     * type: Python sub-classes of {@code int} are tolerated, but with a
     * deprecation warning. Returns not even a sub-class type
     * {@code int} raise {@link PyBaseException TypeError}.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws PyBaseException (TypeError) if {@code integral} seems not
     *     to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c::_PyLong_FromNbInt
    static Object fromIntOf(Object integral)
            throws PyBaseException, Throwable {
        Representation rep = PyType.getRepresentation(integral);

        if (rep.isIntExact()) {
            // Fast path for the case that we already have an int.
            return integral;

        } else {
            try {
                /*
                 * Convert using the op_int slot, which should return
                 * something of exact type int.
                 */
                Object r = rep.op_int().invokeExact(integral);
                if (PyLong.TYPE.checkExact(r)) {
                    return r;
                } else if (PyLong.TYPE.check(r)) {
                    // Result not of exact type int but is a subclass
                    Abstract.returnDeprecation("__int__", "int", r);
                    return r;
                } else
                    throw Abstract.returnTypeError("__int__", "int", r);
            } catch (EmptyException e) {
                // __int__ is not defined for t
                throw Abstract.requiredTypeError("an integer",
                        integral);
            }
        }
    }

    /**
     * Convert the given object to a {@code int} using the
     * {@code __index__} or {@code __int__} special methods, if
     * available (the latter is deprecated).
     * <p>
     * The return is not always exactly an {@code int}.
     * {@code integral.__index__} or {@code integral.__int__}, which
     * this method wraps, may return any type: Python sub-classes of
     * {@code int} are tolerated, but with a deprecation warning.
     * Returns not even a sub-class type {@code int} raise
     * {@link PyBaseException TypeError}. This method should be replaced
     * with {@link PyNumber#index(Object)} after the deprecation period.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws PyBaseException(TypeError) if {@code integral} seems not
     *     to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c :: _PyLong_FromNbIndexOrNbInt
    static Object fromIndexOrIntOf(Object integral)
            throws PyBaseException, Throwable {
        Representation ops = PyType.getRepresentation(integral);;

        if (ops.isIntExact())
            // Fast path for the case that we already have an int.
            return integral;

        try {
            // Normally, the op_index slot will do the job
            Object r = ops.op_index().invokeExact(integral);
            if (PyType.getRepresentation(r).isIntExact())
                return r;
            else if (PyLong.TYPE.check(r)) {
                // 'result' not of exact type int but is a subclass
                Abstract.returnDeprecation("__index__", "int", r);
                return r;
            } else
                throw Abstract.returnTypeError("__index__", "int", r);
        } catch (EmptyException e) {}

        // We're here because op_index was empty. Try op_int.
        if (SpecialMethod.op_int.isDefinedFor(ops)) {
            Object r = fromIntOf(integral);
            // ... but grumble about it.
            Warnings.format(PyExc.DeprecationWarning, 1,
                    "an integer is required (got type %.200s).  "
                            + "Implicit conversion to integers "
                            + "using __int__ is deprecated, and may be "
                            + "removed in a future version of Python.",
                    ops.pythonType(integral).getName());
            return r;
        } else
            throw Abstract.requiredTypeError("an integer", integral);
    }

    /**
     * Convert a sequence of Unicode digits in the string u to a Python
     * integer value.
     *
     * @param u string to convert
     * @param base in which to interpret it
     * @return converted value
     * @throws PyBaseException(TypeError) if {@code u} is an invalid
     *     literal
     * @throws PyBaseException(TypeError) if {@code u} is not a Python
     *     {@code str}
     */
    // Compare CPython longobject.c :: PyLong_FromUnicodeObject
    static BigInteger fromUnicode(Object u, int base)
            throws PyBaseException {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            String value = PyUnicode.asString(u);
            return new BigInteger(value, base);
        } catch (NumberFormatException e) {
            throw PyErr.format(PyExc.ValueError,
                    "invalid literal for int() with base %d: %.200s",
                    base, u);
        }
    }

    /**
     * Return a Python {@code int} from a Python {@code int} or
     * subclass. If the value has exactly Python type {@code int} return
     * it, otherwise construct a new instance of exactly {@code int}
     * type.
     *
     * @param value to represent
     * @return the same value as exactly {@code int}
     * @throws PyBaseException(TypeError) if not a Python {@code int} or
     *     sub-class
     */
    // Compare CPython longobject.c :: long_long
    static Object from(Object value) throws PyBaseException {
        Representation ops = PyType.getRepresentation(value);
        if (ops.isIntExact())
            return value;
        else if (value instanceof PyLong)
            return ((PyLong)value).value;
        else
            throw Abstract.requiredTypeError("an integer", value);
    }

    /**
     * Create a Python {@code int} from a Java {@code double}.
     *
     * @param value to convert
     * @return BigInteger equivalent.
     * @throws PyBaseException(TypeError) when {@code value} is a
     *     floating infinity
     * @throws PyBaseException(TypeError) when {@code value} is a
     *     floating NaN
     */
    // Compare CPython longobject.c :: PyLong_FromDouble
    static BigInteger fromDouble(double value) {
        // XXX Maybe return Object and Integer if possible
        return PyFloat.bigIntegerFromDouble(value);
    }

    // plumbing -------------------------------------------------------

    // Convert between int and other types (core use) -----------------

    /*
     * These conversion methods are for use internal to the core, in the
     * implementation of special functions: they may throw NoConversion
     * on failure, which must be caught by those implementations. They
     * convert a Python int, or a specific Java implementation of int,
     * to a specified Java type, or convert from a range of inputs to a
     * Python int.
     */

    /**
     * Convert an {@code int} to a Java {@code double} (or throw
     * {@link NoConversion}), using the round-half-to-even rule.
     * Conversion to a {@code double} may overflow, raising an exception
     * that is propagated to the caller.
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action. Binary
     * operations will normally return {@link Py#NotImplemented} in
     * response.
     *
     * @param v to convert
     * @return converted to {@code double}
     * @throws NoConversion v is not an {@code int}
     * @throws PyBaseException (OverflowError) v is too large to be a
     *     {@code float}
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    static double convertToDouble(Object v)
            throws NoConversion, PyBaseException {
        // Check against supported types, most likely first
        if (v instanceof Integer)
            // No loss of precision
            return ((Integer)v).doubleValue();
        else if (v instanceof BigInteger)
            // Round half-to-even
            return convertToDouble((BigInteger)v);
        else if (v instanceof PyLong)
            // Round half-to-even
            return convertToDouble(((PyLong)v).value);
        else if (v instanceof Boolean)
            return (Boolean)v ? 1.0 : 0.0;
        throw PyUtil.NO_CONVERSION;
    }

    /**
     * Convert a {@code BigInteger} to a Java double , using the
     * round-half-to-even rule. Conversion to a double may overflow,
     * raising an exception that is propagated to the caller.
     *
     * @param v to convert
     * @return converted to {@code double}
     * @throws PyBaseException (OverflowError) if too large to be a
     *     {@code float}
     */
    static double convertToDouble(BigInteger v) throws PyBaseException {
        /*
         * According to the code, BigInteger.doubleValue() rounds
         * half-to-even as required. This differs from conversion from
         * long which rounds to nearest (JLS 3.0 5.1.2).
         */
        double vv = v.doubleValue();
        // On overflow, doubleValue returns ±∞ rather than throwing.
        if (Double.isInfinite(vv))
            throw tooLarge("Python int", "float");
        else
            return vv;
    }

    /**
     * Convert a Python {@code int} to a Java {@code int} (or throw
     * {@link NoConversion}). Conversion to an {@code int} may overflow,
     * raising an exception that is propagated to the caller.
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action.
     *
     * @param v to convert
     * @return converted to {@code int}
     * @throws NoConversion v is not an {@code int}
     * @throws PyBaseException (OverflowError) v is too large to be a
     *     Java {@code int}
     */
    // Compare CPython longobject.c: PyLong_AsSsize_t
    static int convertToInt(Object v)
            throws NoConversion, PyBaseException {
        // Check against supported types, most likely first
        if (v instanceof Integer)
            return ((Integer)v).intValue();
        else if (v instanceof BigInteger)
            return convertToInt((BigInteger)v);
        else if (v instanceof PyLong)
            return convertToInt(((PyLong)v).value);
        else if (v instanceof Boolean)
            return (Boolean)v ? 1 : 0;
        throw PyUtil.NO_CONVERSION;
    }

    /**
     * Convert a {@code BigInteger} to a Java {@code int}. Conversion to
     * an {@code int} may overflow, raising an exception that is
     * propagated to the caller.
     *
     * @param v to convert
     * @return converted to {@code int}
     * @throws PyBaseException (OverflowError) if too large to be a Java
     *     {@code int}
     */
    static int convertToInt(BigInteger v) throws PyBaseException {
        if (v.bitLength() < 32)
            return v.intValue();
        else
            throw tooLarge("Python int", "int");
    }

    /**
     * Convert a Python {@code int} to a Java {@code BigInteger} (or
     * throw {@link NoConversion}). Conversion may raise an exception
     * that is propagated to the caller. If the Java type of the
     * {@code int} is declared, generally there is a better option than
     * this method. We only use it for {@code Object} arguments.
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action.
     *
     * @param v claimed {@code int}
     * @return converted to {@code BigInteger}
     * @throws NoConversion if {@code v} is not a Python {@code int}
     */
    static BigInteger convertToBigInteger(Object v)
            throws NoConversion {
        if (v instanceof BigInteger)
            return (BigInteger)v;
        else if (v instanceof Integer)
            return BigInteger.valueOf(((Integer)v).longValue());
        else if (v instanceof PyLong)
            return ((PyLong)v).value;
        else if (v instanceof Boolean)
            return (Boolean)v ? ONE : ZERO;
        throw PyUtil.NO_CONVERSION;
    }

    /**
     * Create an {@link PyBaseException OverflowError} with a message
     * along the lines "X too large to convert to Y", where X is
     * {@code from} and Y is {@code to}.
     *
     * @param from description of type to convert from
     * @param to description of type to convert to
     * @return an {@code OverflowError} with that message
     */
    static PyBaseException tooLarge(String from, String to) {
        String msg = String.format(TOO_LARGE, from, to);
        return PyErr.format(PyExc.OverflowError, msg);
    }

    private static final String TOO_LARGE =
            "%s too large to convert to %s";
}
