package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test the {@link PyWrapperDescr}s for unary special functions on a
 * variety of types. The particular operations are not the focus: we are
 * testing the mechanisms for creating and calling slot wrappers.
 */
class UnarySlotWrapperTest extends UnitTestSupport {

    /**
     * Test invocation of {@code float.__neg__} descriptor on accepted
     * {@code float} classes.
     */
    @Test
    void float_neg() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__neg__);

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = neg.__call__(Py.tuple(x), null);
            assertPythonType(PyFloat.TYPE, r);
            assertEquals(-42.0, PyFloat.asDouble(r));
        }
    }

    /**
     * Test invocation of the {@code float.__neg__} descriptor on
     * accepted {@code float}. classes. Variant intended to test
     * handling of empty keyword dictionary (rather than {@code null}).
     */
    @Test
    void float_neg_emptyKwds() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__neg__);

        Double dx = -1e42;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = neg.__call__(Py.tuple(x), Py.dict());
            assertPythonType(PyFloat.TYPE, r);
            assertEquals(1e42, PyFloat.asDouble(r));
        }
    }

    /**
     * Test invocation of {@code int.__neg__} descriptor on accepted
     * {@code int} classes.
     */
    @Test
    void int_neg() throws Throwable {

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
     * Test the {@link Operations} object of {@code Boolean} is the type
     * object {@code bool}.
     */
    @Test
    void boolean_is_canonical() throws Throwable {
        PyType bool = PyType.of(true);
        assertEquals(bool, PyBool.TYPE);
        Operations boolOps = Operations.of(Boolean.FALSE);
        assertEquals(boolOps, PyBool.TYPE);
    }

    /**
     * Test invocation of {@code bool.__neg__} descriptor.
     */
    @Test
    void bool_neg() throws Throwable {

        PyWrapperDescr neg =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__neg__);
        assertEquals(PyLong.TYPE.lookup(ID.__neg__), neg);

        for (Object x : List.of(false, true)) {
            Object r = neg.__call__(Py.tuple(x), null);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(-toInt(x), toInt(r));
        }
    }

    /**
     * Test invocation of the {@code float.__repr__} descriptor on
     * accepted {@code float} classes.
     */
    @Test
    void float_repr() throws Throwable {

        PyWrapperDescr repr =
                (PyWrapperDescr) PyFloat.TYPE.lookup(ID.__repr__);

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, r);
            assertEquals("42.0", r.toString());
        }
    }

    /**
     * Test invocation of the {@code int.__repr__} descriptor on
     * accepted {@code int} classes.
     */
    @Test
    void int_repr() throws Throwable {

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
     * Test invocation of the {@code bool.__repr__} descriptor.
     */
    @Test
    void bool_repr() throws Throwable {

        PyWrapperDescr repr =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__repr__);

        for (Boolean x : List.of(false, true)) {
            Object r = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, r);
            String e = x ? "True" : "False";
            assertEquals(e, r.toString());
        }
    }

    /**
     * Test invocation of the {@code str.__repr__} descriptor on
     * accepted {@code str} classes.
     */
    @Test
    void str_repr() throws Throwable {

        PyWrapperDescr repr =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__repr__);

        String sx = "forty-two";
        PyUnicode ux = newPyUnicode(sx);

        // x is PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = repr.__call__(Py.tuple(x), null);
            assertPythonType(PyUnicode.TYPE, r);
            assertEquals("'forty-two'", r.toString());
        }
    }

    /**
     * Test invocation of {@code str.__len__} descriptor on accepted
     * {@code str} classes.
     */
    @Test
    void str_len() throws Throwable {

        PyWrapperDescr len =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__len__);

        String sx = "Hello";
        PyUnicode ux = newPyUnicode(sx);

        // Invoke for PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = len.__call__(Py.tuple(x), null);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(5, PyLong.asInt(r));
        }
    }

    /**
     * Test invocation of {@code str.__hash__} descriptor on accepted
     * {@code str} classes.
     */
    @Test
    void str_hash() throws Throwable {

        PyWrapperDescr hash =
                (PyWrapperDescr) PyUnicode.TYPE.lookup(ID.__hash__);

        String sx = "Hello";
        PyUnicode ux = newPyUnicode(sx);
        int exp = sx.hashCode();

        // Invoke for PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = hash.__call__(Py.tuple(x), null);
            assertPythonType(PyLong.TYPE, r);
            assertEquals(exp, PyLong.asInt(r));
        }
    }

    /**
     * Test invocation of {@code int.__float__} descriptor on accepted
     * {@code int} classes.
     */
    @Test
    void int_float() throws Throwable {

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

}
