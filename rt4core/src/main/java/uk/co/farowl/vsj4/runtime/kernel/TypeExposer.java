// Copyright (c)2024 Jython Developers.
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
     * Build the result from the defining class.
     *
     * @param definingClass to scan for definitions
     */
    void expose(Class<?> definingClass);

    /**
     * For each name having a definition in {@link #specs}, construct
     * the attribute and add it to the map passed in. The map is
     * normally the dictionary of the type. Attributes may rely on a
     * {@code MethodHandle} or {@code VarHandle}, so a lookup object
     * must be provided that can create them.
     *
     * @param dict to which the attributes should be delivered
     * @param lookup authorisation to access members
     */
    void populate(Map<? super String, Object> dict, Lookup lookup);
}
