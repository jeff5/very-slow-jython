// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.Exposed.Default;
import uk.co.farowl.vsj4.runtime.Exposed.DocString;
import uk.co.farowl.vsj4.runtime.Exposed.PythonNewMethod;

/**
 * The Python {@code bool} object. The only instances of {@code bool} in
 * Python are {@code False} and {@code True}, represented by Java
 * {@code Boolean.FALSE} and {@code Boolean.TRUE}, and there are no
 * sub-classes. (Rogue instances of Java {@code Boolean} will generally
 * behave as {@code False} or {@code True} but may fail identity tests.)
 */
public final class PyBool {

    /** The type of Python object this class implements. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("bool", MethodHandles.lookup())
                    .primary(Boolean.class) //
                    .base(PyLong.TYPE));

    private PyBool() {}  // enforces the doubleton :)

    // Constructor from Python ----------------------------------------

    /**
     * @param cls ignored since {@code bool} cannot be sub-classed.
     * @param x to convert to its truth value.
     * @return {@code False} or {@code True}
     * @throws Throwable on argument type or other errors
     */
    @PythonNewMethod
    @DocString("""
            bool(x) -> bool

            Returns True when the argument x is true, False otherwise.
            The builtins True and False are the only two instances of the class bool.
            The class bool is a subclass of the class int, and cannot be subclassed.
            """)
    static Object __new__(PyType cls,
            @Default("False") Object x) throws Throwable {
        assert cls == TYPE;  // Guaranteed by construction (maybe)
        return Abstract.isTrue(x);
    }

    // special methods ------------------------------------------------

    static Object __repr__(Boolean self) {
        return self ? "True" : "False";
    }
}
