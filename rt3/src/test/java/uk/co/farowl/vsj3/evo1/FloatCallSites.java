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
class FloatCallSites extends UnitTestSupport  {

    /** Test invocation of {@code __neg__} call site on accepted classes. */
    @Test
    void site_neg() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyFloat.TYPE, r);
            assertEquals(-42.0, PyFloat.asDouble(r));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = newPyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertEquals(1e42, PyFloat.asDouble(r));
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test invocation of {@code __repr__} call site on accepted classes. */
    @Test
    void site_repr() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, r);
            assertEquals("42.0", r.toString());
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1.25);
        px = newPyFloat(-1.25);
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertEquals("-1.25", r.toString());
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test __invert__ call site throws TypeError. */
    @Test
    void site_invert_error() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_invert);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            assertThrows(TypeError.class, () -> invoker.invokeExact(x));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dx = -1e42;
        px = newPyFloat(dx);
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

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_sub);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dv = 50.0, dw = 8.0;
        PyFloat pv = newPyFloat(dv), pw = newPyFloat(dw);

        // Update and invoke for Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(42.0, PyFloat.asDouble(r));
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should involve no fall-back)
        dv = 456.0;
        dw = 345.0;
        pv = newPyFloat(dv);
        pw = newPyFloat(dw);
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(111.0, PyFloat.asDouble(r));
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
                Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(42.0, PyFloat.asDouble(r));
            }
        }

        // Update and invoke for int - float.
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                final Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(-42.0, PyFloat.asDouble(r));
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
                Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(111.0, PyFloat.asDouble(r));
            }
        }
        // int - float
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                Object r = invoker.invokeExact(v, w);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(-111.0, PyFloat.asDouble(r));
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /** Test __or__ call site throws TypeError. */
    @Test
    void site_or_error() throws Throwable {

        Double dv = 50.0, dw = 8.0;
        PyFloat pv = newPyFloat(dv), pw = newPyFloat(dw);

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
        dv = 456.0;
        dw = 345.0;
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
}
