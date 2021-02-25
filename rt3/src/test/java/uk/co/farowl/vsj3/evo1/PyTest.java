package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyType.Spec;

/**
 * Test some convenience methods offered from the {@link Py} class.
 * Tests of methods returning built-in objects do not replace those on
 * the implementation the types themselves.
 */
public class PyTest {

    /** Test PyUnicode.toString is not recursive, etc. */
    @Test
    void testStrToString() {
        assertEquals("hello", Py.str("hello").toString());
    }

    /** Test PyLong.toString is its repr. */
    @Test
    void testIntToString() {
        assertEquals("42", Py.val(42).toString());
        assertEquals("-42", Py.val(-42).toString());
        assertEquals("1", Py.val(BigInteger.ONE).toString());
    }

    /** Test PyTuple.toString is its repr. */
    @Test
    void testTupleToString() {
        assertEquals("()", Py.tuple().toString());
        Object[] x = {Py.val(1), Py.val(2)};
        assertEquals("(1, 2)", Py.tuple(x).toString());
        assertEquals("(1,)", Py.tuple(x[0]).toString());
    }

    /** Test PyDict.toString is its repr. */
    @Test
    void testDictToString() {
        PyDict d = Py.dict();
        assertEquals("{}", d.toString());
        d.put(Py.str("b"), Py.val(1));
        assertEquals("{'b': 1}", d.toString());
        d.put(Py.str("a"), Py.dict());
        assertEquals("{'b': 1, 'a': {}}", d.toString());
    }

    /** Test PyException to String for a simple case. */
    @Test
    void testExceptionToString() {
        PyException e = new TypeError("test");
        assertEquals("TypeError: test", e.toString());
    }

    /** Test PyException repr for a simple case. */
    @Test
    void testExceptionRepr() {
        PyException e = new TypeError("test");
        assertEquals(Py.str("TypeError('test')"), e.__repr__());
    }

    /** Every slot will fall back to {@link PyBaseObject}. */
    private static class HasToString implements CraftedType {

        @Override
        public PyType getType() {
            throw new RuntimeException();
        }

        @Override
        public String toString() {
            return Py.defaultToString(this);
        }
    }

    /**
     * Test implementation of default toString() always gives us
     * something, when __str__ or __repr__ are not defined.
     */
    @Test
    void testToString() {
        String s = (new HasToString()).toString();
        assertTrue(s.startsWith("<object object at "),
                "toString fallback");
    }

    /** Python object that defines __repr__. */
    @SuppressWarnings("unused")
    private static class HasRepr extends HasToString {

        PyType type = PyType.fromSpec(
                new Spec("0TestToStringRepr", MethodHandles.lookup()));

        @Override
        public PyType getType() { return type; }

        Object __repr__() {
            return Py.str("grim!");
        }
    }

    /**
     * Test implementation of default toString() always gives us
     * something, and __str__ or __repr__ if it can.
     */
    @Test
    void testToStringRepr() {
        String s = (new HasRepr()).toString();
        assertEquals("grim!", s, "toString is __repr__");
    }

    /** Python object that defines __str__. */
    @SuppressWarnings("unused")
    private static class HasStr extends HasToString {

        PyType type = PyType.fromSpec(
                new Spec("0TestToStringStr", MethodHandles.lookup()));

        @Override
        public PyType getType() { return type; }

        Object __str__() {
            return Py.str("w00t!");
        }
    }

    /**
     * Test implementation of default toString() always gives us
     * something, and __str__ or __repr__ if it can.
     */
    @Test
    void testToStringStr() {
        String s = (new HasStr()).toString();
        assertEquals("w00t!", s, "toString is __str__");
    }

}
