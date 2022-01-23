package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code UnboundLocalError} exception. */
public class UnboundLocalError extends NameError {
    private static final long serialVersionUID = 1L;

    /**
     * The type object of Python {@code UnboundLocalError} exceptions.
     */
    @SuppressWarnings("hiding")
    public static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("NameError", MethodHandles.lookup())
                    .base(NameError.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected UnboundLocalError(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public UnboundLocalError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
