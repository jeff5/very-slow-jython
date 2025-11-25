// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.kernel;

import uk.co.farowl.vsj4.core.PyFloat;
import uk.co.farowl.vsj4.core.PyLong;
import uk.co.farowl.vsj4.type.TypeFlag;

/**
 * A {@code Representation} that relates an adopted representation to
 * its {@link AdoptiveType}.
 */
class AdoptedRepresentation extends Representation {

    /** The type of which this is an adopted representation. */
    final AdoptiveType type;

    /**
     * Index of this representation class in the type (see
     * {@link AdoptiveType#getSubclassIndex(Class)}.
     */
    final int index;

    /**
     * Create a {@code Representation} object associating a Python type
     * with the Java type.
     *
     * @param index by which known to the type object
     * @param javaClass implementing it
     * @param type of which this is an accepted implementation
     */
    AdoptedRepresentation(int index, Class<?> javaClass,
            AdoptiveType type) {
        super(javaClass);
        this.type = type;
        this.index = index;
    }

    @Override
    public boolean hasFeature(Object x, TypeFlag feature) {
        return type.hasFeature(feature);
    }

    @Override
    public boolean hasFeature(Object x, KernelTypeFlag feature) {
        return type.hasFeature(feature);
    }

    @Override
    public AdoptiveType pythonType(Object x) { return type; }

    @Override
    public int getIndex() { return index; }

    @Override
    public boolean isIntExact() { return type == PyLong.TYPE; }

    @Override
    public boolean isFloatExact() { return type == PyFloat.TYPE; }

    @Override
    public String toString() {
        String javaName = javaClass().getSimpleName();
        return javaName + " as " + type.toString();
    }
}
