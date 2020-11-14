package uk.co.farowl.vsj2.evo3;

/**
 * Our equivalent to the Python code object ({@code PyCodeObject} in
 * CPython's C API).
 */
class CPythonCode extends PyCode {

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}. The {@link #traits} of the
     * code are supplied here as CPython reports them: as a bit array in
     * an integer, but the constructor makes a conversion, and it is the
     * {@link #traits} which should be used at the Java level.
     *
     * @param argcount value of {@link PyCode#argcount}
     * @param posonlyargcount value of {@link PyCode#posonlyargcount}
     * @param kwonlyargcount value of {@link PyCode#kwonlyargcount}
     * @param nlocals value of {@link PyCode#nlocals}
     * @param stacksize value of {@link PyCode#stacksize}
     * @param flags value of {@link PyCode#flags} and
     *            {@link PyCode#traits}
     * @param code value of {@link PyCode#code}
     * @param consts value of {@link PyCode#consts}
     * @param names value of {@link PyCode#names}
     * @param varnames value of {@link PyCode#varnames} must be
     *            {@code str}
     * @param freevars value of {@link PyCode#freevars} must be
     *            {@code str}
     * @param cellvars value of {@link PyCode#cellvars} must be
     *            {@code str}
     * @param filename value of {@link PyCode#filename} must be
     *            {@code str}
     * @param name value of {@link PyCode#name}
     * @param firstlineno value of {@link PyCode#firstlineno}
     * @param lnotab value of {@link PyCode#lnotab}
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
            PyCell[] closure) {
        CPythonFrame f =
                new CPythonFrame(interpreter, this, globals, closure);
        return f;
    }

    @Override
    PyFrame fastFrame(Interpreter interpreter, PyDict globals,
            PyObject[] stack, int start) {
        CPythonFrame f = new CPythonFrame(interpreter, this, globals,
                stack, start);
        return f;
    }

}
