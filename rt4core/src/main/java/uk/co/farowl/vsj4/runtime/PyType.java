// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyBaseObject;
import uk.co.farowl.vsj4.runtime.kernel.AbstractPyType;
import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.ReplaceableType;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SimpleType;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/**
 * Each Python {@code type} object is implemented by an <i>instance</i>
 * in Java of {@code PyType}. As {@code PyType} is {@code abstract},
 * that means an instance of a subclass of {@code PyType}, although only
 * {@code PyType} is public API. The built-ins {@code object},
 * {@code type}, {@code str}, {@code int}, etc. and any class defined in
 * Python, are instances in Java of such a subclass.
 * <p>
 * A particular Java subclass of {@code PyType}, implementing
 * {@link ExtensionPoint}, is used to represent those types that are
 * instances of Python subclasses of {@code type} (known as
 * <i>metaclasses</i>). Given the program text: <pre>
 * class Meta(type): pass
 * class MyClass(metaclass=Meta): pass
 * mc = MyClass()
 * </pre>
 * <p>
 * The following will be the implementation types of the objects
 * defined:
 * <ul>
 * <li>{@code Meta} will be an instance in Java of
 * {@link ReplaceableType}, a subclass of {@code PyType} that is not an
 * extension point class.</li>
 * <li>{@code MyClass} will be an instance in Java of
 * {@link AbstractPyType.Derived}, a subclass of {@code PyType}
 * implementing {@link ExtensionPoint}.</li>
 * <li>{@code mc} will be an instance in Java of
 * {@link AbstractPyBaseObject}, a subclass of {@code Object}
 * implementing {@link ExtensionPoint}.</li>
 * </ul>
 */
public abstract sealed class PyType extends AbstractPyType
        permits SimpleType, ReplaceableType, AdoptiveType,
        AbstractPyType.Derived {
    /**
     * The type factory to which the run-time system goes for all type
     * objects.
     */
    @SuppressWarnings("deprecation")
    static final TypeFactory factory = new TypeFactory();

    /** The type object of {@code type} objects. */
    @SuppressWarnings("deprecation")
    public static final PyType TYPE = factory.typeForType();

    /**
     * The type registry to which this run-time system goes for all
     * class look-ups.
     */
    static final TypeRegistry registry;

    /** An empty array of type objects */
    public static final PyType[] EMPTY_ARRAY;

    /*
     * The static initialisation of this class, above and in the next
     * block, brings the type system into existence in the *only* way it
     * should happen. At this point, 'type' and 'object' exist in their
     * "Java ready" form, but they are not "Python ready", and nothing
     * else exists. The block intends to make all the bootstrap types
     * Java ready, then Python ready, before any type object becomes
     * visible.
     */
    static {
        // This cute re-use also proves 'type' and 'object' exist.
        EMPTY_ARRAY = TYPE.base.bases;
        assert EMPTY_ARRAY.length == 0;
        // Get all the bootstrap types ready for Python.
        factory.createBootstrapTypes();
        // Publish registry for use (package visible)
        registry = factory.getRegistry();
    }

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaType implementing Python instances of the type
     * @param bases of the new type
     */
    protected PyType(String name, Class<?> javaType, PyType[] bases) {
        super(name, javaType, bases);
    }

    /**
     * Determine (or create if necessary) the Python type for the given
     * object.
     *
     * @param o for which a type is required
     * @return the type
     */
    public static PyType of(Object o) {
        Representation rep = registry.get(o.getClass());
        return rep.pythonType(o);
    }

    /**
     * Create a Python type according to the specification. This is the
     * normal way to create any Python type that is defined in Java: the
     * Python built-ins or user-defined types. The minimal idiom
     * is:<pre>
     * class MyType {
     *     static PyType TYPE = PyType.fromSpec(
     *         new TypeSpec("mypackage.mytype",
     *                      MethodHandles.lookup());
     * }
     * </pre> The type system will add descriptors for the members of
     * the class identified by annotation or reserved names for exposure
     * to Python.
     *
     * @param spec specifying the new type
     * @return the new type
     */
    public static PyType fromSpec(TypeSpec spec) {
        return factory.typeFrom(spec);
    }
}
