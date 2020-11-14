package uk.co.farowl.vsj2.evo3;

/** The Python {@code NotImplementedError} exception. */

public class NotImplementedError extends RuntimeError {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("NotImplementedError",
            NotImplementedError.class);

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected NotImplementedError(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public NotImplementedError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
