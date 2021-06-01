package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;

class TypeExposer extends Exposer {

    /**
     * Type for which attributes are to be exposed. It is referenced
     * (e.g. where we create a descriptor), but is not otherwise
     * accessed, since it is (necessarily) incomplete at this time.
     */
    final PyType type;

    TypeExposer(PyType type) {
        this.type = type;
    }

    @Override
    ScopeKind kind() {
        return ScopeKind.TYPE;
    }

    /** Build the result from the defining class. */
    void expose(Class<?> definingClass) {
        // Scan the defining class for exposed and special methods
        addMethodSpecs(definingClass);

        // XXX ... and for fields.

        // XXX ... and for types defined in the module maybe? :o
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
        for (Spec spec : specs.values()) {
            Object attr = spec.asAttribute(type, lookup);
            dict.put(spec.name, attr);
        }
    }

    /**
     * Add to {@link #specs}, definitions found in the given class and
     * either annotated for exposure or having the name of a special
     * method.
     *
     * @param defsClass to introspect for definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    @Override
    void addMethodSpecs(Class<?> defsClass) throws InterpreterError {

        // Iterate over methods looking for those to expose
        for (Method m : defsClass.getDeclaredMethods()) {
            /*
             * Note: annotations (and special names) are not treated as
             * alternatives, to catch exposure of methods by multiple
             * routes.
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

            // If it has a special method name record that definition.
            String name = m.getName();
            Slot slot = Slot.forMethodName(name);
            if (slot != null) { addWrapperSpec(slot, m); }
        }
    }

    /**
     * Process a method that matches a slot name to a descriptor
     * specification and add it to the table of specifications by name.
     *
     * @param slot annotation encountered
     * @param meth method annotated
     * @throws {@link InterpreterError} on duplicates or unsupported
     *     types
     */
    void addWrapperSpec(Slot slot, Method meth)
            throws InterpreterError {

        String name = slot.methodName;
        // Find any existing definition
        Spec spec = specs.get(name);
        WrapperSpec wrapperSpec;
        if (spec == null) {
            // A new entry is needed
            wrapperSpec = new WrapperSpec(slot);
            specs.put(name, wrapperSpec);
        } else if (spec instanceof WrapperSpec) {
            // Existing entry will be updated
            wrapperSpec = (WrapperSpec) spec;
        } else {
            // Existing entry is not compatible
            wrapperSpec = new WrapperSpec(slot);
            throw duplicateError(name, meth, wrapperSpec, spec);
        }
        wrapperSpec.add(meth);
    }
}
