package uk.co.farowl.vsj4.runtime.kernel;

import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;

/**
 * A Python type object used where instances (in Python) of the type
 * belong (in Java) to a single class, or a Java class hierarchy with a
 * single root, that is not an {@link ExtensionPoint}. All instances of
 * these Java classes except those that implement {@link ExtensionPoint}
 * have this Python type. The Java implementation of Python instance
 * methods in such a type will have the root Java type (or a superclass)
 * as their {@code self} parameter.
 */
public final class SimpleType extends PyType {

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
     * Partially construct a {@code type} object for {@code type}.
     * This constructor is
     * <b>only used once</b>, during the static initialisation of
     * the type system.
     * @param object the type object for {@code object} (as base).
     */
    SimpleType(PyType object) {
        this("type", PyType.class, new PyType[] {object});
    }

    @Override
    public SimpleType pythonType(Object x) { return this; }
}
