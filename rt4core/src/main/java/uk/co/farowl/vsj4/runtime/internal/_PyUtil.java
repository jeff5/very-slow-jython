// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.FastCall;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyUtil;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * Utility methods that should be visible throughout the run time
 * system, but not public API. (That's all {@code public} means in this
 * package.) It's the unpresentable cousin of {@link PyUtil}.
 */
public class _PyUtil {

    /**
     * Call a Python object when the first argument {@code arg0} is
     * provided "loose". This is a frequent need when that argument is
     * the {@code self} object in a method call. The call effectively
     * prepends {@code arg0} to {@code args}. It does no attribute
     * binding.
     *
     * @param callable a Python callable target
     * @param arg0 the first argument
     * @param args arguments from 1 (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException (TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Call_Prepend in call.c
    public static Object callPrepend(Object callable, Object arg0,
            Object[] args, String[] names)
            throws PyBaseException, Throwable {

        // Speed up the common idiom:
        // if (names == null || names.length == 0) ...
        if (names != null && names.length == 0) { names = null; }

        if (callable instanceof FastCall fast) {
            // Fast path recognising optimised callable
            try {
                return fast.call(arg0, args, names);
            } catch (ArgumentError ae) {
                // Demand a proper TypeError.
                throw fast.typeError(ae, args, names);
            }
        } else {
            // Go via callable.__call__
            int n = args.length;
            Object[] allargs = new Object[1 + n];
            allargs[0] = arg0;
            System.arraycopy(args, 0, allargs, 1, n);
            return _PyUtil.standardCall(callable, allargs, names);
        }
    }

    /**
     * Call an object with the standard protocol, via the
     * {@code __call__} special method.
     *
     * @param callable target
     * @param args all the arguments (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException (TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    public static Object standardCall(Object callable, Object[] args,
            String[] names) throws PyBaseException, Throwable {

        try {
            // Call via the special method
            // XXX Stop gap code until support for special functions
            // Object o = PyType.of(callable).lookup("__call__");

            throw new EmptyException();

        } catch (EmptyException e) {
            throw Abstract.typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    private static final String OBJECT_NOT_CALLABLE =
            "'%.200s' object is not callable";
}
