// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.util.List;

import uk.co.farowl.vsj4.runtime.PyType;

/**
 * A Python type object used where instances (in Python) of the type
 * belong (in Java) to a single representation class, or a Java class
 * hierarchy with a single root.
 * <p>
 * A Python instance method in such a type, when implemented in Java,
 * must have a {@code self} parameter that accepts the representation
 * class (or a superclass).
 */
public non-sealed class SimpleType extends PyType {

    /**
     * Construct a type object defining the name and single Java type
     * that all instances are assignable to.
     *
     * @param name of the new type
     * @param javaType of instances
     * @param bases of the type
     */
    public SimpleType(String name, Class<?> javaType, PyType[] bases) {
        super(name, javaType, bases);
    }

    /**
     * Partially construct a {@code type} object for {@code object}.
     * This constructor is <b>only used once</b>, during the static
     * initialisation of the type system.
     */
    SimpleType() {
        // The canonical class is Object and there are no bases.
        this("object", Object.class, new PyType[0]);
    }

    /**
     * Partially construct a {@code type} object for {@code type}. This
     * constructor is <b>only used once</b>, during the static
     * initialisation of the type system.
     *
     * @param object the type object for {@code object} (as base).
     */
    SimpleType(PyType object) {
        this("type", PyType.class, new PyType[] {object});
    }

    @Override
    public List<Representation> representations() {
        return List.of(this);
    }

    @Override
    public List<Class<?>> selfClasses() { return List.of(javaClass); }

    // XXX Decide the immutability of SimpleType
    @Override
    public boolean isMutable() { return false; }

    @Override
    public PyType pythonType(Object x) {
        // I don't *think* we should be asked this question unless:
        assert javaClass.isAssignableFrom(x.getClass());
        return this;
    }

    /**
     * A lookup with package scope within the public {@code runtime}
     * package. This lookup object is provided to the kernel to grant it
     * package-level access to the run-time system. For example, it
     * makes it possible to form method handles on Python type
     * implementations defined in {@code runtime}.
     */
    static MethodHandles.Lookup getRuntimeLookup() {
        return RUNTIME_LOOKUP;
    }

    /**
     * The type factory to which the run-time system goes for all type
     * objects.
     */
    static TypeFactory getFactory() { return factory; }

    /**
     * The type registry to which this run-time system goes for all
     * class look-ups.
     */
    static TypeRegistry getRegistry() { return registry; }
}
