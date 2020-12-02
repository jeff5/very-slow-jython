package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.EnumSet;
import java.util.Map;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj2.evo4.Exposed.DocString;
import uk.co.farowl.vsj2.evo4.Exposed.Member;

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

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();

        static PyType TYPE =
                PyType.fromSpec(new PyType.Spec("ObjectWithMembers",
                        ObjectWithMembers.class, LOOKUP));

        @Override
        public PyType getType() { return TYPE; }

        @Member
        int i;
        @Member
        @DocString("My test x")
        double x;
        /** String with change of name. */
        @Member("text")
        String t;
        /** Can be properly deleted without popping up as None */
        @Member(optional = true)
        String s;

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
            t2 = t = s = String.format("%d", i);
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
        // Repeat roughly what PyType.fromSpec already did.
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
                ObjectWithMembers.TYPE);
        // Try a few attributes of i
        assertTrue(mds.containsKey("i"));
        PyMemberDescr md = mds.get("i");
        assertEquals("<member 'i' of 'ObjectWithMembers' objects>",
                md.toString());
        assertNull(md.doc);
        assertTrue(md.flags.isEmpty());
        // Try a few attributes of x
        md = mds.get("x");
        assertEquals("My test x", md.doc);
        assertTrue(md.flags.isEmpty());
        // Now text2
        md = mds.get("text2");
        assertNull(md.doc);
        assertEquals(EnumSet.of(PyMemberDescr.Flag.READONLY), md.flags);
    }

    /**
     * Test that we can get values via the {@link PyMemberDescr}s the
     * {@link Exposer} creates for fields annotated as {@link Member}.
     */
    @Test
    void memberGetValues() {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
                ObjectWithMembers.TYPE);
        ObjectWithMembers o = new ObjectWithMembers(42.0);
        ObjectWithMembers p = new ObjectWithMembers(-1.0);

        // Same PyMemberDescr, different objects
        PyMemberDescr md_i = mds.get("i");
        assertEquals(Py.val(42), md_i.__get__(o, null));
        assertEquals(Py.val(-1), md_i.__get__(p, null));

        PyMemberDescr md_x = mds.get("x");
        assertEquals(Py.val(42.0), md_x.__get__(o, null));

        PyMemberDescr md_t = mds.get("text");
        assertEquals(Py.str("-1"), md_t.__get__(p, null));

        PyMemberDescr md_s = mds.get("s");
        assertEquals(Py.str("42"), md_s.__get__(o, null));

        // Read-only cases work too
        PyMemberDescr md_i2 = mds.get("i2");
        assertEquals(Py.val(42), md_i2.__get__(o, null));
        assertEquals(Py.val(-1), md_i2.__get__(p, null));

        PyMemberDescr md_x2 = mds.get("x2");
        assertEquals(Py.val(42.0), md_x2.__get__(o, null));

        PyMemberDescr md_t2 = mds.get("text2");
        assertEquals(Py.str("-1"), md_t2.__get__(p, null));
    }

    /**
     * Test that we can set values via the {@link PyMemberDescr}s the
     * {@link Exposer} creates for fields annotated as {@link Member},
     * and receive an {@link AttributeError} when read-only.
     *
     * @throws Throwable unexpectedly
     * @throws TypeError unexpectedly
     */
    @Test
    void memberSetValues() throws TypeError, Throwable {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
                ObjectWithMembers.TYPE);
        final ObjectWithMembers o = new ObjectWithMembers(42.0);
        final ObjectWithMembers p = new ObjectWithMembers(-1.0);

        int i = 43, j = 44;
        final PyObject oi = Py.val(i);
        final PyObject oj = Py.val(j);
        double x = 9.0;
        final PyObject ox = Py.val(x);
        String t = "Gumby";
        final PyObject ot = Py.str(t);

        // Same descriptor applicable to different objects
        PyMemberDescr md_i = mds.get("i");
        md_i.__set__(o, oi);
        md_i.__set__(p, oj);
        assertEquals(i, o.i);
        assertEquals(j, p.i);

        // Set a double
        PyMemberDescr md_x = mds.get("x");
        md_x.__set__(o, ox);
        assertEquals(x, o.x, 1e-6);

        // Set a String
        PyMemberDescr md_t = mds.get("text");
        md_t.__set__(p, ot);
        assertEquals(t, p.t);

        // It is a TypeError to set the wrong kind of value
        assertThrows(TypeError.class, () -> md_i.__set__(o, ot));
        assertThrows(TypeError.class, () -> md_t.__set__(o, oi));

        // It is an AttributeError to set a read-only attribute
        final PyMemberDescr md_i2 = mds.get("i2");
        assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
        assertThrows(AttributeError.class, () -> md_i2.__set__(p, oj));

        final PyMemberDescr md_x2 = mds.get("x2");
        assertThrows(AttributeError.class, () -> md_x2.__set__(o, ox));

        final PyMemberDescr md_text2 = mds.get("text2");
        assertThrows(AttributeError.class,
                () -> md_text2.__set__(p, ot));
    }

    /**
     * Test that we can delete values via the {@link PyMemberDescr}s the
     * {@link Exposer} creates for fields annotated as {@link Member},
     * and receive an {@link AttributeError} when read-only.
     *
     * @throws Throwable unexpectedly
     * @throws TypeError unexpectedly
     */
    @Test
    void memberDeleteValues() throws TypeError, Throwable {
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
                ObjectWithMembers.TYPE);
        final ObjectWithMembers o = new ObjectWithMembers(42.0);
        final PyType T = ObjectWithMembers.TYPE;

        String s = "P.J.", t = "Gumby";
        final PyObject os = Py.str(s);
        final PyObject ot = Py.str(t);

        /*
         * The text (o.t) attribute is a writable, non-optional String
         */
        PyMemberDescr md_t = mds.get("text");
        assertEquals("42", o.t);
        // Deleting it makes it null internally, None externally
        md_t.__delete__(o);
        assertEquals(null, o.t);
        assertEquals(Py.None, md_t.__get__(o, T));
        // We can delete it again (no error) and set it
        md_t.__delete__(o);
        md_t.__set__(o, ot);
        assertEquals(t, o.t);

        /*
         * The s attribute is a writable, optional String
         */
        PyMemberDescr md_s = mds.get("s");
        md_s.__set__(o, os);
        assertEquals(s, o.s);
        assertEquals(os, md_s.__get__(o, T));
        // Deleting it makes it null internally, vanish externally
        md_s.__delete__(o);
        assertEquals(null, o.s);
        assertThrows(AttributeError.class, () -> md_s.__get__(o, T));
        // Deleting it again is an error
        assertThrows(AttributeError.class, () -> md_s.__delete__(o));
        // But we can set it
        md_s.__set__(o, ot);
        assertEquals(t, o.s);

        /*
         * i, x are primitives, so cannot be deleted.
         */
        // Deleting a primitive is a TypeError
        PyMemberDescr md_i = mds.get("i");
        assertThrows(TypeError.class, () -> md_i.__delete__(o));
        final PyMemberDescr md_x = mds.get("x");
        assertThrows(TypeError.class, () -> md_x.__delete__(o));

        /*
         * i2, x2, text2 (o.t) are read-only, so cannot be deleted.
         */
        // Deleting a read-only is an AttributeError
        final PyMemberDescr md_i2 = mds.get("i2");
        assertThrows(AttributeError.class, () -> md_i2.__delete__(o));
        final PyMemberDescr md_x2 = mds.get("x2");
        assertThrows(AttributeError.class, () -> md_x2.__delete__(o));
        final PyMemberDescr md_text2 = mds.get("text2");
        assertThrows(AttributeError.class,
                () -> md_text2.__delete__(o));
    }

    /**
     * Test that we can get and set values in a Java sub-class via the
     * {@link MemberDef}s the {@link Exposer} creates.
     *
     * @throws Throwable unexpectedly
     * @throws TypeError unexpectedly
     */
    @Test
    void memberInDerived() throws TypeError, Throwable {
        // Note we make the table for the super-class
        Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
                ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
                ObjectWithMembers.TYPE);
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
        md_i.__set__(o, oi);
        assertEquals(oi, md_i.__get__(o, null));

        PyMemberDescr md_x = mds.get("x");
        md_x.__set__(o, ox);
        assertEquals(x, o.x, 1e-6);

        PyMemberDescr md_t = mds.get("text");
        md_t.__set__(o, ot);
        assertEquals(t, o.t);
        assertEquals(ot, md_t.__get__(o, null));

        // Read-only cases throw
        final PyMemberDescr md_i2 = mds.get("i2");
        assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
    }

    private static class PyObjectWithSpecial implements PyObject {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("ObjectWithSpecialMethods",
                        PyObjectWithSpecial.class, LOOKUP));
        int value;

        public PyObjectWithSpecial(int value) { this.value = value; }

        PyObject __neg__() { return Py.val(-value); }

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Test that we get working descriptors of type
     * {@link PyWrapperDescr}s the {@link Exposer} creates for methods
     * with special names.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    @Test
    void wrapperConstruct() throws AttributeError, Throwable {
        // Roughly what PyType.fromSpec does in real life.
        Map<String, PyWrapperDescr> wds = Exposer.wrapperDescrs(
                PyObjectWithSpecial.LOOKUP, PyObjectWithSpecial.class,
                PyObjectWithSpecial.TYPE);

        // We defined this special method
        PyWrapperDescr neg = wds.get("__neg__");

        assertEquals("__neg__", neg.name);
        assertEquals(PyObjectWithSpecial.TYPE, neg.objclass);
        assertEquals(
                "<slot wrapper '__neg__' of 'ObjectWithSpecialMethods' objects>",
                neg.toString());
    }

}
