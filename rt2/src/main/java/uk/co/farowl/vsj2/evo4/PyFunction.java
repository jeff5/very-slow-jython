package uk.co.farowl.vsj2.evo4;

import java.util.Collection;
import java.util.EnumSet;

import uk.co.farowl.vsj2.evo4.PyCode.Trait;

/**
 * Function object as created by a function definition and subsequently
 * called.
 */
class PyFunction implements PyObject {

    static final PyType TYPE = new PyType("function", PyFunction.class);

    @Override
    public PyType getType() { return TYPE; }

    /** __code__, the code object */
    PyCode code;
    /** __globals__, a dict (other mappings won't do) */
    final PyDict globals;
    /** The (positional) default arguments or {@code null}. */
    PyObject[] defaults;
    /** A dict or null, the __kwdefaults__ attribute. */
    PyDict kwdefaults;
    /** The __closure__ attribute, or {@code null}. */
    PyCell[] closure;
    /** The __doc__ attribute, can be set to anything. */
    // (but only a str prints in help)
    PyObject doc;
    /** __name__ attribute, a str */
    PyUnicode name;
    /** __dict__ attribute, a dict or null */
    PyDict dict;
    /** __module__ attribute, can be anything */
    PyObject module;
    /** __annotations__, a dict or {@code null} */
    PyDict annotations;
    /** __qualname__, the qualified name, a str */
    PyUnicode qualname;

    // One or both set when optimised call is possible
    boolean fast, fast0;

    /** The interpreter that defines the import context. */
    final Interpreter interpreter;

    // Compare PyFunction_NewWithQualName + explicit interpreter
    PyFunction(Interpreter interpreter, PyCode code, PyDict globals,
            PyUnicode qualname) {
        this.interpreter = interpreter;
        setCode(code);
        this.globals = globals;
        this.name = code.name;

        // Get __doc__ from first constant in code (if str)
        PyObject doc;
        PyObject[] consts = code.consts;
        if (consts.length >= 1
                && (doc = consts[0]) instanceof PyUnicode)
            this.doc = doc;
        else
            this.doc = Py.None;

        // __module__: if module name is in globals, use it.
        this.module = globals.get(ID.__name__);
        this.qualname = qualname != null ? qualname : this.name;
    }

    // slot functions -------------------------------------------------

    static PyObject tp_call(PyFunction func, PyTuple args,
            PyDict kwargs) throws Throwable {
        PyFrame frame = func.createFrame(args, kwargs);
        return frame.eval();
    }

    /** Prepare the frame from "classic" arguments. */
    protected PyFrame createFrame(PyTuple args, PyDict kwargs) {

        PyFrame frame = code.createFrame(interpreter, globals, closure);
        final int nargs = args.value.length;

        // Set parameters from the positional arguments in the call.
        frame.setPositionalArguments(args);

        // Set parameters from the keyword arguments in the call.
        if (kwargs != null && !kwargs.isEmpty())
            frame.setKeywordArguments(kwargs);

        if (nargs > code.argcount) {

            if (code.traits.contains(Trait.VARARGS)) {
                // Locate the * parameter in the frame
                int varIndex = code.argcount + code.kwonlyargcount;
                // Put the excess positional arguments there
                frame.setLocal(varIndex, new PyTuple(args.value,
                        code.argcount, nargs - code.argcount));
            } else {
                // Excess positional arguments but no VARARGS for them.
                throw tooManyPositional(nargs, frame);
            }

        } else { // nargs <= code.argcount

            if (code.traits.contains(Trait.VARARGS)) {
                // No excess: set the * parameter in the frame to empty
                int varIndex = code.argcount + code.kwonlyargcount;
                frame.setLocal(varIndex, PyTuple.EMPTY);
            }

            if (nargs < code.argcount) {
                // Set remaining positional parameters from default
                frame.applyDefaults(nargs, defaults);
            }
        }

        if (code.kwonlyargcount > 0)
            // Set keyword parameters from default values
            frame.applyKWDefaults(kwdefaults);

        // Create cells for bound variables
        if (code.cellvars.length > 0)
            frame.makeCells();

        // XXX Handle generators by returning Evaluable wrapping gen.

        return frame;
    }

    static PyObject tp_vectorcall(PyFunction func, PyObject[] stack,
            int start, int nargs, PyTuple kwnames) throws Throwable {
        PyFrame frame = func.createFrame(stack, start, nargs, kwnames);
        return frame.eval();
    }

    /** Prepare the frame from CPython vector call arguments. */
    protected PyFrame createFrame(PyObject[] stack, int start,
            int nargs, PyTuple kwnames) {

        int nkwargs = kwnames == null ? 0 : kwnames.value.length;

        /*
         * Here, CPython applies certain criteria for calling a fast
         * path that (in our terms) calls only setPositionalArguments().
         * Those that depend only on code or defaults we make when those
         * attributes are defined.
         */
        /*
         * CPython's criteria: code.kwonlyargcount == 0 && nkwargs == 0
         * && code.traits.equals(EnumSet.of(Trait.OPTIMIZED,
         * Trait.NEWLOCALS, Trait.NOFREE)), and then either nargs ==
         * code.argcount or nargs == 0 and func.defaults fills the
         * positional arguments exactly.
         */
        if (fast && nargs == code.argcount && nkwargs == 0)
            return code.fastFrame(interpreter, globals, stack, start);
        else if (fast0 && nargs == 0)
            return code.fastFrame(interpreter, globals, defaults, 0);

        PyFrame frame = code.createFrame(interpreter, globals, closure);

        // Set parameters from the positional arguments in the call.
        frame.setPositionalArguments(stack, start, nargs);

        // Set parameters from the keyword arguments in the call.
        if (nkwargs > 0)
            frame.setKeywordArguments(stack, start + nargs,
                    kwnames.value);

        if (nargs > code.argcount) {

            if (code.traits.contains(Trait.VARARGS)) {
                // Locate the *args parameter in the frame
                int varIndex = code.argcount + code.kwonlyargcount;
                // Put the excess positional arguments there
                frame.setLocal(varIndex, new PyTuple(stack,
                        start + code.argcount, nargs - code.argcount));
            } else {
                // Excess positional arguments but no VARARGS for them.
                throw tooManyPositional(nargs, frame);
            }

        } else { // nargs <= code.argcount

            if (code.traits.contains(Trait.VARARGS)) {
                // No excess: set the * parameter in the frame to empty
                int varIndex = code.argcount + code.kwonlyargcount;
                frame.setLocal(varIndex, PyTuple.EMPTY);
            }

            if (nargs < code.argcount) {
                // Set remaining positional parameters from default
                frame.applyDefaults(nargs, defaults);
            }
        }

        if (code.kwonlyargcount > 0)
            // Set keyword parameters from default values
            frame.applyKWDefaults(kwdefaults);

        // Create cells for bound variables
        if (code.cellvars.length > 0)
            frame.makeCells();

        // XXX Handle generators by returning Evaluable wrapping gen.

        return frame;
    }

    // attribute access ----------------------------------------

    PyCode getCode() { return code; }

    void setCode(PyCode code) {
        this.code = code;
        // Optimisation relies on code properties
        this.fast = code.kwonlyargcount == 0
                && code.traits.equals(FAST_TRAITS);
        this.fast0 = this.fast && defaults != null
                && defaults.length == code.argcount;
    }

    /**
     * @return the positional {@code __defaults__ tuple} or
     *         {@code None}.
     */
    PyObject getDefaults() {
        return defaults != null ? PyTuple.wrap(defaults) : Py.None;
    }

    void setDefaults(PyTuple defaults) {
        this.defaults = defaults.value;
        // Must recompute if code or defaults re-assigned
        this.fast0 = this.fast && defaults != null
                && defaults.size() == code.argcount;
    }

    /**
     * @return the keyword {@code __kwdefaults__ dict} or {@code None}.
     */
    PyObject getKwdefaults() {
        return kwdefaults != null ? kwdefaults : Py.None;
    }

    void setKwdefaults(PyDict kwdefaults) {
        this.kwdefaults = kwdefaults;
    }

    /**
     * @return the {@code __closure__ tuple} or {@code None}.
     */
    PyObject getClosure() {
        return closure != null ? PyTuple.wrap(closure) : Py.None;
    }

    /** Set the {@code __closure__} attribute. */
    <E extends PyObject> void setClosure(Collection<E> closure) {

        int n = closure == null ? 0 : closure.size();
        int nfree = code.freevars.length;

        if (nfree == 0) {
            if (n == 0)
                this.closure = null;
            else
                throw new TypeError("%s closure must be empty/None",
                        code.name);
        } else {
            if (n == nfree) {
                try {
                    this.closure = closure.toArray(new PyCell[n]);
                } catch (ArrayStoreException e) {
                    // The closure is not tuple of cells only
                    for (PyObject o : closure) {
                        if (!(o instanceof PyCell)) {
                            throw Abstract.typeError(
                                    "closure: expected cell, found %s",
                                    o);
                        }
                    }
                    throw new InterpreterError(
                            "Failed to make closure from %s", closure);
                }
            } else
                throw new ValueError(
                        "%s requires closure of length %d, not %d",
                        code.name, nfree, n);
        }
    }

    PyDict getAnnotations() { return annotations; }

    void setAnnotations(PyDict annotations) {
        this.annotations = annotations;
    }

    // plumbing ------------------------------------------------------

    private static final EnumSet<Trait> FAST_TRAITS =
            EnumSet.of(Trait.OPTIMIZED, Trait.NEWLOCALS, Trait.NOFREE);

    @Override
    public String toString() {
        return String.format("<function %s>", name);
    }

    /*
     * Compare CPython ceval.c::too_many_positional(). Unlike that
     * function, on diagnosing a problem, we do not have to set a
     * message and return status. Also, when called there is *always* a
     * problem, and therefore an exception.
     */
    // XXX Do not report kw arguments given: unnatural constraint.
    /*
     * The caller must defer the test until after kw processing, just so
     * the actual kw-args given can be reported accurately. Otherwise,
     * the test could be after (or part of) positional argument
     * processing.
     */
    protected TypeError tooManyPositional(int posGiven, PyFrame f) {
        boolean posPlural = false;
        int kwGiven = 0;
        String posText, givenText;
        int argcount = code.argcount;
        int defcount = defaults.length;
        int end = argcount + code.kwonlyargcount;

        assert (!code.traits.contains(Trait.VARARGS));

        // Count keyword-only args given
        for (int i = argcount; i < end; i++) {
            if (f.getLocal(i) != null) { kwGiven++; }
        }

        if (defcount != 0) {
            posPlural = true;
            posText = String.format("from %d to %d",
                    argcount - defcount, argcount);
        } else {
            posPlural = (argcount != 1);
            posText = String.format("%d", argcount);
        }

        if (kwGiven > 0) {
            String format = " positional argument%s"
                    + " (and %d keyword-only argument%s)";
            givenText = String.format(format, posGiven != 1 ? "s" : "",
                    kwGiven, kwGiven != 1 ? "s" : "");
        } else {
            givenText = "";
        }

        return new TypeError(
                "%s() takes %s positional argument%s but %d%s %s given",
                code.name, posText, posPlural ? "s" : "", posGiven,
                givenText,
                (posGiven == 1 && kwGiven == 0) ? "was" : "were");
    }

}
