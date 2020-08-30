package uk.co.farowl.vsj2.evo4;

/** The Python {@code Exception} exception. */
class PyException extends BaseException {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("Exception", BaseException.class)
                    .base(BaseException.TYPE));

    protected PyException(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public PyException(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
