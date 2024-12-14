// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.Exposed.KeywordOnly;
import uk.co.farowl.vsj4.runtime.Exposed.PositionalCollector;

/**  The Python {@code StopIteration} exception. */
public class PyStopIteration extends PyBaseException {

    /** The type object of Python {@code NameError} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new TypeSpec("StopIteration", MethodHandles.lookup())
                    .base(PyExc.Exception)
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Signal the end from iterator.__next__()."));

    private static final long serialVersionUID = 1L;

    /** The generator return value. */
    private Object value;

    /**
     * Constructor specifying Python type.
     *
     * @param type Python type of the exception
     * @param args positional arguments
     */
    public PyStopIteration(PyType type, PyTuple args) {
        super(type, args);
    }

    // special methods ------------------------------------------------

    // Compare CPython StopIteration_* in exceptions.c

    /**
     * @param value exit value of the iteration
     * @param args positional arguments
     */
    void __init__(@KeywordOnly Object value,
            @PositionalCollector PyTuple args) {
        this.args = args;
        this.value = value;
    }

}
