package uk.co.farowl.vsj2.evo3;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj2.evo3.PyCode.Trait;

/** A {@code PyFrame} is the context for the execution of code. */
abstract class PyFrame implements PyObject {

    /** Frames form a stack by chaining through the back pointer. */
    PyFrame back;
    /** Code this frame is to execute. */
    final PyCode code;
    /** Interpreter owning this frame. */
    protected final Interpreter interpreter;
    /** Built-in objects. */
    protected PyDict builtins;
    /** Global context (name space) of execution. */
    final PyDict globals;
    /** Local context (name space) of execution. (Assign if needed.) */
    Map<PyObject, PyObject> locals = null;

    /**
     * Foundation constructor on which subclass constructors rely.
     *
     * This provides a "loose" frame that is not yet part of any stack
     * until explicitly pushed (with {@link #push()}. A frame always
     * belongs to an {@link Interpreter}, but it does not necessarily
     * belong to a particular {@link ThreadState}.
     *
     * In particular, the {@link #back} pointer is {@code null} in the
     * newly-created frame.
     *
     * @param interpreter providing the module context
     * @param code that this frame executes
     * @param globals global name space
     * @throws TypeError if {@code globals['__builtins__']} is invalid
     */
    protected PyFrame(Interpreter interpreter, PyCode code,
            PyDict globals) throws TypeError {
        this.code = code;
        this.interpreter = interpreter;
        this.globals = globals;
    }

    /**
     * Find or create a {@code dict} to be the built-ins of this frame.
     * If the frame at {@link #back} exists and it has the same
     * {@link #globals}, we get the result from there. Otherwise, either
     * the {@code __builtins__} element of {@link #globals} provides it,
     * or we create a minimal dictionary.
     * {@code globals['__builtins__']}, if present, must be a Python
     * {@code module} or {@code dict}.
     * <p>
     * The caller is responsible for assigning {@link #builtins}.
     *
     * @return a {@code dict} suitable as the built-ins of this frame
     */
    private PyDict inferBuiltins() {
        if (back != null && back.globals == globals)
            // Same globals, same builtins.
            return back.builtins;
        else {
            PyObject b = globals.get(Py.BUILTINS);
            if (b != null) {
                // Normally, globals[__builtins__] is a module
                if (b instanceof PyModule)
                    return ((PyModule) b).dict;
                else if (b instanceof PyDict)
                    return (PyDict) b;
                throw new TypeError("%s should be module not %s",
                        Py.BUILTINS, b);
            } else {
                // Substitute minimal builtins
                PyDict builtins = new PyDict();
                builtins.put("None", Py.None);
                return builtins;
            }
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
     * @param code that this frame executes
     * @param interpreter providing the module context
     * @param globals global name space
     * @param locals local name space (or it may be {@code globals})
     */
    protected PyFrame(Interpreter interpreter, PyCode code,
            PyDict globals, PyObject locals) {

        // Initialise the basics.
        this(interpreter, code, globals);

        // The need for a dictionary of locals depends on the code
        EnumSet<PyCode.Trait> traits = code.traits;
        if (traits.contains(PyCode.Trait.NEWLOCALS)) {
            // Ignore locals argument
            if (traits.contains(PyCode.Trait.OPTIMIZED)) {
                // We can create it later but probably won't need to
                this.locals = null;
            } else {
                this.locals = new PyDict();
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

    /** Push the frame to the stack of the current thread. */
    void push() {
        // Push this frame to stack
        ThreadState tstate = ThreadState.get();
        back = tstate.swap(this);
        // Infer builtins module now back is set.
        builtins = inferBuiltins();
    }

    /** Execute the code in this frame. */
    abstract PyObject eval();

    /** Get the local variable named by {@code code.varnames[i]} */
    abstract PyObject getLocal(int i);

    /** Set the local variable named by {@code code.varnames[i]} */
    abstract void setLocal(int i, PyObject v);

    /**
     * Create a cell for the non-local bound variable named by
     * {@code code.cellvars[i]}
     */
    abstract void makeCell(int i, PyObject v);

    /**
     * Copy positional arguments into local variables, making sure we
     * don't copy more than have been allowed for in the frame. Excess
     * will go into a VARARGS tuple if allowed, but providing too few is
     * not an error at this stage, as missing ones may be present in the
     * call as keyword arguments, or in the function as defaults.
     *
     * @param args positional arguments
     * @return arguments actually copied to local variables
     */
    int setPositionalArguments(PyTuple args) {

        int nargs = args.value.length;
        int n = Math.min(nargs, code.argcount);

        // Copy the allowed number (or fewer)
        for (int i = 0; i < n; i++)
            setLocal(i, args.value[i]);

        if (code.traits.contains(Trait.VARARGS)) {
            // Locate the * argument and put any excess there
            int varIndex = code.argcount + code.kwonlyargcount;
            if (nargs > n)
                setLocal(varIndex,
                        new PyTuple(args.value, n, nargs - n));
            else
                setLocal(varIndex, PyTuple.EMPTY);
        }

        // Return number copied to locals
        return n;
    }

    /**
     * For each of the names used as keywords in the call, match it with
     * an allowable parameter name, and assign that frame-local variable
     * the keyword argument given in the call. If the variable is not
     * null, this is an error.
     * <p>
     * "Allowable parameter name" here means the names in
     * {@code code.varnames[p:q]} where {@code p=code.posonlyargcount}
     * and {@code q=code.argcount + code.kwonlyargcount}. If the name
     * used in the call is not is not an allowable keyword, then if
     * {@code code} has the VARKEYWORDS trait, add it to the frame's
     * keyword dictionary, otherwise throw an informative error.
     * <p>
     * In this version, accept the keyword arguments passed as a
     * dictionary, as in the "classic" {@code (*args, **kwargs)} call.
     *
     * @param kwargs keyword arguments given in call
     */
    void setKeywordArguments(PyDict kwargs) {
        /*
         * Create a dictionary for the excess keyword parameters, and
         * insert it in the local variables at the proper position.
         */
        int totalArgs = code.argcount + code.kwonlyargcount;
        PyDict kwdict = null;
        if (code.traits.contains(Trait.VARKEYWORDS)) {
            kwdict = Py.dict();
            int kwargsIndex = code.traits.contains(Trait.VARARGS)
                    ? totalArgs + 1 : totalArgs;
            setLocal(kwargsIndex, kwdict);
        }

        /*
         * For each entry in kwargs, search code.varnames for a match,
         * and either assign the local variable or add the name-value
         * pair to kwdict.
         */
        for (Map.Entry<PyObject, PyObject> entry : kwargs.entrySet()) {
            PyObject name = entry.getKey();
            PyObject value = entry.getValue();
            int index = varnameIndexOf(name);

            if (index < 0) {
                // Not found in (allowed slice of) code.varnames
                if (kwdict != null)
                    // Put unmatched (name, value) in VARKEYWORDS dict.
                    kwdict.put(name, value);
                else
                    // No VARKEYWORDS dict: everything must match.
                    throw unexpectedKeyword(name, kwargs.keySet());
            } else {
                // Keyword found to name allowable variable at index
                if (getLocal(index) == null)
                    setLocal(index, value);
                else
                    // Unfortunately, that seat is already taken
                    throw new TypeError(MULTIPLE_VALUES, code.name,
                            name);
            }
        }
    }

    /**
     * For each of the names used as keywords in the call, match it with
     * an allowable parameter name, and assign that frame-local variable
     * the keyword argument given in the call. If the variable is not
     * null, this is an error.
     * <p>
     * "Allowable parameter name" here means the names in
     * {@code code.varnames[p:q]} where {@code p=code.posonlyargcount}
     * and {@code q=code.argcount + code.kwonlyargcount}. If the name
     * used in the call is not is not an allowable keyword, then if
     * {@code code} has the VARKEYWORDS trait, add it to the frame's
     * keyword dictionary, otherwise throw an informative error.
     * <p>
     * In this version, accept the keyword arguments passed as two
     * arrays, with a slice convention. This is primarily to support
     * CPython's vector convention in which the values are a slice of
     * the interpreter stack.
     *
     * @param kwnames keywords used in the call (or {@code **kwargs})
     * @param kwvalues {@code [kwstart:kwstart+len(kwnames)]} values
     *            corresponding to {@code kwnames} in order
     * @param kwstart start position in {@code kwvalues}
     */
    void setKeywordArguments(PyObject[] kwnames, PyObject[] kwvalues,
            int kwstart) {
        /*
         * Create a dictionary for the excess keyword parameters, and
         * insert it in the local variables at the proper position.
         */
        int total_args = code.argcount + code.kwonlyargcount;
        PyDict kwdict = null;
        if (code.traits.contains(Trait.VARKEYWORDS)) {
            kwdict = Py.dict();
            int kwargsIndex = code.traits.contains(Trait.VARARGS)
                    ? total_args + 1 : total_args;
            setLocal(kwargsIndex, kwdict);
        }

        /*
         * For each of the names in kwnames, search code.varnames for a
         * match, and either assign the local variable or add the
         * name-value pair to kwdict.
         */
        int kwcount = kwnames == null ? 0 : kwnames.length;
        for (int i = 0; i < kwcount; i++) {
            PyObject name = kwnames[i];
            PyObject value = kwvalues[i];
            int index = varnameIndexOf(name);

            if (index < 0) {
                // Not found in (allowed slice of) code.varnames
                if (kwdict != null)
                    // Put unmatched (name, value) in VARKEYWORDS dict.
                    kwdict.put(name, value);
                else
                    // No VARKEYWORDS dict: everything must match.
                    throw unexpectedKeyword(name,
                            Arrays.asList(kwnames));
            } else {
                // Keyword found to name allowable variable at index
                if (getLocal(index) == null)
                    setLocal(index, value);
                else
                    // Unfortunately, that seat is already taken
                    throw new TypeError(MULTIPLE_VALUES, code.name,
                            name);
            }

            if (name == null || !(name instanceof PyUnicode)) {
                throw new TypeError(KEYWORD_NOT_STRING, code.name);
            }

        }
    }

    /**
     * Find the given name in {@code code.varnames}, and if it is not
     * found, return -1. Only the "allowable parameter names", those
     * acceptable as keyword arguments, are searched. It is an error if
     * the name is not a Python {@code str}.
     *
     * @param name parameter name given as keyword
     * @return index of {@code name} in {@code code.varnames} or -1
     */
    private int varnameIndexOf(PyObject name) {

        PyObject[] varnames = code.varnames.value;
        int end = code.argcount + code.kwonlyargcount;

        if (name == null || !(name instanceof PyUnicode)) {
            throw new TypeError(KEYWORD_NOT_STRING, code.name);
        }

        /*
         * For speed, try raw pointer comparison. As names are normally
         * interned this should almost always hit.
         */
        for (int i = code.posonlyargcount; i < end; i++) {
            if (varnames[i] == name)
                return i;
        }

        /*
         * It's not definitive until we have repeated the search using
         * proper object comparison.
         */
        for (int i = code.posonlyargcount; i < end; i++) {
            if (Abstract.richCompareBool(name, varnames[i],
                    Comparison.EQ, null))
                return i;
        }

        return -1;
    }

    /**
     * Diagnose an unexpected keyword occurring in a call and represent
     * the problem as an exception. The particular keyword may
     * incorrectly name a positional argument, or it may be entirely
     * unexpected (not be a parameter at all). In any case, since this
     * error is going to be fatal to the call, this method looks at
     * <i>all</i> the keywords to see if any are positional-only
     * parameters, and if that's not the problem, reports just the
     * originally-offending keyword as unexpected.
     * <p>
     * We call this method when any keyword has been encountered that
     * does not match a legitimate parameter, and there is no
     * {@code **kwargs} dictionary to catch it.
     *
     * @param name the unexpected keyword encountered in the call
     * @param kwnames all the keywords used in the call
     * @return TypeError diagnosing the problem
     */
    /*
     * Compare CPython ceval.c::positional_only_passed_as_keyword(), and
     * the code around its call. Unlike that function, on diagnosing a
     * problem, we do not have to set a message and return status. Also,
     * when called there is *always* a problem, and therefore an
     * exception.
     */
    protected TypeError unexpectedKeyword(PyObject name,
            Collection<PyObject> kwnames) throws TypeError {
        /*
         * Compare each of the positional only parameter names with each
         * of the keyword names given in the call. Collect the matches
         * in a list.
         */
        List<String> names = new ArrayList<>();
        for (int k = 0; k < code.posonlyargcount; k++) {
            PyObject varname = code.varnames.value[k];
            for (PyObject kw : kwnames) {
                try {
                    if (Abstract.richCompareBool(varname, kw,
                            Comparison.EQ))
                        names.add(kw.toString());
                } catch (Throwable e) {
                    // They're both str. This really can't happen.
                    throw new InterpreterError(KEYWORD_NOT_COMPARABLE,
                            kw);
                }
            }
        }

        if (!names.isEmpty()) {
            // We caught one or more matches: throw
            return new TypeError(POSITIONAL_ONLY, code.name,
                    String.join(", ", names));
        } else {
            // No match, so it is just unexpected altogether
            return new TypeError(UNEXPECTED_KEYWORD, code.name, name);

        }
    }

    static final String KEYWORD_NOT_STRING =
            "%.200s(): keywords must be strings";
    static final String KEYWORD_NOT_COMPARABLE =
            "Keyword names %s not comparable.";
    static final String MULTIPLE_VALUES =
            "%.200s(): multiple values for argument '%s'";
    static final String POSITIONAL_ONLY =
            "%.200s(): positional-only arguments passed by keyword: %s";
    static final String UNEXPECTED_KEYWORD =
            "%.200s(): unexpected keyword argument '%s'";

    void checkTooManyPositionalArgs(PyTuple args, PyTuple defaults) {
        /*
         * Check the number of positional arguments. We do it here so
         * that tooManyPositional(), can describe the call including
         * keyword arguments.
         */
        int nargs = args.value.length;
        if ((nargs > code.argcount)
                && !(code.traits.contains(Trait.VARARGS))) {
            // Excess positional arguments but no VARARGS for them.
            throw tooManyPositional(nargs, defaults.value.length);
        }
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
    protected TypeError tooManyPositional(int positionalGiven,
            int defcount) {
        boolean plural = false;
        int kwonlyGiven = 0;
        String takesText, givenText;
        int co_argcount = code.argcount;

        assert (!code.traits.contains(Trait.VARARGS));

        // Count missing keyword-only args.
        for (int i = co_argcount; i < co_argcount + code.kwonlyargcount;
                i++) {
            if (getLocal(i) != null) { kwonlyGiven++; }
        }

        if (defcount != 0) {
            int atleast = co_argcount - defcount;
            plural = true;
            takesText = String.format("from %d to %d", atleast,
                    co_argcount);
        } else {
            plural = (co_argcount != 1);
            takesText = String.format("%d", co_argcount);
        }

        if (kwonlyGiven > 0) {
            String format =
                    " positional argument%s (and %d keyword-only argument%s)";
            givenText = String.format(format,
                    positionalGiven != 1 ? "s" : "", kwonlyGiven,
                    kwonlyGiven != 1 ? "s" : "");
        } else {
            givenText = "";
        }

        return new TypeError(
                "%s() takes %s positional argument%s but %d%s %s given",
                code.name, takesText, plural ? "s" : "",
                positionalGiven, givenText,
                (positionalGiven == 1 && kwonlyGiven == 0) ? "was"
                        : "were");
    }

    /**
     * Deal with too many or too few arguments. A check is made for too
     * many positional arguments (and no VARARGS code trait), which
     * would be an error. In the case of too few positional arguments,
     * or missing keyword arguments, an attempt is made to fill them
     * from {@code defdaults} or {@code kwdefs}. If any parameters are
     * unfilled after that, this is an error.
     *
     * The positional arguments {@code args} are provided only for their
     * number.
     *
     * @param args arguments given
     * @param defaults default values by position or {@code null}
     * @param kwdefs default values by keyword or {@code null}
     * @throws TypeError if there are too many or missing arguments.
     */
    void applyDefaults(PyTuple args, PyTuple defaults,
            PyDict kwdefs) throws TypeError {

        int nargs = args.value.length;
        int ndefs = defaults == null ? 0 : defaults.value.length;
        int end = code.argcount + code.kwonlyargcount;

        /*
         * Check the number of positional arguments. We do it here so
         * that tooManyPositional(), can describe the call including
         * keyword arguments.
         */
        if ((nargs > code.argcount)
                && !(code.traits.contains(Trait.VARARGS))) {
            // Excess positional arguments but no VARARGS for them.
            throw tooManyPositional(nargs, ndefs);
        }

        /*
         * Provide missing positional arguments (if any) from defaults
         */
        if (nargs < code.argcount) {
            /*
             * At this stage, the first nargs parameter slots have been
             * filled and some (or all) of the remaining
             * code.argcount-nargs positional arguments may have been
             * assigned using keyword arguments. Meanwhile, defs is
             * available to provide values for (only) the last
             * defs.length positional arguments.
             */
            // locals[nargs:m] have no default values, where:
            int m = code.argcount - ndefs;
            int missing = 0;
            for (int i = nargs; i < m; i++) {
                if (getLocal(i) == null) { missing++; }
            }
            if (missing > 0) { missing_arguments(missing, ndefs); }

            /*
             * Variables in locals[m:code.argcount] may take defaults
             * from defs, but perhaps nargs > m. Begin at index nargs,
             * but not necessarily at the start of defs.
             */
            for (int i = nargs, j = Math.max(nargs - m, 0); j < ndefs;
                    i++, j++) {
                if (getLocal(i) == null) {
                    setLocal(i, defaults.value[j]);
                }
            }
        }

        /*
         * Provide missing keyword arguments (if any) from defaults
         */
        if (code.kwonlyargcount > 0) {
            /*
             * Variables in locals[code.argcount:total_args] are
             * keyword-only arguments. If they have not been assigned
             * yet, they take values from dict kwdefs.
             */
            PyObject[] varnames = code.varnames.value;
            int missing = 0;
            for (int i = code.argcount; i < end; i++) {
                PyObject value = getLocal(i);
                if (value == null && kwdefs != null)
                    setLocal(i, value = kwdefs.get(varnames[i]));
                if (value == null) { missing++; }
            }
            if (missing > 0) { missing_arguments(missing, -1); }
        }
    }

    /**
     * Diagnose which positional or keywords arguments are missing, if
     * any, and throw {@link TypeError} if there are any. Otherwise,
     * return quietly. We call this method when .
     *
     * @param missing number of missing arguments
     * @param defcount number of positional defaults available (or -1)
     * @return TypeError listing names of the missing arguments
     */
    /*
     * Compare CPython ceval.c::missing_arguments(). Unlike that
     * function, on diagnosing a problem, we do not have to set a
     * message and return status so the caller can "goto fail" and clean
     * up. We can just throw directly.
     */
    private TypeError missing_arguments(int missing, int defcount) {
        int i, j = 0;
        int start, end;
        boolean positional = (defcount != -1);
        String kind = positional ? "positional" : "keyword-only";
        ArrayList<String> missing_names = new ArrayList<>(missing);

        if (positional) {
            start = 0;
            end = code.argcount - defcount;
        } else {
            start = code.argcount;
            end = start + code.kwonlyargcount;
        }
        for (i = start; i < end; i++) {
            if (getLocal(i) == null) {
                String name = code.varnames.value[i].toString();
                missing_names.add(j++, name);
            }
        }
        assert (j == missing);
        return format_missing(kind, missing_names);

    }

    /*
     * Compare CPython ceval.c::format_missing(). Unlike that function,
     * on diagnosing a problem, we do not have to set a message and
     * return status so the caller can "goto fail" and clean up. We can
     * just throw directly.
     */
    private TypeError format_missing(String kind,
            ArrayList<String> names) {
        int len = names.size();
        String name_str, tail;

        assert (len >= 1);
        /* Deal with the joys of natural language. */
        switch (len) {
            case 1:
                name_str = names.get(0);
                break;
            case 2:
                name_str = String.format("%s and %s",
                        names.get(len - 2), names.get(len - 1));
                break;
            default:
                tail = String.format(", %s and %s", names.get(len - 2),
                        names.get(len - 1));
                // Chop off the last two objects in the list.
                names.remove(len - 1);
                names.remove(len - 2);

                // Stitch everything up in a nice comma-separated list.
                name_str = String.join(", ", names) + tail;
        }

        return new TypeError(
                "%s() missing %i required %s argument%s: %s", code.name,
                len, kind, len == 1 ? "" : "s", name_str);
    }

    /**
     * Create cells for non-local variables bound to the current scope.
     *
     * This must come after all argument processing because of the
     * possibility that one of the arguments binds a cell variable
     * required in a nested scope. It's value must be present, and could
     * have been set by any mechanism.
     */
    void makeCells() {
        for (int i = 0; i < code.cellvars.value.length; ++i) {
            int arg;
            // Perhaps the cell variable is also an argument.
            if (code.cell2arg != null && (arg =
                    code.cell2arg[i]) != PyCode.CELL_NOT_AN_ARG) {
                makeCell(i, getLocal(arg));
                // Clear the direct reference.
                setLocal(arg, null);
            } else {
                makeCell(i, null);
            }
        }
    }

}
