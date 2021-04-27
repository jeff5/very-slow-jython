package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.JavaMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.PyType.Spec;

/**
 * Test that methods exposed by a definition in Java, using the scheme
 * of annotations defined in {@link Exposed} result in a method
 * descriptor with the expected characteristics. The first test in each
 * case is to examine the fields in the parser that attaches to the
 * {@link MethodDef}. There is a nested test suite for each
 */
@DisplayName("The descriptor of an exposed method")
class ExposedMethodTest {

    /**
     * Nested test classes implement these as standard. A base class
     * here is just a way to describe the tests once that we repeat in
     * each nested case.
     */
    abstract static class Standard {

        /**
         * A parser attached to the method descriptor should have field
         * values that correctly reflect the signature and annotations
         * in the defining class.
         */
        abstract void has_expected_fields();

        /**
         * Apply Python classic arguments matching the method's
         * specification. The method should obtain the correct result
         * (and not throw).
         *
         * @throws Throwable
         */
        abstract void parses_classic_args() throws Throwable;

        /**
         * Apply Java call arguments matching the method's
         * specification. The method should obtain the correct result
         * (and not throw).
         *
         * @throws Throwable
         */
        abstract void parses_java_args() throws Throwable;
    }

    /**
     * A Python type definition that exhibits a range of method
     * signatures explored in the tests.
     */
    static class ExampleObject {

        static PyType TYPE = PyType.fromSpec(
                new Spec("ExampleObject", MethodHandles.lookup())
                        .adopt(ExampleObject2.class));

        /**
         * See {@link NoParams}: no parameters are allowed (after
         * {@code self}).
         */
        @JavaMethod(primary = false)
        void m0() {}

        @JavaMethod
        static void m0(ExampleObject2 self) {}

        /**
         * See {@link DefaultPositionalParams}: the parameters are
         * positional-only as a result of the default exposure.
         */
        @JavaMethod
        PyTuple m3(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @JavaMethod(primary = false)
        static PyTuple m3(ExampleObject2 self, int a, String b,
                Object c) {
            return Py.tuple(self, a, b, c);
        }

        /**
         * See {@link PositionalOrKeywordParams}: the parameters are
         * positional-or-keyword but none are positional-only.
         */
        @JavaMethod(positionalOnly = false)
        PyTuple m3pk(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @JavaMethod(primary = false)
        static PyTuple m3pk(ExampleObject2 self, int a, String b,
                Object c) {
            return Py.tuple(self, a, b, c);
        }

        /**
         * See {@link SomePositionalOnlyParams}: two parameters are
         * positional-only as a result of an annotation.
         */
        @JavaMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @JavaMethod(primary = false)
        static PyTuple m3p2(ExampleObject2 self, int a, String b,
                Object c) {
            return Py.tuple(self, a, b, c);
        }
    }

    /**
     * Class cited as an "adopted implementation" of
     * {@link ExampleObject}
     */
    static class ExampleObject2 {}

    /** {@link ExampleObject#m0()} accepts no arguments. */
    @Nested
    @DisplayName("with no parameters")
    class NoParams extends Standard {

        PyMethodDescr descr =
                (PyMethodDescr) ExampleObject.TYPE.lookup("m0");
        ArgParser ap = descr.methodDef.argParser;

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("m0", ap.name);
            assertEquals(0, ap.argnames.length);
            assertEquals(0, ap.argcount);
            assertEquals(0, ap.posonlyargcount);
            assertEquals(0, ap.kwonlyargcount);
            assertEquals(0, ap.regargcount);
            assertEquals(-1, ap.varArgsIndex);
            assertEquals(-1, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() throws Throwable {
            // We call type(o).m0(o)
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o);
            PyDict kwargs = Py.dict();

            // The method is declared void (which means return None)
            Object r = descr.__call__(args, kwargs);
            assertEquals(Py.None, r);
        }

        @Override
        @Test
        void parses_java_args() throws Throwable {
            // We call type(o).m0(o)
            Object o = new ExampleObject();

            // The method is declared void (which means return None)
            Object r = descr.call(o);
            assertEquals(Py.None, r);
        }
    }

    /**
     * {@link ExampleObject#m3(int, String, Object)} accepts 3 arguments
     * that <b>must</b> be given by position.
     */
    @Nested
    @DisplayName("with positional-only parameters by default")
    class DefaultPositionalParams extends Standard {

        PyMethodDescr descr =
                (PyMethodDescr) ExampleObject.TYPE.lookup("m3");
        ArgParser ap = descr.methodDef.argParser;

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("m3", ap.name);
            assertEquals(3, ap.argnames.length);
            assertEquals(3, ap.argcount);
            assertEquals(3, ap.posonlyargcount);
            assertEquals(0, ap.kwonlyargcount);
            assertEquals(3, ap.regargcount);
            assertEquals(-1, ap.varArgsIndex);
            assertEquals(-1, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() throws Throwable {
            // We call type(o).m3(*(o, 1, '2', 3))
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1, "2", 3);
            PyDict kwargs = Py.dict();

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);

            assertArrayEquals(args.value, r.value);
        }

        @Test
        void raises_TypeError_on_kwargs() {
            // We call type(o).m3(*(o, 1, '2'), **dict(c=3))
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);

            assertThrows(TypeError.class,
                    () -> descr.__call__(args, kwargs));
        }

        @Override
        @Test
        void parses_java_args() throws Throwable {
            // We call type(o).m3(o, 1, '2', 3)
            Object o = new ExampleObject();

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.call(o, 1, "2", 3);

            Object[] exp = {o, 1, "2", 3};
            assertArrayEquals(exp, r.value);
        }

        @Test
        void raises_TypeError_on_java_kwnames() throws Throwable {
            // We call type(o).m3(*(o, 1), **dict(c=3, b='2'))
            Object o = new ExampleObject();
            Object[] vec = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");

            assertThrows(TypeError.class,
                    () -> descr.call(o, vec, names));
        }
    }

    /**
     * {@link ExampleObject#m3pk(int, String, Object)} accepts 3
     * arguments that may be given by position or keyword.
     */
    @Nested
    @DisplayName("with positional-or-keyword parameters")
    class PositionalOrKeywordParams extends Standard {

        PyMethodDescr descr =
                (PyMethodDescr) ExampleObject.TYPE.lookup("m3pk");
        ArgParser ap = descr.methodDef.argParser;

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("m3pk", ap.name);
            assertEquals(3, ap.argnames.length);
            assertEquals(3, ap.argcount);
            assertEquals(0, ap.posonlyargcount);
            assertEquals(0, ap.kwonlyargcount);
            assertEquals(3, ap.regargcount);
            assertEquals(-1, ap.varArgsIndex);
            assertEquals(-1, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() throws Throwable {
            // We call type(o).m3pk(o, *(1, '2', 3), **{})
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1, "2", 3);
            PyDict kwargs = Py.dict();

            // The method just parrots its arguments as a tuple
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            assertArrayEquals(args.value, r.value);
        }

        @Test
        void parses_classic_kwargs() throws Throwable {
            // We call type(o).m3pk(*(o, 1), **{c:3, b:'2')
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2);

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);

            Object[] exp = {o, 1, "2", 3};
            assertArrayEquals(exp, r.value);
        }

        @Override
        @Test
        void parses_java_args() throws Throwable {
            Object o = new ExampleObject();
            Object[] exp = {o, 1, "2", 3};
            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.call(o, 1, "2", 3);
            assertArrayEquals(exp, r.value);
        }

        @Test
        void parses_java_args_kwnames() throws Throwable {
            // We call type(o).m3pk(*(o, 1), **{c:3, b:'2')
            Object o = new ExampleObject();
            Object[] args = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.call(o, args, names);

            Object[] exp = {o, 1, "2", 3};
            assertArrayEquals(exp, r.value);
        }
    }

    /**
     * {@link ExampleObject#m3p2(int, String, Object)} accepts 3
     * arguments, two of which may be given by position only, and the
     * last by either position or keyword.
     */
    @Nested
    @DisplayName("with two positional-only parameters")
    class SomePositionalOnlyParams extends Standard {

        PyMethodDescr descr =
                (PyMethodDescr) ExampleObject.TYPE.lookup("m3p2");
        ArgParser ap = descr.methodDef.argParser;

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("m3p2", ap.name);
            assertEquals(3, ap.argnames.length);
            assertEquals(3, ap.argcount);
            assertEquals(2, ap.posonlyargcount);
            assertEquals(0, ap.kwonlyargcount);
            assertEquals(3, ap.regargcount);
            assertEquals(-1, ap.varArgsIndex);
            assertEquals(-1, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() throws Throwable {
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1, "2", 3);
            PyDict kwargs = Py.dict();

            // The method just parrots its arguments as a tuple
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            assertArrayEquals(args.value, r.value);
        }

        /** Supply third argument by keyword. */
        @Test
        void parses_classic_kwargs() throws Throwable {
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);

            Object[] exp = {o, 1, "2", 3};
            assertArrayEquals(exp, r.value);
        }

        /** Supply second and third argument by keyword. */
        @Test
        void raises_TypeError_on_bad_kwarg() throws Throwable {
            // We call type(o).m3pk(*(o, 1), **{c:3, b:'2')
            Object o = new ExampleObject();
            PyTuple args = Py.tuple(o, 1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2); // error

            assertThrows(TypeError.class,
                    () -> descr.__call__(args, kwargs));
        }

        @Override
        @Test
        void parses_java_args() throws Throwable {
            Object o = new ExampleObject();
            Object[] exp = {o, 1, "2", 3};
            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.call(o, 1, "2", 3);
            assertArrayEquals(exp, r.value);
        }

        /** Supply third argument by keyword. */
        @Test
        void parses_java_args_kwnames() throws Throwable {
            Object o = new ExampleObject();
            Object[] args = {1, "2", 3};
            PyTuple names = Py.tuple("c");

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) descr.call(o, args, names);

            Object[] exp = {o, 1, "2", 3};
            assertArrayEquals(exp, r.value);
        }

        /** Supply second and third argument by keyword. */
        @Test
        void raises_TypeError_on_bad_kwname() throws Throwable {
            // We call type(o).m3(*(o, 1), **dict(c=3, b='2'))
            Object o = new ExampleObject();
            Object[] vec = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");

            assertThrows(TypeError.class,
                    () -> descr.call(o, vec, names));
        }
    }
}
