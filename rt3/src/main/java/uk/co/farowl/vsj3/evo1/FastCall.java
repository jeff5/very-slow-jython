package uk.co.farowl.vsj3.evo1;

import java.util.Arrays;

/**
 * Support direct calls from Java to the function represented by this
 * object, potentially without constructing an argument array.
 * <p>
 * This is an efficiency mechanism similar to the "fast call" paths in
 * CPython. It may provide a basis for efficient call sites for function
 * and method calling when argument lists are simple.
 */
interface FastCall {

    /**
     * Invoke the target object with standard arguments
     * ({@code Object[]} and {@code String[]}).
     *
     * @implSpec An object that is a {@link FastCall} must support the
     *     standard call (and with the same result as the direct call).
     *
     * @param args all arguments given, positional then keyword
     * @param names of keyword arguments or {@code null}
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    Object __call__(Object[] args, String[] names) throws Throwable;

    /**
     * Call the object with arguments given by position.
     *
     * @implSpec The default implementation calls
     *     {@link #__call__(Object[], String[]) __call__(args, null)} .
     * @param args arguments given by position
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object... args) throws Throwable {
        return __call__(args, null);
    }

    // The idea is to provide a series of specialisations e.g.
    // default Object call(arg0, arg1, arg2) { ... };
    // Implementations then override the one they like and __call__.

    default Object call() throws Throwable {
        return call(Py.EMPTY_ARRAY);
    }

    default Object call(Object arg0) throws Throwable {
        return call(new Object[] {arg0});
    }

    default Object call(Object arg0, Object arg1) throws Throwable {
        return call(new Object[] {arg0, arg1});
    }

    default Object call(Object arg0, Object arg1, Object arg2)
            throws Throwable {
        return call(new Object[] {arg0, arg1, arg2});
    }

    default Object call(Object arg0, Object arg1, Object arg2,
            Object arg3) throws Throwable {
        return call(new Object[] {arg0, arg1, arg2, arg3});
    }

    /**
     * Call this object with the vector call protocol. This supports
     * CPython byte code generated according to the conventions in
     * PEP-590.
     * <p>
     * The {@code stack} argument (which is often the interpreter stack)
     * contains, at a given offset {@code start}, the {@code count}
     * arguments of which the last {@code len(kwnames)} are given by
     * keyword (and may therefore not be in the order expected by the
     * called object).
     *
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of <b>positional</b> arguments
     * @param kwnames names of keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Vectorcall in abstract.h
    // In CPython nargs counts only positional arguments
    default Object vectorcall(Object[] stack, int start, int nargs,
            PyTuple kwnames) throws Throwable {
        Object[] args = Arrays.copyOfRange(stack, start, start + nargs);
        String[] names = Callables.namesArray(kwnames);
        if (names.length == 0) {
            // Positional arguments only.
            return call(args);
        } else {
            // Some given by keyword.
            return __call__(args, names);
        }
    }
}
