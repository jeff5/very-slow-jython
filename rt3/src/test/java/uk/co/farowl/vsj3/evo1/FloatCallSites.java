package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyRT.UnaryOpCallSite;

/**
 * Test of the mechanism for binding call sites that involve
 * implementations of Python {@code float}.
 *
 */
class FloatCallSites {

    /** Test invocation of __neg__ call site on accepted classes. */
    @Test
    void site_op_neg() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = new PyFloat(42.0);

        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        // Link and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertPythonType(PyFloat.TYPE, res);
            assertEquals(-42.0, PyFloat.asDouble(res));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = new PyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertEquals(1e42, PyFloat.asDouble(res));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test invocation of __repr__ call site on accepted classes. */
    @Test
    void site_op_repr() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = new PyFloat(42.0);

        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        // Link and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, res);
            assertEquals("42.0", res.toString());
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1.25);
        px = new PyFloat(-1.25);
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertEquals("-1.25", res.toString());
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test __invert__ call site throws TypeError. */
    @Test
    void site_op_invert_error() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = new PyFloat(42.0);

        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_invert);
        MethodHandle invoker = cs.dynamicInvoker();

        // Link and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            assertThrows(TypeError.class, () -> invoker.invokeExact(x));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = new PyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            assertThrows(TypeError.class, () -> invoker.invokeExact(x));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    // plumbing -------------------------------------------------------

    /**
     * The Python type of {@code o} is the one expected.
     *
     * @param expected type
     * @param o to test
     */
    private static void assertPythonType(PyType expected, Object o) {
        assertTrue(expected.checkExact(o),
                () -> String.format("Java %s not Python '%s'",
                        o.getClass().getSimpleName(), expected.name));
    }
}
