// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

/**
 * The Python {@code NameError} exception, and the same class also
 * represents {@code UnboundLocalError}.
 */
public class PyNameError extends PyBaseException {

    /** The type object of Python {@code NameError} exceptions. */
    public static final PyType TYPE = PyType
            .fromSpec(new TypeSpec("NameError", MethodHandles.lookup())
                    .base(PyExc.Exception)
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Name not found globally."));

    private static final long serialVersionUID = 1L;

    /** The problematic name. */
    private String name;

    /**
     * Constructor  specifying Python type.
     *
     * @param type Python type of the exception
     * @param args positional arguments
     */
    public PyNameError(PyType type, PyTuple args) {
        super(type, args);
    }


    private static final ArgParser INIT_PARSER = ArgParser
            .fromSignature("__init__", "*args, name");

    @Override
    void __init__(Object[] args, String[] kwds) {
        Object[] frame = INIT_PARSER.parse(args, kwds);
        this.args = new PyTuple(frame,0,1);
        this.name = PyUnicode.asString(frame[1]);
    }
}
