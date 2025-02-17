// Copyright (c)2025 Jython Developers.
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
                    .base(Exception)
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Name not found globally."));

    /** {@code UnboundLocalError} extends {@code NameError}. */
    protected static PyType UnboundLocalError =
            extendsException(TYPE, "UnboundLocalError",
                    "Local name referenced but not bound to a value.");


    private static final long serialVersionUID = 1L;

    /** The problematic name (or {@code null}). */
    // TODO Expose as get-set. Remove Java getter.
    private String name;

    /**
     * Constructor specifying Python type.
     *
     * @param type Python type of the exception
     * @param args positional arguments
     */
    public PyNameError(PyType type, PyTuple args) { super(type, args); }

    private static final ArgParser INIT_PARSER =
            ArgParser.fromSignature("__init__", "*args", "name")
                    .kwdefaults(Py.None);

    @Override
    void __init__(Object[] args, String[] kwds) {
        Object[] frame = INIT_PARSER.parse(args, kwds);
        // frame = [name, *args]
        if (frame[1] instanceof PyTuple argsTuple) { // always is
            this.args = argsTuple;
        }
        // name keyword: can't default directly to null in the parser
        Object name = frame[0];
        this.name = name == Py.None ? null : PyUnicode.asString(name);
    }

    /** @return {@code name} attribute. */
    @Deprecated
    public Object name() { return name == null ? Py.None : name; }
}
