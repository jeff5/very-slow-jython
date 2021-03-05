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
 * implementations of Python {@code int}. This is more difficult than
 * for {@code float} because the inheritance relationship with
 * {@code bool} means it should be an acceptable type for "self".
 */
class IntCallSites extends UnitTestSupport {

    /**
     * Test invocation of {@code __neg__} call site on accepted classes.
     */
    @Test
    void site_neg() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(Slot.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is correct here since int.__neg__ is applicable
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(-toInt(x), toInt(r));
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        ix = -42;
        bx = BigInteger.valueOf(ix);
        px = newPyLong(ix);
        for (Object x : List.of(px, ix, bx, false, true)) {
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

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger
        // Boolean would not be correct: bool has its own __repr__
        for (Object x : List.of(px, ix, bx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, r);
            String e = Integer.toString(toInt(x));
            assertEquals(e, r.toString());
        }
        int baseFallbackCalls = cs.fallbackCalls;

        // Re-invoke (should entail no further fall-back)
        ix = Integer.MAX_VALUE;
        bx = BigInteger.valueOf(ix);
        px = newPyLong(ix);
        for (Object x : List.of(px, ix, bx)) {
            Object r = invoker.invokeExact(x);
            String e = Integer.toString(toInt(x));
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
     * Test invocation of {@code __and__} call site on accepted classes.
     * For this test, we need parts of {@code bool} to work too, since
     * that type will be consulted about {@code bool & int}.
     */
    @Test
    void site_and() throws Throwable {

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
}
