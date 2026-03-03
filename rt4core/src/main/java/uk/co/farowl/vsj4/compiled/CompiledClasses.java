// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.compiled;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import uk.co.farowl.vsj4.core.PyBytes;

/**
 * A helper class that is able to define a hidden class belonging to
 * this package using package-private access.
 */
// Note that public here means visible to the core module, not beyond.
public class CompiledClasses {

    /**
     * Lookup object allowing package-level access to generated classes,
     * including the right to define new ones.
     */
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private CompiledClasses() {} // No instances

    /**
     * Create a lookup object on an initialised class from the provided
     * JVM byte code. This class definition must be for a class in this
     * package.
     *
     * @param bytecode of the required class
     * @return lookup on the Java class
     * @throws IllegalAccessException if the class in the claims
     *     membership of a package other than this one.
     */
    public static Lookup classFrom(PyBytes bytecode)
            throws IllegalAccessException {
        return bytecode.defineHiddenClass(LOOKUP, true);
    }
}
