package uk.co.farowl.vsj2.evo4;

import java.math.BigInteger;

/** The Python {@code float} object. */
class PyFloat extends AbstractPyObject {

    /** The type {@code float}. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("float", PyFloat.class));

    static PyFloat ZERO = new PyFloat(0.0);
    final double value;

    /** Constructor for Python sub-class specifying {@link #type}. */
    protected PyFloat(PyType type, double value) {
        super(type);
        this.value = value;
    }

    PyFloat(double value) { this(TYPE, value); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyFloat) {
            PyFloat other = (PyFloat) obj;
            return other.value == this.value;
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static PyObject __neg__(PyFloat v) { return new PyFloat(-v.value); }

    static PyObject __abs__(PyFloat v) {
        return new PyFloat(Math.abs(v.value));
    }

    static PyObject __add__(PyFloat v, PyObject w) {
        try {
            return new PyFloat(v.value + valueOf(w));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __radd__(PyFloat w, PyObject v) {
        try {
            return new PyFloat(valueOf(v) + w.value);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __sub__(PyFloat v, PyObject w) {
        try {
            return new PyFloat(v.value - valueOf(w));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rsub__(PyFloat w, PyObject v) {
        try {
            return new PyFloat(valueOf(v) - w.value);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __mul__(PyFloat v, PyObject w) {
        try {
            return new PyFloat(v.value * valueOf(w));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject __rmul__(PyFloat w, PyObject v) {
        try {
            return new PyFloat(valueOf(v) * w.value);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static boolean __bool__(PyFloat v) { return v.value != 0.0; }

    static PyObject __new__(PyType type, PyTuple args, PyDict kwargs)
            throws Throwable {
        PyObject x = null;
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
            return (PyFloat) x;
        else if (x instanceof PyUnicode)
            return new PyFloat(
                    Double.valueOf(x.toString()).doubleValue());
        else
            return Number.toFloat(x);
    }

    /** Convert to {@code double} */
    static double valueOf(PyObject v) {
        if (v instanceof PyFloat)
            return ((PyFloat) v).value;
        else
            return ((PyLong) v).value.doubleValue();
    }

    /**
     * Value as a Java {@code double}.
     *
     * @return value as double
     */
    double doubleValue() { return value; }

    /**
     * Convert the argument to a Java {@code double} value. If
     * {@code pyfloat} is not a Python {@code float} try the
     * {@code __float__()} method, then {@code __index__()}.
     *
     * @param o to convert
     * @return converted value
     * @throws Throwable
     */
    // Compare CPython floatobject.c: PyFloat_AsDouble
    static double asDouble(PyObject o) throws Throwable {
        /*
         * Ever so similar to Number.toFloat, but returns the double
         * value extracted from (potentially) a sub-type of PyFloat, and
         * does not try to convert from strings.
         */
        PyType oType = o.getType();

        if (oType == PyFloat.TYPE) {
            return ((PyFloat) o).value;

        } else {
            try {
                // Try __float__ (if defined)
                PyObject res = (PyObject) oType.nb_float.invokeExact(o);
                if (res.getType() == PyFloat.TYPE) // Exact type
                    return ((PyFloat) res).value;
                else if (res instanceof PyFloat) { // Sub-class
                    // Warn about this and make a clean PyFloat
                    Abstract.returnDeprecation("__float__", "float",
                            res);
                    return ((PyFloat) res).doubleValue();
                } else
                    // Slot defined but not a PyFloat at all
                    throw Abstract.returnTypeError("__float__", "float",
                            res);
            } catch (Slot.EmptyException e) {}

            // Fall out here if __float__ was not defined
            if (Slot.nb_index.isDefinedFor(oType))
                return Number.index(o).doubleValue();
            else
                throw Abstract.requiredTypeError("a real number",
                        oType);
        }
    }

    /**
     * Convert a string-like object to a Python {@code float}.
     */
    // Compare CPython floatobject.c :: PyFloat_FromString
    static PyFloat fromString(PyObject v) {
        // Keep it simple (a lot simpler than in CPython)
        return new PyFloat(Double.valueOf(v.toString()));
    }

    static PyObject __int__(PyFloat self) {
        return new PyLong(bigIntegerFromDouble(self.value));
    }

    /**
     * Convert to Java {@code double} to Java {@code BigInteger} by
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
}
