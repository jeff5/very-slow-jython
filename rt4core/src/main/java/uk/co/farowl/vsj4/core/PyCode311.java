// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.util.EnumSet;

/**
 * {@code PyCode311} is a specialisation of {@link PyCode} for the
 * implementation of the Python v3.11 {@code code} object. For our
 * purposes, we need at least two concrete implementations of code
 * objects, to contain CPython byte code and JVM byte code. There is a
 * large amount of commonality between these, which we support here.
 */
// We might also be wrong about which things are CPython-specific
abstract class PyCode311 extends PyCode {

    /**
     * Full constructor based on CPython's
     * {@code PyCode_NewWithPosOnlyArgs}.
     * <p>
     * Where the parameters map directly to an attribute of the code
     * object, that is the best way to explain them. Note that this
     * factory method is tuned to the needs of {@code marshal.read}
     * where the serialised form makes no secret of the version-specific
     * implementation details.
     *
     * @param filename {@code co_filename}
     * @param name {@code co_name}
     * @param qualname {@code co_qualname}
     * @param flags {@code co_flags} a set of code flags
     *
     * @param firstlineno first source line of this code
     *
     * @param consts {@code co_consts}
     * @param names {@code co_names}
     *
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only parameters and those
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only parameters (including those with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only parameters (including those with default values)
     */
    public PyCode311( //
            // Grouped as _PyCodeConstructor in pycore_code.h
            // Metadata
            String filename, String name, String qualname, //
            EnumSet<CodeFlag> flags,
            // Locating the code in the file co_firstlineno
            int firstlineno,
            // Used by the code
            Object[] consts, String[] names,
            // Parameter navigation with varnames
            int argcount, int posonlyargcount, int kwonlyargcount) {

        // Most of the arguments are applicable to any PyCode
        super(filename, name, qualname, flags, //
                firstlineno, //
                consts, names, //
                argcount, posonlyargcount, kwonlyargcount);
    }
}
