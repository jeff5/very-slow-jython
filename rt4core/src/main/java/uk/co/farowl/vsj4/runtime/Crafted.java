package uk.co.farowl.vsj4.runtime;

/**
 * A {@code Crafted} Python object implementation reports an explicit
 * type. Subclasses of the {@code Crafted} Python object implementation
 * represent the same Python type as their nearest explicitly exposed
 * superclass.
 */
public interface Crafted {
    /**
     * Return the actual type of the object. The type may be the same
     * for all instances of the same Java class or be assignable within
     * certain constraints the object must enforce.
     *
     * @return the actual type of the object
     */
    PyType getType();
}
