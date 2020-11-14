package uk.co.farowl.vsj2.evo3;

/** The Python {@code Warning} exception. */
class Warning extends PyException {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("Warning", Warning.class);

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected Warning(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public Warning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
