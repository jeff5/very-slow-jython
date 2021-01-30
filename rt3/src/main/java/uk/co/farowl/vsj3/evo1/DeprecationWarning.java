package uk.co.farowl.vsj3.evo1;

/** The Python {@code DeprecationWarning} exception. */
class DeprecationWarning extends Warning {

    /** The type of Python object this class implements. */
    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("DeprecationWarning",
                    DeprecationWarning.class).base(Warning.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected DeprecationWarning(PyType type, String msg,
            Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public DeprecationWarning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
