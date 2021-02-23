package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;

/** Runtime */
public class Py {

    private static class Singleton implements CraftedType {

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
        public String toString() {
            return name;
        }
    }

    /** Python {@code None} object. */
    static final Object None = new Singleton("None") {};

    /** Python {@code NotImplemented} object. */
    static final Object NotImplemented =
            new Singleton("NotImplemented") {};

    /**
     * Return the Python {@code type} of an object.
     *
     * @param object to interrogate
     * @return the type
     */
    static PyType type(Object object) {
        return PyType.of(object);
    }

    /**
     * Return Python {@code int} for Java {@code long}.
     *
     * @param value to represent
     * @return equivalent {@code int}
     */
    public static PyLong val(long value) {
        return new PyLong(value);
    }

    /**
     * Return Python {@code int} for Java {@code BigInteger}.
     *
     * @param value to wrap
     * @return equivalent {@code int}
     */
    public static PyLong val(BigInteger value) {
        return new PyLong(value);
    }

    /**
     * Return Python {@code float} for Java {@code double}.
     *
     * @param value to represent
     * @return equivalent {@code float}
     */
    public static PyFloat val(double value) {
        return new PyFloat(value);
    }

    /**
     * Return Python {@code bool} (one of {@link #True} or
     * {@link #False}) for Java {@code boolean}.
     *
     * @param value to represent
     * @return equivalent {@code bool}
     */
    @Deprecated // Just use Boolean or primitive auto-boxed
    static Object val(boolean value) {
        return value ? Py.True : Py.False;
    }

    /** Python {@code False} object. */
    static final Object False = false;

    /** Python {@code True} object. */
    static final Object True = true;

    /**
     * Return a Python {@code object}.
     *
     * @return {@code object()}
     */
    static PyBaseObject object() {
        return new PyBaseObject();
    }

    /**
     * Return Python {@code str} for Java {@code String}.
     *
     * @param value to wrap
     * @return equivalent {@code str}
     */
    public static PyUnicode str(String value) {
        return new PyUnicode(value);
    }

// /**
// * Return Python {@code bytes} for Java {@code byte[]} (copy).
// *
// * @param values to copy
// * @return equivalent {@code bytes} object
// */
// static PyBytes bytes(byte... values) {
// return values.length == 0 ? PyBytes.EMPTY : new PyBytes(values);
// }
//
// /**
// * Return Python {@code bytes} for Java {@code int[]} (copy).
// *
// * @param values to copy
// * @return equivalent {@code bytes} object
// */
// static PyBytes bytes(int... values) {
// return values.length == 0 ? PyBytes.EMPTY : new PyBytes(values);
// }

    /**
     * Return Python {@code tuple} for array of {@code Object}.
     *
     * @param values to contain
     * @return equivalent {@code tuple} object
     */
    static PyTuple tuple(Object... values) {
        return values.length == 0 ? PyTuple.EMPTY : new PyTuple(values);
    }

// /**
// * Return empty Python {@code list}.
// *
// * @return {@code list()}
// */
// static PyList list() { return new PyList(); }
//
// /**
// * Return Python {@code list} for array of {@code Object}.
// *
// * @param values to contain
// * @return these as a {@code list}
// */
// static PyList list(Object... values) {
// return new PyList(values, 0, values.length);
// }

    /**
     * Return empty Python {@code dict}.
     *
     * @return {@code dict()}
     */
    static PyDict dict() {
        return new PyDict();
    }

    /** Empty (zero-length) array of {@link Object}. */
    static final Object[] EMPTY_ARRAY = new Object[0];

    /**
     * Convenient default toString implementation that tries __str__, if
     * defined, but always falls back to something. Use as:<pre>
     * public String toString() { return Py.defaultToString(this); }
     * </pre>
     *
     * @param o object to represent
     * @return a string representation
     */
    static String defaultToString(Object o) {
        if (o == null)
            return "null";
        else {
            Operations ops = null;
            try {
                ops = Operations.of(o);
                MethodHandle str = ops.op_str;
                Object res = str.invokeExact(o);
                if (res instanceof PyUnicode)
                    return ((PyUnicode) res).value;
            } catch (Throwable e) {}
            // Fall back on pseudo object.__str__
            String name = "";
            if (ops != null)
                name = ops.type(o).name;
            else {
                Class<?> c = o.getClass();
                if (c.isAnonymousClass())
                    name = c.getName();
                else
                    name = c.getSimpleName();
            }
            return "<" + name + " object>";
        }
    }

    // Interpreter ---------------------------------------------------

// /**
// * Create an interpreter in its default state.
// *
// * @return the interpreter
// */
// static Interpreter createInterpreter() {
// return new Interpreter();
// }

    // Initialisation ------------------------------------------------

    /** Action we might need to initialise the run-time system. */
    static synchronized void initialise() {}

    /** Action we might need to finalise the run-time system. */
    static synchronized void finalise() {}
}
