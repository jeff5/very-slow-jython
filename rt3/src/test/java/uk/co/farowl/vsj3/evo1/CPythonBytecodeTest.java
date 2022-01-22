package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import uk.co.farowl.vsj3.evo1.base.InterpreterError;
import uk.co.farowl.vsj3.evo1.modules.marshal;

/**
 * Tests that read code objects from prepared {@code .pyc} files and
 * execute the byte code.
 *
 * These files are prepared in the Gradle build using a compatible
 * version of CPython, from Python source in .
 */
@DisplayName("Given programs compiled by CPython")
class CPythonBytecodeTest extends UnitTestSupport {

    @DisplayName("Read a code object")
    @ParameterizedTest(name = "from {0}")
    @ValueSource(strings = {"unary_op", "binary_op", "load_store_name"})
    void loadCodeObject(String name) {
        PyCode code = readCode(name);
        assertNotNull(code);
    }

    @DisplayName("Read a result object")
    @ParameterizedTest(name = "from {0}")
    @ValueSource(strings = {"unary_op", "binary_op", "load_store_name"})
    void loadResultDict(String name) {
        PyDict dict = readResultDict(name);
        assertNotNull(dict);
    }

    // Supporting constants and methods -------------------------------

    /** The Gradle build directory. */
    private static final Path BUILD = buildDirectory();
    /**
     * Python source of the examples for test. This must be consistent
     * with the definition of
     */
    private static final Path PYTHON_DIR = BUILD
            .resolve("generated/sources/pythonExample/test/vsj3/evo1");
    /** Where compiled files are placed by CPython. */
    private static final Path PYC_DIR =
            PYTHON_DIR.resolve("__pycache__");

    /**
     * The name fragment used by the compiler in the supported version
     * of CPython, e.g. {@code "cpython-38"}.
     */
    private static final String CPYTHON_VER = "cpython-38";
    /**
     * The magic number placed by the supported version of CPython, in
     * the header of compiled files.
     */
    private static final int MAGIC_NUMBER = 3413;

    private static final String PYC_SUFFIX = "pyc";
    private static final String VAR_SUFFIX = "var";

    /**
     * Read a {@code code} object with {@code marshal}. The method looks
     * for compiled examples in the customary directory
     * ({@link #PYC_DIR}}, being provided only the base name of the
     * program. So for example, {@code "unary_op"} will retrieve a code
     * object from {@code unary_op.cpython-38.pyc} in
     * {@code generated/sources/pythonExample/test/vsj3/evo1/__pycache__}.
     *
     * @param progName base name of program
     * @return {@code code} object read in
     */
    static PyCode readCode(String progName) {
        String name = progName + "." + CPYTHON_VER + "." + PYC_SUFFIX;
        File f = PYC_DIR.resolve(name).toFile();
        try (
                FileInputStream fs = new FileInputStream(f);
                BufferedInputStream s = new BufferedInputStream(fs);) {

            // Wrap a marshal reader around the input stream
            marshal.Reader reader = new marshal.StreamReader(s);

            // First 16 bytes is a header
            int magic = reader.readShort();
            assert magic == MAGIC_NUMBER;
            int magic2 = reader.readShort();
            assert magic2 == 0x0a0d;
            for (int i = 0; i < 3; i++) { reader.readInt(); }

            // Next should be a code object
            Object o = reader.readObject();
            if (o instanceof PyCode) {
                return (PyCode)o;
            } else {
                throw new InterpreterError("Not a code object: %s",
                        name);
            }

        } catch (IOException ioe) {
            throw new InterpreterError(ioe);
        }
    }

    /**
     * Read a {@code dict} object with {@code marshal}. The method looks
     * for the saved results of compiled examples in the customary
     * directory ({@link #PYC_DIR}}, being provided only the base name
     * of the program. So for example, {@code "unary_op"} will retrieve
     * a code object from {@code unary_op.cpython-38.var} in
     * {@code generated/sources/pythonExample/test/vsj3/evo1/__pycache__}.
     *
     * @param progName base name of program
     * @return {@code dict} object read in
     */
    static PyDict readResultDict(String progName) {
        String name = progName + "." + CPYTHON_VER + "." + VAR_SUFFIX;
        File f = PYC_DIR.resolve(name).toFile();
        try (
                FileInputStream fs = new FileInputStream(f);
                BufferedInputStream s = new BufferedInputStream(fs);) {

            // Wrap a marshal reader around the input stream
            marshal.Reader reader = new marshal.StreamReader(s);

            // Should be a dict object
            Object o = reader.readObject();
            if (o instanceof PyDict) {
                return (PyDict)o;
            } else {
                throw new InterpreterError("Not a dict object: %s",
                        name);
            }

        } catch (IOException ioe) {
            throw new InterpreterError(ioe);
        }
    }
}
