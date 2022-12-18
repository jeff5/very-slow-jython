// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.util.Map;
import java.util.StringJoiner;
import java.util.function.Function;

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
            Function<T, E> exc) throws E {
        if (v != null) { return v; }
        throw exc.apply(v);
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
