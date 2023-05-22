// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

/** A {@link PyFunction} defined in CPython 3.11 byte code. */
 class CPython311Function extends PyFunction<CPython311Code> {

     /** Argument parser matched to {@link #code}. */
     private ArgParser argParser;

    /**
     * Create a Python {@code function} object defined in CPython 3.11
     * code (full-featured constructor).
     *
     * @param interpreter providing the module context
     * @param code defining the function
     * @param globals name space to treat as global variables
     * @param defaults default positional argument values
     * @param kwdefaults default keyword argument values
     * @param annotations type annotations
     * @param closure variable referenced but not defined here, must be
     *     the same size as code
     */
    CPython311Function(Interpreter interpreter, CPython311Code code,
            PyDict globals, Object[] defaults, PyDict kwdefaults,
            Object annotations, PyCell[] closure) {
        super(interpreter, code, globals, defaults, kwdefaults,
                annotations, closure);
        buildParser();
    }

    /**
     * Create a Python {@code function} object defined in CPython 3.11
     * code in a simplified form suitable to represent execution of a
     * top-level module.
     *
     * @param interpreter providing the module context
     * @param code defining the function
     * @param globals name space to treat as global variables
     */
    public CPython311Function(Interpreter interpreter, CPython311Code code,
            PyDict globals) {
        this(interpreter, code, globals, null, null, null, null);
    }

   @Override
    void setCode(CPython311Code code) {
        // if (code == null) {
        // throw Abstract.attrMustBe("__code__", "a code object", code);
        // }
        super.setCode(code);
        buildParser();
    }

    @Override
    Object getDefaults() { return tupleOrNone(defaults); }

    @Override
    void setDefaults(PyTuple defaults) {
        this.defaults = defaults.toArray();
        argParser.defaults(this.defaults);
    }

    @Override
    Object getKwdefaults() {
        return kwdefaults != null ? kwdefaults : Py.None;
    }

    @Override
    void setKwdefaults(PyDict kwdefaults) {
        this.kwdefaults = kwdefaults;
        argParser.kwdefaults(this.kwdefaults);
    }


    @Override
    CPython311Frame createFrame(Object locals) {
        return new CPython311Frame(this, locals);
    }


    // slot methods --------------------------------------------------

    @Override
    Object __call__(Object[] args, String[] names) throws Throwable {

        // Create a loose frame
        CPython311Frame frame = createFrame(null);

        // Fill the local variables that are arguments
        ArgParser.FrameWrapper wrapper =
                argParser.new ArrayFrameWrapper(frame.fastlocals);
        argParser.parseToFrame(wrapper, args, names);

        // Run the function body
        return frame.eval();
    }

    // FastCall support ----------------------------------------------



    // plumbing ------------------------------------------------------
    /**
     * Build {@link #argParser} to match the function object. The parser
     * must reflect variable names and the frame layout implied by the
     * code object, and also the default values of arguments held by the
     * function itself. We have to re-compute this if ever we replace
     * the code object in the function. This is called by
     * {@link #setCode(CPython311Code)}.
     */
    protected void buildParser() {
        CPython311Code c = code;
        int regargcount = c.argcount+ c.kwonlyargcount;
        ArgParser ap = new ArgParser(name, // name is unchanged
                c.names, regargcount, c.posonlyargcount,
                c.kwonlyargcount,
                c.traits.contains(PyCode.Trait.VARARGS),
                c.traits.contains(PyCode.Trait.VARKEYWORDS));
        argParser = ap.defaults(defaults).kwdefaults(kwdefaults);
    }
}
