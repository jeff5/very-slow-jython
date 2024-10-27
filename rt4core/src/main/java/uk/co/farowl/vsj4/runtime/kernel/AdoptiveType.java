// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.util.List;
import java.util.function.BiConsumer;

import uk.co.farowl.vsj4.runtime.PyType;

/**
 * A Python {@code type} object that accepts instances of specific
 * unrelated Java classes as instances in Python of the type, in
 * addition (optionally) to a canonical representation explicitly
 * crafted in Java. This is the case for a small number of built-in
 * types.
 * <p>
 * We refer to these extra representations as "adopted". The
 * {@code AdoptiveType} holds a {@link Representation} object for each
 * adopted representation.
 */
public final class AdoptiveType extends PyType {

    /**
     * A {@link Representation} object for each class represented,
     * adopted or accepted by this type. The first entry is this type
     * (primary). Next come the adopted representations. Finally, the
     * accepted representations, usually none.
     */
    private final Representation[] representations;
    /**
     * The number of adopted representations. These are the
     * {@link #representations} array starting at index 1.
     */
    private final int adoptedCount;

    /** The Java classes of the {@link #representations}. */
    private final List<Class<?>> selfClasses;

    /**
     * Create an {@code AdoptiveType} and its attached
     * {@link Representation}s, from the Java representation classes.
     * The type itself represents the primary class. An adopted
     * representation is created and registered for each adopted class
     * using the function provided. An unregistered representation is
     * created for each accepted class.
     *
     * @param name of the type (fully qualified).
     * @param primary implementing Python instances of the type.
     * @param bases of the new type.
     * @param adopted the adopted representation classes.
     * @param accepted the accepted representation classes.
     * @param registrar a functional interface to register a
     *     representation
     */
    AdoptiveType(String name, Class<?> primary, PyType[] bases,
            BiConsumer<Class<?>, Representation.Adopted> registrar,
            List<Class<?>> adopted, List<Class<?>> accepted) {
        super(name, primary, bases);

        this.adoptedCount = adopted.size();
        int index = 0, n = adoptedCount + accepted.size() + 1;
        Representation.Adopted r;
        this.representations = new Representation[n];
        Class<?>[] classes = new Class<?>[n];

        // The first representation is this type (primary)
        classes[index] = primary;
        this.representations[index++] = this;

        // Next come the adopted representations
        for (Class<?> c : adopted) {
            classes[index] = c;
            r = new Representation.Adopted(index, c, this);
            this.representations[index++] = r;
            registrar.accept(c, r);
        }

        // Finally, the accepted representations, usually none.
        for (Class<?> c : accepted) {
            classes[index] = c;
            r = new Representation.Adopted(index, c, this);
            this.representations[index++] = r;
        }

        // For efficiency, pre-compute this result.
        this.selfClasses = List.of(classes);
    }

    @Override
    public PyType pythonType(Object x) { return this; }

    /**
     * Get the representation by index. Index 0 is always this type
     * object.
     *
     * @param i index of representation
     * @return the representation
     */
    Representation getRepresentation(int i) {
        return representations[i];
    }

    /**
     * Get the Java representation class by index. Index 0 is always
     * this type object's primary class.
     *
     * @param index index of representation
     * @return the class
     */
    Class<?> javaClass(int index) {
        return this.selfClasses.get(index);
    }

    @Override
    public List<Class<?>> selfClasses() { return this.selfClasses; }
}
