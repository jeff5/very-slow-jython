package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.PythonStaticMethod;
import uk.co.farowl.vsj3.evo1.base.InterpreterError;
import uk.co.farowl.vsj3.evo1.ModuleDef.MethodDef;

/**
 * A {@code ModuleExposer} provides access to the attributes of a module
 * defined in Java (a built-in or extension module). These are primarily
 * the {@link MethodDef}s derived from annotated methods in the defining
 * class. It is normally obtained by a call to
 * {@link Exposer#exposeModule(Class)}.
 */
class ModuleExposer extends Exposer {

    /**
     * Construct the {@code ModuleExposer} instance for a particular
     * module.
     */
    ModuleExposer() {}

    /**
     * Build the result from the defining class. We populate this
     * {@code ModuleExposer} with {@link MethodSpec} and
     * {@link StaticMethodSpec} objects that describe the methods we
     * discover in the {@code definingClass}. A subsequent call to
     * {@link #getMethodDefs(Lookup)} will return {@link MethodDef}
     * objects from them.
     *
     * @param definingClass to scan for definitions
     */
    void expose(Class<?> definingClass) {
        // Scan the defining class for definitions
        scanJavaMethods(definingClass);
        // XXX ... and for fields.
        // XXX ... and for types defined in the module maybe? :o
    }

    @Override
    ScopeKind kind() { return ScopeKind.MODULE; }

    /**
     * From the methods discovered by introspection of the class, return
     * an array of {@link MethodDef}s. This array will normally be part
     * of a {@link ModuleDef} from which the dictionary of each instance
     * of the module will be created.
     *
     * A {@link MethodDef} relies on {@code MethodHandle}, so a lookup
     * object must be provided with the necessary access to the defining
     * class.
     *
     * @param lookup authorisation to access methods
     * @return method definitions
     * @throws InterpreterError on lookup prohibited
     */
    MethodDef[] getMethodDefs(Lookup lookup) throws InterpreterError {
        MethodDef[] a = new MethodDef[methodSpecs.size()];
        int i = 0;
        for (CallableSpec ms : methodSpecs) {
            a[i++] = ms.getMethodDef(lookup);
        }
        return a;
    }

    /**
     * For a Python module defined in Java, add to {@link specs}, the
     * methods found in the given defining class and annotated for
     * exposure.
     *
     * @param definingClass to introspect for definitions
     * @throws InterpreterError on duplicates or unsupported types
     */
    @Override
    void scanJavaMethods(Class<?> definingClass)
            throws InterpreterError {

        // Collect exposed functions (Java methods)
        for (Method m : definingClass.getDeclaredMethods()) {
            PythonMethod a =
                    m.getDeclaredAnnotation(PythonMethod.class);
            if (a != null) { addMethodSpec(m, a); }
            PythonStaticMethod sm =
                    m.getDeclaredAnnotation(PythonStaticMethod.class);
            if (sm != null) { addStaticMethodSpec(m, sm); }
        }
    }
}
