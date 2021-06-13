package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Map;
import java.util.TreeMap;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.Exposed.Deleter;
import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Exposed.Member;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj3.evo1.Exposed.Setter;
import uk.co.farowl.vsj3.evo1.Exposer.CallableSpec;
import uk.co.farowl.vsj3.evo1.TypeExposer.GetSetSpec;
import uk.co.farowl.vsj3.evo1.TypeExposer.MemberSpec;

/**
 * Test that the annotations defined in {@link Exposed}, and intended
 * for exposing attributes of a type defined in Java, are processed
 * correctly by a {@link Exposer} to a {@link TypeExposer} containing
 * appropriate attribute specifications. This tests a large part of the
 * exposure mechanism, without activating the wider Python type system.
 */
@DisplayName("For a type exposed from a Java definition")
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

        // Instance methods -------------------------------------------

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

        // Instance members -------------------------------------------

        // Plain int
        @Member
        int i;

        // Plain float (with doc string)
        @Member
        @DocString("My test x")
        double x;

        // String with change of name.
        @Member("text")
        String t;

        // String can be properly deleted without popping up as None
        @Member(optional = true)
        String s;

        // Arbitrary object
        @Member
        Object obj;

        // Read-only by annotation
        @Member(readonly = true)
        int i2;

        // Read-only by final.
        @Member
        final double x2 = 1.0;

        // Read-only by annotation given before name change
        @Member(readonly = true, value = "text2")
        String t2;

        // String again (?)
        @Member(readonly = true)
        PyUnicode strhex2;

        // Instance attributes ----------------------------------------

        // Read-only (but changes to count updates to foo
        int count = 0;
        // Writable, but cannot delete
        String foo;
        // Writable, and has delete operation
        double thingValue;

        @Getter
        Object count() {
            return count;
        }

        @Getter
        Object foo() {
            return thingValue;
        }

        @Setter
        void foo(Object v) throws TypeError, Throwable {
            try {
                foo = (String) v;
            } catch (ClassCastException cce) {
                foo = "<invalid>";
            }
        }

        @Getter
        Object thing() {
             return thingValue;
        }

        @Setter("thing")
        void thing(Object v) throws TypeError, Throwable {
            try {
                thingValue = (Double) v;
            } catch (ClassCastException cce) {
                thingValue = Double.NaN;
            }
            count += 1;
        }

        @Deleter("thing")
        void deleteThing(Object v) throws TypeError, Throwable {
            thingValue = Double.NaN;
            count = 0;
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
            // type=null in order not to wake the type system
            TypeExposer exposer =
                    Exposer.exposeType(null, Fake.class, null);
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

        @Test
        @DisplayName("finds the expected members")
        void getMembers() {
            // type=null in order not to wake the type system
            TypeExposer exposer =
                    Exposer.exposeType(null, Fake.class, null);
            // Fish out those things that are members
            Map<String, MemberSpec> dict = new TreeMap<>();
            for (Exposer.Spec s : exposer.specs.values()) {
                if (s instanceof MemberSpec) {
                    MemberSpec ms = (MemberSpec) s;
                    dict.put(ms.name, ms);
                }
            }
            checkMembers(dict);
        }

        private void checkMembers(Map<String, MemberSpec> dict) {

            assertEquals(9, dict.size());

            MemberSpec ms;

            ms = checkMember(dict, "i");

            ms = checkMember(dict, "x");
            assertEquals("My test x", ms.doc);
            ms = checkMember(dict, "text");
            assertNull(ms.doc);

            ms = checkMemberOptional(dict, "s");
            ms = checkMember(dict, "obj");

            ms = checkMemberReadonly(dict, "i2");
            ms = checkMemberReadonly(dict, "x2");
            ms = checkMemberReadonly(dict, "text2");
            ms = checkMemberReadonly(dict, "strhex2");
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         * @param optional if it should be
         * @param readonly if it should be
         * @return the member spec (for further checks)
         */
        private MemberSpec checkMember(Map<String, MemberSpec> dict,
                String name, boolean optional, boolean readonly) {
            MemberSpec ms = dict.get(name);
            assertNotNull(ms, () -> name + " not found");
            assertEquals(optional, ms.optional,
                    () -> name + " optional");
            assertEquals(readonly, ms.readonly,
                    () -> name + " readonly");
            return ms;
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private MemberSpec checkMember(Map<String, MemberSpec> dict,
                String name) {
            return checkMember(dict, name, false, false);
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private MemberSpec checkMemberReadonly(
                Map<String, MemberSpec> dict, String name) {
            return checkMember(dict, name, false, true);
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private MemberSpec checkMemberOptional(
                Map<String, MemberSpec> dict, String name) {
            return checkMember(dict, name, true, false);
        }

        @Test
        @DisplayName("finds the expected get-set attributes")
        void getGetSets() {
            // type=null in order not to wake the type system
            TypeExposer exposer =
                    Exposer.exposeType(null, Fake.class, null);
            // Fish out those things that are get-sets
            Map<String, GetSetSpec> dict = new TreeMap<>();
            for (Exposer.Spec s : exposer.specs.values()) {
                if (s instanceof GetSetSpec) {
                    GetSetSpec gs = (GetSetSpec) s;
                    dict.put(gs.name, gs);
                }
            }
            checkGetSets(dict);
        }

        private void checkGetSets(Map<String, GetSetSpec> dict) {
            assertEquals(3, dict.size());
            checkGetSet(dict, "foo");
            checkGetSetOptional(dict, "thing");
            checkGetSetReadonly(dict, "count");
        }

        /**
         * Check that a get-set with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of attribute
         * @param optional if it should be
         * @param readonly if it should be
         * @return the member spec (for further checks)
         */
        private GetSetSpec checkGetSet(Map<String, GetSetSpec> dict,
                String name, boolean optional, boolean readonly) {
            GetSetSpec gs = dict.get(name);
            assertNotNull(gs, () -> name + " not found");
            assertEquals(optional, gs.optional(),
                    () -> name + " optional");
            assertEquals(readonly, gs.readonly(),
                    () -> name + " readonly");
            if (!readonly) {
                assertEquals(gs.getters.size(), gs.setters.size(),
                        () -> name + " setter size mismatch");
            }
            if (optional) {
                assertEquals(gs.getters.size(), gs.deleters.size(),
                        () -> name + " deleter size mismatch");
            }
            return gs;
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private GetSetSpec checkGetSet(Map<String, GetSetSpec> dict,
                String name) {
            return checkGetSet(dict, name, false, false);
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private GetSetSpec checkGetSetReadonly(
                Map<String, GetSetSpec> dict, String name) {
            return checkGetSet(dict, name, false, true);
        }

        /**
         * Check that a member with the expected properties is in the
         * dictionary and return the exposer specification object.
         *
         * @param dict dictionary
         * @param name of member
         */
        private GetSetSpec checkGetSetOptional(
                Map<String, GetSetSpec> dict, String name) {
            return checkGetSet(dict, name, true, false);
        }

    }
}
