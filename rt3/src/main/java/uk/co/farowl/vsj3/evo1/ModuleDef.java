package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * A {@code ModuleDef} stands in relation to the Java classes that
 * define Python modules, somewhat in the way a Python {@code type}
 * object stands in relation to the Java classes that define Python
 * objects. It is a definition from which instances of the module may be
 * made.
 * <p>
 * What we most often encounter as "a module", a Python source file, is
 * actually just a definition from which a module object may be made.
 * This happens <i>once in each interpreter</i> where the module is
 * imported. A distinct object, with mutable state, represents that
 * module in each interpreter. There must therefore be a factory object
 * that has access to the definition of the module, but is able to
 * instantiate it (equivalent to executing the body of a module defined
 * in Python).
 * <p>
 * This initialisation cannot be identified with the static
 * initialisation of the Java class, since that cannot be repeated, but
 * must happen per instance. It is useful, however, to have an
 * intermediate cache of the results of processing the defining Java
 * class once statically initialised.
 */
public class ModuleDef {
    // Compare CPython PyModuleDef

    /** Name of the module. */
    final String name;

    /** The Java class defining instances of the module. */
    final Class<?> definingClass;

    /**
     * Definitions for the members that appear in the dictionary of
     * instances of the module named. Instances receive members by copy,
     * by binding to the module instance (descriptors), or by reference
     * (if immutable).
     */
    private final MethodDef[] methods;

    /**
     * Create a definition for the module, largely by introspection on
     * the class and by forming {@code MethodHandle}s on discovered
     * attributes.
     *
     * @param name of the module (e.g. "sys" or "math")
     * @param lookup authorises access to the defining class.
     */
    ModuleDef(String name, Lookup lookup) {
        this.name = name;
        this.definingClass = lookup.lookupClass();
        ModuleExposer exposer = Exposer.exposeModule(definingClass);
        this.methods = exposer.getMethodDefs(lookup);
        // XXX ... and for fields.
        // XXX ... and for types defined in the module maybe? :o
    }

    /**
     * Get the method definitions. This method is provided for test use
     * only. It isn't safe as for public use.
     *
     * @return the method definitions
     */
    MethodDef[] getMethods() { return methods; }

    /**
     * Add members defined here to the dictionary of a module instance.
     *
     * @param module to populate
     * @throws Throwable
     */
    void addMembers(JavaModule module) {
        PyDict d = module.dict;
        for (MethodDef md : methods) {
            // Create function by binding to the module
            d.put(md.name, new PyJavaMethod(md, module, module.name));
        }
    }
}
