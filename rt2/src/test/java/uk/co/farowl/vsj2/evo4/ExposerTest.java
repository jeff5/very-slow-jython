package uk.co.farowl.vsj2.evo4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.DataDescriptor.Flag;
import uk.co.farowl.vsj2.evo4.Exposed.DocString;
import uk.co.farowl.vsj2.evo4.Exposed.Member;
import uk.co.farowl.vsj2.evo4.PyType.Spec;

/**
 * Unit tests for the {@link Exposer} and the {@link Descriptor}s it
 * produces for the several kinds of annotation that may be applied to
 * Java classes implementing Python types.
 */

class ExposerTest {

    /**
     * A test class with exposed members. This doesn't have to be a
     * Python object, but formally implements {@link PyObject} in order
     * to be an acceptable target for get and set operations.
     */
    private static class ObjectWithMembers implements PyObject {

        static PyType TYPE = new PyType("ObjectWithMembers",
                ObjectWithMembers.class);

        @Override
        public PyType getType() { return TYPE; }

        /** Lookup object to support creation of descriptors. */
        private static final MethodHandles.Lookup LOOKUP = MethodHandles
                .lookup().dropLookupMode(MethodHandles.Lookup.PRIVATE);

        @Member
        int i;
        @Member
        @DocString("My test x")
        double x;
        /** String with change of name. */
        @Member("text")
        String t;

        /** Read-only access. */
        @Member(readonly = true)
        int i2;
        /** Read-only access since final. */
        @Member
        final double x2;
        /** Read-only access given first. */
        @Member(readonly = true, value = "text2")
        String t2;

        ObjectWithMembers(double value) {
            x2 = x = value;
            i2 = i = Math.round((float) value);
            t2 = t = String.format("%d", i);
        }

    }

    /**
     * A class that extends the above, with the same Python type. We
     * want to check that what we're doing to reflect on the parent
     * produces descriptors we can apply to a sub-class.
     */
    private static class DerivedWithMembers extends ObjectWithMembers {

        DerivedWithMembers(double value) { super(value); }
    }

    /**
     * Test that the {@link Exposer} creates {@link PyMemberDescr}s for
     * fields annotated as {@link Member}.
     */
    @Test
    void memberConstruct() {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.TYPE, ObjectWithMembers.LOOKUP);
        // Try a few attributes of i
        assertTrue(mds.containsKey("i"));
        PyMemberDescr md = mds.get("i");
        assertNull(md.doc);
        assertTrue(md.flags.isEmpty());
        // Try a few attributes of x
        md = mds.get("x");
        assertEquals("My test x", md.doc);
        assertTrue(md.flags.isEmpty());
        // Now text2
        md = mds.get("text2");
        assertNull(md.doc);
        assertEquals(EnumSet.of(DataDescriptor.Flag.READONLY),
                md.flags);
    }

    /**
     * Test that we can get values via the {@link PyMemberDescr}s the
     * {@link Exposer} creates for fields annotated as {@link Member}.
     */
    @Test
    void memberGetValues() {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.TYPE, ObjectWithMembers.LOOKUP);
        ObjectWithMembers o = new ObjectWithMembers(42.0);
        ObjectWithMembers p = new ObjectWithMembers(-1.0);

        // Same PyMemberDescr, different objects
        PyMemberDescr md_i = mds.get("i");
        assertEquals(Py.val(42), PyMemberDescr.__get__(md_i, o, null));
        assertEquals(Py.val(-1), PyMemberDescr.__get__(md_i, p, null));

        PyMemberDescr md_x = mds.get("x");
        assertEquals(Py.val(42.0),
                PyMemberDescr.__get__(md_x, o, null));

        PyMemberDescr md_t = mds.get("text");
        assertEquals(Py.str("-1"),
                PyMemberDescr.__get__(md_t, p, null));

        // Read-only cases work too
        PyMemberDescr md_i2 = mds.get("i2");
        assertEquals(Py.val(42), PyMemberDescr.__get__(md_i2, o, null));
        assertEquals(Py.val(-1), PyMemberDescr.__get__(md_i2, p, null));

        PyMemberDescr md_x2 = mds.get("x2");
        assertEquals(Py.val(42.0),
                PyMemberDescr.__get__(md_x2, o, null));

        PyMemberDescr md_t2 = mds.get("text2");
        assertEquals(Py.str("-1"),
                PyMemberDescr.__get__(md_t2, p, null));
    }

    /**
     * Test that we can get values via the {@link PyMemberDescr}s the
     * {@link Exposer} creates for fields annotated as {@link Member}.
     *
     * @throws Throwable
     * @throws TypeError
     */
    @Test
    void memberSetValues() throws TypeError, Throwable {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.TYPE, ObjectWithMembers.LOOKUP);
        final ObjectWithMembers o = new ObjectWithMembers(42.0);
        final ObjectWithMembers p = new ObjectWithMembers(-1.0);

        int i = 43, j = 44;
        final PyObject oi = Py.val(i);
        final PyObject oj = Py.val(j);
        double x = 9.0;
        final PyObject ox = Py.val(x);
        String t = "Gumby";
        final PyObject ot = Py.str(t);

        // Same PyMemberDescr, different objects
        PyMemberDescr md_i = mds.get("i");
        PyMemberDescr.__set__(md_i, o, oi);
        PyMemberDescr.__set__(md_i, p, oj);
        assertEquals(i, o.i);
        assertEquals(j, p.i);

        PyMemberDescr md_x = mds.get("x");
        PyMemberDescr.__set__(md_x, o, ox);
        assertEquals(x, o.x, 1e-6);

        PyMemberDescr md_t = mds.get("text");
        PyMemberDescr.__set__(md_t, p, ot);
        assertEquals(t, p.t);

        // Read-only cases throw
        final PyMemberDescr md_i2 = mds.get("i2");
        assertThrows(AttributeError.class,
                () -> PyMemberDescr.__set__(md_i2, o, oi));
        assertThrows(AttributeError.class,
                () -> PyMemberDescr.__set__(md_i2, p, oj));

        final PyMemberDescr md_x2 = mds.get("x2");
        assertThrows(AttributeError.class,
                () -> PyMemberDescr.__set__(md_x2, o, ox));

        final PyMemberDescr md_text2 = mds.get("text2");
        assertThrows(AttributeError.class,
                () -> PyMemberDescr.__set__(md_text2, p, ot));
    }

    /**
     * Test that we can get and set values in a Java sub-class via the
     * {@link MemberDef}s the {@link Exposer} creates.
     *
     * @throws Throwable
     * @throws TypeError
     */
    @Test
    void memberInDerived() throws TypeError, Throwable {
        // Note we make the table for the super-class
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.TYPE, ObjectWithMembers.LOOKUP);
        // But the test object is the sub-class
        final DerivedWithMembers o = new DerivedWithMembers(42.0);

        int i = 45;
        final PyObject oi = Py.val(i);
        double x = 9.0;
        final PyObject ox = Py.val(x);
        String t = "Gumby";
        final PyObject ot = Py.str(t);

        // Set then get
        PyMemberDescr md_i = mds.get("i");
        PyMemberDescr.__set__(md_i, o, oi);
        assertEquals(oi, PyMemberDescr.__get__(md_i, o, null));

        PyMemberDescr md_x = mds.get("x");
        PyMemberDescr.__set__(md_x, o, ox);
        assertEquals(x, o.x, 1e-6);

        PyMemberDescr md_t = mds.get("text");
        PyMemberDescr.__set__(md_t, o, ot);
        assertEquals(t, o.t);
        assertEquals(ot, PyMemberDescr.__get__(md_t, o, null));

        // Read-only cases throw
        final PyMemberDescr md_i2 = mds.get("i2");
        assertThrows(AttributeError.class,
                () -> PyMemberDescr.__set__(md_i2, o, oi));
    }

    /**
     * Test that attempts to apply a descriptor to the wrong type
     * produce a {@code TypeError}.
     */
    @Test
    void memberTypeError() {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.TYPE, ObjectWithMembers.LOOKUP);
        // But the test object is something incompatible
        final PyObject o = new PyFloat(42.0);

        PyMemberDescr md_x = mds.get("x");
        assertThrows(TypeError.class,
                () -> PyMemberDescr.__get__(md_x, o, null));

        double x = 9.0;
        final PyObject ox = Py.val(x);
        assertThrows(TypeError.class,
                () -> PyMemberDescr.__set__(md_x, o, ox));

        PyMemberDescr md_x2 = mds.get("x2");
        assertThrows(TypeError.class,
                () -> PyMemberDescr.__get__(md_x2, o, null));

        assertThrows(TypeError.class,
                () -> PyMemberDescr.__set__(md_x2, o, ox));
    }

    /**
     * A test {@link PyObject} with exposed members that {@link PyType}
     * should pick up.
     */
    private static class PyObjectWithMembers extends AbstractPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new Spec("PyObjectWithMembers",
                        PyObjectWithMembers.class));
        @Member
        int i;
        @Member
        @DocString("My test x")
        double x;
        /** String with change of name. */
        @Member("text")
        String t;

        /** Read-only access. */
        @Member(readonly = true)
        int i2;
        /** Read-only access since final. */
        @Member
        final double x2;
        /** Read-only access given first. */
        @Member(readonly = true, value = "text2")
        String t2;

        PyObjectWithMembers(double value) {
            super(TYPE);
            x2 = x = value;
            i2 = i = Math.round((float) value);
            t2 = t = String.format("%d", i);
        }
    }

}
