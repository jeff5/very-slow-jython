package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyRT.BinaryOpCallSite;
import uk.co.farowl.vsj3.evo1.PyRT.UnaryOpCallSite;

/**
 * Test of the mechanism for binding call sites that involve
 * implementations of Python {@code bool}. The complication (which we
 * test has been dealt with correctly) is that {@code bool} inherits
 * methods from {@code int}.
 */
class BoolCallSites extends UnitTestSupport {

    /**
     * Test invocation of {@code __neg__} call site on accepted classes.
     */
    @Test
    void site_neg() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Object x : List.of(false, true)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(-toInt(x), toInt(r));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        for (Object x : List.of(false, true)) {
            Object r = invoker.invokeExact(x);
            assertEquals(-toInt(x), toInt(r));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * classes.
     */
    @Test
    void site_repr() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object) x);
            assertPythonType(PyUnicode.TYPE, r);
            String e = x ? "True" : "False";
            assertEquals(e.toString(), r.toString());
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object) x);
            String e = x ? "True" : "False";
            assertEquals(e, r.toString());
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of {@code __sub__} call site on accepted classes.
     * For this test, we need parts of {@code bool} to work too, since
     * that type will be consulted about {@code bool - int}. In the
     * interests of simplicity, we pair all combinations of the accepted
     * classes, so we will "accidentally" test {@code bool - bool}.
     */
    @Test
    void site_sub() throws Throwable {

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_sub);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(true, false)) {
                Object r = invoker.invokeExact(v, w);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(true, false)) {
                Object r = invoker.invokeExact(v, w);
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of {@code __and__} call site on accepted classes.
     * For this test, we need parts of {@code bool} to work too, since
     * that type will be consulted about {@code bool & int}.
     */
    @Test
    void site_and() throws Throwable {

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_and);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = invoker.invokeExact((Object) v, (Object) w);
                assertEquals(Boolean.class, r.getClass());
                assertEquals(v & w, r);
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = invoker.invokeExact((Object) v, (Object) w);
                assertEquals(v & w, r);
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }
}
