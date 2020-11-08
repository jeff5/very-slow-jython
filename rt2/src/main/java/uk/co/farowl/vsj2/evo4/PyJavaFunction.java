package uk.co.farowl.vsj2.evo4;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.throwException;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.B;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.DICT;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.I;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.O;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.OA;
import static uk.co.farowl.vsj2.evo4.ClassShorthand.TUPLE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.EnumSet;

import uk.co.farowl.vsj2.evo4.MethodDef.Flag;

/** The Python {@code builtin_function_or_method} object. */
class PyJavaFunction implements PyObject {

    static final PyType TYPE = PyType.fromSpec(new PyType.Spec("builtin_function_or_method",
            PyJavaFunction.class));

    @Override
    public PyType getType() { return TYPE; }

    /** Description of the function to call */
    final MethodDef methodDef;

    /** Name of the containing module (or {@code null}). */
    final PyUnicode module;

    /** The MethodType of {@link #opCall}. */
    protected static final MethodType methCallType =
            MethodType.methodType(O, TUPLE, DICT);

    /**
     * Handle of Java method that implements the function or method. The
     * type of this handle is {@code (TUPLE, DICT) O}. It wraps
     * methodDef.meth with a check on the number of arguments and
     * manipulation to deliver them to this target.
     */
    final MethodHandle opCall;

    PyJavaFunction(MethodDef def, PyUnicode module) {
        this.methodDef = def;
        this.module = module;
        this.opCall = getOpCallHandle(def);
    }

    PyJavaFunction(MethodDef def) {
        this(def, null);
    }

    @Override
    public String toString() {
        return String.format("<built-in function %s>", methodDef.name);
    }

    /**
     * Create a MethodHandle with the signature {@code (TUPLE, DICT) O}
     * that will make a "classic call" to the method described in a
     * {@link MethodDef}.
     *
     * @param def defining information
     * @return required handle
     */
    // XXX This should probably be in MethodDef
    private static MethodHandle getOpCallHandle(MethodDef def) {
        EnumSet<Flag> f = def.flags;

        if (f.contains(Flag.FASTCALL)) {
            if (f.contains(Flag.KEYWORDS)) {
                // FAST, VARARGS, KEYWORDS: object[], int, int, tuple
                throw new InterpreterError("Not implemented");
            } else {
                // FAST, VARARGS: object[], int, int
                throw new InterpreterError("Not implemented");
            }

        } else if (f.contains(Flag.KEYWORDS)) {
            // VARARGS, KEYWORDS: tuple, dict
            return Util.wrapKeywords(def);

        } else if (f.contains(Flag.VARARGS)) {
            // VARARGS: tuple
            return Util.wrapVarargs(def);

        } else {
            // Fixed arity: object, object, ...
            return Util.wrapFixedArity(def);
        }

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
     * Helpers for {@link PyJavaFunction} used to construct
     * {@code MethodHandle}s.
     */
    // XXX This should probably be in MethodDef
    private static class Util {

        /** Single re-used instance of {@code BadCallException} */
        static final BadCallException BADCALL = new BadCallException();

        /** Lookup for resolving handle3s throughout {@code Util} */
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
                mh = LOOKUP.findStatic(Util.class, "notNull", DT);
                notNullGuard = dropArguments(mh, 0, TUPLE);
                // Used in wrapVarargs
                mh = LOOKUP.findStatic(Util.class, "nullOrEmpty", DT);
                nullOrEmptyGuard = dropArguments(mh, 0, TUPLE);
                // Used in wrapFixedArity
                fixedArityGuard =
                        LOOKUP.findStatic(Util.class, "fixedArityGuard",
                                MethodType.methodType(B, OA, DICT, I));

            } catch (NoSuchMethodException | IllegalAccessException
                    | NoSuchFieldException e) {
                throw new InterpreterError(e, "during handle lookup");
            }
        }

        /**
         * {@code ()PyObject} handle to throw a
         * {@link BadCallException}. <pre>
         * λ : throw BadCallException
         * </pre>
         */
        private static final MethodHandle throwBadCall =
                throwException(O, BadCallException.class)
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

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must describe a KEYWORDS function, to a handle that
         * accepts a classic (*args, **kwargs) call, in which the
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
         * accepts a classic (*args, **kwargs) call, in which the
         * dictionary must be {@code null} or empty.
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
         * must describe a fixed-arity plain signature, to a
         * handle that accepts a classic {@code (*args, **kwargs)}
         * call, in which the dictionary must be {@code null} or empty,
         * and the size of the tuple match the number of argument.
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
         * Check PyDict argument is {@code null} or empty and that
         * an array (tuple innards) has a specific size.
         */
        @SuppressWarnings("unused") // Used reflectively
        private static boolean fixedArityGuard(PyObject[] a,
                PyDict d, int n) {
            return (d == null || d.size() == 0) && a.length == n;
        }
    }

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyJavaFunction func) throws Throwable {
        return func.repr();
    }

    protected PyUnicode repr() {
        return PyUnicode.fromFormat("<built-in function %s>",
                this.methodDef.name);
    }

    static PyObject __call__(PyJavaFunction f, PyTuple args,
            PyDict kwargs) throws Throwable {
        try {
            return (PyObject) f.opCall.invokeExact(args, kwargs);
        } catch (BadCallException bce) {
            // After the BCE, check() should always throw.
            f.methodDef.check(args, kwargs);
            // It didn't :( so this is an internal error
            throw new InterpreterError(bce,
                    "Unexplained BadCallException in __call__");
        }
    }

}
