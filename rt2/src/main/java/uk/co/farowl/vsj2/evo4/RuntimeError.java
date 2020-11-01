package uk.co.farowl.vsj2.evo4;

/** The Python {@code RuntimeError} exception. */
class RuntimeError extends PyException {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("RuntimeError", RuntimeError.class)
                    .base(PyException.TYPE));

    protected RuntimeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public RuntimeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
