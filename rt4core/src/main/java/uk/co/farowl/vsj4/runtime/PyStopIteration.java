// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.Exposed.KeywordOnly;
import uk.co.farowl.vsj4.runtime.Exposed.PositionalCollector;

/** The Python {@code StopIteration} exception. */
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

    /** Constructor with default arguments. */
    public PyStopIteration() {
        super(TYPE, PyTuple.EMPTY);
    }

    // special methods ------------------------------------------------

    // Compare CPython StopIteration_* in exceptions.c

    private static final ArgParser INIT_PARSER =
            ArgParser.fromSignature("__init__", "*args")
                    .kwdefaults((Object)null);

    @Override
    void __init__(Object[] args, String[] kwds) {
        Object[] frame = INIT_PARSER.parse(args, kwds);
        // frame = [*args]
        if (frame[0] instanceof PyTuple argsTuple) { // always is
            this.args = argsTuple;
        }
        PyTuple a = this.args;
        this.value = (a.size() == 0) ? Py.None : a.get(0);
    }

}
