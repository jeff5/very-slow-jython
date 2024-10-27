// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

/** Placeholder until implemented. */
// FIXME implement me
public class PyLong implements WithClass {
    /** The type {@code int}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("int", MethodHandles.lookup())
                    .adopt(BigInteger.class, Integer.class)
                    .accept(Boolean.class) //
    // .methods(PyLongMethods.class)
    // .binops(PyLongBinops.class)
    );

    @Override
    public PyType getType() { return TYPE; }
}
