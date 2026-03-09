// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.objectweb.asm.Opcodes.ALOAD;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.GETFIELD;
import static org.objectweb.asm.Opcodes.GETSTATIC;

import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.VarInsnNode;

import uk.co.farowl.vsj4.codegen.JVM17FrameFactory;
import uk.co.farowl.vsj4.codegen.JVM17FrameFactory.FrameClassBuilder;

/**
 * This is a test that we can execute frames created by code objects,
 * themselves prepared in memory using operations like those a compiler
 * might emit. We test the implementation of the code-function-frame
 * architectural pattern for JVM code. In a limited way we also test our
 * understanding of code generation with ASM, such as will be necessary
 * in a compiler.
 * <p>
 * The test was written to drive development of {@link JVM17Frame},
 * {@link JVM17FrameFactory} and {@link JVM17Code}. A lot of
 * JVM-specific apparatus is needed that we cannot simply mimic from
 * CPython.
 * <p>
 * We shall create a few code objects, of increasing complexity, and for
 * each of them create a function object (for the function that might
 * have given rise to that code). Then we shall call that function.
 * <p>
 * As each {@code code} object has its own peculiarities and expected
 * results, we define a base test containing (or at minimum naming) the
 * tests we expect to make on each code object, and common support for
 * those tests. Each code object then has its own specialisation as a
 * nested concrete test class that runs all the tests.
 *
 **/
@DisplayName("Given a JVM17 code object ...")
class JVM17FrameTest extends UnitTestSupport {

    /** Write generated classes here. (Dump with {@code javap}.) */
    // Make this null to turn of this debug output.
    private static Path DEBUG_FRAME_CLASSES = Path.of("temp");

    /** Subclass factory to use in creating subclasses. */
    // Write generated classes as files. (Dump with {@code javap}.)
    static final JVM17FrameFactory FRAME_FACTORY =
            new JVM17FrameFactory(DEBUG_FRAME_CLASSES);

    /**
     * Certain nested test classes implement these as standard. A base
     * class here is just a way to describe the tests once that reappear
     * in each nested case.
     */
    abstract static class BaseTest {

        /**
         * A signature for a function that uses the code object. There
         * is a lot of commonality between the layout of a code object
         * and an argument parser for the notional function that uses
         * it. We exploit this to express structure succinctly in tests.
         */
        abstract ArgParser parser();

        /** The code object in this test. */
        abstract JVM17Code code();

        /**
         * Check for a selection of the expected {@code co_*}
         * attributes. This is achieved by a comparison with a parser,
         * computed from a function pseudo-signature.
         *
         * @throws Throwable unexpectedly
         */
        @Test
        void has_expected_code_attributes() throws Throwable {
            ArgParser parser = parser();
            JVM17Code code = code();
            assertPythonType(PyCode.TYPE, code);
            String name = parser.name;
            assertEquals(name, Abstract.getAttr(code, "co_name"));
            assertEquals(name, Abstract.getAttr(code, "co_qualname"));

            assertEquals(parser.argcount,
                    Abstract.getAttr(code, "co_argcount"));
            assertEquals(parser.posonlyargcount,
                    Abstract.getAttr(code, "co_posonlyargcount"));
            assertEquals(parser.kwonlyargcount,
                    Abstract.getAttr(code, "co_kwonlyargcount"));
            assertEquals(parser.argnames.length,
                    Abstract.getAttr(code, "co_nlocals"));

            assertEquals(PyTuple.from(parser.argnames),
                    Abstract.getAttr(code, "co_varnames"));
            assertEquals(Py.tuple(),
                    Abstract.getAttr(code, "co_cellvars"));
            assertEquals(Py.tuple(),
                    Abstract.getAttr(code, "co_freevars"));
        }

        /**
         * Create and execute the code object as a function and test the
         * return value is as expected.
         *
         * @throws Throwable unexpectedly
         */
        abstract void returns_expected_value() throws Throwable;
    }

    /**
     * A bundle of elements, derived mostly from a parser that each test
     * creates as a shorthand description of the frame object. However,
     * a parser only partially specifies the code object. The missing
     * part, which every test must also supply, consists of the JVM byte
     * code for the {@code body()} method.
     */
    record TestSetup(Layout311 layout, FrameClassBuilder builder,
            EnumSet<CodeFlag> flags) {}

    /**
     * Create a {@link Layout311} and a {@link FrameClassBuilder},
     * matching the given parser, and optionally declare some of the
     * names to be cell or free. The names given when the parser is
     * constructed may include non-parameter local variables, following
     * the parameters.
     *
     * @param parser giving the function signature
     * @param cell these names are defined in this scope and referenced
     *     from an inner scope
     * @param free these names are defined in an outer scope and
     *     referenced from this scope.
     * @return the layout
     */
    static TestSetup setupFromParser(ArgParser parser,
            List<String> cell, List<String> free) {

        // Make localsplusnames from (all) the parser names
        PyTuple localsplusnames = PyTuple.from(parser.argnames);
        int n = localsplusnames.size();

        // localspluskinds is trickier: need cell and free info
        if (cell == null) { cell = Collections.emptyList(); }
        if (free == null) { free = Collections.emptyList(); }

        int[] kinds = new int[n];

        for (int i = 0; i < n; i++) {
            String name = parser.argnames[i];
            if (cell.contains(name)) {
                kinds[i] = Layout311.CO_FAST_CELL;
            } else if (free.contains(name)) {
                kinds[i] = Layout311.CO_FAST_FREE;
            } else {
                kinds[i] = Layout311.CO_FAST_LOCAL;
            }
        }
        PyBytes localspluskinds = new PyBytes(kinds);

        // The flags must reflect the use of collector args
        EnumSet<CodeFlag> flags = EnumSet.of(CodeFlag.OPTIMIZED);
        if (parser.hasVarArgs()) { flags.add(CodeFlag.VARARGS); }
        if (parser.hasVarKeywords()) {
            flags.add(CodeFlag.VARKEYWORDS);
        }

        // We can now construct the layout
        Layout311 layout = new Layout311(localsplusnames,
                localspluskinds, parser.argcount,
                parser.posonlyargcount, parser.kwonlyargcount, flags);

        // And with the layout, we can construct a builder
        FrameClassBuilder builder =
                FRAME_FACTORY.createBuilder(parser.name, layout);

        // Now return a bundle of these pieces needed by the test.
        return new TestSetup(layout, builder, flags);
    }

    /**
     * Create a {@link Layout311} and a {@link FrameClassBuilder},
     * matching the given parser.
     *
     * @param parser giving the function signature
     * @return the layout
     */
    static TestSetup setupFromParser(ArgParser parser) {
        return setupFromParser(parser, null, null);
    }

    @Nested
    @DisplayName("with no body")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class TestNoBody extends BaseTest {

        private static ArgParser PARSER =
                ArgParser.fromSignature("no_body", NO_STRINGS);
        private static JVM17Code CODE;

        @Override
        ArgParser parser() { return PARSER; }

        @Override
        JVM17Code code() { return CODE; }

        @BeforeAll
        static void setCode() {
            TestSetup ts = setupFromParser(PARSER);
            FrameClassBuilder builder = ts.builder;

            // Define the instructions in the body of body()
            InsnList ins = builder.getBody().instructions;

            // -------------------------------------------------------
            // return Py.None
            ins.add(new FieldInsnNode(GETSTATIC, PY_CLASS_NAME, "None",
                    PY_NONE_TYPE_DESCR));
            ins.add(new InsnNode(ARETURN));
            // -------------------------------------------------------

            // Make a code object from completed class definition
            CODE = new JVM17Code("<test>", PARSER.name, PARSER.name,
                    ts.flags, builder.toBytes(), 1, NO_BYTES,
                    NO_OBJECTS, NO_STRINGS, ts.layout);
        }

        @Test
        @Override
        void returns_expected_value() throws Throwable {
            // Try using code in a function
            Interpreter interp = new Interpreter();
            PyDict globals = Py.dict();
            PyFunction func = new PyFunction(interp, code(), globals);

            Object r = Callables.call(func);
            assertEquals(Py.None, r);
        }
    }

    @Nested
    @DisplayName("that returns its argument")
    @DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
    class TestReturnArgument extends BaseTest {
        private static ArgParser PARSER =
                ArgParser.fromSignature("return_argument", "v");
        private static JVM17Code CODE;

        @Override
        ArgParser parser() { return PARSER; }

        @Override
        JVM17Code code() { return CODE; }

        @BeforeAll
        static void setCode() {
            // Create frame class based on the parser
            TestSetup ts = setupFromParser(PARSER);
            FrameClassBuilder builder = ts.builder;

            // Define the instructions in the body of body()
            InsnList ins = builder.getBody().instructions;

            // -------------------------------------------------------
            // return this.v
            ins.add(new VarInsnNode(ALOAD, 0));
            ins.add(new FieldInsnNode(GETFIELD,
                    builder.getFrameClassName(), "v",
                    OBJECT_CLASS_DESCR));
            ins.add(new InsnNode(ARETURN));
            // -------------------------------------------------------

            // Make a code object from completed class definition
            CODE = new JVM17Code("<test>", PARSER.name, PARSER.name,
                    ts.flags, builder.toBytes(), 1, NO_BYTES,
                    NO_OBJECTS, NO_STRINGS, ts.layout);
        }

        @Test
        @Override
        void returns_expected_value() throws Throwable {
            // Try using code in a function
            Interpreter interp = new Interpreter();
            PyDict globals = Py.dict();
            PyFunction func = new PyFunction(interp, code(), globals);

            Object r = Callables.call(func, 42);
            assertPythonEquals(42, r);
            r = Callables.call(func, "spam");
            assertPythonEquals("spam", r);
        }
    }

    // Generate JVM byte code ----------------------------------------

    /*
     * Some of this belongs in code generation classes created to
     * support the compiler, but while we don't know what we're doing,
     * it is better developed jointly with its test code.
     */
    private static final String PY_CLASS_NAME =
            Type.getInternalName(Py.class);
    private static final String PY_NONE_TYPE_DESCR =
            Type.getDescriptor(PyNone.class);
    private static final String FRAME_CLASS_NAME =
            Type.getInternalName(JVM17Frame.class);
    private static final String FRAME_CLASS_DESCR =
            Type.getDescriptor(JVM17Frame.class);
    private static final String OBJECT_CLASS_NAME =
            Type.getInternalName(Object.class);
    private static final String OBJECT_CLASS_DESCR =
            Type.getDescriptor(Object.class);

    // Plumbing ------------------------------------------------------

    static final byte[] NO_BYTES = new byte[0];
    static final String[] NO_STRINGS = new String[0];
    static final Object[] NO_OBJECTS = new Object[0];

    /**
     * The nested classes here are prototypes for the sort of class we
     * might generate by compiling Python functions to the JVM variants
     * of the {@code frame} object held in a {@code code} object.
     * <p>
     * Use the {@code javap} command to view the code of these classes
     * as a clue to the code that should appear as the body of the
     * {@code body()} method. For example, use the command (Windows,
     * Eclipse): <pre>
     * javap -c -cp .\rt4core\bin\test\ 'uk.co.farowl.vsj4.core.JVM17FrameTest$Proto$Pass'
     * </pre> and compare it with the file created by execution of
     * {@link JVM17FrameTest.TestNoBody}.
     */
    static class Proto {
        static class Pass extends JVM17Frame {
            public Pass(PyFunction func, JVM17Code code,
                    Object locals) {
                super(func, code, locals);
            }

            @Override
            public Object body() { return Py.None; }

            // Experimentally define a FastCall call.
            @Override
            public Object call() { return eval(); }
        }

        static class Argument extends JVM17Frame {
            public Argument(PyFunction func, JVM17Code code,
                    Object locals) {
                super(func, code, locals);
            }

            private Object a;

            @Override
            public Object body() { return a; }
        }

        static class Negate extends JVM17Frame {
            public Negate(PyFunction func, JVM17Code code,
                    Object locals) {
                super(func, code, locals);
            }

            private Object v;

            @Override
            public Object body() throws Throwable {
                return PyNumber.negative(v);
            }
        }

        static class Subtract extends JVM17Frame {
            public Subtract(PyFunction func, JVM17Code code,
                    Object locals) {
                super(func, code, locals);
            }

            Object v, w;

            @Override
            public Object body() throws Throwable {
                return PyNumber.subtract(v, w);
            }
        }

        static class Quartic extends JVM17Frame {
            public Quartic(PyFunction func, JVM17Code code,
                    Object locals) {
                super(func, code, locals);
            }

            Object v, w;

            @Override
            public Object body() throws Throwable {
                // v * w * (v + w) * (v - w);
                return PyNumber.multiply(v,
                        PyNumber.multiply(w,
                                PyNumber.multiply(PyNumber.add(v, w),
                                        PyNumber.subtract(v, w))));
            }

            // Experimentally define a FastCall call.
            @Override
            public Object call(Object v, Object w) {
                this.v = v;
                this.w = w;
                return eval();
            }
        }
    }

}
