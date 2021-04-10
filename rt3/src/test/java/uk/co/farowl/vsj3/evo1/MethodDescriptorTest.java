package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test the {@link PyMethodDescr}s on a variety of types. The particular
 * operations are not the focus: we are testing the mechanisms for
 * creating and calling method descriptors from methods defined in Java.
 * <p>
 * An example the type of object under test is:<pre>
 * >>> str.replace
 * &lt;method 'replace' of 'str' objects>
 * </pre> and its bound counterpart <pre>
 * >>> "hello".replace
 * &lt;built-in method replace of str object at 0x000001EC5935A3B0>
 * </pre>
 */
class MethodDescriptorTest extends UnitTestSupport {

    @Nested
    @DisplayName("Check some basic Exposer properties")
    class ExposerBasic {

        /** An instance method with no arguments. */
        @Test
        void str_isascii() throws Throwable {
            PyMethodDescr isascii =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("isascii");

            assertEquals("isascii", isascii.name);
            assertEquals(PyUnicode.TYPE, isascii.objclass);

            // Check the parser
            ArgParser md = isascii.methodDef.argParser;
            assertEquals("isascii", md.name);
            assertEquals(0, md.argnames.length);
        }

        /** An instance method with one argument. */
        @Test
        void str_zfill() throws Throwable {
            PyMethodDescr zfill =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("zfill");

            assertEquals("zfill", zfill.name);
            assertEquals(PyUnicode.TYPE, zfill.objclass);

            // Check the parser
            ArgParser ap = zfill.methodDef.argParser;
            assertEquals("zfill", ap.name);
            assertEquals(1, ap.argnames.length);
            assertEquals("width", ap.argnames[0]);
        }

        /**
         * An instance method two object arguments and an optional int.
         */
        @Test
        void str_replace() throws Throwable {
            PyMethodDescr replace =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("replace");

            assertEquals("replace", replace.name);
            assertEquals(PyUnicode.TYPE, replace.objclass);

            // Check the parser
            ArgParser ap = replace.methodDef.argParser;;
            assertEquals("replace", ap.name);
            assertEquals(2, ap.argnames.length);
            assertEquals("old", ap.argnames[0]);
            assertEquals("new", ap.argnames[1]);
            // assertEquals("count", ap.argnames[2]);
        }
    }

    @Nested
    @DisplayName("Call built-in method via descriptor")
    class CallBuiltin {

        /** An instance method with no arguments. */
        @Test
        void str_isascii() throws Throwable {
            PyMethodDescr isascii =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("isascii");

            boolean even = true;
            for (String s : List.of("Hej!", "¡Hola!")) {
                PyUnicode u = newPyUnicode(s);
                for (Object o : List.of(s, u)) {
                    Object r = isascii.__call__(Py.tuple(o), null);
                    assertEquals(even, r);
                }
                even = !even;
            }
        }

        /** An instance method with one argument. */
        @Test
        void str_zfill() throws Throwable {
            PyMethodDescr zfill =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("zfill");

            String s = "-123";
            PyUnicode u = newPyUnicode(s);

            for (Object o : List.of(s, u)) {
                Object r = zfill.__call__(Py.tuple(o, 6), null);
                assertEquals("-00123", r.toString());
            }
        }

        /**
         * An instance method two object arguments and an optional int.
         */
        @Test
        void str_replace() throws Throwable {
            PyMethodDescr replace =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("replace");

            String s = "hello";
            PyUnicode u = newPyUnicode(s);

            for (Object o : List.of(s, u)) {
                Object r = replace.__call__(Py.tuple(o, "ell", "ipp"),
                        null);
                assertEquals("hippo", r.toString());
            }
        }
    }
}
