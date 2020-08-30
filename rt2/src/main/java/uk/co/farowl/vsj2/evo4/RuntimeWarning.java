package uk.co.farowl.vsj2.evo4;

/** The Python {@code RuntimeWarning} exception. */
class RuntimeWarning extends Warning {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("RuntimeWarning", RuntimeWarning.class)
                    .base(Warning.TYPE));

    protected RuntimeWarning(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    public RuntimeWarning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
