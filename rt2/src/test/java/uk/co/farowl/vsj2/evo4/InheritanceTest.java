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

        protected A(PyType type) { super(type); }

        protected A() { this(TYPE); }
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

        protected B() { super(TYPE); }
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

        // A inherits from object
        A a = new A();
        var count = Abstract.getAttr(a, IDcount);
        assertEquals(Py.val(0), count);
        Abstract.setAttr(a, IDcount, Number.add(count, Py.val(1)));
        assertEquals(Py.val(1), Abstract.getAttr(a, IDcount));

        // B inherits from A
        B b = new B();
        count = Abstract.getAttr(b, IDcount);
        assertEquals(Py.val(0), count);
        Abstract.setAttr(b, IDcount, Number.add(count, Py.val(2)));
        assertEquals(Py.val(2), Abstract.getAttr(b, IDcount));

    }

}
