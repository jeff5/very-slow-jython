package uk.co.farowl.vsj1;

import static java.lang.invoke.MethodHandles.guardWithTest;
import static java.lang.invoke.MethodHandles.lookup;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;

import uk.co.farowl.vsj1.TreePython.unaryop;

/**
 * Call site to be bound when a unary operation is required. It implements
 * a single cache of the last method handle, guarded by the Java class of
 * the operand.
 */
public class UnaryOpCallSite extends MutableCallSite {

    private final unaryop op;
    private final Lookup lookup;
    private final MethodHandle fallbackMH;

    public static int fallbackCalls = 0;

    public UnaryOpCallSite(Lookup lookup, unaryop op)
            throws NoSuchMethodException, IllegalAccessException {
        super(Py.UOP);
        this.op = op;
        this.lookup = lookup;
        fallbackMH = lookup().bind(this, "fallback", Py.UOP);
        setTarget(fallbackMH);
    }

    @SuppressWarnings("unused")
    private Object fallback(Object v) throws Throwable {
        fallbackCalls += 1;
        Class<?> V = v.getClass();
        MethodType mt = MethodType.methodType(Object.class, V);
        // MH to compute the result for this class
        MethodHandle resultMH = Py.findUnaryOp(op, V);
        // MH for guarded invocation (becomes new target)
        MethodHandle testV = Py.HAS_CLASS.bindTo(V);
        setTarget(guardWithTest(testV, resultMH, fallbackMH));
        // Compute the result for this case
        return resultMH.invokeExact(v);
    }
}