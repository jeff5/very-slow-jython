package uk.co.farowl.vsj4.runtime;

/**
 * A crafted Python object implementation reports an explicit type,
 * which may be the same for all instances of the same Java class or
 * vary.
 */
public interface Crafted {
    /**
     * Return the actual type of the object.
     *
     * @return the actual type of the object
     */
    PyType getType();
}
