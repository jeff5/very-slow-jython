package uk.co.farowl.vsj2.evo4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.PyCode.Trait;

/**
 * A {@code PyFrame} is the context for the execution of code. For
 * example with the function definition:<pre>
 * def func(a, b, c=3, d=4, /, e=5, f=6, *aa, g=7, h, i=9, **kk):
 *     v, w, x = b, c, d, e
 *     return u
 * </pre>
 * the layout of the local variables in a frame would be as below
 * <table class="framed-layout" style="border: none;">
 * <caption>A Python {@code frame}</caption>
 * <tr>
 * <td class="label">frame</td>
 * <td>a</td>
 * <td>b</td>
 * <td>c</td>
 * <td>d</td>
 * <td>e</td>
 * <td>f</td>
 * <td>g</td>
 * <td>h</td>
 * <td>i</td>
 * <td>aa</td>
 * <td>kk</td>
 * <td>u</td>
 * <td>v</td>
 * <td>w</td>
 * <td>x</td>
 * </tr>
 * <tr>
 * <td class="label" rowspan=2>code</td>
 * <td colspan=6>argcount</td>
 * <td colspan=3>kwonlyargcount</td>
 * <td>*</td>
 * <td>**</td>
 * <td colspan=4></td>
 * </tr>
 * <tr>
 * <td colspan=4>posonlyargcount</td>
 * <td colspan=13></td>
 * </tr>
 * <tr>
 * <td class="label">function</td>
 * <td colspan=2></td>
 * <td colspan=4>defaults</td>
 * <td colspan=3 style="border-style: dashed;">kwdefaults</td>
 * </tr>
 * </table>
 * <p>
 * In the last row of the table, the properties are supplied by the
 * function object during each call. {@code defaults} apply in the
 * position show, in order, while {@code kwdefaults} (in a map) apply to
 * keywords wherever the name matches. The names in the frame are those
 * in the {@link PyCode#varnames} field of the associated code object
 * <p>
 * The frame presents an abstraction of an array of named local
 * variables, and two more of cell and free variables, while concrete
 * subclasses are free to implement these in whatever manner they
 * choose.
 */
abstract class PyFrame implements PyObject {

    /** The type {@code frame}. */
    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("frame", PyFrame.class));

    // Type admits no subclasses.
    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() { return Py.defaultToString(this); }

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
            PyObject b = globals.get(ID.__builtins__);
            if (b != null) {
                // Normally, globals[__builtins__] is a module
                if (b instanceof PyModule)
                    return ((PyModule) b).dict;
                else if (b instanceof PyDict)
                    return (PyDict) b;
                throw new TypeError("%s should be module not %s",
                        ID.__builtins__, b);
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
     * don't copy more than have been allowed for in the frame.
     * Providing too many or too few is not an error at this stage, as
     * this may be a VARARGS function or one with positional defaults.
     *
     * @param args positional arguments
     */
    void setPositionalArguments(PyTuple args) {
        int n = Math.min(args.value.length, code.argcount);
        for (int i = 0; i < n; i++)
            setLocal(i, args.value[i]);
    }

    /**
     * The equivalent of {@link #setPositionalArguments(PyTuple)} in
     * support of the vector call, taking arguments directly from a
     * slice of the CPython stack, rather than a {@code tuple}, which
     * often has to be created just to satisfy the classic call
     * protocol.
     *
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments
     */
    void setPositionalArguments(PyObject[] stack, int start,
            int nargs) {
        int n = Math.min(nargs, code.argcount);
        for (int i = 0, j = start; i < n; i++)
            setLocal(i, stack[j++]);
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
        PyDict kwdict = null;
        if (code.traits.contains(Trait.VARKEYWORDS)) {
            kwdict = Py.dict();
            int kwargsIndex = code.argcount + code.kwonlyargcount;
            if (code.traits.contains(Trait.VARARGS))
                kwargsIndex += 1;
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
     * The equivalent of {@link #setKeywordArguments(PyDict)} in support
     * of the vector call.
     *
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
     * @param stack {@code [kwstart:kwstart+len(kwnames)]} values
     *            corresponding to {@code kwnames} in order
     * @param kwstart start position in {@code kwvalues}
     * @param kwnames keywords used in the call (or {@code **kwargs})
     */
    void setKeywordArguments(PyObject[] stack, int kwstart,
            PyObject[] kwnames) {
        /*
         * Create a dictionary for the excess keyword parameters, and
         * insert it in the local variables at the proper position.
         */
        PyDict kwdict = null;
        if (code.traits.contains(Trait.VARKEYWORDS)) {
            kwdict = Py.dict();
            int kwargsIndex = code.argcount + code.kwonlyargcount;
            if (code.traits.contains(Trait.VARARGS)) { kwargsIndex++; }
            setLocal(kwargsIndex, kwdict);
        }

        /*
         * For each of the names in kwnames, search code.varnames for a
         * match, and either assign the local variable or add the
         * name-value pair to kwdict.
         */
        int kwcount = kwnames == null ? 0 : kwnames.length;
        for (int i = 0, j=kwstart; i < kwcount; i++) {
            PyObject name = kwnames[i];
            PyObject value = stack[j++];
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

        PyObject[] varnames = code.varnames;
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
     * Fill in missing positional parameters from a from {@code defs}.
     * If any positional parameters are cannot be filled, this is an
     * error. The number of positional arguments {@code nargs} are
     * provided so we know where to start only for their number.
     *
     * It is harmless (but a waste) to call this when
     * {@code nargs >= code.argcount}.
     *
     * @param nargs number of positional arguments given in call
     * @param defs default values by position or {@code null}
     * @throws TypeError if there are still missing arguments.
     */
    void applyDefaults(int nargs, PyObject[] defs) throws TypeError {

        int ndefs = defs == null ? 0 : defs.length;
        /*
         * At this stage, the first nargs parameter slots have been
         * filled and some (or all) of the remaining code.argcount-nargs
         * positional arguments may have been assigned using keyword
         * arguments. Meanwhile, defs is available to provide values for
         * (only) the last defs.length positional arguments.
         */
        // locals[nargs:m] have no default values, where:
        int m = code.argcount - ndefs;
        int missing = 0;
        for (int i = nargs; i < m; i++) {
            if (getLocal(i) == null) { missing++; }
        }
        if (missing > 0) { missingArguments(missing, ndefs); }

        /*
         * Variables in locals[m:code.argcount] may take defaults from
         * defs, but perhaps nargs > m. Begin at index nargs, but not
         * necessarily at the start of defs.
         */
        for (int i = nargs, j = Math.max(nargs - m, 0); j < ndefs;
                i++, j++) {
            if (getLocal(i) == null) { setLocal(i, defs[j]); }
        }
    }

    /**
     * Deal with missing keyword arguments, attempting to fill them from
     * {@code kwdefs}. If any parameters are unfilled after that, this
     * is an error.
     *
     * It is harmless (but a waste) to call this when
     * {@code code.kwonlyargcount == 0}.
     *
     * @param kwdefs default values by keyword or {@code null}
     * @throws TypeError if there are too many or missing arguments.
     */
    void applyKWDefaults(PyDict kwdefs) throws TypeError {
        /*
         * Variables in locals[code.argcount:end] are keyword-only
         * arguments. If they have not been assigned yet, they take
         * values from dict kwdefs.
         */
        PyObject[] varnames = code.varnames;
        int end = code.argcount + code.kwonlyargcount;
        int missing = 0;
        for (int i = code.argcount; i < end; i++) {
            PyObject value = getLocal(i);
            if (value == null && kwdefs != null)
                setLocal(i, value = kwdefs.get(varnames[i]));
            if (value == null) { missing++; }
        }
        if (missing > 0) { missingArguments(missing, -1); }
    }

    /**
     * Create cells for non-local variables bound to the current scope.
     *
     * This must come after all argument processing because of the
     * possibility that one of the arguments binds a cell variable
     * required in a nested scope. Its value must be present, and could
     * have been set by any mechanism.
     *
     * It is harmless (but a waste) to call this when
     * {@code code.cellvars.length == 0}.
     */
    void makeCells() {
        for (int i = 0; i < code.cellvars.length; ++i) {
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
    protected TypeError tooManyPositional(int posGiven,
            PyTuple defaults) {
        boolean posPlural = false;
        int kwGiven = 0;
        String posText, givenText;
        int argcount = code.argcount;
        int defcount = defaults.value.length;
        int end = argcount + code.kwonlyargcount;

        assert (!code.traits.contains(Trait.VARARGS));

        // Count keyword-only args given
        for (int i = argcount; i < end; i++) {
            if (getLocal(i) != null) { kwGiven++; }
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
            givenText = String.format(format, //
                    posGiven != 1 ? "s" : "", kwGiven,
                    kwGiven != 1 ? "s" : "");
        } else {
            givenText = "";
        }

        return new TypeError(
                "%s() takes %s positional argument%s but %d%s %s given",
                code.name, posText, posPlural ? "s" : "", posGiven,
                givenText,
                (posGiven == 1 && kwGiven == 0) ? "was" : "were");
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
            PyObject varname = code.varnames[k];
            for (PyObject keyword : kwnames) {
                if (Abstract.richCompareBool(varname, keyword,
                        Comparison.EQ, null))
                    names.add(keyword.toString());
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
    protected TypeError missingArguments(int missing, int defcount) {
        String kind;
        int start, end;

        // Choose the range in which to look for null arguments
        if (defcount >= 0) {
            kind = "positional";
            start = 0;
            end = code.argcount - defcount;
        } else {
            kind = "keyword-only";
            start = code.argcount;
            end = start + code.kwonlyargcount;
        }

        // Make a list of names from that range where value is null
        ArrayList<String> names = new ArrayList<>(missing);
        for (int i = start, j = 0; i < end; i++) {
            if (getLocal(i) == null) {
                names.add(j++, code.varnames[i].toString());
            }
        }

        // Formulate an error from the list
        return missingNamesTypeError(kind, names);
    }

    /** Compose a {@link TypeError} from the missing argument names. */
    /*
     * Compare CPython ceval.c::format_missing(). Unlike that function,
     * on diagnosing a problem, we do not have to set a message and
     * return status so the caller can "goto fail" and clean up. We can
     * just throw directly.
     */
    private TypeError missingNamesTypeError(String kind,
            ArrayList<String> names) {
        int len = names.size();
        String joinedNames;

        switch (len) {
            case 0:
                // Shouldn't happen but let's avoid trouble
                joinedNames = "";
                break;
            case 1:
                joinedNames = names.get(0);
                break;
            case 2:
                joinedNames = String.format("%s and %s",
                        names.get(len - 2), names.get(len - 1));
                break;
            default:
                String tail = String.format(", %s and %s",
                        names.get(len - 2), names.get(len - 1));
                // Chop off the last two objects in the list.
                names.remove(len - 1);
                names.remove(len - 2);
                // Stitch everything into a nice comma-separated list.
                joinedNames = String.join(", ", names) + tail;
        }

        return new TypeError(
                "%s() missing %d required %s argument%s: %s", code.name,
                len, kind, len == 1 ? "" : "s", joinedNames);
    }
}
