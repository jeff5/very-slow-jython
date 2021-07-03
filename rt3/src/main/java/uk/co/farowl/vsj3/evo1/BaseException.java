package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code BaseException} exception. */
class BaseException extends RuntimeException implements CraftedPyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("BaseException", MethodHandles.lookup()));
    private final PyType type;
    final PyTuple args;

    @Override
    public PyType getType() { return type; }

    /**
     * Constructor for sub-class use specifying {@link #type}.
     *
     * @param type object being constructed
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected BaseException(PyType type, String msg, Object... args) {
        super(String.format(msg, args));
        this.type = type;
        msg = this.getMessage();
        this.args = msg.length() > 0 ? new PyTuple(msg) : PyTuple.EMPTY;
    }

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public BaseException(String msg, Object... args) {
        this(TYPE, msg, args);
    }

    @Override
    public String toString() {
        String msg = args.size() > 0 ? args.get(0).toString() : "";
        return String.format("%s: %s", getType().name, msg);
    }

    // slot functions -------------------------------------------------

    protected Object __repr__() {
        // Somewhat simplified
        return getType().name + "('" + getMessage() + "')";
    }
}
