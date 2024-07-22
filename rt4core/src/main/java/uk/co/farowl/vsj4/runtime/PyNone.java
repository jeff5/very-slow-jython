// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

/** The Python {@code None} object. */
public final class PyNone extends Singleton {

    /** The Python type of {@code None}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("NoneType", MethodHandles.lookup()));

    /** The only instance, published as {@link Py#None}. */
    static final PyNone INSTANCE = new PyNone();

    private PyNone() { super(TYPE, "None"); }

    // Special methods -----------------------------------------------

    @SuppressWarnings({"static-method", "unused"})
    private boolean __bool__() { return false; }
}
