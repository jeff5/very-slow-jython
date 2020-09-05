/**
 *
 */
package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.PyType.Flag;
import uk.co.farowl.vsj2.evo4.PyType.Spec;

/**
 * Unit tests for {@link PyType}.
 *
 */
class PyTypeTest {

    static final PyType OBJECT = PyBaseObject.TYPE;
    static final PyType TYPE = PyType.TYPE;

    @SuppressWarnings("unused")
    private static class C0 implements PyObject {

        static PyType TYPE =
                PyType.fromSpec(new Spec("TestC0", C0.class));
        int value;

        C0(int v) { this.value = v; }

        @Override
        public PyType getType() { return TYPE; }

        static PyObject __new__(PyType type, PyTuple args,
                PyDict kwargs) throws Throwable {
            PyLong value = (PyLong) args.get(0);
            return new C0(value.asSize());
        }

        static PyObject __str__(C0 c) {
            return Py.str(Integer.toString(c.value));
        }
    }

    @SuppressWarnings("unused")
    private static class C1 implements PyObject {

        static PyType TYPE =
                PyType.fromSpec(new Spec("TestC1", C1.class)
                        .flag(Flag.MUTABLE).flag(Flag.REMOVABLE));
        int value;

        C1(int value) { this.value = value; }

        @Override
        public PyType getType() { return TYPE; }

        static PyObject __add__(C1 v, PyObject w) {
            return w instanceof C1 ? new C1(v.value + ((C1) w).value)
                    : Py.NotImplemented;
        }
    }

    static final PyType INT = PyLong.TYPE;
    static final PyType BOOL = PyBool.TYPE;

    /**
     * Test method for {@link PyType#fromSpec(PyType.Spec)}.
     */
    @Test
    void testFromSpecC0() {
        PyType t = C0.TYPE;
        assertEquals("TestC0", t.name);
        assertEquals(OBJECT, t.getBase());
        assertArrayEquals(new PyType[] {OBJECT}, t.getBases());
        assertEquals(EnumSet.of(Flag.BASETYPE), t.flags);
    }

    /**
     * Test method for {@link PyType#fromSpec(PyType.Spec)}.
     */
    @Test
    void testFromSpecC1() {
        PyType t = C1.TYPE;
        assertEquals("TestC1", t.name);
        assertEquals(OBJECT, t.getBase());
        assertArrayEquals(new PyType[] {OBJECT}, t.getBases());
        assertEquals(
                EnumSet.of(Flag.BASETYPE, Flag.MUTABLE, Flag.REMOVABLE),
                t.flags);
    }

    /**
     * Test method for {@link PyType#getType()}.
     */
    @Test
    void testGetType() {
        assertEquals("type", OBJECT.getType().name);
        assertEquals("type", TYPE.getType().name);
        C0 c0 = new C0(42);
        assertEquals("TestC0", c0.getType().name);
        C1 c1 = new C1(42);
        assertEquals("TestC1", c1.getType().name);
    }

    /**
     * Test method for {@link PyType#toString()}.
     */
    @Test
    void testToString() {
        assertEquals("<class 'TestC0'>", C0.TYPE.toString());
        assertEquals("<class 'object'>", OBJECT.toString());
        assertEquals("<class 'type'>", TYPE.toString());
        assertEquals("<class 'int'>", INT.toString());
    }

    /**
     * Test method for {@link PyType#getName()}.
     */
    @Test
    void testGetName() {
        assertEquals("TestC0", C0.TYPE.getName());
        assertEquals("TestC1", C1.TYPE.getName());
    }

    /**
     * Test method for {@link PyType#isSubTypeOf(PyType)}.
     */
    @Test
    void testIsSubTypeOf() {
        assertTrue(BOOL.isSubTypeOf(OBJECT));
        assertTrue(BOOL.isSubTypeOf(INT));
        assertTrue(BOOL.isSubTypeOf(BOOL));
        assertTrue(INT.isSubTypeOf(OBJECT));
        assertTrue(OBJECT.isSubTypeOf(OBJECT));
    }

    /**
     * Test method for {@link PyType#isMutable()}.
     */
    @Test
    void testIsMutable() {
        assertFalse(C0.TYPE.isMutable());
        assertTrue(C1.TYPE.isMutable());
    }

    /**
     * Test method for {@link PyType#getBase()}.
     */
    @Test
    void testGetBase() {
        assertNull(OBJECT.getBase());
        assertEquals(OBJECT, TYPE.getBase());
        assertEquals(OBJECT, INT.getBase());
        assertEquals(INT, BOOL.getBase());
        assertEquals(BaseException.TYPE, PyException.TYPE.getBase());
        assertEquals(PyException.TYPE, TypeError.TYPE.getBase());
    }

    /**
     * Test method for {@link PyType#getBases()}.
     */
    @Test
    void testGetBases() {
        assertArrayEquals(new PyType[] {}, OBJECT.getBases());
        assertArrayEquals(new PyType[] {OBJECT}, C0.TYPE.getBases());
        assertArrayEquals(new PyType[] {OBJECT}, TYPE.getBases());
        assertArrayEquals(new PyType[] {OBJECT}, INT.getBases());
        assertArrayEquals(new PyType[] {INT}, BOOL.getBases());
        assertArrayEquals(new PyType[] {BaseException.TYPE},
                PyException.TYPE.getBases());
        assertArrayEquals(new PyType[] {PyException.TYPE},
                TypeError.TYPE.getBases());
    }

    /**
     * Test method for {@link PyType#__repr__(PyType)}. We test this
     * through the abstract object API.
     *
     * @throws Throwable
     */
    @Test
    void test__repr__() throws Throwable {
        assertEquals(Py.str("<class 'TestC0'>"),
                Abstract.repr(C0.TYPE));
        assertEquals(Py.str("<class 'object'>"), Abstract.repr(OBJECT));
        assertEquals(Py.str("<class 'type'>"), Abstract.repr(TYPE));
        assertEquals(Py.str("<class 'int'>"), Abstract.repr(INT));
    }

    /**
     * Test method for {@link PyType#__call__(PyType, PyTuple, PyDict)}.
     * Calling a type is requesting an instance of that type.
     *
     * @throws Throwable
     */
    @Test
    void test__call__() throws Throwable {
        PyObject c = PyType.__call__(C0.TYPE, Py.tuple(Py.val(42)),
                Py.dict());
        assertEquals(C0.TYPE, c.getType());
    }

    /**
     * Test bases and MRO in certain simple numeric types by
     * {@link PyType#getMRO()}. This is a test that fundamental types
     * (constants in the {@link PyType} class) are constructed with the
     * right MRO.
     */
    @Test
    void testBuiltinMRO() {
        // object
        PyType t = Py.object().getType();
        assertNull(t.getBase());
        assertArrayEquals(new PyType[] {OBJECT}, t.getMRO());

        // type
        t = t.getType();
        assertEquals(OBJECT, t.getBase());
        assertArrayEquals(new PyType[] {TYPE, OBJECT}, t.getMRO());

        // int
        t = Py.val(1).getType();
        assertEquals(OBJECT, t.getBase());
        assertArrayEquals(new PyType[] {INT, OBJECT}, t.getMRO());

        // bool
        t = Py.True.getType();
        assertEquals(INT, t.getBase());
        assertArrayEquals(new PyType[] {BOOL, INT, OBJECT}, t.getMRO());
    }

    /**
     * Test MRO in certain exception types by {@link PyType#getMRO()}.
     * Exceptions provide longer MROs than simple types, but (except for
     * {@code io.UnsupportedOperation}) no multiple inheritance.
     */
    @Test
    void testExceptionsMRO() {

        PyType OBJECT = PyBaseObject.TYPE;
        PyType BASE_EXCEPTION = BaseException.TYPE;
        PyType EXCEPTION = PyException.TYPE;
        PyType TYPE_ERROR = TypeError.TYPE;

        // Basic exceptions from their type objects
        assertEquals(OBJECT, BASE_EXCEPTION.getBase());
        assertEquals(BASE_EXCEPTION, EXCEPTION.getBase());
        assertArrayEquals(
                new PyType[] {EXCEPTION, BASE_EXCEPTION, OBJECT},
                EXCEPTION.getMRO());

        // TypeError accessed via instance for a change
        PyType t = new TypeError("").getType();
        assertEquals(EXCEPTION, t.getBase());
        PyType[] exp = new PyType[] {TYPE_ERROR, EXCEPTION,
                BASE_EXCEPTION, OBJECT};
        assertArrayEquals(exp, t.getMRO());
    }

}
