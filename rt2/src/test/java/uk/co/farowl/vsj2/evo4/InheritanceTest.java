package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.Exposed.Getter;
import uk.co.farowl.vsj2.evo4.Exposed.Member;
import uk.co.farowl.vsj2.evo4.Exposed.Setter;
import uk.co.farowl.vsj2.evo4.PyType.Spec;

/**
 * A test case that sets up some Python objects defined in Java and
 * tests for inheritance of attributes between them.
 */
class InheritanceTest {

    /**
     * Something like:<pre>
      * class A:
      *     pass
      * </pre>
     */
    static class A extends AbstractPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new Spec("A", A.class));

        @Member
        int count = 0;

        double gettable;

        @Getter
        PyObject got() { return Py.val(gettable); }

        @Setter("got")
        void set_got(PyObject v) throws TypeError, Throwable {
            gettable = Number.toFloat(v).doubleValue();
        }

        protected A(PyType type, int count) {
            super(type);
            this.count = count;
            this.gettable = (double) count;
        }

        protected A(int count) { this(TYPE, count); }

        protected PyObject __neg__() { return Py.val(-count); }
    }

    /**
     * Something like:<pre>
      * class B(A):
      *     pass
      * </pre>
     */
    static class B extends A {

        static final PyType TYPE = PyType.fromSpec( //
                new Spec("B", B.class).base(A.TYPE));

        protected B(int count) { super(TYPE, count); }
    }

    /**
     * The classes inherit {@code __class__}
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void test__class__() throws AttributeError, Throwable {

        // Get reference object and type
        PyType object = PyBaseObject.TYPE;
        PyType type = PyType.TYPE;

        // Test the an instance of object and the type
        PyObject o = Callables.call(object);
        assertEquals(object, Abstract.getAttr(o, ID.__class__));
        assertEquals(type, Abstract.getAttr(object, ID.__class__));

        // Test the descriptor in its own right
        // Python: descr = object.__dict__['__class__']
        PyObject descr = object.getDict(false).get(ID.__class__);
        assertEquals(PyGetSetDescr.TYPE, descr.getType());
        // Python: get = getattr(descr, '__get__')
        PyObject get = Abstract.getAttr(descr, ID.__get__);
        // Python: object == get(o)
        assertEquals(object, Callables.call(get, Py.tuple(o), null));

        // A inherits from object
        PyType classA = A.TYPE;
        PyObject a = Callables.call(classA);
        // assertEquals(descr, classA.getDict(false).get(ID.__class__));
        assertEquals(classA, Abstract.getAttr(a, ID.__class__));
        assertEquals(type, Abstract.getAttr(classA, ID.__class__));

        // B inherits from object
        PyType classB = B.TYPE;
        PyObject b = Callables.call(classB);
        // assertEquals(descr, classB.getDict(false).get(ID.__class__));
        assertEquals(classB, Abstract.getAttr(b, ID.__class__));
        assertEquals(type, Abstract.getAttr(classB, ID.__class__));
    }

    /**
     * The classes inherit a {@code Member} attribute
     * ({@code int count}).
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testMemberCount() throws AttributeError, Throwable {

        PyUnicode countID = Py.str("count");
        int beforeInt = 42, afterInt = beforeInt + 1;
        PyObject before = Py.val(beforeInt), after = Py.val(afterInt);

        // A has a member attribute "count"
        A a = new A(beforeInt);
        var count = Abstract.getAttr(a, countID);
        assertEquals(before, count);
        Abstract.setAttr(a, countID, Number.add(count, Py.val(1)));
        assertEquals(after, Abstract.getAttr(a, countID));

        // An attribute descriptor is in the dictionary of A
        PyMemberDescr countD =
                (PyMemberDescr) A.TYPE.getDict().get(countID);
        assertEquals(A.TYPE, countD.objclass);
        assertEquals("count", countD.name);

        // The attribute is not in the dictionary of B
        assertNull(B.TYPE.getDict().get(countID));

        // B has a member attribute "count" by inheritance
        B b = new B(10);
        count = Abstract.getAttr(b, countID);
        assertEquals(Py.val(10), count);
        Abstract.setAttr(b, countID, Number.add(count, Py.val(2)));
        assertEquals(Py.val(12), Abstract.getAttr(b, countID));
    }

    /**
     * The classes inherit a {@code Getter} attribute.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testGetter() throws AttributeError, Throwable {

        PyUnicode gotID = Py.str("got");
        int beforeInt = 42, afterInt = beforeInt + 1;
        PyObject before = Py.val((double) beforeInt),
                after = Py.val((double) afterInt);

        // A has a member attribute "got"
        A a = new A(beforeInt);
        var got = Abstract.getAttr(a, gotID);
        assertEquals(before, got);
        Abstract.setAttr(a, gotID, Number.add(got, Py.val(1.0)));
        assertEquals(after, Abstract.getAttr(a, gotID));

        // An attribute descriptor is in the dictionary of A
        PyGetSetDescr gotD =
                (PyGetSetDescr) A.TYPE.getDict().get(gotID);
        assertEquals(A.TYPE, gotD.objclass);
        assertEquals("got", gotD.name);

        // The attribute is not in the dictionary of B
        assertNull(B.TYPE.getDict().get(gotID));

        // B has a member attribute "got" by inheritance
        B b = new B(10);
        got = Abstract.getAttr(b, gotID);
        assertEquals(Py.val(10.0), got);
        Abstract.setAttr(b, gotID, Number.add(Py.val(2), got));
        assertEquals(Py.val(12.0), Abstract.getAttr(b, gotID));
    }

    /**
     * The classes inherit a special method ({@code SlotWrapperDescr}).
     * {@code A} defines {@code __neg__} so {@code B} should support
     * {@link Number#negative(PyObject)}.
     *
     * @throws ClassCastException if descriptor type is wrong
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testSpecialMethod()
            throws ClassCastException, AttributeError, Throwable {

        // Fundamentals: A.__str__ id object.__str__
        PyType object = PyBaseObject.TYPE;
        PyType typeA = A.TYPE;

        PyWrapperDescr strDescr =
                (PyWrapperDescr) Abstract.getAttr(object, ID.__str__);
        PyWrapperDescr strDescrA =
                (PyWrapperDescr) Abstract.getAttr(typeA, ID.__str__);
        assertSame(object.op_str, typeA.op_str);
        assertSame(strDescr, strDescrA);
        assertEquals(object.op_str, strDescr.wrapped);

        // A implements __neg__
        A a = new A(5);
        assertEquals(Py.val(-5), Number.negative(a));
        PyWrapperDescr negA =
                (PyWrapperDescr) Abstract.getAttr(A.TYPE, ID.__neg__);
        assertSame(A.TYPE, negA.objclass);
        assertEquals(A.TYPE.op_neg, negA.wrapped);
        assertSame(A.TYPE.op_neg, negA.wrapped);
        assertEquals("<slot wrapper '__neg__' of 'A' objects>",
                negA.toString());

        // B inherits __neg__ from A
        B b = new B(6);
        assertEquals(Py.val(-6), Number.negative(b));
        PyWrapperDescr negB =
                (PyWrapperDescr) Abstract.getAttr(B.TYPE, ID.__neg__);
        // Exposing B *did not* separately expose B.__neg__
        assertSame(negA, negB);
        assertSame(A.TYPE, negB.objclass);
        assertSame(A.TYPE.op_neg, negB.wrapped);
        assertSame(A.TYPE.op_neg, B.TYPE.op_neg);
    }

    /**
     * Test binding a special method ({@code SlotWrapperDescr}) to base
     * and subclass objects produces a working method wrapper. {@code A}
     * defines {@code __neg__} so {@code B} should support it should be
     * possible to bind the descriptor to and instance of either.
     *
     * @throws ClassCastException if descriptor type is wrong
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testSpecialMethodBinding()
            throws ClassCastException, AttributeError, Throwable {

        // A implements __neg__
        A a = new A(7);
        PyMethodWrapper nega =
                (PyMethodWrapper) Abstract.getAttr(a, ID.__neg__);
        assertSame(A.TYPE.getDict().get(ID.__neg__), nega.descr);
        assertSame(a, nega.self);
        assertEquals(a, Abstract.getAttr(nega, ID.__self__));
        assertTrue(nega.toString()
                .startsWith("<method-wrapper '__neg__' of A object"));
        assertEquals(Py.val(-7), Callables.call(nega));

        // B inherits __neg__ from A
        B b = new B(8);
        PyMethodWrapper negb =
                (PyMethodWrapper) Abstract.getAttr(b, ID.__neg__);
        assertSame(b, negb.self);
        assertEquals(b, Abstract.getAttr(negb, ID.__self__));
        assertTrue(negb.toString()
                .startsWith("<method-wrapper '__neg__' of B object"));
        assertEquals(Py.val(-8), Callables.call(negb));
    }

}
