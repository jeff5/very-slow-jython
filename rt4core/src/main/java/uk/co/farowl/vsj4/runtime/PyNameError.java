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
     * Constructor resembling {@code __new__}, specifying Python
     * argument array and keywords.
     *
     * @param type Python type of the exception
     * @param args arguments to fossilise in the exception instance
     * @param kwds keyword names to go with the trailing arguments
     */
    public PyNameError(PyType type, Object[] args, String[] kwds) {
        super(type, args, kwds);
        // Stop-gap argument processing to show principle
        // XXX Should the be in __init__ (and use the exposer)
        int n = args.length, i = n - kwds.length;
        assert i >= 0;
        for (String kwd : kwds) {
            switch (kwd) {
                case "name":
                    this.name = (String)args[i];
                    break;
                default: // ignore
            }
            i += 1;
        }
    }
}
