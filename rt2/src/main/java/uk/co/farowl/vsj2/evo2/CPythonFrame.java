package uk.co.farowl.vsj2.evo2;

/** A {@link PyFrame} for executing CPython 3.8 byte code. */
class CPythonFrame extends PyFrame {
    static final PyType TYPE = new PyType("frame", PyCode.class);
    @Override
    public PyType getType() { return TYPE; }

    /** Cells for free variables (used not created in this code). */
    final Cell[] freevars;
    /** Cells for local cell variables (created in this code). */
    final Cell[] cellvars;
    /** Local simple variables (corresponds to "varnames"). */
    final PyObject[] fastlocals;
    /** Value stack. */
    final PyObject[] valuestack;
    /** Index of first empty space on the value stack. */
    int stacktop = 0;

    /** Assigned eventually by return statement (or stays None). */
    PyObject returnValue = Py.None;

    private static String NAME_ERROR_MSG = "name '%.200s' is not defined";

    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with the
     * storage and mechanism to execute code. The constructor specifies the
     * back-reference to the current frame (which is {@code null} when this
     * frame is first in the stack) via the {@code ThreadState}. No other
     * argument may be {@code null}.
     *
     * The caller specifies the local variables dictionary explicitly: it
     * may be the same as the {@code globals}.
     *
     * @param tstate thread state (supplies back)
     * @param code that this frame executes
     * @param globals global name space
     * @param locals local name space
     */
    CPythonFrame(ThreadState tstate, PyCode code, PyDictionary globals,
            PyObject locals) {
        super(tstate, code, globals, locals);
        this.valuestack = new PyObject[code.stacksize];
        this.fastlocals = null;
        this.freevars = null;
        this.cellvars = null;
    }

    @Override
    PyObject eval() {
        // Push this frame to stack
        back = tstate.swap(this);
        // Evaluation stack index
        int sp = this.stacktop;
        // Cached references from code
        PyTuple names = code.names;
        PyTuple consts = code.consts;
        byte[] inst = code.code.value;
        // Get first instruction
        int opcode = inst[0] & 0xff;
        int oparg = inst[1] & 0xff;
        int ip = 2;
        // Local variables used repeatedly in the loop
        PyObject name, res, v, w;

        loop : for (;;) {
            try {

                // Interpret opcode
                switch (opcode) {

                    case Opcode.ROT_TWO:
                        v = valuestack[sp - 1]; // TOP
                        valuestack[sp - 1] = valuestack[sp - 2];
                        valuestack[sp - 2] = v; // SET_SECOND
                        break;

                    case Opcode.UNARY_NEGATIVE:
                        v = valuestack[sp - 1]; // TOP
                        res = Number.negative(v);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BINARY_MULTIPLY:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.multiply(v, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BINARY_ADD:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.add(v, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BINARY_SUBTRACT:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.subtract(v, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.RETURN_VALUE:
                        returnValue = valuestack[--sp]; // POP
                        break loop;

                    case Opcode.STORE_NAME:
                        name = names.getItem(oparg);
                        v = valuestack[--sp]; // POP
                        if (locals == null)
                            throw new SystemError(
                                    "no locals found when storing '%s'",
                                    name);
                        locals.put(name, v);
                        break;

                    case Opcode.LOAD_CONST:
                        v = consts.getItem(oparg);
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_NAME:
                        name = names.getItem(oparg);

                        if (locals == null)
                            throw new SystemError(
                                    "no locals found when loading '%s'",
                                    name);
                        v = locals.get(name);
                        if (v == null) {
                            v = globals.get(name);
                            if (v == null) {
                                v = builtins.get(name);
                                if (v == null)
                                    throw new PyException(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    default:
                        throw new SystemError("ip: %d, opcode: %d", ip - 2,
                                opcode);
                } // switch

                // Pick up the next instruction
                opcode = inst[ip] & 0xff;
                oparg = inst[ip + 1] & 0xff;
                ip += 2;
            } catch (PyException pye) {
                /*
                 * We ought here to check for exception handlers (defined
                 * in Python and reflected in the byte code) potentially
                 * resuming the loop with ip at the handler code, or in a
                 * Python finally clause.
                 */
                // Should handle within Python, but for now, stop.
                throw pye;
            } catch (InterpreterError ie) {
                /*
                 * An InterpreterError signals an internal error,
                 * recognised by our implementation: stop.
                 */
                throw ie;
            } catch (Throwable t) {
                /*
                 * A non-Python exception signals an internal error, in our
                 * implementation, in user-supplied Java, or from a Java
                 * library misused from Python.
                 */
                // Should handle within Python, but for now, stop.
                throw new InterpreterError("PyException", t);
            }
        } // loop

        tstate.swap(back);
        return returnValue;
    }
}
