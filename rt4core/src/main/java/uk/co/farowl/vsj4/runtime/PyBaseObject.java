// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyBaseObject;

/**
 * The Python {@code object} type is implemented by
 * {@code java.lang.Object} but gets its behaviour from this class,
 * which is also the implementation of subclasses where {@code object}
 * is the only built-in base.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public final class PyBaseObject extends AbstractPyBaseObject {

    /** The type object {@code object}. */
    public static final PyType TYPE =
            /*
             * This looks a bit weird, but we need to make sure PyType
             * gets initialised, and the whole type system behind it,
             * before the first type object becomes visible.
             */
            PyType.of(new Object());

    /**
     * Construct an instance of a proper subclass of {@code object}.
     * This is the constructor that creates an instance of a Python
     * class when {@code object} is the built-in base.
     *
     * @param type actual Python type of this object (a proper subclass
     *     of {@code object})
     */
    protected PyBaseObject(PyType type) { super(type); }
}
