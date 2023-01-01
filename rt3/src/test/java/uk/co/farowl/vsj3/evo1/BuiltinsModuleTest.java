package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static uk.co.farowl.vsj3.evo1.CPython38CodeTest.assertExpectedVariables;
import static uk.co.farowl.vsj3.evo1.CPython38CodeTest.readCode;
import static uk.co.farowl.vsj3.evo1.CPython38CodeTest.readResultDict;

import java.util.HashMap;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * This is a test of instantiating and using the {@code builtins}
 * module, which has a special place in the Python interpreter as the
 * name space. Many built-in types and functions are named there for use
 * by the Python interpreter and it is effectively implicitly imported.
 */
@DisplayName("The builtins module")
class BuiltinsModuleTest extends UnitTestSupport {

    static final String FILE = "BuiltinsModuleTest.java";

    @Test
    @DisplayName("exists on an interepreter")
    @SuppressWarnings("static-method")
    void existsOnInterpreter() {
        Interpreter interp = new Interpreter();
        PyModule builtins = interp.builtinsModule;
        assertNotNull(builtins);
    }

    @Test
    @DisplayName("has independent instances")
    @SuppressWarnings("static-method")
    void canBeInstantiated() {
        Interpreter interp1 = new Interpreter();
        Interpreter interp2 = new Interpreter();
        // Look up an arbitrary function in each interpreter
        PyJavaFunction abs1 = (PyJavaFunction)interp1.getBuiltin("abs");
        assertSame(abs1.self, interp1.builtinsModule);
        PyJavaFunction abs2 = (PyJavaFunction)interp2.getBuiltin("abs");
        assertSame(abs2.self, interp2.builtinsModule);
        // Each module provides distinct function objects
        assertNotSame(abs1, abs2);
        // builtins module instances are distinct
        assertNotSame(interp1.builtinsModule, interp2.builtinsModule);
    }

    @Nested
    @DisplayName("provides expected function ...")
    class TestFunctions {
        Interpreter interp;
        PyDict globals;
        Object locals;
        /* BuiltinsModule? */ PyModule builtins;

        @BeforeEach
        void setup() {
            interp = new Interpreter();
            globals = Py.dict();
            locals = PyMapping.map(new HashMap<>());
            builtins = interp.builtinsModule;
        }

        @Test
        @DisplayName("abs")
        void testAbs() throws Throwable {
            Object f = Abstract.getAttr(builtins, "abs");
            Object r = Callables.callFunction(f, -5.0);
            assertEquals(5.0, r);
        }

        /**
         * A simple test of {@code builtins.exec} where the code object
         * is created locally using {@link ActionHolder}.
         */
        @Test
        @DisplayName("exec(code)")
        void testExec() {
            String SPAM = "svinekjøtt";
            // A code object to exec
            ActionHolder example = new ActionHolder("spam_setter") {
                @Override
                Object body() throws Throwable {
                    globals.put("spam", SPAM);
                    return null;
                }
            };
            // Invokes the exec method
            ActionHolder c = new ActionHolder("exec-code") {
                @Override
                Object body() throws Throwable {
                    Object f = interp.getBuiltin("exec");
                    Callables.callFunction(f, example);
                    assertEquals(globals.get("spam"), SPAM);
                    return null;
                }
            };
            interp.eval(c, globals, locals);
        }

        /**
         * A test of {@code builtins.exec} using code objects read from
         * the {@code pythonExample} directory.
         *
         * @param name of the module to load
         */
        @DisplayName("exec(file)")
        @ParameterizedTest(name = "{0}.py")
        @ValueSource(strings = {"load_store_name", "unary_op",
                "binary_op", "attr_access_builtin",
                "call_method_builtin", "function_def", "function_call"})
        void testExecFile(String name) {
            // A code object to exec
            CPython38Code code = readCode(name);
            // Invokes the exec method
            ActionHolder c = new ActionHolder("exec-file") {
                @Override
                Object body() throws Throwable {
                    Object f = interp.getBuiltin("exec");
                    return Callables.callFunction(f, code);
                }
            };
            Object r = interp.eval(c, globals);
            assertEquals(Py.None, r);
            assertExpectedVariables(readResultDict(name), globals);
        }

        @Test
        @DisplayName("globals")
        void testGlobals() {
            ActionHolder c = new ActionHolder("globals-test") {
                @Override
                Object body() throws Throwable {
                    Object f = interp.getBuiltin("globals");
                    Object r = Callables.callFunction(f);
                    assertSame(globals, r);
                    return null;
                }
            };
            interp.eval(c, globals, locals);
        }

        @Test
        @DisplayName("locals")
        void testLocals() {
            ActionHolder c = new ActionHolder("locals-test") {
                @Override
                Object body() throws Throwable {
                    Object f = interp.getBuiltin("locals");
                    Object r = Callables.callFunction(f);
                    assertSame(locals, r);
                    return null;
                }
            };
            interp.eval(c, globals, locals);
        }

        @Test
        @DisplayName("len")
        void testLen() throws Throwable {
            Object f = Abstract.getAttr(builtins, "len");
            Object r = Callables.callFunction(f, "hello");
            assertEquals(5, r);
        }

        @Test
        @DisplayName("max")
        void testMax() throws Throwable {
            Object f = Abstract.getAttr(builtins, "max");
            Object r = Callables.callFunction(f, 4, 4.2, 5.0, 6);
            assertEquals(6, r);
            r = Callables.callFunction(f, Py.tuple(4, 4.2, 5.0, 6));
            assertEquals(6, r);
        }

        @Test
        @DisplayName("min")
        void testMin() throws Throwable {
            Object f = Abstract.getAttr(builtins, "min");
            Object r = Callables.callFunction(f, 4, 5.0, 6, 4.2);
            assertEquals(4, r);
            r = Callables.callFunction(f, Py.tuple(4, 5.0, 6, 4.2));
            assertEquals(4, r);
        }

        @Test
        @DisplayName("repr")
        void testRepr() throws Throwable {
            Object f = Abstract.getAttr(builtins, "repr");
            assertEquals("123", Callables.callFunction(f, 123));
            assertEquals("'spam'", Callables.callFunction(f, "spam"));
            // XXX implement None.__repr__
            // assertEquals("None", Callables.callFunction(f, Py.None));
        }
    }

    /**
     * An action defined in Java that Python will treat as a
     * {@code code} object. We use this trick to call functions in tests
     * where the function needs information from the execution
     * environment that can only be found through the current stack
     * frame, that is, through the {@link ThreadState} of the current
     * Java {@code Thread}. Most of the functions we test, although
     * Python objects, do not refer to the {@code ThreadeState}.
     * <p>
     * The class is a microcosm of the interpreter architectural
     * pattern: {@code code-function-frame}.
     *
     * @apiNote A more general version could have use as a JavaCode
     *     object.
     */
    abstract static class ActionHolder extends PyCode {

        final static PyTuple E = PyTuple.EMPTY;

        public ActionHolder(String name) {
            // No arguments, variables, etc..
            super(0, 0, 0, 0, 0, E, E, E, E, E, FILE, name, name, 0);
        }

        @Override
        Function createFunction(Interpreter interpreter, PyDict globals,
                Object[] defaults, PyDict kwdefaults,
                Object annotations, PyCell[] closure) {
            return new Function(interpreter, this, globals);
        }

        /**
         * The definition given to this method will be executed while
         * the {@link ThreadState} stack contains one valid frame. It
         * runs when we execute the code as a module with
         * {@link Interpreter#eval(PyCode, PyDict, Object)}.
         * <p>
         * It may also be run as a parameterless function created with
         * {@link #createFunction(Interpreter, PyDict)}, and called with
         * {@link Callables#call(Object)}.
         *
         * @return result of evaluation
         * @throws Throwable since we have no idea what the code is
         */
        abstract Object body() throws Throwable;

        /**
         * Present the code as a parameterless Python function. An
         * instance is created by
         * {@link ActionHolder#createFunction(Interpreter, PyDict)}.
         */
        static class Function extends PyFunction<ActionHolder> {

            Function(Interpreter interpreter, ActionHolder code,
                    PyDict globals) {
                super(interpreter, code, globals, null, null, null,
                        null);
            }

            @Override
            Frame createFrame(Object locals) {
                return new Frame(this, locals);
            }

            @Override
            void setDefaults(PyTuple defaults) {}

            @Override
            void setKwdefaults(PyDict kwdefaults) {}

            @Override
            Object __call__(Object[] args, String[] names)
                    throws Throwable {
                // There is a higher frame
                assert ThreadState.get().frame != null;
                // We're only expecting one (if not faulty test).
                assert ThreadState.get().frame.back == null;
                // This frame is loose
                Frame frame = createFrame(null);
                // No args to parse
                return frame.eval();
            }
        }

        /**
         * A Python frame representing the running state of the code. An
         * instance is created by {@link Function#createFrame(Object)}.
         */
        static class Frame extends PyFrame<ActionHolder> {
            Frame(Function func, Object locals) {
                super(func);
                this.locals = locals;
            }

            @Override
            Object eval() {
                // This frame is loose: push onto stack
                ThreadState tstate = ThreadState.get();
                tstate.push(this);
                try {
                    return func.code.body();
                } catch (Throwable t) {
                    throw Abstract.asUnchecked(t, "during eval()");
                } finally {
                    tstate.pop();
                }
            }

            @Override
            void fastToLocals() {}
        }
    }
}
