package uk.co.farowl.vsj3.evo1;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * This class provides a parser for the positional and keyword arguments
 * of built-in functions and methods. The purpose of an argument parser
 * is to provide the body of a function with an array of values,
 * corresponding in order and number to its (logical) arguments.
 * <p>
 * This parser transforms several argument presentations that occur in a
 * Python implementation, into an array. This array is either created by
 * the parser, or designated by the caller. The parser may therefore be
 * used to prepare arguments for a pure a Java method (or
 * {@code MethodHandle}) accepting an array, or to insert them as
 * initial values in an interpreter frame ({@code PyFrame}).
 * <p>
 * The fields of the parser that determine the acceptable numbers of
 * positional arguments and their names are essentially those of a
 * {@code code} object ({@link PyCode}). Default are provided values
 * mirror the defaults built into a {@code function} object
 * ({@link PyFunction}).
 * <p>
 * The easiest way of specifying a parser (although one that is a little
 * costly to construct) is to list the arguments as they would be
 * declared in Python, including the furniture that marks up the
 * positional-only, keyword-only, positional varargs, and keyword
 * varargs. This is the API offered by
 * {@link #fromSignature(String, String...)}.
 */
class ArgParser {

    // Compare code object (and CPython _Arg_Parser in modsupport.h)

    /** Empty names array. */
    private static final String[] NO_STRINGS = new String[0];

    /** The name of the function, mainly for error messages. */
    final String name;

    /**
     * Names of arguments that could be supplied by position or keyword.
     * Elements are guaranteed by construction to be of type
     * {@code str}, and not {@code null} or empty.
     */
    final String[] argnames;

    /**
     * The number of positional or keyword arguments, excluding the
     * "collector" ({@code *args} and {@code **kwargs}) arguments, and
     * any data that may follow the legitimate argument names. Equal to
     * {@code argcount + kwonlyargcount}.
     */
    final int regargcount;

    /** The number of <b>positional</b> arguments. */
    final int argcount;

    /** The number of positional-only arguments. */
    final int posonlyargcount;

    /** The number of keyword-only arguments. */
    final int kwonlyargcount;

    // Compare function object

    /** The (positional) default arguments or {@code null}. */
    private List<Object> defaults;

    /** The keyword defaults, may be a {@code dict} or {@code null}. */
    private Map<Object, Object> kwdefaults;

    /**
     * The frame has a collector ({@code tuple}) for excess positional
     * arguments at this index, if it is {@code >0}.
     */
    final int varArgsIndex;

    /**
     * The frame has a collector ({@code dict}) for excess keyword
     * arguments at this index, if it is {@code >0}.
     */
    final int varKeywordsIndex;

    /**
     * Create a parser, for a named function, with defined numbers of
     * positional-only and keyword-only arguments, and naming the
     * arguments. Arguments that may only be given by position need not
     * be named. ("" is acceptable in the names array.)
     * <p>
     * Overflow of positional and/or keyword arguments into a
     * {@code tuple} or {@code dict} may also be allowed. For example, a
     * function that in Python would have the signature with the
     * function definition:<pre>
     * def func(a, b, c=3, d=4, /, e=5, f=6, *aa, g=7, h, i=9, **kk):
     *     pass
     * # func.__defaults__ == (3, 4, 5, 6)
     * # func.__kwdefaults__ == {'g': 7, 'i': 9}
     * </pre>would be described by a constructor call: <pre>
     * private static ArgParser parser =
     *     new ArgParser("func", "aa", "kk", 4, 3, //
     *             "a", "b", "c", "d", "e", "f", "g", "h", "i")
     *                     .defaults(3, 4, 5, 6) //
     *                     .kwdefaults(7, null, 9);
     *
     * </pre> Note that "aa" and "kk" are given separately, not amongst
     * the argument names. In the parsing result array, they will be at
     * the end. (This is how a CPython frame is laid out.)
     * <p>
     * Defaults are provided, after the parser has been constructed, as
     * values corresponding to argument names, when right-justified in
     * the space to which they apply. (See diagram below.) Both the
     * positional and keyword defaults are given by position in this
     * formulation. The {@link #kwdefaults(Object...)} call is allowed
     * to supply {@code null} values at positions it does not define.
     * <p>
     * When parsed to an array, the layout of the argument values, in
     * relation to fields of the parser will be as follows. <style>
     * table.lined td {border: 1px solid black; text-align: center;
     * min-width: 2em;} table.lined {border-collapse: collapse;}
     * table.lined td.row-label {text-align: left;} </style>
     * <table class="lined">
     * <tr>
     * <td class="row-label">names</td>
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
     * </tr>
     * <tr>
     * <td class="row-label" rowspan=3>layout</td>
     * <td colspan=4>posOnly</td>
     * <td colspan=2></td>
     * <td colspan=3>kwOnly</td>
     * </tr>
     * <tr>
     * <td colspan=2></td>
     * <td colspan=4>defaults</td>
     * <td colspan=3 style="border-style: dashed;">kwdefaults</td>
     * </tr>
     * </table>
     *
     * @param name of the function
     * @param varargs name of the positional collector or {@code null}
     * @param varkw name of the keywords collector or {@code null}
     * @param posOnly number of positional-only arguments
     * @param kwdOnly number of keyword-only arguments
     * @param names of the (non-collector) arguments
     */
    ArgParser(String name, String varargs, String varkw, int posOnly,
            int kwdOnly, String... names) {

        // Name of function
        this.name = name;

        // Total argument count *except* possible varargs, varkwargs
        int N = names.length;
        this.regargcount = N;

        // Fill in other cardinal points
        this.posonlyargcount = posOnly;
        this.kwonlyargcount = kwdOnly;
        this.argcount = N - kwdOnly;

        // There may be positional and/or keyword collectors
        this.varArgsIndex = varargs != null ? N++ : -1;
        this.varKeywordsIndex = varkw != null ? N++ : -1;

        // Make a new array of the names, including the collectors
        String[] argnames = new String[N];
        this.argnames = argnames;
        System.arraycopy(names, 0, argnames, 0, regargcount);

        if (hasVarArgs())
            argnames[varArgsIndex] = varargs;
        if (hasVarKeywords())
            argnames[varKeywordsIndex] = varkw;

        // Check for empty names
        for (int i = posOnly; i < N; i++) {
            if (argnames[i].length() == 0) {
                // We found a "" name beyond positional only.
                throw new InterpreterError(MISPLACED_EMPTY, name,
                        argnames.toString());
            }
        }
    }

    /**
     * Create a parser for a named function, with defined numbers of
     * positional-only and keyword-only arguments, and argument names in
     * an array prepared by client code.
     * <p>
     * The capabilities of this parser, are exactly the same as one
     * defined by
     * {@link #ArgParser(String, String, String, int, int, String...)}.
     * The parser in the example there may be generated by: <pre>
     * String[] names = {"a", "b", "c", "d", "e", "f", "g", "h", "i",
     *         "aa", "kk"};
     * ArgParser ap = new ArgParser("func", true, true, 4, 3, names,
     *         names.length - 2) //
     *                 .defaults(3, 4, 5, 6) //
     *                 .kwdefaults(7, null, 9);
     * </pre> The differences allow the array of names to be used
     * in-place (not copied). The client code must therefore ensure that
     * it cannot be modified after the parser has been constructed.
     * <p>
     * The array of names may be longer than is necessary: the caller
     * specifies how much of the array should be treated as regular
     * argument names, and whether zero, one or two further elements
     * will name collectors for excess positional or keyword arguments.
     * The rest of the elements will not be examined by the parser. The
     * motivation for this design is to permit efficient construction
     * when the the array of names is the local variable names in a
     * Python {@code code} object.
     *
     * @param name of the function
     * @param varargs whether there is positional collector
     * @param varkw whether there is a keywords collector
     * @param posOnly number of positional-only arguments
     * @param kwdOnly number of keyword-only arguments
     * @param names of the arguments including any collectors (varargs)
     * @param count number of regular (non-collector) arguments
     */
    ArgParser(String name, boolean varargs, boolean varkw, int posOnly,
            int kwdOnly, String[] names, int count) {

        // Name of function
        this.name = name;
        this.argnames = names;

        // Total argument count *except* possible varargs, varkwargs
        int N = Math.min(count, names.length);
        this.regargcount = N;
        this.posonlyargcount = posOnly;
        this.kwonlyargcount = kwdOnly;
        this.argcount = N - kwdOnly;

        // There may be positional and/or keyword collectors
        this.varArgsIndex = varargs ? N++ : -1;
        this.varKeywordsIndex = varkw ? N++ : -1;
    }

    /**
     * Create a parser, for a named function, with defined numbers of
     * positional-only and keyword-only arguments, and naming the
     * arguments. Arguments that may only be given by position need not
     * be named. ("" is acceptable in the names array.)
     *
     * @param name of function
     * @param decl names of arguments and indicators "/", "*", "**"
     */
    static ArgParser fromSignature(String name, String... decl) {

        // Collect the names of the arguments here
        ArrayList<String> args = new ArrayList<>();
        String varargs = null, varkw = null;

        int posOnly = 0, posCount = 0;

        /*
         * Scan names, looking out for /, * and ** markers. Nameless
         * arguments are tolerated in the positional-only section.
         */
        for (String arg : decl) {
            int arglen = arg.length();
            if (arglen > 0) {
                if (arg.charAt(0) == '/') {
                    // We found a positional-only end marker /
                    posOnly = args.size();
                } else if (arg.charAt(0) == '*') {
                    if (arglen > 1) {
                        if (arg.charAt(1) == '*') {
                            // Looks like a keywords collector
                            if (arglen > 2) {
                                // ... and it has a name.
                                varkw = arg.substring(2);
                            }
                        } else {
                            // Looks like a positional collector
                            varargs = arg.substring(1);
                            posCount = args.size();
                        }
                    } else {
                        // We found a keyword-only start marker *
                        posCount = args.size();
                    }
                } else {
                    // We found a proper name for the argument.
                    args.add(arg);
                }
            } else {
                // We found a "": tolerate for now.
                args.add("");
            }
        }

        // Total argument count *except* possible varargs, varkwargs
        int N = args.size();
        if (posCount == 0) { posCount = N; }
        String[] names =
                N == 0 ? NO_STRINGS : args.toArray(new String[N]);

        return new ArgParser(name, varargs, varkw, posOnly,
                N - posCount, names);
    }

    /**
     * @return true if there is an excess positional argument collector.
     */
    boolean hasVarArgs() {
        return varArgsIndex >= 0;
    }

    /**
     * @return true if there is an excess keyword argument collector.
     */
    boolean hasVarKeywords() {
        return varKeywordsIndex >= 0;
    }

    @Override
    public String toString() {
        return sigString(name);
    }

    private String sigString(String fname) {
        StringJoiner sj = new StringJoiner(", ", fname + "(", ")");

        // Keyword only parameters start at k
        int k = regargcount - kwonlyargcount;
        // The positional defaults start at d
        int d = k - (defaults == null ? 0 : defaults.size());
        // We work through the parameters with index i
        int i = 0;

        // Positional-only parameters
        while (i < posonlyargcount) {
            sj.add(parameterToString(i++, d));
        }

        // If there were any positional-only parameters ...
        if (i > 0) {
            // ... mark the end of them.
            sj.add("/");
        }

        // Positional (but not positional-only) parameters
        while (i < k) { sj.add(parameterToString(i++, d)); }

        // Reached the end of the positional section
        if (hasVarArgs()) {
            // Excess from the positional section does to a *args
            sj.add("*" + argnames[varArgsIndex]);
        } else if (i < regargcount) {
            // Mark the end but no *args to catch the excess
            sj.add("*");
        }

        // Keyword only parameters begin properly
        while (i < regargcount) { sj.add(parameterToString(i++)); }

        if (hasVarKeywords()) {
            // Excess from the keyword section does to a **kwargs
            sj.add("**" + argnames[varKeywordsIndex]);
        }

        return sj.toString();
    }

    /**
     * Return <i>i</i>th positional parameter name and default value if
     * available. Helper to {@link #sigString()}.
     */
    private String parameterToString(int i, int d) {
        if (i < d)
            return argnames[i];
        else {
            // A default value is available
            Object value = defaults.get(i - d);
            return argnames[i] + "=" + value.toString();
        }
    }

    /**
     * Return <i>i</i>th parameter name and keyword default value if
     * available. Helper to {@link #sigString()}.
     */
    private String parameterToString(int i) {
        String name = argnames[i];
        if (kwdefaults != null) {
            Object value = kwdefaults.get(name);
            if (value != null) {
                // A default value is available
                return argnames[i] + "=" + value.toString();
            }
        }
        return name;
    }

    /**
     * Parse classic arguments and create an array, using the arguments
     * supplied and the defaults held in the parser.
     *
     * @param args positional arguments
     * @param kwargs keyword arguments
     * @return array of parsed arguments
     */
    Object[] parse(PyTuple args, PyDict kwargs) {
        Object[] a = new Object[argnames.length];
        FrameWrapper w = new ArrayFrameWrapper(a);
        parseToFrame(w, args, kwargs);
        return a;
    }

    private static final String MISPLACED_EMPTY =
            "Misplaced empty keyword in ArgParser spec for %s %s";

    /**
     * Provide the positional defaults. If L values are provided, they
     * correspond to {@code arg[max-L] ... arg[max-1]}, where
     * {@code max} is the index of the first keyword-only argument, or
     * the number of arguments if there are no keyword-only arguments.
     * The minimum number of positional arguments will then be
     * {@code max-L}.
     *
     * @param values
     * @return this
     */
    ArgParser defaults(Object... values) {
        if (values == null || values.length == 0) {
            defaults = null;
        } else {
            defaults = Arrays.asList(values);
        }
        checkShape();
        return this;
    }

    /**
     * Provide the keyword-only defaults as values. If K values are
     * provided, they correspond to {@code arg[N-K] ... arg[N-1]}, where
     * {@code N} is the number of regular arguments
     * ({@link #regargcount}). The number of keyword-only arguments and
     * positional-only arguments must not together exceed the number of
     * regular arguments named in the constructor.
     *
     * @param values keyword values aligned to the argument names
     * @return this
     */
    ArgParser kwdefaults(Object... values) {
        PyDict d = new PyDict();
        int K = values.length;
        for (int i = 0, p = regargcount - K; i < K; i++, p++) {
            Object v = values[i];
            if (v != null) { d.put(argnames[p], v); }
        }
        kwdefaults = d;
        return this;
    }

    /**
     * Provide the keyword-only defaults, perhaps as a {@code dict}. If
     * the argument is empty, it is converted to {@code null}
     * internally.
     *
     * @param kwd replacement keyword defaults (or {@code null}
     * @return this
     */
    ArgParser kwdefaults(Map<Object, Object> kwd) {
        kwdefaults = (kwd == null || kwd.isEmpty()) ? null : kwd;
        return this;
    }

    /**
     * The number of keyword-only arguments and positional-only
     * arguments must not together exceed the number of arguments named
     * in the constructor. (The last two are defined in the
     * constructor.) Nor must there be excess default values for the
     * number of arguments.
     */
    private void checkShape() {
        final int N = regargcount;
        final int L = defaults == null ? 0 : defaults.size();
        final int K = kwdefaults == null ? 0 : kwdefaults.size();

        // XXX LOgic is incorrect following changed semantics
        int posArgCount = N - K;
        int min = posArgCount - L;

        if (min < 0) {
            throw new InterpreterError(TOO_MANY_DEFAULTS, L,
                    posArgCount, name);
        } else if (posArgCount < posonlyargcount) {
            throw new InterpreterError(TOO_MANY_KWDEFAULTS, K,
                    posonlyargcount, name);
        }
    }

    private static final String TOO_MANY_DEFAULTS =
            "More defaults (%d given) than "
                    + "positional arguments (%d allowed) "
                    + "when specifying '%s'";

    private static final String TOO_MANY_KWDEFAULTS =
            "More keyword defaults (%d given) than remain after "
                    + "positional-only arguments (%d left)"
                    + "when specifying '%s'";

    static final String MULTIPLE_VALUES =
            "%.200s(): multiple values for argument '%s'";

    /** Get the name of arg i or make one up. */
    private String nameArg(int i) {
        String arg = argnames[i].toString();
        if (arg.length() == 0) { arg = String.format("arg %d", i + 1); }
        return arg;
    }

    /*
     * Experiment: base a parser on the mechanics of function call.
     */
    abstract class FrameWrapper {

        /**
         * Get the local variable named by {@code argnames[i]}
         *
         * @param i index of variable name in {@code argnames}
         * @return value of variable named {@code argnames[i]}
         */
        abstract Object getLocal(int i);

        /**
         * Set the local variable named by {@code argnames[i]}
         *
         * @param i index of variable name in {@code argnames}
         * @param v to assign to variable named {@code argnames[i]}
         */
        abstract void setLocal(int i, Object v);

        /**
         * Copy positional arguments into local variables, making sure
         * we don't copy more than have been allowed for in the frame.
         * Providing too many or too few is not an error at this stage,
         * as there may be a collector to catch the excess arguments or
         * positional or keyword defaults to make up the shortfall.
         *
         * @param args positional arguments
         */
        void setPositionalArguments(PyTuple args) {
            int n = Math.min(args.value.length, argcount);
            for (int i = 0; i < n; i++)
                setLocal(i, args.value[i]);
        }

        /**
         * For each of the names used as keywords in the call, match it
         * with an allowable parameter name, and assign that frame-local
         * variable the keyword argument given in the call. If the
         * variable is not null, this is an error.
         * <p>
         * "Allowable parameter name" here means the names in
         * {@code argnames[p:q]} where {@code p=posonlyargcount} and
         * {@code q=argcount + kwonlyargcount}. If the name used in the
         * call is not is not an allowable keyword, then if this parser
         * allows for excess keywords, add it to the frame's keyword
         * dictionary, otherwise throw an informative error.
         * <p>
         * In this version, accept the keyword arguments passed as a
         * dictionary, as in the "classic" {@code (*args, **kwargs)}
         * call.
         *
         * @param kwargs keyword arguments given in call
         */
        void setKeywordArguments(PyDict kwargs) {
            /*
             * Create a dictionary for the excess keyword parameters,
             * and insert it in the local variables at the proper
             * position.
             */
            PyDict kwdict = null;
            if (hasVarKeywords()) {
                kwdict = Py.dict();
                setLocal(varKeywordsIndex, kwdict);
            }

            /*
             * For each entry in kwargs, search argnames for a match,
             * and either assign the local variable or add the
             * name-value pair to kwdict.
             */
            for (Map.Entry<Object, Object> entry : kwargs.entrySet()) {
                Object name = entry.getKey();
                Object value = entry.getValue();
                int index = argnamesIndexOf(name);

                if (index < 0) {
                    // Not found in (allowed slice of) argnames
                    if (kwdict != null)
                        kwdict.put(name, value);
                    else
                        // No kwdict: everything must match.
                        throw unexpectedKeyword(name, kwargs.keySet());
                } else {
                    // Keyword found to name allowable variable at index
                    if (getLocal(index) == null)
                        setLocal(index, value);
                    else
                        // Unfortunately, that seat is already taken
                        throw new TypeError(MULTIPLE_VALUES, name,
                                name);
                }
            }
        }

        /**
         * Find the given name in {@code argnames}, and if it is not
         * found, return -1. Only the "allowable parameter names", those
         * acceptable as keyword arguments, are searched. It is an error
         * if the name is not a Python {@code str}.
         *
         * @param name parameter name given as keyword
         * @return index of {@code name} in {@code argnames} or -1
         */
        private int argnamesIndexOf(Object name) {

            int end = regargcount;

            if (name == null || !(PyUnicode.TYPE.check(name))) {
                throw new TypeError(KEYWORD_NOT_STRING, name);
            }

            /*
             * For speed, try raw pointer comparison. As names are
             * normally interned Strings this should almost always hit.
             */
            for (int i = posonlyargcount; i < end; i++) {
                if (argnames[i] == name)
                    return i;
            }

            /*
             * It's not definitive until we have repeated the search
             * using proper object comparison.
             */
            for (int i = posonlyargcount; i < end; i++) {
                if (Abstract.richCompareBool(name, argnames[i],
                        Comparison.EQ, null))
                    return i;
            }

            return -1;
        }

        /**
         * Fill in missing positional parameters from a from
         * {@code defs}. If any positional parameters are cannot be
         * filled, this is an error. The number of positional arguments
         * {@code nargs} are provided so we know where to start only for
         * their number.
         *
         * It is harmless (but a waste) to call this when
         * {@code nargs >= argcount}.
         *
         * @param nargs number of positional arguments given in call
         * @param defs default values by position or {@code null}
         * @throws TypeError if there are still missing arguments.
         */
        void applyDefaults(int nargs, List<Object> defs)
                throws TypeError {

            int ndefs = defs == null ? 0 : defs.size();
            /*
             * At this stage, the first nargs parameter slots have been
             * filled and some (or all) of the remaining argcount-nargs
             * positional arguments may have been assigned using keyword
             * arguments. Meanwhile, defs is available to provide values
             * for (only) the last defs.length positional arguments.
             */
            // locals[nargs:m] have no default values, where:
            int m = argcount - ndefs;
            int missing = 0;
            for (int i = nargs; i < m; i++) {
                if (getLocal(i) == null) { missing++; }
            }
            if (missing > 0) { throw missingArguments(missing, ndefs); }

            /*
             * Variables in locals[m:argcount] may take defaults from
             * defs, but perhaps nargs > m. Begin at index nargs, but
             * not necessarily at the start of defs.
             */
            for (int i = nargs, j = Math.max(nargs - m, 0); j < ndefs;
                    i++, j++) {
                if (getLocal(i) == null) { setLocal(i, defs.get(j)); }
            }
        }

        /**
         * Deal with missing keyword arguments, attempting to fill them
         * from {@code kwdefs}. If any parameters are unfilled after
         * that, this is an error.
         *
         * It is harmless (but a waste) to call this when
         * {@code kwonlyargcount == 0}.
         *
         * @param kwdefs default values by keyword or {@code null}
         * @throws TypeError if there are too many or missing arguments.
         */
        void applyKWDefaults(Map<Object, Object> kwdefs)
                throws TypeError {
            /*
             * Variables in locals[argcount:end] are keyword-only
             * arguments. If they have not been assigned yet, they take
             * values from dict kwdefs.
             */
            int end = regargcount;
            int missing = 0;
            for (int i = argcount; i < end; i++) {
                Object value = getLocal(i);
                if (value == null && kwdefs != null)
                    setLocal(i, value = kwdefs.get(argnames[i]));
                if (value == null) { missing++; }
            }
            if (missing > 0) { throw missingArguments(missing, -1); }
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
         * message and return status. Also, when called there is
         * *always* a problem, and therefore an exception.
         */
        // XXX Do not report kw arguments given: unnatural constraint.
        /*
         * The caller must defer the test until after kw processing,
         * just so the actual kw-args given can be reported accurately.
         * Otherwise, the test could be after (or part of) positional
         * argument processing.
         */
        protected TypeError tooManyPositional(int posGiven) {
            boolean posPlural = false;
            int kwGiven = 0;
            String posText, givenText;
            int defcount = defaults.size();
            int end = regargcount;

            assert (!hasVarArgs());

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
                if (argcount == 0) {
                    posText = "no";
                } else {
                    posText = String.format("%d", argcount);
                }
            }

            if (kwGiven > 0) {
                String format = " positional argument%s"
                        + " (and %d keyword-only argument%s)";
                givenText =
                        String.format(format, posGiven != 1 ? "s" : "",
                                kwGiven, kwGiven != 1 ? "s" : "");
            } else {
                givenText = "";
            }

            return new TypeError(
                    "%s() takes %s positional argument%s but %d%s %s given",
                    name, posText, posPlural ? "s" : "", posGiven,
                    givenText,
                    (posGiven == 1 && kwGiven == 0) ? "was" : "were");
        }

        /**
         * Diagnose an unexpected keyword occurring in a call and
         * represent the problem as an exception. The particular keyword
         * may incorrectly name a positional argument, or it may be
         * entirely unexpected (not be a parameter at all). In any case,
         * since this error is going to be fatal to the call, this
         * method looks at <i>all</i> the keywords to see if any are
         * positional-only parameters, and if that's not the problem,
         * reports just the originally-offending keyword as unexpected.
         * <p>
         * We call this method when any keyword has been encountered
         * that does not match a legitimate parameter, and there is no
         * {@code **kwargs} dictionary to catch it.
         *
         * @param name the unexpected keyword encountered in the call
         * @param kwnames all the keywords used in the call
         * @return TypeError diagnosing the problem
         */
        /*
         * Compare CPython ceval.c::positional_only_passed_as_keyword(),
         * and the code around its call. Unlike that function, on
         * diagnosing a problem, we do not have to set a message and
         * return status. Also, when called there is *always* a problem,
         * and therefore an exception.
         */
        protected TypeError unexpectedKeyword(Object name,
                Collection<Object> kwnames) throws TypeError {
            /*
             * Compare each of the positional only parameter names with
             * each of the keyword names given in the call. Collect the
             * matches in a list.
             */
            List<String> names = new ArrayList<>();
            for (int k = 0; k < posonlyargcount; k++) {
                Object varname = argnames[k];
                for (Object keyword : kwnames) {
                    if (Abstract.richCompareBool(varname, keyword,
                            Comparison.EQ, null))
                        names.add(keyword.toString());
                }
            }

            if (!names.isEmpty()) {
                // We caught one or more matches: throw
                return new TypeError(POSITIONAL_ONLY, name,
                        String.join(", ", names));
            } else {
                // No match, so it is just unexpected altogether
                return new TypeError(UNEXPECTED_KEYWORD, name, name);
            }
        }

        /**
         * Diagnose which positional or keywords arguments are missing,
         * and throw {@link TypeError} listing them. We call this when
         * we have already detected a problem, and the process is one of
         * going over the data again to create an accurate message.
         *
         * @param missing number of missing arguments
         * @param defcount number of positional defaults available (or
         *     -1)
         * @return TypeError listing names of the missing arguments
         */
        /*
         * Compare CPython ceval.c::missing_arguments(). Unlike that
         * function, on diagnosing a problem, we do not have to set a
         * message and return status so the caller can "goto fail" and
         * clean up. We can just throw directly.
         */
        protected TypeError missingArguments(int missing,
                int defcount) {
            String kind;
            int start, end;

            // Choose the range in which to look for null arguments
            if (defcount >= 0) {
                kind = "positional";
                start = 0;
                end = argcount - defcount;
            } else {
                kind = "keyword-only";
                start = argcount;
                end = start + kwonlyargcount;
            }

            // Make a list of names from that range where value is null
            ArrayList<String> names = new ArrayList<>(missing);
            for (int i = start, j = 0; i < end; i++) {
                if (getLocal(i) == null) { names.add(j++, nameArg(i)); }
            }

            // Formulate an error from the list
            return missingNamesTypeError(kind, names);
        }

        /**
         * Compose a {@link TypeError} from the missing argument names.
         */
        /*
         * Compare CPython ceval.c::format_missing(). Unlike that
         * function, on diagnosing a problem, we do not have to set a
         * message and return status so the caller can "goto fail" and
         * clean up. We can just throw directly.
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
                    // Stitch into a nice comma-separated list.
                    joinedNames = String.join(", ", names) + tail;
            }

            return new TypeError(
                    "%s() missing %d required %s argument%s: %s", name,
                    len, kind, len == 1 ? "" : "s", joinedNames);
        }
    }

    class ArrayFrameWrapper extends FrameWrapper {

        private final Object[] args;
        final int start;
        final int count;

        ArrayFrameWrapper(Object[] args, int start, int count) {
            super();
            this.args = args;
            this.start = start;
            this.count = count;
        }

        ArrayFrameWrapper(Object[] args) {
            this(args, 0, args.length);
        }

        @Override
        Object getLocal(int i) {
            return args[start + i];
        }

        @Override
        void setLocal(int i, Object v) {
            args[start + i] = v;
        }

        @Override
        void setPositionalArguments(PyTuple argsTuple) {
            int n = Math.min(argsTuple.value.length, argcount);
            System.arraycopy(argsTuple.value, 0, args, start, n);
        }
    }

    /**
     * Parse when an args tuple and keyword dictionary are supplied,
     * that is, for a classic call.
     */
    void parseToFrame(FrameWrapper frame, PyTuple args, PyDict kwargs) {

        final int nargs = args.value.length;

        // Set parameters from the positional arguments in the call.
        frame.setPositionalArguments(args);

        // Set parameters from the keyword arguments in the call.
        if (kwargs != null && !kwargs.isEmpty())
            frame.setKeywordArguments(kwargs);

        if (nargs > argcount) {

            if (hasVarArgs()) {
                // Locate the * parameter in the frame
                // Put the excess positional arguments there
                frame.setLocal(varArgsIndex, new PyTuple(args.value,
                        argcount, nargs - argcount));
            } else {
                // Excess positional arguments but no *args for them.
                throw frame.tooManyPositional(nargs);
            }

        } else { // nargs <= argcount

            if (hasVarArgs()) {
                // No excess: set the * parameter in the frame to empty
                frame.setLocal(varArgsIndex, PyTuple.EMPTY);
            }

            if (nargs < argcount) {
                // Set remaining positional parameters from default
                frame.applyDefaults(nargs, defaults);
            }
        }

        if (kwonlyargcount > 0)
            // Set keyword parameters from default values
            frame.applyKWDefaults(kwdefaults);
    }
}
