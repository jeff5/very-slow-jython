package uk.co.farowl.vsj2.evo4;

/** The Python {@code LookupError} exception. */
class LookupError extends PyException {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("LookupError", LookupError.class)
                    .base(PyException.TYPE));

    protected LookupError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public LookupError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
