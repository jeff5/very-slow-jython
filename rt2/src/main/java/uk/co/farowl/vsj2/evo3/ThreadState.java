package uk.co.farowl.vsj2.evo3;

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

    /** The top frame of the call stack. */
    PyFrame frame = null;

    /** Java {@code Thread} represented by this {@code ThreadState} */
    final Thread thread;

    // Missing: exception support (main. generators and co-routines).
    // Missing: hooks for _threadmodule (join, resources, etc.).
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
    static final ThreadState get() {
        return current.get();
    }
}
