package uk.co.farowl.vsj2.evo4;

/** The Python {@code IndexError} exception. */
class IndexError extends PyException {

    static final PyType TYPE =
            new PyType("IndexError", IndexError.class);

    protected IndexError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public IndexError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
