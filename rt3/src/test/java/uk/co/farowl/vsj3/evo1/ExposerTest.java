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
 * <p>
 * This skimpy test may be unnecessary in the light of more thorough
 * ones on module and type exposure.
 */
class ExposerTest {

    /**
     * Model canonical implementation to explore exposure of a special
     * method.
     */
    private static class ObjectWithSpMeth implements CraftedPyObject {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("ObjectWithSpecialMethods", LOOKUP)
                        .accept(AcceptedSpecial.class));
        int value;

        @SuppressWarnings("unused")
        public ObjectWithSpMeth(int value) { this.value = value; }

        @SuppressWarnings("unused")
        Object __neg__() { return new ObjectWithSpMeth(-value); }

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

        public AcceptedSpecial(int value) { this.value = value; }
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

    private static class PyObjectWithMethods
            implements CraftedPyObject {

        /** Lookup object to support creation of descriptors. */
        private static final Lookup LOOKUP = MethodHandles.lookup();
        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("PyObjectWithMethods", LOOKUP));
        String value;

        public PyObjectWithMethods(String value) { this.value = value; }

        // Methods using Java primitives -----------------------------

        @PythonMethod
        int length() { return value.length(); }

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
        Object upper() { return value.toUpperCase(); }

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
    @Test
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
    @Test
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
        Object[] args = {a};
        result = Callables.call(length, args, null);
        assertEquals(hello.length(), PyNumber.index(result));

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
    @Test
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
        assertSame(a, bm.self);
        assertSame(length.argParser, bm.argParser);
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
    @Test
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
        assertEquals(hello.length(), PyNumber.index(result));

        // m = a.density
        bm = (PyJavaMethod) Abstract.getAttr(a, "density");

        // Force a classic call
        // result = bm("l") # = 0.25
        Object[] args = {"l"};
        result = bm.__call__(args, null);
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
