// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.objectweb.asm.Opcodes.ARETURN;
import static org.objectweb.asm.Opcodes.GETSTATIC;

import java.nio.file.Path;
import java.util.EnumSet;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodNode;

import uk.co.farowl.vsj4.codegen.JVM17FrameFactory;
import uk.co.farowl.vsj4.codegen.JVM17FrameFactory.FrameClassBuilder;
import uk.co.farowl.vsj4.core.JVM17Code.JVM17Layout;

/**
 * Test that we can execute frames from code objects prepared in memory
 * using a sequence of operations like those a compiler might emit.
 **/
@DisplayName("Given a JVM17 code object ...")
class JVM17FrameTest extends UnitTestSupport {

    /** Write generated classes here. (Dump with {@code javap}.) */
    // Make this null to turn of this debug output.
    private static Path DEBUG_FRAME_CLASSES = Path.of("temp");

    /** Subclass factory to use in creating subclasses. */
    // Write generated classes as files. (Dump with {@code javap}.)
    static final JVM17FrameFactory FRAME_FACTORY =
            new JVM17FrameFactory("VSJ$%s$%d", DEBUG_FRAME_CLASSES);

    /**
     * Create and execute the code object for a function that returns
     * {@code None}.
     *
     * @throws Throwable
     */
    @Test
    @SuppressWarnings("static-method")
    @DisplayName("pass")
    void exec_pass() throws Throwable {
        PyCode code = create_pass();
        assertPythonType(PyCode.TYPE, code);

        // Try using code in a function
        Interpreter interp = new Interpreter();
        PyDict globals = Py.dict();
        PyFunction func = new PyFunction(interp, code, globals);

        Object r = Callables.call(func);
        assertEquals(Py.None, r);
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

    static JVM17Code create_pass() {
        // Layout implied by this (imagined) function
        int argcount = 0, posonlyargcount = 0, kwonlyargcount = 0;
        PyTuple localsplusnames = Py.tuple();
        PyBytes localspluskinds = PyBytes.EMPTY;

        JVM17Layout layout =
                new JVM17Layout(localsplusnames, localspluskinds);

        // A builder for the frame class
        FrameClassBuilder builder = FRAME_FACTORY.createBuilder("pass",
                layout, argcount, posonlyargcount, kwonlyargcount);

        // Define the body of body()
        MethodNode body = builder.getBody();
        InsnList ins = body.instructions;

        // return Py.None
        ins.add(new FieldInsnNode(GETSTATIC, PY_CLASS_NAME, "None",
                PY_NONE_TYPE_DESCR));
        ins.add(new InsnNode(ARETURN));

        // Make a code object
        PyBytes bytecode = builder.toBytes();
        JVM17Code code = new JVM17Code("", "pass", "pass",
                EnumSet.of(CodeFlag.OPTIMIZED), bytecode, 1, NO_BYTES,
                NO_OBJECTS, NO_STRINGS, localsplusnames,
                localspluskinds, argcount, posonlyargcount,
                kwonlyargcount);

        return code;
    }

    // Plumbing ------------------------------------------------------

    static final byte[] NO_BYTES = new byte[0];
    static final String[] NO_STRINGS = new String[0];
    static final Object[] NO_OBJECTS = new Object[0];

    /**
     * The nested classes here are prototypes for the sort of class we
     * might generate by compiling Python functions, and therefore that
     * we would have to use the JVM variants of the {@code code} object
     * and {@code frame} object to execute.
     * <p>
     * Use the {@code javap} command to view the code of these classes
     * as a clue to the code that should appear as the body of the
     * {@code body()} method. For example, use the command (Windows,
     * Eclipse): <pre>
     * javap -c -cp .\rt4core\bin\test\ 'uk.co.farowl.vsj4.core.JVM17FrameTest$Proto$Pass'
     * </pre> and compare it with the file created by execution of
     * {@link JVM17FrameTest#create_pass()}.
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
            public Object call(Object v, Object w) {
                this.v = v;
                this.w = w;
                return eval();
            }
        }
    }

}
