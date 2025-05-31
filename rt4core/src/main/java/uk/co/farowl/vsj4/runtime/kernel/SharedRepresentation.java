// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.O;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MethodHandles.Lookup;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * The {@link Representation} for a Python class defined in Python. Many
 * Python classes may be represented by the same Java class, the actual
 * Python type being indicated by the instance.
 */
class SharedRepresentation extends Representation {

    /** To return as {@link #canonicalClass()}. */
    private final Class<?> canonicalClass;

    /**
     * {@code MethodHandle} of type {@code (Object)PyType}, to get the
     * actual Python type of an {@link Object} object.
     */
    private static final MethodHandle getType;

    /**
     * The type {@code (PyType)MethodHandle} used to cast the method
     * handle getter.
     */
    private static final MethodType MT_MH_FROM_TYPE;

    /** Rights to form method handles. */
    private static final Lookup LOOKUP = MethodHandles.lookup();

    static {
        try {
            // Used as a cast in the formation of getMHfromType
            // (PyType)MethodHandle
            MT_MH_FROM_TYPE =
                    MethodType.methodType(MethodHandle.class, T);
            // Used as a cast in the formation of getType
            // (PyType)MethodHandle
            // getType = λ x : x.getType()
            // .type() = (Object)PyType
            getType = LOOKUP
                    .findVirtual(WithClass.class, "getType",
                            MethodType.methodType(T))
                    .asType(MethodType.methodType(T, O));
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InterpreterError(e,
                    "preparing handles in Representation.Shared");
        }
    }

    /**
     * Create a {@code Representation} object that is the class used to
     * represent instances of (potentially) many types defined in
     * Python.
     *
     * @param javaClass Java representation class
     * @param canonical class on which subclasses are based
     */
    SharedRepresentation(Class<?> javaClass, Class<?> canonical) {
        super(javaClass);
        this.canonicalClass = canonical;
    }

    @Override
    public String toString() {
        return String.format("Shared[%s]", javaClass().getSimpleName());
    }

    @Override
    public PyType pythonType(Object x) {
        if (x instanceof WithClass wcx) {
            return wcx.getType();
        } else if (x == null) {
            return null;
        } else {
            throw notSharedError(x);
        }
    }

    @Override
    public boolean isIntExact() { return false; }

    @Override
    public boolean isFloatExact() { return false; }

    /**
     * The {@link PyType#canonicalClass()} of types that share this
     * representation (the "clique"). Subclasses in Python of those
     * types will (in general) not share this representation,
     *
     *
     * as it depends on whether {@code __dict__} is defined and on the
     * content of {@code __slots__}. However, they will all have the
     * same the canonical class, of which their Java representation
     * class {@link #javaClass()} is a proper subclass in Java. This
     * design allows us to re-use an existing representation, if that is
     * possible, when defining a subclass.
     *
     * @return the canonical Java representation class of types
     */
    public Class<?> canonicalClass() { return canonicalClass; }

    /**
     * Return an exception reporting that the given object was
     * registered as if implementing a {@link ReplaceableType}, but it
     * cannot be inspected for its type. The {@link TypeFactory} has a
     * bug if it created this {@code Representation}. Or the type system
     * has a bug if it allowed anything else to do so.
     *
     * @param x objectionable object
     * @return to throw
     */
    private InterpreterError notSharedError(Object x) {
        String msg = String.format(
                "unsharable class %.100s registered as %s",
                x.getClass().getTypeName(), this.toString());
        return new InterpreterError(msg);
    }
}
