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
import uk.co.farowl.vsj3.evo1.Exposer.BaseMethodSpec;
import uk.co.farowl.vsj3.evo1.Exposer.CallableSpec;

/**
 * Test that a Python <b>type</b> defined in Java, using the scheme of
 * annotations defined in {@link Exposed} is processed correctly by a
 * {@link Exposer} to a dictionary. This tests a large part of the
 * exposure mechanism.
 */
@DisplayName("For a type defined in Java")
class TypeExposerTest {

    /**
     * This class is not actually a Python type definition, but is
     * annotated as if it were. We will test whether the type dictionary
     * is filled as expected.
     *
     * Methods named {@code m*()} are instance methods to Python,
     * declared to Java as instance methods ({@code this} is
     * {@code self}).
     *
     * Methods named {@code f*()} are static methods to Python (no
     * {@code self}), declared to Java as static methods.
     */
    static class Fake {

        static final Lookup LOOKUP = MethodHandles.lookup();

        // Signature: (/)
        @PythonStaticMethod
        static void f0() {}

        // Signature: ($self, /)
        @PythonMethod
        void m0() {}

        // Signature: (a, b, c /)
        @PythonStaticMethod
        static PyTuple f3(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($self, a, b, c /)
        @PythonMethod
        PyTuple m3(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: (/, a, b, c)
        @PythonStaticMethod(positionalOnly = false)
        static PyTuple f3pk(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($self, /, a, b, c)
        @PythonMethod(positionalOnly = false)
        PyTuple m3pk(int a, String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: (a, b, /, c)
        @PythonStaticMethod
        static PyTuple f3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(a, b, c);
        }

        // Signature: ($self, a, b, /, c)
        @PythonMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) {
            return Py.tuple(a, b, c);
        }
    }

    @Nested
    @DisplayName("calling the Exposer")
    class TestExposer {

        @Test
        @DisplayName("produces a TypeExposer")
        void getExposer() {
            TypeExposer exposer =
                    Exposer.exposeType(null, Fake.class, null);
            assertNotNull(exposer);
        }

        @Test
        @DisplayName("finds the expected methods")
        void getMethodSignatures() {
            TypeExposer exposer = Exposer.exposeType(PyBaseObject.TYPE,
                    Fake.class, null);
            // Fish out those things that are methods
            Map<String, ArgParser> dict = new TreeMap<>();
            for (Exposer.Spec s : exposer.specs.values()) {
                if (s instanceof CallableSpec) {
                    CallableSpec ms = (CallableSpec) s;
                    dict.put(ms.name, ms.getParser());
                }
            }
            checkMethodSignatures(dict);
        }

        private void
                checkMethodSignatures(Map<String, ArgParser> dict) {
            assertEquals(8, dict.size());

            checkSignature(dict, "f0()");
            checkSignature(dict, "m0($self, /)");
            checkSignature(dict, "f3(a, b, c, /)");
            checkSignature(dict, "m3($self, a, b, c, /)");
            checkSignature(dict, "f3pk(a, b, c)");
            checkSignature(dict, "m3pk($self, /, a, b, c)");
            checkSignature(dict, "f3p2(a, b, /, c)");
            checkSignature(dict, "m3p2($self, a, b, /, c)");
        }

        /**
         * Check that a method with the expected signature is in the
         * dictionary.
         *
         * @param dict dictionary
         * @param spec signature
         */
        private void checkSignature(Map<String, ArgParser> dict,
                String spec) {
            int k = spec.indexOf('(');
            assertTrue(k > 0);
            String name = spec.substring(0, k);
            String expect = spec.substring(k);
            ArgParser ap = dict.get(name);
            assertNotNull(ap, () -> name + " not found");
            assertEquals(expect, ap.textSignature());
        }
    }
}
