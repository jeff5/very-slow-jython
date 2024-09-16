package uk.co.farowl.vsj4.runtime.kernel;

import static org.objectweb.asm.Opcodes.*;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

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

import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithClassAssignment;
import uk.co.farowl.vsj4.runtime.WithDictAssignment;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A helper to the {@link TypeFactory} that is responsible for the
 * synthetic classes we use to represent subclasses defined in Python.
 */
class SubclassFactory {

    /** Write generated classes as files. (Dump with {@code javap}.) */
    private static boolean DEBUG_CLASSFILES = true;

    /** Logger for the subclass factory. */
    final Logger logger =
            LoggerFactory.getLogger(SubclassFactory.class);

    /** A prefix used in {@link SubclassBuilder#begin()}. */
    private final String packagePart;
    /** A name template used in {@link SubclassBuilder#begin()}. */
    private final String subclassNameTemplate;

    /**
     * The classes created in this factory by spec. We do not use a weak
     * reference here because the loader (which is part of class
     * identity) keeps the classes alive anyway.
     */
    private final Map<RepresentationSpec, Class<?>> subclasses;
    /** Minimum number of distinct specifications to expect. */
    private static final int CLASSES_MAP_SIZE = 100;

    /** {@code ClassLoader} for the factory. */
    static class SubclassLoader extends ClassLoader {
        public Class<?> defineClass(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }

    /** {@code ClassLoader} for the tests. */
    final SubclassLoader loader;

    /**
     * /** Create a factory with specific package (a dotted string like
     * {@code "org.python.subclasses"}) and a string format for
     * generating class names, requiring one string and one integer
     * (like {@code "JY$%s$%d"}). When creating a will be the simple
     * name of the base class=
     *
     * @param subclassesPackage dotted package name
     * @param subclassNameTemplate format of class names
     */
    SubclassFactory(String subclassesPackage,
            String subclassNameTemplate) {
        // Convert package name for ASM: org/python/subclasses/
        this.packagePart = subclassesPackage.replace('.', '/') + "/";
        this.subclassNameTemplate = subclassNameTemplate;
        this.subclasses = new HashMap<>(CLASSES_MAP_SIZE * 2);
        this.loader = new SubclassLoader();
        logger.atInfo().setMessage("Subclass factory created for {}")
                .addArgument(subclassesPackage).log();
    }

    /**
     * We name each class we synthesise after its Java base type, with a
     * one-up number. This table must only be accessed when holding a
     * lock on this instance of {@code SubclassFactory}.
     */
    private final Map<String, AtomicInteger> unique = new HashMap<>();

    /**
     * Find a class (created by a previous call), or create a class now,
     * that matches the specification for base, interfaces, slots and
     * possession of a {@code __dict__} member.
     *
     * @param spec of the required class
     * @return Java class representing the Python class described
     */
    synchronized Class<?>
            findOrCreateSubclass(RepresentationSpec spec) {
        Class<?> c = subclasses.get(spec);
        if (c == null) {
            // Make a class
            SubclassBuilder sw = new SubclassBuilder(spec);
            sw.build();
            byte[] b = sw.toByteArray();

            if (DEBUG_CLASSFILES) {
                // Write so we can dump it later.
                String fn = spec.getName() + ".class";
                try (OutputStream f = new FileOutputStream(fn)) {
                    f.write(b);
                } catch (IOException e) {
                    throw new InterpreterError(e, "writing class file");
                }
            }

            // Create (and cache) the representation class
            c = loader.defineClass(b);
            subclasses.put(spec, c);
        }
        return c;
    }

    /**
     * We name each class we synthesise after its Java base type, with a
     * one-up number.
     */
    synchronized String uniqueName(String baseName) {
        AtomicInteger id = unique.get(baseName);
        if (id == null) {
            id = new AtomicInteger();
            unique.put(baseName, id);
        }
        int n = id.incrementAndGet();
        return String.format(subclassNameTemplate, baseName, n);
    }

    /** Description of a field with elements in internal format. */
    static class FieldDescr {
        /** Name of field. */
        final String name;
        /** Descriptor of field in internal format. */
        final String descr;

        /**
         * Create from name and type.
         *
         * @param name of field
         * @param type of field
         */
        FieldDescr(String name, Class<?> type) {
            this.name = name;
            this.descr = Type.getType(type).getDescriptor();
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

    // Field for __class__ and methods from WithClassAssignment.

    private static final FieldDescr TYPE_FIELD =
            new FieldDescr("$type", PyType.class);
    private static final MethodDescr GET_TYPE =
            new MethodDescr(WithClassAssignment.class, "getType");
    private static final MethodDescr SET_TYPE = new MethodDescr(
            WithClassAssignment.class, "setType", Object.class);
    private static final MethodDescr CHECK_TYPE =
            new MethodDescr(WithClassAssignment.class,
                    "checkClassAssignment", Object.class);

    // Field for __dict__ and methods from WithDictAssignment.
    private static final FieldDescr DICT_FIELD =
            new FieldDescr("$dict", PyDict.class);
    private static final MethodDescr GET_DICT =
            new MethodDescr(WithDictAssignment.class, "getDict");
    private static final MethodDescr SET_DICT = new MethodDescr(
            WithDictAssignment.class, "setDict", Object.class);
    private static final MethodDescr CHECK_DICT =
            new MethodDescr(WithDictAssignment.class,
                    "checkDictAssignment", Object.class);

    /** A builder object for one subclass from a given specification. */
    class SubclassBuilder {

        final RepresentationSpec spec;
        private final Class<?> baseClass;
        private final ClassNode cn;

        /**
         * Create builder from specification.
         *
         * @param spec to follow
         */
        SubclassBuilder(RepresentationSpec spec) {
            this.cn = new ClassNode();
            this.baseClass = spec.getBase();
            // Get a new unique class name according to factory + spec
            String name = uniqueName(baseClass.getSimpleName());
            this.spec = spec.setName(name).freeze();
            logger.atDebug().setMessage("Creating class for spec {}")
                    .addArgument(spec).log();
        }

        /**
         * Build the internal representation of the class being
         * specified.
         */
        void build() {
            begin();
            addConstructor();
            addGetSet(TYPE_FIELD, GET_TYPE, SET_TYPE, CHECK_TYPE);
            if (spec.hasDict()) {
                addGetSet(DICT_FIELD, GET_DICT, SET_DICT, CHECK_DICT);

            }
            for (String name : spec.getSlots()) { addObjectAttr(name); }
            end();
        }

        /**
         * Get the class definition as a JVM byte code file.
         *
         * @return the class definition
         */
        byte[] toByteArray() {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                    | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        }

        /**
         * Begin the class definition with a version and a name based on
         * the base Java class that this class extends. This base class
         * will be the canonical representation of the "solid base".
         */
        void begin() {
            cn.version = V17;
            cn.access = 0;
            cn.name = packagePart + spec.getName();
            cn.superName = Type.getInternalName(baseClass);
            for (Class<?> c : spec.getInterfaces()) {
                cn.interfaces.add(Type.getInternalName(c));
            }
        }

        /** */
        void addConstructor() {
            // MethodNode cons = new MethodNode(V17, CONS_DESCR, null,
            // null, null);
            // cn.methods.add(cons);
        }

        /**
         * Add a field with get and set methods as described and a
         * checked cast used when setting.
         *
         * @param field describing the field to create
         * @param getMethod describing the getter
         * @param setMethod describing the setter
         * @param checkMethod describing the checked cast
         */
        void addGetSet(FieldDescr field, MethodDescr getMethod,
                MethodDescr setMethod, MethodDescr checkMethod) {
            // Add a field
            cn.fields.add(new FieldNode(ACC_PRIVATE, field.name,
                    field.descr, null, null));
            cn.methods.add(getter(field, getMethod));
            cn.methods.add(setter(field, setMethod, checkMethod));
        }

        /**
         * Add a getter for the named field.
         *
         * @param field to get
         * @param method descriptor for the method to create
         * @return method for the get operation
         */
        MethodNode getter(FieldDescr field, MethodDescr method) {
            MethodNode mn = new MethodNode(ACC_PUBLIC, method.name,
                    method.descr, null, null);
            InsnList ins = mn.instructions;
            // Return the __class__ attribute
            ins.add(new VarInsnNode(ALOAD, 0));
            ins.add(new FieldInsnNode(GETFIELD, cn.name, field.name,
                    field.descr));
            ins.add(new InsnNode(ARETURN));
            return mn;
        }

        /**
         * Add a setter for the named field, using the checked cast
         * method to disallow values that would make this not a valid
         * representation class for the clique of mutually-replaceable
         * types.
         *
         * @param field to set
         * @param method descriptor for the method to create
         * @param check descriptor for the checked cast method
         * @return method for the set operation
         */
        MethodNode setter(FieldDescr field, MethodDescr method,
                MethodDescr check) {
            MethodNode mn = new MethodNode(ACC_PUBLIC, method.name,
                    method.descr, null, null);
            InsnList ins = mn.instructions;
            ins.add(new VarInsnNode(ALOAD, 0));
            ins.add(new VarInsnNode(ALOAD, 0));
            // Get the purported new value for __dict__
            ins.add(new VarInsnNode(ALOAD, 1));
            // Cast to PyDict with check (may throw)
            ins.add(new MethodInsnNode(INVOKEVIRTUAL, cn.name,
                    check.name, check.descr));
            // Assign to the __dict__ attribute
            ins.add(new FieldInsnNode(PUTFIELD, cn.name, field.name,
                    field.descr));
            ins.add(new InsnNode(RETURN));
            return mn;
        }

        /** */
        void addWithDictInterface() {

        }

        /**
         * Add a field with {@code Object} type as needed for a slot
         * (named in {@code __slots__}). The field has default (package)
         * access so that a package-level lookup may be used to generate
         * handles.
         *
         * @param field describing the field to create
         */
        void addObjectAttr(String name) {
            // Add a field (with package access)
            FieldDescr field = new FieldDescr(name, Object.class);
            cn.fields.add(new FieldNode(0, field.name, field.descr,
                    null, null));
        }

        /** */
        void end() {

        }
    }
}
