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
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.ClassNode;
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
    private static final Method BODY_METHOD;
    static {
        try {
            BASE_CONSTRUCTOR = BASE_CLASS.getConstructor(
                    PyFunction.class, JVM17Code.class, Object.class);
            BODY_METHOD = BASE_CLASS.getMethod("body");
        } catch (NoSuchMethodException | SecurityException e) {
            throw new InterpreterError(e,
                    "Failed to initialise JVM17FrameFactory");
        }
    }

    /** A prefix used in {@link FrameClassBuilder#begin()}. */
    private final String subclassPkg;
    /** A name template used in {@link FrameClassBuilder#begin()}. */
    private final String nameTemplate;

    /** Write generated classes as files. (Dump with {@code javap}.) */
    private final Path debugPath;

    /**
     * Create a factory that creates classes in the {@code subclasses}
     * package, but also writes them to a specified directory.
     * Otherwise, exactly as {@link #JVM17FrameFactory(String)}.
     *
     * @param nameTemplate format of class names
     * @param debugPath {@code null} or directory (path) at which to
     *     write class definition files as they are created
     */
    public JVM17FrameFactory(String nameTemplate, Path debugPath) {

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
        this.nameTemplate = nameTemplate;
        logger.atInfo().setMessage("Frame class factory created for {}")
                .addArgument(subclassPkg).log();
    }

    /**
     * Create a factory that manufactures classes in the
     * {@code compiled} package, specifying a string format for
     * generating class names, requiring one string and one integer
     * (like {@code "%s_FRM%d"}). When creating a class, the name
     * generated from the simple name of the code and a meaningless
     * unique number.
     *
     * @param nameTemplate format of class names
     */
    public JVM17FrameFactory(String nameTemplate) {
        this(nameTemplate, null);
    }

    /**
     * We name each class we synthesise after its Java base type, with a
     * one-up number. This table must only be accessed when holding a
     * lock on this instance of {@code SubclassFactory}.
     */
    private final Map<String, AtomicInteger> unique = new HashMap<>();

    /**
     * Create a builder for a the frame class supporting the given
     * layout. The builder requires the client to supply the body
     * (behaviour) of the frame by by compiling Python source or
     * otherwise.
     *
     * @param name of the code object (for debugging)
     * @param layout of the required class
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only parameters and those
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only parameters (including those with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only parameters (including those with default values)
     * @return compiled code frame class
     */
    public FrameClassBuilder createBuilder(String name, Layout layout,
            int argcount, int posonlyargcount, int kwonlyargcount) {

        // Make a builder for the required class
        FrameClassBuilder builder = new FrameClassBuilder(name, layout,
                argcount, posonlyargcount, kwonlyargcount);

        // These stages are called separately for readability
        builder.begin();
        builder.addLocalVariables();
        builder.addConstructor();
        builder.addPartialBody();
        builder.addCall();

        return builder;
    }

    /**
     * We name each class we synthesise after its Java base type, with a
     * one-up number.
     *
     * @param baseName name of the base (to extend)
     * @return chosen unique name
     */
    private synchronized String uniqueName(String baseName) {
        AtomicInteger id = unique.get(baseName);
        if (id == null) {
            id = new AtomicInteger();
            unique.put(baseName, id);
        }
        int n = id.incrementAndGet();
        return String.format(nameTemplate, baseName, n);
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
        private final int argcount;
        private final int posonlyargcount;
        private final int kwonlyargcount;
        private final ClassNode cn;

        private MethodNode body;

        /**
         * Create builder from specification.
         *
         * @param name of the code object (for debugging)
         * @param layout variable names and properties, in the order
         *     {@code co_varnames + co_cellvars + co_freevars} but
         *     without repetition.
         * @param argcount {@code co_argcount} the number of positional
         *     parameters (including positional-only parameters and
         *     those with default values)
         * @param posonlyargcount {@code co_posonlyargcount} the number
         *     of positional-only parameters (including those with
         *     default values)
         * @param kwonlyargcount {@code co_kwonlyargcount} the number of
         *     keyword-only parameters (including those with default
         *     values)
         */
        FrameClassBuilder(String name, Layout layout, int argcount,
                int posonlyargcount, int kwonlyargcount) {
            this.name = name;
            this.layout = layout;
            this.argcount = argcount;
            this.posonlyargcount = posonlyargcount;
            this.kwonlyargcount = kwonlyargcount;

            this.cn = new ClassNode();

            logger.atDebug().setMessage("Creating frame for {}")
                    .addArgument(name).log();
        }

        /**
         * Return the body method as an ASM {@code MethodNode}
         * describing the implementation of {@link PyFrame}. This is the
         * only method into which the user should insert code. The body
         * method
         *
         * @return the body node
         */
        public MethodNode getBody() { return body; }

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
         * Add fields to store the local variables referenced by the
         * Python code. All the fields have type {@code Object} or
         * {@link PyCell}, according to the types in {@link #layout}.
         */
        void addLocalVariables() {
            // TODO add local variable fields
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
         * Add a field according to the specification given.
         *
         * @param field specifying the field
         */
        private void addField(FieldDescr field) {
            cn.fields.add(new FieldNode(field.access, field.name,
                    field.descr, null, null));
        }

        /**
         * Add a field with {@code Object} type as needed for a local
         * variable (named in {@link #layout}). The field has private
         * access so that only the variable handles generated by the
         * class itself may be used to access them.
         *
         * @param name of the field to create
         */
        private void addObjectAttr(String name) {
            // Add a field
            FieldDescr field = new FieldDescr(name, Object.class);
            cn.fields.add(new FieldNode(ACC_PRIVATE, field.name,
                    field.descr, null, null));
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
         * Add a method that will call {@code eval()} with exactly the
         * right parameters filled in by a fast path.
         */
        void addCall() {
            // FIXME Is this the right signature?
            String descr = CALL_DESCR[argcount];

            MethodNode call = new MethodNode(ACC_PUBLIC, "call", descr,
                    null, BODY_EXCEPTIONS);
        }

        /** Final actions on the builder before */
        void end() {
            // Finish static section
            // InsnList ins = staticInit.instructions;
            // ins.add(new InsnNode(RETURN));
        }

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

        /**
         * CALL_DESCR[n] is the JVM descriptor for a
         * {@link FastCall#call(Object, Object, Object, Object)
         * FastCall.call} with {@code n} positional parameters.
         */
        private static final String[] CALL_DESCR = {callDescr(0),
                callDescr(1), callDescr(1), callDescr(1)};
        private static final String[] CALL_EXCEPTIONS =
                new String[] {ARGUMENT_ERROR_NAME, THROWABLE_NAME};

        /**
         * Compose the method descriptor string for a method with
         * {@code n} parameters of type {@code Object}, returning
         * {@code Object}.
         *
         * @param n number of parameters
         * @return the descriptor
         */
        private static String callDescr(int n) {
            final String obj = "Ljava/lang/Object;";
            return "(" + obj.repeat(n) + ")" + obj;
        }
    }
}
