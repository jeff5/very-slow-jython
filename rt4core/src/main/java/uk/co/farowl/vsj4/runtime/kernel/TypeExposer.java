// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles.Lookup;
import java.util.Map;

/**
 * Interface expected of the type exposer, and used by the
 * {@link TypeFactory}.
 */
/*
 * We define this (in the kernel) to make it possible for the runtime
 * package to contain the implementation of the type exposer, while the
 * TypeFactory, safely locked away in the kernel, can request and use
 * instances. When we create a type factory, we provide it with a
 * factory object for TypeExposer instances.
 */
public interface TypeExposer {
    /**
     * Gather methods (including getters and setters of fields) from the
     * specified class. Definitions (a precursor of Python descriptors)
     * accumulate in the exposer.
     *
     * @param methodClass to scan for definitions
     */
    void exposeMethods(Class<?> methodClass);

    /// **
    // * Gather members (fields exposed as Python attributes) from the
    // * specified class. Definitions (a precursor of Python
    // * descriptors) accumulate in the exposer.
    // *
    // * @param memberClass to scan for definitions
    // */
    // void exposeMembers(Class<?> memberClass);

    /**
     * For each name having a definition in this {@code TypeExposer}
     * instance, construct the attribute and add it to the map passed
     * in. The map is normally the dictionary of the type. Attributes
     * may rely on a {@code MethodHandle} or {@code VarHandle}, so a
     * lookup object must be provided that can create them.
     *
     * @param dict to which the attributes should be delivered
     * @param lookup authorisation to access members
     * @deprecated Use the iterator instead.
     */
    @Deprecated
    void populate(Map<? super String, Object> dict, Lookup lookup);

    /**
     * A name-value pair that hold one entry intended for the dictionary
     * of the type.
     */
    public static record Entry(String name, Object value) {}

    /**
     * Get the definitions created in this {@code TypeExposer} as name
     * and value pairs. The client for this action is the type object
     * itself, and the values are attributes to be entered in its
     * dictionary. These are constructed as the iterator runs.
     * <p>
     * Attributes may rely on a {@code MethodHandle} or
     * {@code VarHandle}, so a lookup object must be provided that can
     * create them.
     *
     * @param lookup authorisation to access members
     * @return sequence of definitions found in the type
     */
    public Iterable<Entry> entries(Lookup lookup);
}
