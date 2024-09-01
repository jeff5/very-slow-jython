// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

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
    public SimpleType pythonType(Object x) { return this; }
}
