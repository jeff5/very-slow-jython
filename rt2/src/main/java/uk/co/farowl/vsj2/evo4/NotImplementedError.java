package uk.co.farowl.vsj2.evo4;

/** The Python {@code NotImplementedError} exception. */
public class NotImplementedError extends RuntimeError {

    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("NotImplementedError",
                    NotImplementedError.class).base(RuntimeError.TYPE));

    protected NotImplementedError(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    public NotImplementedError(String msg, Object... args) {
        this(TYPE, msg, args);
    }

}
