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
            ArgParser ap = isascii.methodDef.argParser;
            assertEquals("isascii", ap.name);
            assertEquals(0, ap.regargcount);
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
            assertEquals(1, ap.regargcount);
            assertEquals("width", ap.argnames[0]);
        }

        /**
         * An instance method with two object arguments. (There should
         * be an optional {@code int} but it isn't implemented.)
         */
        @Test
        void str_replace() throws Throwable {
            PyMethodDescr replace =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("replace");

            assertEquals("replace", replace.name);
            assertEquals(PyUnicode.TYPE, replace.objclass);

            // Check the parser
            ArgParser ap = replace.methodDef.argParser;
            assertEquals("replace", ap.name);
            assertEquals(2, ap.regargcount);
            assertEquals("old", ap.argnames[0]);
            assertEquals("new", ap.argnames[1]);
            // assertEquals("count", ap.argnames[2]);
        }

        /**
         * An instance method with an int and an optional object
         * argument.
         */
        @Test
        void str_ljust() throws Throwable {
            PyMethodDescr ljust =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("ljust");

            assertEquals("ljust", ljust.name);
            assertEquals(PyUnicode.TYPE, ljust.objclass);

            // Check the parser
            ArgParser ap = ljust.methodDef.argParser;
            String sig = "ljust($self, width, fillchar=' ', /)";
            assertEquals(sig, ap.toString());

            assertEquals("ljust", ap.name);
            assertEquals(2, ap.regargcount);
            assertEquals("width", ap.argnames[0]);
            assertEquals("fillchar", ap.argnames[1]);
            assertEquals(2, ap.posonlyargcount);
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
         * An instance method with two object arguments. (There should
         * be an optional {@code int} but it isn't implemented.)
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

        /**
         * An instance method with an int and an optional object
         * argument.
         */
        @Test
        void str_ljust() throws Throwable {
            PyMethodDescr ljust =
                    (PyMethodDescr) PyUnicode.TYPE.lookup("ljust");

            String s = "hello";
            PyUnicode u = newPyUnicode(s);

            // Test with fill character explicit
            for (Object o : List.of(s, u)) {
                Object r = ljust.__call__(Py.tuple(o, 8, "*"), null);
                assertEquals("hello***", r.toString());
            }

            // Test with fill character taking default value
            for (Object o : List.of(s, u)) {
                Object r = ljust.__call__(Py.tuple(o, 7), null);
                assertEquals("hello  ", r.toString());
            }
        }
    }
}
