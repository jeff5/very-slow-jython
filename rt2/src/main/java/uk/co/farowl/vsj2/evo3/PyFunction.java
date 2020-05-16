package uk.co.farowl.vsj2.evo3;

import uk.co.farowl.vsj2.evo3.PyCode.Trait;

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
    /** A tuple, the __defaults__ attribute (positional), not null. */
    PyTuple defaults = PyTuple.EMPTY;
    /** A dict or null, the __kwdefaults__ attribute. */
    PyDict kwdefaults;
    /** A tuple of cells, the __closure__ attribute, not null. */
    PyTuple closure = PyTuple.EMPTY;
    /** The __doc__ attribute, can be set to anything. */
    // (but only a str prints in help)
    PyObject doc;
    /** __name__ attribute, a str */
    PyUnicode name;
    /** __dict__ attribute, a dict or null */
    PyDict dict;
    /** __module__ attribute, can be anything */
    PyObject module;
    /** __annotations__, a dict or null */
    PyDict annotations;
    /** __qualname__, the qualified name, a str */
    PyUnicode qualname;

    // In CPython a pointer to function
    // vectorcallfunc vectorcall;

    /** The interpreter that defines the import context. */
    final Interpreter interpreter;

    private static final PyUnicode STR__name__ = Py.str("__name__");

    // Compare PyFunction_NewWithQualName + explicit interpreter
    PyFunction(Interpreter interpreter, PyCode code, PyDict globals,
            PyUnicode qualname) {
        this.interpreter = interpreter;
        this.code = code;
        this.globals = globals;
        this.name = code.name;

        // XXX: when we support the stack-slice call add handle.
        // op->vectorcall = _PyFunction_Vectorcall;

        // Get __doc__ from first constant in code (if str)
        PyObject doc;
        PyObject[] consts = code.consts.value;
        if (consts.length >= 1
                && (doc = consts[0]) instanceof PyUnicode)
            this.doc = doc;
        else
            this.doc = Py.None;

        // __module__: if module name is in globals, use it.
        this.module = globals.get(STR__name__);
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
                // Put any excess positional args there
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
        if (code.cellvars.value.length > 0)
            frame.makeCells();

        // XXX Handle generators by returning Evaluable wrapping gen.

        return frame;
    }

    // Experiment: define a vector call

    static PyObject tp_vectorcall(PyFunction func, PyObject[] stack,
            int start, int nargs, PyTuple kwnames) throws Throwable {
        PyFrame frame = func.createFrame(stack, start, nargs, kwnames);
        return frame.eval();
    }

    /** Prepare the frame from CPython vector call arguments. */
    protected PyFrame createFrame(PyObject[] stack, int start,
            int nargs, PyTuple kwnames) {

        PyFrame frame = code.createFrame(interpreter, globals, closure);
        int nkwargs = kwnames==null ? 0 : kwnames.value.length;

        /*
         * Here, CPython applies certain criteria for calling a fast
         * path that (in our terms) calls only setPositionalArguments().
         * Our version makes essentially the same tests below, but
         * progressively and in a different order.
         */
        /*
         * CPython's criteria: code.kwonlyargcount == 0 && nkwargs == 0
         * && code.traits.equals(EnumSet.of(Trait.OPTIMIZED,
         * Trait.NEWLOCALS, Trait.NOFREE)), and then either nargs ==
         * code.argcount or nargs == 0 and func.defaults fills
         * the positional arguments exactly.
         */

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
                // Put any excess positional args there
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
        if (code.cellvars.value.length > 0)
            frame.makeCells();

        // XXX Handle generators by returning Evaluable wrapping gen.

        return frame;
    }






    // Experiment: define entry points for specific call signatures

    // Zero argument call, no defaults or closure

    // Single argument call

    // attribute access ----------------------------------------

    PyTuple getDefaults() { return defaults; }

    void setDefaults(PyTuple defaults) {
        this.defaults = defaults != null ? defaults : PyTuple.EMPTY;
    }

    PyDict getKwdefaults() { return kwdefaults; }

    void setKwdefaults(PyDict kwdefaults) {
        this.kwdefaults = kwdefaults;
    }

    PyTuple getClosure() { return closure; }

    /** Set the {@code __closure__} attribute. */
    void setClosure(PyTuple closure) {

        if (closure == null)
            closure = PyTuple.EMPTY;

        int nfree = code.freevars.value.length;
        if (closure.value.length != nfree) {
            if (nfree == 0) {
                throw new TypeError("%s closure must be empty/None",
                        code.name);
            } else {
                throw new ValueError(
                        "%s requires closure of length %d, not %d",
                        code.name, nfree, closure.value.length);
            }
        }

        // check that the closure is tuple of cells
        for (PyObject o : closure.value) {
            if (!(o instanceof PyCell)) {
                throw Abstract.typeError(
                        "closure: expected cell, found %s", o);
            }
        }

        this.closure = closure;
    }

    PyDict getAnnotations() { return annotations; }

    void setAnnotations(PyDict annotations) {
        this.annotations = annotations;
    }

    // plumbing ------------------------------------------------------

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
        int defcount = defaults.value.length;
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
