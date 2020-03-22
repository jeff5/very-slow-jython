package uk.co.farowl.vsj2.evo3;

/** Runtime */
class Py {

    private static class Singleton implements PyObject {

        final PyType type;

        @Override
        public PyType getType() { return type; }
        String name;

        Singleton(String name) {
            this.name = name;
            PyType.Spec spec = new PyType.Spec(name, getClass());
            spec.flagNot(PyType.Flag.BASETYPE);
            type = PyType.fromSpec(spec);
        }

        @Override
        public String toString() { return name; }
    }

    /** Python {@code None} object. */
    static final PyObject None = new Singleton("None") {};

    /** Python {@code NotImplemented} object. */
    static final PyObject NotImplemented =
            new Singleton("NotImplemented") {};

    /** Return Python {@code int} for Java {@code long}. */
    static PyLong val(long value) {
        return new PyLong(value);
    }

    /** Return Python {@code float} for Java {@code double}. */
    static PyFloat val(double value) {
        return new PyFloat(value);
    }

    static PyBool val(boolean value) {
        return value ? Py.True : Py.False;
    }

    /** Python {@code False} object. */
    static final PyBool False = PyBool.False;

    /** Python {@code True} object. */
    static final PyBool True = PyBool.True;

    /** Return a Python {@code object}. */
    static PyBaseObject object() {
        return new PyBaseObject();
    }

    /** Return Python {@code str} for Java {@code String}. */
    static PyUnicode str(String value) {
        return new PyUnicode(value);
    }

    /** Return Python {@code bytes} for Java {@code byte[]} (copy). */
    static PyBytes bytes(byte... value) {
        return value.length == 0 ? PyBytes.EMPTY : new PyBytes(value);
    }

    /** Return Python {@code bytes} for Java {@code int[]} (copy). */
    static PyBytes bytes(int... value) {
        return value.length == 0 ? PyBytes.EMPTY : new PyBytes(value);
    }

    /** Return Python {@code tuple} for array of {@code PyObject}. */
    static PyTuple tuple(PyObject... value) {
        return value.length == 0 ? PyTuple.EMPTY : new PyTuple(value);
    }

    /** Return empty Python {@code list}. */
    static PyList list() {
        return new PyList();
    }

    /** Return empty Python {@code dict}. */
    static PyDictionary dict() {
        return new PyDictionary();
    }

}
