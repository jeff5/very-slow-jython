package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.Slot.EmptyException;

/** The Python {@code int} object. */
class PyLong implements CraftedType {

    /** The type {@code int}. */
    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", MethodHandles.lookup())
                    .adopt(BigInteger.class, Integer.class)
                    .accept(Boolean.class) //
                    .methods(PyLongMethods.class)
                    .binops(PyLongBinops.class));

    static Integer ZERO = Integer.valueOf(0);
    static Integer ONE = Integer.valueOf(0);

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
     * Value as a Java {@code int}.
     *
     * @return value as Java {@code int}
     * @throws OverflowError if out of Java {@code int} range
     */
    // XXX re-think as static
    int intValue() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE, "int");
        }
    }

    /**
     * Present the value as a Java {@code int} when the argument is
     * known to be a Python {@code float} or a sub-class of it.
     *
     * @param v claimed {@code float}
     * @return {@code double} valkue
     * @throws TypeError if {@code v} is not a Python {@code float}
     */
    // Compare CPython floatobject.h: PyFloat_AS_DOUBLE
    static double bigIntegerValue(Object v) throws TypeError {
        if (v instanceof Double)
            return ((Double) v).doubleValue();
        else if (v instanceof PyFloat)
            return ((PyFloat) v).value;
        else
            throw Abstract.requiredTypeError("a float", v);
    }

    private static String INT_TOO_LARGE =
            "Python int too large to convert to Java '%s'";

    /**
     * Value as a Java {@code int}. Same as {@link #intValue()} except
     * for the error message.
     *
     * @return value as Java {@code int}
     * @throws OverflowError if out of Java {@code int} range
     */
    // XXX re-think as static
    int asSize() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE, "size");
        }
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

    // XXX re-think as static
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyLong) {
            PyLong other = (PyLong) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }

    // special methods ------------------------------------------------

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
            else if (x instanceof PyUnicode)
                return new PyLong(new BigInteger(x.toString(), base));
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

    private static final String NON_STR_EXPLICIT_BASE =
            "int() can't convert non-string with explicit base";

//
//@formatter:off
//    protected Object __lt__(Object w) {
//        return cmp(w, Comparison.LT);
//    }
//
//    protected Object __le__(Object w) {
//        return cmp(w, Comparison.LE);
//    }
//
//    protected Object __eq__(Object w) {
//        return cmp(w, Comparison.EQ);
//    }
//
//    protected Object __ne__(Object w) {
//        return cmp(w, Comparison.NE);
//    }
//
//    protected Object __gt__(Object w) {
//        return cmp(w, Comparison.GT);
//    }
//
//    protected Object __ge__(Object w) {
//        return cmp(w, Comparison.GE);
//    }
//
//    protected Object __index__() {
//        if (getType() == TYPE)
//            return this;
//        else
//            return new PyLong(value);
//    }
//
//    protected Object __int__() { // identical to __index__
//        if (getType() == TYPE)
//            return this;
//        else
//            return new PyLong(value);
//    }
//@formatter:on

// protected Object __float__() { // return PyFloat
// return Py.val(doubleValue());
// }

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
     */
    // Compare CPython longobject.c :: PyLong_FromUnicodeObject
    static BigInteger fromUnicode(PyUnicode u, int base)
            throws ValueError {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            return new BigInteger(u.toString(), base);
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
    static PyLong fromDouble(double value) {
        return new PyLong(PyFloat.bigIntegerFromDouble(value));
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

    // plumbing ------------------------------------------------------

//
//@formatter:off
//    /**
//     * Shorthand for implementing comparisons. Note that the return type
//     * is arbitrary because one may define {@code __lt__} etc. to return
//     * anything.
//     *
//     * @param w the right-hand operand
//     * @param op the type of operation
//     * @return result or {@code Py.NotImplemented}
//     */
//    private Object cmp(Object w, Comparison op) {
//        if (w instanceof PyLong) {
//            return op.toBool(value.compareTo(((PyLong) w).value));
//        } else {
//            return Py.NotImplemented;
//        }
//    }
//@formatter:on

    /**
     * Convert an {@code int} to a Java double (or throw
     * {@link NoConversion}), using the round-half-to-even rule.
     * Conversion to a double may overflow, raising an exception that is
     * propagated to the caller.
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it. Generally it will convert it to an
     * alternative course of action or an appropriate Python exception.
     * Binary operations will normally return {@link Py#NotImplemented}
     * in response.
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
            return ((Integer) v).doubleValue();
        else if (v instanceof BigInteger)
            return convertToDouble((BigInteger) v);
        else if (v instanceof PyLong)
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
     * @throws OverflowError v is too large to be a {@code float}
     */
    static double convertToDouble(BigInteger v) throws OverflowError {
        double vv = v.doubleValue();
        if (Double.isFinite(vv))
            return vv;
        else
            throw tooLarge("Python int", "float");
    }

    private static OverflowError tooLarge(String from, String to) {
        String msg = String.format(TOO_LARGE, from, to);
        return new OverflowError(msg);
    }

    private static final String TOO_LARGE =
            "%s too large to convert to %s";

}
