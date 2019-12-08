package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.dropArguments;
import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.lookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import uk.co.farowl.vsj1.TreePython.operator;

/**
 * Call site to be bound when a binary operation is required. It implements
 * a single cache of the last method handle, guarded by the Java class of
 * the operands.
 */
public class BinOpCallSite extends MutableCallSite {

    private final operator op;
    private final Lookup lookup; // XXX not used: what am I doing wrong?
    private final MethodHandle fallbackMH;

    public static int fallbackCalls = 0;

    public BinOpCallSite(Lookup lookup, operator op)
            throws NoSuchMethodException, IllegalAccessException {
        super(Py.BINOP);
        this.op = op;
        this.lookup = lookup;
        fallbackMH = lookup().bind(this, "fallback", Py.BINOP);
        setTarget(fallbackMH);
    }

    @SuppressWarnings("unused")
    private Object fallback(Object v, Object w) throws Throwable {
        fallbackCalls += 1;
        Class<?> V = v.getClass();
        Class<?> W = w.getClass();
        MethodType mt = MethodType.methodType(Object.class, V, W);
        // MH to compute the result for these classes
        MethodHandle resultMH = Py.findBinOp(V, op, W);
        // MH for guarded invocation (becomes new target)
        MethodHandle guarded = makeGuarded(V, W, resultMH, fallbackMH);
        setTarget(guarded);
        // Compute the result for this case
        return resultMH.invokeExact(v, w);
    }

    /**
     * Adapt two method handles, one that computes the desired result
     * specialised to the given classes, and a fall-back appropriate when
     * the arguments (when the handle is invoked) are not the given types.
     *
     * @param V Java class of left argument of operation
     * @param W Java class of right argument of operation
     * @param resultMH computes v op w, where v&in;V and w&in;W.
     * @param fallbackMH computes the result otherwise
     * @return method handle computing the result either way
     */
    private static MethodHandle makeGuarded(Class<?> V, Class<?> W,
            MethodHandle resultMH, MethodHandle fallbackMH) {
        MethodHandle testV, testW, guardedForW, guarded;
        testV = Py.HAS_CLASS.bindTo(V);
        testW = Py.HAS_CLASS.bindTo(W);
        testW = dropArguments(testW, 0, Object.class);
        guardedForW = guardWithTest(testW, resultMH, fallbackMH);
        guarded = guardWithTest(testV, guardedForW, fallbackMH);
        return guarded;
    }
}