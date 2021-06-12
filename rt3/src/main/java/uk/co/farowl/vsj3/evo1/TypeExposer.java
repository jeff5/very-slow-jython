package uk.co.farowl.vsj3.evo1;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.Member;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj3.evo1.PyMemberDescr.Flag;

class TypeExposer extends Exposer {

    /**
     * Type for which attributes are to be exposed (or {@code null}
     * during certain tests). It is referenced (e.g. where we create a
     * descriptor), but is not otherwise accessed, since it is
     * (necessarily) incomplete at this time.
     */
    final PyType type;

    /**
     * The table of intermediate descriptions for members. They will
     * eventually become descriptors in a built-in object type . Every
     * entry here is also a value in {@link Exposer#specs}.
     */
    final Set<MemberSpec> memberSpecs;

    /**
     * Construct the {@code TypeExposer} instance for a particular
     * Python type. The {@code type} object is referenced (e.g. in
     * intermediate specification objects), but is not otherwise
     * accessed, since it is (necessarily) incomplete at this time. It
     * will be interrogated as to its implementing classes, where we
     * create descriptors, at the point {@link #expose(Class)} is
     * called.
     *
     * @param type being exposed
     */
    TypeExposer(PyType type) {
        this.type = type;
        this.memberSpecs = new TreeSet<>();
    }

    @Override
    ScopeKind kind() {
        return ScopeKind.TYPE;
    }

    /**
     * Build the result from the defining class.
     *
     * @param definingClass to scan for definitions
     */
    void expose(Class<?> definingClass) {
        // Scan the defining class for exposed and special methods
        addMethodSpecs(definingClass);

        // ... and for fields.
        addFieldSpecs(definingClass);

        // XXX ... and for types defined in the module maybe? :o
    }

    /**
     * For each name having a definition in {@link #specs}, construct
     * the attribute and add it to the map passed in. The map is
     * normally the dictionary of the type. Attributes may rely on a
     * {@code MethodHandle} or {@code VarHandle}, so a lookup object
     * must be provided that can create them.
     *
     * @param dict to which the attributes should be delivered
     * @param lookup authorisation to access members
     */
    void populate(Map<? super String, Object> dict, Lookup lookup) {
        if (type == null)
            // type may only properly be null during certain tests
            throw new InterpreterError(
                    "Cannot generate descriptors for type 'null'");
        for (Spec spec : specs.values()) {
            Object attr = spec.asAttribute(type, lookup);
            dict.put(spec.name, attr);
        }
    }

    /**
     * Add to {@link #specs}, definitions based on methods found in the
     * given class and either annotated for exposure or having the name
     * of a special method.
     *
     * @param defsClass to introspect for methods
     * @throws InterpreterError on duplicates or unsupported types
     */
    @Override
    void addMethodSpecs(Class<?> defsClass) throws InterpreterError {

        // Iterate over methods looking for those to expose
        for (Method m : defsClass.getDeclaredMethods()) {
            /*
             * Note: method annotations (and special names) are not
             * treated as alternatives, to catch exposure of methods by
             * multiple routes.
             */

            // Check for instance method
            PythonMethod pm =
                    m.getDeclaredAnnotation(PythonMethod.class);
            if (pm != null) { addMethodSpec(m, pm); }

            // Check for static method
            PythonStaticMethod psm =
                    m.getDeclaredAnnotation(PythonStaticMethod.class);
            if (psm != null) { addStaticMethodSpec(m, psm); }

            // XXX Check for class method
            // PythonClassMethod pcm =
            // m.getDeclaredAnnotation(PythonClassMethod.class);
            // if (pcm != null) { addClassMethodSpec(m, pcm); }

            // XXX Check for getter, setter, deleter method
            // PythonClassMethod gm =
            // m.getDeclaredAnnotation(Getter.class);
            // if (gm != null) { addGetter(m, gm); }

            // If it has a special method name record that definition.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null) { addWrapperSpec(m, slot); }
        }
    }

    /**
     * Process a method that matches a slot name to a descriptor
     * specification and add it to the table of specifications by name.
     *
     * @param meth method annotated
     * @param slot annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addWrapperSpec(Method meth, Slot slot)
            throws InterpreterError {

        String name = slot.methodName;
        // Find any existing definition
        Spec spec = specs.get(name);
        WrapperSpec wrapperSpec;
        if (spec == null) {
            // A new entry is needed
            wrapperSpec = new WrapperSpec(slot);
            specs.put(name, wrapperSpec);
        } else if (spec instanceof WrapperSpec) {
            // Existing entry will be updated
            wrapperSpec = (WrapperSpec) spec;
        } else {
            // Existing entry is not compatible
            wrapperSpec = new WrapperSpec(slot);
            throw duplicateError(name, meth, wrapperSpec, spec);
        }
        wrapperSpec.add(meth);
    }

    /**
     * Add to {@link #specs}, definitions of fields found in the given
     * class and annotated for exposure.
     *
     * @param defsClass to introspect for field definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addFieldSpecs(Class<?> defsClass) throws InterpreterError {

        // Iterate over methods looking for those to expose
        for (Field f : defsClass.getDeclaredFields()) {
            // Check for instance method
            Member m = f.getDeclaredAnnotation(Member.class);
            if (m != null) { addMemberSpec(f, m); }
        }
    }

    /**
     * Process an annotated field, that describes an exposed attribute,
     * into a specification, and add it to the table of specifications
     * by name.
     *
     * @param f field annotated
     * @param anno annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addMemberSpec(Field f, Member anno) throws InterpreterError {

        // The name is as annotated or the "natural" one
        String name = anno.value();
        if (name == null || name.length() == 0)
            name = f.getName();

        /*
         * XXX we follow the same pattern as with other spec types, in
         * accumulating multiple definitions in a list. Repeat
         * definition is almost certainly an error, and at this time,
         * MemberSpec.add treats it as such. This makes Member
         * annotations incompatible with the idea of multiple accepted
         * implementations of a type.
         */
        // Find any existing definition
        Spec spec = specs.get(name);
        MemberSpec memberSpec;
        if (spec == null) {
            // A new entry is needed
            memberSpec = new MemberSpec(name);
            specs.put(memberSpec.name, memberSpec);
            memberSpecs.add(memberSpec);
        } else if (spec instanceof MemberSpec) {
            // Existing entry will be updated
            memberSpec = (MemberSpec) spec;
        } else {
            // Existing entry is not compatible
            memberSpec = new MemberSpec(name);
            throw duplicateError(name, f, memberSpec, spec);
        }
        // Add the field, processing the additional properties
        memberSpec.add(f, anno.optional(), anno.readonly());
    }

    @Override
    public String toString() {
        return "TypeExposer [type=" + type + "]";
    }

    /**
     * A specialisation of {@link Exposer.Spec} to describe a named,
     * built-in data-like object, during the exposure process.
     */
    static class MemberSpec extends Exposer.Spec {

        /** Collects the fields declared (should be just one). */
        final List<Field> fields;

        /**
         * The member disappears when the field is {@code null}. This is
         * always {@code false} for primitive types.
         */
        boolean optional;
        /** The member may be read but not written or deleted. */
        boolean readonly;

        MemberSpec(String name) {
            super(name, ScopeKind.TYPE);
            this.fields = new ArrayList<>(1);
        }

        /**
         * Add a field implementing this member to the collection.
         *
         * @param field to add to {@link #fields}
         * @param optional member is optional
         * @param readonly member is read only
         */
        void add(Field field, boolean optional, boolean readonly) {

            // Final overrides readonly=false
            int modifiers = field.getModifiers();
            readonly |= (modifiers & Modifier.FINAL) != 0;

            // Disallow static (in Java)
            boolean javaStatic = (modifiers & Modifier.STATIC) != 0;
            if (javaStatic) {
                throw new InterpreterError(CANNOT_BE_JAVA_STATIC,
                        getJavaName());
            }

            // Disallow optional if primitive (in Java)
            if (optional) {
                if (field.getType().isPrimitive()) {
                    throw new InterpreterError(CANNOT_BE_OPTIONAL,
                            "Primitive", getJavaName());
                } else if (readonly) {
                    throw new InterpreterError(CANNOT_BE_OPTIONAL,
                            "Read-only", getJavaName());
                }
            }

            // Add with check multiplicity (do we actually need a list?)
            fields.add(field);
            if (fields.size() != 1) {
                throw duplicateError(name, field, this, this);
            }

            // Finally insert the allowed combination
            this.optional = optional;
            this.readonly = readonly;

            // There may be a @DocString annotation
            DocString docAnno = field.getAnnotation(DocString.class);
            if (docAnno != null) { doc = docAnno.value(); }
        }

        private static final String CANNOT_BE_JAVA_STATIC =
                "The definition of '%s' cannot be Java static "
                        + "because it is a Python member";
        private static final String CANNOT_BE_OPTIONAL =
                "%s field '%s' cannot be optional";

        @Override
        Class<? extends Annotation> annoClass() {
            return Member.class;
        }

        /**
         * {@inheritDoc}
         * <p>
         * In a type, the attribute must be represented by a descriptor
         * for the Python member attribute from this specification.
         * <p>
         * Note that specification may have collected multiple Java
         * definitions of the same name. This method checks there is
         * exactly one.
         *
         * @return descriptor for access to the methods
         * @throws InterpreterError if the method type is not supported
         */
        @Override
        PyMemberDescr asAttribute(PyType objclass, Lookup lookup) {
            EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
            if (readonly) { flags.add(Flag.READONLY); }
            if (optional) { flags.add(Flag.OPTIONAL); }
            return PyMemberDescr.forField(objclass, name, fields.get(0),
                    lookup, flags, doc);
        }

        /** @return a name designating the field */
        @Override
        String getJavaName() {
            StringBuilder b = new StringBuilder(64);
            if (!fields.isEmpty()) {
                // It shouldn't matter, but take the last added
                Field field = fields.get(fields.size() - 1);
                b.append(field.getDeclaringClass().getSimpleName());
                b.append('.');
                b.append(field.getName());
            } else {
                // Take the name from the Spec instead
                b.append(name);
            }
            return b.toString();
        }

        /** @return the Java declaration for the field */
        String getJavaDeclaration() {
            StringBuilder b = new StringBuilder(64);
            if (!fields.isEmpty()) {
                // It shouldn't matter, but take the last added
                Field field = fields.get(fields.size() - 1);
                b.append(field.getType().getSimpleName());
                b.append(' ');
                b.append(field.getName());
            }
            return b.toString();
        }

        @Override
        public String toString() {
            return String.format("%s(%s [%s])",
                    getClass().getSimpleName(), name,
                    getJavaDeclaration());
        }
    }

}
