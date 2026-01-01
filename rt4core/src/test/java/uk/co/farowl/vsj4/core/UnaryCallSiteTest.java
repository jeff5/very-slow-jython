// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandle;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj4.core.PyRT.UnaryOpCallSite;
import uk.co.farowl.vsj4.kernel.SpecialMethod;

/**
 * Test of the mechanism for invoking and updating unary call sites on a
 * variety of types. The particular operations are not the focus: we are
 * testing the mechanisms.
 */
class UnaryCallSiteTest extends UnitTestSupport {

    /**
     * Test invocation of a {@code __neg__} call site on accepted
     * {@code float} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void neg_float() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyFloat.TYPE, r);
            assertEquals(-42.0, PyFloat.asDouble(r));
        }
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1e42);
        px = newPyFloat(-1e42);
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertEquals(1e42, PyFloat.asDouble(r));
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test invocation of a {@code __neg__} call site on accepted
     * {@code int} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void neg_int() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_neg);
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
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should entail no further fall-back)
        ix = -42;
        bx = BigInteger.valueOf(ix);
        px = newPyLong(ix);
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = invoker.invokeExact(x);
            assertEquals(-toInt(x), toInt(r));
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test invocation of a {@code __neg__} call site on accepted
     * {@code bool} classes. This is to treat {@code bool} as an
     * {@code int}.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void neg_bool() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_neg);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Object x : List.of(false, true)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(-toInt(x), toInt(r));
        }
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should entail no further fall-back)
        for (Object x : List.of(false, true)) {
            Object r = invoker.invokeExact(x);
            assertEquals(-toInt(x), toInt(r));
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code float} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_float() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, r);
            assertEquals("42.0", r.toString());
        }
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should involve no fall-back)
        dx = Double.valueOf(-1.25);
        px = newPyFloat(-1.25);
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertEquals("-1.25", r.toString());
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code int} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_int() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
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
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should entail no further fall-back)
        ix = Integer.MAX_VALUE;
        bx = BigInteger.valueOf(ix);
        px = newPyLong(ix);
        for (Object x : List.of(px, ix, bx)) {
            Object r = invoker.invokeExact(x);
            String e = Integer.toString(toInt(x));
            assertEquals(e, r.toString());
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code bool} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_bool() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object)x);
            assertPythonType(PyUnicode.TYPE, r);
            String e = x ? "True" : "False";
            assertEquals(e.toString(), r.toString());
        }
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke (should entail no further fall-back)
        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object)x);
            String e = x ? "True" : "False";
            assertEquals(e, r.toString());
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }

    /**
     * Test a {@code __invert__} call site throws {@link TypeError}
     * {@code float} classes.
     */
    @SuppressWarnings("static-method")
    @Test
    void invert_float_error() {

        // Bootstrap the call site
        UnaryOpCallSite cs =
                new UnaryOpCallSite(SpecialMethod.op_invert);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            assertRaises(PyExc.TypeError, () -> invoker.invokeExact(x));
        }
        int baseFallbackCalls = cs.fallbackCount;

        // Re-invoke. (There will be further fall-back calls.)
        dx = -1e42;
        px = newPyFloat(dx);
        for (Object x : List.of(px, dx)) {
            assertRaises(PyExc.TypeError, () -> invoker.invokeExact(x));
        }
        // FIXME Call-site fall-back behaviour and test expectations
        // assertEquals(baseFallbackCalls, cs.fallbackCount,
        // "fallback calls");
    }
}
