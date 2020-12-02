package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.Exposed.Deleter;
import uk.co.farowl.vsj2.evo4.Exposed.DocString;
import uk.co.farowl.vsj2.evo4.Exposed.Getter;
import uk.co.farowl.vsj2.evo4.Exposed.Setter;
import uk.co.farowl.vsj2.evo4.PyGetSetDescr.GetSetDef;
import uk.co.farowl.vsj2.evo4.PyMemberDescr.Flag;

/**
 * Methods for tabulating the attributes of classes that define Python
 * types.
 */
class Exposer {

    private Exposer() {} // No instances

    /**
     * Create a table of {@link PyMemberDescr}s annotated on the given
     * implementation class, on behalf of the type given. This type
     * object will become the {@link Descriptor#objclass} reference of
     * the descriptors created, but is not otherwise accessed, since it
     * is (necessarily) incomplete at this time.
     *
     * @param lookup authorisation to access fields
     * @param implClass to introspect for member definitions
     * @param type to which these descriptors apply
     * @return members defined (in the order encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyMemberDescr> memberDescrs(Lookup lookup,
            Class<?> implClass, PyType type) throws InterpreterError {

        Map<String, PyMemberDescr> defs = new LinkedHashMap<>();

        for (Field f : implClass.getDeclaredFields()) {
            Exposed.Member a =
                    f.getDeclaredAnnotation(Exposed.Member.class);
            if (a != null) {
                PyMemberDescr def = getMemberDescr(type, f, lookup);
                DataDescriptor previous = defs.put(def.name, def);
                if (previous != null) {
                    // There was one already :(
                    throw new InterpreterError(MEMBER_REPEAT, def.name,
                            implClass.getSimpleName());
                }
            }
        }
        return defs;
    }

    /** Build one member descriptor for the given field. */
    private static PyMemberDescr getMemberDescr(PyType type, Field f,
            Lookup lookup) {

        String name = null;

        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

        // Get the exposed name.
        Exposed.Member memberAnno =
                f.getAnnotation(Exposed.Member.class);
        if (memberAnno != null) {
            name = memberAnno.value();
            if (memberAnno.readonly())
                flags.add(Flag.READONLY);
            if (memberAnno.optional())
                flags.add(Flag.OPTIONAL);
        }

        // May also have DocString annotation
        String doc = "";
        Exposed.DocString d = f.getAnnotation(Exposed.DocString.class);
        if (d != null)
            doc = d.value();

        // From all these parts, construct a descriptor.
        return PyMemberDescr.forField(type, name, f, lookup, flags,
                doc);
    }

    /**
     * Create a table of {@link PyGetSetDescr}s annotated on the given
     * implementation class, on behalf of the type given. This type
     * object will become the {@link Descriptor#objclass} reference of
     * the descriptors created, but is not otherwise accessed, since it
     * is (necessarily) incomplete at this time.
     *
     * @param lookup authorisation to access methods
     * @param implClass to introspect for getters, setters and deleters
     * @param type to which these descriptors apply
     * @return attributes defined (in the order first encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyGetSetDescr> getsetDescrs(Lookup lookup,
            Class<?> implClass, PyType type) throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        Map<String, GetSetDef> defs = new LinkedHashMap<>();

        for (Method m : implClass.getDeclaredMethods()) {
            // Look for all three types now. so as to detect conflicts.
            Exposed.Getter getterAnno =
                    m.getAnnotation(Exposed.Getter.class);
            Exposed.Setter setterAnno =
                    m.getAnnotation(Exposed.Setter.class);
            Exposed.Deleter deleterAnno =
                    m.getAnnotation(Exposed.Deleter.class);
            String repeated = null;

            // Now process the relevant annotation, if any.
            if ((getterAnno) != null) {
                // There is a Getter annotation: add to definitions
                if (setterAnno != null || deleterAnno != null)
                    throw new InterpreterError(GETSET_MULTIPLE, m,
                            implClass.getSimpleName());
                // Add to definitions.
                repeated = addGetter(defs, getterAnno, m);

            } else if ((setterAnno) != null) {
                // There is a Setter annotation
                if (deleterAnno != null)
                    throw new InterpreterError(GETSET_MULTIPLE, m,
                            implClass.getSimpleName());
                // Add to definitions.
                repeated = addSetter(defs, setterAnno, m);

            } else if ((deleterAnno) != null) {
                // There is a Deleter annotation: add to definitions
                repeated = addDeleter(defs, deleterAnno, m);
            }

            // If set non-null at any point, indicates a repeat.
            if (repeated != null) {
                throw new InterpreterError(GETSET_REPEAT, repeated,
                        m.getName(), implClass.getSimpleName());
            }
        }

        // For each entry found in the class, construct a descriptor
        Map<String, PyGetSetDescr> descrs = new LinkedHashMap<>();
        for (GetSetDef def : defs.values()) {
            descrs.put(def.name, def.create(type, lookup));
        }

        return descrs;
    }

    /**
     * Record a {@link Getter} in the table of {@link GetSetDef}s. The
     * return from this method is {@code null} for success or a
     * {@code String} identifying a duplicate definition.
     *
     * @param defs table of {@link GetSetDef}s
     * @param getterAnno annotation found
     * @param m method annotated
     * @return {@code null} for success or string naming duplicate
     */
    // Using an error return simplifies getsetDescrs() internally.
    private static String addGetter(Map<String, GetSetDef> defs,
            Exposed.Getter getterAnno, Method m) {
        // Get the entry to which we are adding a getter
        GetSetDef def = ensureDefined(defs, getterAnno.value(), m);
        if (def.setGet(m) != null) {
            // There was one already :(
            return "getter for " + def.name;
        }
        // May also have DocString annotation to add.
        return addDoc(def, m);
    }

    /**
     * Record a {@link Setter} in the table of {@link GetSetDef}s. The
     * return from this method is {@code null} for success or a
     * {@code String} identifying a duplicate definition.
     *
     * @param defs table of {@link GetSetDef}s
     * @param getterAnno annotation found
     * @param m method annotated
     * @return {@code null} for success or string naming duplicate
     */
    // Using an error return simplifies getsetDescrs() internally.
    private static String addSetter(Map<String, GetSetDef> defs,
            Exposed.Setter setterAnno, Method m) {
        // Get the entry to which we are adding a getter
        GetSetDef def = ensureDefined(defs, setterAnno.value(), m);
        if (def.setSet(m) != null) {
            // There was one already :(
            return "setter for " + def.name;
        }
        // May also have DocString annotation to add.
        return addDoc(def, m);
    }

    /**
     * Record a {@link Deleter} in the table of {@link GetSetDef}s. The
     * return from this method is {@code null} for success or a
     * {@code String} identifying a duplicate definition.
     *
     * @param defs table of {@link GetSetDef}s
     * @param deleterAnno annotation found
     * @param m method annotated
     * @return {@code null} for success or string naming duplicate
     */
    // Using an error return simplifies getsetDescrs() internally.
    private static String addDeleter(Map<String, GetSetDef> defs,
            Exposed.Deleter deleterAnno, Method m) {
        // Get the entry to which we are adding a getter
        GetSetDef def = ensureDefined(defs, deleterAnno.value(), m);
        if (def.setDelete(m) != null) {
            // There was one already :(
            return "deleter for " + def.name;
        }
        // May also have DocString annotation to add.
        return addDoc(def, m);
    }

    /**
     * Add an entry to the table of {@link GetSetDef}s for the given
     * name.
     *
     * @param defs table of {@link GetSetDef}s
     * @param name to define
     * @param m method (supplies default name)
     * @return new or found definition
     */
    private static GetSetDef ensureDefined(Map<String, GetSetDef> defs,
            String name, Method m) {
        if (name == null || name.length() == 0) { name = m.getName(); }

        // Ensure there is a GetSetDef for the name.
        GetSetDef def = defs.get(name);
        if (def == null) {
            def = new GetSetDef(name);
            defs.put(name, def);
        }
        return def;
    }

    /**
     * Add a doc string if the {@link DocString} annotation is present
     * on the given method.
     *
     * @param def to add it to
     * @param m method in question
     * @return {@code null} or string indicating an error
     */
    private static String addDoc(GetSetDef def, Method m) {
        Exposed.DocString d = m.getAnnotation(Exposed.DocString.class);
        if (d != null) {
            String doc = d.value();
            if (def.setDoc(doc) != null) {
                // There was one already :(
                return "doc string for " + def.name;
            }
        }
        return null;
    }

    /**
     * Create a table of {@link PyWrapperDescr}s defined on the given
     * implementation class, on behalf of the type given. This type
     * object will become the {@link Descriptor#objclass} reference of
     * the descriptors created, but is not otherwise accessed, since it
     * is (necessarily) incomplete at this time.
     *
     * @param lookup authorisation to access methods
     * @param implClass to introspect for getters, setters and deleters
     * @param type to which these descriptors apply
     * @return attributes defined (in the order first encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyWrapperDescr> wrapperDescrs(Lookup lookup,
            Class<?> implClass, PyType type) throws InterpreterError {

        // Iterate over methods looking for the relevant annotations
        Map<String, PyWrapperDescr> descrs = new LinkedHashMap<>();
        for (Method m : implClass.getDeclaredMethods()) {
            // If it is a special method, create a wrapper.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null) {
                try {
                    descrs.put(name, slot.makeDescriptor(type,
                            implClass, lookup));
                } catch (NoSuchMethodException
                        | IllegalAccessException e) {
                    // Although m exists, we could not form handle to it
                    throw new InterpreterError(
                            "cannot get handle to method %s in %s",
                            name, implClass.getSimpleName());
                }
            }
        }

        return descrs;
    }

    private static final String MEMBER_REPEAT =
            "Repeated definition of member %.50s in type %.50s";
    private static final String GETSET_REPEAT =
            "Definition of %s repeated at method %.50s in type %.50s";
    private static final String GETSET_MULTIPLE =
            "Multiple get-set-delete annotations"
                    + " on method %.50s in type %.50s";

}
