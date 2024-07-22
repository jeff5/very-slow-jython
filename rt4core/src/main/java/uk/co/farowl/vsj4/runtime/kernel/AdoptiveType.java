// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

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

    private final Representation.Adopted[] adopted;

    /**
     * Create a type prepared for a certain number of adopted
     * representations, and optionally a crafted one. Examples from the
     * implementation are <pre>
     * object = AdoptiveType("object", null, 1, new PyType[0])
     * // ... add Representation of Object.class
     * str = AdoptiveType("str", PyUnicode.class, 1, object)
     * // ... add Representation of String.class
     * </pre>although they may not be created exactly like that.
     *
     * @param name of the type (fully qualified).
     * @param canonicalClass implementing Python instances of the type
     *     or {@code null}.
     * @param adoptedCount the number of adopted representations.
     * @param bases of the new type.
     */
    AdoptiveType(String name, Class<?> canonicalClass, int adoptedCount,
            PyType[] bases) {
        super(name, canonicalClass, bases);
        assert adoptedCount > 0;
        this.adopted = new Representation.Adopted[adoptedCount];
    }

    /**
     * Partially construct a {@code type} object for {@code object}.
     * This constructor is <b>only used once</b>, during the static
     * initialisation of the type system.
     */
    AdoptiveType() {
        // There is no canonical class and no bases.
        this("object", null, 1, new PyType[0]);
        // We have to adopt Object here.
        this.adopted[0] =
                new Representation.Adopted(Object.class, this);
    }

    @Override
    public PyType pythonType(Object x) { return this; }

    /**
     * Provide a representation by index.
     *
     * @param i index of representation
     * @param a the representation
     */
    @Deprecated
    void setAdopted(int i, Representation.Adopted a) {
        assert adopted[i] == null;
        adopted[i] = a;
    }

    /**
     * Provide a representation, so store at the first non-null entry in
     * the array of adopted representations.
     *
     * @param a the representation
     */
    void adopt(Representation.Adopted a) {
        int i = 0;
        while (adopted[i++] != null) {}
        adopted[--i] = a;
    }

    /**
     * Get the adopted representation by index
     *
     * @param i index of representation
     * @return the representation
     */
    Representation.Adopted getAdopted(int i) { return adopted[i]; }

    /**
     * @return the number of representations
     */
    int getAdoptedCount() { return adopted.length; }
}
