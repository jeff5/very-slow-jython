package uk.co.farowl.vsj2.evo4;

/**
 * Internal error thrown when the Python implementation cannot be relied
 * on to work. A Python exception (a {@code PyObject} that might be
 * caught in Python code) is not then appropriate. Typically thrown
 * during initialisation or for irrecoverable internal errors.
 */
class InterpreterError extends RuntimeException {

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected InterpreterError(String msg, Object... args) {
        super(String.format(msg, args));
    }

    /**
     * Constructor specifying a cause and a message.
     *
     * @param cause a Java exception behind the interpreter error
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected InterpreterError(Throwable cause, String msg,
            Object... args) {
        super(String.format(msg, args), cause);
    }

}
