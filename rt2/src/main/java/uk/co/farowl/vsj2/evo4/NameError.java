package uk.co.farowl.vsj2.evo4;

/** The Python {@code NameError} exception. */
class NameError extends PyException {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("NameError", NameError.class)
                    .base(PyException.TYPE));

    protected NameError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public NameError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
