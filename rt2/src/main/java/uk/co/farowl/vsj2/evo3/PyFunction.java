package uk.co.farowl.vsj2.evo3;

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
    /** A tuple or null, the __defaults__ attribute (positional). */
    PyTuple defaults;
    /** A dict or null, the __kwdefaults__ attribute. */
    PyDict kwdefaults;
    /** A tuple of cells or null, the __closure__ attribute */
    PyTuple closure;
    /** The __doc__ attribute, can be set to anything */
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
        // The defining interpreter is the one that called this.
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

    // Compare PyFunction_NewWithQualName
    PyFunction(PyCode code, PyDict globals, PyUnicode qualname) {
        // The defining interpreter is the one that called this.
        this(Interpreter.get(), code, globals, qualname);
    }

    // slot functions -------------------------------------------------

    static PyObject tp_call(PyFunction func, PyTuple args,
            PyDict kwargs) throws Throwable {
        return func.call(args, kwargs);
    }

    /** Implementation of function call for "classic" arguments. */
    PyObject call(PyTuple args, PyDict kwargs) {
        // Version from considering fundamentals

        // Create a frame of the right type for the code
        PyFrame f = code.createFrame(interpreter, globals, closure);

        // Set parameters from the positional arguments in the call.
        int n = f.setPositionalArguments(args);

        // Set parameters from the keyword arguments in the call.
        if (kwargs != null && !kwargs.isEmpty())
            f.setKeywordArguments(kwargs);

        // Set parameters from default values
        f.applyDefaults(args, defaults, kwdefaults);

        // Evaluate
        f.push();
        return f.eval();
    }

    // attribute access ----------------------------------------

    // Compare with PyFunction_New
    PyFunction(PyCode code, PyDict globals) {
        this(code, globals, null);
    }

    PyTuple getDefaults() { return defaults; }

    void setDefaults(PyTuple defaults) { this.defaults = defaults; }

    PyDict getKwdefaults() { return kwdefaults; }

    void setKwdefaults(PyDict kwdefaults) {
        this.kwdefaults = kwdefaults;
    }

    PyTuple getClosure() { return closure; }

    /** Set the {@code __closure__} attribute. */
    void setClosure(PyTuple closure) {

        int nfree = code.freevars.value.length;
        int nclosure = closure == null ? 0 : closure.value.length;

        if (nclosure != nfree) {
            if (nfree == 0) {
                throw new TypeError("%s closure must be empty/None",
                        code.name);
            } else {
                throw new ValueError(
                        "%s requires closure of length %d, not %d",
                        code.name, nfree, nclosure);
            }
        }

        // check that the closure is tuple of cells
        if (nclosure != 0) {
            for (PyObject o : closure.value) {
                if (!(o instanceof PyCell)) {
                    throw Abstract.typeError(
                            "closure: expected cell, found %s", o);
                }
            }
        }

        this.closure = closure;
    }

    PyDict getAnnotations() { return annotations; }

    void setAnnotations(PyDict annotations) {
        this.annotations = annotations;
    }

    @Override
    public String toString() {
        return String.format("<function %s>", name);
    }

}
