// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.internal.Singleton;
import uk.co.farowl.vsj4.types.TypeSpec;

/** The Python {@code Ellipsis} object. */
public final class PyEllipsis extends Singleton {

    /** The Python type of {@code Ellipsis}. */
    public static final PyType TYPE = PyType.fromSpec(//
            new TypeSpec("ellipsis", MethodHandles.lookup()));

    /** The only instance, published as {@link Py#Ellipsis}. */
    public static final PyEllipsis INSTANCE = new PyEllipsis();

    private PyEllipsis() { super(TYPE, "Ellipsis"); }

    // Special methods -----------------------------------------------

    @SuppressWarnings({"static-method", "unused"})
    private boolean __bool__() { return false; }
}
