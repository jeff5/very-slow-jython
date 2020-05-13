package uk.co.farowl.vsj2.evo3;

/** The Python {@code LookupError} exception. */
class LookupError extends PyException {

    static final PyType TYPE = new PyType("LookupError", LookupError.class);

    protected LookupError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public LookupError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
