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

    /** Constructor resembling {@code __new__}. */
    public PyAttributeError(PyType type, Object[] args, String[] kwds) {
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
                case "obj":
                    this.obj = args[i];
                    break;
                default: // ignore
            }
            i += 1;
        }
    }
}
