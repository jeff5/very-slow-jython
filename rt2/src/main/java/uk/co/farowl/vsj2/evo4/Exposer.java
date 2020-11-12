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

class Exposer {

    protected static final MethodHandles.Lookup lookup =
            MethodHandles.lookup();

    /**
     * Create a table of {@link PyMemberDescr}s for the given type and
     * lookup class.
     *
     * @param type to introspect for member definitions
     * @param lookup authorisation to access fields
     * @return members defined (in the order encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, PyMemberDescr> memberDescrs(PyType type,
            Lookup lookup) throws InterpreterError {

        Map<String, PyMemberDescr> defs = new LinkedHashMap<>();
        Class<?> implClass = type.implClass;
        for (Field f : implClass.getDeclaredFields()) {
            Exposed.Member a =
                    f.getDeclaredAnnotation(Exposed.Member.class);
            if (a != null) {
                PyMemberDescr def = getMemberDescr(type, f, lookup);
                PyMemberDescr previous = defs.put(def.name, def);
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
            if (memberAnno.readonly()) {
                flags.add(PyMemberDescr.Flag.READONLY);
            }
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
