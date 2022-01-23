package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code RecursionError} exception. */
public class RecursionError extends RuntimeError {
    private static final long serialVersionUID = 1L;

    /** The type object of Python {@code RecursionError} exceptions. */
    @SuppressWarnings("hiding")
    public static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("RecursionError", MethodHandles.lookup())
                    .base(RuntimeError.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected RecursionError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public RecursionError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
