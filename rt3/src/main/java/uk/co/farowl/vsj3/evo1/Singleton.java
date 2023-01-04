// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

abstract class Singleton implements CraftedPyObject {

    /** The Python type of the object implemented. */
    final PyType type;

    /**
     * The name in Python of the object implemented as returned by
     * {@code repr()}.
     */
    private final String name;

    protected Singleton(PyType type, String name) {
        this.name = name;
        this.type = type;
    }

    protected Object __repr__() { return name; }

    @Override
    public String toString() { return name; }

    @Override
    public PyType getType() { return type; }
}
