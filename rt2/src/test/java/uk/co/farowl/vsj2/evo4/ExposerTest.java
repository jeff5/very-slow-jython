package uk.co.farowl.vsj2.evo4;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.Exposed.DocString;
import uk.co.farowl.vsj2.evo4.Exposed.Member;
import uk.co.farowl.vsj2.evo4.PyType.Spec;

/**
 * Unit tests for descriptor objects {@link Descriptor}.
 */

class ExposerTest {

    /**
     * A test class with exposed members. This doesn't have to be a
     * Python object, but formally implements {@link PyObject} in order
     * to be an acceptable target for get and set operations.
     */
    private static class ObjectWithMembers implements PyObject {

        @Override
        public PyType getType() { return PyType.OBJECT_TYPE; }

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
        /** Read-only access with array initialiser. */
        @Member(readonly = true)
        double x2;
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
     * Test that the {@link Exposer} creates {@link MemberDef}s for
     * fields annotated as {@link Member}.
     */
    @Test
    void memberConstructDefs() {
        Map<String, MemberDef> mds = Exposer.memberDefs(
                ObjectWithMembers.class, ObjectWithMembers.LOOKUP);
        // Try a few attributes of i
        assertTrue(mds.containsKey("i"));
        MemberDef md = mds.get("i");
        assertEquals("", md.doc);
        assertEquals(EnumSet.noneOf(MemberDef.Flag.class), md.flags);
        // Try a few attributes of x
        md = mds.get("x");
        assertEquals("My test x", md.doc);
        assertEquals(EnumSet.noneOf(MemberDef.Flag.class), md.flags);
        // Now text2
        md = mds.get("text2");
        assertEquals("", md.doc);
        assertEquals(EnumSet.of(MemberDef.Flag.READONLY), md.flags);
    }

    /**
     * Test that we can get values via the {@link MemberDef}s the
     * {@link Exposer} creates for fields annotated as {@link Member}.
     */
    @Test
    void memberGetValues() {
        Map<String, MemberDef> mds = Exposer.memberDefs(
                ObjectWithMembers.class, ObjectWithMembers.LOOKUP);
        ObjectWithMembers o = new ObjectWithMembers(42.0);
        ObjectWithMembers p = new ObjectWithMembers(-1.0);

        // Same MemberDef, different objects
        MemberDef md_i = mds.get("i");
        assertEquals(Py.val(42), md_i.get(o));
        assertEquals(Py.val(-1), md_i.get(p));
        assertEquals(Py.val(42.0), mds.get("x").get(o));
        assertEquals(Py.str("-1"), mds.get("text").get(p));

        // Read-only cases work too
        MemberDef md_i2 = mds.get("i2");
        assertEquals(Py.val(42), md_i2.get(o));
        assertEquals(Py.val(-1), md_i2.get(p));
        assertEquals(Py.val(42.0), mds.get("x").get(o));
        assertEquals(Py.str("-1"), mds.get("text").get(p));
    }

    /**
     * Test that we can get values via the {@link MemberDef}s the
     * {@link Exposer} creates for fields annotated as {@link Member}.
     *
     * @throws Throwable
     * @throws TypeError
     */
    @Test
    void memberSetValues() throws TypeError, Throwable {
        Map<String, MemberDef> mds = Exposer.memberDefs(
                ObjectWithMembers.class, ObjectWithMembers.LOOKUP);
        final ObjectWithMembers o = new ObjectWithMembers(42.0);
        final ObjectWithMembers p = new ObjectWithMembers(-1.0);

        int i = 43, j = 44;
        final PyObject oi = Py.val(i);
        final PyObject oj = Py.val(j);
        double x = 9.0;
        final PyObject ox = Py.val(x);
        String t = "Gumby";
        final PyObject ot = Py.str(t);

        // Same MemberDef, different objects
        MemberDef md_i = mds.get("i");
        md_i.set(o, oi);
        md_i.set(p, oj);
        assertEquals(i, o.i);
        assertEquals(j, p.i);

        MemberDef md_x = mds.get("x");
        md_x.set(o, ox);
        assertEquals(x, o.x, 1e-6);

        mds.get("text").set(p, ot);
        assertEquals(t, p.t);

        // Read-only cases throw
        final MemberDef md_i2 = mds.get("i2");
        assertThrows(AttributeError.class, () -> md_i2.set(o, oi));
        assertThrows(AttributeError.class, () -> md_i2.set(p, oj));

        final MemberDef md_x2 = mds.get("x2");
        assertThrows(AttributeError.class, () -> md_x2.set(o, ox));

        final MemberDef md_text2 = mds.get("text2");
        assertThrows(AttributeError.class, () -> md_text2.set(p, ot));
    }

    private static class DerivedWithMembers extends ObjectWithMembers {

        DerivedWithMembers(double value) { super(value); }
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
        Map<String, MemberDef> mds = Exposer.memberDefs(
                ObjectWithMembers.class, ObjectWithMembers.LOOKUP);
        // But the test object is the sub-class
        final DerivedWithMembers o = new DerivedWithMembers(42.0);

        int i = 45;
        final PyObject oi = Py.val(i);
        double x = 9.0;
        final PyObject ox = Py.val(x);
        String t = "Gumby";
        final PyObject ot = Py.str(t);

        // Set then get
        MemberDef md_i = mds.get("i");
        md_i.set(o, oi);
        assertEquals(oi, md_i.get(o));

        MemberDef md_x = mds.get("x");
        md_x.set(o, ox);
        assertEquals(x, o.x, 1e-6);

        mds.get("text").set(o, ot);
        assertEquals(t, o.t);

        MemberDef md_t = mds.get("text");
        md_t.set(o, ot);
        assertEquals(ot, md_t.get(o));

        // Read-only cases throw
        final MemberDef md_i2 = mds.get("i2");
        assertThrows(AttributeError.class, () -> md_i2.set(o, oi));
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
        /** Read-only access with array initialiser. */
        @Member(readonly = true)
        double x2;
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
