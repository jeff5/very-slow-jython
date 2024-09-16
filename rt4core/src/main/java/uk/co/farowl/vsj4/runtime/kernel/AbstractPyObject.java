// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import uk.co.farowl.vsj4.runtime.PyObject;

/**
 * The Python {@code object} is represented by {@code java.lang.Object}
 * but its Python behaviour is implemented by {@link PyObject}, which
 * extends this class. This class provides members used internally in
 * the run-time system, but that we do not intend to expose as API from
 * {@link PyObject} itself.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public abstract class AbstractPyObject {

    /** Lookup object with package visibility. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);
}
