// Copyright (c)2022 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/** Common run-time constants and constructors. */
public class Py {

    private static class Singleton implements CraftedPyObject {

        final PyType type;

        @Override
        public PyType getType() { return type; }

        String name;

        Singleton(String name) {
            this.name = name;
            type = PyType.fromSpec(
                    new PyType.Spec(name, MethodHandles.lookup())
                            .canonical(getClass())
                            .flagNot(PyType.Flag.BASETYPE));
        }

        @Override
        public String toString() {
            return name;
        }
    }

    /** Python {@code None} object. */
    public static final Object None = new Singleton("None") {};

    /** Python {@code NotImplemented} object. */
    static final Object NotImplemented =
            new Singleton("NotImplemented") {};

    /** Python {@code False} object. */
    public static final Boolean False = false;

    /** Python {@code True} object. */
    public static final Boolean True = true;

    /**
     * Return a Python {@code object}.
     *
     * @return {@code object()}
     */
    static PyBaseObject object() {
        return new PyBaseObject();
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
    public static PyTuple tuple(Object... values) {
        return PyTuple.from(values);
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
    public static PyDict dict() {
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
                return res.toString();
            } catch (Throwable e) {}

            // Even object.__str__ not working.
            String name = "";
            try {
                // Got a Python type at all?
                name = ops.type(o).name;
            } catch (Throwable e) {
                // Maybe during start-up. Fall back to Java.
                Class<?> c = o.getClass();
                if (c.isAnonymousClass())
                    name = c.getName();
                else
                    name = c.getSimpleName();
            }
            return "<" + name + " object>";
        }
    }

    /**
     * Return the unique numerical identiy of a given Python object.
     * Objects with the same id() are identical as long as both exist.
     * By implementing it here, we encapsulate the problem of qualified
     * type name and what "address" or "identity" should mean.
     *
     * @param o the object
     * @return the Python {@code id(o)}
     */
    static int id(Object o) {
        // For the time being identity means:
        return System.identityHashCode(o);
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
