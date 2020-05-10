package uk.co.farowl.vsj2.evo3;

/**
 * Our equivalent to the Python code object ({@code PyCodeObject} in
 * CPython's C API).
 */
class CPythonCode extends PyCode {

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}.
     */
    public CPythonCode( //
            int argcount,           // co_argcount
            int posonlyargcount,    // co_posonlyargcount
            int kwonlyargcount,     // co_kwonlyargcount
            int nlocals,            // co_nlocals
            int stacksize,          // co_stacksize
            int flags,              // co_flags

            PyBytes code,           // co_code

            PyTuple consts,         // co_consts

            PyTuple names,          // names ref'd in code
            PyTuple varnames,       // args and non-cell locals
            PyTuple freevars,       // ref'd here, def'd outer
            PyTuple cellvars,       // def'd here, ref'd nested

            PyUnicode filename,     // loaded from
            PyUnicode name,         // of function etc.
            int firstlineno,        // of source
            PyBytes lnotab          // map opcode address to source
    ) {
        super(argcount, posonlyargcount, kwonlyargcount, nlocals,
                stacksize, flags, code, consts, names, varnames,
                freevars, cellvars, filename, name, firstlineno,
                lnotab);
        // A few of these (just a few) should be local to this type.
    }

    @Override
    PyFrame createFrame(Interpreter interpreter, PyDict globals,
            PyObject locals) {
        return new CPythonFrame(interpreter, this, globals, locals);
    }

    @Override
    CPythonFrame createFrame(Interpreter interpreter, PyDict globals,
            PyTuple closure) {
        CPythonFrame f =
                new CPythonFrame(interpreter, this, globals, closure);
        return f;
    }

}
