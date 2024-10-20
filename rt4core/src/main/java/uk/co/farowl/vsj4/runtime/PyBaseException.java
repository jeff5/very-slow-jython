// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static uk.co.farowl.vsj4.support.internal.Util.EMPTY_ARRAY;
import static uk.co.farowl.vsj4.support.internal.Util.EMPTY_STRING_ARRAY;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Map;;

/**
 * The Python {@code BaseException} exception and many common Python
 * exceptions (for example {@code TypeError}) are represented by
 * instances of this class, not of its subclasses. A Java subclass of
 * {@code PyBaseException} exists only where a Python subclass adds
 * fields to its parent.
 * <p>
 * The Python type of the exception is represented as a field (see
 * {@link #getType()}). The Python type is re-writable through
 * {@link #setType(Object)} with types represented by the same Java
 * class (which obviously cannot change).
 * <p>
 * A Java {@code try-catch} construct intended to catch Python
 * exceptions should catch {@code PyBaseException}. If it is intended to
 * catch only specific kinds of Python exception it must examine the
 * type and re-throw the unwanted exceptions.
 *
 * @implNote It would have been convenient, when catching exceptions in
 *     Java, if the different classes of Python exception could have
 *     been distinct classes in Java. This is not possible. User-defined
 *     exceptions extending different built-in exceptions allow class
 *     assignment even when they have distinct bases. It follows that
 *     all built-in exception types where this could happen must have
 *     the same representation class in Java.
 */
// Compare CPython PyBaseExceptionObject in pyerrors.c
public class PyBaseException extends RuntimeException
        implements WithClassAssignment, WithDict {
    private static final long serialVersionUID = 1L;

    /** The type object of Python {@code BaseException} exceptions. */
    public static final PyType TYPE = PyType.fromSpec(
            new TypeSpec("BaseException", MethodHandles.lookup())
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc("Common base class for all exceptions"));

    /** Python type of the exception. */
    private PyType type;

    // XXX dictionary required
    Map<Object, Object> dict;

    // XXX align constructor more directly to CPython.

    /**
     * The argument array given to the constructor, which is also the
     * arguments from {@code __new__} or {@code __init__}. Not
     * {@code null}.
     */
    /* XXX Should be tuple */
    private Object[] args;
    // private PyTuple args;

    private Object notes;
    private Object traceback;
    private Object context;
    private Object cause;
    private boolean suppressContext;

    // XXX current format and args could be applied to message.

    /**
     * Constructor for sub-class use specifying {@link #type}. The
     * message {@code msg} is a Java format string in which the
     * constructor arguments {@code vals} are used to fill the place
     * holders. The formatted message is the exception message from the
     * Java point of view.
     * <p>
     * From a Python perspective, the tuple ({@code exception.args}) has
     * one element, the formatted message, or zero elements if the
     * message is zero length.
     *
     * @param type actual Python type of this object
     * @param msg a Java format string for the message
     * @param vals to insert in the format string
     */
    protected PyBaseException(PyType type, String msg, Object... vals) {
        super(String.format(msg, vals));
        this.type = type;
        msg = this.getMessage();
        this.args = msg.length() > 0 ? new Object[] {msg} : EMPTY_ARRAY;
    }

    /** Constructor specifying Python argument array. */
    public PyBaseException(PyType type, Object[] args, String[] kwds) {
        super();
        // Ensure Python type is valid for Java class.
        this.type = TYPE; // For the check
        this.type = checkClassAssignment(type);
        // XXX Extract helper to support __init__
        // Fossilise the positional args
        if (args == null || args.length == 0) {
            this.args = EMPTY_ARRAY; // XXX PyTuple.EMPTY
        } else {
            if (kwds == null) { kwds = EMPTY_STRING_ARRAY; }
            this.args = Arrays.copyOfRange(args, 0,
                    args.length - kwds.length);
        }
    }

    @Override
    public PyType getType() { return type; }

    @Override
    public Map<Object, Object> getDict() {
        if (dict == null) { dict = new PyDict(); }
        return dict;
    }

    @Override
    public void setType(Object replacementType) {
        type = checkClassAssignment(replacementType);
    }

    /**
     * If the Python type of this exception is not the {@code wanted}
     * type, immediately re-throw it, with its original stack trace.
     * This may be used at the top of a catch clause to narrow the
     * caught exception almost as if it were a Java type.
     *
     * @param wanted type for which the method returns normally
     */
    public void only(PyType wanted) {
        // XXX Should be isinstance test, accepting sub-types
        if (type != wanted) { throw this; }
    }

    @Override
    public String toString() {
        String msg = args.length > 0 ? args[0].toString() : "";
        return String.format("%s: %s", type.getName(), msg);
    }

    // special methods ------------------------------------------------

    // Compare CPython PyBaseException_* in exceptions.c

    protected Object __repr__() {
        // Somewhat simplified
        return getType().getName() + "('" + getMessage() + "')";
    }

    // plumbing -------------------------------------------------------
}
