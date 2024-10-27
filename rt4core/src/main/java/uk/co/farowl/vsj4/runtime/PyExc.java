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
     * {@code NameError} extends {@code Exception} and is implemented by
     * {@link PyNameError}.
     */
    public static PyType NameError = PyNameError.TYPE;

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
