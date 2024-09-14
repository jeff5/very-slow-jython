// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/**
 * A base class for Python singletons {@code None},
 * {@code NotImplemented}, {@code ...}.
 */
abstract class Singleton implements WithClass {

    /** The Python type of the object implemented. */
    private final PyType type;

    /**
     * The name in Python of the object implemented as returned by
     * {@code repr()}.
     */
    private final String name;

    /**
     * Specify the Python type and repr-name.
     *
     * @param type of the single instance
     * @param name that {@code repr()} should produce.
     */
    protected Singleton(PyType type, String name) {
        this.name = name;
        this.type = type;
    }

    @Override
    public String toString() { return name; }

    @Override
    public PyType getType() { return type; }

    // Special methods -----------------------------------------------

    /**
     * {@code __repr__}
     *
     * @return {@code repr(this)} as defined in constructor.
     */
    protected Object __repr__() {
        return name;
    }
}
