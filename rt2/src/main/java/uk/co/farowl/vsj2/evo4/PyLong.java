package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/** The Python {@code int} object. */
class PyLong implements PyObject {

    /** The type {@code int}. */
    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", PyLong.class,
                    MethodHandles.lookup()));

    static PyLong ZERO = new PyLong(BigInteger.ZERO);
    static PyLong ONE = new PyLong(BigInteger.ONE);

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
    PyLong(BigInteger value) { this(TYPE, value); }

    /**
     * Construct a Python {@code int} from a Java {@code long}.
     *
     * @param value of the {@code int}
     */
    PyLong(long value) { this(BigInteger.valueOf(value)); }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    /**
     * Value as a Java {@code int}.
     *
     * @return value as Java {@code int}
     * @throws OverflowError if out of Java {@code int} range
     */
    int intValue() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE, "int");
        }
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

    private static String INT_TOO_LARGE_FLOAT =
            "Python int too large to convert to float";

    int signum() { return value.signum(); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyLong) {
            PyLong other = (PyLong) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    protected static PyObject __new__(PyType type, PyTuple args,
            PyDict kwargs) throws Throwable {
        PyObject x = null, obase = null;
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

    protected PyObject __repr__() { return Py.str(value.toString()); }

    protected PyObject __neg__() { return new PyLong(value.negate()); }

    protected PyObject __abs__() { return new PyLong(value.abs()); }

    protected PyObject __add__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.add(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __radd__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.add(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __sub__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.subtract(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __rsub__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.subtract(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __mul__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.multiply(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __rmul__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.multiply(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __and__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.and(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __rand__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.and(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __or__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.or(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __ror__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.or(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __xor__(PyObject w) {
        if (w instanceof PyLong)
            return new PyLong(value.xor(((PyLong) w).value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __rxor__(PyObject v) {
        if (v instanceof PyLong)
            return new PyLong(((PyLong) v).value.xor(value));
        else
            return Py.NotImplemented;
    }

    protected PyObject __lt__(PyObject w) {
        return cmp(w, Comparison.LT);
    }

    protected PyObject __le__(PyObject w) {
        return cmp(w, Comparison.LE);
    }

    protected PyObject __eq__(PyObject w) {
        return cmp(w, Comparison.EQ);
    }

    protected PyObject __ne__(PyObject w) {
        return cmp(w, Comparison.NE);
    }

    protected PyObject __gt__(PyObject w) {
        return cmp(w, Comparison.GT);
    }

    protected PyObject __ge__(PyObject w) {
        return cmp(w, Comparison.GE);
    }

    protected boolean __bool__() {
        return !BigInteger.ZERO.equals(value);
    }

    protected PyObject __index__() {
        if (getType() == TYPE)
            return this;
        else
            return new PyLong(value);
    }

    protected PyObject __int__() { // identical to __index__
        if (getType() == TYPE)
            return this;
        else
            return new PyLong(value);
    }

    protected PyObject __float__() { // return PyFloat
        return Py.val(doubleValue());
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
    static PyObject fromIntOf(PyObject integral)
            throws TypeError, Throwable {
        PyType t = integral.getType();

        if ((t == PyLong.TYPE)) {
            // Fast path for the case that we already have an int.
            return integral;
        }

        else
            try {
                /*
                 * Convert using the op_int slot, which should return
                 * something of exact type int.
                 */
                PyObject r = (PyObject) t.op_int.invokeExact(integral);
                if (r.getType() == PyLong.TYPE) {
                    return r;
                } else if (r instanceof PyLong) {
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
     * {@link Number#index(PyObject)} after the end of the deprecation
     * period.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws TypeError if {@code integral} seems not to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c :: _PyLong_FromNbIndexOrNbInt
    static PyObject fromIndexOrIntOf(PyObject integral)
            throws TypeError, Throwable {
        PyType t = integral.getType();

        if (t == PyLong.TYPE)
            // Fast path for the case that we already have an int.
            return integral;

        try {
            // Normally, the op_index slot will do the job
            PyObject r = (PyObject) t.op_index.invokeExact(integral);
            if (r.getType() == PyLong.TYPE)
                return r;
            else if (r instanceof PyLong) {
                // 'result' not of exact type int but is a subclass
                Abstract.returnDeprecation("__index__", "int", r);
                return r;
            } else
                throw Abstract.returnTypeError("__index__", "int", r);
        } catch (EmptyException e) {}

        // We're here because op_index was empty. Try op_int.
        if (Slot.op_int.isDefinedFor(t)) {
            PyObject r = fromIntOf(integral);
            // ... but grumble about it.
            Warnings.format(DeprecationWarning.TYPE, 1,
                    "an integer is required (got type %.200s).  "
                            + "Implicit conversion to integers "
                            + "using __int__ is deprecated, and may be "
                            + "removed in a future version of Python.",
                    t.getName());
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
    static PyLong fromUnicode(PyUnicode u, int base) throws ValueError {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            return new PyLong(new BigInteger(u.toString(), base));
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
     * @throws OverflowError when this is a floating infinity
     * @throws ValueError when this is a floating NaN
     */
    // Compare CPython longobject.c :: PyLong_FromDouble
    PyLong fromDouble(double value) {
        return new PyLong(PyFloat.bigIntegerFromDouble(value));
    }

    /**
     * Construct a Python {@code int} from a Python {@code int} or
     * subclass. If the value has Python type {@code int} exactly return
     * it, otherwise construct a new instance of exactly {@code int}
     * type.
     *
     * @param value
     * @return the same value as exactly {@code PyLong}
     */
    static PyLong from(PyLong value) {
        return value.getType() == TYPE ? value : Py.val(value.value);
    }

    // plumbing ------------------------------------------------------

    /**
     * Shorthand for implementing comparisons. Note that the return type
     * is arbitrary because one may define {@code __lt__} etc. to return
     * anything.
     *
     * @param w the right-hand operand
     * @param op the type of operation
     * @return result or {@code Py.NotImplemented}
     */
    private PyObject cmp(PyObject w, Comparison op) {
        if (w instanceof PyLong) {
            return op.toBool(value.compareTo(((PyLong) w).value));
        } else {
            return Py.NotImplemented;
        }
    }

}
