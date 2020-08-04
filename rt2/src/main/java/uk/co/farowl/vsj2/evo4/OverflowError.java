package uk.co.farowl.vsj2.evo4;

/** The Python {@code OverflowError} exception. */
class OverflowError extends PyException {

    static final PyType TYPE =
            new PyType("OverflowError", OverflowError.class);

    protected OverflowError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public OverflowError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
