package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link PyWrapperDescr}s formed on built-in Python
 * {@code float}. The accepted implementations are {@code PyFloat}
 * subclasses and {@code Double}, while in binary operations, the other
 * operand could also be {@code int} and its subclasses in Python.
 */
class FloatPyWrappers extends UnitTestSupport {

    /**
     * Test invocation of {@code __neg__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_neg() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__neg__);

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object res = neg.__call__(Py.tuple(x), null);
            assertPythonType(PyFloat.TYPE, res);
            assertEquals(-42.0, PyFloat.asDouble(res));
        }
    }

    /**
     * Test invocation of the {@code __neg__} descriptor on accepted
     * classes. Variant intended to test handling of empty keyword
     * dictionary (rather than {@code null}).
     */
    @Test
    void wrap_neg_emptyKwds() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__neg__);

        Double dx = -1e42;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object res = neg.__call__(Py.tuple(x), Py.dict());
            assertPythonType(PyFloat.TYPE, res);
            assertEquals(1e42, PyFloat.asDouble(res));
        }
    }

    /**
     * Test invocation of the {@code __repr__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_repr() throws Throwable {

        PyWrapperDescr repr =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__repr__);

        Double dx = 42.;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object res = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, res);
            assertEquals("42.0", res.toString());
        }
    }

    /**
     * Test invocation of the {@code __sub__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid.
     */
    @Test
    void wrap_sub() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__sub__);

        Double dv = 50., dw = 8.;
        PyFloat pv = newPyFloat(dv), pw = newPyFloat(dw);
        Integer iw = 8;

        List<Object> wList = List.of(pw, dw, newPyLong(iw), iw,
                BigInteger.valueOf(iw), false, true);

        // v is Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            // w is PyFloat, Double, and int types
            for (Object w : wList) {
                Object res = sub.__call__(Py.tuple(v, w), null);
                assertPythonType(PyFloat.TYPE, res);
                double exp = dv - PyFloat.asDouble(w);
                assertEquals(exp, PyFloat.doubleValue(res));
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
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__rsub__);

        Object dv = Double.valueOf(50), dw = Double.valueOf(8);
        Object pv = newPyFloat(dv), pw = newPyFloat(dw);

        // Invoke for Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                Object res = rsub.__call__(Py.tuple(w, v), null);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(42.0, PyFloat.asDouble(res));
            }
        }
    }

    /**
     * Test invocation of the {@code __sub__} descriptor on mixed types.
     * All combinations of the accepted and (non-accepted) operand
     * classes must be valid.
     */
    @Test
    void wrap_sub_mix() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__sub__);

        Object da = Double.valueOf(50), db = newPyFloat(50);
        Object ia = Integer.valueOf(8), ib = BigInteger.valueOf(8);

        // Invoke for float - int.
        for (Object v : List.of(da, db)) {
            for (Object w : List.of(ia, ib)) {
                Object res = sub.__call__(Py.tuple(v, w), null);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(42.0, PyFloat.asDouble(res));
            }
        }
    }

    /**
     * Test invocation of the {@code __rsub__} descriptor on mixed
     * types. All combinations of the accepted and (non-accepted)
     * operand classes must be valid.
     */
    @Test
    void wrap_rsub_mix() throws Throwable {

        Object da = Double.valueOf(50), db = newPyFloat(50);
        Object ia = Integer.valueOf(8), ib = BigInteger.valueOf(8);

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__rsub__);

        // Invoke for int - float.
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                Object res = rsub.__call__(Py.tuple(w, v), null);
                assertPythonType(PyFloat.TYPE, res);
                assertEquals(-42.0, PyFloat.asDouble(res));
            }
        }
    }
}
