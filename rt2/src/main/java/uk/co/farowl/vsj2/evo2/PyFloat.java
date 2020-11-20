package uk.co.farowl.vsj2.evo2;

/** The Python {@code float} object. */
class PyFloat implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("float", PyFloat.class);

    @Override
    public PyType getType() { return TYPE; }
    final double value;

    PyFloat(double value) { this.value = value; }

    @Override
    public String toString() { return Double.toString(value); }

    @Override

    public boolean equals(Object obj) {
        if (obj instanceof PyFloat) {
            PyFloat other = (PyFloat) obj;
            return other.value == this.value;
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static PyObject neg(PyObject v) {
        try {
            double a = ((PyFloat) v).value;
            return new PyFloat(-a);
        } catch (ClassCastException cce) {
            // Impossible: throw InterpreterError or EmptyException?
            return Py.NotImplemented;
        }
    }

    static PyObject add(PyObject v, PyObject w) {
        try {
            double a = valueOf(v);
            double b = valueOf(w);
            return new PyFloat(a + b);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject sub(PyObject v, PyObject w) {
        try {
            double a = valueOf(v);
            double b = valueOf(w);
            return new PyFloat(a - b);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject mul(PyObject v, PyObject w) {
        try {
            double a = valueOf(v);
            double b = valueOf(w);
            return new PyFloat(a * b);
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static boolean bool(PyObject v) {
        double a = valueOrError(v);
        return a != 0.0;
    }

    /** Convert to {@code double}  */
    private static double valueOf(PyObject v) {
        if (v instanceof PyFloat)
            return ((PyFloat) v).value;
        else
            return ((PyLong) v).value.doubleValue();
    }

    /**
     * Check the argument is a {@code PyFloat} and return its value, or
     * raise internal error.
     *
     * @param v ought to be a {@code PyFloat} (or sub-class)
     * @return the {@link #value} field of {@code v}
     * @throws InterpreterError if {@code v} is not compatible
     */
    private static double valueOrError(PyObject v)
            throws InterpreterError {
        try {
            return ((PyFloat) v).value;
        } catch (ClassCastException cce) {
            throw PyObjectUtil.typeMismatch(v, TYPE);
        }
    }
}
