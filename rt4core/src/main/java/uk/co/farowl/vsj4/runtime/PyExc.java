// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

/**
 * Python type objects for exceptions. The built-in Python exception
 * types form an extensive hierarchy, but are represented in Java by a
 * much smaller set of of Java exception classes that all extend
 * {@link PyBaseException}. Multiple built-in Python exceptions (and
 * user-defined exceptions derived from them) may share a given
 * representation in Java.
 * <p>
 * When it becomes necessary to create (or raise) an exception
 * code will refer to one of the type objects here.
 * <p>
 * In CPython, the object implementation of many Python exception types
 * is shared with multiple others. This allows multiple inheritance and
 * class assignment amongst user-defined exceptions, with diverse
 * built-in bases, in ways that may be surprising. The following is
 * valid in Python: <pre>
 * class TE(TypeError): __slots__=()
 * class FPE(FloatingPointError): __slots__=()
 * TE().__class__ = FPE
 * class E(ZeroDivisionError, TypeError): __slots__=()
 * E().__class__ = FPE
 * </pre>In order to meet user expectations set by CPython, the Java
 * representation of many Python exception types is shared. For example
 * {@code TypeError}, {@code FloatingPointError} and
 * {@code ZeroDivisionError} must share a representation (that of
 * {@code BaseException}, in fact). Since they are not different classes,
 * we cannot use a Java {@code catch} clause to select them.
 * <p>
 * CPython prohibits class-assignment involving built-in types directly.
 * For example {@code FloatingPointError().__class__ = E} and its
 * converse are not allowed. There seems to be no structural reason to
 * prohibit it, but we should do so for compatibility.
 */
// Compare CPython exceptions.c
/*
 * We choose the class name, and type object names somewhat against
 * convention, so that a reference to the type object looks like its
 * name in the CPython codebase. E.g. PyExc.TypeError looks like
 * PyExc_TypeError.
 */
public class PyExc {
    private PyExc() {}

    /** Allow the type system package access. */
    // XXX could this be null since no new attributes?
    private static final MethodHandles.Lookup LOOKUP = MethodHandles
            .lookup().dropLookupMode(MethodHandles.Lookup.PRIVATE);

    /**
     * Create a type object for a built-in exception that extends a
     * single base, with the addition of no fields or methods, and
     * therefore has the same Java representation as its base.
     *
     * @param excbase the base (parent) exception
     * @param excname the name of the new exception
     * @param excdoc a documentation string for the new exception type
     * @return the type object for the new exception type
     */
    // Compare CPython SimpleExtendsException in exceptions.c
    // ... or (same to us) MiddlingExtendsException
    private static PyType extendsException(PyType excbase,
            String excname, String excdoc) {
        TypeSpec spec = new TypeSpec(excname, LOOKUP).base(excbase)
                // Share the same Java representation class as base
                .primary(excbase.javaClass())
                // This will be a replaceable type.
                .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                .doc(excdoc);
        return PyType.fromSpec(spec);
    }

    /**
     * {@code BaseException} is the base type in Python of all
     * exceptions and is implemented by {@link PyBaseException}.
     */
    public static PyType BaseException = PyBaseException.TYPE;
    /** Exception extends {@link PyBaseException}. */
    public static PyType Exception =
            extendsException(BaseException, "Exception",
                    "Common base class for all non-exit exceptions.");
    /** {@code TypeError} extends {@link Exception}. */
    public static PyType TypeError = extendsException(Exception,
            "TypeError", "Inappropriate argument type.");

    /**
     * {@code StopIteration} extends {@code Exception} and is implemented by
     * {@link PyStopIteration}.
     */
    public static PyType StopIteration = PyStopIteration.TYPE;

    /**
     * {@code NameError} extends {@code Exception} and is implemented by
     * {@link PyNameError}.
     */
    public static PyType NameError = PyNameError.TYPE;

    /** {@code LookupError} extends {@link Exception}. */
    public static PyType UnboundLocalError =
            extendsException(NameError, "UnboundLocalError",
                    "Local name referenced but not bound to a value.");

    /**
     * {@code AttributeError} extends {@code Exception} and is
     * implemented by {@link PyAttributeError}.
     */
    public static PyType AttributeError = PyAttributeError.TYPE;

    /** {@code LookupError} extends {@link Exception}. */
    public static PyType LookupError = extendsException(Exception,
            "LookupError", "Base class for lookup errors.");
    /** {@code IndexError} extends {@link LookupError}. */
    public static PyType IndexError = extendsException(LookupError,
            "IndexError", "Sequence index out of range.");
    /** {@code ValueError} extends {@link Exception}. */
    public static PyType ValueError =
            extendsException(Exception, "ValueError",
                    "Inappropriate argument value (of correct type).");

    /** {@code ArithmeticError} extends {@link Exception}. */
    public static PyType ArithmeticError = extendsException(Exception,
            "ArithmeticError", "Base class for arithmetic errors.");
    /** {@code FloatingPointError} extends {@link ArithmeticError}. */
    public static PyType FloatingPointError =
            extendsException(ArithmeticError, "FloatingPointError",
                    "Floating point operation failed.");
    /** {@code OverflowError} extends {@link ArithmeticError}. */
    public static PyType OverflowError =
            extendsException(ArithmeticError, "OverflowError",
                    "Result too large to be represented.");
    /** {@code ZeroDivisionError} extends {@link ArithmeticError}. */
    public static PyType ZeroDivisionError = extendsException(
            ArithmeticError, "ZeroDivisionError",
            "Second argument to a division or modulo operation was zero.");

    /*
     * Warnings are Exception objects, but do not get thrown (I think),
     * being used as "categories" in the warnings module.
     */

    /** {@code Warning} extends {@link Exception}. */
    public static PyType Warning = extendsException(Exception,
            "Warning", "Base class for warning categories.");
    /** {@code DeprecationWarning} extends {@link Warning}. */
    public static PyType DeprecationWarning = extendsException(Warning,
            "DeprecationWarning",
            "Base class for warnings about deprecated features.");
    /** {@code RuntimeWarning} extends {@link Warning}. */
    public static PyType RuntimeWarning = extendsException(Warning,
            "RuntimeWarning",
            "Base class for warnings about dubious runtime behavior.");
}
