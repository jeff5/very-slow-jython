package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * This is a test of instantiating and using the {@code builtins}
 * module, which has a special place in the Python interpreter as the
 * name space. Many built-in types and functions are named there for use
 * by the Python interpreter and it is effectively implicitly imported.
 */
@DisplayName("The builtins module")
class BuiltinsModuleTest extends UnitTestSupport {

    @Test
    @DisplayName("exists on an interepreter")
    void existsOnInterpreter() {
        Interpreter interp = new Interpreter();
        PyModule builtins = interp.builtinsModule;
        assertNotNull(builtins);
    }

    // @Test
    @DisplayName("has independent instances")
    void canBeInstantiated() { fail("Not yet implemented"); }

    @Nested
    @DisplayName("provides expected function ...")
    static class TestFunctions {

        @Test
        @DisplayName("abs")
        void testAbs() throws AttributeError, Throwable {
            PyModule builtins = new BuiltinsModule();
            Object abs = Abstract.getAttr(builtins, "abs");
            Object r = Callables.callFunction(abs, -5.0);
            assertEquals(5.0, r);
        }

        // @Test
        @DisplayName("globals")
        void testGlobals() { fail("Not yet implemented"); }

        @Test
        @DisplayName("len")
        void testLen() throws AttributeError, Throwable {
            PyModule builtins = new BuiltinsModule();
            Object len = Abstract.getAttr(builtins, "len");
            Object r = Callables.callFunction(len, "hello");
            assertEquals(5, r);
        }

        // @Test
        @DisplayName("max")
        void testMax() { fail("Not yet implemented"); }

        // @Test
        @DisplayName("min")
        void testMin() { fail("Not yet implemented"); }

        // @Test
        @DisplayName("repr")
        void testRepr() { fail("Not yet implemented"); }
    }
}
