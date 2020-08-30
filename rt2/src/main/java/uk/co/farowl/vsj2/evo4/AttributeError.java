package uk.co.farowl.vsj2.evo4;

/** The Python {@code AttributeError} exception. */
class AttributeError extends PyException {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("AttributeError", AttributeError.class)
                    .base(PyException.TYPE));

    protected AttributeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public AttributeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
