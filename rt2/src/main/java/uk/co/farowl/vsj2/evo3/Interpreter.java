package uk.co.farowl.vsj2.evo3;

/**
 * An interpreter is responsible for certain variable aspects of the
 * "context" within which Python code executes. Chief among these is the
 * search path (in fact the whole mechanism) along which modules are
 * found, and the dictionary of imported modules itself.
 *
 * <p>
 * The interpreter also holds the wrappers around the standard input and
 * output streams, and the registry of codecs. Many of these are exposed
 * through the {@code sys} module, rather than any class with
 * "interpreter" in the name.
 */
class Interpreter {

    /**
     * The list of modules created by this interpreter, exposed as
     * {@code sys.modules} when we have a {@code sys} module
     */
    final PyDictionary modules = new PyDictionary();

    /**
     * The builtins module. An instance is created with each
     * {@code Interpreter}. Not {@code null}.
     */
    final PyModule builtinsModule;

    /** Create a new {@code Interpreter}. */
    Interpreter() {
        builtinsModule = new BuiltinsModule();
        builtinsModule.init();
        addModule(builtinsModule);
    }

    void addModule(PyModule m) {
        if (modules.putIfAbsent(m.name, m) != null)
            throw new InterpreterError(
                    "Interpreter.addModule: Module already added %s",
                    m.name);
    }

    /**
     *
     * @param code compiled code object
     * @param globals global context dictionary
     * @param locals local variables (may be same as {@code globals})
     * @return
     */
    PyObject evalCode(PyCode code, PyDictionary globals,
            PyObject locals) {
        ThreadState tstate = ThreadState.get();
        globals.putIfAbsent(Py.BUILTINS, builtinsModule);
        PyFrame f = code.createFrame(tstate, this, globals, locals);
        return f.eval();
    }

    /**
     * Get the current frame or null in there is none. The current frame
     * is the one at the top of the stack in the current ThreadState.
     */
    static PyFrame getFrame() { return ThreadState.get().frame; }

    /**
     * Get the current {@code Interpreter} (via the top stack-frame of
     * the current {@code ThreadState}). There is no current interpreter
     * if the stack is empty.
     *
     * @return current {@code Interpreter} or {@code null}
     */
    static Interpreter get() {
        PyFrame f = ThreadState.get().frame;
        return f != null ? f.interpreter : null;
    }

}