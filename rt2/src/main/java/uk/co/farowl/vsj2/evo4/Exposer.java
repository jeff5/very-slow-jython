package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj2.evo4.MemberDef.Flag;

class Exposer {

    protected static final MethodHandles.Lookup lookup =
            MethodHandles.lookup();

    /**
     * Create a table of {@link MemberDef}s for the given class.
     *
     * @param klass to introspect for member definitions
     * @param lookup authorisation to access fields of {@code klass}
     * @return members defined (in the order encountered)
     * @throws InterpreterError on duplicates or unsupported types
     */
    static Map<String, MemberDef> memberDefs(Class<?> klass,
            MethodHandles.Lookup lookup) throws InterpreterError {

        Map<String, MemberDef> defs = new LinkedHashMap<>();

        for (Field f : klass.getDeclaredFields()) {
            Exposed.Member a =
                    f.getDeclaredAnnotation(Exposed.Member.class);
            if (a != null) {
                MemberDef def = getMethodDef(f, lookup);
                MemberDef previous = defs.put(def.name, def);
                if (previous != null) {
                    // There was one already :(
                    throw new InterpreterError(MEMBER_REPEAT, def.name,
                            klass.getSimpleName());
                }
            }
        }
        return defs;
    }

    protected static final String MEMBER_REPEAT =
            "Repeated definition of member %.50s in type %.50s";

    /** Build one member definition for the given field. */
    private static MemberDef getMethodDef(Field f, MethodHandles.Lookup lookup) {

        String name = f.getName();
        int modifiers = f.getModifiers();
        Class<?> type = f.getType();

        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);

        // Get the exposed name.
        Exposed.Member memberAnno =
                f.getAnnotation(Exposed.Member.class);
        if (memberAnno != null) {
            String exposedName = memberAnno.value();
            if (exposedName != null && exposedName.length() > 0)
                name = exposedName;
            boolean ro = memberAnno.readonly()
                    || (modifiers & Modifier.FINAL) != 0;
            if (ro) { flags.add(MemberDef.Flag.READONLY); }
        }

        // May also have DocString annotation
        String doc = "";
        Exposed.DocString d = f.getAnnotation(Exposed.DocString.class);
        if (d != null)
            doc = d.value();

        // Create a handle for the member
        VarHandle handle;
        try {
            handle = lookup.unreflectVarHandle(f);
        } catch (IllegalAccessException e) {
            throw new InterpreterError(e,
                    "cannot get method handle for '%s'", name);
        }

        // From all these parts, construct a definition.
        return MemberDef.forClass(type, name, handle, flags, doc);
    }

}
