package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.Exposed.Deleter;
import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Exposed.JavaMethod;
import uk.co.farowl.vsj3.evo1.Exposed.Setter;
import uk.co.farowl.vsj3.evo1.Operations.BinopGrid;
// import uk.co.farowl.vsj3.evo1.PyGetSetDescr.GetSetDef;
// import uk.co.farowl.vsj3.evo1.PyMemberDescr.Flag;
import uk.co.farowl.vsj3.evo1.PyWrapperDescr.WrapperDef;
import uk.co.farowl.vsj3.evo1.Slot.Signature;

/**
 * Methods for tabulating the attributes of classes that define Python
 * types.
 */
class Exposer {

    private Exposer() {} // No instances

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
     * Create a table of {@link PyWrapperDescr}s defined on the given
     * implementation classes, on behalf of the type given. This type
     * object will become the {@link Descriptor#objclass} reference of
     * the descriptors created, but is not otherwise accessed, since it
     * is (necessarily) incomplete at this time.
     *
     * @param lookup authorisation to access methods
     * @param definingClass to introspect for special functions
     * @param methodClass to introspect additionally (if non-null)
     * @param type to which these descriptors apply
     * @return attributes defined (in the order first encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyWrapperDescr> wrapperDescrs(Lookup lookup,
            Class<?> definingClass, Class<?> methodClass, PyType type)
            throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        Map<Slot, WrapperDef> defs = new LinkedHashMap<>();

        addWrapperDefs(defs, lookup, definingClass);
        if (methodClass != null)
            addWrapperDefs(defs, lookup, methodClass);

        // For each slot having any definitions, construct a descriptor
        Map<String, PyWrapperDescr> descrs = new LinkedHashMap<>();
        for (WrapperDef def : defs.values()) {
            PyWrapperDescr d = def.createDescr(type, lookup);
            descrs.put(d.name, d);
        }

        return descrs;
    }

    /**
     * Add to a table of {@link WrapperDef}s definitions found in the
     * given implementation class.
     *
     * @param defs to add definitions (in the order first encountered)
     * @param lookup authorisation to access methods
     * @param binopsClass to introspect for special functions
     */
    static void addWrapperDefs(Map<Slot, WrapperDef> defs,
            Lookup lookup, Class<?> binopsClass) {
        for (Method m : binopsClass.getDeclaredMethods()) {
            // If it is a special method, record the definition.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null) {
                WrapperDef def = defs.get(slot);
                if (def == null) {
                    // A new entry is needed
                    def = new WrapperDef(slot);
                    defs.put(slot, def);
                }
                def.add(m);
            }
        }
    }

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

//@formatter:off
//    /**
//     * Create a table of {@link PyMethodDescr}s for methods annotated as
//     * {@link JavaMethod} on the given implementation class, on behalf
//     * of the type given. This type object will become the
//     * {@link Descriptor#objclass} reference of the descriptors created,
//     * but is not otherwise accessed, since it is (necessarily)
//     * incomplete at this time.
//     * <p>
//     * Python knows methods by a simple name while Java allows there to
//     * be multiple definitions separated by signature, and for these to
//     * coexist with inherited definitions. We will have to confront this
//     * overloading when we come to expose "discovered" Java classes as
//     * Python object types. For now, a repeat definition of a name is
//     * considered an error.
//     *
//     * @param lookup authorisation to access fields
//     * @param implClass to introspect for method definitions
//     * @param type to which these descriptors apply
//     * @return methods defined (in the order encountered)
//     * @throws InterpreterError on duplicates or unsupported types
//     */
//    public static Map<String, PyMethodDescr> methodDescrs(Lookup lookup,
//            Class<?> implClass, PyType type) throws InterpreterError {
//
//
//        Map<String, PyMethodDescr> defs = new LinkedHashMap<>();
//
//        // Iterate over methods looking for the relevant annotations
//        for (Method m : implClass.getDeclaredMethods()) {
//            // Look for all three types now, so as to detect conflicts.
//            JavaMethod a = m.getDeclaredAnnotation(JavaMethod.class);
//            if (a != null) {
//                MethodDef def = getMethodDef(a, m, lookup);
//                PyMethodDescr descr = new PyMethodDescr(type, def);
//                PyMethodDescr previous = defs.put(def.name, descr);
//                if (previous != null) {
//                    // There was one already :(
//                    throw new InterpreterError(DEF_REPEAT, "method",
//                            def.name, implClass.getSimpleName());
//                }
//            }
//        }
//        return defs;
//
//    }
//
//    private static MethodDef getMethodDef(JavaMethod a, Method m,
//            Lookup lookup) {
//
//        // Name is as annotated or is the Java name of the method
//        String name = a.value();
//        if (name == null || name.length() == 0)
//            name = m.getName();
//
//        // May also have DocString annotation
//        String doc = "";
//        Exposed.DocString d = m.getAnnotation(Exposed.DocString.class);
//        if (d != null)
//            doc = d.value();
//
//        try {
//            // From these parts, construct a definition.
//            return new MethodDef(name, m, lookup, doc);
//        } catch (IllegalAccessException e) {
//            throw new InterpreterError(e,
//                    "cannot get method handle for '%s'", m);
//        }
//    }
//@formatter:on

    private static final String MEMBER_REPEAT =
            "Repeated definition of member %.50s in type %.50s";
    private static final String DEF_REPEAT =
            "Definition of %s repeated at method %.50s in type %.50s";
    private static final String DEF_MULTIPLE =
            "Multiple %s annotations on method %.50s in type %.50s";

}
