package uk.co.farowl.vsj2.evo3;

import static uk.co.farowl.vsj2.evo3.ClassShorthand.DICT;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.O;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.OA;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.I;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.B;
import static uk.co.farowl.vsj2.evo3.ClassShorthand.TUPLE;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.EnumSet;

import uk.co.farowl.vsj2.evo3.MethodDef.Flag;
import uk.co.farowl.vsj2.evo3.Slot.EmptyException;
import uk.co.farowl.vsj2.evo3.Slot.Self;

/** The Python {@code builtin_function} object. */
class PyJavaFunction implements PyObject {

    static final PyType TYPE = new PyType("builtin_function_or_method",
            PyJavaFunction.class);

    @Override
    public PyType getType() { return TYPE; }
    /** Description of the function to call */
    final MethodDef methodDef;

    /** The MethodType of {@link #methCall}. */
    protected static final MethodType methCallType =
            MethodType.methodType(O, TUPLE, DICT);
    /**
     * Handle of Java method that implements the function or method. The
     * type of this handle is {@code (TUPLE, DICT) O}. It wraps
     * methodDef.meth with a check on the number of arguments and
     * manipulation to deliver them to this target.
     */
    final MethodHandle methCall;

    /** The module in which this function was defined. */
    final PyModule module;

    PyJavaFunction(MethodDef def, PyModule module) {
        this.methodDef = def;
        this.module = module;
        this.methCall = getCallHandle(def);
    }

    @Override
    public String toString() {
        return String.format("<built-in function %s>", methodDef.name);
    }

    /**
     * Create a MethodHandle with the signature
     *
     * @param def
     * @return
     */
    private static MethodHandle getCallHandle(MethodDef def) {
        EnumSet<Flag> f = def.flags;

        if (f.contains(Flag.FASTCALL)) {
            if (f.contains(Flag.KEYWORDS)) {
                // FAST, VARARGS, KEYWORDS: object[], int, int, dict
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
     * {@link PyJavaFunction#methCall} with the wrong size of arguments
     * (unacceptable tuple size or keyword dictionary unexpectedly
     * present or null. It doesn't tell us what went wrong: instead, we
     * catch it and work out what kind of {@link TypeError} to throw.
     */
    static class BadCallException extends Exception {}

    /**
     * Helpers for {@link PyJavaFunction} used to construct
     * {@code MethodHandle}s.
     */
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
         * {@code (PyTuple, PyDictionary)boolean} handle to check
         * {@code kwargs} is not {@code null}.
         */
        private static final MethodHandle notNullGuard;

       /**
         * {@code (PyTuple, PyDictionary)boolean} handle to check
         * {@code kwargs} is {@code null} or empty. <pre>
         * λ a k : (k==null || k.empty())
         * </pre>
         */
        private static final MethodHandle nullOrEmptyGuard;

         /**
         * {@code (PyObject[], PyDictionary)boolean} handle to check
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
                notNullGuard =
                        MethodHandles.dropArguments(mh, 0, TUPLE);
                // Used in wrapVarargs
                mh = LOOKUP.findStatic(Util.class, "nullOrEmpty", DT);
                nullOrEmptyGuard =
                        MethodHandles.dropArguments(mh, 0, TUPLE);
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
                MethodHandles.throwException(O, BadCallException.class)
                        .bindTo(BADCALL);
        /**
         * {@link #throwBadCall} wrapped as
         * {@code (PyObject[], PyDictionary)PyObject}
         */
        private static final MethodHandle throwBadCallOA =
                MethodHandles.dropArguments(throwBadCall, 0, OA, DICT);
        /**
         * {@link #throwBadCall} wrapped as
         * {@code (PyTuple, PyDictionary)PyObject}
         */
        private static final MethodHandle throwBadCallTUPLE =
                MethodHandles.dropArguments(throwBadCall, 0, TUPLE,
                        DICT);


        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must correspond to the KEYWORDS function, to a handle that
         * accepts an classic (*args, **kwargs) call, in which the
         * dictionary must not be {@code null}.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapKeywords(MethodDef def) {
            // f = λ a k : meth(a, k)
            MethodHandle f = def.meth;

            // Use the guard to switch between calling and throwing
            // g = λ a k : nullOrEmpty ? fa : throw BadCall
            MethodHandle g = MethodHandles.guardWithTest(notNullGuard, f,
                    throwBadCallTUPLE);
            return g;
        }

        @SuppressWarnings("unused") // Used reflectively
        private static boolean notNull(PyDictionary d) {
            return d != null;
        }

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must correspond to the VARARG-only function, to a handle that
         * accepts an classic (*args, **kwargs) call, in which the
         * dictionary must be {@code null} or empty.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapVarargs(MethodDef def) {
            // f = λ a : meth(a)
            MethodHandle f = def.meth;

            // fa = λ a k : meth(a) (i.e. ignoring kwargs)
            MethodHandle fa = MethodHandles.dropArguments(f, 1, DICT);

            // Use the guard to switch between calling and throwing
            // g = λ a k : nullOrEmpty ? fa : throw BadCall
            MethodHandle g = MethodHandles.guardWithTest(nullOrEmptyGuard,
                    fa, throwBadCallTUPLE);
            return g;
        }

        @SuppressWarnings("unused") // Used reflectively
        private static boolean nullOrEmpty(PyDictionary d) {
            return d == null || d.size() == 0;
        }

        /**
         * Convert the method handle in {@code MethodDef def}, which
         * must correspond to the a fixed-arity plain signature, to a
         * handle that accepts an classic {@code (*args, **kwargs)}
         * call, in which the dictionary must be {@code null} or empty,
         * and the size of the tuple match the number of argument.
         *
         * @param def method definition
         * @return handle for classic call
         */
        static MethodHandle wrapFixedArity(MethodDef def) {
            // Number or arguments expected by the def target f
            int n = def.getNargs();

            // f = λ u0, u1, ... un : meth(u0, u1, ... un)
            MethodHandle f = def.meth;

            // fv = λ v k : meth(v[0], v[1], ... v[n])
            MethodHandle fv = f.asSpreader(OA, n);
            fv = MethodHandles.dropArguments(fv, 1, DICT);

            // argsOK = λ v k : (k==null || k.empty()) && v.length==n
            MethodHandle argsOK = MethodHandles.insertArguments(fixedArityGuard, 2, n);

            // Use the guard to switch between calling and throwing
            // g = λ v k : argsOK(v,k) ? fv(v,k) : throw BadCall
            MethodHandle g = MethodHandles.guardWithTest(argsOK, fv,
                    throwBadCallOA);

            // ga = λ a k : g(a.value, k)
            MethodHandle ga =
                    MethodHandles.filterArguments(g, 0, getValue);
            return ga;
        }

        /**
         * Check PyDictionary argument is {@code null} or empty and that
         * an array (tuple innards) has a specific size.
         */
        @SuppressWarnings("unused") // Used reflectively
        private static boolean fixedArityGuard(PyObject[] a,
                PyDictionary d, int n) {
            return (d == null || d.size() == 0) && a.length == n;
        }
    }

    // slot functions -------------------------------------------------

    static PyObject tp_call(PyJavaFunction f, PyTuple args,
            PyDictionary kwargs) throws Throwable {
        try {
            return (PyObject) f.methCall.invokeExact(args, kwargs);
        } catch (BadCallException bce) {
            // After the BCE, check() should always throw.
            f.methodDef.check(args, kwargs);
            // It didn't :( so this is an internal error
            throw new InterpreterError(bce,
                    "Unexplained BadCallException in tp_call");
        }
    }

}