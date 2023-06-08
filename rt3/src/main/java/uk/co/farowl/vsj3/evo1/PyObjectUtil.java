// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Miscellaneous static helpers commonly needed to implement Python
 * objects in Java.
 */
public class PyObjectUtil {

    private PyObjectUtil() {} // no instances

    /**
     * An implementation of {@code dict.__repr__} that may be applied to
     * any Java {@code Map} between {@code Object}s, in which keys and
     * values are represented as with {@code repr()}.
     *
     * @param map to be reproduced
     * @return a string like <code>{'a': 2, 'b': 3}</code>
     * @throws Throwable from the {@code repr()} implementation
     */
    static String mapRepr(Map<? extends Object, ?> map)
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
    static String toAt(Object o) {
        // For the time being type name means:
        String typeName = PyType.of(o).name;
        return String.format("%s object at %#x", typeName, Py.id(o));
    }

    /**
     * Produce a {@code String} name for a function-like object or its
     * {@code str()} if it doesn't even have a
     * {@code __qualname__}.<pre>
     *     def functionStr(func):
     *         try:
     *             qualname = func.__qualname__
     *         except AttributeError:
     *             return str(func)
     *         try:
     *             module = func.__module__
     *             if module is not None and mod != 'builtins':
     *                 return ".".join(module, qualname)
     *         except AttributeError:
     *             pass
     *         return qualname
     * </pre> This differs from its CPython counterpart
     * {@code _PyObject_FunctionStr} by decisively not adding
     * parentheses.
     *
     * @param func the function
     * @return a name for {@code func}
     */
    // Compare CPython _PyObject_FunctionStr in object.c
    static String functionStr(Object func) {
        Object name;
        try {
            Object qualname = Abstract.lookupAttr(func, "__qualname__");
            if (qualname != null) {
                Object module = Abstract.lookupAttr(func, "__module__");
                if (module != null && module != Py.None
                        && Abstract.richCompareBool("builtins", module,
                                Comparison.NE)) {
                    name = Callables.callMethod(".", "join", module,
                            qualname);
                }
                name = qualname;
            } else {
                name = Abstract.str(func);
            }
            return PyUnicode.asString(name);
        } catch (Throwable e) {
            // Unlike CPython fall back on a generic answer
            return "function";
        }
    }

    // Some singleton exceptions --------------------------------------

    /**
     * The type of exception thrown when an attempt to convert an object
     * to a common data type fails. This type of exception carries no
     * stack context, since it is used only as a sort of "alternative
     * return value".
     */
    public static class NoConversion extends Exception {
        private static final long serialVersionUID = 1L;

        private NoConversion() { super(null, null, false, false); }
    }

    /**
     * A statically allocated {@link NoConversion} used in conversion
     * methods to signal "cannot convert". No stack context is preserved
     * in the exception.
     */
    public static final NoConversion NO_CONVERSION = new NoConversion();

    /**
     * A statically allocated {@link StopIteration} used to signal
     * exhaustion of an iterator, but providing no useful stack context.
     */
    public static final StopIteration STOP_ITERATION =
            new StopIteration();

    // Helpers for methods and attributes -----------------------------

    /**
     * Return a default value if {@code v} is {@code null}. This may be
     * used a wrapper on an expression typically to return a field
     * during attribute access when "not set" should be represented to
     * Python.
     *
     * @param <T> type of {@code v}
     * @param v to return if not {@code null}
     * @param defaultValue to return if {@code v} is {@code null}
     * @return {@code v} or {@code defaultValue}
     */
    static <T> T defaultIfNull(T v, T defaultValue) {
        return v != null ? v : defaultValue;
    }

    /**
     * Return the argument if it is not {@code null}, or {@code None} if
     * it is.
     *
     * @param o object to return is not {@code null}
     * @return {@code o} or {@code None} if {@code o} was {@code null}.
     */
    static Object noneIfNull(Object o) {
        return o == null ? Py.None : o;
    }

    /**
     * Throw an exception if {@code v} is {@code null}.
     *
     * @param <T> type of {@code v}
     * @param <E> type of exception to throw
     * @param v to return if not {@code null}
     * @param exc supplier of exception to throw
     * @return {@code v}
     * @throws E if {@code v} is {@code null}
     */
    static <T, E extends PyException> T errorIfNull(T v,
            Supplier<E> exc) throws E {
        if (v != null) { return v; }
        throw exc.get();
    }

    /**
     * Present an array as a tuple, or if the expression variable is
     * {@code null}, as a Python {@code None}.
     *
     * @param <E> element type of the array
     * @param a array providing elements or {@code null}
     * @return tuple from argument array or {@code None} if the array
     *     was Java {@code null}.
     */
    protected static <E> Object tupleOrNone(E[] a) {
        return a == null ? Py.None : PyTuple.from(a);
    }

    /**
     * Return {@code v} if it is of the expected Python type, otherwise
     * throw supplied exception.
     *
     * @param <T> type of {@code v}
     * @param <E> type of exception to throw
     * @param v to return if of expected type
     * @param type expected
     * @param exc supplier of exception to throw
     * @return {@code v}
     * @throws E if {@code v} is not of expected type
     */
    static <T, E extends PyException> T typeChecked(T v, PyType type,
            Function<T, E> exc) {
        if (type.check(v)) { return v; }
        throw exc.apply(v);
    }
}
