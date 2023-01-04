// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code ...} (ellipsis) object. */
public final class PyEllipsis extends Singleton {

    /** The Python type of {@code Ellipsis}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("ellipsis", MethodHandles.lookup())
                    .flagNot(PyType.Flag.BASETYPE));

    /** The only instance, published as {@link Py#Ellipsis}. */
    static final PyEllipsis INSTANCE = new PyEllipsis();

    private PyEllipsis() { super(TYPE, "Ellipsis"); }
}
