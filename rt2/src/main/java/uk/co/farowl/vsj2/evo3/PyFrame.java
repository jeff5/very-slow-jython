package uk.co.farowl.vsj2.evo3;

import java.util.EnumSet;
import java.util.Map;

/** A {@code PyFrame} is the context for the execution of code. */
abstract class PyFrame implements PyObject {

    /** ThreadState owning this frame. */
    protected final ThreadState tstate;
    /** Frames form a stack by chaining through the back pointer. */
    PyFrame back;
    /** Code this frame is to execute. */
    final PyCode code;
    /** ThreadState owning this frame. */
    protected final Interpreter interpreter;
    /** Built-in objects */
    final PyDictionary builtins;
    /** Global context (name space) of execution. */
    final PyDictionary globals;
    /** Local context (name space) of execution. (Assign if needed.) */
    Map<PyObject, PyObject> locals = null;

    /**
     * Partial constructor, leaves {@link #locals} {@code null}.
     * Establishes the back-link to the current stack top but does not
     * make this frame the stack top. ({@link #eval()} should do that.)
     *
     * @param tstate thread state (supplies link to previous frame)
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     */
    PyFrame(ThreadState tstate, PyCode code, Interpreter interpreter,
            PyDictionary globals) {
        this.tstate = tstate;
        this.code = code;
        this.interpreter = interpreter;
        this.back = tstate.frame; // NB not pushed until eval()
        this.globals = globals;
        // Infer builtins (a PyDictionary or subclass)
        if (back != null && back.globals == globals)
            // Same globals, so same builtins
            this.builtins = back.builtins;
        else
            this.builtins = inferBuiltins();
    }

    /**
     * Find or create a {@code dict} to be the built-ins of this frame.
     * Either the __builtins__ element of the globals provides it, or we
     * supply a minimal dictionary.
     */
    private PyDictionary inferBuiltins() {
        // Normally, __builtins__ is a module
        PyObject b = globals.get(Py.BUILTINS);
        if (b != null) {
            if (b instanceof PyModule)
                return ((PyModule) b).dict;
            else if (b instanceof PyDictionary)
                return (PyDictionary) b;
            throw new TypeError("%s should be module not %s",
                    Py.BUILTINS, b);
        } else {
            // Substitute minimal builtins
            PyDictionary builtins = new PyDictionary();
            builtins.put("None", Py.None);
            return builtins;
        }
    }

    /**
     * Foundation constructor on which subclass constructors rely.
     *
     * <ul>
     * <li>If the code has the trait {@link PyCode.Trait#NEWLOCALS} the
     * {@code locals} argument is ignored.</li>
     * <li>If the code has the trait {@link PyCode.Trait#NEWLOCALS} but
     * not {@link PyCode.Trait#OPTIMIZED}, a new empty ``dict`` will be
     * provided as locals.</li>
     * <li>If the code has the traits {@link PyCode.Trait#NEWLOCALS} and
     * {@link PyCode.Trait#OPTIMIZED}, {@code this.locals} will be
     * {@code null} until set by the sub-class.</li>
     * <li>Otherwise, if the argument {@link #locals} is not
     * {@code null} it specifies {@code this.locals}, and</li>
     * <li>if the argument {@link #locals} is {@code null}
     * {@code this.locals} will be the same as {@code globals}.</li>
     * </ul>
     *
     * @param tstate thread state (supplies back)
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space (or it may be {@code globals})
     */
    protected PyFrame(ThreadState tstate, PyCode code,
            Interpreter interpreter, PyDictionary globals,
            PyObject locals) {

        // Initialise the basics.
        this(tstate, code, interpreter, globals);

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)) {
            // Ignore locals argument
            if (traits.contains(PyCode.Trait.OPTIMIZED)) {
                // We can create it later but probably won't need to
                this.locals = null;
            } else {
                this.locals = new PyDictionary();
            }
        } else if (locals == null) {
            // Default to same as globals.
            this.locals = globals;
        } else {
            /*
             * Use supplied locals. As it may not implement j.u.Map, we
             * should arrange to wrap any Python object supporting the
             * right methods as a Map<>, but later.
             */
            this.locals = (Map<PyObject, PyObject>) locals;
        }
    }

    /** Execute the code in this frame. */
    abstract PyObject eval();
}
