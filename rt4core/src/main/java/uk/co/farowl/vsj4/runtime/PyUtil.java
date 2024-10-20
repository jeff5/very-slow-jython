// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

/**
 * Miscellaneous static helpers commonly needed to implement Python
 * objects in Java.
 */
public class PyUtil {

    private PyUtil() {} // no instances

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
        String typeName = PyType.of(o).getName();
        return String.format("%s object at %#x", typeName, Py.id(o));
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
}
