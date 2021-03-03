package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyRT.BinaryOpCallSite;
import uk.co.farowl.vsj3.evo1.PyRT.UnaryOpCallSite;

/**
 * Test of the mechanism for binding call sites that involve
 * implementations of Python {@code float}.
 *
 */
class FloatCallSites {

    /** Test invocation of __neg__ call site on accepted classes. */
    @Test
    void site_neg() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = newPyFloat(42.0);

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertPythonType(PyFloat.TYPE, res);
            assertEquals(-42.0, PyFloat.asDouble(res));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = newPyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertEquals(1e42, PyFloat.asDouble(res));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test invocation of __repr__ call site on accepted classes. */
    @Test
    void site_repr() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = newPyFloat(42.0);

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, res);
            assertEquals("42.0", res.toString());
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1.25);
        px = newPyFloat(-1.25);
        for (Object x : List.of(px, dx)) {
            final Object res = invoker.invokeExact(x);
            assertEquals("-1.25", res.toString());
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test __invert__ call site throws TypeError. */
    @Test
    void site_invert_error() throws Throwable {

        Object dx = Double.valueOf(42.0);
        Object px = newPyFloat(42.0);

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_invert);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            assertThrows(TypeError.class, () -> invoker.invokeExact(x));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = newPyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            assertThrows(TypeError.class, () -> invoker.invokeExact(x));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of __sub__ call site on accepted classes. All
     * combinations of the accepted classes must be valid without
     * resorting to the reflected operation, since both are accepted
     * implementations of the *same* type.
     */
    @Test
    void site_sub() throws Throwable {

        Object dv = Double.valueOf(50), dw = Double.valueOf(8);
        Object pv = newPyFloat(dv), pw = newPyFloat(dw);

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_sub);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(42.0, PyFloat.asDouble(res));
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dv = Double.valueOf(456);
        dw = Double.valueOf(345);
        pv = newPyFloat(dv);
        pw = newPyFloat(dw);
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(111.0, PyFloat.asDouble(res));
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of __sub__ call site on mixed types. All
     * combinations of the accepted and (non-accepted) operand classes
     * must be valid. The reflected operation will be engaged. For this
     * test, we need parts of {@code int} to work well enough to refuse
     * the job.
     */
    @Test
    void site_sub_mix() throws Throwable {

        Object da = Double.valueOf(50), db = newPyFloat(50);
        Object ia = Integer.valueOf(8), ib = BigInteger.valueOf(8);

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_sub);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for float - int.
        for (Object v : List.of(da, db)) {
            for (Object w : List.of(ia, ib)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(42.0, PyFloat.asDouble(res));
            }
        }

        // Update and invoke for int - float.
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(-42.0, PyFloat.asDouble(res));
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        da = Double.valueOf(456);
        db = newPyFloat(456);
        ia = Integer.valueOf(345);
        ib = BigInteger.valueOf(345);
        // float - int.
        for (Object v : List.of(da, db)) {
            for (Object w : List.of(ia, ib)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(111.0, PyFloat.asDouble(res));
            }
        }
        // int - float
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                final Object res = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(-111.0, PyFloat.asDouble(res));
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test __or__ call site throws TypeError. */
    @Test
    void site_or_error() throws Throwable {

        Object dv = Double.valueOf(50), dw = Double.valueOf(8);
        Object pv = newPyFloat(dv), pw = newPyFloat(dw);

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_or);
        MethodHandle invoker = cs.dynamicInvoker();

        // Update and invoke for Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                assertThrows(TypeError.class,
                        () -> invoker.invokeExact(v, w));
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dv = Double.valueOf(456);
        dw = Double.valueOf(345);
        pv = newPyFloat(dv);
        pw = newPyFloat(dw);
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                assertThrows(TypeError.class,
                        () -> invoker.invokeExact(v, w));
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    // plumbing -------------------------------------------------------

    /**
     * Force creation of an actual {@link PyFloat}
     *
     * @return from this value.
     */
    private PyFloat newPyFloat(double value) {
        return new PyFloat(PyFloat.TYPE, value);
    }

    /**
     * Force creation of an actual {@link PyFloat} from Object
     *
     * @return from this value.
     */
    private PyFloat newPyFloat(Object value) {
        double vv = 0.0;
        try {
            vv = PyFloat.asDouble(value);
        } catch (Throwable e) {
            e.printStackTrace();
            fail("Failed to create a PyFloat");
        }
        return newPyFloat(vv);
    }

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
