// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/** Operations on Python objects. */
public class Py {

    /** The (static singleton) registry created by PyType. */
    private static final TypeRegistry registry = PyType.registry;

    static {
        // Failure indicates type system initialisation problem.
        assert registry != null;
    }

    private Py() {};    // Instances are not allowed.

    /**
     * Return the unique numerical identity of a given Python object.
     * Objects with the same {@code id()} are identical as long as both
     * exist. By implementing it here, we encapsulate the problem of
     * qualified type name and what "address" or "identity" should mean.
     *
     * @param o the object
     * @return the Python {@code id(o)}
     */
    static int id(Object o) {
        // For the time being identity means:
        return System.identityHashCode(o);
    }

    /** Python {@code None} object. */
    public static final PyNone None = PyNone.INSTANCE;

    /** Python {@code None} object. */
    public static final PyNotImplemented NotImplemented =
            PyNotImplemented.INSTANCE;

    /**
     * Return a {@link PyTuple tuple} of the arguments (which may or may
     * not be a new object).
     *
     * @param a of the {@code tuple}
     * @return a {@link PyTuple} of the argument.
     */
    public static PyTuple tuple(Object... a) {
        return a.length == 0 ? PyTuple.EMPTY : new PyTuple(a);
    }

    /**
     * Return a new, empty {@link PyDict dict}.
     *
     * @return new, empty {@link PyDict}.
     */
    public static PyDict dict() { return new PyDict(); }

    /** The Python {@code False} singleton is {@code Boolean.FALSE}. */
    public static final Boolean False = Boolean.FALSE;
    /** The Python {@code True} singleton is {@code Boolean.TRUE}. */
    public static final Boolean True = Boolean.TRUE;
}
