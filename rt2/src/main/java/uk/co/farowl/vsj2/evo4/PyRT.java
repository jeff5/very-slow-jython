package uk.co.farowl.vsj2.evo4;

import static java.lang.invoke.MethodHandles.guardWithTest;

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

    /** Shorthand for {@code PyObject.class}. */
    private static final Class<?> O = PyObject.class;
    /** Shorthand for {@code Class.class}. */
    private static final Class<?> C = Class.class;

    /** A method implementing a unary op has this type. */
    static final MethodType UOP = Slot.Signature.UNARY.empty.type();
    /** A method implementing a binary op has this type. */
    static final MethodType BINOP = Slot.Signature.BINARY.empty.type();
//    /** Handle to throw {@link TypeError} (binary op unsupported). */
//    static final MethodHandle OPERAND_ERROR_2;
    /** Handle of testing that an object has a particular class. */
    static final MethodHandle CLASS_GUARD;
    /** Lookup with the rights of the run-time system. */
    private static final Lookup lookup;

    static {
        lookup = MethodHandles.lookup();
        try {
//            OPERAND_ERROR_2 =
//                    lookup.findStatic(PyRT.class, "operandError",
//                            BINOP.insertParameterTypes(0, Slot.class));
            CLASS_GUARD = lookup.findStatic(PyRT.class, "classEquals",
                    MethodType.methodType(boolean.class, C, O));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw staticInitError(e, PyRT.class);
        }
    }

    public static CallSite bootstrap(Lookup lookup, String name,
            MethodType type)
            throws NoSuchMethodException, IllegalAccessException {
        CallSite site;
        // Probably want Slot to be public
        switch (name) {
            case "negative":
                site = new UnaryOpCallSite(Slot.op_neg);
                break;
            default:
                site = null;
                break;
        }

        return site;
    }

    // enum Validity {CLASS, TYPE, INSTANCE, ONCE}

    static class UnaryOpCallSite extends MutableCallSite {

        /** Handle to throw {@link TypeError} (op unsupported). */
        static final MethodHandle OPERAND_ERROR;

        private static final MethodHandle fallbackMH;
        public static int fallbackCalls = 0;
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
        private final Slot op;

        public UnaryOpCallSite(Slot op)
                throws NoSuchMethodException, IllegalAccessException {
            super(UOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        /**
         * Compute the result of the call for this particular argument,
         * and optionally resolve the site to do this efficiently in
         * comparable circumstances in the future.
         *
         * @param v operand
         * @return {@code op(v)}
         * @throws Throwable
         */
        @SuppressWarnings("unused")
        private PyObject fallback(PyObject v) throws Throwable {
            fallbackCalls += 1;
            PyType vType = v.getType();
            MethodHandle resultMH, targetMH;
            /*
             * XXX The logic of the next bit is inadequate for derived
             * Python types which have the same Java class and
             * (obviously) different Python types. These may have to
             * guard on Python type or always indirect (if the type is
             * mutable).
             */
            if (op.isDefinedFor(vType)) {
                resultMH = op.getSlot(vType);
            } else {
                // Not defined for this type, so will throw
                resultMH = OPERAND_ERROR.bindTo(op);
            }

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = CLASS_GUARD.bindTo(v.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            // Compute the result for this case
            return (PyObject) resultMH.invokeExact(v);
        }

        // XXX Possibly move to Slot so may generate handle once.
        static PyObject operandError(Slot op, PyObject v) {
            throw Number.operandError(op, v);
        }

    }

    @SuppressWarnings("unused") // referenced as CLASS_GUARD
    private static boolean classEquals(Class<?> clazz, PyObject obj) {
        return clazz == obj.getClass();
    }

    static InterpreterError staticInitError(Throwable cause,
            Class<?> cls) {
        return new InterpreterError(cause,
                "failed initialisation of %s", cls.getSimpleName());
    }

}
