package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code ZeroDivisionError} exception. */
public class ZeroDivisionError extends ArithmeticError {
    private static final long serialVersionUID = 1L;

    /** The type object of Python {@code ZeroDivisionError} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("ZeroDivisionError", MethodHandles.lookup())
                    .base(ArithmeticError.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected ZeroDivisionError(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public ZeroDivisionError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
