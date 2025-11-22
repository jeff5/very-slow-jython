// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import java.util.Map;
import java.util.StringJoiner;

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.FastCall;
import uk.co.farowl.vsj4.runtime.Py;
import uk.co.farowl.vsj4.runtime.PyAttributeError;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUtil;
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
     * Call a Python object when the first argument {@code self} is
     * provided "loose". This is a frequent need when that argument is
     * the {@code self} object in a method call. The call effectively
     * prepends {@code self} to {@code args}. It will use the
     * {@link FastCall} interface to avoid actually prepending to an
     * array if the {@code callable} supports it. It does no attribute
     * binding.
     *
     * @param callable a Python callable target
     * @param self the first argument
     * @param args arguments from 1 (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException (TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Call_Prepend in call.c
    public static Object callPrepend(Object callable, Object self,
            Object[] args, String[] names)
            throws PyBaseException, Throwable {

        if (callable instanceof FastCall fast) {
            // Fast path recognising optimised callable
            try {
                if (names == null || names.length == 0) {
                    // No arguments by keyword
                    switch (args.length) {
                        case 0:
                            return fast.call(self);
                        case 1:
                            return fast.call(self, args[0]);
                        case 2:
                            return fast.call(self, args[0], args[1]);
                        case 3:
                            return fast.call(self, args[0], args[1],
                                    args[2]);
                        default:
                            // Ensure args is treated as an array
                            return fast.call(self, args, null);
                    }
                } else {
                    // Some arguments by keyword
                    return fast.call(self, args, names);
                }
            } catch (ArgumentError ae) {
                // TypeError needs full argument list to make sense.
                Object[] a = Util.prepend(self, args);
                throw fast.typeError(ae, a, names);
            }

        } else {
            // Go via callable.__call__ special method
            Object[] a = Util.prepend(self, args);
            return Callables.standardCall(callable, a, names);
        }
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object has no attribute N", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError noAttributeError(Object v,
            Object name) {
        return noAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object has no attribute N", where T is the type given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError noAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object has no attribute '%.50s'";
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "'N' must be T, not 'X' as received" involving the name
     * N of the attribute, any descriptive phrase T and the type X of
     * {@code value}, e.g. "'<u>__dict__</u>' must be <u>a
     * dictionary</u>, not '<u>list</u>' as received".
     *
     * @param name of the attribute
     * @param kind expected kind of thing
     * @param value provided to set this attribute in some object
     * @return exception to throw
     */
    public static PyBaseException attrMustBe(String name, String kind,
            Object value) {
        return PyErr.format(PyExc.TypeError, ATTR_TYPE, name, kind,
                PyType.of(value).getName());
    }

    private static final String ATTR_TYPE =
            "'%.50s' must be %.50s, not '%.50s' as received";

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "'N' must be a string, not 'X' as received".
     *
     * @param name of the attribute
     * @param value provided to set this attribute in some object
     * @return exception to throw
     */
    public static PyBaseException attrMustBeString(String name,
            Object value) {
        return attrMustBe(name, "a string", value);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N is read-only", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError readonlyAttributeError(Object v,
            Object name) {
        return readonlyAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N is read-only", where T is the type given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError readonlyAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object attribute '%s' is read-only";
        // XXX It would be cool if the type could be parameterised
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N cannot be deleted", where T is the type
     * of the object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError mandatoryAttributeError(Object v,
            Object name) {
        return mandatoryAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N cannot be deleted", where T is the type
     * given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    public static PyAttributeError mandatoryAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object attribute '%s' cannot be deleted";
        // XXX It would be cool if the type could be parameterised
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "can't set attributes of X" giving str of {@code name}.
     *
     * @param obj actual object on which setting failed
     * @return exception to throw
     */
    public static PyBaseException cantSetAttributeError(Object obj) {
        return PyErr.format(PyExc.TypeError,
                "can't set attributes of %.200s", obj);
    }
}
