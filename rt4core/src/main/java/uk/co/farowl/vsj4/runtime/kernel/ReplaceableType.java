package uk.co.farowl.vsj4.runtime.kernel;

import uk.co.farowl.vsj4.runtime.PyType;

/**
 * A Python type object used where multiple Python types share a single
 * representation in Java, making them all acceptable for assignment to
 * the {@code __class__} member of Python instances of any of them. The
 * common representation encapsulates what Python terms the "layout
 * constraints". The Java implementation of Python instance methods in
 * such a type will have the common Java type (or a superclass) as their
 * {@code self} parameter.
 */
public final class ReplaceableType extends PyType {

    /** The representation shared by this type and others. */
    final Representation.Shared representation;

    /**
     * Construct one of several types that share a single representation
     * in Java.
     *
     * @param name of the type (fully qualified).
     * @param representation shared
     * @param bases of the new type
     */
    ReplaceableType(String name, Representation.Shared representation,
            PyType[] bases) {
        super(name, representation.javaType, bases);
        this.representation = representation;
    }

    @Override
    public PyType pythonType(Object x) { return this; }
}
