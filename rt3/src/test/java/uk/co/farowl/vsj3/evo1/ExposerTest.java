package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;

import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;

/**
 * Unit tests for the {@link Exposer} and the {@link Descriptor}s it
 * produces for the several kinds of annotation that may be applied to
 * Java classes implementing Python types.
 */
class ExposerTest {

// /**
// * A test class with exposed members. This doesn't have to be a
// * Python object, but formally implements {@link CraftedType} in
// * order to be an acceptable target for get and set operations.
// */
// private static class ObjectWithMembers implements CraftedType {
//
// /** Lookup object to support creation of descriptors. */
// private static final Lookup LOOKUP = MethodHandles.lookup();
//
// static PyType TYPE =
// PyType.fromSpec(new PyType.Spec("ObjectWithMembers",
// ObjectWithMembers.class, LOOKUP));
//
// @Override
// public PyType getType() { return TYPE; }
//
// @Member
// int i;
// @Member
// @DocString("My test x")
// double x;
// /** String with change of name. */
// @Member("text")
// String t;
// /** String can be properly deleted without popping up as None */
// @Member(optional = true)
// String s;
// /** {@code Object} member */
// @Member
// Object obj;
// /** {@code PyUnicode} member: care needed on set. */
// @Member
// PyUnicode strhex;
//
// /** Read-only access. */
// @Member(readonly = true)
// int i2;
// /** Read-only access since final. */
// @Member
// final double x2;
// /** Read-only access given first. */
// @Member(readonly = true, value = "text2")
// String t2;
//
// ObjectWithMembers(double value) {
// x2 = x = value;
// i2 = i = Math.round((float) value);
// t2 = t = s = String.format("%d", i);
// obj = new PyUnicode(Integer.toString(i));
// strhex = new PyUnicode(Integer.toString(i, 16));
// }
// }
//
// /**
// * A class that extends the above, with the same Python type. We
// * want to check that what we're doing to reflect on the parent
// * produces descriptors we can apply to a sub-class.
// */
// private static class DerivedWithMembers extends ObjectWithMembers {
//
// DerivedWithMembers(double value) {
// super(value);
// }
// }
//
// /**
// * Test that the {@link Exposer} creates {@link PyMemberDescr}s for
// * fields annotated as {@link Member}.
// */
// // @Test
// void memberConstruct() {
// // Repeat roughly what PyType.fromSpec already did.
// Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
// ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
// ObjectWithMembers.TYPE);
// // Try a few attributes of i
// assertTrue(mds.containsKey("i"));
// PyMemberDescr md = mds.get("i");
// assertEquals("<member 'i' of 'ObjectWithMembers' objects>",
// md.toString());
// assertNull(md.doc);
// assertTrue(md.flags.isEmpty());
// // Try a few attributes of x
// md = mds.get("x");
// assertEquals("My test x", md.doc);
// assertTrue(md.flags.isEmpty());
// // Now text2
// md = mds.get("text2");
// assertNull(md.doc);
// assertEquals(EnumSet.of(PyMemberDescr.Flag.READONLY), md.flags);
// }
//
// /**
// * Test that we can get values via the {@link PyMemberDescr}s the
// * {@link Exposer} creates for fields annotated as {@link Member}.
// */
// // @Test
// void memberGetValues() {
// Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
// ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
// ObjectWithMembers.TYPE);
// ObjectWithMembers o = new ObjectWithMembers(42.0);
// ObjectWithMembers p = new ObjectWithMembers(-1.0);
//
// // Same PyMemberDescr, different objects
// PyMemberDescr md_i = mds.get("i");
// assertEquals(Py.val(42), md_i.__get__(o, null));
// assertEquals(Py.val(-1), md_i.__get__(p, null));
//
// PyMemberDescr md_x = mds.get("x");
// assertEquals(Py.val(42.0), md_x.__get__(o, null));
//
// PyMemberDescr md_t = mds.get("text");
// assertEquals("-1", md_t.__get__(p, null));
//
// PyMemberDescr md_s = mds.get("s");
// assertEquals("42", md_s.__get__(o, null));
//
// PyMemberDescr md_obj = mds.get("obj"); // Object
// assertEquals("42", md_obj.__get__(o, null));
//
// PyMemberDescr md_strhex = mds.get("strhex"); // Object
// assertEquals("2a", md_strhex.__get__(o, null));
//
// // Read-only cases work too
// PyMemberDescr md_i2 = mds.get("i2");
// assertEquals(Py.val(42), md_i2.__get__(o, null));
// assertEquals(Py.val(-1), md_i2.__get__(p, null));
//
// PyMemberDescr md_x2 = mds.get("x2");
// assertEquals(Py.val(42.0), md_x2.__get__(o, null));
//
// PyMemberDescr md_t2 = mds.get("text2");
// assertEquals("-1", md_t2.__get__(p, null));
// }
//
// /**
// * Test that we can set values via the {@link PyMemberDescr}s the
// * {@link Exposer} creates for fields annotated as {@link Member},
// * and receive an {@link AttributeError} when read-only.
// *
// * @throws Throwable unexpectedly
// * @throws TypeError unexpectedly
// */
// // @Test
// void memberSetValues() throws TypeError, Throwable {
// Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
// ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
// ObjectWithMembers.TYPE);
// final ObjectWithMembers o = new ObjectWithMembers(42.0);
// final ObjectWithMembers p = new ObjectWithMembers(-1.0);
//
// int i = 43, j = 44;
// final Object oi = Py.val(i);
// final Object oj = Py.val(j);
// double x = 9.0;
// final Object ox = Py.val(x);
// String t = "Gumby";
// final Object ot = Py.str(t);
//
// // Same descriptor applicable to different objects
// PyMemberDescr md_i = mds.get("i");
// md_i.__set__(o, oi);
// md_i.__set__(p, oj);
// assertEquals(i, o.i);
// assertEquals(j, p.i);
//
// // Set a double
// PyMemberDescr md_x = mds.get("x");
// md_x.__set__(o, ox);
// assertEquals(x, o.x, 1e-6);
//
// // Set a String
// PyMemberDescr md_t = mds.get("text");
// md_t.__set__(p, ot);
// assertEquals(t, p.t);
//
// PyMemberDescr md_obj = mds.get("obj"); // Object
// md_obj.__set__(p, ox);
// assertSame(ox, p.obj);
//
// PyMemberDescr md_strhex = mds.get("strhex"); // PyUnicode
// md_strhex.__set__(p, ot);
// assertSame(ot, p.strhex);
//
// // It is a TypeError to set the wrong kind of value
// assertThrows(TypeError.class, () -> md_i.__set__(o, ot));
// assertThrows(TypeError.class, () -> md_t.__set__(o, oi));
// assertThrows(TypeError.class, () -> md_strhex.__set__(o, ox));
//
// // It is an AttributeError to set a read-only attribute
// final PyMemberDescr md_i2 = mds.get("i2");
// assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
// assertThrows(AttributeError.class, () -> md_i2.__set__(p, oj));
//
// final PyMemberDescr md_x2 = mds.get("x2");
// assertThrows(AttributeError.class, () -> md_x2.__set__(o, ox));
//
// final PyMemberDescr md_text2 = mds.get("text2");
// assertThrows(AttributeError.class,
// () -> md_text2.__set__(p, ot));
// }
//
// /**
// * Test that we can delete values via the {@link PyMemberDescr}s the
// * {@link Exposer} creates for fields annotated as {@link Member},
// * and receive an {@link AttributeError} when read-only.
// *
// * @throws Throwable unexpectedly
// * @throws TypeError unexpectedly
// */
// // @Test
// void memberDeleteValues() throws TypeError, Throwable {
// Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
// ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
// ObjectWithMembers.TYPE);
// final ObjectWithMembers o = new ObjectWithMembers(42.0);
// final PyType T = ObjectWithMembers.TYPE;
//
// String s = "P.J.", t = "Gumby";
// final Object os = Py.str(s);
// final Object ot = Py.str(t);
//
// /*
// * The text (o.t) attribute is a writable, non-optional String
// */
// PyMemberDescr md_t = mds.get("text");
// assertEquals("42", o.t);
// // Deleting it makes it null internally, None externally
// md_t.__delete__(o);
// assertEquals(null, o.t);
// assertEquals(Py.None, md_t.__get__(o, T));
// // We can delete it again (no error) and set it
// md_t.__delete__(o);
// md_t.__set__(o, ot);
// assertEquals(t, o.t);
//
// /*
// * The s attribute is a writable, optional String
// */
// PyMemberDescr md_s = mds.get("s");
// md_s.__set__(o, os);
// assertEquals(s, o.s);
// assertEquals(os, md_s.__get__(o, T));
// // Deleting it makes it null internally, vanish externally
// md_s.__delete__(o);
// assertEquals(null, o.s);
// assertThrows(AttributeError.class, () -> md_s.__get__(o, T));
// // Deleting it again is an error
// assertThrows(AttributeError.class, () -> md_s.__delete__(o));
// // But we can set it
// md_s.__set__(o, ot);
// assertEquals(t, o.s);
//
// /*
// * i, x are primitives, so cannot be deleted.
// */
// // Deleting a primitive is a TypeError
// PyMemberDescr md_i = mds.get("i");
// assertThrows(TypeError.class, () -> md_i.__delete__(o));
// final PyMemberDescr md_x = mds.get("x");
// assertThrows(TypeError.class, () -> md_x.__delete__(o));
//
// /*
// * i2, x2, text2 (o.t) are read-only, so cannot be deleted.
// */
// // Deleting a read-only is an AttributeError
// final PyMemberDescr md_i2 = mds.get("i2");
// assertThrows(AttributeError.class, () -> md_i2.__delete__(o));
// final PyMemberDescr md_x2 = mds.get("x2");
// assertThrows(AttributeError.class, () -> md_x2.__delete__(o));
// final PyMemberDescr md_text2 = mds.get("text2");
// assertThrows(AttributeError.class,
// () -> md_text2.__delete__(o));
// }
//
// /**
// * Test that we can get and set values in a Java sub-class via the
// * {@link MemberDef}s the {@link Exposer} creates.
// *
// * @throws Throwable unexpectedly
// * @throws TypeError unexpectedly
// */
// // @Test
// void memberInDerived() throws TypeError, Throwable {
// // Note we make the table for the super-class
// Map<String, PyMemberDescr> mds = Exposer.memberDescrs(
// ObjectWithMembers.LOOKUP, ObjectWithMembers.class,
// ObjectWithMembers.TYPE);
// // But the test object is the sub-class
// final DerivedWithMembers o = new DerivedWithMembers(42.0);
//
// int i = 45;
// final Object oi = Py.val(i);
// double x = 9.0;
// final Object ox = Py.val(x);
// String t = "Gumby";
// final Object ot = Py.str(t);
//
// // Set then get
// PyMemberDescr md_i = mds.get("i");
// md_i.__set__(o, oi);
// assertEquals(oi, md_i.__get__(o, null));
//
// PyMemberDescr md_x = mds.get("x");
// md_x.__set__(o, ox);
// assertEquals(x, o.x, 1e-6);
//
// PyMemberDescr md_t = mds.get("text");
// md_t.__set__(o, ot);
// assertEquals(t, o.t);
// assertEquals(ot, md_t.__get__(o, null));
//
// // Read-only cases throw
// final PyMemberDescr md_i2 = mds.get("i2");
// assertThrows(AttributeError.class, () -> md_i2.__set__(o, oi));
// }

    /**
     * Model canonical implementation to explore exposure of a special
     * method.
     */
    private static class ObjectWithSpMeth implements CraftedType {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("ObjectWithSpecialMethods", LOOKUP)
                        .accept(AcceptedSpecial.class));
        int value;

        @SuppressWarnings("unused")
        public ObjectWithSpMeth(int value) {
            this.value = value;
        }

        @SuppressWarnings("unused")
        Object __neg__() {
            return new ObjectWithSpMeth(-value);
        }

        @SuppressWarnings("unused")
        static Object __neg__(AcceptedSpecial v) {
            return new AcceptedSpecial(-v.value);
        }

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Model accepted implementation to explore exposure of a special
     * method.
     */
    private static class AcceptedSpecial {

        int value;

        public AcceptedSpecial(int value) {
            this.value = value;
        }
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

        PyType type = ObjectWithSpMeth.TYPE;

        // Inherited __repr__
        PyWrapperDescr repr = (PyWrapperDescr) type.lookup("__repr__");
        assertNotNull(repr);

        // We defined this special method
        PyWrapperDescr.Multiple neg =
                (PyWrapperDescr.Multiple) type.lookup("__neg__");
        assertNotNull(neg);

        assertEquals("__neg__", neg.name);
        assertEquals(ObjectWithSpMeth.TYPE, neg.objclass);
        assertEquals(
                "<slot wrapper '__neg__' of 'ObjectWithSpecialMethods' objects>",
                neg.toString());

        // This target should be a method for PyObjectWithSpecial
        MethodHandle neg0 = neg.wrapped[0];
        assertSame(Object.class, neg0.type().parameterType(0));

        // This target should be a method for AcceptedSpecial
        MethodHandle neg1 = neg.wrapped[1];
        assertSame(Object.class, neg1.type().parameterType(0));
    }

    private static class PyObjectWithMethods implements CraftedType {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("PyObjectWithMethods", LOOKUP));
        String value;

        public PyObjectWithMethods(String value) {
            this.value = value;
        }

        // Methods using Java primitives -----------------------------

        @PythonMethod
        int length() {
            return value.length();
        }

        @PythonMethod
        double density(String ch) {
            int n = value.length(), count = 0;
            if (ch.length() != 1) {
                throw new TypeError("arg must be single character");
            } else if (n > 0) {
                char c = ch.charAt(0);
                for (int i = 0; i < n; i++) {
                    if (value.charAt(i) == c) { count++; }
                }
                return ((double) count) / n;
            } else {
                return 0.0;
            }
        }

        // Methods using Python only types ---------------------------

        @PythonMethod
        Object upper() {
            return value.toUpperCase();
        }

        @PythonMethod
        Object find(PyTuple args) {
            // No intention of processing arguments robustly
            Object target = args.get(0);
            return value.indexOf(PyUnicode.asString(target));
        }

        @PythonMethod
        Object encode(PyTuple args, PyDict kwargs) {
            // No intention of processing arguments robustly
            Object encoding = kwargs.get("encoding");
            if (PyUnicode.TYPE.check(encoding)) {
                Charset cs =
                        Charset.forName(PyUnicode.asString(encoding));
                ByteBuffer bb = cs.encode(value);
                byte[] b = new byte[bb.limit()];
                bb.get(b);
                return null; // return new PyBytes(b);
            } else {
                throw new TypeError("encoding must be string");
            }
        }

        @Override
        public PyType getType() { return TYPE; }
    }

    /**
     * Test that we get working descriptors of type
     * {@link PyMethodDescr}s from the {@link Exposer} for methods
     * annotated in the test class {@link PyObjectWithMethods}.
     *
     * @throws Throwable unexpectedly
     * @throws AttributeError unexpectedly
     */
    // @Test
    void methodConstruct() throws AttributeError, Throwable {

        // We defined this Java method: should retrieve a descriptor
        PyMethodDescr length = (PyMethodDescr) PyObjectWithMethods.TYPE
                .lookup("length");

        assertNotNull(length);
        assertEquals("length", length.name);
        assertEquals(PyObjectWithMethods.TYPE, length.objclass);
        assertEquals(
                "<method 'length' of 'PyObjectWithMethods' objects>",
                length.toString());
    }

    /**
     * Test that we can call {@link PyMethodDescr}s directly for methods
     * annotated in the test class {@link PyObjectWithMethods}.
     *
     * @throws Throwable unexpectedly
     */
    // @Test
    void methodDescrCall() throws AttributeError, Throwable {

        PyType A = PyObjectWithMethods.TYPE;
        String hello = "Hello World!";
        Object a = new PyObjectWithMethods(hello);
        Object result;

        // length = A.length
        PyMethodDescr length =
                (PyMethodDescr) Abstract.getAttr(A, "length");
        assertEquals("length", length.name);
        assertEquals(A, length.objclass);
        // n = length(a) # = 12
        PyTuple args = Py.tuple(a);
        result = Callables.call(length, args, null);
        assertEquals(hello.length(), Number.index(result));

        // density = A.density(a, "l") # = 0.25
        PyMethodDescr density =
                (PyMethodDescr) Abstract.getAttr(A, "density");
        // Make a vector call
        result = density.call(a, "l");
        assertEquals(0.25, PyFloat.doubleValue(result), 1e-6);
    }

    /**
     * Test that attribute access on {@link PyMethodDescr}s from the
     * {@link Exposer} create bound method objects of type
     * {@link PyJavaMethod}, for methods annotated in the test class
     * {@link PyObjectWithMethods}.
     *
     * @throws Throwable unexpectedly
     */
    // @Test
    void boundMethodConstruct() throws AttributeError, Throwable {

        // Create an object of the right type
        String hello = "Hello World!";
        PyObjectWithMethods a = new PyObjectWithMethods(hello);

        // We defined this Java method: should retrieve a descriptor
        PyMethodDescr length = (PyMethodDescr) PyObjectWithMethods.TYPE
                .lookup("length");
        // Get the bound method (bound to a)
        PyJavaMethod bm = (PyJavaMethod) length.__get__(a, null);

        assertNotNull(bm);
        assertEquals(a, bm.self);
        assertEquals(length.methodDef, bm.methodDef);
        assertStartsWith(
                "<built-in method length of PyObjectWithMethods object",
                bm);
    }

    /**
     * Test that we can call {@link PyJavaMethod}s created by attribute
     * access on methods annotated in the test class
     * {@link PyObjectWithMethods}.
     *
     * @throws Throwable unexpectedly
     */
    // @Test
    void boundMethodCall() throws AttributeError, Throwable {

        String hello = "Hello World!";
        Object a = new PyObjectWithMethods(hello);
        Object result;

        // bm = a.length
        PyJavaMethod bm = (PyJavaMethod) Abstract.getAttr(a, "length");
        assertNotNull(bm);
        assertEquals(a, bm.self);

        // n = bm() # = 12
        result = Callables.call(bm);
        assertEquals(hello.length(), Number.index(result));

        // m = a.density
        bm = (PyJavaMethod) Abstract.getAttr(a, "density");

        // Force a classic call
        // result = bm("l") # = 0.25
        PyTuple args = Py.tuple("l");
        result = bm.__call__(args, null);
        assertEquals(0.25, PyFloat.doubleValue(result), 1e-6);

        // Make a vector call
        // result = bm("l") # = 0.25
        Object[] stack = new Object[] {"l"};
        result = bm.call(stack, 0, 1, null);
        assertEquals(0.25, PyFloat.doubleValue(result), 1e-6);
    }

    // Support methods -----------------------------------------------

    /** Assertion for prefix of a result. */
    private static void assertStartsWith(String expected,
            Object actual) {
        assertNotNull(actual);
        String actualString = actual.toString();
        int len = Math.min(expected.length(), actualString.length());
        String actualPrefix = actualString.substring(0, len);
        assertEquals(expected, actualPrefix);
    }

}
