package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test the {@link PyWrapperDescr}s for binary special functions on a
 * variety of types. Unlike the companion call-site tests, a descriptor
 * is <b>the descriptor in a paticular type</b> The particular
 * operations are not the focus: we are testing the mechanisms for
 * creating and calling slot wrappers.
 */
class BinarySlotWrapperTest extends UnitTestSupport {

    /**
     * Test invocation of the {@code float.__sub__} descriptor on
     * accepted {@code float} classes. All combinations of the accepted
     * classes with {@code float} and {@code int} operand types must be
     * valid.
     */
    @Test
    void float_sub() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__sub__);

        Double dv = 50.0, dw = 8.0;
        PyFloat pv = newPyFloat(dv), pw = newPyFloat(dw);
        Integer iw = 8;

        List<Object> wList = List.of(pw, dw, newPyLong(iw), iw,
                BigInteger.valueOf(iw), false, true);

        // v is Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            // w is PyFloat, Double, and int types
            for (Object w : wList) {
                Object r = sub.__call__(Py.tuple(v, w), null);
                assertPythonType(PyFloat.TYPE, r);
                double exp = dv - PyFloat.asDouble(w);
                assertEquals(exp, PyFloat.doubleValue(r));
            }
        }
    }

    /**
     * Test invocation of the {@code float.__rsub__} descriptor on
     * accepted {@code float} classes. All combinations of the accepted
     * classes with {@code float} and {@code int} operand types must be
     * valid.
     */
    @Test
    void float_rsub() throws Throwable {

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__rsub__);

        Object dv = Double.valueOf(50), dw = Double.valueOf(8);
        Object pv = newPyFloat(dv), pw = newPyFloat(dw);

        // Invoke for Double, PyFloat.
        for (Object v : List.of(dv, pv)) {
            for (Object w : List.of(dw, pw)) {
                Object r = rsub.__call__(Py.tuple(w, v), null);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(42.0, PyFloat.asDouble(r));
            }
        }
    }

    /**
     * Test invocation of the {@code int.__sub__} descriptor on accepted
     * {@code int} classes in all combinations.
     */
    @Test
    void int_sub() throws Throwable {

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
     * Test invocation of the {@code int.__rsub__} descriptor on
     * accepted {@code int} classes in all combinations.
     */
    @Test
    void int_rsub() throws Throwable {

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
     * Test invocation of the {@code float.__sub__} descriptor on
     * {@code float - int}.
     */
    @Test
    void float_sub_int() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__sub__);

        Object da = Double.valueOf(50), db = newPyFloat(50);
        Object ia = Integer.valueOf(8), ib = BigInteger.valueOf(8);

        // Invoke for float - int.
        for (Object v : List.of(da, db)) {
            for (Object w : List.of(ia, ib)) {
                Object r = sub.__call__(Py.tuple(v, w), null);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(42.0, PyFloat.asDouble(r));
            }
        }
    }

    /**
     * Test invocation of the {@code float.__rsub__} descriptor on on
     * {@code int - float}.
     */
    @Test
    void float_rsub_int() throws Throwable {

        Object da = Double.valueOf(50), db = newPyFloat(50);
        Object ia = Integer.valueOf(8), ib = BigInteger.valueOf(8);

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__rsub__);

        // Invoke for int - float.
        for (Object v : List.of(ia, ib)) {
            for (Object w : List.of(da, db)) {
                Object r = rsub.__call__(Py.tuple(w, v), null);
                assertPythonType(PyFloat.TYPE, r);
                assertEquals(-42.0, PyFloat.asDouble(r));
            }
        }
    }

    /**
     * Test invocation of the {@code bool.__sub__} descriptor.
     */
    @Test
    void bool_sub() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__sub__);

        // bool inherits from int
        PyWrapperDescr sub2 =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__sub__);
        assertSame(sub2, sub);

        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(true, false)) {
                Object r = sub.__call__(Py.tuple(v, w), null);
                // The result will be Integer
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code bool.__rsub__} descriptor.
     */
    @Test
    void bool_rsub() throws Throwable {

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__rsub__);

        // bool inherits from int
        PyWrapperDescr rsub2 =
                (PyWrapperDescr) PyLong.TYPE.lookup(ID.__rsub__);
        assertSame(rsub2, rsub);

        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(true, false)) {
                Object r = rsub.__call__(Py.tuple(w, v), null);
                // The result will be Integer
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code int.__and__} descriptor on accepted
     * {@code int} and {@code bool} classes. Even when both arguments
     * are {@code bool} we must get an integer result.
     */
    @Test
    void int_and() throws Throwable {

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
     * Test invocation of the {@code int.__rand__} descriptor on
     * accepted classes. All combinations of the accepted classes must
     * be valid, including both being {@code bool} with integer result.
     */
    @Test
    void int_rand() throws Throwable {

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

    /**
     * Test invocation of the {@code bool.__and__} descriptor. We should
     * get a {@code bool} result.
     */
    @Test
    void bool_and() throws Throwable {

        PyWrapperDescr and =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__and__);

        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = and.__call__(Py.tuple(v, w), null);
                assertEquals(Boolean.class, r.getClass());
                assertEquals(v & w, r);
            }
        }
    }

    /**
     * Test invocation of the {@code bool.__rand__} descriptor. We
     * should get a {@code bool} result.
     */
    @Test
    void bool_rand() throws Throwable {

        PyWrapperDescr rand =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__rand__);

        for (Boolean v : List.of(true, false)) {
            for (Boolean w : List.of(true, false)) {
                Object r = rand.__call__(Py.tuple(w, v), null);
                assertEquals(Boolean.class, r.getClass());
                assertEquals(v & w, r);
            }
        }
    }

    /**
     * Test invocation of the {@code str.__add__} descriptor on the adopted
     * implementations of {@code str}. Note that CPython {@code str}
     * defines {@code str.__add__} but not {@code str.__radd__}.
     */
    @Test
    void str_add() throws Throwable {

        PyWrapperDescr add =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__add__);

        String sv = "pets", sw = "hop";
        PyUnicode uv = newPyUnicode(sv), uw = newPyUnicode(sw);
        String exp = "petshop";

        // v is String, PyUnicode.
        for (Object v : List.of(sv, uv)) {
            // w is PyUnicode, String, and int types
            for (Object w : List.of(uw, sw)) {
                Object r = add.__call__(Py.tuple(v, w), null);
                assertPythonType(PyUnicode.TYPE, r);
                assertEquals(exp, toString(r));
            }
        }
    }

    /**
     * Test invocation of the {@code str.__mul__} descriptor on the adopted
     * implementations of {@code str} with the accepted implementations
     * of {@code int}. This is the one that implements
     * {@code "hello" * 3}.
     */
    @Test
    void str_mul() throws Throwable {

        PyWrapperDescr mul =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__mul__);

        String sv = "woof!";
        PyUnicode uv = newPyUnicode(sv);
        int iw = 3;

        List<Object> wList = List.of(newPyLong(iw), iw,
                BigInteger.valueOf(iw), false, true);

        // v is String, PyUnicode.
        for (Object v : List.of(sv, uv)) {
            // w is various int types
            for (Object w : wList) {
                Object r = mul.__call__(Py.tuple(v, w), null);
                assertPythonType(PyUnicode.TYPE, r);
                assertEquals(sv.repeat(toInt(w)), toString(r));
            }
        }
    }

    /**
     * Test invocation of the {@code str.__rmul__} descriptor on the adopted
     * implementations of {@code str} with the accepted implementations
     * of {@code int}. This is the one that implements
     * {@code 3 * "hello"}, once {@code int} has realised it doesn't
     * know how.
     */
    @Test
    void str_rmul() throws Throwable {

        PyWrapperDescr rmul =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__rmul__);

        int iv = 3;
        String sw = "woof!";
        PyUnicode uw = newPyUnicode(sw);

        List<Object> vList = List.of(newPyLong(iv), iv,
                BigInteger.valueOf(iv), false, true);

        // v is various int types
        for (Object v : vList) {
            // w is PyUnicode, String
            for (Object w : List.of(sw, uw)) {
                Object r = rmul.__call__(Py.tuple(w, v), null);
                assertPythonType(PyUnicode.TYPE, r);
                assertEquals(sw.repeat(toInt(v)), toString(r));
            }
        }
    }

}
