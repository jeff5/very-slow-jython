package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.PyType.Spec;

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
                (PyWrapperDescr) PyFloat.TYPE.lookup("__neg__");

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = neg.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyFloat.TYPE.lookup("__neg__");

        Double dx = -1e42;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = neg.__call__(new Object[] {x}, new String[0]);
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
                (PyWrapperDescr) PyLong.TYPE.lookup("__neg__");

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is correct here since it has the same __neg__
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = neg.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyBool.TYPE.lookup("__neg__");
        assertEquals(PyLong.TYPE.lookup("__neg__"), neg);

        for (Object x : List.of(false, true)) {
            Object r = neg.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyFloat.TYPE.lookup("__repr__");

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // x is PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = repr.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyLong.TYPE.lookup("__repr__");

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is ok here since int.__repr__ is applicable
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = repr.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyBool.TYPE.lookup("__repr__");

        for (Boolean x : List.of(false, true)) {
            Object r = repr.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyUnicode.TYPE.lookup("__repr__");

        String sx = "forty-two";
        PyUnicode ux = newPyUnicode(sx);

        // x is PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = repr.__call__(new Object[] {x}, null);
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
                (PyWrapperDescr) PyUnicode.TYPE.lookup("__len__");

        String sx = "Hello";
        PyUnicode ux = newPyUnicode(sx);

        // Invoke for PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = len.__call__(new Object[] {x}, null);
            // The result will be Integer (since slot returns int)
            assertEquals(Integer.class, r.getClass());
            assertEquals(5, r);
        }
    }

    /**
     * Test invocation of {@code str.__hash__} descriptor on accepted
     * {@code str} classes.
     */
    @Test
    void str_hash() throws Throwable {

        PyWrapperDescr hash =
                (PyWrapperDescr) PyUnicode.TYPE.lookup("__hash__");

        String sx = "Hello";
        PyUnicode ux = newPyUnicode(sx);
        int exp = sx.hashCode();

        // Invoke for PyUnicode, String
        for (Object x : List.of(ux, sx)) {
            Object r = hash.__call__(new Object[] {x}, null);
            // The result will be Integer (since slot returns int)
            assertEquals(Integer.class, r.getClass());
            assertEquals(exp, r);
        }
    }

    /**
     * Test invocation of {@code int.__float__} descriptor on accepted
     * {@code int} classes.
     */
    @Test
    void int_float() throws Throwable {

        PyWrapperDescr f =
                (PyWrapperDescr) PyLong.TYPE.lookup("__float__");

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger, Boolean
        // Boolean is correct here since int.__float__ is applicable
        for (Object x : List.of(px, ix, bx, false, true)) {
            Object r = f.__call__(new Object[] {x}, null);
            // The result will be Double
            assertEquals(Double.class, r.getClass());
            double exp = toDouble(x);
            assertEquals(exp, PyFloat.asDouble(r));
        }
    }

    /**
     * Define a Python sub-class of {@code int} in order to test binding
     * and calling descriptors added to the class but not capable of
     * being called on instances.
     * <p>
     * This isn't exactly how we should make a Python sub-type in
     * Python, but it allows testing of some behaviour before we master
     * that.
     */
    static class MyInt extends PyLong.Derived {

        static final PyType TYPE = PyType.fromSpec( //
                new Spec("MyInt", MethodHandles.lookup())
                        .base(PyLong.TYPE) //
                        .flag(Flag.MUTABLE));

        MyInt(int value) { super(TYPE, BigInteger.valueOf(value)); }
    }

    /**
     * Methods are inherited from {@code int} by {@link MyInt}. This is
     * just a sanity check to compare with
     * {@link UnarySlotWrapperTest#myint_float_neg()}.
     */
    @Test
    void myint_neg() throws AttributeError, Throwable {
        Object v = new MyInt(42);
        Object r = Callables.callMethod(v, "__neg__");
        assertEquals(-42, PyLong.asInt(r));
    }

    /**
     * A slot-wrapper descriptor added to a type that does not match the
     * defining class is allowed but produces TypeError when bound or
     * called.
     */
    // @Test // Disabled until we have slot_* functions
    void myint_float_neg() throws AttributeError, Throwable {

        // v = MyInt(42)
        Object v = new MyInt(42);

        // Sanity check: -v the long way round
        // f = v.__neg__
        Object f = Abstract.getAttr(v, "__neg__");
        // r = f() # = -42
        Object r = Callables.call(f);
        assertEquals(-42, PyLong.asInt(r));

        // Sanity check: -v the short way
        r = Number.negative(v);
        assertEquals(-42, PyLong.asInt(r));

        // Now this should break negation ...
        // MyInt.__neg__ = float.__neg__
        Object neg = Abstract.getAttr(PyFloat.TYPE, "__neg__");
        Abstract.setAttr(MyInt.TYPE, "__neg__", neg);

        // f = v.__neg__
        // f = Abstract.getAttr(v, "__neg__");
        // TypeError: descriptor '__neg__' for 'float' objects ...
        assertThrows(TypeError.class,
                () -> Abstract.getAttr(v, "__neg__"));

        // r = -v
        // r = Number.negative(v);
        // TypeError: descriptor '__neg__' requires a 'float' ...
        assertThrows(TypeError.class, () -> Number.negative(v));
    }

}
