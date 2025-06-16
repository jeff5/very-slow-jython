// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Function;

import uk.co.farowl.vsj4.runtime.Exposed.Deleter;
import uk.co.farowl.vsj4.runtime.Exposed.DocString;
import uk.co.farowl.vsj4.runtime.Exposed.Getter;
import uk.co.farowl.vsj4.runtime.Exposed.Member;
import uk.co.farowl.vsj4.runtime.Exposed.PythonMethod;
import uk.co.farowl.vsj4.runtime.Exposed.PythonNewMethod;
import uk.co.farowl.vsj4.runtime.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj4.runtime.Exposed.Setter;
import uk.co.farowl.vsj4.runtime.PyMemberDescr.Flag;
import uk.co.farowl.vsj4.runtime.kernel.BaseType;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.runtime.kernel.TypeExposer;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.MethodKind;
import uk.co.farowl.vsj4.support.ScopeKind;

class TypeExposerImplementation extends Exposer implements TypeExposer {

    /**
     * Type for which attributes are to be exposed (or {@code null}
     * during certain tests). It is referenced (e.g. where we create a
     * descriptor), but is not otherwise accessed, since it is
     * (necessarily) incomplete at this time.
     */
    final BaseType type;

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
     * will be interrogated as to its implementing classes when we
     * create descriptors, at the point {@link #exposeMethods(Class)}
     * and {@link #exposeMembers(Class)} are called.
     *
     * @param type being exposed
     */
    TypeExposerImplementation(BaseType type) {
        this.type = type;
        this.memberSpecs = new TreeSet<>();
        this.getSetSpecs = new TreeSet<>();
    }

    @Override
    ScopeKind kind() { return ScopeKind.TYPE; }

    @Override
    public void exposeMethods(Class<?> methodClass) {
        // Scan the defining class for exposed and special methods
        scanJavaMethods(methodClass);
    }

    @Override
    public void exposeMembers(Class<?> memberClass) {
        // Scan the defining class for exposed fields
        scanJavaFields(memberClass);
    }

    @Override
    public Iterable<Entry> entries(Lookup lookup) {
        if (type == null)
            // type may only properly be null during certain tests
            throw new InterpreterError(
                    "Cannot generate entries for type 'null'");
        logger.atDebug().addArgument(type.getName())
                .log("Populating '{}'");

        // The returned object will stream name-attr pairs
        return new Iterable<Entry>() {

            @Override
            public Iterator<Entry> iterator() {
                return new Iterator<Entry>() {
                    // Entries derive from the specs table
                    Iterator<Spec> specIter = specs.values().iterator();

                    @Override
                    public boolean hasNext() {
                        // ... if there are specs left to process
                        return specIter.hasNext();
                    }

                    @Override
                    public Entry next() {
                        Spec spec = specIter.next();
                        logger.atTrace().addArgument(type.getName())
                                .addArgument(spec.name)
                                .addArgument(spec.annoClassName())
                                .log("-  Add {}.{} ({})");
                        spec.checkFormation();
                        // Create attribute according to spec type
                        Object attr = spec.asAttribute(type, lookup);
                        return new Entry(spec.name, attr);
                    }
                };
            }
        };
    }

    /**
     * Add to {@link #specs}, definitions based on methods found in the
     * given class and either annotated for exposure or having the name
     * of a special method.
     *
     * @param c to introspect for methods
     * @throws InterpreterError on duplicates or unsupported types
     */
    @Override
    void scanJavaMethods(Class<?> c) throws InterpreterError {

        logger.atTrace().addArgument(c).log("Finding methods in {}");

        // Iterate over methods looking for those to expose
        for (Method m : c.getDeclaredMethods()) {
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

            // Check for __new__ method
            PythonNewMethod pnm =
                    m.getDeclaredAnnotation(PythonNewMethod.class);
            if (pnm != null) { addNewMethodSpec(m, pnm, type); }

            // XXX Check for class method
            // PythonClassMethod pcm =
            // m.getDeclaredAnnotation(PythonClassMethod.class);
            // if (pcm != null) { addClassMethodSpec(m, pcm); }

            // XXX Check for getter, setter, deleter methods
            Getter get = m.getAnnotation(Getter.class);
            if (get != null) { addGetter(m, get); }
            Setter set = m.getAnnotation(Setter.class);
            if (set != null) { addSetter(m, set); }
            Deleter del = m.getAnnotation(Deleter.class);
            if (del != null) { addDeleter(m, del); }

            // If it has a special method name record a definition.
            String name = m.getName();
            SpecialMethod sm = SpecialMethod.forMethodName(name);
            if (sm != null) { addWrapperSpec(m, sm); }
        }
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
        addSpec(m, anno.value(), gss -> castGetSet(gss),
                GetSetSpec::new, ms -> getSetSpecs.add(ms),
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
        addSpec(m, anno.value(), gss -> castGetSet(gss),
                GetSetSpec::new, ms -> getSetSpecs.add(ms),
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
        addSpec(m, anno.value(), gss -> castGetSet(gss),
                GetSetSpec::new, ms -> getSetSpecs.add(ms),
                GetSetSpec::addDeleter);
    }

    /**
     * Cast an arbitrary {@link Spec} to a {@link GetSetSpec} or return
     * {@code null}.
     *
     * @param spec to cast
     * @return {@code spec} or {@code null}
     */
    private static GetSetSpec castGetSet(Spec spec) {
        return spec instanceof GetSetSpec ? (GetSetSpec)spec : null;
    }

    /**
     * Process a method that matches a special method name to a
     * descriptor specification and add it to the table of
     * specifications by name.
     *
     * @param meth method annotated
     * @param sm annotation encountered
     * @throws InterpreterError on duplicates or unsupported types
     */
    private void addWrapperSpec(Method meth, SpecialMethod sm)
            throws InterpreterError {

        // For clarity, name lambda expression for cast
        Function<Spec, WrapperSpec> cast =
                // Test and cast a found Spec to MethodSpec
                spec -> spec instanceof WrapperSpec ? (WrapperSpec)spec
                        : null;
        // Now use the generic create/update
        addSpec(meth, sm.methodName, cast,
                (String ignored) -> new WrapperSpec(sm), ms -> {},
                WrapperSpec::add);
    }

    /**
     * Process an annotation that identifies a {@code __new__} method of
     * a Python type or module defined in Java, into a specification for
     * a method, and add it to the table of specifications by name.
     *
     * @param anno annotation encountered
     * @param meth method annotated
     * @param type defining type ({@code __self__} in the exposed form)
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addNewMethodSpec(Method meth, PythonNewMethod anno,
            BaseType type) throws InterpreterError {
        // For clarity, name lambda expressions for the actions
        BiConsumer<NewMethodSpec, Method> addMethod =
                // Add method m to spec ms
                (NewMethodSpec ms, Method m) -> {
                    ms.add(m, true, true, MethodKind.NEW);
                };
        Function<Spec, NewMethodSpec> cast =
                // Test and cast a found Spec to NewMethodSpec
                spec -> spec instanceof NewMethodSpec
                        ? (NewMethodSpec)spec : null;
        // Now use the generic create/update
        addSpec(meth, anno.value(), cast,
                (String name) -> new NewMethodSpec(name, type),
                ms -> methodSpecs.add(ms), addMethod);
    }

    /**
     * Add to {@link #specs}, definitions of fields found in the given
     * class and annotated for exposure.
     *
     * @param c to introspect for field definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    void scanJavaFields(Class<?> c) throws InterpreterError {

        logger.atTrace().addArgument(c).log("Finding members in {}");

        // Iterate over fields looking for the relevant annotations
        for (Field f : c.getDeclaredFields()) {
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
            memberSpec = (MemberSpec)spec;
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

            // Add the only definition (do we actually need a list?)
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
        Class<? extends Annotation> annoClass() { return Member.class; }

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
        PyMemberDescr asAttribute(BaseType objclass, Lookup lookup) {
            EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
            if (readonly) { flags.add(Flag.READONLY); }
            if (optional) { flags.add(Flag.OPTIONAL); }
            return PyMemberDescr.forField(objclass, name, fields.get(0),
                    lookup, flags, doc);
        }

        @Override
        public void checkFormation() throws InterpreterError {}

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
        /** Java class of attribute from setter parameter. */
        Class<?> klass = Object.class;

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
        boolean optional() { return !deleters.isEmpty(); }

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
            // Process parameters of the Setter
            determineAttrType(method);
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

        /**
         * Deduce the attribute type from the (raw) set method
         * signature. We do this in order to give a sensible
         * {@link PyBaseException TypeError} when a cast fails for the
         * {@link PyGetSetDescr#__set__} operation.
         *
         * @param method annotated with a {@code Setter}
         */
        private void determineAttrType(Method method) {
            // Save class of value accepted (if signature is sensible)
            int modifiers = method.getModifiers();
            int v = (modifiers & Modifier.STATIC) != 0 ? 1 : 0;
            Class<?>[] paramClasses = method.getParameterTypes();
            if (paramClasses.length == v + 1) {
                Class<?> valueClass = paramClasses[v];
                if (valueClass == klass) {
                    // No change
                } else if (klass.isAssignableFrom(valueClass)) {
                    // The parameter is more specific than klass
                    klass = valueClass;
                }
            }
        }

        @Override
        Object asAttribute(BaseType objclass, Lookup lookup)
                throws InterpreterError {
            if (objclass.selfClasses().size() == 1)
                return createDescrSingle(objclass, lookup);
            else
                return createDescrMultiple(objclass, lookup);
        }

        @Override
        public void checkFormation() throws InterpreterError {}

        /**
         * Create a {@code PyGetSetDescr} from this specification. Note
         * that a specification collects all the methods as declared
         * with this name (in separate getter, setter and deleter
         * lists). Here there should be at most one of each.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private Object createDescrSingle(BaseType objclass,
                Lookup lookup) {
            /*
             * Lazily, we use the mechanism unreflect() that works for
             * types with multiple self-classes, knowing there can be
             * only one implementation.
             */
            // Handles on implementation methods
            MethodHandle g, s = null, d = null;
            g = unreflect(objclass, lookup, PyGetSetDescr.GETTER,
                    getters)[0];
            if (!readonly()) {
                // We can set this attribute
                s = unreflect(objclass, lookup, PyGetSetDescr.SETTER,
                        setters)[0];
                if (optional()) {
                    // We can delete this attribute
                    d = unreflect(objclass, lookup,
                            PyGetSetDescr.DELETER, deleters)[0];
                }
            }

            // Create a descriptor from the get, set and delete handles
            return new PyGetSetDescr.Single(objclass, name, g, s, d,
                    doc, klass);
        }

        /**
         * Create a {@code PyGetSetDescr} from this specification. Note
         * that a specification collects all the methods as declared
         * with this name (in separate getter, setter and deleter
         * lists). Normally there is at most one of each.
         * <p>
         * Normally also, a Python type has just one Java
         * implementation. If a type has N accepted implementations,
         * there should be definitions of the getter, setter, and
         * deleter methods, if defined at all, applicable to each
         * accepted implementation. This method matches defined methods
         * to the supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyGetSetDescr createDescrMultiple(BaseType objclass,
                Lookup lookup) throws InterpreterError {

            // Handles on implementation methods
            MethodHandle[] g, s = null, d = null;
            g = unreflect(objclass, lookup, PyGetSetDescr.GETTER,
                    getters);
            if (!readonly()) {
                // We can set this attribute
                s = unreflect(objclass, lookup, PyGetSetDescr.SETTER,
                        setters);
                if (optional()) {
                    // We can delete this attribute
                    d = unreflect(objclass, lookup,
                            PyGetSetDescr.DELETER, deleters);
                }
            }

            return new PyGetSetDescr.Multiple(objclass, name, g, s, d,
                    doc, klass);
        }

        /**
         * Build a method handle array for use in a descriptor defined
         * by the given Python type and having an entry for each
         * self-class of the type.
         *
         * @param objclass defining the descriptor
         * @param lookup rights to form handles on the methods
         * @param mt of the handles to return
         * @param methods to consider as candidates
         * @return the array of handles
         * @throws InterpreterError
         */
        private MethodHandle[] unreflect(PyType objclass, Lookup lookup,
                MethodType mt, List<Method> methods)
                throws InterpreterError {
            /*
             * In the first stage, translate each method to a handle.
             * There could be any number of candidates in the defining
             * classes. There may be a method for each accepted
             * implementation of the type, or a method may match more
             * than one (e.g. Number matching Long and Integer). We
             * build a list with the more type-specific handles (in the
             * first argument) before the less type-specific.
             */
            LinkedList<MethodHandle> candidates = new LinkedList<>();
            for (Method m : methods) {
                // Convert m to a handle (if L args and accessible)
                try {
                    MethodHandle mh = lookup.unreflect(m);
                    addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw cannotGetHandle(m, e);
                }
            }

            /*
             * We will try to create a handle for each self-class of the
             * type defining the instance method.
             */
            List<Class<?>> classes = objclass.selfClasses();
            final int N = classes.size();
            MethodHandle[] method = new MethodHandle[N];

            // Fill the method array with matching method handles
            for (int i = 0; i < N; i++) {
                Class<?> klass = classes.get(i);
                /*
                 * Fill method[i] with the method handle where the first
                 * parameter is the most specific match for class
                 * accepted[i].
                 */
                // Try the candidate method until one matches
                for (MethodHandle mh : candidates) {
                    MethodType mt1 = mh.type();
                    if (mt1.parameterType(0).isAssignableFrom(klass)) {
                        /*
                         * Each sub-type of MethodDef handles
                         * callMethod(self, args, kwargs) in its own
                         * way, and must prepare the arguments of the
                         * generic method handle to match.
                         */
                        try {
                            // XXX not yet supporting Java args
                            method[i] = mh.asType(mt);
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(objclass, mh);
                        }
                        break;
                    }
                }

                // We should have a value in each of method[]
                if (method[i] == null) {
                    PyGetSetDescr.Type dt =
                            PyGetSetDescr.Type.fromMethodType(mt);
                    throw new InterpreterError(ATTR_NOT_IMPL, dt, name,
                            objclass.getName(), klass);
                }
            }

            /*
             * There are multiple definitions so use the array form of
             * built-in method. This is the case for types that have
             * multiple accepted implementations and methods on them
             * that are not static or "Object self".
             */
            return method;
        }

        private static String ATTR_NOT_IMPL =
                "%s of attribute '%s' of '%s' objects"
                        + " is not defined for implementation %s";

        @Override
        Class<? extends Annotation> annoClass() {
            // Try annotations in order of popularity
            if (getters.size() > 0)
                ; // -> Getter
            else if (setters.size() > 0)
                return Setter.class;
            else if (deleters.size() > 0)
                return Deleter.class;
            // Or by default, claim to have a Getter
            return Getter.class;
        }

        @Override
        public String toString() {
            return String.format("%s(%s[%d,%d,%d])",
                    getClass().getSimpleName(), name, getters.size(),
                    setters.size(), deleters.size());
        }
    }

    /**
     * Specification in which we assemble information about a Python
     * special method in advance of creating a special method
     * descriptor.
     */
    static class WrapperSpec extends BaseMethodSpec {

        /** The special method being defined. */
        final SpecialMethod sm;

        WrapperSpec(SpecialMethod sm) {
            super(sm.methodName, ScopeKind.TYPE);
            this.sm = sm;
        }

        @Override
        Object asAttribute(BaseType objclass, Lookup lookup)
                throws InterpreterError {
            /*
             * We will try to create a handle for each implementation of
             * a special (instance) method. See corresponding logic in
             * SpecialMethod.setSlot(Representation, Object)
             */
            return createDescrForInstanceMethod(objclass, lookup);
        }

        @Override
        public void checkFormation() throws InterpreterError {
            // XXX Check the signature instead of in createDescr?
        }

        @Override
        void add(Method method) { super.add(method); }

        @Override
        Class<? extends Annotation> annoClass() {
            // Special methods recognised by name, so no annotation
            return Annotation.class;
        }

        /**
         * {@inheritDoc}
         * <p>
         * In this case, we name the special method, as there is no
         * annotation.
         */
        @Override
        protected String annoClassName() { return sm.name(); }

        /**
         * Create a {@code PyWrapperDescr} from this specification. Note
         * that a specification describes the methods as declared, and
         * that there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyWrapperDescr createDescrForInstanceMethod(
                BaseType objclass, Lookup lookup)
                throws InterpreterError {

            // Acceptable methods can be coerced to this signature
            MethodType slotType = sm.getType();
            final int L = slotType.parameterCount();
            assert L >= 1;

            /*
             * There could be any number of candidates in the
             * implementation. An implementation method could match
             * multiple accepted implementations of the type (e.g.
             * Number matching Long and Integer).
             */
            LinkedList<MethodHandle> candidates = new LinkedList<>();
            for (Method m : methods) {
                // Convert m to a handle (if L args and accessible)
                try {
                    MethodHandle mh = lookup.unreflect(m);
                    if (mh.type().parameterCount() == L)
                        addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw cannotGetHandle(m, e);
                }
            }

            /*
             * We will try to create a handle for each implementation of
             * an instance method, but only one handle for static/class
             * methods (like __new__). See corresponding logic in
             * SpecialMethod.setSlot(Representation, Object)
             */
            // TODO Rationalise with PyMethodDescr.prepareCandidates
            List<Class<?>> selfClasses = objclass.selfClasses();
            final int N = selfClasses.size();
            MethodHandle[] wrapped = new MethodHandle[N];

            /*
             * Fill the method array with matching method handles for
             * the primary, adopted and accepted Java representation
             * classes of this type.
             */
            for (int i = 0; i < N; i++) {
                // Seek most specific match for the i.th self-class
                Class<?> sc = selfClasses.get(i);
                // The candidates are sorted most specific first
                for (MethodHandle mh : candidates) {
                    if (mh.type().parameterType(0)
                            .isAssignableFrom(sc)) {
                        try {
                            // must have the expected signature
                            checkCast(mh, slotType);
                            wrapped[i] = mh.asType(slotType);
                            break;
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(objclass, mh);
                        }
                    }
                }

                // We should have a value in each of wrapped[]
                if (wrapped[i] == null) {
                    throw new InterpreterError(
                            "'%s.%s' not defined for %s",
                            objclass.getName(), sm.methodName, sc);
                }
            }

            if (N == 1)
                /*
                 * There is only one definition so use the simpler form
                 * of slot-wrapper. This is the frequent case.
                 */
                return new PyWrapperDescr.Single(objclass, sm,
                        wrapped[0]);
            else
                /*
                 * There are multiple definitions so use the array form
                 * of slot-wrapper. This is the case for types that have
                 * multiple accepted implementations and methods on them
                 * that are not static or "Object self".
                 */
                return new PyWrapperDescr.Multiple(objclass, sm,
                        wrapped);
        }

        /**
         * Throw a {@code WrongMethodTypeException} if the offered
         * method (e.g. a special method) cannot be called with
         * arguments matching the specified type. This makes up for the
         * fact that {@code MethodHandle.asType} does not do much
         * checking. This way, we get an error at specification time,
         * not run-time.
         *
         * @param mh handle of method offered
         * @param slotType required type
         * @throws WrongMethodTypeException if cannot cast
         */
        private static void checkCast(MethodHandle mh,
                MethodType slotType) throws WrongMethodTypeException {
            MethodType mt = mh.type();
            int n = mt.parameterCount();
            boolean ok = (n == slotType.parameterCount()) && slotType
                    .returnType().isAssignableFrom(mt.returnType());
            for (int i = 0; ok && i < n; i++) {
                ok = slotType.parameterType(i)
                        .isAssignableFrom(mt.parameterType(i));
            }
            if (!ok) { throw new WrongMethodTypeException(); }
        }
    }

    /**
     * Specification in which we assemble information about a Python
     * {@code __new__} method in advance of creating a function
     * {@link PyJavaFunction}.
     */
    static class NewMethodSpec extends CallableSpec {

        /** The defining Python type. */
        final private PyType type;

        NewMethodSpec(String name, BaseType type) {
            super(name, ScopeKind.TYPE);
            this.type = type;
        }

        /**
         * {@inheritDoc}
         * <p>
         * In a type, the attribute representing a {@code __new__}
         * method is a {@code PyJavaFunction}. This method creates it
         * from the specification.
         * <p>
         * Note that a specification describes the method as declared,
         * and that there must be exactly one, even if there are
         * multiple implementations of the type.
         *
         * @param objclass Python type that owns the method
         * @param lookup authorisation to access members
         * @return function for access to the method
         * @throws InterpreterError if the method type is not supported
         */
        @Override
        PyJavaFunction asAttribute(BaseType objclass, Lookup lookup) {
            assert methodKind == MethodKind.NEW;
            ArgParser ap = getParser();

            // There should be exactly one candidate implementation.
            if (methods.size() != 1) {
                throw new InterpreterError("%s has %d definitions in ",
                        name, methods.size(), getJavaName());
            }

            Method m = methods.get(0);
            try {
                // Convert m to a handle (if accessible)
                MethodHandle mh = lookup.unreflect(m);
                PyJavaFunction javaFunction =
                        PyJavaFunction.forNewMethod(ap, mh, type);
                return javaFunction;
            } catch (IllegalAccessException e) {
                throw cannotGetHandle(m, e);
            }
        }

        @Override
        Class<? extends Annotation> annoClass() {
            return PythonNewMethod.class;
        }
    }

    // TODO Not ready for binary operation call-sites this yet
/// **
// * Create a table of {@code MethodHandle}s from binary operations
// * defined in the given class, on behalf of the type given. This
// * table is 3-dimensional, being indexed by the slot of the method
// * being defined, which must be a binary operation, and the indices
// * of the operand classes in the type. These handles are used
// * privately by the type to create call sites. Although the process
// * of creating them is similar to making wrapper descriptors, these
// * structures do not become exposed as descriptors.
// *
// * @param lookup authorisation to access methods
// * @param binops to introspect for binary operations
// * @param type to which these descriptors apply
// * @return attributes defined (in the order first encountered)
// * @throws InterpreterError on duplicates or unsupported types
// */
// static Map<SpecialMethod, BinopGrid> binopTable(Lookup lookup,
// Class<?> binops, PyType type) throws InterpreterError {
//
// // Iterate over methods looking for the relevant annotations
// Map<SpecialMethod, BinopGrid> defs = new HashMap<>();
//
// for (Method m : binops.getDeclaredMethods()) {
// // If it is a special method, record the definition.
// String name = m.getName();
// SpecialMethod slot = SpecialMethod.forMethodName(name);
// if (slot != null && slot.signature == Signature.BINARY) {
// binopTableAdd(defs, slot, m, lookup, binops, type);
// }
// }
//
// // Check for nulls in the table.
// for (BinopGrid grid : defs.values()) { grid.checkFilled(); }
//
// return defs;
// }
//
/// **
// * Add a method handle to the table, verifying that the method type
// * produced is compatible with the {@link #slot}.
// *
// * @param defs the method table to add to
// * @param slot being matched
// * @param m implementing method
// * @param lookup authorisation to access fields
// * @param binops class defining class-specific binary operations
// * @param type to which these belong
// */
// private static void binopTableAdd(Map<SpecialMethod, BinopGrid> defs,
// SpecialMethod slot, Method m, Lookup lookup, Class<?> binops,
// PyType type) {
//
// // Get (or create) the table for this slot
// BinopGrid def = defs.get(slot);
// if (def == null) {
// // A new special method has been encountered
// def = new BinopGrid(slot, type);
// defs.put(slot, def);
// }
//
// try {
// // Convert the method to a handle
// def.add(lookup.unreflect(m));
// } catch (IllegalAccessException | WrongMethodTypeException e) {
// throw new InterpreterError(e,
// "ill-formed or inaccessible binary op '%s'", m);
// }
// }
}
