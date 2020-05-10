package uk.co.farowl.vsj2.evo3;

import java.util.Arrays;
import java.util.EnumSet;

import uk.co.farowl.vsj2.evo3.PyCode.Trait;

/** A {@link PyFrame} for executing CPython 3.8 byte code. */
class CPythonFrame extends PyFrame {

    static final PyType TYPE = new PyType("frame", PyCode.class);

    @Override
    public PyType getType() { return TYPE; }

    /**
     * Non-local variables used in the current scope or a nested scope,
     * <b>and</b> in an enclosing scope. These are named in
     * {@link PyCode#freevars}. During a call, these are provided in the
     * closure.
     */
    final PyCell[] freevars;
    /**
     * Non-local variables used in the current scope <b>and</b> a nested
     * scope. These are named in {@link PyCode#cellvars}.
     */
    final PyCell[] cellvars;
    /** Simple local variables, named in {@link PyCode#varnames}. */
    final PyObject[] fastlocals;
    /** Value stack. */
    final PyObject[] valuestack;
    /** Index of first empty space on the value stack. */
    int stacktop = 0;

    /** Assigned eventually by return statement (or stays None). */
    PyObject returnValue = Py.None;

    private static final String NAME_ERROR_MSG =
            "name '%.200s' is not defined";
    private static final String UNBOUNDLOCAL_ERROR_MSG =
            "local variable '%.200s' referenced before assignment";
    private static final String UNBOUNDFREE_ERROR_MSG =
            "free variable '%.200s' referenced before assignment"
                    + " in enclosing scope";

    private static final PyCell[] EMPTY_CELL_ARRAY = new PyCell[0];


    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
     * the storage and mechanism to execute a module or isolated
     * code object (compiled to a {@link CPythonCode}.
     *
     * The caller specifies the local variables dictionary explicitly:
     * it may be the same as the {@code globals}.
     *
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space
     */
    CPythonFrame(Interpreter interpreter, PyCode code,
            PyDictionary globals, PyObject locals) {
        super(interpreter, code, globals, locals);
        this.valuestack = new PyObject[code.stacksize];
        freevars = EMPTY_CELL_ARRAY;
        cellvars = EMPTY_CELL_ARRAY;

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)
                && traits.contains(PyCode.Trait.OPTIMIZED)) {
            this.fastlocals = new PyObject[code.nlocals];
        } else {
            this.fastlocals = null;
        }
    }

    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
     * the storage and mechanism to execute code, suitable for a
     * function.
     *
     * @param interpreter providing the module context
     * @param code that this frame executes
     * @param globals global name space
     * @param closure closure from function
     */
    CPythonFrame(Interpreter interpreter, PyCode code,
            PyDictionary globals, PyTuple closure) {
        super(interpreter, code, globals);
        this.fastlocals = new PyObject[code.nlocals];
        this.valuestack = new PyObject[code.stacksize];
        int n = code.cellvars.value.length;
        this.cellvars = n > 0 ? new PyCell[n] : EMPTY_CELL_ARRAY;
        if (closure == null)
            this.freevars = EMPTY_CELL_ARRAY;
        else
            this.freevars = Arrays.copyOf(closure.value,
                    closure.value.length, PyCell[].class);
        assert (code.freevars.value.length == this.freevars.length);

        // Assume this supports an optimised function
        assert (code.traits.contains(Trait.NEWLOCALS));
        assert (code.traits.contains(Trait.OPTIMIZED));
    }

    @Override
    PyObject getLocal(int i) { return fastlocals[i]; }

    @Override
    void setLocal(int i, PyObject v) { fastlocals[i] = v; }

    @Override
    void makeCell(int i, PyObject v) { cellvars[i] = new PyCell(v); }

    /**
     * {@inheritDoc}
     * <p>
     * Optimised version for {@code CPythonFrame} copying to
     * {@link #fastlocals} directly.
     */
    @Override
    int setPositionalArguments(PyTuple args) {
        // Copy the allowed number (or fewer)
        int nargs = args.value.length;
        int n = Math.min(nargs, code.argcount);
        System.arraycopy(args.value, 0, fastlocals, 0, n);
        if (code.traits.contains(Trait.VARARGS)) {
            // Locate the * argument and put any excess there
            int varIndex = code.argcount + code.kwonlyargcount;
            if (nargs > n)
                fastlocals[varIndex] =
                        new PyTuple(args.value, n, nargs - n);
            else
                fastlocals[varIndex] = PyTuple.EMPTY;
        }
        // Return number copied to locals
        return n;
    }

    @Override
    PyObject eval() {
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
        PyObject name, res, u, v, w;
        PyObject func, args, kwargs;
        PyDictionary map;

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

                    case Opcode.BINARY_SUBSCR: // w[v]
                        v = valuestack[--sp]; // POP
                        w = valuestack[sp - 1]; // TOP
                        res = Abstract.getItem(w, v);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.STORE_SUBSCR: // w[v] = u
                        v = valuestack[sp - 1]; // TOP
                        w = valuestack[sp - 2]; // SECOND
                        u = valuestack[sp - 3]; // THIRD
                        sp -= 3; // STACK_SHRINK(3);
                        Abstract.setItem(w, v, u);
                        break;

                    case Opcode.BINARY_AND:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.and(v, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BINARY_OR:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.or(v, w);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BINARY_XOR:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        res = Number.xor(v, w);
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
                                    throw new PyException(
                                            NAME_ERROR_MSG, name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.COMPARE_OP:
                        w = valuestack[--sp]; // POP
                        v = valuestack[sp - 1]; // TOP
                        Comparison cmpOp = Comparison.from(oparg);
                        switch (cmpOp) {
                            case IS_NOT:
                                res = v != w ? PyBool.True
                                        : PyBool.False;
                                break;
                            case IS:
                                res = v == w ? PyBool.True
                                        : PyBool.False;
                                break;
                            case NOT_IN:
                            case IN:
                            case EXC_MATCH:
                                throw cmpError(cmpOp);
                            default:
                                res = Abstract.richCompare(v, w, cmpOp);
                        }
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.JUMP_FORWARD:
                        ip += oparg; // JUMPBY
                        break;

                    case Opcode.JUMP_IF_FALSE_OR_POP:
                        v = valuestack[--sp]; // POP
                        if (!Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = oparg; // JUMPTO
                        }
                        break;

                    case Opcode.JUMP_IF_TRUE_OR_POP:
                        v = valuestack[--sp]; // POP
                        if (Abstract.isTrue(v)) {
                            sp += 1;    // UNPOP
                            ip = oparg; // JUMPTO
                        }
                        break;

                    case Opcode.JUMP_ABSOLUTE:
                        ip = oparg; // JUMPTO
                        break;

                    case Opcode.POP_JUMP_IF_FALSE:
                        v = valuestack[--sp]; // POP
                        if (!Abstract.isTrue(v))
                            ip = oparg; // JUMPTO
                        break;

                    case Opcode.POP_JUMP_IF_TRUE:
                        v = valuestack[--sp]; // POP
                        if (Abstract.isTrue(v))
                            ip = oparg; // JUMPTO
                        break;

                    case Opcode.BUILD_TUPLE:
                        sp -= oparg; // STACK_SHRINK(oparg)
                        w = new PyTuple(valuestack, sp, oparg);
                        valuestack[sp++] = w; // PUSH
                        break;

                    case Opcode.BUILD_LIST:
                        sp -= oparg; // STACK_SHRINK(oparg)
                        w = new PyList(valuestack, sp, oparg);
                        valuestack[sp++] = w; // PUSH
                        break;

                    case Opcode.BUILD_MAP:
                        map = Py.dict();
                        while (--oparg > 0) {
                            v = valuestack[--sp]; // POP
                            name = valuestack[--sp]; // POP
                            map.put(name, v);
                        }
                        valuestack[sp++] = map; // PUSH
                        break;

                    case Opcode.LOAD_FAST:
                        v = fastlocals[oparg];
                        if (v != null) {
                            valuestack[sp++] = v; // PUSH
                            break;
                        }
                        throw new UnboundLocalError(
                                UNBOUNDLOCAL_ERROR_MSG,
                                code.varnames.value[oparg]);

                    case Opcode.STORE_FAST:
                        fastlocals[oparg] = valuestack[--sp]; // POP
                        break;

                    case Opcode.DELETE_FAST:
                        v = fastlocals[oparg];
                        if (v != null) {
                            fastlocals[oparg] = null;
                            break;
                        }
                        throw new UnboundLocalError(
                                UNBOUNDLOCAL_ERROR_MSG,
                                code.varnames.value[oparg]);

                    case Opcode.CALL_FUNCTION:
                        // func | args[n] |
                        // ----------------^sp
                        // oparg = n
                        sp -= oparg + 1; // STACK_SHRINK(oparg+1)
                        func = valuestack[sp];
                        res = Callables.vectorcall(func, valuestack,
                                sp + 1, oparg, null);
                        valuestack[sp++] = res; // PUSH
                        break;

                    case Opcode.MAKE_FUNCTION:
                        name = valuestack[--sp]; // POP

                        if (oparg == 0) {
                            /*
                             * Simple case: swap the code object on the
                             * stack for a function object.
                             */
                            valuestack[sp - 1] = new PyFunction(
                                    (PyCode) valuestack[sp - 1],
                                    globals, (PyUnicode) name);

                        } else {
                            /*
                             * Optional extras specified: make the
                             * function object and add them.
                             */
                            PyFunction pyfunc = new PyFunction(
                                    (PyCode) valuestack[--sp], globals,
                                    (PyUnicode) name);
                            if ((oparg & 8) == 0)
                                pyfunc.setClosure(
                                        (PyTuple) valuestack[--sp]);
                            if ((oparg & 4) == 0)
                                pyfunc.setAnnotations(
                                        (PyDictionary) valuestack[--sp]);
                            if ((oparg & 2) == 0)
                                pyfunc.setKwdefaults(
                                        (PyDictionary) valuestack[--sp]);
                            if ((oparg & 1) == 0)
                                pyfunc.setDefaults(
                                        (PyTuple) valuestack[--sp]);
                            valuestack[sp++] = pyfunc; // PUSH
                        }
                        break;

                    case Opcode.CALL_FUNCTION_KW:
                        // func | args[n] | kwargs[m] | kwnames |
                        // --------------------------------------^sp
                        // oparg = n + m and knames has m names
                        break;

                    case Opcode.CALL_FUNCTION_EX:
                        kwargs = oparg == 0 ? null : valuestack[--sp];
                        args = valuestack[--sp]; // POP
                        func = valuestack[sp - 1]; // TOP
                        res = Callables.call(func, args, kwargs);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

                    case Opcode.BUILD_TUPLE_UNPACK_WITH_CALL:
                        // func | x[n] | --> func | z |
                        // -------------^sp ----------^sp
                    case Opcode.BUILD_TUPLE_UNPACK:
                    case Opcode.BUILD_LIST_UNPACK:
                        // x[n] | --> z |
                        // ------^sp ---^sp
                        // oparg = n
                        // z = sum(x) where each x[i] is iterable
                        sp -= oparg;
                        PyList sum = Py.list();
                        for (int i = 0; i < oparg; i++) {
                            v = valuestack[sp + i];
                            try {
                                sum.extend(v);
                            } catch (TypeError te) {
                                if (opcode == Opcode.//
                                        BUILD_TUPLE_UNPACK_WITH_CALL) {
                                    // throw tailored error
                                    func = valuestack[sp - 1];
                                    checkArgsIterable(func, v);
                                }
                                throw te;
                            }
                        }
                        valuestack[sp++] = // PUSH
                                opcode == Opcode.BUILD_LIST_UNPACK ? sum
                                        : PyTuple.fromList(sum);
                        break;

                    default:
                        throw new SystemError("ip: %d, opcode: %d",
                                ip - 2, opcode);
                } // switch

                // Pick up the next instruction
                opcode = inst[ip] & 0xff;
                oparg = inst[ip + 1] & 0xff;
                ip += 2;
            } catch (PyException pye) {
                /*
                 * We ought here to check for exception handlers
                 * (defined in Python and reflected in the byte code)
                 * potentially resuming the loop with ip at the handler
                 * code, or in a Python finally clause.
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
                 * A non-Python exception signals an internal error, in
                 * our implementation, in user-supplied Java, or from a
                 * Java library misused from Python.
                 */
                // Should handle within Python, but for now, stop.
                t.printStackTrace();
                throw new InterpreterError("Non-PyException", t);
            }
        } // loop

        ThreadState.get().swap(back);
        return returnValue;
    }

    private InterpreterError cmpError(Comparison cmpOp) {
        return new InterpreterError("Comparison '%s' not implemented",
                cmpOp);
    }

    /**
     * If the {@code args} to the function {@code func} are not
     * iterable, throw a {@link TypeError}.
     */
    private void checkArgsIterable(PyObject func, PyObject args) {
        if (args == null || Slot.tp_iter.isDefinedFor(args.getType()))
            return;
        if (Sequence.check(args))
            return;
        throw new TypeError(ARGUMENT_AFTER_STAR, getFuncName(func),
                getFuncDesc(func), args.getType().name);
    }

    private static final String ARGUMENT_AFTER_STAR =
            "%.200s%.200s argument after * "
                    + "must be an iterable, not %.200s";

    private static String getFuncName(PyObject func) {
        // if (func instanceof PyMethod) return
        // getFuncName(PyMethod_GET_FUNCTION(func)); else
        if (func instanceof PyFunction)
            return ((PyFunction) func).name.toString();
        else if (func instanceof PyJavaFunction)
            return ((PyJavaFunction) func).methodDef.name;
        else
            return func.getType().name;
    }

    private static String getFuncDesc(PyObject func) {
        // XXX De-comment when PyMethod defined
        if (// func instanceof PyMethod ||
        func instanceof PyFunction || func instanceof PyJavaFunction)
            return "()";
        else
            return " object";
    }
}
