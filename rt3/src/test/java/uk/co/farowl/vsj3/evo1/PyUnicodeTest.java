package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.LinkedList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj3.evo1.PyUnicode.CodepointDelegate;

/**
 * Test selected methods of {@link PyUnicode} on a variety of argument
 * types.
 */
@DisplayName("In PyUnicode")
class PyUnicodeTest extends UnitTestSupport {

    abstract static class AdaptationTest {
        @Test
        void testAdaptObject() {
            CodepointDelegate delegate = PyUnicode.adapt("hello");
            assertEquals(5, delegate.length());
        }

        @Test
        void testAdapt() { fail("Not yet implemented"); }
    }

    abstract static class FactoryTest {
        @Test
        void testFromJavaString() { fail("Not yet implemented"); }
    }

    /** Base of tests that find strings in others. */
    abstract static class AbstractFindTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character, that is .
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> findExamples() {
            return Stream.of(//
                    findExample("pandemic", "pan"), //
                    findExample("pandemic", "mic"), //
                    findExample("abracadabra", "bra"), //
                    findExample("abracadabra", "a"), //
                    findExample("Bananaman", "ana"), //
                    findExample(GREEK, "λόγος"), //
                    findExample(GREEK, " "), //
                    findExample("画蛇添足 添足 添足", " 添"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    findExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {4, 16, 17}),
                    findExample("Left 🐍🦓🐍🦓: right.", "🐍🦓:",
                            new int[] {7}));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #findExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @return example data for a test
         */
        private static Arguments findExample(String self,
                String needle) {
            int[] indices = findIndices(self, needle);
            return findExample(self, needle, indices);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments findExample(String self, String needle,
                int[] indices) {
            return arguments(self, needle, indices);
        }
    }

    /** Tests of {@code str.find} operating on the whole string. */
    @Nested
    @DisplayName("find (whole string)")
    class FindTest extends AbstractFindTest {

        @DisplayName("find(String, String, null, null)")
        @ParameterizedTest(name = "find(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_find_S(String s, String needle, int[] indices) {
            int r = PyUnicode.find(s, needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "find(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_find_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.find(s, uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "find(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_find_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.find(needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("find(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "find(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_find_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.find(uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }
    }

    /** Tests of {@code str.partition}. */
    @Nested
    @DisplayName("partition")
    class PartitionTest extends AbstractFindTest {

        @DisplayName("partition(String, String)")
        @ParameterizedTest(name = "partition(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_partition_S(String s, String needle, int[] indices) {
            PyTuple r = PyUnicode.partition(s, needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(String, PyUnicode)")
        @ParameterizedTest(name = "partition(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_partition_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = PyUnicode.partition(s, uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(PyUnicode, String)")
        @ParameterizedTest(name = "partition(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_partition_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyTuple r = u.partition(needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }

        @DisplayName("partition(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "partition(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_partition_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyTuple r = u.partition(uNeedle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            if (indices.length == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }
    }

    /** Tests of {@code str.count} operating on the whole string. */
    @Nested
    @DisplayName("count (whole string)")
    class CountTest extends AbstractFindTest {

        @DisplayName("count(String, String, null, null)")
        @ParameterizedTest(name = "count(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_count_S(String s, String needle, int[] indices) {
            int r = PyUnicode.count(s, needle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "count(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void S_count_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.count(s, uNeedle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "count(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_count_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.count(needle, null, null);
            assertEquals(indices.length, r);
        }

        @DisplayName("count(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "count(\"{0}\", \"{1}\")")
        @MethodSource("findExamples")
        void U_count_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.count(uNeedle, null, null);
            assertEquals(indices.length, r);
        }
    }

    /** Base of tests that find strings in others. */
    abstract static class AbstractReverseFindTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character, that is .
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> rfindExamples() {
            return Stream.of(//
                    rfindExample("pandemic", "pan"), //
                    rfindExample("pandemic", "mic"), //
                    rfindExample("abracadabra", "bra"), //
                    rfindExample("Bananaman", "ana"), //
                    rfindExample(GREEK, "λόγος"), //
                    rfindExample(GREEK, " "), //
                    rfindExample("画蛇添足 添足 添足", " 添"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    rfindExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {17, 16, 4}),
                    rfindExample("Left 🐍🦓🐍🦓: right.", "🐍🦓:",
                            new int[] {7}));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #rfindExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @return example data for a test
         */
        private static Arguments rfindExample(String self,
                String needle) {
            int[] indices = rfindIndices(self, needle);
            return rfindExample(self, needle, indices);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments rfindExample(String self,
                String needle, int[] indices) {
            return arguments(self, needle, indices);
        }
    }

    /** Tests of {@code str.rfind} operating on the whole string. */
    @Nested
    @DisplayName("rfind (whole string)")
    class ReverseFindTest extends AbstractReverseFindTest {

        @DisplayName("rfind(String, String, null, null)")
        @ParameterizedTest(name = "rfind(\"{0}\", \"{1}\")")
        @MethodSource("rfindExamples")
        void S_rfind_S(String s, String needle, int[] indices) {
            int r = PyUnicode.rfind(s, needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("rfind(String, PyUnicode, null, null)")
        @ParameterizedTest(name = "rfind(\"{0}\", \"{1}\")")
        @MethodSource("rfindExamples")
        void S_rfind_U(String s, String needle, int[] indices) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = PyUnicode.rfind(s, uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("rfind(PyUnicode, String, null, null)")
        @ParameterizedTest(name = "rfind(\"{0}\", \"{1}\")")
        @MethodSource("rfindExamples")
        void U_rfind_S(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            int r = u.rfind(needle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }

        @DisplayName("rfind(PyUnicode, PyUnicode, null, null)")
        @ParameterizedTest(name = "rfind(\"{0}\", \"{1}\")")
        @MethodSource("rfindExamples")
        void U_rfind_U(String s, String needle, int[] indices) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            int r = u.rfind(uNeedle, null, null);
            if (indices.length == 0) {
                // There should be no match
                assertEquals(-1, r);
            } else {
                // Match at indices[0]
                assertEquals(indices[0], r);
            }
        }
    }

    /** Tests of {@code str.rpartition}. */
    @Nested
    @DisplayName("rpartition")
    class ReversePartitionTest extends AbstractReverseFindTest {

        @DisplayName("rpartition(String, String)")
        @ParameterizedTest(name = "rpartition(\"{0}\", \"{1}\")")
        @MethodSource("rfindExamples")
        void S_rpartition_S(String s, String needle, int[] indices) {
            PyTuple r = PyUnicode.rpartition(s, needle);
            assertPythonType(PyTuple.TYPE, r);
            assertEquals(3, r.size());
            for (int i = 0; i < 3; i++) {
                assertPythonType(PyUnicode.TYPE, r.get(i));
            }
            int M = indices.length;
            if (M == 0) {
                // There should be no match
                assertEquals(Py.tuple(s, "", ""), r);
            } else {
                // Match at indices[0]
                int[] charIndices = toCharIndices(s, indices);
                // Work in char indices (so doubtful with surrogates)
                int n = charIndices[0], m = n + needle.length();
                assertEquals(Py.tuple(s.substring(0, n), needle,
                        s.substring(m)), r);
            }
        }
    }

    /** Base of tests that exercise string replacement. */
    abstract static class AbstractReplaceTest {
        /**
         * Provide a stream of examples as parameter sets to the tests
         * of methods that have "search" character, that is .
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> replaceExamples() {
            return Stream.of(//
                    replaceExample("pandemic", "pan", "ping"), //
                    replaceExample("abracadabra", "bra", "x"), //
                    replaceExample("bananarama", "anar", " dr"), //
                    replaceExample("Σωκρατικὸς λόγος", "ὸς", "ὸι"), //
                    replaceExample("Σωκρατικὸς λόγος", "ς", "σ"), //
                    replaceExample("画蛇添足 添足 添足", " 添", "**"), //
                    /*
                     * The following contain non-BMP characters
                     * 🐍=U+1F40D and 🦓=U+1F993, each of which Python
                     * must consider to be a single character, but in
                     * the Java String realisation each is two chars.
                     */
                    // 🐍=\ud802\udc40, 🦓=\ud83e\udd93
                    replaceExample("One 🐍, a 🦓, two 🐍🐍.", "🐍",
                            new int[] {4, 16, 17}, "🦓"),
                    replaceExample("Swap 🐍🦓.", "🐍🦓", new int[] {5},
                            "(🦓🐍)"));
        }

        /**
         * Construct a search problem and reference result. This uses
         * Java {@code String.indexOf} for the reference answer, so it
         * will work correctly only for BMP strings. Where any SMP
         * characters are involved, call
         * {@link #replaceExample(String, String, int[], String)}.
         *
         * @param self to search
         * @param needle to search for
         * @param pin to replace needle
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, String pin) {
            int[] indices = findIndices(self, needle);
            return replaceExample(self, needle, indices, pin);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs once.
         *
         * @param self to search
         * @param needle to search for
         * @param index at which {@code needle} may be found
         * @param pin to replace needle
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, int index, String pin) {
            return replaceExample(self, needle, new int[] {index}, pin);
        }

        /**
         * Construct a search problem and reference result, where the
         * needle occurs at a list of indices.
         *
         * @param self to search
         * @param needle to search for
         * @param indices at which {@code needle}is found (code points)
         * @param pin to replace needle (if tested)
         * @return example data for a test
         */
        private static Arguments replaceExample(String self,
                String needle, int[] indices, String pin) {
            return arguments(self, needle, indices, pin);
        }

        /**
         * Return a list of strings equal to {@code s} with {@code 0} to
         * {@code M} replacements of the needle by the pin, guided by an
         * array of {@code M} char indices for the needle. Element zero
         * of the returned value is {@code s}.
         *
         * @param s in which to effect the replacements.
         * @param needle to replace
         * @param cpIndices array of {@code M} character indices
         * @param pin replacement string
         * @return {@code M+1} strings
         */
        static String[] replaceResults(String s, String needle,
                int[] cpIndices, String pin) {
            int[] charIndices = toCharIndices(s, cpIndices);
            final int M = charIndices.length, N = needle.length(),
                    P = pin.length();
            // Make a list of s with 0..M replacements at the indices
            List<String> results = new LinkedList<>();
            StringBuilder r = new StringBuilder(s);
            results.add(s);
            for (int m = 0; m < M; m++) {
                /*
                 * r contains s with m replacements, and its value has
                 * already been emitted to results. Compute the result
                 * of m+1 replacements. Start by trimming r at the
                 * (m+1)th needle.
                 */
                r.setLength(charIndices[m] + m * (P - N));
                // Now append the pin and the rest of s after the needle
                r.append(pin).append(s.substring(charIndices[m] + N));
                results.add(r.toString());
            }
            return results.toArray(new String[M + 1]);
        }

    }

    @Nested
    @DisplayName("replace")
    class ReplaceTest extends AbstractReplaceTest {

        @DisplayName("replace(String, String, String)")
        @ParameterizedTest(name = "replace(\"{0}\", \"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_SS(String s, String needle, int[] indices,
                String pin) {
            Object r = PyUnicode.replace(s, needle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, PyUnicode, String)")
        @ParameterizedTest(name = "replace(\"{0}\", \"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_US(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            Object r = PyUnicode.replace(s, uNeedle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, String, PyUnicode)")
        @ParameterizedTest(name = "replace(\"{0}\", \"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_SU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = PyUnicode.replace(s, needle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("replace(String, PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "replace(\"{0}\", \"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void S_replace_UU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = PyUnicode.replace(s, uNeedle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(String, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_SS(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            Object r = u.replace(needle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(PyUnicode, String)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_US(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            Object r = u.replace(uNeedle, pin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(String, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_SU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = u.replace(needle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        @DisplayName("PyUnicode.replace(PyUnicode, PyUnicode)")
        @ParameterizedTest(name = "\"{0}\".replace(\"{1}\", \"{3}\")")
        @MethodSource("replaceExamples")
        void U_replace_UU(String s, String needle, int[] indices,
                String pin) {
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            PyUnicode uNeedle =
                    new PyUnicode(needle.codePoints().toArray());
            PyUnicode uPin = new PyUnicode(pin.codePoints().toArray());
            Object r = u.replace(uNeedle, uPin);
            final int M = indices.length;
            String[] e = replaceResults(s, needle, indices, pin);
            assertEquals(e[M], r);
        }

        // Cases where simulation by Java String is too hard.
        // 🐍=\ud802\udc40, 🦓=\ud83e\udd93

        // @Test
        void surrogatePairNotSplit_SS() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", needle = "\ud83d", pin = "#";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("#\udc0d#\udc0d");

            // Python does not match paired high surrogates as isolated
            Object r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = "\udc0d";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("\ud83d#\ud83d#");

            // Python does not match paired low surrogates as isolated
            r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);
        }

        // @Test
        void surrogatePairNotSplit_US() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", pin = "#";
            PyUnicode needle = new PyUnicode(0xd83d);
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle.toString(), pin)
                    .equals("#\udc0d#\udc0d");

            // Python does not match paired low surrogates as isolated
            Object r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = new PyUnicode(0xdc0d);
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle.toString(), pin)
                    .equals("\ud83d#\ud83d#");

            // Python does not match paired low surrogates as isolated
            r = PyUnicode.replace(s, needle, pin);
            assertEquals(s, r);
        }

        // @Test
        @DisplayName("🐍 is not dissected as \\ud802\\udc40")
        void supplementaryCharacterNotSplit_SS() {
            // No high surrogate (D800-DBFF) accidental replacement
            String s = "🐍🐍", needle = "\ud83d", pin = "#";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("#\udc0d#\udc0d");

            // PyUnicode stores a surrogate pair as one character
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            assert u.equals(s);
            Object r = u.replace(needle, pin);
            assertEquals(u, r);

            // No low surrogate (DC00-DFFF) accidental replacement
            needle = "\udc0d";
            // Assert that Java gets the non-Pythonic answer
            assert s.replace(needle, pin).equals("\ud83d#\ud83d#");

            // PyUnicode stores a surrogate pair as one character
            r = u.replace(needle, pin);
            assertEquals(u, r);
        }

        // @Test
        @DisplayName("a 🦓 is not produced by String \\ud83e\\udd93")
        void S_noSpontaneousZebras() {
            // Deleting "-" risks surrogate pair formation
            String s = "\ud83e-\udd93\ud83e-\udd93", needle = "-";
            // Java String: nothing, bang, zebras
            assert s.replace(needle, "").equals("🦓🦓");

            // Python lone surrogates remain aloof even when adjacent
            PyUnicode e = new PyUnicode(0xd83e, 0xdd93, 0xd83e, 0xdd93);
            Object r = PyUnicode.replace(s, needle, "");
            assertEquals(e, r);
        }

        // @Test
        @DisplayName("a 🦓 is not produced by PyUnicode \\ud83e\\udd93")
        void U_noSpontaneousZebras_SS() {
            // No accidental surrogate pair formation
            String s = "\ud83e-\udd93\ud83e-\udd93", needle = "-";
            // Java String: nothing, bang, zebras
            assert s.replace(needle, "").equals("🦓🦓");

            // Python lone surrogates remain aloof even when adjacent
            PyUnicode u = new PyUnicode(s.codePoints().toArray());
            assert u.equals(s);
            PyUnicode e = new PyUnicode(0xd83e, 0xdd93, 0xd83e, 0xdd93);
            Object r = u.replace(needle, "");
            assertEquals(e, r);
        }
    }

    abstract static class PredicateTest {
        @Test
        void testIsascii() { fail("Not yet implemented"); }
    }

    // @Nested
    class AdaptationTestString extends AdaptationTest {

    }

    // Support code ---------------------------------------------------

    /**
     * Return a list of char indices on {@code s} at which the given
     * {@code needle} may be found. Occurrences found are
     * non-overlapping. This uses Java {@code String.indexOf} and will
     * work correctly for BMP strings, but is unreliable where any SMP
     * characters are involved.
     *
     * @param s string in question
     * @param needle to search for
     * @return char indices at which {@code needle} may be found
     */
    static int[] findIndices(String s, String needle) {
        LinkedList<Integer> charIndices = new LinkedList<>();
        int n = needle.length(), p = 0;
        while ((p = s.indexOf(needle, p)) >= 0) {
            charIndices.add(p);
            p += n;
        }
        int[] a = new int[charIndices.size()];
        for (int i = 0; i < a.length; i++) { a[i] = charIndices.pop(); }
        return a;
    }

    /**
     * Return a list of char indices on {@code s} at which the given
     * {@code needle} may be found, working backwards from the end. This
     * uses Java {@code String.indexOf} and will work correctly for BMP
     * strings, but is unreliable where any SMP characters are involved.
     *
     * @param s string in question
     * @param needle to search for
     * @return char indices at which {@code needle} may be found
     */
    static int[] rfindIndices(String s, String needle) {
        LinkedList<Integer> charIndices = new LinkedList<>();
        int n = needle.length(), p = s.length() - n;
        while ((p = s.lastIndexOf(needle, p)) >= 0) {
            charIndices.add(p);
            p -= n;
        }
        int[] a = new int[charIndices.size()];
        for (Integer i = 0; i < a.length; i++) {
            a[i] = charIndices.pop();
        }
        return a;
    }

    /**
     * Return a list of char indices on {@code s} equivalent to the code
     * point indices supplied.
     *
     * @param s string in question
     * @param cpIndices code point indices to convert
     * @return equivalent char indices on s
     */
    static int[] toCharIndices(String s, int[] cpIndices) {
        final int M = cpIndices.length;
        int[] charIndices = new int[M];
        int cpi = 0, p = 0, m = 0;
        for (int cpIndex : cpIndices) {
            // Advance p to char index of next cp index
            while (cpi < cpIndex) {
                int cp = s.codePointAt(p);
                p += Character.isBmpCodePoint(cp) ? 1 : 2;
                cpi++;
            }
            charIndices[m++] = p;
        }
        return charIndices;
    }

    // Non-ascii quotation with precomposed polytonic Greek characters.
    static final String GREEK = "Ἐν ἀρχῇ ἦν ὁ λόγος, " //
            + "καὶ ὁ λόγος ἦν πρὸς τὸν θεόν, " //
            + "καὶ θεὸς ἦν ὁ λόγος.";

}
