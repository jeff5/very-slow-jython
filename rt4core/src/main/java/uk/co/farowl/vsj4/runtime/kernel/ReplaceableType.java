package uk.co.farowl.vsj4.runtime.kernel;

import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;

/**
 * A Python type object used where multiple Python types share a single
 * representation in Java, which may be replaced on instances by
 * {@code __class__} assignment, if other constraints allow. Often
 * (always?) the Java class of implementations {@link ExtensionPoint}.
 * The Java implementation of Python instance methods in such a type
 * will have the common Java type (or a superclass) as their
 * {@code self} parameter.
 */
public final class ReplaceableType extends PyType {

    final Representation.Shared representation;

    ReplaceableType(String name, Representation.Shared representation,
            PyType[] bases) {
        super(name, representation.javaType, bases);
        this.representation = representation;
    }

    @Override
    public PyType pythonType(Object x) { // TODO Auto-generated method stub
    return null; }
}
