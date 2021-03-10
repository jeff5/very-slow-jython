package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link PyWrapperDescr}s formed on built-in Python
 * {@code int}. The accepted implementations are {@link PyLong}
 * subclasses, {@code Integer}, {@code BigInteger}, and {@code Boolean}
 * (since {@code bool} subclasses {@code int} in Python).
 */
class IntPyWrappers extends UnitTestSupport {

    /**
     * Test invocation of {@code __neg__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_neg() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__neg__);

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is correct here since it has the same __neg__
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = neg.__call__(Py.tuple(x), null);
            assertPythonType(PyLong.TYPE, r);
            int exp = -toInt(x);
            assertEquals(exp, toInt(r));
        }
    }

    /**
     * Test invocation of the {@code __repr__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_repr() throws Throwable {

        PyWrapperDescr repr =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__repr__);

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is ok here since int.__repr__ is applicable
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, r);
            String e = Integer.toString(toInt(x));
            assertEquals(e, r.toString());
        }
    }

    /**
     * Test invocation of {@code __float__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_float() throws Throwable {

        PyWrapperDescr f =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__float__);

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is correct here since int.__float__ is applicable
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = f.__call__(Py.tuple(x), null);
            // The result will be Double
            assertEquals(Double.class, r.getClass());
            double exp = toDouble(x);
            assertEquals(exp, PyFloat.asDouble(r));
        }
    }

    /**
     * Test invocation of the {@code __sub__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid.
     */
    @Test
    void wrap_sub() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__sub__);

        Integer iv = 50, iw = 8;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = sub.__call__(Py.tuple(v, w), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code __rsub__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid.
     */
    @Test
    void wrap_rsub() throws Throwable {

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__rsub__);

        Integer iv = 50, iw = 8;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = rsub.__call__(Py.tuple(w, v), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code __and__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid,
     * including both being {@code bool} with integer result.
     */
    @Test
    void wrap_and() throws Throwable {

        PyWrapperDescr and =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__and__);

        Integer iv = 47, iw = 58;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = and.__call__(Py.tuple(v, w), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv & toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code __rand__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid,
     * including both being {@code bool} with integer result.
     */
    @Test
    void wrap_rand() throws Throwable {

        PyWrapperDescr rand =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__rand__);

        Integer iv = 47, iw = 58;
        BigInteger bv = BigInteger.valueOf(iv),
                bw = BigInteger.valueOf(iw);
        PyLong pv = newPyLong(iv), pw = newPyLong(iw);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                Object r = rand.__call__(Py.tuple(w, v), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv & toInt(w);
                assertEquals(exp, r);
            }
        }
    }
}
