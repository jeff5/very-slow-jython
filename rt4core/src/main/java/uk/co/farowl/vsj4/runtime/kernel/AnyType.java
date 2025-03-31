// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.Representation;

/**
 * A base shared by the concrete implementation types of the Python
 * {@code type} object. This class also allows us to publish in the
 * {@code kernel} package, accessors relevant to all types of
 * {@link PyType}, and avoid them becoming Jython API.
 */
public abstract sealed class AnyType extends PyType
        permits SimpleType, ReplaceableType, AdoptiveType {

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     */
    protected AnyType(String name, Class<?> javaClass, PyType[] bases) {
        super(name, javaClass, bases);
    }

    /**
     * Determine (or create if necessary) the {@link Representation} for
     * the given object. The representation is found (in the type
     * registry) from the Java class of the argument.
     * <p>
     * Duplicates {@code PyType.getRepresentation} for the kernel.
     *
     * @param o for which a {@code Representation} is required
     * @return the {@code Representation}
     */
    static Representation getRepresentation(Object o) {
        return registry.get(o.getClass());
    }

    protected static boolean systemReady() {
        return PyType.systemReady();
    }
}
