package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyType.Spec;
import uk.co.farowl.vsj3.evo1.base.MethodKind;

/**
 * Test that methods exposed by a Python <b>type</b> defined in Java,
 * using the scheme of annotations defined in {@link Exposed}, result in
 * method descriptors with characteristics that correspond to their
 * definitions.
 * <p>
 * The first test in each case is to examine the fields in the parser
 * that attaches to the {@link MethodDef}. Then we call the function
 * using the {@code __call__} special method, and using our "fast call"
 * signatures.
 * <p>
 * There is a nested test suite for each signature pattern.
 */
@DisplayName("A method exposed by a type")
class TypeExposerMethodTest {

    /**
     * Certain nested test classes implement these as standard. A base
     * class here is just a way to describe the tests once that reappear
     * in each nested case.
     */
    abstract static class Standard {

        // Working variables for the tests
        /** Unbound descriptor by type access to examine or call. */
        PyMethodDescr descr;
        /** The object on which to invoke the method. */
        Object obj;
        /** The function to examine or call (bound to {@code obj}). */
        PyJavaMethod func;
        /** The parser we examine. */
        ArgParser ap;
        /** The expected result of calling the method. */
        Object[] exp;

        /**
         * A parser attached to the method descriptor should have field
         * values that correctly reflect the signature and annotations
         * in the defining class.
         */
        abstract void has_expected_fields();

        /**
         * Call the function using the {@code __call__} special method
         * with arguments correct for the method's specification. The
         * method should obtain the correct result (and not throw).
         *
         * @throws Throwable unexpectedly
         */
        abstract void supports__call__() throws Throwable;

        /**
         * Call the method using the {@code __call__} special method
         * with arguments correct for the method's specification, and
         * explicitly zero or more keywords. The method should obtain
         * the correct result (and not throw).
         *
         * @throws Throwable unexpectedly
         */
        abstract void supports_keywords() throws Throwable;

        /**
         * Call the method using the {@code __call__} special method and
         * an unexpected keyword: where none is expected, for a
         * positional argument, or simply an unacceptable name. The
         * method should throw {@link TypeError}.
         *
         * @throws Throwable unexpectedly
         */
        abstract void raises_TypeError_on_unexpected_keyword()
                throws Throwable;

        /**
         * Call the function using the Java call interface with
         * arguments correct for the function's specification. The
         * function should obtain the correct result (and not throw).
         *
         * @throws Throwable unexpectedly
         */
        abstract void supports_java_call() throws Throwable;

        /**
         * Check that the fields of the parser match expectations for a
         * method with no collector parameters and a certain number of
         * positional-only parameters.
         *
         * @param kind static or instance
         * @param name of method
         * @param count of parameters
         * @param posonlycount count of positional-only parameters
         */
        void no_collector(MethodKind kind, String name, int count,
                int posonlycount) {
            assertEquals(name, ap.name);
            assertEquals(kind, ap.methodKind);
            assertEquals(count, ap.argnames.length);
            assertEquals(count, ap.argcount);
            assertEquals(posonlycount, ap.posonlyargcount);
            assertEquals(0, ap.kwonlyargcount);
            assertEquals(count, ap.regargcount);
            assertEquals(-1, ap.varArgsIndex);
            assertEquals(-1, ap.varKeywordsIndex);
        }

        /**
         * Check that the fields of the parser match expectations for a
         * static method with no collector parameters and a certain
         * number of positional-only parameters.
         *
         * @param name of method
         * @param count of parameters
         * @param posonly count of positional-only parameters
         */
        void no_collector_static(String name, int count, int posonly) {
            no_collector(MethodKind.STATIC, name, count, posonly);
        }

        /**
         * Check that the fields of the parser match expectations for a
         * instance method with no collector parameters and a certain
         * number of positional-only parameters.
         *
         * @param name of method
         * @param count of parameters
         * @param posonly count of positional-only parameters
         */
        void no_collector_instance(String name, int count,
                int posonly) {
            no_collector(MethodKind.INSTANCE, name, count, posonly);
        }

        /**
         * Helper to set up each test.
         *
         * @param name of the method
         * @param o to use as the self argument
         * @throws AttributeError if method not found
         * @throws Throwable other errors
         */
        void setup(String name, Object o)
                throws AttributeError, Throwable {
            descr = (PyMethodDescr) ExampleObject.TYPE.lookup(name);
            ap = descr.methodDef.argParser;
            obj = o;
            func = (PyJavaMethod) Abstract.getAttr(obj, name);
        }

        /**
         * Check the result of a call against {@link #exp}. The
         * reference result is the same throughout a given sub-class
         * test.
         *
         * @param result of call
         */
        void check_result(PyTuple result) {
            assertArrayEquals(exp, result.value);
        }

    }

    /**
     * A Python type definition that exhibits a range of method
     * signatures explored in the tests. Methods named {@code m*()} are
     * instance methods to Python, declared to Java as either instance
     * methods ({@code this} is {@code self}) or as static methods
     * ({@code self} is the first parameter).
     */
    static class ExampleObject {

        static PyType TYPE = PyType
                .fromSpec(new Spec("Example", MethodHandles.lookup())
                        .adopt(ExampleObject2.class));

        /**
         * See {@link NoParams}: no parameters are allowed (after
         * {@code self}).
         */
        @PythonMethod(primary = false)
        void m0() {}

        @PythonMethod
        static void m0(ExampleObject2 self) {}

        /**
         * See {@link DefaultPositionalParams}: the parameters are
         * positional-only as a result of the default exposure.
         */
        @PythonMethod
        PyTuple m3(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @PythonMethod(primary = false)
        static PyTuple m3(ExampleObject2 self, int a, String b,
                Object c) {
            return Py.tuple(self, a, b, c);
        }

        /**
         * See {@link PositionalOrKeywordParams}: the parameters are
         * positional-or-keyword but none are positional-only.
         */
        @PythonMethod(positionalOnly = false)
        PyTuple m3pk(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @PythonMethod(primary = false)
        static PyTuple m3pk(ExampleObject2 self, int a, String b,
                Object c) {
            return Py.tuple(self, a, b, c);
        }

        /**
         * See {@link SomePositionalOnlyParams}: two parameters are
         * positional-only as a result of an annotation.
         */
        @PythonMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        @PythonMethod(primary = false)
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

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m0
            setup("m0", new ExampleObject());
            // The method is declared void (which means return None)
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m0", 0, 0);
        }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).m0(obj)
            Object[] args = {obj};
            Object r = descr.__call__(args, null);
            assertEquals(Py.None, r);

            // We call obj.m0()
            args = new Object[0];
            r = func.__call__(args, null);
            assertEquals(Py.None, r);
        }

        @Override
        @Test
        void supports_keywords() throws Throwable {
            // We call type(obj).m0(obj)
            Object[] args = {obj};
            String[] names = {};
            Object r = descr.__call__(args, names);
            assertEquals(Py.None, r);

            // We call obj.m0()
            args = new Object[0];
            r = func.__call__(args, names);
            assertEquals(Py.None, r);
        }

        /** To set anything by keyword is a {@code TypeError}. */
        @Override
        @Test
        void raises_TypeError_on_unexpected_keyword() {
            // We call type(obj).m0(obj, c=3)
            Object[] args = {obj, 3};
            String[] names = {"c"};
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, names));

            // We call obj.m0(c=3)
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, names));
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).m0(obj)
            Object r = descr.call(obj);
            assertEquals(Py.None, r);

            // We call obj.m0()
            r = func.call();
            assertEquals(Py.None, r);
        }
    }

    /**
     * {@link NoParams} with {@link ExampleObject2} as the
     * implementation.
     */
    @Nested
    @DisplayName("with no parameters" + " (type 2 impl)")
    class NoParams2 extends NoParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m0
            setup("m0", new ExampleObject2());
        }
    }

    /**
     * {@link ExampleObject#m3(int, String, Object)} accepts 3 arguments
     * that <b>must</b> be given by position.
     */
    @Nested
    @DisplayName("with positional-only parameters by default")
    class DefaultPositionalParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3
            setup("m3", new ExampleObject());
            exp = new Object[] {obj, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3", 3, 3);
        }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).m3(obj, 1, '2', 3)
            Object[] args = {obj, 1, "2", 3};
            PyTuple r = (PyTuple) descr.__call__(args, null);
            check_result(r);

            // We call obj.m3(1, '2', 3)
            args = Arrays.copyOfRange(args, 1, args.length);
            r = (PyTuple) func.__call__(args, null);
            check_result(r);
        }

        @Override
        @Test
        void supports_keywords() throws Throwable {
            // We call type(obj).m3(obj, 1, '2', 3)
            Object[] args = {obj, 1, "2", 3};
            String[] names = {};
            PyTuple r = (PyTuple) descr.__call__(args, names);
            check_result(r);

            // We call obj.m3(1, '2', 3)
            args = Arrays.copyOfRange(args, 1, args.length);
            r = (PyTuple) func.__call__(args, names);
            check_result(r);
        }

        @Override
        @Test
        void raises_TypeError_on_unexpected_keyword() {
            // We call type(obj).m3(obj, 1, '2', c=3)
            Object[] args = {obj, 1, "2", 3};
            String[] names = {"c"};
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, names));

            // We call obj.m3(1, '2', c=3)
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, names));
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).m3(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3(obj, 1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }
    }

    /**
     * {@link DefaultPositionalParams} with {@link ExampleObject2} as
     * the implementation.
     */
    @Nested
    @DisplayName("with positional-only parameters by default"
            + " (type 2 impl)")
    class DefaultPositionalParams2 extends DefaultPositionalParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3
            setup("m3", new ExampleObject2());
            exp = new Object[] {obj, 1, "2", 3};
        }
    }

    /**
     * {@link ExampleObject#m3pk(int, String, Object)} accepts 3
     * arguments that may be given by position or keyword.
     */
    @Nested
    @DisplayName("with positional-or-keyword parameters")
    class PositionalOrKeywordParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3pk
            setup("m3pk", new ExampleObject());
            exp = new Object[] {obj, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3pk", 3, 0);
        }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).m3pk(obj, 1, '2', 3)
            Object[] args = {obj, 1, "2", 3};
            String[] names = {};
            PyTuple r = (PyTuple) descr.__call__(args, names);
            check_result(r);

            // We call obj.m3pk(1, '2', 3)
            args = Arrays.copyOfRange(args, 1, args.length);
            r = (PyTuple) func.__call__(args, names);
            check_result(r);
        }

        @Override
        @Test
        void supports_keywords() throws Throwable {
            // We call type(obj).m3pk(obj, 1, c=3, b='2')
            Object[] args = {obj, 1, 3, "2"};
            String[] names = {"c", "b"};
            PyTuple r = (PyTuple) descr.__call__(args, names);
            check_result(r);

            // We call obj.m3pk(1, c=3, b='2')
            args = Arrays.copyOfRange(args, 1, args.length);
            r = (PyTuple) func.__call__(args, names);
            check_result(r);
        }

        @Override
        @Test
        void raises_TypeError_on_unexpected_keyword() throws Throwable {
            // We call type(obj).m3pk(obj, 1, c=3, b='2', x=4)
            Object[] args = {obj, 1, 3, "2", 4};
            String[] names = {"c", "b", /* unknown */"x"};
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, names));

            // We call obj.m3pk(1, c=3, b='2', x=4)
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, names));
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).m3pk(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3pk(1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }
    }

    /**
     * {@link PositionalOrKeywordParams} with {@link ExampleObject2} as
     * the implementation.
     */
    @Nested
    @DisplayName("with positional-or-keyword parameters"
            + " (type 2 impl)")
    class PositionalOrKeywordParams2 extends PositionalOrKeywordParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3pk
            setup("m3pk", new ExampleObject2());
            exp = new Object[] {obj, 1, "2", 3};
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

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3p2
            setup("m3p2", new ExampleObject());
            exp = new Object[] {obj, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3p2", 3, 2);
        }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).m3p2(obj, 1, '2', 3)
            Object[] args = {obj, 1, "2", 3};
            String[] names = {};
            PyTuple r = (PyTuple) descr.__call__(args, names);
            check_result(r);
        }

        /** To set {@code c} by keyword is a ok. */
        @Override
        @Test
        void supports_keywords() throws Throwable {
            // We call type(obj).m3p2(obj, 1, '2', c=3)
            Object[] args = {obj, 1, "2", 3};
            String[] names = {"c"};
            PyTuple r = (PyTuple) descr.__call__(args, names);
            check_result(r);

            // We call obj.m3p2(1, '2', c=3)
            args = Arrays.copyOfRange(args, 1, args.length);
            r = (PyTuple) func.__call__(args, names);
            check_result(r);
        }

        @Override
        @Test
        void raises_TypeError_on_unexpected_keyword() throws Throwable {
            // We call type(obj).m3p2(obj, 1, c=3, b='2')
            Object[] args = {obj, 1, 3, "2"};
            String[] names = {"c", /* positional */"b"};
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, names));

            // We call obj.m3p2(1, c=3, b='2')
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, names));
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).m3p2(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3p2(1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }
    }

    /**
     * {@link PositionalOrKeywordParams} with {@link ExampleObject2} as
     * the implementation.
     */
    @Nested
    @DisplayName("with two positional-only parameters"
            + " (type 2 impl)")
    class SomePositionalOnlyParams2 extends SomePositionalOnlyParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.m3p2
            setup("m3p2", new ExampleObject2());
            exp = new Object[] {obj, 1, "2", 3};
        }
    }

}
