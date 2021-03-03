package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link PyWrapperDescr}s formed on built-in Python
 * {@code int}. The accepted implementations are {@link PyLong}
 * subclasses, {@code Integer}, {@code BigInteger}, and Boolean (since
 * {@code bool} subclasses {@code int} in Python).
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
        Object r;

        // x is Integer, BigInteger, PyLong
        for (Object x : List.of(px, ix, bx, false, true)) {
            r = neg.__call__(Py.tuple(x), null);
            assertPythonType(PyLong.TYPE, r);
            int exp = -PyLong.asInt(x);
            assertEquals(exp, PyLong.asInt(r));
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
        Object r;

        // x is Integer, BigInteger, PyLong
        for (Object x : List.of(px, ix, bx, false, true)) {
            r = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, r);
            String exp = String.format("%d", PyLong.asInt(x));
            assertEquals(exp, r.toString());
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
        Object r;

        // x is Integer, BigInteger, PyLong
        for (Object x : List.of(px, ix, bx, false, true)) {
            r = f.__call__(Py.tuple(x), null);
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
        Object r;

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = PyLong.asInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                r = sub.__call__(Py.tuple(v, w), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - PyLong.asInt(w);
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
        Object r;

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(iv, bv, pv, true, false)) {
            int vv = PyLong.asInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(iw, bw, pw, true, false)) {
                r = rsub.__call__(Py.tuple(w, v), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - PyLong.asInt(w);
                assertEquals(exp, r);
            }
        }
    }
}
