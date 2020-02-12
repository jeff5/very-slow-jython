package uk.co.farowl.vsj2.evo2;

import java.math.BigInteger;

/** The Python {@code int} object. */
class PyLong implements PyObject {

    static PyType TYPE = new PyType("int", PyLong.class);

    @Override
    public PyType getType() { return TYPE; }
    final BigInteger value;

    PyLong(BigInteger value) { this.value = value; }

    PyLong(long value) { this.value = BigInteger.valueOf(value); }

    @Override
    public String toString() { return value.toString(); }

    int asSize() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE);
        }
    }
    private static String INT_TOO_LARGE =
            "Python int too large to convert to 'size'";

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

    static PyObject neg(PyObject v) {
        try {
            BigInteger a = valueOf(v);
            return new PyLong(a.negate());
        } catch (ClassCastException cce) {
            // Impossible: throw InterpreterError or EmptyException?
            return Py.NotImplemented;
        }
    }

    static PyObject add(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.add(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject sub(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.subtract(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject mul(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.multiply(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject index(PyObject v) {
        try {
            if (v.getType() == TYPE)
                return v;
            else
                return new PyLong(valueOf(v));
        } catch (ClassCastException cce) {
            // Impossible: throw InterpreterError or EmptyException?
            return Py.NotImplemented;
        }
    }

    /**
     * Check the argument is a {@code PyLong} and return its value.
     *
     * @param v ought to be a {@code PyLong} (or sub-class)
     * @return the {@link #value} field of {@code v}
     * @throws ClassCastException if {@code v} is not compatible
     */
    private static BigInteger valueOf(PyObject v)
            throws ClassCastException {
        return ((PyLong) v).value;
    }

    /**
     * Check the argument is a {@code PyLong} and return its value, or
     * raise internal error. Differs from {@link #valueOf(PyObject)}
     * only in type of exception thrown.
     *
     * @param v ought to be a {@code PyLong} (or sub-class)
     * @return the {@link #value} field of {@code v}
     * @throws InterpreterError if {@code v} is not compatible
     */
    private static BigInteger valueOrError(PyObject v)
            throws InterpreterError {
        try {
            return ((PyLong) v).value;
        } catch (ClassCastException cce) {
            throw PyObjectUtil.typeMismatch(v, TYPE);
        }
    }
}
