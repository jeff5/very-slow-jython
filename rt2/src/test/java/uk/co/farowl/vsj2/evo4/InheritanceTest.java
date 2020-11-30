package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.Exposed.Member;
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

        protected A(PyType type, int count) {
            super(type);
            this.count = count;
        }

        protected A(int count) { this(TYPE, count); }

        PyObject __neg__() { return Py.val(-count); }
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
        PyObject descr = object.getDict(false).get(ID.__class__);
        assertEquals(PyGetSetDescr.TYPE, descr.getType());
        // * We need slot-wrapper to invoke its __get__.
        // * PyObject get = Abstract.getAttr(descr, ID.__get__);
        // * assertEquals(object, Callables.call(get, Py.tuple(o),
        // null));

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
     * The classes inherit a {@code Member}.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testMember() throws AttributeError, Throwable {

        PyUnicode IDcount = Py.str("count");

        // A inherits attribute access from object
        A a = new A(0);
        var count = Abstract.getAttr(a, IDcount);
        assertEquals(Py.val(0), count);
        Abstract.setAttr(a, IDcount, Number.add(count, Py.val(1)));
        assertEquals(Py.val(1), Abstract.getAttr(a, IDcount));

        // B inherits from A
        B b = new B(10);
        count = Abstract.getAttr(b, IDcount);
        assertEquals(Py.val(10), count);
        Abstract.setAttr(b, IDcount, Number.add(count, Py.val(2)));
        assertEquals(Py.val(12), Abstract.getAttr(b, IDcount));
    }

    /**
     * The classes inherit a special method ({@code SlotWrapperDescr}).
     * {@code A} defines {@code __neg__} so {@code B} should support
     * {@link Number#negative(PyObject)}.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void testSpecialMethod() throws AttributeError, Throwable {

        // A implements __neg__
        A a = new A(5);
        assertEquals(Py.val(-5), Number.negative(a));
        // * We need slot-wrapper to find a descriptor here.
        // * var negA = Abstract.getAttr(A.TYPE, ID.__neg__);

        // B inherits __neg__ from A
        B b = new B(6);
        assertEquals(Py.val(-6), Number.negative(b));
        // * var negB = Abstract.getAttr(B.TYPE, ID.__neg__);
        // * assertEquals(negA, negB);
    }

}
