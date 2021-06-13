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
import java.util.function.Function;

import uk.co.farowl.vsj3.evo1.Exposed.Deleter;
import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Exposed.Member;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj3.evo1.Exposed.Setter;
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
     * eventually become descriptors in a built-in object type. Every
     * entry here is also a value in {@link Exposer#specs}.
     */
    final Set<MemberSpec> memberSpecs;

    /**
     * The table of intermediate descriptions for get-sets. They will
     * eventually become descriptors in a built-in object type. Every
     * entry here is also a value in {@link Exposer#specs}.
     */
    final Set<GetSetSpec> getSetSpecs;

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
        this.getSetSpecs = new TreeSet<>();
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
        scanJavaMethods(definingClass);
        // ... and for fields.
        scanJavaFields(definingClass);
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
    void scanJavaMethods(Class<?> defsClass) throws InterpreterError {

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

            // Check for getter, setter, deleter methods
            Getter get = m.getAnnotation(Getter.class);
            if (get != null) { addGetter(m, get); }
            Setter set = m.getAnnotation(Setter.class);
            if (set != null) { addSetter(m, set); }
            Deleter del = m.getAnnotation(Deleter.class);
            if (del != null) { addDeleter(m, del); }

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
    private void addWrapperSpec(Method meth, Slot slot)
            throws InterpreterError {

        // For clarity, name lambda expression for cast
        Function<Spec, WrapperSpec> cast =
                // Test and cast a found Spec to MethodSpec
                spec -> spec instanceof WrapperSpec ? (WrapperSpec) spec
                        : null;
        // Now use the generic create/update
        addSpec(meth, slot.methodName, cast,
                (String ignored) -> new WrapperSpec(slot), ms -> {},
                WrapperSpec::add);
    }

    /**
     * Process a method annotated as an exposed attribute get method,
     * into a specification, and find a {@link GetSetSpec} to the table
     * of specifications by name (or add one) to hold it.
     *
     * @param m method annotated
     * @param anno annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    private void addGetter(Method m, Getter anno) {
        addSpec(m, anno.value(), TypeExposer::castGetSet,
                GetSetSpec::new, ms -> { getSetSpecs.add(ms); },
                GetSetSpec::addGetter);
    }

    /**
     * Process a method annotated as an exposed attribute set method,
     * into a specification, and find a {@link GetSetSpec} to the table
     * of specifications by name (or add one) to hold it.
     *
     *
     * @param m method annotated
     * @param anno annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    private void addSetter(Method m, Setter anno) {
        addSpec(m, anno.value(), TypeExposer::castGetSet,
                GetSetSpec::new, ms -> { getSetSpecs.add(ms); },
                GetSetSpec::addSetter);
    }

    /**
     * Process a method annotated as an exposed attribute get method,
     * into a specification, and find a {@link GetSetSpec} to the table
     * of specifications by name (or add one) to hold it.
     *
     *
     * @param m method annotated
     * @param anno annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    private void addDeleter(Method m, Deleter anno) {
        addSpec(m, anno.value(), TypeExposer::castGetSet,
                GetSetSpec::new, ms -> { getSetSpecs.add(ms); },
                GetSetSpec::addDeleter);
    }

    /**
     * Cast an arbitrary {@link Spec} to a {@link GetSetSpec} or return
     * {@code null}.
     *
     * @param spec to cast
     * @return {@code spec} or {@code null}
     */
    static GetSetSpec castGetSet(Spec spec) {
        return spec instanceof GetSetSpec ? (GetSetSpec) spec : null;
    }

    /**
     * Add to {@link #specs}, definitions of fields found in the given
     * class and annotated for exposure.
     *
     * @param defsClass to introspect for field definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    void scanJavaFields(Class<?> defsClass) throws InterpreterError {

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

    /**
     * A specialisation of {@link Exposer.Spec} to describe a named,
     * built-in data-like object, during the exposure process.
     */
    static class GetSetSpec extends BaseMethodSpec {

        /** Collects the getters declared (often just one). */
        final List<Method> getters;
        /** Collects the setters declared (often just one). */
        final List<Method> setters;
        /** Collects the deleters declared (often just one). */
        final List<Method> deleters;

        GetSetSpec(String name) {
            super(name, ScopeKind.TYPE);
            this.getters = methods;
            this.setters = new ArrayList<>(1);
            this.deleters = new ArrayList<>(1);
        }

        /**
         * The attribute may not be set or deleted.
         *
         * @return true if set and delete are absent
         */
        boolean readonly() {
            return setters.isEmpty() && deleters.isEmpty();
        }

        /**
         * The attribute may be deleted.
         *
         * @return true if delete is present
         */
        boolean optional() {
            return !deleters.isEmpty();
        }

        /**
         * Add a getter to the collection.
         *
         * @param method to add to {@link #getters}
         */
        void addGetter(Method method) {
            // Add to list of methods
            getters.add(method);
            // There may be a @DocString annotation
            maybeAddDoc(method);
        }

        /**
         * Add a setter to the collection.
         *
         * @param method to add to {@link #setters}
         */
        void addSetter(Method method) {
            // Add to list of methods
            setters.add(method);
            // There may be a @DocString annotation
            maybeAddDoc(method);
        }

        /**
         * Add a deleter to the collection.
         *
         * @param method to add to {@link #deleters}
         */
        void addDeleter(Method method) {
            // Add to list of methods
            deleters.add(method);
            // There may be a @DocString annotation
            maybeAddDoc(method);
        }

        @Override
        Object asAttribute(PyType objclass, Lookup lookup)
                throws InterpreterError {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        Class<? extends Annotation> annoClass() {
            // XXX Not always :(
            return Getter.class;
        }

        @Override
        public String toString() {
            return String.format("%s(%s[%d,%d,%d])",
                    getClass().getSimpleName(), name, getters.size(),
                    setters.size(), deleters.size());
        }
    }
}
