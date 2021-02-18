package uk.co.farowl.vsj3.evo1;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.insertArguments;
import static java.lang.invoke.MethodHandles.foldArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.identity;
import static java.lang.invoke.MethodHandles.constant;
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

    static class UnaryOpCallSite extends MutableCallSite {

        /** Handle to throw {@link TypeError} (op unsupported). */
        private static final MethodHandle OPERAND_ERROR;

        /** Handle to {@link #fallback(Object)} */
        private static final MethodHandle fallbackMH;
        int fallbackCalls = 0;
        static {
            try {
                fallbackMH = lookup.findVirtual(UnaryOpCallSite.class,
                        "fallback", UOP);
                // XXX Move to Slot so may generate handle once?
                OPERAND_ERROR = lookup.findStatic(UnaryOpCallSite.class,
                        "operandError",
                        UOP.insertParameterTypes(0, Slot.class));
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
            /*
             * XXX The logic of the next bit is inadequate for derived
             * Python types which have the same Java class and
             * (obviously) different Python types. These may have to
             * guard on Python type or always indirect (if the type is
             * mutable).
             */
            if (op.isDefinedFor(vOps)) {
                resultMH = op.getSlot(vOps);
            } else {
                // Not defined for this type, so will throw
                resultMH = OPERAND_ERROR.bindTo(op);
            }

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = CLASS_GUARD.bindTo(v.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            // Compute the result for this case
            return resultMH.invokeExact(v);
        }

        // XXX Possibly move to Slot so may generate handle once.
        static Object operandError(Slot op, Object v) {
            throw Number.operandError(op, v);
        }

    }

    static class BinaryOpCallSite extends MutableCallSite {

        /** Handle to throw {@link TypeError} (op unsupported). */
        private static final MethodHandle OPERAND_ERROR;
        /** Handle that matks an empty binary operation slot. */
        private static final MethodHandle BINARY_EMPTY =
                Slot.Signature.BINARY.empty;

        private static final MethodHandle fallbackMH;
        private static final MethodHandle notImplementedMH;
        public static int fallbackCalls = 0;

        static {
            try {
                fallbackMH = lookup.findVirtual(BinaryOpCallSite.class,
                        "fallback", BINOP);
                notImplementedMH = dropArguments(
                        constant(O, Py.NotImplemented), 0, O, O);
                // XXX Move to Slot so may generate handle once?
                OPERAND_ERROR = lookup.findStatic(
                        BinaryOpCallSite.class, "operandError",
                        BINOP.insertParameterTypes(0, Slot.class));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw staticInitError(e, BinaryOpCallSite.class);
            }
        }
        private final Slot op;

        public BinaryOpCallSite(Slot op)
                throws NoSuchMethodException, IllegalAccessException {
            super(BINOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        /**
         * Compute the result of the call for this particular argument,
         * and optionally update the site to do this efficiently in
         * comparable circumstances in the future.
         *
         * @param v left operand
         * @param w right operand
         * @return {@code op(v, w)}
         * @throws Throwable
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
             * XXX The logic of the next bit is inadequate for derived
             * Python types which have the same Java class and
             * (obviously) different Python types. These may have to
             * guard on Python type or always indirect (if the type is
             * mutable).
             */
            MethodHandle slotv = op.getSlot(vOps);
            MethodHandle slotw;

            /*
             * CPython would also test: (slotw = rop.getSlot(wType)) ==
             * slotv as an optimisation , but that's never the case
             * since we use distinct op and rop slots.
             */
            if (wType == vType) // XXX use type not ops
                // Same types so only try the op slot
                if (slotv != BINARY_EMPTY) {
                    resultMH = op.getSlot(vOps);
                } else {
                    // Not defined for this type, so will throw
                    resultMH = notImplementedMH;
                }

            else if (!wType.isSubTypeOf(vType)) {
                // Ask left (if not empty) then right.
                slotw = op.getAltSlot(wOps);
                if (slotw != BINARY_EMPTY) {
                    slotw = permuteArguments(slotw, BINOP, 1, 0);
                    if (slotv != BINARY_EMPTY) {
                        resultMH = firstImplementer(slotv, slotw);
                    } else {
                        resultMH = slotw;
                    }
                } else {
                    if (slotv != BINARY_EMPTY) {
                        resultMH = slotv;
                    } else {
                        resultMH = notImplementedMH;
                    }
                }

            } else {
                // Right is sub-class: ask first (if not empty).
                slotw = op.getAltSlot(wOps);
                if (slotw != BINARY_EMPTY) {
                    slotw = permuteArguments(slotw, BINOP, 1, 0);
                    if (slotv != BINARY_EMPTY) {
                        resultMH = firstImplementer(slotv, slotw);
                    } else {
                        resultMH = slotw;
                    }
                } else {
                    if (slotv != BINARY_EMPTY) {
                        resultMH = slotv;
                    } else {
                        resultMH = notImplementedMH;
                    }
                }
            }

            /*
             * Sadly, we're not finished. resultMH is a handle that may
             * return Py.NotImplemented, which we must turn into an
             * error message.
             */
            if (resultMH == notImplementedMH) {
                // Easy case: neither slot was defined.
                resultMH = OPERAND_ERROR.bindTo(op);
            } else {
                // Dynamic case: we'll have to call it to find out.
                resultMH = firstImplementer(resultMH,
                        OPERAND_ERROR.bindTo(op));
            }

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = insertArguments(CLASS2_GUARD, 0,
                    v.getClass(), w.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            // Compute the result for this case
            return resultMH.invokeExact(v, w);
        }

        /**
         * An adapter for two method handles {@code a} and {@code b}
         * such that when invoked, first {@code a} is invoked, then if
         * it returns {@link Py#NotImplemented}, {@code b} is invoked on
         * the same arguments. {@code b} may also returns
         * {@code NotImplemented} but this gets no special treatment.
         * This corresponds to a central part of the way Python
         * implements binary operations when each operand offers a
         * different implementation.
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

        // XXX Possibly move to Slot so may generate handle once.
        static Object operandError(Slot op, Object v, Object w) {
            throw Number.operandError(op, v, w);
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
