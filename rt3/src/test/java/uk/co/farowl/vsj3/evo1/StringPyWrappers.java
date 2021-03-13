package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Test of {@link PyWrapperDescr}s formed on built-in Python
 * {@code str}. The accepted implementations are {@code PyUnicode}
 * subclasses and {@code String}.
 */
class StringPyWrappers extends UnitTestSupport {

    /**
     * Test invocation of {@code __len__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_len() throws Throwable {

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
     * Test invocation of {@code __hash__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_hash() throws Throwable {

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
     * Test invocation of the {@code __repr__} descriptor on accepted
     * classes.
     */
    @Test
    void wrap_repr() throws Throwable {

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
     * Test invocation of the {@code __add__} descriptor on the adopted
     * implementations of {@code str}. Note that CPython {@code str}
     * defines {@code __add__} but not {@code __radd__}.
     */
    @Test
    void wrap_add() throws Throwable {

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
     * Test invocation of the {@code __mul__} descriptor on the adopted
     * implementations of {@code str} with the accepted implementations
     * of {@code int}. This is the one that implements
     * {@code "hello" * 3}.
     */
    @Test
    void wrap_mul() throws Throwable {

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
     * Test invocation of the {@code __rmul__} descriptor on the adopted
     * implementations of {@code str} with the accepted implementations
     * of {@code int}. This is the one that implements
     * {@code 3 * "hello"}, once {@code int} has realised it doesn't
     * know how to.
     */
    @Test
    void wrap_rmul() throws Throwable {

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
