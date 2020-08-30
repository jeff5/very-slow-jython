package uk.co.farowl.vsj2.evo4;

/** The Python {@code Warning} exception. */
class Warning extends PyException {

    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("Warning", Warning.class)
                    .base(PyException.TYPE));

    protected Warning(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public Warning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
