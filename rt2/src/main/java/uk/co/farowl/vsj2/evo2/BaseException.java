package uk.co.farowl.vsj2.evo2;

/** The Python {@code BaseException} exception. */
class BaseException extends RuntimeException implements PyObject {

    static final PyType TYPE =
            new PyType("BaseException", BaseException.class);
    private final PyType type;

    @Override
    public PyType getType() { return type; }

    /** Constructor for sub-class use specifying {@link #type}. */
    protected BaseException(PyType type, String msg, Object... args) {
        super(String.format(msg, args));
        this.type = type;
    }

    public BaseException(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
