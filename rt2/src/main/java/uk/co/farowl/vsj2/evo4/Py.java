package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;

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

    /** Return the Python {@code type} of an object. */
    static PyType type(PyObject object) {
        return object.getType();
    }

    /** Return Python {@code int} for Java {@code long}. */
    static PyLong val(long value) {
        return new PyLong(value);
    }

    /** Return Python {@code int} for Java {@code BigInteger}. */
    static PyLong val(BigInteger value) {
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

    /** Return Python {@code list} for array of {@code PyObject}. */
    static PyList list(PyObject... value) {
        return new PyList(value, 0, value.length);
    }

    /** Return empty Python {@code dict}. */
    static PyDict dict() {
        return new PyDict();
    }

    /** Empty (zero-length) array of {@link PyObject}. */
    static final PyObject[] EMPTY_ARRAY = new PyObject[0];

    /**
     * Convenient default toString implementation that tries __str__, if
     * defined, but always falls back to something. Use as:<pre>
     * public String toString() { return Py.defaultToString(this); }
     * </pre>
     *
     * @param o object to represent
     * @return a string representation
     */
    static String defaultToString(PyObject o) {
        if (o == null)
            return "null";
        else {
            PyType type = null;
            try {
                type = o.getType();
                MethodHandle str = type.tp_str;
                PyObject res = (PyObject) str.invoke(o);
                if (res instanceof PyUnicode)
                    return ((PyUnicode) res).toString();
            } catch (Throwable e) {}
            // Fall back on pseudo object.__str__
            String name = type != null ? type.name
                    : o.getClass().getSimpleName();
            return "<" + name + " object>";
        }
    }

    // -------------------- Interpreter ----------------------------

    static Interpreter createInterpreter() { return new Interpreter(); }

    // -------------------- Initialisation ----------------------------

    static synchronized void initialise() {}

    static synchronized void finalise() {}

}
