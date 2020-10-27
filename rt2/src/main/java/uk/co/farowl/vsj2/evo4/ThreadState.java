package uk.co.farowl.vsj2.evo4;

/**
 * Represents a platform thread (that is, a Java {@code Thread})
 * internally to the runtime.
 */
class ThreadState {

    /** Current ThreadState */
    static final ThreadLocal<ThreadState> current =
            new ThreadLocal<ThreadState>() {

                @Override
                protected ThreadState initialValue() {
                    return new ThreadState(Thread.currentThread());
                }
            };

    // Intentionally missing: Interpreter interp;

    /** The top frame of the call stack. */
    PyFrame frame = null;

    // Recursion control ----------
    /**
     * Maximum recursion depth after which the interpreter will raise
     * {@code RecursionError}
     */
    static final int RECURSION_LIMIT = 1000;
    /**
     * Extra recursion depth allowed while processing a
     * {@code RecursionError}
     */
    private static final int RECURSION_EXTRA = 50;
    /** Recursion state of this thread */
    private final RecursionState recursionState = new RecursionState();

    // Missing: recursionCritical;

    class RecursionState implements AutoCloseable {

        /** Current recursion limit */
        private int recursionLimit;
        private int lowWaterMark;
        private int limit;
        private int depth = 0;
        /**
         * {@code StackOverflow} has been raised and the stack has not
         * yet recovered.
         */
        private boolean overflowed;

        RecursionState() { setRecursionLimit(RECURSION_LIMIT); }

        RecursionState enter() throws RecursionError {
            if (++depth > limit) {
                if (overflowed) {
                    // Overflowing while handling an overflow. Give up.
                    throw new InterpreterError(
                            "Cannot recover from stack overflow.");
                } else {
                    // Entering overflow state: increase working limit.
                    overflowed = true;
                    limit += RECURSION_EXTRA;
                    throw new RecursionError(
                            "maximum recursion depth exceeded");
                }
            }
            return this;
        }

        @Override
        public void close() {
            --depth;
            if (overflowed && depth <= lowWaterMark) {
                // Leaving overflow state: restore working limit.
                overflowed = false;
                limit = recursionLimit;
            }
        }

        /**
         * Set the recursion l;imit for this thread. The new limit must
         * be higher than the current depth by about 50.
         */
        void setRecursionLimit(int limit) {
            int mark = Math.max(limit - 50, 3 * limit / 4);
            if (depth >= mark) {
                throw new RecursionError(LIMIT_TOO_LOW, limit, depth);
            }
            lowWaterMark = mark;
            this.limit = recursionLimit = limit;
            overflowed = false;
        }

        private static final String LIMIT_TOO_LOW =
                "cannot set the recursion limit to %i at depth %i: "
                        + "the limit is too low";

        /**
         * {@code StackOverflow} has been raised and the stack has not
         * yet recovered.
         */
        public boolean isOverflowed() { return overflowed; }
    }

    /**
     * Use as a resource in a try-with-resources construct, around any
     * method body that should count against the limit.
     */
    public static RecursionState enterRecursiveCall(String cause) {
        try {
            return current.get().recursionState.enter();
        } catch (RecursionError re) {
            throw new RecursionError("%s %s", re.getMessage(), cause);
        }
    }

    /** Java {@code Thread} represented by this {@code ThreadState} */
    final Thread thread;

    // Missing: exception support (main. generators and co-routines).
    // Missing: hooks for _threadmodule (join, resources, etc.).

    /**
     * Make given stack frame the top of the stack.
     *
     * @param frame new stack top.
     * @return previous stack top
     */
    PyFrame swap(PyFrame frame) {
        PyFrame prevFrame = this.frame;
        this.frame = frame;
        return prevFrame;
    }

    private ThreadState(Thread thread) { this.thread = thread; }

    /**
     * Find or create a {@code ThreadState} representing the current
     * Java {@code Thread}. This is never {@code null}.
     */
    static final ThreadState get() { return current.get(); }
}
