package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;

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
 * method descriptors with characteristics that correspond to the
 * definition.
 * <p>
 * The first test in each case is to examine the fields in the parser
 * that attaches to the {@link MethodDef}. Then we call the descriptor
 * using classic arguments, and using our equivalent of the "vector
 * call", intended to support call sites compiled where Python invokes
 * the method.
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
         * Apply Python classic arguments matching the method's
         * specification. The method should obtain the correct result
         * (and not throw).
         *
         * @throws Throwable
         */
        abstract void takes_classic_args() throws Throwable;

        /**
         * Apply Java call arguments matching the method's
         * specification. The method should obtain the correct result
         * (and not throw).
         *
         * @throws Throwable
         */
        abstract void takes_java_args() throws Throwable;

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
        void takes_classic_args() throws Throwable {
            // We call type(obj).m0(obj)
            PyTuple args = Py.tuple(obj);
            PyDict kwargs = Py.dict();
            Object r = descr.__call__(args, kwargs);
            assertEquals(Py.None, r);

            // We call obj.m0()
            args = Py.tuple();
            r = func.__call__(args, kwargs);
            assertEquals(Py.None, r);
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call type(obj).m0(obj)
            Object r = descr.call(obj);
            assertEquals(Py.None, r);

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
        void takes_classic_args() throws Throwable {
            // We call type(obj).m3(*(obj, 1, '2', 3), **{})
            PyTuple args = Py.tuple(obj, 1, "2", 3);
            PyDict kwargs = Py.dict();
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            check_result(r);

            // We call obj.m3(*(1, '2', 3), **{})
            args = Py.tuple(1, "2", 3);
            r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        /** To set anything by keyword is a {@code TypeError}. */
        @Test
        void raises_TypeError_on_kwargs() {
            // We call type(obj).m3(*(obj, 1, '2'), **dict(c=3))
            PyTuple args = Py.tuple(obj, 1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, kwargs));

            // We call obj.m3(*(1, '2'), **dict(c=3))
            PyTuple args2 = Py.tuple(1, "2");
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, kwargs));
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call type(obj).m3(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3(obj, 1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        /** To set anything by keyword is a {@code TypeError}. */
        // @Test
        void raises_TypeError_on_java_kwnames() throws Throwable {
            // We call type(obj).m3(obj, 1, '2', c=3)
            Object[] vec = {1, "2", 3};
            PyTuple names = Py.tuple("c");
            assertThrows(TypeError.class,
                    () -> descr.call(obj, vec, names));

            // We call obj.m3(1, '2', c=3)
            assertThrows(TypeError.class, () -> func.call(vec, names));
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
        void takes_classic_args() throws Throwable {
            // We call type(obj).m3pk(obj, *(1, '2', 3), **{})
            PyTuple args = Py.tuple(obj, 1, "2", 3);
            PyDict kwargs = Py.dict();
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            check_result(r);

            // We call obj.m3pk(*(1, '2', 3))
            args = Py.tuple(1, "2", 3);
            r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        @Test
        void takes_classic_kwargs() throws Throwable {
            // We call type(obj).m3pk(*(obj, 1), **{c:3, b:'2')
            PyTuple args = Py.tuple(obj, 1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2);
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            check_result(r);

            // We call obj.m3pk(*(1,), **{c:3, b:'2')
            args = Py.tuple(1);
            r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call type(obj).m3pk(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3pk(1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        // @Test
        void takes_java_args_kwnames() throws Throwable {
            // We call type(obj).m3pk(obj, 1, c=3, b='2')
            Object[] args = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");
            PyTuple r = (PyTuple) descr.call(obj, args, names);
            check_result(r);

            // We call obj.m3pk(1, c=3, b='2')
            // XXX Needs to be callkw or something to disambiguate.
            r = (PyTuple) func.call(1, 3, "2", names);
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
        void takes_classic_args() throws Throwable {
            // We call type(obj).m3p2(obj, *(1, '2', 3), **{})
            PyTuple args = Py.tuple(obj, 1, "2", 3);
            PyDict kwargs = Py.dict();
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            check_result(r);
        }

        /** To set {@code c} by keyword is a ok. */
        @Test
        void takes_classic_kwargs() throws Throwable {
            // We call type(obj).m3p2(obj, *(1, '2'), **{c:3})
            PyTuple args = Py.tuple(obj, 1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            PyTuple r = (PyTuple) descr.__call__(args, kwargs);
            check_result(r);

            // We call obj.m3p2(*(1, '2'), **{c:3})
            args = Py.tuple(1, "2");
            r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        /** To set {@code b} by keyword is a {@code TypeError}. */
        @Test
        void raises_TypeError_on_bad_kwarg() throws Throwable {
            // We call type(obj).m3p2(*(obj, 1), **{c:3, b:'2'})
            PyTuple args = Py.tuple(obj, 1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2); // error
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, kwargs));

            // We call obj.m3p2(*(1), **{c:3, b:'2'})
            PyTuple args2 = Py.tuple(1);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, kwargs));
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call type(obj).m3p2(obj, 1, '2', 3)
            PyTuple r = (PyTuple) descr.call(obj, 1, "2", 3);
            check_result(r);

            // We call obj.m3p2(1, '2', 3)
            r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        /** To set {@code c} by keyword is a ok. */
        // @Test
        void takes_java_args_kwnames() throws Throwable {
            // We call type(obj).m3p2(obj, 1, '2', c=3)
            PyTuple names = Py.tuple("c");
            PyTuple r = (PyTuple) descr.call(obj, 1, '2', 3, names);
            check_result(r);

            // We call obj.m3p2(1, '2', c=3)
            r = (PyTuple) func.call(1, '2', 3, names);
            check_result(r);
        }

        /** To set {@code b} by keyword is a {@code TypeError}. */
        // @Test
        void raises_TypeError_on_bad_kwname() throws Throwable {
            // We call type(obj).m3p2(obj, 1, c=3, b='2')
            // The attempt to set b by keyword is a TypeError.
            Object[] vec = {obj, 1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");
            assertThrows(TypeError.class, () -> descr.call(vec, names));

            // We call obj.m3p2(1, c=3, b='2')
            Object[] vec2 = {1, 3, "2"};
            assertThrows(TypeError.class, () -> func.call(vec2, names));
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
