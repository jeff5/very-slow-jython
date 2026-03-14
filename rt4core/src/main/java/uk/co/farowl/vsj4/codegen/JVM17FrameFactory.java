// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.codegen;

import static org.objectweb.asm.Opcodes.*;

import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.FieldNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.compiled.CompiledClasses;
import uk.co.farowl.vsj4.core.ArgumentError;
import uk.co.farowl.vsj4.core.JVM17Code;
import uk.co.farowl.vsj4.core.JVM17Frame;
import uk.co.farowl.vsj4.core.PyBytes;
import uk.co.farowl.vsj4.core.PyCell;
import uk.co.farowl.vsj4.core.PyCode.Layout;
import uk.co.farowl.vsj4.core.PyFrame;
import uk.co.farowl.vsj4.core.PyFunction;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.types.FastCall;

/**
 * A helper to the compiler that is responsible for the JVM byte code
 * class definitions held in the code objects of function, class and
 * module bodies defined in Python. The classes defined are all
 * subclasses of {@link JVM17Frame}.
 */
public class JVM17FrameFactory {

    /** Logger for the frame class factory. */
    final Logger logger =
            LoggerFactory.getLogger(JVM17FrameFactory.class);

    /**
     * Lookup object allowing package-level access to generated classes.
     */
    static final Lookup LOOKUP = MethodHandles.lookup();

    private static final Class<?> BASE_CLASS = JVM17Frame.class;

    private static final Constructor<?> BASE_CONSTRUCTOR;
    private static final Method BODY_METHOD, EVAL_METHOD;
    static {
        try {
            BASE_CONSTRUCTOR = BASE_CLASS.getConstructor(
                    PyFunction.class, JVM17Code.class, Object.class);
            BODY_METHOD = BASE_CLASS.getMethod("body");
            EVAL_METHOD = BASE_CLASS.getMethod("eval");
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InterpreterError(e,
                    "Failed to initialise JVM17FrameFactory");
        }
    }

    /** A prefix used in {@link FrameClassBuilder#begin()}. */
    private final String subclassPkg;

    /** Write generated classes as files. (Dump with {@code javap}.) */
    private final Path debugPath;

    /**
     * Create a factory that creates classes in the {@code subclasses}
     * package, but also writes them to a specified directory.
     * Otherwise, exactly as {@link #JVM17FrameFactory()}.
     *
     * @param debugPath {@code null} or directory (path) at which to
     *     write class definition files as they are created
     */
    public JVM17FrameFactory(Path debugPath) {

        // Convert package name for ASM: org/python/runtime/compiled/
        String[] parts =
                CompiledClasses.class.getPackageName().split("\\.");
        this.subclassPkg = String.join("/", parts) + "/";

        // Where to write class definition files for examination
        if (debugPath != null) {
            File dir = debugPath.toFile();
            if (!dir.isDirectory()) { dir.mkdirs(); }
        }
        this.debugPath = debugPath;

        // Pattern for class names
        logger.atInfo().setMessage("Frame class factory created for {}")
                .addArgument(subclassPkg).log();
    }

    /**
     * Create a factory that manufactures classes in the
     * {@code compiled} package. When generating a class, the class name
     * is the simple name of the code.
     */
    public JVM17FrameFactory() { this(null); }

    /**
     * Create a builder for a the frame class supporting the given
     * layout. The builder requires the client to supply the body
     * (behaviour) of the frame by compiling Python source or otherwise.
     *
     * @param name of the class class (and code object)
     * @param layout of the required class
     * @return compiled code frame class
     */
    public FrameClassBuilder createBuilder(String name, Layout layout) {

        // Make a builder for the required class
        FrameClassBuilder builder = new FrameClassBuilder(name, layout);
        int nargs = layout.argcount(), nkw = layout.kwonlyargcount();

        // These stages are called separately for readability
        builder.begin();
        builder.addLocalVariables();
        builder.addConstructor();
        builder.addPartialBody();

        if (nkw == 0 && nargs <= FastCall.MAX_POSITIONAL) {
            // FastCall support is possible
            builder.addCall(nargs);
        }

        return builder;
    }

    /** Description of a field with elements in internal format. */
    static class FieldDescr {
        /** Access mode (private, static, etc.). */
        final int access;
        /** Name of field. */
        final String name;
        /** Descriptor of field in internal format. */
        final String descr;

        /**
         * Create from name and type.
         *
         * @param access access flags of the fields
         * @param name of field
         * @param type of field
         */
        FieldDescr(int access, String name, Class<?> type) {
            this.access = access;
            this.name = name;
            this.descr = Type.getType(type).getDescriptor();
        }

        /**
         * Create private instance field from name and type.
         *
         * @param name of field
         * @param type of field
         */
        FieldDescr(String name, Class<?> type) {
            this(ACC_PRIVATE, name, type);
        }
    }

    /** Description of a method with elements in internal format. */
    static class MethodDescr {
        /** Name of method. */
        final String name;
        /** Descriptor of method in internal format. */
        final String descr;

        /**
         * Create from name and type.
         *
         * @param c class in which named method is found
         * @param name of method
         * @param args types of arguments
         */
        MethodDescr(Class<?> c, String name, Class<?>... args) {
            this.name = name;
            try {
                this.descr = Type.getType(c.getMethod(name, args))
                        .getDescriptor();
            } catch (NoSuchMethodException | SecurityException e) {
                // Should never happen.
                throw new InterpreterError(e, "reflecting %s.%s",
                        c.getName(), name);
            }
        }
    }

    /** A builder object for one subclass from a given specification. */
    public class FrameClassBuilder {

        private final String name;
        private final Layout layout;
        private final ClassNode cn;

        private MethodNode body;

        /**
         * Create builder from specification.
         *
         * @param name of the code object (for debugging)
         * @param layout frame variable names and properties, in the
         *     order {@code co_varnames + co_cellvars + co_freevars} but
         *     without repetition.
         */
        FrameClassBuilder(String name, Layout layout) {
            this.name = name;
            this.layout = layout;
            this.cn = new ClassNode();
            logger.atDebug().setMessage("Creating frame class for {}")
                    .addArgument(name).log();
        }

        @Override
        public String toString() {
            return String.format("%s%s->%s", name, layout, cn.name);
        }

        /**
         * Return the body method as an ASM {@code MethodNode}
         * describing the implementation of {@link PyFrame}. This is the
         * only method into which the client should insert code. The
         * body method defined the behaviour of the corresponding code
         * object.
         *
         * @return the body node
         */
        public MethodNode getBody() { return body; }

        /**
         * Return the internal name of class being built.
         *
         * @return the name
         */
        public String getFrameClassName() { return cn.name; }

        /**
         * Return the class being built method as an ASM
         * {@code ClassNode} a sub-class of {@link PyFrame}. The client
         * should probably only use this to access attributes.
         * Manipulating the class under construction may have unexpected
         * results.
         *
         * @return the body node
         */
        public ClassNode getFrameClass() { return cn; }

        /**
         * Get the class definition as a JVM byte code file in a
         * {@link PyBytes}.
         *
         * @return the class definition
         */
        public PyBytes toBytes() {
            // Final actions/checks
            end();

            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                    | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            byte[] bytecode = cw.toByteArray();

            if (debugPath != null) {
                // Write so we can dump it later.
                Path fp = debugPath.resolve(name + ".class");
                try {
                    Files.write(fp, bytecode);
                    logger.atTrace().setMessage("Wrote {}")
                            .addArgument(fp).log();
                } catch (IOException e) {
                    logger.atWarn().setMessage("Failed to write {}")
                            .addArgument(fp).log();
                }
            }

            return new PyBytes(bytecode);
        }

        /**
         * Begin the class definition with a version and a name based on
         * the base Java class that this class extends. This base class
         * will be the canonical representation of the "solid base".
         */
        void begin() {
            cn.version = V17;
            cn.access = 0;
            cn.name = subclassPkg + name;
            cn.superName = BASE_CLASS_NAME;
        }

        /**
         * Synthesise a constructor for this class. The signature is
         * always the same as the base {@link JVM17Frame}, and we simply
         * pass on the arguments unchanged.
         */
        void addConstructor() {

            // The parameter types are those of the base constructor
            MethodNode mn = new MethodNode(ACC_PUBLIC, "<init>",
                    BASE_CONSTRUCTOR_DESCR, null, null);

            // Build the constructor code T(this, func, code, locals)
            InsnList ins = mn.instructions;

            // Before any parameter comes 'this'
            ins.add(new VarInsnNode(ALOAD, 0));
            // Then 'func, code, locals'.
            ins.add(new VarInsnNode(ALOAD, 1));
            ins.add(new VarInsnNode(ALOAD, 2));
            ins.add(new VarInsnNode(ALOAD, 3));

            // Call super(func, code, locals).
            ins.add(new MethodInsnNode(INVOKESPECIAL, BASE_CLASS_NAME,
                    "<init>", BASE_CONSTRUCTOR_DESCR));

            ins.add(new InsnNode(RETURN));

            cn.methods.add(mn);
        }

        /**
         * Add fields to store the local variables referenced by the
         * Python code. All the fields have type {@code Object} or
         * {@link PyCell}, according to the types in {@link #layout}.
         */
        void addLocalVariables() {
            int n = layout.size();
            for (int index = 0; index < n; index++) {
                String name = layout.name(index);
                if (layout.isCellOrFree(index)) {
                    // This should be a PyCell
                    addCellField(name);
                } else {
                    // This should be a plain Object
                    addObjectField(name);
                }
            }
        }

        /**
         * Add a field with {@code Object} type as needed for a local
         * variable (named in {@link #layout}). The field has private
         * access so that only the variable handles generated by the
         * class itself may be used to access them.
         *
         * @param name of the field to create
         */
        private void addObjectField(String name) {
            cn.fields.add(new FieldNode(ACC_PRIVATE, name,
                    OBJECT_CLASS_DESCR, null, null));
        }

        /**
         * Add a field with {@code PyCell} type as needed for a local
         * variable (named in {@link #layout}). The field has private
         * access so that only the variable handles generated by the
         * class itself may be used to access them.
         *
         * @param name of the field to create
         */
        private void addCellField(String name) {
            cn.fields.add(new FieldNode(ACC_PRIVATE, name,
                    PY_CELL_CLASS_DESCR, null, null));
        }

        /**
         * Partially create the body method (overriding
         * {@link JVM17Frame#body()}) leaving the instruction sequence
         * for the client to supply,
         */
        void addPartialBody() {
            body = new MethodNode(ACC_PUBLIC, "body", BODY_DESCR, null,
                    BODY_EXCEPTIONS);

            cn.methods.add(body);
        }

        /**
         * Add a method that will populate the frame with exactly the
         * right parameters, filled in by a fast path, and will then
         * call {@code eval()} directly. We only make one of these when
         * the code is for a function with a fixed (small) number
         * parameters that may be given by position, and the function
         * will only call it when exactly that many arguments have been
         * supplied by position.
         *
         * @param n the number of parameters
         */
        void addCall(int n) {
            // Create a descriptor for a method with n Object params
            StringBuilder descr = new StringBuilder(200);
            descr.append('(');
            for (int i = 0; i < n; i++) {
                descr.append(OBJECT_CLASS_DESCR);
            }
            descr.append(')').append(OBJECT_CLASS_DESCR);

            // The method itself is sets first n fields by name
            MethodNode call = new MethodNode(ACC_PUBLIC, "call",
                    descr.toString(), null, CALL_EXCEPTIONS);
            InsnList ins = call.instructions;
            ins.add(new VarInsnNode(ALOAD, 0));
            for (int i = 0; i < n; i++) {
                ins.add(new InsnNode(DUP)); // this
                ins.add(new VarInsnNode(ALOAD, i + 1)); // i.th arg
                FieldNode f = cn.fields.get(i); // i.th local variable
                ins.add(new FieldInsnNode(PUTFIELD, cn.name, f.name,
                        OBJECT_CLASS_DESCR));
            }
            // stack = [this]

            // return eval();
            ins.add(new MethodInsnNode(INVOKEVIRTUAL, BASE_CLASS_NAME,
                    "eval", EVAL_DESCR));
            ins.add(new InsnNode(ARETURN));

            // Add the call(a1...an) method to the class
            cn.methods.add(call);
        }

        /**
         * Final actions on the builder before we create a class
         * definition (bytes).
         */
        void end() {
            // Finish static section?
            // Anything else?
        }

        private static final String OBJECT_CLASS_NAME =
                Type.getInternalName(Object.class);
        private static final String OBJECT_CLASS_DESCR =
                Type.getDescriptor(Object.class);

        private static final String PY_CELL_CLASS_NAME =
                Type.getInternalName(PyCell.class);
        private static final String PY_CELL_CLASS_DESCR =
                Type.getDescriptor(PyCell.class);

        private static final String THROWABLE_NAME =
                Type.getInternalName(Throwable.class);
        private static final String ARGUMENT_ERROR_NAME =
                Type.getInternalName(ArgumentError.class);
        private static final String BASE_CLASS_NAME =
                Type.getInternalName(BASE_CLASS);

        private static final String BASE_CONSTRUCTOR_DESCR =
                Type.getConstructorDescriptor(BASE_CONSTRUCTOR);

        private static final String BODY_DESCR =
                Type.getMethodDescriptor(BODY_METHOD);
        private static final String[] BODY_EXCEPTIONS =
                new String[] {THROWABLE_NAME};

        private static final String EVAL_DESCR =
                Type.getMethodDescriptor(EVAL_METHOD);

        private static final String[] CALL_EXCEPTIONS =
                new String[] {ARGUMENT_ERROR_NAME, THROWABLE_NAME};
    }
}
