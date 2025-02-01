// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import java.util.Map;
import java.util.StringJoiner;

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.FastCall;
import uk.co.farowl.vsj4.runtime.Py;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUtil;
import uk.co.farowl.vsj4.support.internal.EmptyException;
import uk.co.farowl.vsj4.support.internal.Util;

/**
 * Utility methods that should be visible throughout the run time
 * system, but not public API. (That's all {@code public} means in this
 * package.) It's the unpresentable cousin of {@link PyUtil}.
 */
public class _PyUtil {

    /**
     * An implementation of {@code dict.__repr__} that may be applied to
     * any Java {@code Map} between {@code Object}s, in which keys and
     * values are represented as with {@code repr()}.
     *
     * @param map to be reproduced
     * @return a string like <code>{'a': 2, 'b': 3}</code>
     * @throws Throwable from the {@code repr()} implementation
     */
    public static String mapRepr(Map<? extends Object, ?> map)
            throws Throwable {
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        for (Map.Entry<? extends Object, ?> e : map.entrySet()) {
            String key = Abstract.repr(e.getKey()).toString();
            String value = Abstract.repr(e.getValue()).toString();
            sj.add(key + ": " + value);
        }
        return sj.toString();
    }

    /**
     * A string along the lines "T object at 0xhhh", where T is the type
     * of {@code o}. This is for creating default {@code __repr__}
     * implementations seen around the code base and containing this
     * form. By implementing it here, we encapsulate the problem of
     * qualified type name and what "address" or "identity" should mean.
     *
     * @param o the object (not its type)
     * @return string denoting {@code o}
     */
    public static String toAt(Object o) {
        // For the time being type name means:
        String typeName = PyType.of(o).getName();
        return String.format("%s object at %#x", typeName, Py.id(o));
    }

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
                // TypeError needs full argument list to make sense.
                Object[] a = Util.prepend(arg0, args);
                throw fast.typeError(ae, a, names);
            }
        } else {
            // Go via callable.__call__
            Object[] a = Util.prepend(arg0, args);
            return _PyUtil.standardCall(callable, a, names);
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
