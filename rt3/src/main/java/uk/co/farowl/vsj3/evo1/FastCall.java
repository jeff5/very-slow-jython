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
     * @implSpec An object that is a {@link FastCall} must support the
     *     classic call (and with the same result as the direct call).
     *
     * @param args all arguments given positional then keyword
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

    /**
     * Call the object with arguments given by position and by keyword.
     * The {@code stack} argument (which is often the interpreter stack)
     * contains, at a given offset {@code sp}, the arguments given at
     * the call site by position, followed by those given by keyword,
     * {@code n} arguments in all.
     * <p>
     * {@code kwnames} is a Python tuple of the {@code m} names of the
     * last {@code m} elements of the slice, as provided at the call
     * site. Thus, the slice {@code stack[sp:sp+n-m]} provides the
     * arguments given by position, while {@code stack[sp+n-m:sp+n]}
     * provides the arguments given by keyword. This matches (in a Java
     * way) the PEP 590 vectorcall protocol.
     *
     * @apiNote The method exists to provide efficient support to
     *     implementation of the interpreter for CPython byte code. Most
     *     other clients will be able to use
     *     {@link #__call__(Object[], String[])} or
     *     {@link #call(Object...) call(...)} directly, or take
     *     advantage of knowing the actual object type.
     *
     * @implSpec The default implementation copies the stack slice and
     *     calls {@link #__call__(Object[], String[]) __call__(args,
     *     kwnames)}.
     *
     * @param stack containing the arguments
     * @param sp offset in {@code stack} of argument 0
     * @param n number of positional and keyword arguments provided
     * @param kwnames names of keyword arguments provided
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object vectorcall(Object[] stack, int sp, int n,
            String[] kwnames) throws Throwable {
        // Differing convention: take the keyword arguments too
        if (kwnames != null) { n += kwnames.length; }
        return __call__(Arrays.copyOfRange(stack, sp, sp + n), kwnames);
    }
}
