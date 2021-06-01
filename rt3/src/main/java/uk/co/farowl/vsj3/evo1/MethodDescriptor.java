package uk.co.farowl.vsj3.evo1;

/**
 * Abstract base class for the descriptor of a method defined in Java.
 * This class provides some common behaviour and support methods that
 * would otherwise be duplicated.
 */
abstract class MethodDescriptor extends Descriptor {

    MethodDescriptor(PyType descrtype, PyType objclass, String name) {
        super(descrtype, objclass, name);
    }

    /**
     * The type of (non-Python) exception thrown by invoking a slot or
     * method with the wrong pattern of arguments. An
     * {@code ArgumentError} encapsulates what a particular method or
     * slot expected by way of the number of positional arguments and
     * the presence or otherwise of keyword arguments. It should be
     * caught by the immediate caller of
     * {@code callWrapped(MethodHandle, Object, PyTuple, PyDict)}
     */
    static class ArgumentError extends Exception {

        enum Mode { NOARGS, NUMARGS, MINMAXARGS, NOKWARGS }

        final Mode mode;
        final short minArgs, maxArgs;

        private ArgumentError(Mode mode, int minArgs, int maxArgs) {
            this.mode = mode;
            this.minArgs = (short) minArgs;
            this.maxArgs = (short) maxArgs;
        }

        /**
         * The mode is {@link Mode#NOARGS} or {@link Mode#NOKWARGS}. In
         * the latter case, {@link #minArgs} and {@link #maxArgs} should
         * be ignored.
         *
         * @param mode qualifies the sub-type of the problem
         */
        ArgumentError(Mode mode) {
            this(mode, 0, 0);
        }

        /**
         * The mode is {@link Mode#NUMARGS}.
         *
         * @param numArgs expected number of arguments
         */
        ArgumentError(int numArgs) {
            this(Mode.NUMARGS, numArgs, numArgs);
        }

        /**
         * The mode is {@link Mode#MINMAXARGS}.
         *
         * @param minArgs minimum expected number of arguments
         * @param maxArgs maximum expected number of arguments
         */
        ArgumentError(int minArgs, int maxArgs) {
            this(Mode.MINMAXARGS, minArgs, maxArgs);
        }
    }

    /**
     * Translate a problem with the number and pattern of arguments, in
     * a failed attempt to call the wrapped method, to a Python
     * {@link TypeError}.
     *
     * @param ae expressing the problem
     * @param args positional arguments (only the number will matter)
     * @return a {@code TypeError} to throw
     */
    protected TypeError signatureTypeError(
            MethodDescriptor.ArgumentError ae, PyTuple args) {
        int n = args.value.length;
        switch (ae.mode) {
            case NOARGS:
                return new TypeError(TAKES_NO_ARGUMENTS, name, n);
            case NUMARGS:
                int N = ae.minArgs;
                return new TypeError(TAKES_ARGUMENTS, name, N, n);
            case MINMAXARGS:
                String range = String.format("from %d to %d",
                        ae.minArgs, ae.maxArgs);
                return new TypeError(TAKES_ARGUMENTS, name, range, n);
            case NOKWARGS:
            default:
                return new TypeError(TAKES_NO_KEYWORDS, name);
        }
    }

    static final String TAKES_NO_ARGUMENTS =
            "%s() takes no arguments (%d given)";
    static final String TAKES_ARGUMENTS =
            "%s() takes %s arguments (%d given)";
    static final String TAKES_NO_KEYWORDS =
            "%s() takes no keyword arguments";

}
