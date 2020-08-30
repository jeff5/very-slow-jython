package uk.co.farowl.vsj2.evo4;

/** The Python {@code IndexError} exception. */
class IndexError extends PyException {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("IndexError", IndexError.class)
                    .base(PyException.TYPE));

    protected IndexError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public IndexError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
