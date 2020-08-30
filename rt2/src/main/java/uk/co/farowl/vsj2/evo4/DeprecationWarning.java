package uk.co.farowl.vsj2.evo4;

/** The Python {@code DeprecationWarning} exception. */
class DeprecationWarning extends Warning {

    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("DeprecationWarning",
                    DeprecationWarning.class).base(Warning.TYPE));

    protected DeprecationWarning(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    public DeprecationWarning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
