package uk.co.farowl.vsj3.evo1;

/**
 * Thrown when we reach a combination of circumstances in the
 * interpreter that may arise from legitimate use, but we aren't ready
 * to implement it.
 * <p>
 * What does the reference implementation do at this point?
 */
class MissingFeature extends InterpreterError {

    /**
     * Constructor specifying a message.
     *
     * @param msg a Java format string for the message
     * @param args to insert in the format string
     */
    protected MissingFeature(String msg, Object... args) {
        super(String.format(msg, args));
    }
}
