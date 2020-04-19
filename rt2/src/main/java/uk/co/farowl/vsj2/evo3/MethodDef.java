package uk.co.farowl.vsj2.evo3;

import static uk.co.farowl.vsj2.evo3.ClassShorthand.DICT;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.I;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.O;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.TUPLE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.EnumSet;

/**
 * A class to describe a built-in function as it is declared. Only
 * certain patterns (signatures) are supported.
 * <p>
 * <table frame=box rules=all>
 * <caption>Functions</caption>
 *
 * <tr>
 * <th>Flags</th>
 * <th>Arguments</th>
 * </tr>
 *
 * <tr>
 * <td>Fixed arity</td>
 * <td>{@code object, object, ...}</td>
 * <td>1</td>
 * </tr>
 *
 * <tr>
 * <td>VARARGS</td>
 * <td>{@code tuple}</td>
 * </tr>
 *
 * <tr>
 * <td>VARARGS, KEYWORDS</td>
 * <td>{@code tuple, dict}</td>
 * </tr>
 *
 * <tr>
 * <td>FAST, VARARGS</td>
 * <td>{@code object[], int, int}</td>
 * <td>2</td>
 * </tr>
 *
 * <tr>
 * <td>FAST, VARARGS, KEYWORDS</td>
 * <td>{@code object[], int, int, dict}</td>
 * <td>2</td>
 * </tr>
 *
 * <tfoot>
 * <tr>
 * <td colspan=2>
 * <ol>
 * <li>Any number of arguments, including none</li>
 * <li>FAST may offer no advantage in a Java implementation</li>
 * </ol>
 * </td>
 * </tr>
 * </tfoot>
 * </table>
 * <p>
 * By {@code object} is meant here a Java parameter declared exactly as
 * {@code PyObject}, {@code tuple} is {@code PyTuple}, and {@code dict}
 * is {@code PyDictionary}. {@code int} means the Java primitive.
 *
 * <p>
 * The FAST versions replicate the convention in CPython that allows
 * direct access to a slice of the interpreter stack, indicated as an
 * arry (the stack), a start, and a count of arguments. (In Java we
 * cannot simply pass a {@code PyObject*} pointer to a location the
 * array.
 *
 * <p>
 * All the same possibilities exist for methods (or non-static module
 * functions), but there must be a target object of the type declaring
 * the method, shown here as {@code self}.
 *
 * <table frame=box rules=all>
 * <caption>Methods</caption>
 *
 * <tr>
 * <th>Flags</th>
 * <th>Arguments</th>
 * </tr>
 *
 * <tr>
 * <td>Fixed arity</td>
 * <td>{@code self, object, object, ...}</td>
 * <td>1</td>
 * </tr>
 *
 * <tr>
 * <td>VARARGS</td>
 * <td>{@code self, tuple}</td>
 * </tr>
 *
 * <tr>
 * <td>VARARGS, KEYWORDS</td>
 * <td>{@code self, tuple, dict}</td>
 * </tr>
 *
 * <tr>
 * <td>FAST, VARARGS</td>
 * <td>{@code self, object[], int, int}</td>
 * <td>2</td>
 * </tr>
 *
 * <tr>
 * <td>FAST, VARARGS, KEYWORDS</td>
 * <td>{@code self, object[], int, int, dict}</td>
 * <td>2</td>
 * </tr>
 *
 * </table>
 *
 * <p>
 * There is no scope for confusion between these signatures, since
 * {@code self} can never actually be {@code PyObject}. {@code self}
 * could be {@code PyTuple}, and hence a no-argument method of
 * {@code PyTuple} has the same signature as a VARARGS function.
 * However, the former will be an instance method and the latter
 * {@code static}.
 */
class MethodDef {

    /** The name of the built-in function or method */
    final String name;
    /**
     * Handle of Java method that implements the function or method. The
     * type of this handle reflects exactly consistent the declared
     * signature.
     */
    final MethodHandle meth;
    /**
     * Combination of {@code Flag} flags, which mostly describe
     * properties of the function or method, additional to
     * {@link #meth}.
     */
    final EnumSet<Flag> flags;
    /** The {@code __doc__} attribute, or {@code null} */
    final String doc;

    /**
     * Characteristics of the function or method, which it will indicate
     * in {@link MethodDef#flags} . Only certain combinations are
     * allowed.
     */
    enum Flag {
        /** A variable-length positional argument list is expected. */
        VARARGS,
        /** A keyword dictionary argument is expected. */
        KEYWORDS, //
        // CPython Meth_NOARGS and METH_O replaced by Nargs()
        /** Not currently used */
        // CLASS cannot be used for functions in modules.
        CLASS,
        /** An initial self or module argument is not expected. */
        // In CPython STATIC cannot be used for functions in modules.
        STATIC,
        /** Not used. */
        /*
         * CPython comment: COEXIST allows a method to be entered even
         * though a slot has already filled the entry. When defined, the
         * flag allows a separate method, "__contains__" for example, to
         * coexist with a defined slot like sq_contains.
         */
        COEXIST, //
        /**
         * A variable-length positional argument list is expected,
         * presented as a slice of an array. Keywords arguments (if
         * flagged) are in the same array, identified through a keyword
         * names tuple.
         */
        FASTCALL, //
    }

    /** Construct a method definition. */
    MethodDef(String name, MethodHandle mh, EnumSet<Flag> flags,
            String doc) {
        this.name = name;
        this.meth = mh;
        this.doc = doc;
        this.flags = calcFlags(flags);
    }

    /** Construct a method definition. */
    MethodDef(String name, MethodHandle mh, String doc) {
        this(name, mh, EnumSet.noneOf(Flag.class), doc);
    }

    /**
     * By examination of the {@code MethodType} of {@link #meth}, add
     * flags that are shorthands for key characteristics. Also, validate
     * that the signature is supported.
     *
     * @param flags
     * @return flags adjusted to match {@link #meth}
     */
    private EnumSet<Flag> calcFlags(EnumSet<Flag> flags) {
        EnumSet<Flag> f = EnumSet.copyOf(flags);
        MethodType type = meth.type();

        int i = 0, n = type.parameterCount();

        // We'll make up our own mind about these:
        f.removeAll(
                EnumSet.of(Flag.VARARGS, Flag.KEYWORDS, Flag.FASTCALL));

        // Skip over "self" if not static
        if (!f.contains(Flag.STATIC)) {
            if (n < 1 || !O.isAssignableFrom(type.parameterType(0)))
                throw sigError(
                        "first parameter should be a Python type");
            i = 1;
        }

        if (i < n) {
            // There are some arguments: is it VARARGS etc.
            Class<?> t = type.parameterType(i);
            if (t == TUPLE) {
                // tuple must represent a "classic" VARARGS
                i = calcFlagsClassic(i, f);
            } else if (t.getComponentType() == O) {
                // This appears to be a FAST form
                i = calcFlagsFastcall(i, f);
            } else {
                // Fixed arity therefore all PyObject (after self)
                while (i < n) {
                    if ((t = type.parameterType(i++)) != O)
                        throw sigError(OBJECT_NOT, t);
                }
            }
        }

        // Valid signature and f is the flags we need to describe it
        return f;
    }

    /**
     * Helper to {@link #calcFlags(Flag[])} dealing with classic
     * {@code (*args, **kwargs)} signature.
     */
    private int calcFlagsClassic(int i, EnumSet<Flag> f) {
        MethodType type = meth.type();
        int n = type.parameterCount();
        f.add(Flag.VARARGS);
        i += 1;
        if (i < n) {
            Class<?> k = type.parameterType(i);
            if (k == DICT) { f.add(Flag.KEYWORDS); i += 1; }
        }
        if (i != n)
            throw sigError(EXCESS_ARGS, n - 1, "*args, **kwargs");
        return i;
    }

    /**
     * Helper to {@link #calcFlags(Flag[])} dealing with fast call
     * {@code (*args, int, int, **kwnames)} signature.
     */
    private int calcFlagsFastcall(int i, EnumSet<Flag> f) {
        MethodType type = meth.type();
        int n = type.parameterCount();
        f.add(Flag.VARARGS);
        if (i + 2 <= n && type.parameterType(i) == I
                && type.parameterType(i + 1) == I) {
            // ... and the int arguments are present.
            f.add(Flag.FASTCALL);
            i += 2;
        } else {
            throw sigError("start and nargs must follow fast array");
        }
        if (i < n) {
            Class<?> k = type.parameterType(i);
            if (k == TUPLE) { f.add(Flag.KEYWORDS); i += 1; }
        }
        if (i != n)
            throw sigError(EXCESS_ARGS, n - 1, "*args, **kwargs");
        return i;
    }

    /** Construct an exception about the signature. */
    private InterpreterError sigError(String fmt, Object... args) {
        fmt = "MethodDef: in signature of %s (%s), " + fmt;
        return new InterpreterError(fmt, name, meth.type(), args);
    }

    /**
     * Check classic {@code (*args, **kwargs)} call arguments against
     * this MethodDef and throw an exception if if {@code len(args)} or
     * {@code len(kwargs)} does not match the requirements of the
     * definition.
     *
     * @param args {@code tuple} of positional arguments
     * @param kwargs keyword {@code dict} or {@code null.}
     * @throws TypeError for a mismatch
     * @throws InterpreterError for conditions that should not arise
     */
    void check(PyTuple args, PyDictionary kwargs)
            throws TypeError, InterpreterError {
        if (flags.contains(Flag.FASTCALL)) {
            if (flags.contains(Flag.KEYWORDS)) {
                // FAST, VARARGS, KEYWORDS: object[], int, int, dict
                throw new InterpreterError("Not implemented");
            } else {
                // FAST, VARARGS: object[], int, int
                throw new InterpreterError("Not implemented");
            }
        } else if (flags.contains(Flag.KEYWORDS)) {
            // VARARGS, KEYWORDS: tuple, dict
            if (kwargs == null)
                throw new InterpreterError(NULL_KEYWORD_ARGS, name);
        } else if (flags.contains(Flag.VARARGS)) {
            // VARARGS: tuple
            if (kwargs != null && kwargs.size() != 0) {
                throw new TypeError(NO_KEYWORD_ARGS, name);
            }
        } else {
            // Fixed arity: object, object, ...
            if (kwargs != null && kwargs.size() != 0) {
                throw new TypeError(NO_KEYWORD_ARGS, name);
            }
            int nargs = getNargs();
            if (args.value.length != nargs) {
                String[] msgs = {NO_ARGS, ONE_ARG, N_ARGS};
                String msg = msgs[Math.min(nargs, 2)];
                throw new TypeError(msg, name, nargs);
            }
        }
    }

    private static final String EXCESS_ARGS =
            "%d excess argument(s) after %s signature";
    private static final String OBJECT_NOT =
            "parameters should be Python objects not %s";

    static final String NO_ARGS =
            "%.200s() takes no arguments (%d given)";
    static final String ONE_ARG =
            "%.200s() takes exactly one argument (%d given)";
    static final String N_ARGS =
            "%.200s() takes exactly %d arguments (%d given)";
    static final String NO_KEYWORD_ARGS =
            "%.200s() takes no keyword arguments";
    static final String NULL_KEYWORD_ARGS =
            "%.200s() received null keyword arguments";

    boolean isStatic() { return flags.contains(Flag.STATIC); }

    /**
     * Return the number of arguments of a fixed-arity function or
     * method, not counting self.
     *
     * @return
     */
    int getNargs() {
        if (flags.contains(Flag.VARARGS)
                || flags.contains(Flag.KEYWORDS))
            throw new InternalError(
                    "MethodDef: number of args not defined");
        MethodType type = meth.type();
        int n = type.parameterCount();
        return flags.contains(Flag.STATIC) ? n : n - 1;
    }

    @Override
    public String toString() {
        return String.format("MethodDef [name=%s, meth=%s, flags=%s]",
                name, meth, flags);
    }
}
