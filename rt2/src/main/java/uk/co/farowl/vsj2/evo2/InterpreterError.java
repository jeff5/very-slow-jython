package uk.co.farowl.vsj2.evo2;

/**
 * Internal error thrown when the Python implementation cannot be relied on
 * to work. A Python exception (a {@code PyObject} that might be caught in
 * Python code) is not then appropriate. Typically thrown during
 * initialisation or for irrecoverable internal errors.
 */
class InterpreterError extends RuntimeException {
    protected InterpreterError(String msg, Object... args) {
        super(String.format(msg, args));
    }
    protected InterpreterError(Throwable cause, String msg,
            Object... args) {
        super(String.format(msg, args), cause);
    }
}
