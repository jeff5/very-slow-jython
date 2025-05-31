// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * Miscellaneous static helpers commonly needed to implement Python
 * objects in Java.
 */
public class PyUtil {

    private PyUtil() {} // no instances

    /**
     * Convenient default {@code toString} implementation that normally
     * calls {@code __str__}, to provide a familiar-looking output. The
     * values we see in Java debugging are often the {@code toString()}
     * of an object, so we produce a {@code String}, even from broken or
     * partly-ready types. Use as:<pre>
     * public String toString() {
     *     return PyUtil.defaultToString(this);
     * }</pre>
     * <p>
     * When using this method to define {@code toString()}, do not also
     * define {@code __str__} or {@code __repr__} in terms of
     * {@code toString()}, as this will create a loop.
     * <p>
     * The following sequence of delegations operates in Python, when
     * the built in function {@code str()} is called:
     * <ol>
     * <li>{@code str(x)} returns {@code x} if {@code X=type(x)} is
     * exactly {@code str}.</li>
     * <li>{@code str(x)} calls {@code X.__str__}, if it is not empty,
     * or defaults to {@code repr(x)}.</li>
     * <li>{@code X.__str__} may be the inherited
     * {@code object.__str__}.</li>
     * <li>{@code object.__str__(x)} calls {@code X.__repr__(x)}, if it
     * is not empty, or defaults to {@code object.__repr__(x)}.</li>
     * </ol>
     * The following sequence of delegations operates in Python, when
     * the built in function {@code repr()} is called:
     * <ol>
     * <li>{@code repr(x)} calls {@code X.__repr__(x)}, where
     * {@code X=type(x)}, if it is not empty, or defaults to a formula
     * {@code "<X object at A>"} formed from the type and the hex
     * identity of {@code x}.</li>
     * <li>{@code X.__repr__} may be the inherited
     * {@code object.__repr__}.</li>
     * <li>{@code object.__repr__(x)} returns a string like
     * {@code "<M.X object at A>"} formed from the module (if any and
     * not {@code builtins}), the type and the hex identity of
     * {@code x}.</li>
     * </ol>
     * When we write that a special method is "empty" here, we mean that
     * it throws {@link EmptyException} when called. In CPython, the
     * type slot would be {@code NULL}, but we don't use that
     * convention.
     *
     * @param o object to represent
     * @return a string representation
     */
    public static String defaultToString(Object o) {

        if (o == null) { return "<null>"; }

        String name = null;
        if (PyType.systemReady()) {
            Representation rep = null;
            try {
                rep = PyType.getRepresentation(o);
                MethodHandle str = rep.op_str();
                Object r = str.invokeExact(o);
                return r.toString();
            } catch (Throwable e) {}

            // o.__str__ not working. Emulate object.__repr__.
            try {
                // Has o a Python type at all?
                name = rep.pythonType(o).getName();
            } catch (Throwable e) {}
        }

        if (name == null) {
            // Maybe type system broken or not ready. Use Java name.
            Class<?> c = o.getClass();
            if (c.isAnonymousClass()) {
                name = c.getName();
            } else {
                name = c.getSimpleName();
            }
        }

        // Produce something like object.__repr__(o)
        return String.format("<%s object at %#x>", name, Py.id(o));
    }

    /**
     * Return an arbitrary throwable wrapped (if necessary) as a Python
     * exception, in the context of an implementation of
     * {@code __new__}. The special method
     * {@code base.__new__(cls, ...)} has been called, that is, the type
     * {@code base} is requested to create an instance of type
     * {@code cls}, and this has failed for the reason given as a
     * {@code Throwable}.
     * <p>
     * This is a convenience method when implementing an exposed
     * {@code __new__} in a built-in (or extension) type. If the reason
     * is not already a Python exception, we return a
     * {@link PyBaseException TypeError} with a message along the lines
     * {@code "Cannot construct instance of 'CLS' in %BASE.__new__ "},
     * and a the cause embedded with
     * {@link Throwable#initCause(Throwable)}.
     *
     * @param cls of which an instance is required
     * @param base that provides the {@code __new__} method.
     * @param cause resulting from the attempt to construct the object
     * @return a Python exception reporting the failure.
     */
    public static PyBaseException cannotConstructInstance(PyType cls,
            PyType base, Throwable cause) {
        if (cause instanceof PyBaseException e) {
            // Usually signals no matching constructor
            return e;
        } else {
            // Failed while finding/invoking constructor
            PyBaseException err = PyErr.format(PyExc.TypeError,
                    CANNOT_CONSTRUCT_INSTANCE, cls.getName(),
                    base.getName());
            err.initCause(cause);
            return err;
        }
    }

    private static final String CANNOT_CONSTRUCT_INSTANCE =
            "Cannot construct instance of '%s' in %s.__new__ ";

    // Some singleton exceptions --------------------------------------

    /**
     * The type of exception thrown when an attempt to convert an object
     * to a common data type fails. This type of exception carries no
     * stack context, since it is used only as a sort of "alternative
     * return value".
     */
    static class NoConversion extends Exception {
        private static final long serialVersionUID = 1L;

        private NoConversion() { super(null, null, false, false); }
    }

    /**
     * A statically allocated {@link NoConversion} used in conversion
     * methods to signal "cannot convert". No stack context is preserved
     * in the exception.
     */
    static final NoConversion NO_CONVERSION = new NoConversion();

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
