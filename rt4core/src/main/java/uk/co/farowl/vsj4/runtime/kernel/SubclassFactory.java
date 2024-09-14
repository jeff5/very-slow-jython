package uk.co.farowl.vsj4.runtime.kernel;

import static org.objectweb.asm.Opcodes.*;
import static uk.co.farowl.vsj4.runtime.ClassShorthand.DICT;
import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;

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

    /** A prefix used in {@link SubclassBuilder#begin()}. */
    private final String packagePart;
    /** A name template used in {@link SubclassBuilder#begin()}. */
    private final String subclassNameTemplate;

    /**
     * Create a factory with specific package (a dotted string like
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
     * @return Java byte code representing the class
     */
    // XXX Should we return a class? Created through what loader?
    // We think such a subclass does not belong to an interpreter.
    // We think such a subclass cannot belong to a user package.
    byte[] findOrCreateSubclass(RepresentationSpec spec) {
        // FIXME Currently we always create a new one.
        SubclassBuilder sw = new SubclassBuilder(spec);
        sw.build();
        return sw.toByteArray();
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

    static class FieldDescr {
        final String name;
        final String descr;

        FieldDescr(String name, Class<?> type) {
            this.name = name;
            this.descr = Type.getType(type).getDescriptor();
        }
    }

    static class MethodDescr {
        final String name;
        final String descr;

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
            this.spec =
                    spec.setName(uniqueName(baseClass.getSimpleName()))
                            .freeze();
        }

        /** */
        void build() {
            begin();
            addConstructor();
            addGetSet(TYPE_FIELD, GET_TYPE, SET_TYPE, CHECK_TYPE);
            if (spec.hasDict()) {
                addGetSet(DICT_FIELD, GET_DICT, SET_DICT, CHECK_DICT);

                }
            if (spec.hasSlots()) {
                for (String name : spec.getSlots()) {
                    addObjectAttr(name);
                }
            }
            end();
        }

        /** */
        byte[] toByteArray() {
            ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS
                    | ClassWriter.COMPUTE_FRAMES);
            cn.accept(cw);
            return cw.toByteArray();
        }

        /** */
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


        /** */
        void addGetSet(FieldDescr field, MethodDescr getMethod,
                MethodDescr setMethod, MethodDescr checkMethod) {
            // Add a field
            cn.fields.add(new FieldNode(ACC_PRIVATE, field.name,
                    field.descr, null, null));
            cn.methods.add(getter(field, getMethod));
            cn.methods.add(setter(field, setMethod, checkMethod));
        }

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

        /** */
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
        void addHasDictInterface() {

        }

        /** */
        void addObjectAttr(String name) {

        }

        /** */
        void end() {

        }
    }
}
