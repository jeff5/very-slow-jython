// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

/**
 * The Python {@code AttributeError} exception.
 */
public class PyAttributeError extends PyBaseException {

    /** The type object of Python {@code AttributeError} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new TypeSpec("AttributeError", MethodHandles.lookup())
                    .base(PyExc.Exception)
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Attribute not found."));

    private static final long serialVersionUID = 1L;

    /** The problematic attribute name. */
    private String name;
    /** The object that didn't have {@code name} as an attribute. */
    private Object obj;

    /**
     * Constructor resembling {@code __new__}, specifying Python
     * positional arguments (only).
     *
     * @param type Python type of the exception
     * @param args arguments to fossilise in the exception instance
     */
    public PyAttributeError(PyType type, PyTuple args) {
        super(type, args);
    }

    private static final ArgParser INIT_PARSER =
            ArgParser.fromSignature("__init__", "*args, name, obj");

    @Override
    void __init__(Object[] args, String[] kwds) {
        Object[] frame = INIT_PARSER.parse(args, kwds);
        this.args = new PyTuple(frame, 0, 1);
        this.name = PyUnicode.asString(frame[1]);
        this.obj = frame[2];
    }
}
