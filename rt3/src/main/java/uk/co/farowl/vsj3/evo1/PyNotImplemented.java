// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code NotImplemented} object. */
public final class PyNotImplemented extends Singleton {

    /** The Python type of {@code PyNotImplemented}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("NotImplementedType",
                    MethodHandles.lookup())
                            .flagNot(PyType.Flag.BASETYPE));

    /** The only instance, published as {@link Py#NotImplemented}. */
    static final PyNotImplemented INSTANCE = new PyNotImplemented();

    private PyNotImplemented() { super(TYPE, "NotImplemented"); }
}
