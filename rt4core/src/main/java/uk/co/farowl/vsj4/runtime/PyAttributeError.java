// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.type.Feature;
import uk.co.farowl.vsj4.type.TypeSpec;

/**
 * The Python {@code AttributeError} exception.
 */
public class PyAttributeError extends PyBaseException {

    /** The type object of Python {@code AttributeError} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new TypeSpec("AttributeError", MethodHandles.lookup())
                    .base(Exception)
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Attribute not found."));

    private static final long serialVersionUID = 1L;

    /** The problematic attribute name. */
    // TODO Expose as get-set. Remove Java getter.
    private String name;
    /** The object that didn't have {@code name} as an attribute. */
    // TODO Expose as get-set. Remove Java getter.
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
            ArgParser.fromSignature("__init__", "*args", "name", "obj")
                    .kwdefaults(Py.None, Py.None);

    @Override
    void __init__(Object[] args, String[] kwds) {
        Object[] frame = INIT_PARSER.parse(args, kwds);
        // frame = [name, obj, *args]
        if (frame[2] instanceof PyTuple argsTuple) { // always is
            this.args = argsTuple;
        }
        // keywords: can't default directly to null in the parser
        Object name = frame[0], obj = frame[1];
        this.name = name == Py.None ? null : PyUnicode.asString(name);
        this.obj = obj == Py.None ? null : obj;
    }

    /** @return {@code name} attribute. */
    @Deprecated
    public Object name() { return name == null ? Py.None : name; }

    /** @return {@code obj} attribute. */
    @Deprecated
    public Object obj() { return obj == null ? Py.None : obj; }
}
