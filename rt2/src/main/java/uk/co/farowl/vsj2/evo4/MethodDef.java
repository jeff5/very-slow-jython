package uk.co.farowl.vsj2.evo4;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.throwException;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.DICT;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.I;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.O;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.TUPLE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.EnumSet;

/**
 * A class to describe a built-in function as it is declared. Only
 * certain patterns (signatures) are supported.
 * <table>
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
 * is {@code PyDict}. {@code int} means the Java primitive.
 *
 * <p>
 * The FAST versions replicate the convention in CPython that allows
 * direct access to a slice of the interpreter stack, indicated as an
 * array (the stack), a start, and a count of arguments. (In Java we
 * cannot simply pass a {@code PyObject*} pointer to a location the in
 * array, as CPython does.)
 *
 * <p>
 * All the same possibilities exist for methods (or non-static module
 * functions), but there must be a target object of the type declaring
 * the method, shown here as {@code self}.
 *
 * <table>
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
// Compare CPython struct PyMethodDef
class MethodDef {

    /** The name of the built-in function or method */
    final String name;
    /**
     * Handle of the Java method that implements the function or method.
     * The type of this handle exactly reflects the declared signature,
     * including the "self" argument if not static.
     */
    final MethodHandle natural;
    /**
     * Handle for the implementation, adapted so that it may be invoked
     * with {@link PyObject} arguments. This type of this handle is
     * {@code (O,O,O,...)O} with the same number of parameters as
     * {@link #natural}.
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
        FASTCALL //
    }

    /**
     * The type of exception thrown by invoking
     * {@link PyJavaFunction#opCall} with the wrong size of arguments
     * (unacceptable tuple size or keyword dictionary unexpectedly
     * present or null. It doesn't tell us what went wrong: instead, we
     * catch it and work out what kind of {@link TypeError} to throw.
     */
    static class BadCallException extends Exception {

        // Suppression and stack trace disabled since singleton.
        BadCallException() { super(null, null, false, false); }
    }

    /**
     * Construct a method definition from a {@code Method} and a lookup
     * object able to access it.
     *
     * @param name overriding that declared (if not null)
     * @param m to wrap
     * @param lookup granting access to obtian handle on {@code m}
     * @param doc documentation string
     * @throws IllegalAccessException if cannot reflect {@code m}
     */
    MethodDef(String name, Method m, Lookup lookup, String doc)
            throws IllegalAccessException {
        this.name =
                name != null && name.length() > 0 ? name : m.getName();
        this.doc = doc != null ? doc : "";
        this.natural = lookup.unreflect(m);

        // Adapt the natural handle to accept and return PyObjects
        MethodType naturalType = natural.type();
        MethodHandle meth = MethodHandles.filterArguments(natural, 0,
                Clinic.argumentFilter(naturalType));
        MethodHandle rf = Clinic.returnFilter(naturalType);
        if (rf != null) {
            meth = MethodHandles.filterReturnValue(meth, rf);
        }

        // Make all arguments and return exactly PyObject
        MethodType pureObjectMT = Clinic.purePyObject(meth.type());
        this.meth = meth.asType(pureObjectMT);

        // Check whether declared as a method (first parameter is self)
        int modifiers = m.getModifiers();
        EnumSet<MethodDef.Flag> f =
                EnumSet.noneOf(MethodDef.Flag.class);
        if (Modifier.isStatic(modifiers))
            f.add(MethodDef.Flag.STATIC);
        this.flags = calcFlags(f);
    }

    /**
     * Construct a method definition from a {@code MethodHandle}.
     *
     * @param name of the method
     * @param mh handle to wrap in the definition
     * @param flags traits of the method
     * @param doc documentation string
     */
    MethodDef(String name, MethodHandle mh, EnumSet<Flag> flags,
            String doc) {
        this.name = name;
        this.natural = mh; // ... stop-gap until constructor replaced
        this.meth = mh;
        this.doc = doc;
        this.flags = calcFlags(flags);
    }

    /**
     * Construct a method definition from a {@code MethodHandle}.
     *
     * @param name of the method
     * @param mh handle to wrap in the definition
     * @param doc documentation string
     */
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
        Object[] a = new Object[args.length + 2];
        a[0] = name;
        a[1] = meth.type();
        System.arraycopy(args, 0, a, 2, args.length);
        return new InterpreterError(fmt, a);
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
    void check(PyTuple args, PyDict kwargs)
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
     * method.
     *
     * @return number of arguments
     */
    int getNargs() {
        if (flags.contains(Flag.VARARGS)
                || flags.contains(Flag.KEYWORDS))
            throw new InternalError(
                    "MethodDef: number of args not defined");
        return meth.type().parameterCount();
    }

    @Override
    public String toString() {
        return String.format("MethodDef [name=%s, meth=%s, flags=%s]",
                name, meth, flags);
    }

    /**
     * Create a {@code MethodHandle} with the signature
     * {@code (TUPLE, DICT) O} that will make a "classic call" to the
     * method described in this {@code MethodDef}.
     *
     * @return required handle
     */
    MethodHandle getOpCallHandle() {
        EnumSet<Flag> f = this.flags;

        if (f.contains(Flag.FASTCALL)) {
            if (f.contains(Flag.KEYWORDS)) {
                // FAST, VARARGS, KEYWORDS: object[], int, int, tuple
                throw new InterpreterError("FASTCALL not implemented");
            } else {
                // FAST, VARARGS: object[], int, int
                throw new InterpreterError("FASTCALL not implemented");
            }

        } else if (f.contains(Flag.KEYWORDS)) {
            // VARARGS, KEYWORDS: tuple, dict
            return MHUtil.wrapKeywords(this);

        } else if (f.contains(Flag.VARARGS)) {
            // VARARGS: tuple
            return MHUtil.wrapVarargs(this);

        } else {
            // Fixed arity: object, object, ...
            return MHUtil.wrapFixedArity(this);
        }
    }

    /**
     * Create a {@code MethodHandle} with the signature {@code (O[])O}
     * that will make a "vector call" to the method described in this
     * {@code MethodDef}.
     *
     * @return required handle
     */
    MethodHandle getVectorHandle() {
        int n = meth.type().parameterCount();
        MethodHandle vec = meth.asSpreader(MHUtil.OA, n);
        return vec;
    }

    /**
     * Create a {@code MethodHandle} with the signature {@code (O,O[])O}
     * that will make a "bound method call" to the method described in
     * this {@code MethodDef}.
     *
     * @param o to bind as "self"
     * @return required handle
     */
    MethodHandle getBoundHandle(PyObject o) {
        // ... Defend against n = 0
        int n = meth.type().parameterCount();
        MethodHandle vec = meth.bindTo(o).asSpreader(MHUtil.OA, n - 1);
        return vec;
    }

    /**
     * Helpers for {@link MethodDef} used to construct
     * {@code MethodHandle}s.
     */
    private static class MHUtil implements ClassShorthand {

        /** Single re-used instance of {@code BadCallException} */
        private static final MethodDef.BadCallException BADCALL =
                new MethodDef.BadCallException();

        /** Lookup for resolving handles throughout {@code MHUtil} */
        private static final MethodHandles.Lookup LOOKUP =
                MethodHandles.lookup();

        /**
         * {@code (PyTuple)PyObject[]} handle to get value of tuple as
         * array.
         */
        private static final MethodHandle getValue;

        /**
         * {@code (PyTuple, PyDict)boolean} handle to check
         * {@code kwargs} is not {@code null}.
         */
        private static final MethodHandle notNullGuard;

        /**
         * {@code (PyTuple, PyDict)boolean} handle to check
         * {@code kwargs} is {@code null} or empty. <pre>
         * λ a k : (k==null || k.empty())
         * </pre>
         */
        private static final MethodHandle nullOrEmptyGuard;

        /**
         * {@code (PyObject[], PyDict)boolean} handle to check
         * {@code kwargs} is {@code null} or empty and that an array
         * ({@code args} innards) has a specific size. <pre>
         * λ n a k : (k==null || k.empty()) && a.length==n
         * </pre>
         */
        private static final MethodHandle fixedArityGuard;

        static {
            try {
                MethodHandle mh;
                getValue = LOOKUP.findGetter(TUPLE, "value", OA);
                final MethodType DT = MethodType.methodType(B, DICT);
                // Used in wrapKeywords
                mh = LOOKUP.findStatic(MHUtil.class, "notNull", DT);
                notNullGuard = dropArguments(mh, 0, TUPLE);
                // Used in wrapVarargs
                mh = LOOKUP.findStatic(MHUtil.class, "nullOrEmpty", DT);
                nullOrEmptyGuard = dropArguments(mh, 0, TUPLE);
                // Used in wrapFixedArity
                fixedArityGuard = LOOKUP.findStatic(MHUtil.class,
                        "fixedArityGuard",
                        MethodType.methodType(B, OA, DICT, I));

            } catch (NoSuchMethodException | IllegalAccessException
                    | NoSuchFieldException e) {
                throw new InterpreterError(e, "during handle lookup");
            }
        }

        /**
         * {@code ()PyObject} handle to throw a
         * {@link MethodDef.BadCallException}. <pre>
         * λ : throw BadCallException
         * </pre>
         */
        private static final MethodHandle throwBadCall =
                throwException(O, MethodDef.BadCallException.class)
                        .bindTo(BADCALL);
        /**
         * {@link #throwBadCall} wrapped as
         * {@code (PyObject[], PyDict)PyObject}
         */
        private static final MethodHandle throwBadCallOA =
                dropArguments(throwBadCall, 0, OA, DICT);
        /**
         * {@link #throwBadCall} wrapped as
         * {@code (PyTuple, PyDict)PyObject}
         */
        private static final MethodHandle throwBadCallTUPLE =
                dropArguments(throwBadCall, 0, TUPLE, DICT);

        private MHUtil() {}   // No instances needed

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must describe a KEYWORDS function, to a handle that accepts a
         * classic {@code (*args, **kwargs)} call, in which the
         * dictionary must not be {@code null}.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapKeywords(MethodDef def) {
            // f = λ a k : meth(a, k)
            MethodHandle f = def.meth;

            // Use the guard to switch between calling and throwing
            // λ a k : nullOrEmpty ? fa : throw BadCall
            return guardWithTest(notNullGuard, f, throwBadCallTUPLE);
        }

        @SuppressWarnings("unused") // Used reflectively
        private static boolean notNull(PyDict d) {
            return d != null;
        }

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must describe a VARARG-only function, to a handle that
         * accepts a classic {@code (*args, **kwargs)} call, in which
         * the dictionary must be {@code null} or empty.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapVarargs(MethodDef def) {
            // f = λ a : meth(a)
            MethodHandle f = def.meth;

            // fa = λ a k : meth(a) (i.e. ignoring kwargs)
            MethodHandle fa = dropArguments(f, 1, DICT);

            // Use the guard to switch between calling and throwing
            // λ a k : nullOrEmpty ? fa : throw BadCall
            return guardWithTest(nullOrEmptyGuard, fa,
                    throwBadCallTUPLE);
        }

        @SuppressWarnings("unused") // Used reflectively
        private static boolean nullOrEmpty(PyDict d) {
            return d == null || d.size() == 0;
        }

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must describe a fixed-arity plain signature, to a handle that
         * accepts a classic {@code (*args, **kwargs)} call, in which
         * the dictionary must be {@code null} or empty, and the size of
         * the tuple match the number of argument.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapFixedArity(MethodDef def) {
            // Number of arguments expected by the def target f
            int n = def.getNargs();

            // f = λ u0, u1, ... u(n-1) : meth(u0, u1, ... u(n-1))
            MethodHandle f = def.meth;

            // fv = λ v k : meth(v[0], v[1], ... v[n-1])
            MethodHandle fv =
                    dropArguments(f.asSpreader(OA, n), 1, DICT);

            // argsOK = λ v k : (k==null || k.empty()) && v.length==n
            MethodHandle argsOK =
                    insertArguments(fixedArityGuard, 2, n);

            // Use the guard to switch between calling and throwing
            // g = λ v k : argsOK(v,k) ? fv(v,k) : throw BadCall
            MethodHandle g = guardWithTest(argsOK, fv, throwBadCallOA);

            // λ a k : g(a.value, k)
            return filterArguments(g, 0, getValue);
        }

        /**
         * Check PyDict argument is {@code null} or empty and that an
         * array (tuple innards) has a specific size.
         */
        @SuppressWarnings("unused") // Used reflectively
        private static boolean fixedArityGuard(PyObject[] a, PyDict d,
                int n) {
            return (d == null || d.size() == 0) && a.length == n;
        }
    }

}
