// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/**
 * Public static methods associated with the Python type {@code object}.
 * The Python {@code object} type is represented by
 * {@code java.lang.Object} and not by instances of this class.
 * <p>
 * The Java implementation class of a type defined in Python <i>will</i>
 * be derived from the canonical implementation class of the "solid
 * base" it inherits in Python. This may well be {@code Object}.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public final class PyObject {

    /** The type object {@code object}. */
    public static final PyType TYPE =
            /*
             * This looks a bit weird, but we need to make sure PyType
             * gets initialised, and the whole type system behind it,
             * before the first type object becomes visible.
             */
            PyType.of(new Object());

    /** One cannot make instances of this class. */
    private PyObject() {}
}
