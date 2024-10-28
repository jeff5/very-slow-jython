// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.util.List;

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
     * (representing the primary class). Next come the representations
     * of the adopted classes. The accepted classes are not represented.
     */
    private final List<Representation> reps;

    /** The Java classes, primary, adopted <i>and</i> accepted. */
    private final List<Class<?>> selfClasses;

    /**
     * Create an {@code AdoptiveType} and its attached
     * {@link Representation}s, from the Java representation classes.
     * The type itself represents the primary class. An adopted
     * representation is created and attached for each adopted class.
     * The caller should register the representations with the type
     * system. Each accepted class is simply recorded as a self-class.
     *
     * @param name of the type (fully qualified).
     * @param primary implementing Python instances of the type.
     * @param bases of the new type.
     * @param adopted the adopted representation classes.
     * @param accepted self-classes.
     */

    AdoptiveType(String name, Class<?> primary, PyType[] bases,
            List<Class<?>> adopted, List<Class<?>> accepted) {
        super(name, primary, bases);

        int n = 1 + adopted.size(), m = n + accepted.size();
        Representation[] reps = new Representation[n];
        Class<?>[] classes = new Class<?>[m];

        // The first representation is this type (primary)
        reps[0] = this;
        classes[0] = primary;

        // Next come the adopted classes and representations.
        int index = 1;
        for (Class<?> c : adopted) {
            reps[index] = new Representation.Adopted(index, c, this);
            classes[index++] = c;
        }

        // Finally, the accepted classes, usually none.
        for (Class<?> c : accepted) { classes[index++] = c; }

        // For efficiency, pre-compute these results.
        this.selfClasses = List.of(classes);
        this.reps = List.of(reps);
    }

    @Override
    public List<Representation> representations() { return reps; }

    @Override
    public List<Class<?>> selfClasses() { return selfClasses; }

    @Override
    public PyType pythonType(Object x) {
        // I don't *think* we should be asked this question unless:
        assert javaClass.isAssignableFrom(x.getClass());
        return this;
    }
}
