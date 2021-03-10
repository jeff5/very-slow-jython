package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link PyWrapperDescr}s formed on built-in Python
 * {@code bool}. {@code bool} is unusual in not having a crafted class
 * as its cannonical implementation, but rather {@code Boolean}.
 * {@code bool} subclasses {@code int} in Python, and inherits most of
 * its numaric descriptors from there.
 */
class BoolPyWrappers extends UnitTestSupport {

    /**
     * Test the {@link Operations} object is the type object.
     */
    @Test
    void is_canonical() throws Throwable {
        PyType bool = PyType.of(true);
        assertEquals(bool, PyBool.TYPE);
        Operations boolOps = Operations.of(Boolean.FALSE);
        assertEquals(boolOps, PyBool.TYPE);
    }

    /**
     * Test invocation of {@code __neg__} descriptor .
     */
    @Test
    void wrap_neg() throws Throwable {

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
     * Test invocation of the {@code __repr__} descriptor.
     */
    @Test
    void wrap_repr() throws Throwable {

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
     * Test invocation of the {@code __sub__} descriptor.
     */
    @Test
    void wrap_sub() throws Throwable {

        PyWrapperDescr sub =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__sub__);

        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            for (Object w : List.of(true, false)) {
                Object r = sub.__call__(Py.tuple(v, w), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code __rsub__} descriptor.
     */
    @Test
    void wrap_rsub() throws Throwable {

        PyWrapperDescr rsub =
                (PyWrapperDescr) PyBool.TYPE.lookup(ID.__rsub__);

        // v is Integer, BigInteger, PyLong, Boolean
        for (Object v : List.of(true, false)) {
            int vv = toInt(v);
            // w is Integer, BigInteger, PyLong, Boolean
            for (Object w : List.of(true, false)) {
                Object r = rsub.__call__(Py.tuple(w, v), null);
                // The result will be Integer (since small enough)
                assertEquals(Integer.class, r.getClass());
                int exp = vv - toInt(w);
                assertEquals(exp, r);
            }
        }
    }

    /**
     * Test invocation of the {@code __and__} descriptor. We should get
     * a {@code bool} result.
     */
    @Test
    void wrap_and() throws Throwable {

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
     * Test invocation of the {@code __rand__} descriptor on accepted
     * classes. All combinations of the accepted classes must be valid,
     * including both being {@code bool} with integer result.
     */
    @Test
    void wrap_rand() throws Throwable {

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
}
