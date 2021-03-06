package uk.co.farowl.vsj3.evo1;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.permuteArguments;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

/**
 * Run-time support for JVM-compiled code (including
 * {@code invokedynamic} call sites).
 */
public class PyRT {

    /** Shorthand for {@code Object.class}. */
    private static final Class<?> O = Object.class;
    /** Shorthand for {@code Class.class}. */
    private static final Class<?> C = Class.class;

    /** A method implementing a unary op has this type. */
    static final MethodType UOP = Slot.Signature.UNARY.empty.type();
    /** A method implementing a binary op has this type. */
    static final MethodType BINOP = Slot.Signature.BINARY.empty.type();
    /** Handle testing an object has a particular class. */
    static final MethodHandle CLASS_GUARD;
    /** Handle testing an object is not {@link Py#NotImplemented}. */
    /** Handle testing two object have a particular classes. */
    static final MethodHandle CLASS2_GUARD;
    static final MethodHandle IMPLEMENTED_GUARD;
    /** Lookup with the rights of the run-time system. */
    private static final Lookup lookup;

    static {
        lookup = MethodHandles.lookup();
        try {
            CLASS_GUARD = lookup.findStatic(PyRT.class, "classEquals",
                    MethodType.methodType(boolean.class, C, O));
            CLASS2_GUARD = lookup.findStatic(PyRT.class, "classEquals",
                    MethodType.methodType(boolean.class, C, C, O, O));
            IMPLEMENTED_GUARD =
                    lookup.findStatic(PyRT.class, "isImplemented",
                            MethodType.methodType(boolean.class, O));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw staticInitError(e, PyRT.class);
        }
    }

    public static CallSite bootstrap(Lookup lookup, String name,
            MethodType type)
            throws NoSuchMethodException, IllegalAccessException {
        CallSite site;
        // Probably want Slot to be public and use the slot name.
        switch (name) {
            case "negative":
                site = new UnaryOpCallSite(Slot.op_neg);
                break;
            case "add":
                site = new BinaryOpCallSite(Slot.op_add);
                break;
            case "multiply":
                site = new BinaryOpCallSite(Slot.op_mul);
                break;
            case "subtract":
                site = new BinaryOpCallSite(Slot.op_sub);
                break;
            default:
                throw new NoSuchMethodException(name);
        }

        return site;
    }

    // enum Validity {CLASS, TYPE, INSTANCE, ONCE}

    /**
     * A call site for unary Python operations. The call site is
     * constructed from a slot such as {@link Slot#op_neg}. It obtains a
     * method handle from the {@link Operations} of each distinct class
     * observed as the argument, and maintains a cache of method handles
     * guarded on those classes.
     */
    static class UnaryOpCallSite extends MutableCallSite {

        /** Handle to {@link #fallback(Object)} */
        private static final MethodHandle fallbackMH;
        int fallbackCalls = 0;
        static {
            try {
                fallbackMH = lookup.findVirtual(UnaryOpCallSite.class,
                        "fallback", UOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw staticInitError(e, UnaryOpCallSite.class);
            }
        }

        /** The abstract operation to be applied by the site. */
        private final Slot op;

        /**
         * Construct a call site with the given unary operation.
         *
         * @param op a unary operation
         * @throws NoSuchMethodException
         * @throws IllegalAccessException
         */
        public UnaryOpCallSite(Slot op)
        /* throws NoSuchMethodException, IllegalAccessException */ {
            super(UOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        /**
         * Compute the result of the call for this particular argument,
         * and optionally update the site to do this efficiently in
         * comparable circumstances in the future.
         *
         * @param v operand
         * @return {@code op(v)}
         * @throws Throwable
         */
        @SuppressWarnings("unused")
        private Object fallback(Object v) throws Throwable {
            fallbackCalls += 1;
            Operations vOps = Operations.of(v);
            MethodHandle resultMH, targetMH;
            if (op.isDefinedFor(vOps)) {
                resultMH = op.getSlot(vOps);
            } else {
                // Not defined for this type, so will throw
                resultMH = op.getOperandError();
            }

            /*
             * Compute the result for this case. If the operation
             * throws, it throws here and we do not bind resultMH as a
             * new target. If it's a one-off, we'll get another go.
             */
            Object result = resultMH.invokeExact(v);

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = CLASS_GUARD.bindTo(v.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            return result;
        }
    }

    /**
     * A call site for binary Python operations. The call site is
     * constructed from a slot such as {@link Slot#op_sub} and its
     * reflection ({@link Slot#op_sub} in the example).
     *
     * The call site implements the full semantics of the related
     * abstract operation, that is it takes care of selecting and
     * invoking the reflected operation when Python requires it.
     *
     * If either the left or right type defines type-specific binary
     * operations, it will look first for a match with one of those
     * definitions.
     *
     * If that does not succeed, it will use handles in the two
     * {@link Operations} objects
     *
     * It constructs a method handle applicable to each distinct pair of
     * classes observed as the arguments, and maintains a cache of
     * method handles guarded on those classes.
     */
    static class BinaryOpCallSite extends MutableCallSite {

        /** Handle that marks an empty binary operation slot. */
        private static final MethodHandle BINARY_EMPTY =
                Slot.Signature.BINARY.empty;

        private static final MethodHandle fallbackMH;
        public int fallbackCalls = 0;

        static {
            try {
                fallbackMH = lookup.findVirtual(BinaryOpCallSite.class,
                        "fallback", BINOP);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw staticInitError(e, BinaryOpCallSite.class);
            }
        }

        /** The abstract operation to be applied by the site. */
        private final Slot op;

        /**
         * Construct a call site with the given binary operation.
         *
         * @param op a binary operation
         * @throws NoSuchMethodException
         * @throws IllegalAccessException
         */
        public BinaryOpCallSite(Slot op)
                throws NoSuchMethodException, IllegalAccessException {
            super(BINOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        /**
         * Compute the result of the call for this particular pair of
         * arguments, and optionally update the site to do this
         * efficiently for the same classes in the future.
         *
         * @param v left operand
         * @param w right operand
         * @return {@code op(v, w)}
         * @throws Throwable on errors or if not implemented
         */
        @SuppressWarnings("unused")
        private Object fallback(Object v, Object w) throws Throwable {
            fallbackCalls += 1;
            Operations vOps = Operations.of(v);
            PyType vType = vOps.type(v);
            Operations wOps = Operations.of(w);
            PyType wType = wOps.type(w);
            MethodHandle resultMH, targetMH;
            /*
             * CPython would also test: (slotw = rop.getSlot(wType)) ==
             * slotv as an optimisation, but that's never the case since
             * we use distinct op and rop slots.
             */
            if (wType == vType) {
                // Same types so only try the op slot
                resultMH = singleType(vType, vOps, wOps);

            } else if (!wType.isSubTypeOf(vType)) {
                // Ask left (if not empty) then right.
                resultMH = leftDominant(vType, vOps, wType, wOps);

            } else {
                // Right is sub-class: ask first (if not empty).
                resultMH = rightDominant(vType, vOps, wType, wOps);
            }

            /*
             * Compute the result for this case. If the operation
             * throws, it throws here and we do not bind resultMH as a
             * new target. If it's a one-off, we'll get another go.
             */
            Object result = resultMH.invokeExact(v, w);

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = insertArguments(CLASS2_GUARD, 0,
                    v.getClass(), w.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            return result;
        }

        /**
         * Compute a method handle in the case where both arguments
         * {@code (v, w)} have the same Python type, although quite
         * possibly different Java classes (in the case where that type
         * has multiple implementations). The returned handle may throw
         * a Python exception when invoked, if that is the correct
         * behaviour, but will not return {@code NotImplemented}.
         *
         * @param type the Python type of {@code v} and {@code w}
         * @param vOps operations of the Java class of {@code v}
         * @param wOps operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle singleType(PyType type, Operations vOps,
                Operations wOps) {

            MethodHandle slotv;

            // Does the type define class-specific implementations?
            Operations.BinopGrid binops = type.binopTable.get(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                slotv = binops.get(vOps, wOps);
                if (slotv != BINARY_EMPTY) { return slotv; }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but hang on ... both have the same type.
                 */
                assert (slotv == BINARY_EMPTY); // XXX error instead?
            } else {
                /*
                 * The type provides no class-specific implementation,
                 * so use the handle in the Operations object.
                 * Typically, this will be strongly-typed on the left
                 * implementation class, but will have to test the
                 * right-hand argument against supported types.
                 */
                slotv = op.getSlot(vOps);
            }

            if (slotv == BINARY_EMPTY) {
                // Not defined for this type, so will throw
                return op.getOperandError();
            } else {
                /*
                 * slotv is a handle that may return Py.NotImplemented,
                 * which we must turn into an error message.
                 */
                return firstImplementer(slotv, op.getOperandError());
            }
        }

        /**
         * Compute a method handle in the case where the left argument
         * {@code (v)} should be consulted, then the right. The returned
         * handle may throw a Python exception when invoked, if that is
         * the correct behaviour, but will not return
         * {@code NotImplemented}.
         *
         * @param vType the Python type of {@code v}
         * @param vOps operations of the Java class of {@code v}
         * @param wType the Python type of {@code w}
         * @param wOps operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle leftDominant(PyType vType, Operations vOps,
                PyType wType, Operations wOps) {

            MethodHandle resultMH, slotv, slotw;
            Slot rop = op.alt;

            // Does vType define class-specific implementations?
            Operations.BinopGrid binops = vType.binopTable.get(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                slotv = binops.get(vOps, wOps);
                if (slotv != BINARY_EMPTY) { return slotv; }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but the signature we are looking for is not
                 * amongst them.
                 */
                assert (slotv == BINARY_EMPTY);
            } else {
                /*
                 * vType provides no class-specific implementation of
                 * op(v,w). Get the handle from the Operations object.
                 */
                slotv = op.getSlot(vOps);
            }

            // Does wType define class-specific rop implementations?
            binops = wType.binopTable.get(rop);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of w, v
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the only alternative to slotv.
                 */
                slotw = binops.get(wOps, vOps);
                if (slotw != BINARY_EMPTY) {
                    // wType provides a rop(w,v) - note ordering
                    slotw = permuteArguments(slotw, BINOP, 1, 0);
                    if (slotv == BINARY_EMPTY) {
                        // It's the only offer, so it's the answer.
                        return slotw;
                    }
                    /*
                     * slotv is also a valid offer, which must be given
                     * first refusal. Only if slotv returns
                     * Py.NotImplemented, will we try binop.
                     */
                    return firstImplementer(slotv, slotw);
                }
                /*
                 * wType provides class-specific implementations of
                 * rop(w,v), but the signature we are looking for is not
                 * amongst them.
                 */
                assert (slotw == BINARY_EMPTY);
            } else {
                /*
                 * wType provides no class-specific implementation of
                 * rop(w,v). Get the handle from the Operations object.
                 */
                slotw = rop.getSlot(wOps);
            }

            /*
             * If we haven't returned a handle yet, we now have slotv
             * and slotw, two apparent offers of a handle to compute the
             * result for the classes at hand. Either may be empty.
             * Either may return Py.NotImplemented.
             */
            if (slotw == BINARY_EMPTY) {
                if (slotv == BINARY_EMPTY) {
                    // Easy case: neither slot was defined. We're done.
                    return op.getOperandError();
                } else {
                    // slotv was the only one defined
                    resultMH = slotv;
                }
            } else {
                // slotw was defined
                slotw = permuteArguments(slotw, BINOP, 1, 0);
                if (slotv == BINARY_EMPTY) {
                    // slotv was not, so slotw is the only one defined
                    resultMH = slotw;
                } else {
                    // Both were defined, so try them in order
                    resultMH = firstImplementer(slotv, slotw);
                }
            }

            /*
             * resultMH may still return Py.NotImplemented. We use
             * firstImplementer to turn that into an error message.
             * Where we could avoid this, we already returned.
             */
            return firstImplementer(resultMH, op.getOperandError());
        }

        /**
         * Compute a method handle in the case where the right argument
         * {@code (w)} should be consulted, then the left. The returned
         * handle may throw a Python exception when invoked, if that is
         * the correct behaviour, but will not return
         * {@code NotImplemented}.
         *
         * @param vType the Python type of {@code v}
         * @param vOps operations of the Java class of {@code v}
         * @param wType the Python type of {@code w}
         * @param wOps operations of the Java class of {@code w}
         * @return a handle that provides the result (or throws)
         */
        private MethodHandle rightDominant(PyType vType,
                Operations vOps, PyType wType, Operations wOps) {

            MethodHandle resultMH, slotv, slotw;
            Slot rop = op.alt;

            // Does wType define class-specific rop implementations?
            Operations.BinopGrid binops = wType.binopTable.get(rop);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of w, v
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the answer.
                 */
                slotw = binops.get(wOps, vOps);
                if (slotw != BINARY_EMPTY) {
                    // wType provides a rop(w,v) - note ordering
                    return permuteArguments(slotw, BINOP, 1, 0);
                }
                /*
                 * wType provides class-specific implementations of
                 * rop(w,v), but the signature we are looking for is not
                 * amongst them.
                 */
                assert slotw == BINARY_EMPTY;
            } else {
                /*
                 * wType provides no class-specific implementation of
                 * rop(w,v). Get the handle from the Operations object.
                 */
                slotw = rop.getSlot(wOps);
            }

            // Does vType define class-specific implementations?
            binops = vType.binopTable.get(op);
            if (binops != null) {
                /*
                 * Are the nominal implementation classes of v, w
                 * supported as operands? These methods are not allowed
                 * to return NotImplemented, so if there's a match, it's
                 * the only alternative to slotw.
                 */
                slotv = binops.get(vOps, wOps);
                if (slotv != BINARY_EMPTY) {
                    // vType provides an op(v,w)
                    if (slotw == BINARY_EMPTY) {
                        // It's the only offer, so it's the answer.
                        return slotv;
                    }
                    /*
                     * slotw is also a valid offer, which must be given
                     * first refusal. Only if slotw returns
                     * Py.NotImplemented, will we try slotv.
                     */
                    slotw = permuteArguments(slotw, BINOP, 1, 0);
                    return firstImplementer(slotw, slotv);
                }
                /*
                 * vType provides class-specific implementations of
                 * op(v,w), but the signature we are looking for is not
                 * amongst them.
                 */
                assert slotv == BINARY_EMPTY;
            } else {
                /*
                 * vType provides no class-specific implementation of
                 * op(v,w). Get the handle from the Operations object.
                 */
                slotv = op.getSlot(vOps);
            }

            /*
             * If we haven't returned a handle yet, we now have slotv
             * and slotw, two apparent offers of a handle to compute the
             * result for the classes at hand. Either may be empty.
             * Either may return Py.NotImplemented.
             */
            if (slotw == BINARY_EMPTY) {
                if (slotv == BINARY_EMPTY) {
                    // Easy case: neither slot was defined. We're done.
                    return op.getOperandError();
                } else {
                    // slotv was the only one defined
                    resultMH = slotv;
                }
            } else {
                // slotw was defined
                slotw = permuteArguments(slotw, BINOP, 1, 0);
                if (slotv == BINARY_EMPTY) {
                    // slotw is the only one defined
                    resultMH = slotw;
                } else {
                    // Both were defined, so try them in order
                    resultMH = firstImplementer(slotw, slotv);
                }
            }

            /*
             * resultMH may still return Py.NotImplemented. We use
             * firstImplementer to turn that into an error message.
             * Where we could avoid this, we already returned.
             */
            return firstImplementer(resultMH, op.getOperandError());
        }

        /**
         * An adapter for two method handles, {@code a} and {@code b},
         * such that when the returned handle is invoked, first
         * {@code a} is invoked, and then if it returned
         * {@link Py#NotImplemented}, {@code b} is invoked on the same
         * arguments to replace the result. {@code b} may also return
         * {@code NotImplemented} but this gets no special treatment.
         * This corresponds to a central part of the way Python
         * implements binary operations when each operand offers a
         * different implementation.
         *
         * @param a to invoke unconditionally
         * @param b if {@code a} returns {@link Py#NotImplemented}
         * @return the handle that does these invocations
         */
        private static MethodHandle firstImplementer(MethodHandle a,
                MethodHandle b) {
            // bb = λ(r,v,w): b(v,w)
            MethodHandle bb = dropArguments(b, 0, O);
            // rr = λ(r,v,w): r
            MethodHandle rr = dropArguments(identity(O), 1, O, O);
            // g = λ(r,v,w): if r!=NotImplemented ? r : b(v,w)
            MethodHandle g = guardWithTest(IMPLEMENTED_GUARD, rr, bb);
            // return λ(v,w): g(a(v, w), v, w)
            return foldArguments(g, a);
        }
    }

    @SuppressWarnings("unused") // referenced as CLASS_GUARD
    private static boolean classEquals(Class<?> clazz, Object obj) {
        return clazz == obj.getClass();
    }

    @SuppressWarnings("unused") // referenced as CLASS2_GUARD
    private static boolean classEquals(Class<?> V, Class<?> W, Object v,
            Object w) {
        return V == v.getClass() && W == w.getClass();
    }

    @SuppressWarnings("unused") // referenced as IMPLEMENTED_GUARD
    private static boolean isImplemented(Object obj) {
        return obj != Py.NotImplemented;
    }

    static InterpreterError staticInitError(Throwable cause,
            Class<?> cls) {
        return new InterpreterError(cause,
                "failed initialisation of %s", cls.getSimpleName());
    }

}
