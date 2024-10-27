// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.JavaClassShorthand;

/**
 * Some shorthands used to construct method signatures of runtime
 * methods, {@code MethodType}s, etc..
 */
public interface ClassShorthand extends JavaClassShorthand {
    /** Shorthand for {@code PyType.class}. */
    public static final Class<PyType> T = PyType.class;
    /** Shorthand for {@code Comparison.class}. */
    // static final Class<Comparison> CMP = Comparison.class;
    /** Shorthand for {@code PyTuple.class}. */
    // static final Class<PyTuple> TUPLE = PyTuple.class;
    /** Shorthand for {@code PyDict.class}. */
    static final Class<PyDict> DICT = PyDict.class;
}
