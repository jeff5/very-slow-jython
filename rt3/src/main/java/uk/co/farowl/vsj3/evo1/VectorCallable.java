package uk.co.farowl.vsj3.evo1;

import java.util.Arrays;

/**
 * Support direct calls from Java to the function represented by this
 * object.
 * <p>
 * This is a parallel efficiency mechanism to CPython PEP 590 vectorcall
 * protocol.
 */
interface VectorCallable {

    /**
     * Call the object with arguments given by position and by keyword.
     * {@code args} contains the arguments given at the call site by
     * position, followed by those given by keyword.
     * <p>
     * {@code kwnames} is a Python tuple of the names given at the call
     * site to the last {@code m} of the {@code n} elements of
     * {@code args}. Thus, the slice {@code args[:-m]} provides the
     * arguments given by position, while {@code args[-m:]} provides the
     * arguments given by keyword.
     *
     * @implSpec If no keyword arguments are allowed, the implementation
     *     must check {@code kwnames} is null or empty..
     * @param args arguments given by position and by keyword
     * @param kwnames names of arguments given by keyword
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    // XXX Needs to be callkw or something to disambiguate.
    Object call(Object[] args, PyTuple kwnames) throws Throwable;

    /**
     * Call the object with arguments given by position.
     *
     * @implSpec The default implementation calls
     *     {@link #call(Object[], PyTuple) call(args, null)} .
     * @param args arguments given by position
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object... args) throws Throwable {
        return call(args, null);
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
     *     {@link #call(Object[], PyTuple)} or {@link #call(Object...)}
     *     directly, or take advantage of knowing the actual object
     *     type.
     *
     * @implSpec The default implementation copies the stack slice and
     *     calls {@link #call(Object[], PyTuple)}.
     *
     * @param stack containing the arguments
     * @param sp offset in {@code stack} of argument 0
     * @param n number of positional and keyword arguments provided
     * @param kwnames names of keyword arguments provided
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    default Object call(Object[] stack, int sp, int n, PyTuple kwnames)
            throws Throwable {
        return call(Arrays.copyOfRange(stack, sp, sp + n), kwnames);
    }

    /**
     * @implSpec An object that is a VectorCallable must also support
     *     the classic call (and with the same result as the direct
     *     call).
     *
     * @param args positional arguments
     * @param kwargs keyword argument dictionary or {@code null}
     * @return result of the invocation
     * @throws Throwable from the implementation
     */
    Object __call__(PyTuple args, PyDict kwargs) throws Throwable;
}
