package uk.co.farowl.vsj2.evo3;

/** The Python {@code UnboundLocalError} exception. */
class UnboundLocalError extends NameError {

    static final PyType TYPE = new PyType("UnboundLocalError", UnboundLocalError.class);

    protected UnboundLocalError(PyType type, String msg, Object... args) {
            super(type, msg, args);
        }

    public UnboundLocalError(String msg, Object... args) {
            this(TYPE, msg, args);
        }
}
