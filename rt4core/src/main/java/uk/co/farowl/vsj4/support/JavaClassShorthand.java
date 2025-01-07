// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.support;

/**
 * Some shorthands used to construct method signatures,
 * {@code MethodType}s, etc..
 */
public interface JavaClassShorthand {
    /** Shorthand for {@code Object.class}. */
    static final Class<Object> O = Object.class;
    /** Shorthand for {@code Class.class}. */
    static final Class<?> C = Class.class;
    /** Shorthand for {@code String.class}. */
    static final Class<String> S = String.class;
    /** Shorthand for {@code int.class}. */
    static final Class<?> I = int.class;
    /** Shorthand for {@code boolean.class}. */
    static final Class<?> B = boolean.class;
    /** Shorthand for {@code void.class}. */
    static final Class<?> V = void.class;
    /** Shorthand for {@code Object[].class}. */
    static final Class<Object[]> OA = Object[].class;
    /** Shorthand for {@code String[].class}. */
    static final Class<String[]> SA = String[].class;
}
