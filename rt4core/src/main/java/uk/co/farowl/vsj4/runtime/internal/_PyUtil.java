// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyUtil;

/**
 * Utility methods that should be visible throughout the run time
 * system, but not public API. It's the unpresentable cousin of
 * {@link PyUtil}.
 */
public class _PyUtil {

    /**
     * Call an object with the standard {@code __call__} protocol, but
     * where the first argument is "loose", and must be prepended to the
     * usual array of arguments. This is potentially useful in calling
     * an instance method.
     *
     * @param callable target
     * @param arg0 the first argument
     * @param args arguments from 1 (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Call_Prepend in call.c
    // Note that CPython allows only exactly tuple and dict.
    public static Object call(Object callable, Object arg0,
            Object[] args, String[] names)
            throws PyBaseException, Throwable {
        // Note names are end-relative, so remain valid after the shift.
        Object[] allargs = new Object[args.length + 1];
        allargs[0] = arg0;
        System.arraycopy(args, 0, allargs, 1, args.length);
        return Callables.call(callable, allargs, names);
    }

}
