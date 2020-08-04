package uk.co.farowl.vsj2.evo4;


/** The Python {@code AttributeError} exception. */
class AttributeError extends PyException {

    static final PyType TYPE = new PyType("AttributeError", AttributeError.class);

    protected AttributeError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public AttributeError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}

