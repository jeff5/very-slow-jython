package uk.co.farowl.vsj2.evo4;

/** The Python {@code RecursionError} exception. */
class RecursionError extends RuntimeError {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("RecursionError", RecursionError.class)
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
