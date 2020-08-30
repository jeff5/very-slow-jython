package uk.co.farowl.vsj2.evo4;

/** The Python {@code TypeError} exception. */
class TypeError extends PyException {

    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("TypeError", TypeError.class)
                    .base(PyException.TYPE));

    protected TypeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public TypeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
