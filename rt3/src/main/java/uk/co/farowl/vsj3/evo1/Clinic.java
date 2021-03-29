package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.util.Arrays;

/**
 * A collection of methods and {@code MethodHandle}s for converting
 * arguments when calling a Java method from Python. The class enables
 * the implementation of built-in (or extension) Python types to be
 * written in a natural way using Java standard and primitive types.
 * <p>
 * The class name refers to the CPython Argument Clinic (by Larry
 * Hastings) which generates argument processing code for exposed
 * methods defined in C and by a textual header.
 */
// Compare CPython *.c.h wrappers
class Clinic {

    /** Lookup for resolving handles throughout the class. */
    private static final Lookup LOOKUP = MethodHandles.lookup();

    private static final Class<?> O = Object.class;

    private static final MethodHandle intArgMH;
    private static final MethodHandle doubleArgMH;
    private static final MethodHandle stringArgMH;

    private static final MethodHandle voidValueMH;

    private static final MethodHandle intValueMH;
    private static final MethodHandle doubleValueMH;
    private static final MethodHandle stringValueMH;

    /**
     * Helpers used to construct {@code MethodHandle}s for type
     * conversion.
     */
    static {
        try {
            intArgMH = LOOKUP.findStatic(PyLong.class, "asInt",
                    MethodType.methodType(int.class, O));
            doubleArgMH = LOOKUP.findStatic(PyFloat.class, "asDouble",
                    MethodType.methodType(double.class, O));
            stringArgMH = LOOKUP.findStatic(Clinic.class, "stringArg",
                    MethodType.methodType(String.class, O));

            voidValueMH = MethodHandles.constant(O, Py.None);

            intValueMH = LOOKUP
                    .findConstructor(PyLong.class,
                            MethodType.methodType(void.class,
                                    long.class))
                    .asType(MethodType.methodType(O, int.class));
            doubleValueMH = LOOKUP.findConstructor(PyFloat.class,
                    MethodType.methodType(void.class, double.class));
            stringValueMH =
                    LOOKUP.findStatic(Py.class, "str", MethodType
                            .methodType(PyUnicode.class, String.class));

            /*
             * intValueMH = LOOKUP.findStatic(Py.class, "val",
             * MethodType.methodType(O, int.class)); doubleValueMH =
             * LOOKUP.findStatic(Py.class, "val",
             * MethodType.methodType(O, double.class));
             */

            /*
             * intValueMH = LOOKUP.findStatic(Clinic.class, "intValue",
             * MethodType.methodType(O, int.class)); doubleValueMH =
             * LOOKUP.findStatic(Clinic.class, "doubleValue",
             * MethodType.methodType(O, double.class));
             */
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new InterpreterError(e, "during handle lookup");
        }
    }

    private Clinic() {} // Oh no you don't

    /**
     * Create an array of filters to convert an existing method handle,
     * with the given type, to one that expects arguments that are all
     * {@code Object} or a sub-class. The returned array is suitable as
     * an argument to {@code MethodHandles.filterArguments.}
     * <p>
     * Where the existing method (described by {@code MethodType mt})
     * expects a primitive type, or a supported reference type (such as
     * {@code BigInteger}), the handle to a standard conversion will be
     * supplied. Where the existing method expects a Java {@code Object}
     * or a sub-class, a {@code null} conversion will be supplied. These
     * conversions will throw a Python exception (often
     * {@link TypeError}), when invoked on objects they cannot convert,
     * according to the usual behaviour of Python.
     *
     *
     * @param mt type to adapt.
     * @return array of filter-adaptors to expect {@code Object}.
     */
    static MethodHandle[] argumentFilter(MethodType mt) {
        final int n = mt.parameterCount();
        MethodHandle[] filter = new MethodHandle[n];
        for (int p = 0; p < n; p++) {
            Class<?> pt = mt.parameterType(p);
            filter[p] = adaptParameterToObject(pt);
        }
        return filter;
    }

    /**
     * Return a filter that will adapt an existing method handle with
     * the given type, to one that the returns {@code Object} or a
     * sub-class. The handle produced is suitable as an argument to
     * {@code MethodHandle.filterReturnValue}
     * <p>
     * This adaptor will often be a constructor for the implementation
     * type or equivalent convenience method. If the return type of is
     * {@code void.class}, the adaptor takes no arguments and produces
     * {@link Py#None}. .
     * <p>
     * If the return type is already {@code Object} or a sub-class, this
     * method returns {@code null} (which is not suitable as an argument
     * to {@code MethodHandle.filterReturnValue}). Client code must test
     * for this.
     *
     * @param mt type to adapt.
     * @return {@code null} or a filter-adaptor to return
     *     {@code Object}.
     */
    static MethodHandle returnFilter(MethodType mt) {
        return adaptReturnToObject(mt.returnType());
    }

    static MethodType purePyObject(MethodType mt) {
        final int n = mt.parameterCount();
        Class<?>[] pt = new Class<?>[n];
        Arrays.fill(pt, O);
        return MethodType.methodType(O, pt);
    }

    // Conversions to Java -------------------------------------------

    /**
     * The logic of this method defines the standard for converting
     * Python types to a specified Java type.
     *
     * @param c Java type
     * @return filter converting Python object to {@code c}.
     */
    private static MethodHandle adaptParameterToObject(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == int.class) {
                return Clinic.intArgMH;
            } else if (c == double.class) { return Clinic.doubleArgMH; }
        } else {
            if (c == String.class) {
                return Clinic.stringArgMH;
            } else if (c == O) {
                // The method expects a Object
                return null;
            } else if (O.isAssignableFrom(c)) {
                // The method expects some sub-class of Object
                return null;
            }
        }
        throw new InterpreterError(
                "Cannot convert Python object to Java %s",
                c.getSimpleName());
    }

//    /**
//     * @param o to convert
//     * @return Java {@code int} value of an object
//     * @throws TypeError if not interpretable as integer
//     * @throws Throwable from {@code o.__int__} if called
//     */
//    private static int intArg(Object o) throws TypeError, Throwable {
//        Class<? extends Object> c = o.getClass();
//        if (c == PyLong.class) {
//            return ((PyLong) o).intValue();
//        } else if (o instanceof PyFloat) {
//            throw Abstract.requiredTypeError("an integer", o);
//        } else {
//            return Number.size(o);
//        }
//    }

//    /**
//     * @param o to convert
//     * @return Java {@code double} value of an object
//     * @throws TypeError if not interpretable as a float
//     * @throws Throwable from {@code o.__float__} if called
//     */
//    private static double doubleArg(Object o)
//            throws TypeError, Throwable {
//        Class<? extends Object> c = o.getClass();
//        if (c == PyFloat.class) {
//            return ((PyFloat) o).doubleValue();
//        } else if (c == PyLong.class) {
//            return ((PyLong) o).doubleValue();
//        } else {
//            return Number.toFloat(o).doubleValue();
//        }
//    }

    /**
     * @param o to convert
     * @return Java {@code String} value of an object
     * @throws TypeError if not interpretable as a string
     * @throws Throwable from {@code o.__str__} if called
     */
    @SuppressWarnings("unused")
    private static String stringArg(Object o)
            throws TypeError, Throwable {
        Class<? extends Object> c = o.getClass();
        if (c == PyUnicode.class) {
            return ((PyUnicode) o).toString();
        } else {
            return Abstract.str(o).toString();
        }
    }

    // Conversions from Java -----------------------------------------

    /**
     * The logic of this method defines the standard for converting
     * specified Java types to Python.
     *
     * @param c Java type
     * @return filter converting {@code c} to a Python object.
     */
    private static MethodHandle adaptReturnToObject(Class<?> c) {
        if (c.isPrimitive()) {
            if (c == void.class) {
                return Clinic.voidValueMH;
            } else if (c == int.class) {
                return Clinic.intValueMH;
            } else if (c == double.class) {
                return Clinic.doubleValueMH;
            }
        } else {
            if (c == String.class) {
                return Clinic.stringValueMH;
            } else if (O.isAssignableFrom(c)) {
                // The value is already some kind of Object
                return null;
            }
        }
        throw new InterpreterError(
                "Cannot convert Java %s to Python object",
                c.getSimpleName());
    }

}
