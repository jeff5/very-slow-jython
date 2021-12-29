package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyRT.BinaryOpCallSite;

/**
 * Test of the mechanism for invoking and updating binary call sites on
 * a variety of types. The particular operations are not the focus: we
 * are testing the mechanisms.
 */
class BinaryCallSiteTest extends UnitTestSupport {

    /**
     * Test invocation of a {@code __sub__} call site on accepted
     * {@code float} classes. All combinations of the accepted classes
     * must be valid. {@code float} defines class-specific
     * implementations of {@code __sub__} so it is these we end up
     * invoking. (We're unable to test for this except with the
     * debugger.)
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void sub_float() throws Throwable {

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
     * Test invocation of a {@code __sub__} call site on accepted
     * {@code int} classes. All combinations of the accepted classes
     * must be valid.
     * <p>
     * This is more complex than for {@code float} because {@code bool}
     * is a sub-class of {@code int}, so {@code Boolean} should be an
     * acceptable implementation class for "self" in {@code int}.
     * <p>
     * When {@code bool} is an argument, Python semantics require it to
     * be consulted first. However, {@code bool} inherits
     * {@code __sub__} from {@code int}.
     * <p>
     * {@code int} defines class-specific implementations of
     * {@code __sub__} so it should be these we end up invoking. (We're
     * unable to test for this except with the debugger.)
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void sub_int() throws Throwable {

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_sub);
        MethodHandle invoker = cs.dynamicInvoker();

        Integer iv = 50, iw = 8;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = invoker.invokeExact(v, w);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        iv = -Integer.MAX_VALUE;
        iw = iv + 42;
        bv = BigInteger.valueOf(iv);
        bw = BigInteger.valueOf(iw);
        pv = newPyLong(iv);
        pw = newPyLong(iw);
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = invoker.invokeExact(v, w);
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of a {@code __sub__} call site on accepted
     * {@code bool} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void sub_bool() throws Throwable {

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
     * Test invocation of a {@code __sub__} call site on mixed
     * {@code float} and {@code int} arguments. All combinations of the
     * accepted and (non-accepted) operand classes must be valid. For
     * this test, we need parts of {@code int} to work too, since that
     * type will be consulted about {@code int - float}.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void sub_float_int() throws Throwable {

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

    /**
     * Test invocation of an {@code __and__} call site on accepted
     * classes of {@code int}. For this test, we need parts of
     * {@code bool} to work too, since that type will be consulted about
     * {@code bool & int}.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void and_int() throws Throwable {

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_and);
        MethodHandle invoker = cs.dynamicInvoker();

        Integer iv = 47, iw = 58;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                if (v instanceof Boolean && w instanceof Boolean) {
                    // Avoid test of bool & bool
                } else {
                    Object r = invoker.invokeExact(v, w);
                    // The result will be Integer (since small enough)
                    assertEquals(Integer.class, r.getClass());
                    int exp = vv & toInt(w);
                    assertEquals(exp, r);
                }
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        iv = -Integer.MAX_VALUE;
        iw = -2;
        bv = BigInteger.valueOf(iv);
        bw = BigInteger.valueOf(iw);
        pv = newPyLong(iv);
        pw = newPyLong(iw);
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(iw, bw, pw, true, false)) {
                if (v instanceof Boolean && w instanceof Boolean) {
                    // Avoid test of bool & bool
                } else {
                    Object r = invoker.invokeExact(v, w);
                    int exp = vv & toInt(w);
                    assertEquals(exp, r);
                }
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test invocation of {@code __and__} call site on accepted
     * {@code bool} classes. Logical operations on {@code bool} operands
     * return {@code bool} results.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void and_bool() throws Throwable {

        // Bootstrap the call site
        BinaryOpCallSite cs = new BinaryOpCallSite(Slot.op_and);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = invoker.invokeExact((Object)v, (Object)w);
                assertEquals(Boolean.class, r.getClass());
                assertEquals(v & w, r);
            }
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = invoker.invokeExact((Object)v, (Object)w);
                assertEquals(v & w, r);
            }
        }
        assertEquals(baseFallbackCalls, cs.fallbackCalls,
                "fallback calls");
    }

    /**
     * Test the {@code __or__} call site throws {@link TypeError} on
     * {@code float}.
     */
    @SuppressWarnings("static-method")
    @Test
    void or_float_error() {

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

        // Re-invoke. (There will be further fall-back calls.)
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
        assertTrue(cs.fallbackCalls > baseFallbackCalls,
                "fallback calls");
    }
}
