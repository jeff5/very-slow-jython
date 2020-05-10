package uk.co.farowl.vsj2.evo3;

/** The Python {@code NameError} exception. */
class NameError extends PyException {

    static final PyType TYPE = new PyType("NameError", NameError.class);

    protected NameError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public NameError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
