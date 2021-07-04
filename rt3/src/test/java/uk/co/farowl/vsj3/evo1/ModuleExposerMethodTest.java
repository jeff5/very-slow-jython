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
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj3.evo1.base.MethodKind;

/**
 * Test that functions exposed by a Python <b>module</b> defined in
 * Java, using the scheme of annotations defined in {@link Exposed},
 * result in {@link PyJavaMethod} descriptors with characteristics that
 * correspond to the definition.
 * <p>
 * The first test in each case is to examine the fields in the parser
 * that attaches to the {@link MethodDef}. Then we call the descriptor
 * using classic arguments, and using our equivalent of the "vector
 * call", intended to support call sites compiled where Python invokes
 * the method.
 * <p>
 * There is a nested test suite for each signature pattern.
 */
// XXX Tests relying on the vector call (java interface) are disabled
@DisplayName("A method exposed by a module")
class ModuleExposerMethodTest {

    /**
     * Nested test classes implement these as standard. A base class
     * here is just a way to describe the tests once that we repeat in
     * each nested case.
     */
    abstract static class Standard {

        // Working variables for the tests
        /** The module we create. */
        PyModule module = new ExampleModule();
        /** The function to examine or call. */
        PyJavaMethod func;
        /** The parser in the function we examine. */
        ArgParser ap;
        /** The expected result of calling the function */
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
         * Check the result of a call against {@link #exp}. The
         * reference rtesult is the same throughout a given sub-class
         * test.
         *
         * @param result of call
         */
        void check_result(PyTuple result) {
            assertArrayEquals(exp, result.value);
        }
    }

    /**
     * A Python module definition that exhibits a range of method
     * signatures explored in the tests.
     */
    static class ExampleModule extends JavaModule {

        static final ModuleDef DEF =
                new ModuleDef("example", MethodHandles.lookup());

        ExampleModule() { super(DEF); }

        /**
         * See {@link StaticNoParams}: no parameters are allowed.
         */
        @PythonStaticMethod
        static void f0() {}

        /**
         * See {@link NoParams}: no parameters are allowed.
         */
        @PythonMethod
        void m0() {}

        /**
         * See {@link StaticDefaultPositionalParams}: the parameters are
         * positional-only as a result of the default exposure.
         */
        @PythonStaticMethod
        static PyTuple f3(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        /**
         * See {@link DefaultPositionalParams}: the parameters are
         * positional-only as a result of the default exposure.
         */
        @PythonMethod
        PyTuple m3(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        /**
         * See {@link StaticPositionalOrKeywordParams}: the parameters
         * are positional-or-keyword but none are positional-only.
         */
        @PythonStaticMethod(positionalOnly = false)
        static PyTuple f3pk(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        /**
         * See {@link PositionalOrKeywordParams}: the parameters are
         * positional-or-keyword but none are positional-only.
         */
        @PythonMethod(positionalOnly = false)
        PyTuple m3pk(int a, String b, Object c) {
            return Py.tuple(this, a, b, c);
        }

        /**
         * See {@link SomePositionalOnlyParams}: two parameters are
         * positional-only as a result of an annotation.
         */
        @PythonStaticMethod
        static PyTuple f3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(a, b, c);
        }

        /**
         * See {@link StaticSomePositionalOnlyParams}: two parameters
         * are positional-only as a result of an annotation.
         */
        @PythonMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(this, a, b, c);
        }
    }

    /** {@link ExampleModule#m0()} accepts no arguments. */
    @Nested
    @DisplayName("with no parameters")
    class NoParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.m0
            func = (PyJavaMethod) Abstract.getAttr(module, "m0");
            ap = func.methodDef.argParser;
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m0", 0, 0);
        }

        @Override
        @Test
        void takes_classic_args() throws Throwable {
            // We call func()
            PyTuple args = Py.tuple();
            PyDict kwargs = Py.dict();

            // The method is declared void (which means return None)
            Object r = func.__call__(args, kwargs);
            assertEquals(Py.None, r);
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call func()
            // The method is declared void (which means return None)
            Object r = func.call();
            assertEquals(Py.None, r);
        }
    }

    /** {@link ExampleModule#f0()} accepts no arguments. */
    @Nested
    @DisplayName("static, with no parameters")
    class StaticNoParams extends NoParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.f0
            func = (PyJavaMethod) Abstract.getAttr(module, "f0");
            ap = func.methodDef.argParser;
        }

        @Override
        @Test
        void has_expected_fields() { no_collector_static("f0", 0, 0); }
    }

    /**
     * {@link ExampleModule#m3(int, String, Object)} accepts 3 arguments
     * that <b>must</b> be given by position.
     */
    @Nested
    @DisplayName("with positional-only parameters by default")
    class DefaultPositionalParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.m3
            func = (PyJavaMethod) Abstract.getAttr(module, "m3");
            ap = func.methodDef.argParser;
            exp = new Object[] {module, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3", 3, 3);
        }

        @Override
        @Test
        void takes_classic_args() throws Throwable {
            // We call func(*(1, '2', 3))
            PyTuple args = Py.tuple(1, "2", 3);
            PyDict kwargs = Py.dict();
            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        @Test
        void raises_TypeError_on_kwargs() {
            // We call func(*(o, 1, '2'), **dict(c=3))
            Object o = new ExampleModule();
            PyTuple args = Py.tuple(o, 1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);

            assertThrows(TypeError.class,
                    () -> func.__call__(args, kwargs));
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // We call func(1, '2', 3)
            PyTuple r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        @Test
        void raises_TypeError_on_java_kwnames() throws Throwable {
            // We call func(*(o, 1), **dict(c=3, b='2'))
            Object o = new ExampleModule();
            Object[] vec = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");

            assertThrows(TypeError.class,
                    () -> func.call(o, vec, names));
        }
    }

    /**
     * {@link ExampleModule#f3(int, String, Object)} accepts 3 arguments
     * that <b>must</b> be given by position.
     */
    @Nested
    @DisplayName("static, with positional-only parameters by default")
    class StaticDefaultPositionalParams
            extends DefaultPositionalParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.f3
            func = (PyJavaMethod) Abstract.getAttr(module, "f3");
            ap = func.methodDef.argParser;
            exp = new Object[] {1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() { no_collector_static("f3", 3, 3); }
    }

    /**
     * {@link ExampleModule#m3pk(int, String, Object)} accepts 3
     * arguments that may be given by position or keyword.
     */
    @Nested
    @DisplayName("with positional-or-keyword parameters")
    class PositionalOrKeywordParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.m3pk
            func = (PyJavaMethod) Abstract.getAttr(module, "m3pk");
            ap = func.methodDef.argParser;
            exp = new Object[] {module, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3pk", 3, 0);
        }

        @Override
        @Test
        void takes_classic_args() throws Throwable {
            // We call func(*(1, '2', 3), **{})
            PyTuple args = Py.tuple(1, "2", 3);
            PyDict kwargs = Py.dict();
            PyTuple r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        @Test
        void takes_classic_kwargs() throws Throwable {
            // We call func(*(1,), **{c:3, b:'2')
            PyTuple args = Py.tuple(1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2);
            PyTuple r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            PyTuple r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        // @Test
        void takes_java_args_kwnames() throws Throwable {
            // We call func(*(1,), **{c:3, b:'2')
            Object[] args = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");
            PyTuple r = (PyTuple) func.call(args, names);
            check_result(r);
        }
    }

    /**
     * {@link ExampleModule#f3pk(int, String, Object)} accepts 3
     * arguments that may be given by position or keyword.
     */
    @Nested
    @DisplayName("static, with positional-or-keyword parameters")
    class StaticPositionalOrKeywordParams
            extends PositionalOrKeywordParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.f3pk
            func = (PyJavaMethod) Abstract.getAttr(module, "f3pk");
            ap = func.methodDef.argParser;
            exp = new Object[] {1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_static("f3pk", 3, 0);
        }

    }

    /**
     * {@link ExampleModule#m3p2(int, String, Object)} accepts 3
     * arguments, two of which may be given by position only, and the
     * last by either position or keyword.
     */
    @Nested
    @DisplayName("with two positional-only parameters")
    class SomePositionalOnlyParams extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.m3p2
            func = (PyJavaMethod) Abstract.getAttr(module, "m3p2");
            ap = func.methodDef.argParser;
            exp = new Object[] {module, 1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_instance("m3p2", 3, 2);
        }

        @Override
        @Test
        void takes_classic_args() throws Throwable {
            // We call func(*(1, '2', 3), **{})
            PyTuple args = Py.tuple(1, "2", 3);
            PyDict kwargs = Py.dict();

            // The method just parrots its arguments as a tuple
            PyTuple r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        /** Supply third argument by keyword. */
        @Test
        void takes_classic_kwargs() throws Throwable {
            // We call func(*(1, '2'), **{c:3)
            PyTuple args = Py.tuple(1, "2");
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) func.__call__(args, kwargs);
            check_result(r);
        }

        /** Supply second and third argument by keyword. */
        @Test
        void raises_TypeError_on_bad_kwarg() throws Throwable {
            // We call func(*(1,), **{c:3, b:'2'})
            PyTuple args = Py.tuple(1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2); // error

            assertThrows(TypeError.class,
                    () -> func.__call__(args, kwargs));
        }

        @Override
        @Test
        void takes_java_args() throws Throwable {
            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) func.call(1, "2", 3);
            check_result(r);
        }

        /** Supply third argument by keyword. */
        // @Test
        void takes_java_args_kwnames() throws Throwable {
            Object[] args = {1, "2", 3};
            PyTuple names = Py.tuple("c");

            // The method reports its arguments as a tuple
            PyTuple r = (PyTuple) func.call(args, names);
            check_result(r);
        }

        /** Supply second and third argument by keyword. */
        // @Test
        void raises_TypeError_on_bad_kwname() throws Throwable {
            // We call func(*(1,), **dict(c=3, b='2'))
            Object[] vec = {1, 3, "2"};
            PyTuple names = Py.tuple("c", "b");

            assertThrows(TypeError.class, () -> func.call(vec, names));
        }
    }

    /**
     * {@link ExampleModule#f3p2(int, String, Object)} accepts 3
     * arguments, two of which may be given by position only, and the
     * last by either position or keyword.
     */
    @Nested
    @DisplayName("static, with two positional-only parameters")
    class StaticSomePositionalOnlyParams
            extends SomePositionalOnlyParams {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // func = module.f3p2
            func = (PyJavaMethod) Abstract.getAttr(module, "f3p2");
            ap = func.methodDef.argParser;
            exp = new Object[] {1, "2", 3};
        }

        @Override
        @Test
        void has_expected_fields() {
            no_collector_static("f3p2", 3, 2);
        }
    }

}
