// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.support.internal;

/**
 * The type of exception thrown by invoking an "empty"
 * {@code MethodHandle} in the runtime system. By convention, we
 * initialise undefined method handles to a handle that throws this
 * exception. We may then invoke them without checking for {@code null},
 * as long as we are prepared to catch {@code EmptyException}. The
 * exception is "lightweight" (it comes with no message or stack trace)
 * so it must be caught close enough to the invocation that we can still
 * identify the cause.
 */
public class EmptyException extends Exception {
    // Suppression and stack trace disabled since singleton.
    public EmptyException() { super(null, null, false, false); }

    private static final long serialVersionUID = 1L;
}
