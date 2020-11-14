package uk.co.farowl.vsj2.evo3;

/** The Python {@code TypeError} exception. */
class TypeError extends PyException {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("TypeError", TypeError.class);

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected TypeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public TypeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
