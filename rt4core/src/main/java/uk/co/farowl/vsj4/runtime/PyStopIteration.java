// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

/**
 * The Python {@code NameError} exception, and the same class also
 * represents {@code UnboundLocalError}.
 */
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

    /** Constructor resembling {@code __new__}. */
    public PyStopIteration(PyType type, Object[] args, String[] kwds) {
        super(type, args, kwds);
        // Stop-gap argument processing to show principle
        // XXX Should the be in __init__ (and use the exposer)
        int n = args.length, i = n - kwds.length;
        assert i >= 0;
        for (String kwd : kwds) {
            switch (kwd) {
                case "value":
                    this.value = args[i];
                    break;
                default: // ignore
            }
            i += 1;
        }
    }
}
