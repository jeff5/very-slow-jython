package uk.co.farowl.vsj2.evo4;

import java.util.EnumSet;

import uk.co.farowl.vsj2.evo4.PyCode.Trait;

/** A {@link PyFrame} for executing CPython 3.8 byte code. */
class CPythonFrame extends PyFrame {

    static final PyType TYPE = new PyType("frame", PyCode.class);

    @Override
    public PyType getType() { return TYPE; }

    /*
     * Translation note: NB: in a CPython frame all local storage
     * local:cell:free:valuestack is one array into which pointers are
     * set during frame construction. For CPython byte code in Java,
     * three arrays seems to suit, but for a pure Java frame 4 would be
     * better.
     */

    /**
     * The concatenation of the cell and free variables (in that order).
     * We place these in a single array, and use the slightly confusing
     * CPython name, to maximise similarity with the CPython code for
     * opcodes LOAD_DEREF, STORE_DEREF, etc..
     * <p>
     * Non-local variables used in the current scope <b>and</b> a nested
     * scope are named in {@link PyCode#cellvars}. These come first.
     * <p>
     * Non-local variables used in the current scope or a nested scope,
     * <b>and</b> in an enclosing scope are named in
     * {@link PyCode#freevars}. During a call, these are provided in the
     * closure, copied to the end of this array.
     */
    final PyCell[] freevars;
    /** Simple local variables, named in {@link PyCode#varnames}. */
    final PyObject[] fastlocals;
    /** Value stack. */
    final PyObject[] valuestack;

    /** Index of first empty space on the value stack. */
    int stacktop = 0;

    /** Assigned eventually by return statement (or stays None). */
    PyObject returnValue = Py.None;

    private static final PyCell[] EMPTY_CELL_ARRAY = PyCell.EMPTY_ARRAY;

    /**
     * Create a {@code CPythonFrame}, which is a {@code PyFrame} with
     * the storage and mechanism to execute a module or isolated code
     * object (compiled to a {@link CPythonCode}.
     *
     * The caller specifies the local variables dictionary explicitly:
     * it may be the same as the {@code globals}.
     *
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space
     */
    CPythonFrame(Interpreter interpreter, PyCode code, PyDict globals,
            PyObject locals) {
        super(interpreter, code, globals, locals);
        valuestack = new PyObject[code.stacksize];
        freevars = EMPTY_CELL_ARRAY;

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)
                && traits.contains(PyCode.Trait.OPTIMIZED)) {
            fastlocals = new PyObject[code.nlocals];
        } else {
            fastlocals = null;
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
    CPythonFrame(Interpreter interpreter, PyCode code, PyDict globals,
            PyCell[] closure) {
        super(interpreter, code, globals);
        fastlocals = new PyObject[code.nlocals];
        valuestack = new PyObject[code.stacksize];
        int ncells = code.cellvars.length, nfree = code.freevars.length,
                n = ncells + nfree;
        if (n == 0)
            freevars = EMPTY_CELL_ARRAY;
        else {
            freevars = new PyCell[n];
            if (closure != null && closure.length != 0) {
                assert (nfree == closure.length);
                System.arraycopy(closure, 0, freevars, ncells, nfree);
            }
        }
        // Assume this supports an optimised function
        assert (code.traits.contains(Trait.NEWLOCALS));
        assert (code.traits.contains(Trait.OPTIMIZED));
    }

    /**
     * Create a frame from CPython vector call arguments, in the simple
     * case where only positional arguments are required and are
     * available in exactly the required number, and copy the arguments
     * into the {@link #fastlocals} of the frame.
     *
     * @param interpreter providing the module context
     * @param code that this frame executes
     * @param globals name space to treat as global variables
     * @param stack array containing {@code code.argcount} arguments
     * @param start start position in that array
     */
    CPythonFrame(Interpreter interpreter, PyCode code, PyDict globals,
            PyObject[] stack, int start) {
        super(interpreter, code, globals);
        this.fastlocals = new PyObject[code.nlocals];
        this.valuestack = new PyObject[code.stacksize];
        assert (code.cellvars.length == 0);
        this.freevars = EMPTY_CELL_ARRAY;
        // Assume this supports an optimised function
        assert (code.traits.contains(Trait.NEWLOCALS));
        assert (code.traits.contains(Trait.OPTIMIZED));
        // Avoid the array copy when short
        int n = code.argcount;
        switch (n) {
            case 4:
                fastlocals[1] = stack[start + 3];
            case 3:
                fastlocals[1] = stack[start + 2];
            case 2:
                fastlocals[1] = stack[start + 1];
            case 1:
                fastlocals[0] = stack[start];
            case 0:
                break;
            default:
                System.arraycopy(stack, start, fastlocals, 0, n);
        }
    }

    @Override
    PyObject getLocal(int i) { return fastlocals[i]; }

    @Override
    void setLocal(int i, PyObject v) { fastlocals[i] = v; }

    @Override
    void makeCell(int i, PyObject v) { freevars[i] = new PyCell(v); }

    /**
     * {@inheritDoc}
     * <p>
     * Optimised version for {@code CPythonFrame} copying to
     * {@link #fastlocals} directly.
     */
    @Override
    void setPositionalArguments(PyTuple args) {
        int n = Math.min(args.value.length, code.argcount);
        System.arraycopy(args.value, 0, fastlocals, 0, n);
    }

    /**
     * {@inheritDoc}
     * <p>
     * Optimised version for {@code CPythonFrame} copying to
     * {@link #fastlocals} directly.
     */
    @Override
    void setPositionalArguments(PyObject[] stack, int start,
            int nargs) {
        int n = Math.min(nargs, code.argcount);
        System.arraycopy(stack, start, fastlocals, 0, n);
    }

    @Override
    PyObject eval() {
        // Push the frame to the stack
        push();
        // Evaluation stack index
        int sp = this.stacktop;
        // Cached references from code
        PyUnicode[] names = code.names;
        PyObject[] consts = code.consts;
        byte[] inst = code.code.value;
        // Get first instruction
        int opcode = inst[0] & 0xff;
        int oparg = inst[1] & 0xff;
        int ip = 2;
        // Local variables used repeatedly in the loop
        PyObject name, res, u, v, w;
        PyObject func, args, kwargs;
        PyDict map;
        PyTuple kwnames;

        loop : for (;;) {
            try {

                // Interpret opcode
                switch (opcode) {

                    case Opcode.POP_TOP:
                        sp -= 1;
                        break;

                    case Opcode.ROT_TWO:
                        v = valuestack[sp - 1]; // TOP
                        valuestack[sp - 1] = valuestack[sp - 2];
                        valuestack[sp - 2] = v; // SET_SECOND
                        break;

                    case Opcode.DUP_TOP:
                        valuestack[sp] = valuestack[sp++ - 1]; // DUP
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

                    case Opcode.LOAD_BUILD_CLASS:
                        v = builtins.get(ID.__build_class__);
                        if (v == null)
                            throw new NameError(
                                    "__build_class__ not found");
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.RETURN_VALUE:
                        returnValue = valuestack[--sp]; // POP
                        break loop;

                    case Opcode.STORE_NAME:
                        name = names[oparg];
                        v = valuestack[--sp]; // POP
                        if (locals == null)
                            throw new SystemError(
                                    "no locals found when storing '%s'",
                                    name);
                        locals.put(name, v);
                        break;

                    case Opcode.STORE_ATTR: // v.name = w
                        // v | w | -> |
                        // -------^sp -^sp
                        sp -= 2; // SHRINK 2
                        v = valuestack[sp];
                        w = valuestack[sp + 1];
                        Abstract.setAttr(v, names[oparg], w);
                        break;

                    case Opcode.DELETE_ATTR: // del v.name
                        v = valuestack[--sp];
                        Abstract.setAttr(v, names[oparg], null);
                        break;

                    case Opcode.STORE_GLOBAL:
                        name = names[oparg];
                        v = valuestack[--sp]; // POP
                        globals.put(name, v);
                        break;

                    case Opcode.LOAD_CONST:
                        v = consts[oparg];
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_NAME:
                        name = names[oparg];

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
                                    throw new NameError(NAME_ERROR_MSG,
                                            name);
                            }
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_ATTR: // v.name
                        // v | -> | v.name |
                        // ---^sp ----------^sp
                        v = valuestack[sp - 1];
                        valuestack[sp - 1] =
                                Abstract.getAttr(v, names[oparg]);
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
                        while (--oparg >= 0) {
                            v = valuestack[--sp]; // POP
                            name = valuestack[--sp]; // POP
                            map.put(name, v);
                        }
                        valuestack[sp++] = map; // PUSH
                        break;

                    case Opcode.LOAD_GLOBAL:
                        name = names[oparg];
                        v = globals.get(name);
                        if (v == null) {
                            v = builtins.get(name);
                            if (v == null)
                                throw new NameError(NAME_ERROR_MSG,
                                        name);
                        }
                        valuestack[sp++] = v; // PUSH
                        break;

                    case Opcode.LOAD_FAST:
                        v = fastlocals[oparg];
                        if (v != null) {
                            valuestack[sp++] = v; // PUSH
                            break;
                        }
                        throw unboundLocal(oparg);

                    case Opcode.STORE_FAST:
                        fastlocals[oparg] = valuestack[--sp]; // POP
                        break;

                    case Opcode.DELETE_FAST:
                        v = fastlocals[oparg];
                        if (v != null) {
                            fastlocals[oparg] = null;
                            break;
                        }
                        throw unboundLocal(oparg);

                    case Opcode.CALL_FUNCTION:
                        // func | args[n] |
                        // ----------------^sp
                        // oparg = n
                        sp -= oparg; // STACK_SHRINK(oparg+1)
                        func = valuestack[sp - 1];
                        res = Callables.vectorcall(func, valuestack, sp,
                                oparg, null);
                        valuestack[sp - 1] = res; // PUSH
                        break;

                    case Opcode.MAKE_FUNCTION:
                        name = valuestack[--sp]; // POP

                        if (oparg == 0) {
                            /*
                             * Simple case: swap the code object on the
                             * stack for a function object.
                             */
                            valuestack[sp - 1] =
                                    new PyFunction(interpreter,
                                            (PyCode) valuestack[sp - 1],
                                            globals, (PyUnicode) name);
                            break;
                        } else {
                            /*
                             * Optional extras specified: make the
                             * function object and add them.
                             */
                            PyFunction pyfunc =
                                    new PyFunction(interpreter,
                                            (PyCode) valuestack[--sp],
                                            globals, (PyUnicode) name);
                            if ((oparg & 8) != 0)
                                pyfunc.setClosure(
                                        (PyTuple) valuestack[--sp]);
                            if ((oparg & 4) != 0)
                                pyfunc.setAnnotations(
                                        (PyDict) valuestack[--sp]);
                            if ((oparg & 2) != 0)
                                pyfunc.setKwdefaults(
                                        (PyDict) valuestack[--sp]);
                            if ((oparg & 1) != 0)
                                pyfunc.setDefaults(
                                        (PyTuple) valuestack[--sp]);
                            valuestack[sp++] = pyfunc; // PUSH
                            break;
                        }

                    case Opcode.LOAD_CLOSURE:
                        valuestack[sp++] = freevars[oparg]; // PUSH
                        break;

                    case Opcode.LOAD_DEREF:
                        v = freevars[oparg].obj;
                        if (v != null) {
                            valuestack[sp++] = v; // PUSH
                            break;
                        }
                        throw unboundDeref(oparg);

                    case Opcode.STORE_DEREF:
                        freevars[oparg].obj = valuestack[--sp]; // POP
                        break;

                    case Opcode.CALL_FUNCTION_KW:
                        // func | args[n] | kwargs[m] | kwnames |
                        // --------------------------------------^sp
                        // oparg = n + m and knames has m names
                        kwnames = (PyTuple) valuestack[--sp]; // POP
                        sp -= oparg; // STACK_SHRINK(oparg+1)
                        func = valuestack[sp - 1];
                        res = Callables.vectorcall(func, valuestack, sp,
                                oparg - kwnames.size(), kwnames);
                        valuestack[sp - 1] = res; // PUSH
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
                        // -------------^sp -----------^sp
                    case Opcode.BUILD_TUPLE_UNPACK:
                    case Opcode.BUILD_LIST_UNPACK:
                        // x[n] | --> z |
                        // ------^sp ----^sp
                        // oparg = n
                        // z = sum(x) where each x[i] is iterable
                        sp -= oparg; // STACK_SHRINK(oparg)
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
                                        : PyTuple.from(sum);
                        break;

                    case Opcode.BUILD_MAP_UNPACK:
                        // x[n] | --> map |
                        // ------^sp ------^sp
                        // oparg = n
                        // map = sum(x) where each x[i] is a mapping
                        map = Py.dict();
                        sp -= oparg; // STACK_SHRINK(oparg)
                        for (int i = 0, j = sp; i < oparg; i++) {
                            v = valuestack[j++];
                            try {
                                map.update(v);
                            } catch (AttributeError e) {
                                throw Abstract.typeError(
                                        "'%.200s' object is not a mapping",
                                        v);

                            }
                        }
                        valuestack[sp++] = map; // PUSH
                        break;

                    case Opcode.BUILD_MAP_UNPACK_WITH_CALL:
                        // func | a | x[n] | --> func | a | map |
                        // -----------------^sp -----------------^sp
                        // oparg = n
                        // map = sum(x) where each x[i] is a mapping
                        // Like BUILD_MAP_UNPACK tailored to calls
                        map = Py.dict();
                        sp -= oparg; // STACK_SHRINK(oparg)
                        for (int i = 0, j = sp; i < oparg; i++) {
                            v = valuestack[j++];
                            try {
                                // Duplicates are an error
                                map.merge(v, PyDict.MergeMode.UNIQUE);
                            } catch (PyException e) {
                                // throw tailored error
                                func = valuestack[sp - 2];
                                format_kwargs_error(e, func, v);
                            }
                        }
                        valuestack[sp++] = map; // PUSH
                        break;

                    case Opcode.BUILD_CONST_KEY_MAP:
                        // values[n] | keys | --> map |
                        // ------------------^sp ------^sp
                        // oparg = n, keys is a tuple of n names
                        map = Py.dict();
                        v = valuestack[--sp]; // POP
                        sp -= oparg; // STACK_SHRINK(oparg)
                        try {
                            PyObject[] keys = ((PyTuple) v).value;
                            if (keys.length != oparg)
                                throw new ValueError("length");
                            for (int i = 0, j = sp; i < oparg; i++)
                                map.put(keys[i], valuestack[j++]);
                        } catch (ClassCastException | ValueError e) {
                            throw new SystemError(BAD_MAP_KEYS);
                        }
                        valuestack[sp++] = map; // PUSH
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

    /**
     * Depending on the particular type of an exception {@code e} raised
     * when processing the {@code kwargs} to function {@code func},
     * throw an appropriate {@link TypeError}.
     */
    private void format_kwargs_error(PyException e, PyObject func,
            PyObject kwargs) {
        if (e instanceof AttributeError) {
            throw new TypeError(ARGUMENT_AFTER_STARSTAR,
                    getFuncName(func), getFuncDesc(func),
                    kwargs.getType().name);
        } else if (e instanceof KeyError) {
            // In the circumstances, KeyError should signify duplicate
            KeyError ke = (KeyError) e;
            if (ke.key != null) {
                if (ke.key instanceof PyUnicode) {
                    throw new TypeError(MULTIPLE_VALUES,
                            getFuncName(func), getFuncDesc(func),
                            ke.key);
                } else {
                    throw new TypeError(MUST_BE_STRINGS,
                            getFuncName(func), getFuncDesc(func));
                }
            } else
                // Some other kind of KeyError: let it explain itself.
                throw ke;
        } else
            // Something else went wrong: let it explain itself.
            throw e;
    }

    private static final String ARGUMENT_AFTER_STAR =
            "%.200s%.200s argument after * "
                    + "must be an iterable, not %.200s";
    private static final String ARGUMENT_AFTER_STARSTAR =
            "%.200s%.200s argument after ** "
                    + "must be a mapping, not %.200s";
    private static final String MUST_BE_STRINGS =
            "%.200s%.200s keywords must be strings";
    private static final String MULTIPLE_VALUES =
            "%.200s%.200s got multiple "
                    + "values for keyword argument '%s'";
    private static final String BAD_MAP_KEYS =
            "bad BUILD_CONST_KEY_MAP keys argument";
    private static final String NAME_ERROR_MSG =
            "name '%.200s' is not defined";
    private static final String UNBOUNDLOCAL_ERROR_MSG =
            "local variable '%.200s' referenced before assignment";
    private static final String UNBOUNDFREE_ERROR_MSG =
            "free variable '%.200s' referenced before assignment"
                    + " in enclosing scope";

    // XXX move this error reporting stuff to base PyFrame

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

    private UnboundLocalError unboundLocal(int oparg) {
        return new UnboundLocalError(UNBOUNDLOCAL_ERROR_MSG,
                code.varnames[oparg]);
    }

    private NameError unboundDeref(int oparg) {
        int ncells = code.cellvars.length;
        if (oparg < ncells) {
            /*
             * This is a cell variable: a non-local variable used in the
             * current scope and a nested scope, named in
             * code.cellvars[].
             */
            return new UnboundLocalError(UNBOUNDLOCAL_ERROR_MSG,
                    code.cellvars[oparg]);
        } else {
            /*
             * This is a free variable: a non-local used in the current
             * scope or a nested scope, and in an enclosing scope, named
             * in code.freevars[]. During a call, these are provided in
             * the closure.
             */
            return new UnboundLocalError(UNBOUNDFREE_ERROR_MSG,
                    code.freevars[oparg - ncells]);
        }
    }
}
