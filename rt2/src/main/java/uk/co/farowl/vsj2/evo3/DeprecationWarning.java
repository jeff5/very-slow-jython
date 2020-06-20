package uk.co.farowl.vsj2.evo3;

/** The Python {@code DeprecationWarning} exception. */
class DeprecationWarning extends Warning {

    static final PyType TYPE = new PyType("DeprecationWarning", DeprecationWarning.class);

    protected DeprecationWarning(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public DeprecationWarning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
