// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code None} object. */
public final class PyNone extends Singleton {

    /** The Python type of {@code None}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("NoneType", MethodHandles.lookup())
                    .flagNot(PyType.Flag.BASETYPE));

    /** The only instance, published as {@link Py#None}. */
    static final PyNone INSTANCE = new PyNone();

    private PyNone() { super(TYPE, "None"); }

    // Special methods -----------------------------------------------

    @SuppressWarnings({"static-method", "unused"})
    private boolean __bool__() { return false; }
}
