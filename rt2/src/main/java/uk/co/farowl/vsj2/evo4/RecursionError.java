package uk.co.farowl.vsj2.evo4;


/** The Python {@code RecursionError} exception. */
public class RecursionError extends RuntimeError {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("RecursionError", RecursionError.class)
                    .base(RuntimeError.TYPE));

    protected RecursionError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public RecursionError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
