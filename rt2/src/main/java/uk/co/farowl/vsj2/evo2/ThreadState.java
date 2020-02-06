package uk.co.farowl.vsj2.evo2;

/**
 * Represents a platform thread (that is, a Java {@code Thread}) internally
 * to the interpreter.
 */
// Used here only to represent the stack of frames.
class ThreadState {
    /** The top frame of the call stack. */
    PyFrame frame = null;
    /**
     * The Java {@code Thread} where this {@code ThreadState} was created
     */
    final Thread thread;
    // Missing: exception support (main. generators and co-routines).
    // Missing: hooks for _threadmodule (join, resources, etc.).
    PyFrame swap(PyFrame frame) {
        PyFrame prevFrame = this.frame;
        this.frame = frame;
        return prevFrame;
    }
    ThreadState() { this.thread = Thread.currentThread(); }
}
