package uk.co.farowl.vsj2.evo2;

/** The Python {@code SystemError} exception. */
class SystemError extends PyException {

    static final PyType TYPE =
            new PyType("SystemError", SystemError.class);

    protected SystemError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public SystemError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
