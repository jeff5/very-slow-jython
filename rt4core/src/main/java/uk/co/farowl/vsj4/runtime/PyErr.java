// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.internal.Util;

/**
 * Convenience methods for creating and manipulating Python exceptions.
 */
// Compare CPython errors.c
/*
 * The class name was chosen to resemble the prefix used by CPython for
 * similar API functions PyErr_*, although much of that API is better
 * represented by methods on PyBaseException.
 */
public class PyErr {

    /**
     * Create a Python exception for the caller to throw, specifying the
     * type of exception, a format string and arguments to compose the
     * message (as in Java {@code String.format}, not quite as in the
     * CPython API)). Note that the exception will be created using only
     * the resulting {@code String} message as the argument. If the
     * Python constructor (__new__ and __init__) of the exception takes
     * other arguments, this call cannot be used to provide them. The
     * first argument is usually one of the type constants found in
     * {@link PyExc}.
     *
     * @param excType exception type
     * @param formatStr format string (Java conventions)
     * @param vals to substitute in the format string
     * @return an exception to throw
     */
    // Compare CPython PyErr_Format in errors.c
    public static PyBaseException format(PyType excType,
            String formatStr, Object... vals) {
        String msg = vals.length == 0 ? formatStr
                : String.format(formatStr, vals);
        Object[] args = {msg};
        // XXX Should construct via the type so as to set members.
        // excType.call(args)
        return new PyBaseException(excType, new Object[] {msg},
                Util.EMPTY_STRING_ARRAY);
    }
}
