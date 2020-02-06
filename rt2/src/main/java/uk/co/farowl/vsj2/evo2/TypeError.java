package uk.co.farowl.vsj2.evo2;

/** The Python {@code TypeError} exception. */
class TypeError extends PyException {
    static final PyType TYPE =
            new PyType("SystemError", SystemError.class);
    protected TypeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }
    public TypeError(String msg, Object... args) { this(TYPE, msg, args); }
}
