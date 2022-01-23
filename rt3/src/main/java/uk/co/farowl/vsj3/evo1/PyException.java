package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code Exception} exception. */
public class PyException extends BaseException {
    private static final long serialVersionUID = 1L;

    /** The type object of Python {@code Exception} exceptions. */
    @SuppressWarnings("hiding")
    public static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("Exception", MethodHandles.lookup())
                    .base(BaseException.TYPE));

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected PyException(PyType type, String msg, Object... args) {
        super(type, msg, args);
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public PyException(String msg, Object... args) {
        this(TYPE, msg, args);
    }
}
