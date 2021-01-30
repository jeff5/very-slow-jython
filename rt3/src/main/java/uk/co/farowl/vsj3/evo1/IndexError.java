package uk.co.farowl.vsj3.evo1;

/** The Python {@code IndexError} exception. */
class IndexError extends PyException {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("IndexError", IndexError.class)
                    .base(PyException.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected IndexError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public IndexError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
