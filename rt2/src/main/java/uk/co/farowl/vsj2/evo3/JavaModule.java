package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import uk.co.farowl.vsj2.evo3.MethodDef.Flag;

/** Common mechanisms for all Python modules defined in Java. */
public abstract class JavaModule extends PyModule {

    /**
     * A table of the methods in this module, from which each instance
     * of the module will build the function objects in its dictionary.
     * Access only through {@link #getMethodDefs()}.
     */
    /*
     * A simple table will not do because it would either be static, and
     * therefore a global JavaModule.methods, whatever the actual
     * module's class, or an instance member, and therefore appear in as
     * many copies as there are instances. The ClassValue gives us one
     * per class, built lazily by the first instance, for its actual
     * class.
     */
    private static ClassValue<MethodDef[]> methods =
            new ClassValue<MethodDef[]>() {

                @Override
                protected MethodDef[] computeValue(Class<?> type) {
                    return initMethods(checkJavaModule(type));
                }

                /**
                 * Checked cast {@code type} to
                 * {@code Class<? extends JavaModule>}
                 *
                 * @throws InterpreterError if it cannot be so cast
                 */
                @SuppressWarnings("unchecked")
                private Class<? extends JavaModule> checkJavaModule(
                        Class<?> type) throws InterpreterError {
                    if (!JavaModule.class.isAssignableFrom(type))
                        throw new InterpreterError(
                                "%s is not a JavaModule",
                                type.getName());
                    return (Class<? extends JavaModule>) type;
                }
            };

    /**
     * A lookup object that subclasses (i.e. Python {@code module}s
     * implemented in Java) may use to look up their members during
     * registration.
     */
    protected static final MethodHandles.Lookup LOOKUP =
            MethodHandles.lookup();

    JavaModule(Interpreter interpreter, String name) {
        super(interpreter, name);
    }

    @Override
    void init() {
        // Register each method as an exported object
        MethodDef[] methods = getMethodDefs();
        for (MethodDef def : methods) {
            PyJavaFunction f = new PyJavaFunction(def, this);
            dict.put(def.name, f);
        }
    }

    /**
     * Get the method definitions applicable to this module, generating
     * them reflectively if necessary (i.e. this is the first instance).
     */
    private MethodDef[] getMethodDefs() {
        /*
         * The ClassValue makes this thread safe, by allowing concurrent
         * entry, but discarding the second result. For efficiency,
         * avoid concurrent entry.
         */
        synchronized (getClass()) {
            return methods.get(getClass());
        }
    }

    /** Build method definitions by reflection of the given class. */
    private static MethodDef[]
            initMethods(Class<? extends JavaModule> mod) {

        List<MethodDef> defs = new LinkedList<>();

        for (Method m : mod.getDeclaredMethods()) {
            Exposed.Function a =
                    m.getDeclaredAnnotation(Exposed.Function.class);
            if (a != null) {
                MethodDef def = getMethodDef(m);
                defs.add(def);
            }
        }

        return defs.toArray(new MethodDef[defs.size()]);
    }

    /** Build one method definition for the given method. */
    // XXX Functions only: extend to methods descriptors IDC.
    private static MethodDef getMethodDef(Method m) {

        String name = m.getName();

        // Check whether declared as a method (first parameter is self)
        int modifiers = m.getModifiers();
        EnumSet<Flag> f = EnumSet.noneOf(Flag.class);
        if (Modifier.isStatic(modifiers))
            f.add(Flag.STATIC);
        // XXX Always STATIC at present:

        // May also have DocString annotation
        String doc = "";
        Exposed.DocString d = m.getAnnotation(Exposed.DocString.class);
        if (d != null)
            doc = d.value();

        // Create a MethodType for the method
        Parameter[] params = m.getParameters();
        Class<?>[] ptypes = new Class<?>[params.length];
        for (int i = 0; i < params.length; i++) {
            Parameter p = params[i];
            ptypes[i] = p.getType();
        }
        Class<?> rtype = m.getReturnType();
        MethodType methodType = MethodType.methodType(rtype, ptypes);

        // Now use the name and MethodType to get a MethodHandle
        Class<?> declaringClass = m.getDeclaringClass();
        MethodHandle mh = null;
        try {
            if (f.contains(Flag.STATIC)) {
                mh = LOOKUP.findStatic(declaringClass, name,
                        methodType);
            } else {
                mh = LOOKUP.findVirtual(declaringClass, name,
                        methodType);
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InterpreterError(e,
                    "cannot get method handle for '%s'", name);
        }

        // From all these parts, construct a definition.
        return new MethodDef(name, mh, f, doc);
    }

}
