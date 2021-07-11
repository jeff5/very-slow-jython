package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code Warning} exception. */
class Warning extends PyException {
    private static final long serialVersionUID = 1L;

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType
            .fromSpec(new PyType.Spec("Warning", MethodHandles.lookup())
                    .base(PyException.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected Warning(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public Warning(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
