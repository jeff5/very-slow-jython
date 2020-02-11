package uk.co.farowl.vsj2.evo2;

/** The Python {@code float} object. */
class PyFloat implements PyObject {

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

    /** Convert to {@code double} */
    static double valueOf(PyObject v) {
        if (v instanceof PyFloat)
            return ((PyFloat) v).value;
        else
            return ((PyLong) v).value.doubleValue();
    }
}
