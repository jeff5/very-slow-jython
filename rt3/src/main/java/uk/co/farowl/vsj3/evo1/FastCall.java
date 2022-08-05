package uk.co.farowl.vsj3.evo1;

import java.util.Arrays;

/**
 * Support direct calls from Java to the function represented by this
 * object, potentially without constructing an argument array. Clients
 * that know the number of arguments they provide are able to call
 * {@code call(...)} with exactly that number. Callable objects that
 * implement this interface may override signatures of {@code call(...)}
 * that they implement most efficiently.
 * <p>
 * This is an efficiency mechanism similar to the "fast call" paths in
 * CPython. It may provide a basis for efficient call sites for function
 * and method calling when argument lists are simple.
 */
interface FastCall {

    /**
     * Invoke the target object with standard arguments
     * ({@code Object[]} and {@code String[]}), providing all the
     * argument values from the caller and names for those given by
     * keyword. If no other methods are implemented, a call to any other
     * interface method will land here with an array of the
     * arguments.This is to provide implementations of {@code __call__}
     * with a default when no more optimal call is possible.
     * <p>
     * {@code np = args.length - names.length} arguments are given by
     * position, and the keyword arguments are
     * {{@code names[i]:args[np+i]}}.
     *
     * @implSpec An object that is a {@link FastCall} must support the
     *     standard call (and with the same result as the direct call).
     *     It must not call any other method in this interface, as that
     *     would risk creating a loop.
     * @implNote The reason we do not name this method {@code __call__}
     *     is because that may be called directly from Python, and an
     *     object should have the chance to choose amongst the optimised
     *     implementations specified by this interface, finally
     *     resorting to {@link #call(Object[], String[])} if necessary.
     *
     * @param args all arguments given, positional then keyword
     * @param names of keyword arguments or {@code null}
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    Object call(Object[] args, String[] names) throws Throwable;

    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[], String[])} with a null array of names.
     *
     * @param args arguments given by position
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object[] args) throws Throwable {
        return call(args, null);
    }

    /* The idea is to provide a series of specialisations e.g. */
    // Object call(arg0, arg1, arg2) { ... }
    /*
     * Implementations then override __call__(Object[], String[]), and
     * all ones they can support efficiently, e.g. call(s), call(s, a),
     * call(s, a, b) for an instance method with up to two arguments.
     * Anything else is converted by a default implementation to
     * call(Object[]).
     */
    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[])} with an empty array.
     *
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call() throws Throwable {
        return call(Py.EMPTY_ARRAY);
    }

    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[])} with an array the single argument.
     *
     * @param a0 single argument (may be {@code self})
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object a0) throws Throwable {
        return call(new Object[] {a0});
    }

    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[])} with an array of the arguments.
     *
     * @param a0 zeroth argument (may be {@code self})
     * @param a1 next argument
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object a0, Object a1) throws Throwable {
        return call(new Object[] {a0, a1});
    }

    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[])} with an array of the arguments.
     *
     * @param a0 zeroth argument (may be {@code self})
     * @param a1 next argument
     * @param a2 next argument
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object a0, Object a1, Object a2)
            throws Throwable {
        return call(new Object[] {a0, a1, a2});
    }

    /**
     * Call the object with arguments given by position only.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[])} with an array of the arguments.
     *
     * @param a0 zeroth argument (may be {@code self})
     * @param a1 next argument
     * @param a2 next argument
     * @param a3 next argument
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object a0, Object a1, Object a2, Object a3)
            throws Throwable {
        return call(new Object[] {a0, a1, a2, a3});
    }

    /**
     * Call this object with the vector call protocol. This supports
     * CPython byte code generated according to the conventions in
     * PEP-590.
     * <p>
     * The {@code stack} argument (which is often the interpreter stack)
     * contains, at a given offset {@code start}, the {@code count}
     * arguments of which the last {@code len(kw)} are given by keyword
     * (and may therefore not be in the order expected by the called
     * object).
     *
     * @param s positional and keyword arguments
     * @param p position of arguments in the array
     * @param n number of positional <b>and keyword</b> arguments
     * @param names of keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Vectorcall in abstract.h
    // In CPython nargs counts only positional arguments
    default Object vectorcall(Object[] s, int p, int n, String[] names)
            throws Throwable {
        if (names == null || names.length == 0)
            return vectorcall(s, p, n);
        else {
            Object[] args = Arrays.copyOfRange(s, p, p + n);
            return call(args, names);
        }
    }

    /**
     * Call this object with the vector call protocol, in the case where
     * no arguments were given by keyword. This supports CPython byte
     * code generated according to the conventions in PEP-590, but
     * specialised for this case.
     * <p>
     * The {@code stack} argument (which is often the interpreter stack)
     * contains, at a given offset {@code start}, the {@code count}
     * arguments given by position.
     *
     * @param s positional and keyword arguments
     * @param p position of arguments in the array
     * @param n number of <b>positional</b> arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Vectorcall in abstract.h
    // In CPython nargs counts only positional arguments
    default Object vectorcall(Object[] s, int p, int n)
            throws Throwable {
        switch (n) {
            case 0:
                return call();
            case 1:
                return call(s[p]);
            case 2:
                return call(s[p++], s[p]);
            case 3:
                return call(s[p++], s[p++], s[p]);
            case 4:
                return call(s[p++], s[p++], s[p++], s[p]);
            default:
                return call(Arrays.copyOfRange(s, p, p + n));
        }
    }
}
