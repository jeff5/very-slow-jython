// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/**
 * The implementation of the Python {@code float} type.
 * <p>
 * We only actually need instances of this class as a base for Python
 * subclasses of {@code float}. Actual float values are represented by
 * {@code double} or {@code java.lang.Double} when boxed as an object.
 */
public class PyFloat {
    /** The type object {@code float}. */
    // Bootstrap type so ask the type system to resolve it.
    public static final PyType TYPE = PyType.of(0.0);

    /** Value of this Python {@code float} as a Java primitive. */
    final double value;

    /**
     * Construct from primitive.
     *
     * @param value of the {@code float}
     */
    protected PyFloat(double value) { this.value = value; }

    // @Override
    public PyType getType() { return TYPE; }
}
