package uk.co.farowl.vsj2.evo4;

/** The Python {@code ValueError} exception. */
class ValueError extends PyException {

    static final PyType TYPE = new PyType("ValueError", ValueError.class);

    protected ValueError(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public ValueError(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
