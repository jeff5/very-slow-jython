// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.function.Function;

/** Placeholder until implemented. */
// FIXME implement me
public class PyUnicode extends TypedPyObject {
    /** The type {@code str}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("str", MethodHandles.lookup()) //
                    .adopt(String.class));
    /**
     * The implementation holds a Java {@code int} array of code points.
     */
    private final int[] value;

    protected PyUnicode(PyType type, int[] v) {
        super(type);
        this.value = Arrays.copyOf(v, v.length);
    }

    /**
     * Present a Python {@code str} as a Java {@code String} value or
     * raise a {@link PyBaseException TypeError}. This is for use when
     * the argument is expected to be a Python {@code str} or a
     * sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws PyBaseException(TypeError) if {@code v} is not a Python
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
}
