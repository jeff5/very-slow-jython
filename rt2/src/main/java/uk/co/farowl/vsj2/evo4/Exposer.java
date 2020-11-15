package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.DataDescriptor.Flag;

/** Methods for tabulating the attributes of classes that define Python types.
 *
 */
class Exposer {

    protected static final MethodHandles.Lookup lookup =
            MethodHandles.lookup();

    /**
     * Create a table of {@link PyMemberDescr}s annotated on the given
     * implementation class, on behalf of the type given.
     * This type object will become the {@link Descriptor#objclass}
     * reference of the descriptors created, but is not otherwise accessed, since it is (necessarily)
     * incomplete at this time.
     * <p>
     * Notice this is a map from {@code String} to descriptor, even
     * though later on we will make a map from keys that are all
     * {@link PyUnicode}. The purpose is to avoid a circular dependency
     * in early in the creation of the type system, where exposing
     * {@code PyUnicode} as {@code str} would require it to exist
     * already.
     *
     * @param lookup authorisation to access fields
     * @param implClass to introspect for member definitions
     * @param type to introspect for member definitions
     * @return members defined (in the order encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyMemberDescr> memberDescrs(
            Lookup lookup, Class<?> implClass, PyType type) throws InterpreterError {

        Map<String, PyMemberDescr> defs = new LinkedHashMap<>();
        if( implClass==null) {implClass = lookup.lookupClass();}
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
                flags.add(DataDescriptor.Flag.READONLY);
            if (memberAnno.optional())
                flags.add(DataDescriptor.Flag.OPTIONAL);
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

    protected static final String MEMBER_REPEAT =
            "Repeated definition of member %.50s in type %.50s";

}
