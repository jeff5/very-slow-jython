package uk.co.farowl.vsj3.evo1;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Member;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeSet;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;

import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.KeywordCollector;
import uk.co.farowl.vsj3.evo1.Exposed.KeywordOnly;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalCollector;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;

/**
 * An object for tabulating the attributes of classes that define Python
 * types or modules.
 */
abstract class Exposer {

    /**
     * The index of intermediate descriptions by name, in which we may
     * locate specifications already made or begun, using the name.
     */
    final Map<String, Spec> specs;

    /**
     * The table of intermediate descriptions for methods. They will
     * become {@link MethodDef}s, and eventually either descriptors in a
     * built-in object type or methods bound to instances of a module
     * type. Every entry here is also a value in {@link #specs}.
     */
    final Set<CallableSpec> methodSpecs;

    /**
     * The table of intermediate descriptions for static methods. They
     * will become {@link MethodDef}s, and eventually either
     * {@code staticmethod} objects in a built-in object type or
     * functions in instances of a module type.
     *
     * Every entry here is also a value in {@link #specs}.
     */
    // final Set<StaticMethodSpec> staticMethodSpecs;

    /** Construct the base with its table of entries. */
    protected Exposer() {
        this.specs = new HashMap<>();
        this.methodSpecs = new TreeSet<>();
        // this.staticMethodSpecs = new TreeSet<>();
    }

    /** @return which {@link ScopeKind} of {@code Exposer} is this? */
    abstract ScopeKind kind();

    /**
     * On behalf of the given module defined in Java, build a
     * description of the attributes discovered by introspection of the
     * class provided.
     * <p>
     * Attributes are identified by annotations. (See {@link Exposed}.)
     *
     * @param definingClass to introspect for members
     * @return exposure result
     * @throws InterpreterError on errors of definition
     */
    static ModuleExposer exposeModule(Class<?> definingClass)
            throws InterpreterError {
        // Create an instance of Exposer to hold specs, type, etc.
        ModuleExposer exposer = new ModuleExposer();
        // Let the exposer control the logic
        exposer.expose(definingClass);
        return exposer;
    }

    /**
     * On behalf of the given type defined in Java, build a description
     * of the attributes discovered by introspection of the class (or
     * classes) provided.
     * <p>
     * Special methods are identified by their reserved name, while
     * other attributes are identified by annotations. (See
     * {@link Exposed}.)
     * <p>
     * In those attributes that reference their defining Python type
     * (descriptors), the {@code type} object will be referenced (as
     * {@link Descriptor#objclass}). It is not otherwise accessed, since
     * it is (necessarily) incomplete at this time.
     *
     * @param type to which these attributes apply
     * @param definingClass to introspect for members
     * @param methodClass additional class to introspect for members (or
     *     {@code null})
     * @return a type exposer able to deliver the attributes
     * @throws InterpreterError on errors of definition
     */
    static TypeExposer exposeType(PyType type, Class<?> definingClass,
            Class<?> methodClass) throws InterpreterError {

        // Create an instance of Exposer to hold specs, type.
        TypeExposer exposer = new TypeExposer(type);

        // Scan the defining class for definitions
        exposer.expose(definingClass);

        // Scan the supplementary class for definitions
        if (methodClass != null) { exposer.expose(methodClass); }

        // For each definition we found, add the attribute
        return exposer;
    }

    /**
     * Add to {@link #specs}, definitions found in the given class and
     * annotated for exposure. (Note that the that the override
     * {@link TypeExposer#scanJavaMethods(Class)} also adds a method if
     * it has a the name of a special method.)
     *
     * @param defsClass to introspect for definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    void scanJavaMethods(Class<?> defsClass) throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        for (Method m : defsClass.getDeclaredMethods()) {
            PythonMethod a =
                    m.getDeclaredAnnotation(PythonMethod.class);
            if (a != null) { addMethodSpec(m, a); }
        }
    }

    /**
     * Process an annotation that identifies a method of a Python type
     * or module defined in Java as one to be exposed to Python, into a
     * specification for a method descriptor, and add it to the table of
     * specifications by name.
     *
     * @param anno annotation encountered
     * @param meth method annotated
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addMethodSpec(Method meth, PythonMethod anno)
            throws InterpreterError {
        // For clarity, name lambda expressions for the actions
        BiConsumer<MethodSpec, Method> addMethod =
                // Add method m to spec ms
                (MethodSpec ms, Method m) -> {
                    ms.add(m, anno.primary(), anno.positionalOnly(),
                            MethodKind.INSTANCE);
                };
        Function<Spec, MethodSpec> cast =
                // Test and cast a found Spec to MethodSpec
                spec -> spec instanceof MethodSpec ? (MethodSpec) spec
                        : null;
        // Now use the generic create/update
        addSpec(meth, anno.value(), cast,
                (String name) -> new MethodSpec(name, kind()),
                ms -> { methodSpecs.add(ms); }, addMethod);
    }

    /**
     * Process an annotation that identifies a method of a Python type
     * or module defined in Java as one to be exposed to Python, into a
     * specification for a method descriptor, and add it to the table of
     * specifications by name.
     *
     * @param anno annotation encountered
     * @param meth method annotated
     * @throws InterpreterError on duplicates or unsupported types
     */
    void addStaticMethodSpec(Method meth, PythonStaticMethod anno)
            throws InterpreterError {
        // For clarity, name lambda expressions for the actions
        BiConsumer<StaticMethodSpec, Method> addMethod =
                // Add method m to spec ms
                (StaticMethodSpec ms, Method m) -> {
                    ms.add(m, true, anno.positionalOnly(),
                            MethodKind.STATIC);
                };
        Function<Spec, StaticMethodSpec> cast =
                // Test and cast a found Spec to StaticMethodSpec
                spec -> spec instanceof StaticMethodSpec
                        ? (StaticMethodSpec) spec : null;
        // Now use the generic create/update
        addSpec(meth, anno.value(), cast,
                (String name) -> new StaticMethodSpec(name, kind()),
                ms -> { methodSpecs.add(ms); }, addMethod);
    }

    /**
     * Create an exception with a message along the lines "'NAME',
     * already exposed as SPEC, cannot be NEW_SPEC (member METH)" where
     * the place-holders are filled from the corresponding arguments (or
     * their names or type names).
     *
     * @param name being defined
     * @param member field or method annotated
     * @param newSpec of the new entry apparently requested
     * @param priorSpec of the inconsistent, existing entry
     * @return the required error
     */
    static InterpreterError duplicateError(String name, Member member,
            Spec newSpec, Spec priorSpec) {
        String priorSpecType = specType(priorSpec);
        String newSpecType = specType(newSpec);
        if (priorSpecType.equals(priorSpecType)) {
            newSpecType = "redefined";
        }
        return new InterpreterError(ALREADY_EXPOSED, name,
                priorSpecType, newSpecType, member.getName());
    }

    private static String specType(Spec s) {
        Class<? extends Annotation> ac = s.annoClass();
        if (ac == null) {
            // Special methods recognised by name, so no annotation
            return "special method";
        } else {
            return ac.getSimpleName();
        }
    }

    private static final String ALREADY_EXPOSED =
            "'%s', already exposed as %s, cannot be %s (member %s)";

//@formatter:off
//    /**
//     * Create a table of {@link PyGetSetDescr}s annotated on the given
//     * implementation class, on behalf of the type given. This type
//     * object will become the {@link Descriptor#objclass} reference of
//     * the descriptors created, but is not otherwise accessed, since it
//     * is (necessarily) incomplete at this time.
//     *
//     * @param lookup authorisation to access methods
//     * @param implClass to introspect for getters, setters and deleters
//     * @param type to which these descriptors apply
//     * @return attributes defined (in the order first encountered)
//     * @throws InterpreterError on duplicates or unsupported types
//     */
//    static Map<String, PyGetSetDescr> getsetDescrs(Lookup lookup,
//            Class<?> implClass, PyType type) throws InterpreterError {
//
//        // Iterate over methods looking for the relevant annotations
//        Map<String, GetSetDef> defs = new LinkedHashMap<>();
//
//        for (Method m : implClass.getDeclaredMethods()) {
//            // Look for all three types now. so as to detect conflicts.
//            Exposed.Getter getterAnno =
//                    m.getAnnotation(Exposed.Getter.class);
//            Exposed.Setter setterAnno =
//                    m.getAnnotation(Exposed.Setter.class);
//            Exposed.Deleter deleterAnno =
//                    m.getAnnotation(Exposed.Deleter.class);
//            String repeated = null;
//
//            // Now process the relevant annotation, if any.
//            if ((getterAnno) != null) {
//                // There is a Getter annotation: add to definitions
//                if (setterAnno != null || deleterAnno != null)
//                    throw new InterpreterError(DEF_MULTIPLE,
//                            "get-set-delete", m,
//                            implClass.getSimpleName());
//                // Add to definitions.
//                repeated = addGetter(defs, getterAnno, m);
//
//            } else if ((setterAnno) != null) {
//                // There is a Setter annotation
//                if (deleterAnno != null)
//                    throw new InterpreterError(DEF_MULTIPLE,
//                            "get-set-delete", m,
//                            implClass.getSimpleName());
//                // Add to definitions.
//                repeated = addSetter(defs, setterAnno, m);
//
//            } else if ((deleterAnno) != null) {
//                // There is a Deleter annotation: add to definitions
//                repeated = addDeleter(defs, deleterAnno, m);
//            }
//
//            // If set non-null at any point, indicates a repeat.
//            if (repeated != null) {
//                throw new InterpreterError(DEF_REPEAT, repeated,
//                        m.getName(), implClass.getSimpleName());
//            }
//        }
//
//        // For each entry found in the class, construct a descriptor
//        Map<String, PyGetSetDescr> descrs = new LinkedHashMap<>();
//        for (GetSetDef def : defs.values()) {
//            descrs.put(def.name, def.createDescr(type, lookup));
//        }
//
//        return descrs;
//    }
//
//    /**
//     * Record a {@link Getter} in the table of {@link GetSetDef}s. The
//     * return from this method is {@code null} for success or a
//     * {@code String} identifying a duplicate definition.
//     *
//     * @param defs table of {@link GetSetDef}s
//     * @param getterAnno annotation found
//     * @param m method annotated
//     * @return {@code null} for success or string naming duplicate
//     */
//    // Using an error return simplifies getsetDescrs() internally.
//    private static String addGetter(Map<String, GetSetDef> defs,
//            Exposed.Getter getterAnno, Method m) {
//        // Get the entry to which we are adding a getter
//        GetSetDef def = ensureGetSetDef(defs, getterAnno.value(), m);
//        if (def.setGet(m) != null) {
//            // There was one already :(
//            return "getter for " + def.name;
//        }
//        // May also have DocString annotation to add.
//        return addDoc(def, m);
//    }
//
//    /**
//     * Record a {@link Setter} in the table of {@link GetSetDef}s. The
//     * return from this method is {@code null} for success or a
//     * {@code String} identifying a duplicate definition.
//     *
//     * @param defs table of {@link GetSetDef}s
//     * @param getterAnno annotation found
//     * @param m method annotated
//     * @return {@code null} for success or string naming duplicate
//     */
//    // Using an error return simplifies getsetDescrs() internally.
//    private static String addSetter(Map<String, GetSetDef> defs,
//            Exposed.Setter setterAnno, Method m) {
//        // Get the entry to which we are adding a getter
//        GetSetDef def = ensureGetSetDef(defs, setterAnno.value(), m);
//        if (def.setSet(m) != null) {
//            // There was one already :(
//            return "setter for " + def.name;
//        }
//        // May also have DocString annotation to add.
//        return addDoc(def, m);
//    }
//
//    /**
//     * Record a {@link Deleter} in the table of {@link GetSetDef}s. The
//     * return from this method is {@code null} for success or a
//     * {@code String} identifying a duplicate definition.
//     *
//     * @param defs table of {@link GetSetDef}s
//     * @param deleterAnno annotation found
//     * @param m method annotated
//     * @return {@code null} for success or string naming duplicate
//     */
//    // Using an error return simplifies getsetDescrs() internally.
//    private static String addDeleter(Map<String, GetSetDef> defs,
//            Exposed.Deleter deleterAnno, Method m) {
//        // Get the entry to which we are adding a getter
//        GetSetDef def = ensureGetSetDef(defs, deleterAnno.value(), m);
//        if (def.setDelete(m) != null) {
//            // There was one already :(
//            return "deleter for " + def.name;
//        }
//        // May also have DocString annotation to add.
//        return addDoc(def, m);
//    }
//
//    /**
//     * Add an entry to the table of {@link GetSetDef}s for the given
//     * name.
//     *
//     * @param defs table of {@link GetSetDef}s
//     * @param name to define
//     * @param m method (supplies default name)
//     * @return new or found definition
//     */
//    private static GetSetDef ensureGetSetDef(
//            Map<String, GetSetDef> defs, String name, Method m) {
//        if (name == null || name.length() == 0) { name = m.getName(); }
//        // Ensure there is a GetSetDef for the name.
//        GetSetDef def = defs.get(name);
//        if (def == null) {
//            def = new GetSetDef(name);
//            defs.put(name, def);
//        }
//        return def;
//    }
//
//    /**
//     * Add a doc string if the {@link DocString} annotation is present
//     * on the given method.
//     *
//     * @param def to add it to
//     * @param m method in question
//     * @return {@code null} or string indicating an error
//     */
//    private static String addDoc(GetSetDef def, Method m) {
//        Exposed.DocString d = m.getAnnotation(Exposed.DocString.class);
//        if (d != null) {
//            String doc = d.value();
//            if (def.setDoc(doc) != null) {
//                // There was one already :(
//                return "doc string for " + def.name;
//            }
//        }
//        return null;
//    }
//@formatter:on

    /**
     * A helper that avoids repeating nearly the same code for adding
     * each particular sub-class of {@link Spec} when a method is
     * encountered. The implementation finds or creates a {@code Spec}
     * by the given name or method name. It then adds this {@code Spec}
     * to {@link #specs}. The caller provides a factory method, in case
     * a new {@code Spec} is needed, a method for adding the Spec to a
     * type-specific list, and a method for adding the method to the
     * {@code Spec}.
     *
     * @param <MS> the type of {@link Spec} being added or added to.
     * @param m the method being adding to the {@code MS}
     * @param name specified in the annotation or {@code null}
     * @param cast to the {@code MS} if possible or {@code null}
     * @param makeSpec constructor for an {@code MS}
     * @param addSpec function to add the {@code MS} to the proper list
     * @param addMethod function to update the {@code MS} with a method
     */
    <MS extends BaseMethodSpec> void addSpec(Method m, String name,
            Function<Spec, MS> cast, //
            Function<String, MS> makeSpec, //
            Consumer<MS> addSpec, //
            BiConsumer<MS, Method> addMethod) {

        // The name is as annotated or the "natural" one
        if (name == null || name.length() == 0)
            name = m.getName();

        // Find any existing definition
        Spec spec = specs.get(name);
        MS entry;
        if (spec == null) {
            // A new entry is needed
            entry = makeSpec.apply(name);
            specs.put(entry.name, entry);
            addSpec.accept(entry);
            addMethod.accept(entry, m);
        } else if ((entry = cast.apply(spec)) != null) {
            // Existing entry will be updated
            addMethod.accept(entry, m);
        } else {
            // Existing entry is not compatible
            entry = makeSpec.apply(name);
            throw duplicateError(name, m, entry, spec);
        }
    }

    /**
     * The base of classes that describe a named, built-in object,
     * during the exposure process. Instances of {@code Exposer.Spec}
     * are created, and added to a collection held by the exposer, as
     * each definition is encountered in a defining class. The
     * annotation and other factors determine the particular subclass of
     * {@code Exposer.Spec} produced.
     * <p>
     * In cases where more than one Java definition contributes to a
     * single exposed attribute, {@code Spec}s are updated as successive
     * definitions are encountered.
     * <p>
     * When exposing attributes of a Python type, the actual object to
     * be entered in a dictionary of a type or module is obtained by a
     * call to {@link #asAttribute(PyType, Lookup)}.
     */
    abstract static class Spec implements Comparable<Spec> {

        /** The Python name of the method being defined. */
        final String name;

        /** The kind of scope (type or module) being defined. */
        final ScopeKind scopeKind;

        /** Documentation string for the (eventual) descriptor. */
        String doc = null;

        /**
         * @param name of member
         * @param scopeKind module or type?
         */
        Spec(String name, ScopeKind scopeKind) {
            this.name = name;
            this.scopeKind = scopeKind;
        }

        /**
         * Create an attribute for the type being defined (suitable as
         * an entry in its dictionary).
         *
         * @param objclass defining type
         * @param lookup authorisation to access methods or fields
         * @return attribute to add
         * @throws InterpreterError on specification errors
         */
        abstract Object asAttribute(PyType objclass, Lookup lookup)
                throws InterpreterError;

        /** @return the documentation string (or {@code null}) */
        String getDoc() {
            return doc;
        }

        /**
         * Name the built-in being defined from a Java perspective,
         * mostly for use in messages regarding errors in definition.
         *
         * @return the Java name
         */
        abstract String getJavaName();

        /**
         * The class of annotation that creates a specification of this
         * type. This is primarily for creating for error messages that
         * direct the author of an exposed class to annotations being
         * used incompatibly. {@code WrapperSpec}s return {@code null}
         * as special methods are not identified by an annotation.
         *
         * @return type of thing exposed.
         */
        abstract Class<? extends Annotation> annoClass();

        /**
         * Check for a &#64;{@link DocString} annotation, and set the
         * document string (but only once)..
         *
         * @param method that may bear the annotation
         * @throws InterpreterError if {@link #doc} is already set
         */
        void maybeAddDoc(Method method) throws InterpreterError {
            // There may be a @DocString annotation
            DocString docAnno = method.getAnnotation(DocString.class);
            if (docAnno != null) {
                if (this.doc == null) {
                    this.doc = docAnno.value();
                } else {
                    throw new InterpreterError("%s %s documented twice",
                            annoClass(), getJavaName());
                }
            }
        }

        @Override
        public int compareTo(Spec o) {
            return name.compareTo(o.name);
        }
    }

    /**
     * A specialisation of {@link Spec} to describe, through one or more
     * Java methods, a named, built-in method-like object, during the
     * exposure process.
     */
    static abstract class BaseMethodSpec extends Spec {

        /** Collects the methods declared (often just one). */
        final List<Method> methods;

        BaseMethodSpec(String name, ScopeKind scopeKind) {
            super(name, scopeKind);
            this.methods = new ArrayList<>(1);
        }

        /**
         * Add a method implementation to the collection.
         *
         * @param method to add to {@link #methods}
         */
        void add(Method method) {
            methods.add(method);
        }

        /** @return a name designating the method */
        @Override
        String getJavaName() {
            StringBuilder b = new StringBuilder(64);
            if (!methods.isEmpty()) {
                // It shouldn't matter, but take the last added
                Method method = methods.get(methods.size() - 1);
                b.append(method.getDeclaringClass().getSimpleName());
                b.append('.');
                b.append(method.getName());
            } else {
                // Take the name from the Spec instead
                b.append(name);
            }
            return b.toString();
        }

        @Override
        public String toString() {
            return String.format("%s(%s[%d])",
                    getClass().getSimpleName(), name, methods.size());
        }

        /**
         * Insert a {@code MethodHandle h} into a list, such that every
         * handle in the list, of which the first parameter type is
         * assignable from the first parameter type of {@code h}, will
         * appear after {@code h} in the list. If there are none such,
         * {@code h} is added at the end. The resulting list is
         * partially ordered, and has the property that, in a forward
         * search for a handle applicable to a given class, the most
         * specific match is found first.
         *
         * @param list to add h into
         * @param h to insert/add
         */
        protected static void addOrdered(LinkedList<MethodHandle> list,
                MethodHandle h) {
            // Type of first parameter of h
            Class<?> c = h.type().parameterType(0);
            // We'll scan until a more general type is found
            ListIterator<MethodHandle> iter = list.listIterator(0);
            while (iter.hasNext()) {
                MethodHandle i = iter.next();
                Class<?> d = i.type().parameterType(0);
                if (d.isAssignableFrom(c)) {
                    /*
                     * d is more general than c (i is more general than
                     * h): back up and position just before i.
                     */
                    iter.previous();
                    break;
                }
            }
            // Insert h where the iterator stopped. Could be the end.
            iter.add(h);
        }
    }

    /**
     * Specification in which we assemble information about a method in
     * advance of creating a method descriptor or other callable.
     * <p>
     * Objects described by this class are defined by a Java signature
     * in which parameters may be annotated to modify their treatment by
     * Python. An argument parser and a {@link MethodDef} will be
     * created to specify that treatment.
     */
    static abstract class CallableSpec extends BaseMethodSpec {

        /**
         * Names of parameters not including the {@code self} of
         * instance methods. (The names are the parameters to the method
         * in the first call to {@link #add(Method)}).
         */
        String[] parameterNames;

        /**
         * The number of positional or keyword parameters, excluding the
         * "collector" ({@code *args} and {@code **kwargs}) arguments.
         * Its value is {@code Integer.MAX_VALUE} until the primary
         * definition of the method has been encountered.
         */
        int regargcount = Integer.MAX_VALUE;

        /**
         * The number of positional-only arguments (after {@code self}).
         * This must be specified in the method declaration marked as
         * primary if more than one declaration of the same name is
         * annotated {@link PythonMethod}. Its value is
         * {@code Integer.MAX_VALUE} until the primary definition of the
         * method has been encountered, after which it is somewhere
         * between 0 and {@link #regargcount} inclusive.
         */
        int posonlyargcount = Integer.MAX_VALUE;

        /**
         * The number of keyword-only parameters (equals the number of
         * positional parameters. This is derived from the
         * {@link KeywordOnly} annotation. If more than one declaration
         * of the same name is annotated {@link PythonMethod}, it must
         * be specified in the method declaration marked as primary.
         */
        int kwonlyargcount;

        /**
         * Default values supplied on positional parameters (not just
         * positional-only parameters), or {@code null}.
         */
        Object[] defaults = null;

        /**
         * Default values supplied on keyword-only parameters, or
         * {@code null}.
         */
        Map<Object, Object> kwdefaults = null;

        /**
         * Position of the excess positional collector in
         * {@link #parameterNames} or {@code -1} if there isn't one.
         */
        int varArgsIndex = -1;

        /**
         * Position of the excess keywords collector in
         * {@link #parameterNames} or {@code -1} if there isn't one.
         */
        int varKeywordsIndex = -1;

        /**
         * Argument parser constructed from the other arguments. (Cache
         * for {@link #getParser()}.)
         */
        ArgParser parser;

        /** Kind of method (from a Python perspective). */
        MethodKind methodKind;

        /**
         * Create a description for a callable.
         *
         * @param name of method.
         * @param scopeKind module or type?
         */
        CallableSpec(String name, ScopeKind scopeKind) {
            super(name, scopeKind);
        }

        /**
         * Check that {@link #processParameters(Method, boolean)} has
         * been called for a primary definition.
         */
        private boolean isDefined() {
            return parameterNames != null
                    && regargcount <= parameterNames.length;
        }

        /**
         * @return true if positional argument collector defined.
         */
        private boolean hasVarArgs() {
            return varArgsIndex >= 0;
        }

        /**
         * @return true if keyword argument collector defined.
         */
        private boolean hasVarKeywords() {
            return varKeywordsIndex >= 0;
        }

        /**
         * Get the argument parser belonging to this
         * {@link CallableSpec}. The many attributes established by
         * {@link #add(Method, boolean, boolean, MethodKind)}, and the
         * parameters of the primary call, determine the attributes of
         * this {@link CallableSpec}.
         * <p>
         * After the processing the primary call, the method signature
         * is known, and it is possible to create a parser. Before that,
         * this method will return {@code null}.
         *
         * @return the parser
         */
        ArgParser getParser() {
            if (parser == null && parameterNames != null
                    && parameterNames.length >= posonlyargcount) {
                parser = new ArgParser(name, scopeKind, methodKind,
                        varArgsIndex >= 0, varKeywordsIndex >= 0,
                        posonlyargcount, kwonlyargcount, parameterNames,
                        regargcount);
                parser.defaults(defaults).kwdefaults(kwdefaults);
            }
            return parser;
        }

        /**
         * Produce a method definition from this specification that
         * references a method handle on the (single) defining method
         * and the parser created from this specification.
         *
         * @param lookup authorisation to access methods
         * @return corresponding method definition
         * @throws InterpreterError on lookup prohibited
         */
        MethodDef getMethodDef(Lookup lookup) throws InterpreterError {
            // Specialise the MethodDef according to the signature.
            assert methods.size() == 1;
            Method m = methods.get(0);
            MethodHandle mh;
            try {
                mh = lookup.unreflect(m);
            } catch (IllegalAccessException e) {
                throw cannotGetHandle(m, e);
            }
            return MethodDef.forInstance(getParser(), mh);
        }

        /**
         * Convenience function to compose error in getMethodDef().
         *
         * @param m method we were working on
         * @param e what went wrong
         * @return an exception to throw
         */
        protected InterpreterError cannotGetHandle(Method m,
                IllegalAccessException e) {
            return new InterpreterError(e, CANNOT_GET_HANDLE,
                    m.getName(), m.getDeclaringClass());
        }

        private static final String CANNOT_GET_HANDLE =
                "cannot get method handle for '%s' in '%s'";

        /**
         * Convenience function to compose error in getMethodDef().
         *
         * @param mh handle from reflected method
         * @return an exception to throw
         */
        protected InterpreterError
                methodSignatureError(MethodHandle mh) {
            return new InterpreterError(UNSUPPORTED_SIG, name,
                    mh.type());
        }

        private static final String UNSUPPORTED_SIG =
                "method %.50s has wrong signature %.50s for spec";

        /**
         * Add a method implementation. (A test that the signature is
         * acceptable follows when we construct the
         * {@link PyMethodDescr}.)
         *
         * @param method to add to {@link #methods}
         * @param primary definition is the primary one
         * @param positionalOnly this method begins with positional-only
         *     parameters
         * @param methodKind instance, static or class?
         */
        void add(Method method, boolean primary, boolean positionalOnly,
                MethodKind methodKind) throws InterpreterError {

            // Check for defined static (in Java, not Python)
            int modifiers = method.getModifiers();
            boolean javaStatic = (modifiers & Modifier.STATIC) != 0;

            int n = method.getParameterCount();

            /*
             * Depending on method kind, when Java static, the parameter
             * list may have to omit the first declared parameter.
             */
            boolean skipFirst;
            if (methodKind == MethodKind.INSTANCE) {
                /*
                 * In the default INSTANCE case, we are implementing a
                 * Python instance method. If the Java method is static,
                 * skip the first parameter "self".
                 */
                skipFirst = javaStatic;

                if (javaStatic
                        && (n < 1 || method.getParameterTypes()[0]
                                .isPrimitive())) {
                    throw new InterpreterError(MUST_HAVE_SELF,
                            getJavaName(), scopeKind.selfName);
                }

            } else if (javaStatic) {
                /*
                 * If CLASS, The (static) Java signature begins with a
                 * {@link PyType}, but if STATIC, the parameters from a
                 * Python perspective are as in the Java definition.
                 */
                skipFirst = methodKind == MethodKind.CLASS;

            } else {
                /*
                 * These other cases can *only* be implemented as static
                 * in Java.
                 */
                throw new InterpreterError(MUST_BE_JAVA_STATIC,
                        getJavaName(),
                        methodKind.toString().toLowerCase());
            }

            /*
             * If not declared static in Java, the effective signature
             * in Python must have a leading "self" (or "module")
             * parameter not declared in the Method object.
             */
            if (skipFirst) { n -= 1; }

            if (methods.isEmpty()) {
                /*
                 * First method definition of this name. Allocate
                 * storage for parameter names. We shall store the names
                 * only if this is also the primary definition, (as well
                 * as the first), but will always check the number of
                 * parameters against this size.
                 */
                parameterNames = n == 0 ? NO_STRINGS : new String[n];

            } else if (n != parameterNames.length) {
                // Number of parameters differs.
                throw new InterpreterError(FURTHER_DEF_ARGS,
                        getJavaName(), n, parameterNames.length);
            }

            // Add to methods
            super.add(method);

            if (primary) {
                // Primary definition defines the signature
                if (isDefined())
                    throw new InterpreterError(ONE_PRIMARY,
                            getJavaName());
                // Whether static, instance or class
                this.methodKind = methodKind;

                /*
                 * If annotated positionalOnly=false, the method has no
                 * positional-only parameters. If not so annotated, then
                 * positionalOnly=true, and all parameters (after any
                 * "self") are positional-only, until a parameter
                 * annotated @PositionalOnly puts an end to that.
                 */
                if (!positionalOnly) { posonlyargcount = 0; }

                // There may be a @DocString annotation
                DocString docAnno =
                        method.getAnnotation(DocString.class);
                if (docAnno != null) { doc = docAnno.value(); }

                /*
                 * Process the sequence of parameters and their
                 * annotations.
                 */
                processParameters(method, skipFirst);

            } else {
                // This is not the primary definition
                disallowAnnotation(method, DocString.class);
                for (Parameter p : method.getParameters()) {
                    disallowAnnotations(p);
                }
            }
        }

        /** Empty names array. */
        private static final String[] NO_STRINGS = new String[0];

        private static final String FURTHER_DEF_ARGS =
                "Further definition of '%s' has %d (not %d) arguments";

        private static final String ONE_PRIMARY =
                "All but one definition of '%s' should have "
                        + "element primary=false";

        private static final String MUST_BE_JAVA_STATIC =
                "The definition of '%s' should be Java static "
                        + "because it is a Python %s method";

        private static final String MUST_HAVE_SELF =
                "Instance method '%s' should have a '%s' parameter "
                        + "because it is Java static";

        /**
         * Scan the parameters of the method being defined looking for
         * annotations that determine the specification of the method as
         * exposed to Python, and which are held temporarily by this
         * {@code MethodSpecification}.
         * <p>
         * Although the annotations do not all work in isolation, their
         * effect may be summarised:
         * <table>
         * <tr>
         * <th>Annotation</th>
         * <th>Effect on fields</th>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link Name}</td>
         * <td>Renames the parameter where needed (e.g. we want to call
         * it "new"). This, or the simple parameter name, appear in at
         * the correct position in {@link #parameterNames}</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link Default}</td>
         * <td>Provides the default value in {@link #defaults} or
         * {@link #kwdefaults}.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link PositionalOnly}</td>
         * <td>Sets {@link #posonlyargcount} to that parameter.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link KeywordOnly}</td>
         * <td>Determines {@link #kwonlyargcount} from a count of this
         * and the regular (non-collector) arguments following.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link PositionalCollector}</td>
         * <td>Designates the collector of excess arguments given by
         * position. (Must follow all regular arguments.) Sets
         * {@link #haveVarargs}.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link KeywordCollector}</td>
         * <td>Designates the collector of excess arguments given by
         * keyword. (Must follow all regular arguments and any
         * positional collector.) Sets {@link #haveVarkwargs}.</td>
         * </tr>
         * </table>
         * <p>
         * When implementing a Python instance method by means of a
         * static Java method, the Java signature begins with a
         * parameter representing the instance operated on. Also, when
         * defining a Python class method the Java method must be static
         * and the Java signature begins with a {@link PyType} parameter
         * representing the type operated on.
         * <p>
         * In both these cases, the client code will ask to skip this
         * first parameter when building the description. This is
         * because, at the point the parser is used, the corresponding
         * first argument is being carried separately from the argument
         * list. (think of the way {@code str.replace} is processed, or
         * {@code int.from_bytes}.
         * <p>
         * A Python static method, Python instance method defined by a
         * Java instance method, and a top-level function in a module,
         * all have parameters exactly as named in the {@code Method}.
         * Any instance implied by a non-static declaration ("self" or
         * "module") remains implicit.
         *
         * @param method being defined
         * @param skipFirst skip the first declared parameter
         */
        private void processParameters(Method method,
                boolean skipFirst) {
            /*
             * This should have the same logic as
             * ArgParser.fromSignature, except that in the absence of
             * a @PositionalOnly annotation, the default is as supplied
             * by the method annotation (already processed). Rather than
             * "/" and "*" markers in the parameter sequence, we find
             * annotations on the parameters themselves.
             */

            // Collect the names of the parameters here
            ArrayList<String> names = new ArrayList<>();

            // Count regular (non-collector) parameters
            int count = 0;

            // Collect the default values here
            ArrayList<Object> posDefaults = null;

            // Indices of specific markers
            int kwOnlyIndex = Integer.MAX_VALUE;

            /*
             * Scan parameters, looking out for Name, Default,
             * PositionalOnly, KeywordOnly, PositionalCollector and
             * KeywordCollector annotations.
             */
            Parameter[] pp = method.getParameters();

            for (int ip = skipFirst ? 1 : 0; ip < pp.length; ip++) {

                // The parameter currently being processed
                Parameter p = pp[ip];

                // index of parameter in Python != ip, possibly
                int i = names.size();

                // Use a replacement Python name if annotated @Name
                Name name = p.getAnnotation(Name.class);
                String paramName =
                        name == null ? p.getName() : name.value();
                names.add(paramName);

                // Pick up all the other annotations on p
                PositionalOnly pos =
                        p.getAnnotation(PositionalOnly.class);
                KeywordOnly kwd = p.getAnnotation(KeywordOnly.class);
                Default def = p.getAnnotation(Default.class);
                PositionalCollector posColl =
                        p.getAnnotation(PositionalCollector.class);
                KeywordCollector kwColl =
                        p.getAnnotation(KeywordCollector.class);

                // Disallow these on the same parameter
                notUsedTogether(method, paramName, pos, kwd, posColl,
                        kwColl);
                notUsedTogether(method, paramName, def, posColl);
                notUsedTogether(method, paramName, def, kwColl);

                /*
                 * We have eliminated the possibility of disallowed
                 * combinations of annotations, so we can process the
                 * parameter types as alternatives.
                 */
                if (pos != null) {
                    // p is the (last) @PositionalOnly parameter
                    posonlyargcount = i + 1;

                } else if (kwd != null
                        && kwOnlyIndex == Integer.MAX_VALUE) {
                    // p is the (first) @KeywordOnly parameter
                    kwOnlyIndex = i;

                } else if (posColl != null) {
                    // p is the @PositionalCollector
                    varArgsIndex = i;

                } else if (kwColl != null) {
                    // p is the @KeywordCollector
                    varKeywordsIndex = i;
                }

                /*
                 * Check for a default value @Default. The value is a
                 * String we must interpret to Python.
                 */
                if (def != null) {
                    /*
                     * We know p is not a *Collector parameter, but our
                     * actions depend on whether it is positional or
                     * keyword-only.
                     */
                    if (i < kwOnlyIndex) {
                        // p is a positional parameter with a default
                        if (posDefaults == null)
                            posDefaults = new ArrayList<>();
                        posDefaults.add(eval(def.value()));
                    } else { // i >= kwOnlyIndex
                        // p is a keyword-only parameter with a default
                        if (kwdefaults == null)
                            kwdefaults = new HashMap<Object, Object>();
                        kwdefaults.put(paramName, eval(def.value()));
                    }

                } else if (posDefaults != null && i < kwOnlyIndex) {
                    /*
                     * Once we have started collecting positional
                     * default values, all subsequent positional
                     * parameters must have a default.
                     */
                    throw new InterpreterError(MISSING_DEFAULT,
                            getJavaName(), paramName);
                }

                /*
                 * Parameters not having *Collector annotations are
                 * "regular". Keep count of them, and check we have not
                 * yet defined either collector.
                 */
                if (kwColl == null) {
                    /*
                     * The parameter is a regular one or a collector of
                     * excess positional arguments.
                     */
                    if (hasVarKeywords())
                        // ... which comes after a keywords collector
                        throw new InterpreterError(FOLLOWS_KW_COLLECTOR,
                                getJavaName(), paramName);
                    if (posColl == null) {
                        // The parameter is a regular one
                        if (hasVarArgs())
                            // .. after a positional collector
                            throw new InterpreterError(
                                    FOLLOWS_POS_COLLECTOR,
                                    getJavaName(), paramName);
                        // A regular one in the right place
                        count = i + 1;
                    }
                }
            }

            /*
             * Some checks and assignments we can only do when we've
             * seen all the parameters.
             */
            regargcount = count;
            posonlyargcount = Math.min(posonlyargcount, count);
            kwonlyargcount = count - Math.min(kwOnlyIndex, count);

            if (posDefaults != null) {
                defaults = posDefaults.toArray();
            }

            int n = names.size();
            assert n == parameterNames.length;
            if (n > 0) { names.toArray(parameterNames); }
        }

        private static final String PARAM = "'%s' parameter '%s' ";
        private static final String MISSING_DEFAULT =
                PARAM + "missing default value";
        private static final String FOLLOWS_POS_COLLECTOR =
                PARAM + "follows postional argument collector";
        private static final String FOLLOWS_KW_COLLECTOR =
                PARAM + "follows keyword argument collector";
        private static final String ANNOTATIONS_TOGETHER =
                PARAM + "annotations %s may not appear together";

        /**
         * Check that only one of the annotations (on a given parameter)
         * is null.
         *
         * @param method within which parameter appears
         * @param paramName its name
         * @param anno the annotations to check
         * @throws InterpreterError if more than one not {@code null}.
         */
        private void notUsedTogether(Method method, String paramName,
                Annotation... anno) throws InterpreterError {
            // Is there a problem?
            int count = 0;
            for (Annotation a : anno) { if (a != null) { count++; } }
            if (count > 1) {
                // There is a problem: collect the details.
                StringJoiner sj = new StringJoiner(",");
                for (Annotation a : anno) {
                    String name = a.annotationType().getSimpleName();
                    if (a != null) { sj.add(name); }
                }
                throw new InterpreterError(ANNOTATIONS_TOGETHER,
                        getJavaName(), paramName, sj);
            }
        }

        /**
         * Poor man's eval() specifically for default values in built-in
         * methods.
         */
        private Object eval(String s) {
            if (s == null || s.equals("None")) {
                return Py.None;
            } else if (s.matches(REGEX_INT)) {
                // Small integer if we can; big if we can't
                BigInteger b = new BigInteger(s);
                try {
                    return b.intValueExact();
                } catch (ArithmeticException e) {
                    return b;
                }
            } else if (s.matches(REGEX_FLOAT)) {
                return Float.valueOf(s);
            } else if (s.matches(REGEX_STRING)) {
                return Float.valueOf(s);
            } else {
                // A somewhat lazy fall-back
                return s;
            }
        }

        private static String REGEX_INT = "-?\\d+";
        private static String REGEX_FLOAT =
                "[-+]?\\d+\\.\\d*((e|E)[-+]?\\d+)?";
        private static String REGEX_STRING = "('[~']*'|\"[~\"]*\")";

        /**
         * Check that the method has no annotation of the given type.
         *
         * @param method to process
         * @parame annoClass type of annotation disallowed
         */
        private void disallowAnnotation(Method method,
                Class<? extends Annotation> annoClass) {
            Annotation a = method.getAnnotation(annoClass);
            if (a != null) {
                String annoName = a.annotationType().getSimpleName();
                throw new InterpreterError(SECONDARY_DEF_ANNO,
                        getJavaName(), annoName);
            }
        }

        private static final String SECONDARY_DEF_ANNO =
                "Secondary definition of '%s' "
                        + "has disallowed annotation '%s'";

        /**
         * Check that the parameter has no annotations
         * &#064;{@link Name}, &#064;{@link PositionalOnly}, and
         * &#064;{@link KeywordOnly}.
         *
         * @param p to process
         */
        private void disallowAnnotations(Parameter p) {
            for (Class<? extends Annotation> annoClass : DISALLOWED_PAR_ANNOS) {
                Annotation a = p.getAnnotation(annoClass);
                if (a != null) {
                    String annoName =
                            a.annotationType().getSimpleName();
                    throw new InterpreterError(SECONDARY_DEF_PAR_ANNO,
                            getJavaName(), p.getName(), annoName);
                }
            }
        }

        /**
         * Parameter annotations disallowed on a secondary definition.
         */
        private static final List<Class<? extends Annotation>> //
        DISALLOWED_PAR_ANNOS = List.of(Name.class, PositionalOnly.class,
                KeywordOnly.class, Default.class);

        private static final String SECONDARY_DEF_PAR_ANNO =
                "Secondary definition of '%s' parameter '%s' "
                        + "has disallowed annotation '%s'";
    }

    /**
     * Specification in which we assemble information about a Python
     * instance method in advance of creating a method definition or
     * method descriptor.
     */
    static class MethodSpec extends CallableSpec {

        MethodSpec(String name, ScopeKind scopeKind) {
            super(name, scopeKind);
        }

        @Override
        Class<? extends Annotation> annoClass() {
            return PythonMethod.class;
        }

        /**
         * {@inheritDoc}
         * <p>
         * In a type, the attribute must be represented by a descriptor
         * for the Python method from this specification.
         * <p>
         * Note that specification may have collected multiple Java
         * definitions of the same name. This method creates a
         * descriptor that matches them to the accepted implementations
         * of the owning class.
         *
         * @return descriptor for access to the methods
         * @throws InterpreterError if the method type is not supported
         */
        @Override
        PyMethodDescr asAttribute(PyType objclass, Lookup lookup) {
            if (objclass.acceptedCount == 1)
                return createDescrSingle(objclass, lookup);
            else
                return createDescrMultiple(objclass, lookup);
        }

        /**
         * Create a {@code PyMethodDescr} from this specification when
         * there is only one defining method.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access members
         * @return descriptor for the method
         * @throws InterpreterError if the method type is not supported
         */
        private PyMethodDescr createDescrSingle(PyType objclass,
                Lookup lookup) throws InterpreterError {

            assert methods.size() == 1;

            // Construct a parser for call arguments
            ArgParser ap = new ArgParser(name, scopeKind,
                    MethodKind.INSTANCE, varArgsIndex >= 0,
                    varKeywordsIndex >= 0, posonlyargcount,
                    kwonlyargcount, parameterNames, regargcount);
            ap.defaults(defaults).kwdefaults(kwdefaults);

            // MethodHandles have self + this many args:
            final int L = parameterNames.length;

            // Unreflect m to its raw/natural method handle

            // Check a sufficient raw match (Clinic?)

            // Form a MethodDef from the parser and handle
            // * Choose specialisation
            // * Prepare handle in MethodDef from raw handle

            // Form a descriptor from objclass and method def

            Method m = methods.get(0);
            MethodHandle mh = null;
            // Convert m to a handle (if L args and accessible)
            try {
                mh = lookup.unreflect(m);
                if (mh.type().parameterCount() != 1 + L) { mh = null; }
            } catch (IllegalAccessException e) {
                throw cannotGetHandle(m, e);
            }

            MethodDef methodDef = null;

            Class<?> acceptedClass = objclass.getJavaClass();
            MethodType mt = mh.type();
            if (mt.parameterType(0).isAssignableFrom(acceptedClass)) {
                /*
                 * Each sub-type of MethodDef handles callMethod(self,
                 * args, kwargs) in its own way, and must prepare the
                 * arguments of the generic method handle to match.
                 */
                try {
                    methodDef = MethodDef.forInstance(ap, mh);
                } catch (WrongMethodTypeException wmte) {
                    // Wrong number of args or cannot cast.
                    throw methodSignatureError(mh);
                }
            }

            // We should have a value in method
            if (methodDef == null) {
                throw new InterpreterError("'%s.%s' not defined for %s",
                        objclass.name, name, objclass.getJavaClass());
            }

            /*
             * There is only one definition so use the simpler form of
             * built-in method. This is the frequent case.
             */
            return new PyMethodDescr.Single(objclass, methodDef);
        }

        /**
         * Create a {@code PyMethodDescr} from this specification. Note
         * that a specification describes the methods as declared, and
         * that there may be any number of them. This method matches
         * them to the supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyMethodDescr createDescrMultiple(PyType objclass,
                Lookup lookup) throws InterpreterError {

            ArgParser ap = new ArgParser(name, scopeKind,
                    MethodKind.INSTANCE, varArgsIndex >= 0,
                    varKeywordsIndex >= 0, posonlyargcount,
                    kwonlyargcount, parameterNames, regargcount);
            ap.defaults(defaults).kwdefaults(kwdefaults);

            // Methods have self + this many args:
            final int L = regargcount;

            // Specialise the MethodDef according to the signature.
            MethodDef methodDef = MethodDef.forInstance(ap, null);

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
                    if (mh.type().parameterCount() == 1 + L)
                        addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw cannotGetHandle(m, e);
                }
            }

            /*
             * We will try to create a handle for each implementation of
             * an instance method.
             */
            final int N = objclass.acceptedCount;
            MethodHandle[] method = new MethodHandle[N];

            // Fill the method array with matching method handles
            for (int i = 0; i < N; i++) {
                Class<?> acceptedClass = objclass.classes[i];
                /*
                 * Fill method[i] with the method handle where the first
                 * parameter is the most specific match for class
                 * accepted[i].
                 */
                // Try the candidate method until one matches
                for (MethodHandle mh : candidates) {
                    MethodType mt = mh.type();
                    if (mt.parameterType(0)
                            .isAssignableFrom(acceptedClass)) {
                        /*
                         * Each sub-type of MethodDef handles
                         * callMethod(self, args, kwargs) in its own
                         * way, and must prepare the arguments of the
                         * generic method handle to match.
                         */
                        try {
                            method[i] = methodDef.prepare(mh);
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(mh);
                        }
                        break;
                    }
                }

                // We should have a value in each of method[]
                if (method[i] == null) {
                    throw new InterpreterError(
                            "'%s.%s' not defined for %s", objclass.name,
                            name, objclass.classes[i]);
                }
            }

            /*
             * There are multiple definitions so use the array form of
             * built-in method. This is the case for types that have
             * multiple accepted implementations and methods on them
             * that are not static or "Object self".
             */
            return new PyMethodDescr.Multiple(objclass, methodDef,
                    method);
        }
    }

    /**
     * Specification in which we assemble information about a Python
     * static method in advance of creating a method definition or
     * method object.
     */
    static class StaticMethodSpec extends CallableSpec {

        StaticMethodSpec(String name, ScopeKind scopeKind) {
            super(name, scopeKind);
        }

        @Override
        PyJavaMethod asAttribute(PyType objclass, Lookup lookup) {
            // TODO Auto-generated method stub
            return null;
        }

        @Override
        Class<? extends Annotation> annoClass() {
            return PythonStaticMethod.class;
        }
    }
}
