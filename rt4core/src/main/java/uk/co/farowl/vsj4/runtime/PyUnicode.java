// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.util.Arrays;
import java.util.Iterator;
import java.util.function.Function;

import uk.co.farowl.vsj4.runtime.PyUtil.NoConversion;

/** Placeholder until implemented. */
// FIXME implement me
public class PyUnicode extends TypedPyObject {
    /** The type {@code str}. */
    // Bootstrap type so ask the type system to resolve it.
    public static final PyType TYPE = PyType.of("");

    /**
     * The implementation holds a Java {@code int} array of code points.
     */
    private final int[] value;

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given array of code points. The constructor
     * takes a copy.
     *
     * @param type actual type the instance should have
     * @param codePoints the array of code points
     */
    protected PyUnicode(PyType type, int[] codePoints) {
        super(type);
        this.value = Arrays.copyOf(codePoints, codePoints.length);
    }

    // Special methods -----------------------------------------------

    @SuppressWarnings("unused")
    static Object __repr__(Object self) {
        try {
            // Ok, it should be more complicated but I'm in a hurry.
            return "'" + convertToString(self) + "'";
        } catch (NoConversion nc) {
            throw Abstract.impossibleArgumentError("str", self);
        }
    }

    // Java API ------------------------------------------------------

    /**
     * Present a Python {@code str} as a Java {@code String} value or
     * raise a {@link PyBaseException TypeError}. This is for use when
     * the argument is expected to be a Python {@code str} or a
     * sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws PyBaseException (TypeError) if {@code v} is not a Python
     *     {@code str}
     */
    public static String asString(Object v) throws PyBaseException {
        return asString(v, o -> Abstract.requiredTypeError("a str", o));
    }

    /**
     * Present a qualifying object {@code v} as a Java {@code String}
     * value or throw {@code E}. This is for use when the argument is
     * expected to be a Python {@code str} or a sub-class of it.
     * <p>
     * The detailed form of exception is communicated in a
     * lambda-function {@code exc} that will be called (if necessary)
     * with {@code v} as argument. We use a {@code Function} to avoid
     * binding a variable {@code v} at the call site.
     *
     * @param <E> type of exception to throw
     * @param v claimed {@code str}
     * @param exc to supply the exception to throw wrapping {@code v}
     * @return {@code String} value
     * @throws E if {@code v} is not a Python {@code str}
     */
    public static <E extends PyBaseException> String asString(Object v,
            Function<Object, E> exc) throws PyBaseException {
        if (v instanceof String)
            return (String)v;
        // else if (v instanceof PyUnicode)
        // return ((PyUnicode)v).asString();
        throw exc.apply(v);
    }

    // Plumbing ------------------------------------------------------

    /**
     * Convert a Python {@code str} to a Java {@code str} (or throw
     * {@link NoConversion}). This is suitable for use where a method
     * argument should be (exactly) a {@code str}, or an alternate path
     * taken.
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action.
     *
     * @param v to convert
     * @return converted to {@code String}
     * @throws NoConversion v is not a {@code str}
     */
    static String convertToString(Object v) throws NoConversion {
        if (v instanceof String)
            return (String)v;
        // else if (v instanceof PyUnicode)
        // return ((PyUnicode)v).asString();
        throw PyUtil.NO_CONVERSION;
    }

    // PLACEHOLDERS to satisfy linkage -------------------------------
    /** Placeholder. */
    public class CodepointDelegate implements Iterable<Integer> {
        // TODO Auto-generatedstub
        @Override
        public Iterator<Integer> iterator() {
            // TODO Auto-generated method stub
            return null;
        }

        public Object length() {
            // TODO Auto-generated method stub
            return null;
        }

        public int compareTo(CodepointDelegate adapt) {
            // TODO Auto-generated method stub
            return 0;
        }
    }

    /** Placeholder. */
    static CodepointDelegate adapt(Object v) throws NoConversion {
        throw PyUtil.NO_CONVERSION;
    }

    /** Placeholder. */
    public CodepointDelegate adapt() {
        // TODO Auto-generated method stub
        return null;
    }

}
