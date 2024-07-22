// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/** Static methods for handling Python {@code float}. */
public final class PyFloat {
    /** The type object {@code float}. */
    public static final PyType TYPE =
            // XXX dummy
            PyType.TYPE.getBase();
            //PyType.forClass(double.class);

    private PyFloat() {}; // No instances.
}
