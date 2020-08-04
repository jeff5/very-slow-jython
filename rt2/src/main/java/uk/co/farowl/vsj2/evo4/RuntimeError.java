package uk.co.farowl.vsj2.evo4;

/** The Python {@code RuntimeError} exception. */
public class RuntimeError extends PyException {

    static final PyType TYPE = new PyType("RuntimeError", RuntimeError.class);

    protected RuntimeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public RuntimeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
