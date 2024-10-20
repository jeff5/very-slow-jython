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

    /** Python {@code None} object. */
    public static final PyNone None = PyNone.INSTANCE;

    /** Instances are not allowed. */
    private Py() {};

    /**
     * Return the unique numerical identity of a given Python object.
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
}
