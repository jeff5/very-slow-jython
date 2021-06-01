package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;

/**
 * Test that a Python <b>module</b> defined in Java, using the scheme of
 * annotations defined in {@link Exposed} is processed correctly by a
 * {@link Exposer} to a {@link ModuleDef}. This tests a large part of
 * the exposure mechanism.
 */
@DisplayName("For a module defined in Java")
class ModuleExposerTest extends UnitTestSupport {

    /**
     * This class is not actually a Python module definition, but is
     * annotated as if it were. We will test whether the
     * {@link MethodDef}s are created as expected. We'll also act on it
     * to produce a dictionary as if it were a real module.
     */
    static class FakeModule {

        static final Lookup LOOKUP = MethodHandles.lookup();

        // Signature: ()
        @PythonStaticMethod
        static void f0() {}

        // Signature: ($module, /)
        @PythonMethod
        void m0() {}

        // Signature: (a, b, c, /)
        @PythonStaticMethod
        static PyTuple f3(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($module, a, b, c, /)
        @PythonMethod
        PyTuple m3(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: (/, a, b, c)
        @PythonStaticMethod(positionalOnly = false)
        static PyTuple f3pk(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($module, /, a, b, c)
        @PythonMethod(positionalOnly = false)
        PyTuple m3pk(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: (a, b, /, c)
        @PythonStaticMethod
        static PyTuple f3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($module, a, b, /, c)
        @PythonMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(a, b, c);
        }
    }

    @Nested
    @DisplayName("calling the Exposer")
    class TestExposer {

        @Test
        @DisplayName("produces a ModuleExposer")
        void getExposer() {
            ModuleExposer exposer =
                    Exposer.exposeModule(FakeModule.class);
            assertNotNull(exposer);
        }

        @Test
        @DisplayName("finds the expected methods")
        void getMethodDefs() {
            ModuleExposer exposer =
                    Exposer.exposeModule(FakeModule.class);
            MethodDef[] mdArray =
                    exposer.getMethodDefs(FakeModule.LOOKUP);
            checkMethodDefArray(mdArray);
        }
    }

    @Nested
    @DisplayName("constructing a ModuleDef")
    class TestDefinition {

        @Test
        @DisplayName("produces a MethodDef array")
        void createMethodDef() {
            ModuleDef md = new ModuleDef("example", FakeModule.LOOKUP);
            checkMethodDefArray(md.getMethods());
        }
    }

    @Nested
    @DisplayName("a module instance")
    class TestInstance {

        @Test
        @DisplayName("has expected method signatures")
        void hasMethods() {
            ModuleDef md = new ModuleDef("example", FakeModule.LOOKUP);
            PyModule mod = new PyModule(md.name);
            for (MethodDef def : md.getMethods()) {
                PyJavaMethod m = new PyJavaMethod(def, mod, md.name);
                mod.dict.put(def.name, m);
            }
            checkMethodSignatures(mod.dict);
        }
    }

    private void checkMethodDefArray(MethodDef[] mdArray) {
        assertNotNull(mdArray);

        Map<String, MethodDef> mds = new TreeMap<>();
        for (MethodDef md : mdArray) { mds.put(md.name, md); }

        Set<String> expected = new TreeSet<>();
        expected.addAll(List.of( //
                "f0", "f3", "f3pk", "f3p2", //
                "m0", "m3", "m3pk", "m3p2"));

        assertEquals(expected, mds.keySet(), "contains expected names");
    }

    private void checkMethodSignatures(Map<Object, Object> dict) {
        assertNotNull(dict);

        checkSignature(dict, "f0()");
        checkSignature(dict, "m0($module, /)");
        checkSignature(dict, "f3(a, b, c, /)");
        checkSignature(dict, "m3($module, a, b, c, /)");
        checkSignature(dict, "f3pk(a, b, c)");
        checkSignature(dict, "m3pk($module, /, a, b, c)");
        checkSignature(dict, "f3p2(a, b, /, c)");
        checkSignature(dict, "m3p2($module, a, b, /, c)");
    }

    /**
     * Check that a method with the expected signature is in the
     * dictionary.
     *
     * @param dict dictionary
     * @param spec signature
     */
    private void checkSignature(Map<Object, Object> dict, String spec) {
        int k = spec.indexOf('(');
        assertTrue(k>0);
        String name = spec.substring(0, k);
        String expect = spec.substring(k);
        PyJavaMethod pjm = (PyJavaMethod) dict.get(name);
        assertEquals(expect, pjm.methodDef.argParser.textSignature());
    }

}
