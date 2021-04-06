package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * This is a test of {@link ArgParser}. Since it is quite a complicated
 * beast, and that might make it fragile, we try to be thorough here,
 * rather than wait for it to let us down inside some complicated Python
 * built-in called in an unforeseen way.
 * <p>
 * Each nested test class provides one parser specification to all its
 * test methods, which then exercise that parser in a range of
 * circumstances. As far as possible, we use the same test names when
 * testing the same kind of behaviour.
 */
class ArgParserTest {

    abstract static class Standard {

        /**
         * A parser should have field values that correctly reflect the
         * arguments used in its construction.
         */
        abstract void has_expected_fields();

        /**
         * A parser should obtain the correct result (and not throw)
         * when applied to classic arguments matching its specification.
         */
        abstract void parses_classic_args();
    }
    @Nested
    @DisplayName("A parser for no arguments")
    class NoArgs extends Standard {

        ArgParser ap = ArgParser.fromSignature("func");

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("func", ap.name);
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
        void parses_classic_args() {
            PyTuple args = PyTuple.EMPTY;
            PyDict kwargs = Py.dict();

            // It's enough that this not throw
            ap.parse(args, kwargs);
        }
    }

    @Nested
    @DisplayName("A parser for positional arguments")
    class PositionalArgs extends Standard {

        ArgParser ap = ArgParser.fromSignature("func", "a", "b", "c");

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("func", ap.name);
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
        void parses_classic_args() {
            PyTuple args = Py.tuple(1, 2, 3);
            PyDict kwargs = Py.dict();

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(new Object[] {1, 2, 3}, frame);
        }

        @Test
        void parses_classic_kwargs() {
            PyTuple args = Py.tuple(1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            kwargs.put("b", 2);

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(new Object[] {1, 2, 3}, frame);
        }
    }

    @Nested
    @DisplayName("A parser for some positional-only arguments")
    class PositionalOnlyArgs extends Standard {

        ArgParser ap =
                ArgParser.fromSignature("func", "a", "b", "/", "c");

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("func", ap.name);
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
        void parses_classic_args() {
            PyTuple args = Py.tuple(1, 2, 3);
            PyDict kwargs = Py.dict();

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(new Object[] {1, 2, 3}, frame);
        }

        @Test
        void throws_when_arg_missing() {
            PyTuple args = Py.tuple(1);
            PyDict kwargs = Py.dict();
            kwargs.put("c", 3);
            assertThrows(TypeError.class, () -> ap.parse(args, kwargs));
        }
    }

    @Nested
    @DisplayName("Example 1 from the Javadoc")
    class FromJavadoc1 extends Standard {

        ArgParser ap = //
                new ArgParser("func", "aa", "kk", //
                        4, 3, //
                        "a", "b", "c", "d", "e", "f", "g", "h", "i")
                                .defaults(3, 4, 5, 6) //
                                .kwdefaults(77, null, 99);

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("func", ap.name);
            assertEquals(11, ap.argnames.length);
            assertEquals(6, ap.argcount);
            assertEquals(4, ap.posonlyargcount);
            assertEquals(3, ap.kwonlyargcount);
            assertEquals(9, ap.regargcount);
            assertEquals(9, ap.varArgsIndex);
            assertEquals(10, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() {
            PyTuple args = Py.tuple(10, 20, 30);
            PyDict kwargs = Py.dict();
            kwargs.put("g", 70);
            kwargs.put("h", 80);

            PyTuple expectedTuple = PyTuple.EMPTY;
            PyDict expectedDict = Py.dict();
            Object[] expected = new Object[] {10, 20, 30, 4, 5, 6, 70,
                    80, 99, expectedTuple, expectedDict};

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(expected, frame);
        }
    }

    @Nested
    @DisplayName("Example 2 from the Javadoc")
    class FromJavadoc2 extends Standard {

        String[] names = {"a", "b", "c", "d", "e", "f", "g", "h", "i",
                "aa", "kk"};
        ArgParser ap = new ArgParser("func", true, true, 4, 3, names,
                names.length - 2) //
                        .defaults(3, 4, 5, 6) //
                        .kwdefaults(77, null, 99);

        @Override
        @Test
        void has_expected_fields() {
            assertEquals("func", ap.name);
            assertEquals(11, ap.argnames.length);
            assertEquals(6, ap.argcount);
            assertEquals(4, ap.posonlyargcount);
            assertEquals(3, ap.kwonlyargcount);
            assertEquals(9, ap.regargcount);
            assertEquals(9, ap.varArgsIndex);
            assertEquals(10, ap.varKeywordsIndex);
        }

        @Override
        @Test
        void parses_classic_args() {
            PyTuple args = Py.tuple(10, 20, 30);
            PyDict kwargs = Py.dict();
            kwargs.put("g", 70);
            kwargs.put("h", 80);

            PyTuple expectedTuple = PyTuple.EMPTY;
            PyDict expectedDict = Py.dict();
            Object[] expected = new Object[] {10, 20, 30, 4, 5, 6, 70,
                    80, 99, expectedTuple, expectedDict};

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(expected, frame);
        }

        /**
         * When the keyword defaults are replaced with a client-supplied
         * {@code dict}, the new values take effect.
         */
        @Test
        void parses_classic_args_kwmap() {
            PyTuple args = Py.tuple(10, 20, 30);
            PyDict kwargs = Py.dict();
            kwargs.put("g", 70);

            PyDict kwd = Py.dict();
            kwd.put("h", 28);
            kwd.put("i", 29);
            ap.kwdefaults(kwd);

            PyTuple expectedTuple = PyTuple.EMPTY;
            PyDict expectedDict = Py.dict();
            Object[] expected = new Object[] {10, 20, 30, 4, 5, 6, 70,
                    28, 29, expectedTuple, expectedDict};

            Object[] frame = ap.parse(args, kwargs);
            assertArrayEquals(expected, frame);
        }
    }
}
