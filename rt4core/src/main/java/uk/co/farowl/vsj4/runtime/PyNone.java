// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.internal.Singleton;

/** The Python {@code None} object. */
public final class PyNone extends Singleton {

    /** The Python type of {@code None}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("NoneType", MethodHandles.lookup()));

    /** The only instance, published as {@link Py#None}. */
    public static final PyNone INSTANCE = new PyNone();

    private PyNone() { super(TYPE, "None"); }

    // Special methods -----------------------------------------------

    @SuppressWarnings({"static-method", "unused"})
    private boolean __bool__() { return false; }
}
