// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.internal.Util;

/** Convenience methods for creating and manipulating Python exceptions. */
// Compare CPython errors.c
/*
 * The class name was chosen to resemble the prefix used by CPython
 *  for similar API functions PyErr_*, although much of that API is better represented by methods on PyBaseException.
 */
public class PyErr {

    public static PyBaseException format(PyType excType,
            String formatStr, Object... vals) {
        String msg = String.format(formatStr, vals);
        Object[] args = {msg};
        // XXX Should construct via the type so as to set members.
        // excType.call(args)
        return new PyBaseException(excType, args, Util.EMPTY_STRING_ARRAY);
    }
}
