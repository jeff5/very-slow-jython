// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyObject;

/**
 * The Python {@code object} type is implemented by
 * {@code java.lang.Object} but gets its behaviour from this class or
 * its superclass.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public final class PyObject extends AbstractPyObject {

    /** The type object {@code object}. */
    public static final PyType TYPE =
            /*
             * This looks a bit weird, but we need to make sure PyType
             * gets initialised, and the whole type system behind it,
             * before the first type object becomes visible.
             */
            PyType.of(new Object());
}
