package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.Slot.EmptyException;

/** The Python {@code int} object. */
class PyLong implements CraftedType, PyDict.Key {

    /** The type {@code int}. */
    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", MethodHandles.lookup())
                    .adopt(BigInteger.class, Integer.class)
                    .accept(Boolean.class) //
                    .methods(PyLongMethods.class)
                    .binops(PyLongBinops.class));

    static Integer ZERO = 0;
    static Integer ONE = 1;

    private final PyType type;
    final BigInteger value;

    /**
     * Constructor for Python sub-class specifying {@link #type}.
     *
     * @param type actual Python sub-class being created
     * @param value of the {@code int}
     */
    PyLong(PyType type, BigInteger value) {
        this.type = type;
        this.value = value;
    }

    /**
     * Construct a Python {@code int} from a {@code BigInteger}.
     *
     * @param value of the {@code int}
     */
    // XXX not needed?
    PyLong(BigInteger value) {
        this(TYPE, value);
    }

    /**
     * Construct a Python {@code int} from a Java {@code long}.
     *
     * @param value of the {@code int}
     */
    // XXX not needed?
    PyLong(long value) {
        this(BigInteger.valueOf(value));
    }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() {
        return Py.defaultToString(this);
    }

    /**
     * Present the value as a Java {@code int} when the argument is
     * expected to be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code int} value
     * @throws TypeError if {@code v} is not a Python {@code int}
     * @throws OverflowError if {@code v} is out of Java range
     */
    static int asInt(Object v) throws TypeError, OverflowError {
        try {
            return convertToInt(v);
        } catch (NoConversion nc) {
            throw Abstract.requiredTypeError("an integer", v);
        }
    }

    /**
     * Present the value as a Java {@code int} when the argument is
     * expected to be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code int} value
     * @throws TypeError if {@code v} is not a Python {@code int}
     * @throws OverflowError if {@code v} is out of Java range
     */
    static int asSize(Object v) throws TypeError, OverflowError {
        return asInt(v);
    }

    /**
     * Present the value as a Java {@code BigInteger} when the argument
     * is expected to be a Python {@code int} or a sub-class of it.
     *
     * @param v claimed {@code int}
     * @return {@code BigInteger} value
     * @throws TypeError if {@code v} is not a Python {@code int}
     */
    static BigInteger asBigInteger(Object v) throws TypeError {
        if (v instanceof BigInteger)
            return (BigInteger) v;
        else if (v instanceof Integer)
            return BigInteger.valueOf(((Integer) v).longValue());
        else if (v instanceof PyLong)
            return ((PyLong) v).value;
        else if (v instanceof Boolean)
            return (Boolean) v ? BigInteger.ONE : BigInteger.ZERO;
        else
            throw Abstract.requiredTypeError("an integer", v);
    }

    /**
     * Value as a Java {@code double} using the round-half-to-even rule.
     *
     * @return nearest double
     * @throws OverflowError if out of double range
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    @Deprecated // Supersede by asDouble(Object v)
    double doubleValue() throws OverflowError {
        /*
         * BigInteger.doubleValue() rounds half-to-even as required, but
         * on overflow returns ±∞ rather than throwing.
         */
        double x = value.doubleValue();
        if (Double.isInfinite(x))
            throw new OverflowError(INT_TOO_LARGE_FLOAT);
        else
            return x;
    }

    /**
     * Value as a Java {@code double} using the round-half-to-even rule.
     *
     * @return nearest double
     * @throws OverflowError if out of double range
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    static double asDouble(Object v) {
        try {
            return convertToDouble(v);
        } catch (NoConversion nc) {
            throw Abstract.requiredTypeError("an integer", v);
        }
    }

    @Deprecated
    private static String INT_TOO_LARGE_FLOAT =
            "Python int too large to convert to float";

    // XXX re-think as static
    int signum() {
        return value.signum();
    }

    // special methods ------------------------------------------------

    @SuppressWarnings("unused")
    private static Object __repr__(Object self) {
        assert TYPE.check(self);
        return asBigInteger(self).toString();
    }

    protected static Object __new__(PyType type, PyTuple args,
            PyDict kwargs) throws Throwable {
        Object x = null, obase = null;
        int argsLen = args.size();
        switch (argsLen) {
            case 2:
                obase = args.get(1); // fall through
            case 1:
                x = args.get(0); // fall through
            case 0:
                break;
            default:
                throw new TypeError(
                        "int() takes at most %d arguments (%d given)",
                        2, argsLen);
        }

        // XXX This does not yet deal correctly with the type argument

        if (x == null) {
            // Zero-arg int() ... unless invalidly like int(base=10)
            if (obase != null) {
                throw new TypeError("int() missing string argument");
            }
            return ZERO;
        }

        if (obase == null)
            return Number.asLong(x);
        else {
            int base = Number.asSize(obase, null);
            if ((base != 0 && base < 2) || base > 36)
                throw new ValueError(
                        "int() base must be >= 2 and <= 36, or 0");
            else if (PyUnicode.TYPE.check(x))
                return PyLong.fromUnicode(x, base);
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

    private static final String NON_STR_EXPLICIT_BASE =
            "int() can't convert non-string with explicit base";

    @SuppressWarnings("unused")
    private static Object __float__(Integer self) {
        return self.doubleValue();
    }

    @SuppressWarnings("unused")
    private static Object __float__(BigInteger self)
            throws OverflowError {
        return convertToDouble(self);
    }

    // Called for PyLong subclasses and Boolean
    @SuppressWarnings("unused")
    private static Object __float__(Object self) throws OverflowError {
        assert TYPE.check(self);
        try {
            return convertToDouble(self);
        } catch (NoConversion e) {
            // This should never be necessary. InterpreterError?
            throw Abstract.requiredTypeError("an integer", self);
        }
    }

    // Non-slot API -------------------------------------------------

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code op_int} slot, if available. Raise {@code TypeError} if
     * either the {@code op_int} slot is not available or the result of
     * the call to {@code op_int} returns something not of type
     * {@code int}.
     * <p>
     * The return is not always exactly an {@code int}.
     * {@code integral.__int__}, which this method wraps, may return any
     * type: Python sub-classes of {@code int} are tolerated, but with a
     * deprecation warning. Returns not even a sub-class type
     * {@code int} raise {@link TypeError}.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws TypeError if {@code integral} seems not to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c::_PyLong_FromNbInt
    static Object fromIntOf(Object integral)
            throws TypeError, Throwable {
        Operations ops = Operations.of(integral);;

        if (ops.isIntExact()) {
            // Fast path for the case that we already have an int.
            return integral;
        }

        else
            try {
                /*
                 * Convert using the op_int slot, which should return
                 * something of exact type int.
                 */
                Object r = ops.op_int.invokeExact(integral);
                if (PyLong.TYPE.checkExact(r)) {
                    return r;
                } else if (PyLong.TYPE.check(r)) {
                    // Result not of exact type int but is a subclass
                    Abstract.returnDeprecation("__int__", "int", r);
                    return r;
                } else
                    throw Abstract.returnTypeError("__int__", "int", r);
            } catch (EmptyException e) {
                // Slot __int__ is not defioned for t
                throw Abstract.requiredTypeError("an integer",
                        integral);
            }
    }

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code op_index} or {@code op_int} slots, if available (the
     * latter is deprecated).
     * <p>
     * The return is not always exactly an {@code int}.
     * {@code integral.__index__} or {@code integral.__int__}, which
     * this method wraps, may return any type: Python sub-classes of
     * {@code int} are tolerated, but with a deprecation warning.
     * Returns not even a sub-class type {@code int} raise
     * {@link TypeError}. This method should be replaced with
     * {@link Number#index(Object)} after the end of the deprecation
     * period.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws TypeError if {@code integral} seems not to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c :: _PyLong_FromNbIndexOrNbInt
    static Object fromIndexOrIntOf(Object integral)
            throws TypeError, Throwable {
        Operations ops = Operations.of(integral);;

        if (ops.isIntExact())
            // Fast path for the case that we already have an int.
            return integral;

        try {
            // Normally, the op_index slot will do the job
            Object r = ops.op_index.invokeExact(integral);
            if (Operations.of(r).isIntExact())
                return r;
            else if (PyLong.TYPE.check(r)) {
                // 'result' not of exact type int but is a subclass
                Abstract.returnDeprecation("__index__", "int", r);
                return r;
            } else
                throw Abstract.returnTypeError("__index__", "int", r);
        } catch (EmptyException e) {}

        // We're here because op_index was empty. Try op_int.
        if (Slot.op_int.isDefinedFor(ops)) {
            Object r = fromIntOf(integral);
            // ... but grumble about it.
            Warnings.format(DeprecationWarning.TYPE, 1,
                    "an integer is required (got type %.200s).  "
                            + "Implicit conversion to integers "
                            + "using __int__ is deprecated, and may be "
                            + "removed in a future version of Python.",
                    ops.type(integral).name);
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
     * @throws ValueError if {@code u} is an invalid literal
     * @throws TypeError if {@code u} is not a Python {@code str}
     */
    // Compare CPython longobject.c :: PyLong_FromUnicodeObject
    static BigInteger fromUnicode(Object u, int base)
            throws ValueError, TypeError {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            String value = PyUnicode.asString(u);
            return new BigInteger(value, base);
        } catch (NumberFormatException e) {
            throw new ValueError(
                    "invalid literal for int() with base %d: %.200s",
                    base, u);
        }
    }

    /**
     * Create a Python {@code int} from a Java {@code double}.
     *
     * @param value to convert
     * @return BigInteger equivalent.
     * @throws OverflowError when {@code value} is a floating infinity
     * @throws ValueError when {@code value} is a floating NaN
     */
    // Compare CPython longobject.c :: PyLong_FromDouble
    static BigInteger fromDouble(double value) {
        return PyFloat.bigIntegerFromDouble(value);
    }

    /**
     * Return a Python {@code int} from a Python {@code int} or
     * subclass. If the value has exactly Python type {@code int} return
     * it, otherwise construct a new instance of exactly {@code int}
     * type.
     *
     * @param value to represent
     * @return the same value as exactly {@code int}
     * @throws TypeError if not a Python {@code int} or sub-class
     */
    static Object from(Object value) throws TypeError {
        Operations ops = Operations.of(value);
        if (ops.isIntExact())
            return value;
        else if (value instanceof PyLong)
            return ((PyLong) value).value;
        else
            throw Abstract.requiredTypeError("an integer", value);
    }

    // PyDict.Key interface ------------------------------------------

    @Override
    public boolean equals(Object obj) {
        return PyDict.pythonEquals(this, obj);
    }

    @Override
    public int hashCode() throws PyException {
        // XXX or return value.hashCode() if not a sub-class?
        return PyDict.pythonHash(this);
    }

    // plumbing ------------------------------------------------------

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
     * @throws OverflowError v is too large to be a {@code float}
     */
    // Compare CPython longobject.c: PyLong_AsDouble
    static double convertToDouble(Object v)
            throws NoConversion, OverflowError {
        // Check against supported types, most likely first
        if (v instanceof Integer)
            // No loss of precision
            return ((Integer) v).doubleValue();
        else if (v instanceof BigInteger)
            // Round half-to-even
            return convertToDouble((BigInteger) v);
        else if (v instanceof PyLong)
            // Round half-to-even
            return convertToDouble(((PyLong) v).value);
        else if (v instanceof Boolean)
            return (Boolean) v ? 1.0 : 0.0;
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Convert a {@code BigInteger} to a Java double , using the
     * round-half-to-even rule. Conversion to a double may overflow,
     * raising an exception that is propagated to the caller.
     *
     * @param v to convert
     * @return converted to {@code double}
     * @throws OverflowError if too large to be a {@code float}
     */
    static double convertToDouble(BigInteger v) throws OverflowError {
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
     * @throws OverflowError v is too large to be a Java {@code int}
     */
    // Compare CPython longobject.c: PyLong_AsSsize_t
    static int convertToInt(Object v)
            throws NoConversion, OverflowError {
        // Check against supported types, most likely first
        if (v instanceof Integer)
            return ((Integer) v).intValue();
        else if (v instanceof BigInteger)
            return convertToInt((BigInteger) v);
        else if (v instanceof PyLong)
            return convertToInt(((PyLong) v).value);
        else if (v instanceof Boolean)
            return (Boolean) v ? 1 : 0;
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Convert a {@code BigInteger} to a Java {@code int}. Conversion to
     * an {@code int} may overflow, raising an exception that is
     * propagated to the caller.
     *
     * @param v to convert
     * @return converted to {@code int}
     * @throws OverflowError if too large to be a Java {@code int}
     */
    static int convertToInt(BigInteger v) throws OverflowError {
        if (v.bitLength() < 32)
            return v.intValue();
        else
            throw tooLarge("Python int", "int");
    }

    private static OverflowError tooLarge(String from, String to) {
        String msg = String.format(TOO_LARGE, from, to);
        return new OverflowError(msg);
    }

    private static final String TOO_LARGE =
            "%s too large to convert to %s";

}
