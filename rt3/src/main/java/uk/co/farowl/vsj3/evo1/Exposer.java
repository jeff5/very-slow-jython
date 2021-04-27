package uk.co.farowl.vsj3.evo1;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.Exposed.JavaMethod;
import uk.co.farowl.vsj3.evo1.Operations.BinopGrid;
import uk.co.farowl.vsj3.evo1.PyMethodDescr.MethodSpecification;
// import uk.co.farowl.vsj3.evo1.PyGetSetDescr.GetSetDef;
// import uk.co.farowl.vsj3.evo1.PyMemberDescr.Flag;
import uk.co.farowl.vsj3.evo1.PyWrapperDescr.WrapperSpecification;
import uk.co.farowl.vsj3.evo1.Slot.Signature;

/**
 * Methods for tabulating the attributes of classes that define Python
 * types.
 */
class Exposer {

    /**
     * Create a table of {@link Descriptor}s, on behalf of the type
     * given, from definitions found in the given class and either
     * annotated for exposure or having the name of a special method.
     * The type object will become the {@link Descriptor#objclass}
     * reference of the descriptors created, but is not otherwise
     * accessed, since it is (necessarily) incomplete at this time.
     *
     * @param lookup authorisation to access methods
     * @param definingClass to introspect for special functions
     * @param methodClass to introspect additionally (if non-null)
     * @param type to which these descriptors apply
     * @return attributes defined (in the order first encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, Descriptor> descriptorsFromMethods(Lookup lookup,
            Class<?> definingClass, Class<?> methodClass, PyType type)
            throws InterpreterError {

        // XXX Make this a member of an instance of Exposer, per PyType?
        Map<String, DescriptorSpecification> specs =
                new LinkedHashMap<>();

        // Scan the defining and methods classes for definitions
        addMethodSpecs(specs, lookup, definingClass);
        if (methodClass != null)
            addMethodSpecs(specs, lookup, methodClass);

        // For each name having a definition, construct a descriptor
        Map<String, Descriptor> descrs = new LinkedHashMap<>();
        for (DescriptorSpecification def : specs.values()) {
            Descriptor d = def.createDescr(type, lookup);
            // PyMethodDescr d = new PyMethodDescr(type, def);
            descrs.put(d.name, d);
        }

        return descrs;
    }

    /**
     * Add to a table of {@link DescriptorSpecification}s, definitions
     * found in the given class and either annotated for exposure or
     * having the name of a special method.
     *
     * @param specs to add definitions (in the order first encountered)
     * @param lookup authorisation to access methods
     * @param defsClass to introspect for definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    private static void addMethodSpecs(
            Map<String, DescriptorSpecification> specs, Lookup lookup,
            Class<?> defsClass) throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        for (Method m : defsClass.getDeclaredMethods()) {

            // Here we must deal with all supported annotations
            JavaMethod a = m.getDeclaredAnnotation(JavaMethod.class);
            if (a != null) { processAnnotation(specs, a, m); }

            // If it is a special method, record the definition.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null) { processSlot(specs, slot, m); }
        }
    }

    /**
     * Process an annotation to a descriptor specification and add it to
     * the table of specifications by name.
     * <p>
     * Python knows methods by a simple name while Java allows there to
     * be multiple definitions separated by signature, and for these to
     * coexist with inherited definitions. We will have to confront this
     * overloading when we come to expose "discovered" Java classes as
     * Python object types. For now, a repeat definition of a name is
     * considered an error.
     *
     * @param specs table to update
     * @param anno annotation encountered
     * @param meth method annotated
     * @throws InterpreterError on duplicates or unsupported types
     */
    static private void processAnnotation(
            Map<String, DescriptorSpecification> specs, JavaMethod anno,
            Method meth) throws InterpreterError {

        // The name is as annotated or the "natural" one
        String name = anno.value();
        if (name == null || name.length() == 0)
            name = meth.getName();

        // Find any existing definition
        DescriptorSpecification descrSpec = specs.get(name);
        MethodSpecification spec;
        if (descrSpec == null) {
            // A new entry is needed
            spec = new MethodSpecification(name);
            specs.put(spec.name, spec);
        } else if (descrSpec instanceof MethodSpecification) {
            // Existing entry will be updated
            spec = (MethodSpecification) descrSpec;
        } else {
            // Existing entry is not compatible
            throw duplicateError(name, descrSpec, meth, anno);
        }
        spec.add(meth);
    }

    /**
     * Process a method that matches a slot name to a descriptor
     * specification and add it to the table of specifications by name.
     *
     * @param specs table to update
     * @param slot annotation encountered
     * @param meth method annotated
     * @throws {@link InterpreterError} on duplicates or unsupported
     *     types
     */
    static private void processSlot(
            Map<String, DescriptorSpecification> specs, Slot slot,
            Method meth) throws InterpreterError {

        String name = slot.methodName;
        // Find any existing definition
        DescriptorSpecification descrSpec = specs.get(name);
        WrapperSpecification spec;
        if (descrSpec == null) {
            // A new entry is needed
            spec = new WrapperSpecification(slot);
            specs.put(name, spec);
        } else if (descrSpec instanceof WrapperSpecification) {
            // Existing entry will be updated
            spec = (WrapperSpecification) descrSpec;
        } else {
            // Existing entry is not compatible
            throw duplicateError(name, descrSpec, meth, "slot-wrapper");
        }
        spec.add(meth);
    }

    /**
     * Create an exception with a message along the lines "'NAME',
     * already exposed as SPEC, cannot be ANNO (method METH)" where the
     * place-holders are filled from the corresponding arguments (or
     * their names or type names).
     *
     * @param name being defined
     * @param spec of the inconsistent, existing entry
     * @param meth method annotated
     * @param anno troublesome new annotation
     * @return the required error
     */
    static private InterpreterError duplicateError(String name,
            DescriptorSpecification spec, Method meth,
            Annotation anno) {
        return duplicateError(name, spec, meth,
                anno.annotationType().getSimpleName());
    }

    /**
     * Create an exception with a message along the lines "'NAME',
     * already exposed as SPEC, cannot be TYPE (method METH)" where the
     * place-holders are filled from the corresponding arguments (or
     * their names or type names).
     *
     * @param name being defined
     * @param spec of the inconsistent, existing entry
     * @param meth method annotated
     * @param type troublesome new entry type
     * @return the required error
     */
    static private InterpreterError duplicateError(String name,
            DescriptorSpecification spec, Method meth, String type) {
        return new InterpreterError(ALREADY_EXPOSED, name,
                spec.getType(), type, meth.getName());
    }

    private static final String ALREADY_EXPOSED =
            "'%s', already exposed as %s, cannot be %s (method %s)";

//@formatter:off
//    /**
//     * Create a table of {@link PyMemberDescr}s annotated on the given
//     * implementation class, on behalf of the type given. This type
//     * object will become the {@link Descriptor#objclass} reference of
//     * the descriptors created, but is not otherwise accessed, since it
//     * is (necessarily) incomplete at this time.
//     *
//     * @param lookup authorisation to access fields
//     * @param implClass to introspect for member definitions
//     * @param type to which these descriptors apply
//     * @return members defined (in the order encountered)
//     * @throws InterpreterError on duplicates or unsupported types
//     */
//    static Map<String, PyMemberDescr> memberDescrs(Lookup lookup,
//            Class<?> implClass, PyType type) throws InterpreterError {
//
//        Map<String, PyMemberDescr> defs = new LinkedHashMap<>();
//
//        for (Field f : implClass.getDeclaredFields()) {
//            Exposed.Member a =
//                    f.getDeclaredAnnotation(Exposed.Member.class);
//            if (a != null) {
//                PyMemberDescr def = getMemberDescr(type, f, lookup);
//                DataDescriptor previous = defs.put(def.name, def);
//                if (previous != null) {
//                    // There was one already :(
//                    throw new InterpreterError(MEMBER_REPEAT, def.name,
//                            implClass.getSimpleName());
//                }
//            }
//        }
//        return defs;
//    }
//
//    /** Build one member descriptor for the given field. */
//    private static PyMemberDescr getMemberDescr(PyType type, Field f,
//            Lookup lookup) {
//
//        String name = null;
//
//        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
//
//        // Get the exposed name.
//        Exposed.Member memberAnno =
//                f.getAnnotation(Exposed.Member.class);
//        if (memberAnno != null) {
//            name = memberAnno.value();
//            if (memberAnno.readonly())
//                flags.add(Flag.READONLY);
//            if (memberAnno.optional())
//                flags.add(Flag.OPTIONAL);
//        }
//
//        // May also have DocString annotation
//        String doc = "";
//        Exposed.DocString d = f.getAnnotation(Exposed.DocString.class);
//        if (d != null)
//            doc = d.value();
//
//        // From all these parts, construct a descriptor.
//        return PyMemberDescr.forField(type, name, f, lookup, flags,
//                doc);
//    }
//
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
     * Create a table of {@code MethodHandle}s from binary operations
     * defined in the given class, on behalf of the type given. This
     * table is 3-dimensional, being indexed by the slot of the method
     * being defined, which must be a binary operation, and the indices
     * of the operand classes in the type. These handles are used
     * privately by the type to create call sites. Although the process
     * of creating them is similar to making wrapper descriptors, these
     * structures do not become exposed as descriptors.
     *
     * @param lookup authorisation to access methods
     * @param binops to introspect for binary operations
     * @param type to which these descriptors apply
     * @return attributes defined (in the order first encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<Slot, BinopGrid> binopTable(Lookup lookup,
            Class<?> binops, PyType type) throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        Map<Slot, BinopGrid> defs = new HashMap<>();

        for (Method m : binops.getDeclaredMethods()) {
            // If it is a special method, record the definition.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null && slot.signature == Signature.BINARY) {
                binopTableAdd(defs, slot, m, lookup, binops, type);
            }
        }

        // Check for nulls in the table.
        for (BinopGrid grid : defs.values()) { grid.checkFilled(); }

        return defs;
    }

    /**
     * Add a method handle to the table, verifying that the method type
     * produced is compatible with the {@link #slot}.
     *
     * @param defs the method table to add to
     * @param slot being matched
     * @param m implementing method
     * @param lookup authorisation to access fields
     * @param binops class defining class-specific binary operations
     * @param type to which these belong
     */
    static void binopTableAdd(Map<Slot, BinopGrid> defs, Slot slot,
            Method m, Lookup lookup, Class<?> binops, PyType type) {

        // Get (or create) the table for this slot
        BinopGrid def = defs.get(slot);
        if (def == null) {
            // A new special method has been encountered
            def = new BinopGrid(slot, type);
            defs.put(slot, def);
        }

        try {
            // Convert the method to a handle
            def.add(lookup.unreflect(m));
        } catch (IllegalAccessException | WrongMethodTypeException e) {
            throw new InterpreterError(e,
                    "ill-formed or inaccessible binary op '%s'", m);
        }
    }
}
