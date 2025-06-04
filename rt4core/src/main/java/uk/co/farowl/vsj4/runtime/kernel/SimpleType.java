// Copyright (c)2025 Jython Developers.
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
public non-sealed class SimpleType extends BaseType {

    /** To return as {@link #canonicalClass()}. */
    private final Class<?> canonicalClass;

    /**
     * Construct a type object defining the name, a single Java type
     * that all instances are assignable to, and the canonical class
     * that is the Java base class of subclass representations.
     *
     * @param name of the new type
     * @param javaClass to which instances are assignable
     * @param canonical class on which subclasses are based
     * @param bases of the type
     */
    public SimpleType(String name, Class<?> javaClass,
            Class<?> canonical, PyType[] bases) {
        super(name, javaClass, bases);
        this.canonicalClass = canonical;
    }

    /**
     * Construct a type object defining the name and single Java type
     * that all instances are assignable to.
     *
     * @param name of the new type
     * @param javaClass to which instances are assignable
     * @param bases of the type
     */
    public SimpleType(String name, Class<?> javaClass, BaseType[] bases) {
        this(name, javaClass, javaClass, bases);
    }

    /**
     * Partially construct a {@code type} object for {@code object}.
     * This constructor is <b>only used once</b>, during the static
     * initialisation of the type system.
     */
    SimpleType() {
        // The representation is Object and there are no bases.
        this("object", Object.class, Object.class, new BaseType[0]);
    }

    /**
     * Partially construct a {@code type} object for {@code type}. This
     * constructor is <b>only used once</b>, during the static
     * initialisation of the type system.
     *
     * @param object the type object for {@code object} (as base).
     */
    SimpleType(BaseType object) {
        this("type", PyType.class, SimpleType.class,
                new BaseType[] {object});
    }

    @Override
    public List<Representation> representations() {
        return List.of(this);
    }

    @Override
    public List<Class<?>> selfClasses() { return List.of(javaClass); }

    @Override
    public Class<?> canonicalClass() { return canonicalClass; }

    @Override
    public BaseType pythonType(Object x) { return this; }

    // TODO: Decide the immutability of SimpleType
    @Override
    public boolean isMutable() { return false; }

    @Override
    public boolean isIntExact() { return false; }

    @Override
    public boolean isFloatExact() { return false; }

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
}
