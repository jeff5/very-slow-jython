package uk.co.farowl.vsj2.evo2;

/** The Python {@code BaseException} exception. */
class BaseException extends RuntimeException implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE =
            new PyType("BaseException", BaseException.class);
    private final PyType type;

    @Override
    public PyType getType() { return type; }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected BaseException(PyType type, String msg, Object... args) {
        super(String.format(msg, args));
        this.type = type;
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public BaseException(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
