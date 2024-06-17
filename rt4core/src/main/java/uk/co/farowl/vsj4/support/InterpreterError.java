package uk.co.farowl.vsj4.support;

/**
 * Internal error thrown when the Python implementation cannot be relied
 * on to work. A Python {@code exception} (that might be caught in
 * Python code) is not then appropriate. An {@code InterpreterError} is
 * typically thrown during initialisation or for irrecoverable internal
 * errors.
 */
public class InterpreterError extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public InterpreterError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    /**
     * Constructor specifying a cause and a message.
     *
     * @param cause a Java exception behind the interpreter error
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    public InterpreterError(Throwable cause, String msg,
            Object... args) {
        super(String.format(msg, args), cause);
    }

    /**
     * Constructor specifying a cause.
     *
     * @param cause a Java exception behind the interpreter error
     */
    public InterpreterError(Throwable cause) {
        this(cause, notNull(cause.getMessage(), "(no message)"));
    }

    /**
     * @param msg a string or {@code null}
     * @param defaultMsg a string or {@code null}
     * @return non-{@code null} {@code msg} or ""
     */
    private static String notNull(String msg, String defaultMsg) {
        return msg != null ? msg : defaultMsg;
    }
}
