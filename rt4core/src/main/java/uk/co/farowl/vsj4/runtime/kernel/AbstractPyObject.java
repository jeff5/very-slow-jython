// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import uk.co.farowl.vsj4.runtime.Exposed;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * The Python {@code object} is represented by {@code java.lang.Object}
 * but its Python behaviour is implemented by {@link PyObject}, which
 * extends this class. This class provides members used internally in
 * the run-time system, but that we do not intend to expose as API from
 * {@link PyObject} itself.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public abstract class AbstractPyObject {

    /** Lookup object with package visibility. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    // Special methods ------------------------------------------------

    /*
     * Methods must be static with a "self" argument of type Object so
     * that method handles copied from the slots of "object" function
     * correctly in the type slots of Python objects.
     *
     * It follows that operations performed here must be feasible for
     * any Python object.
     */

    /**
     * Called by the {@code repr()} built-in function to compute the
     * "official" string representation of an object.
     *
     * @param self target of the operation
     * @return string form
     */
    // Compare CPython object_repr in typeobject.c
    static Object __repr__(Object self) {
        return "<" + _PyUtil.toAt(self) + ">";
    }

    /**
     * Called by {@code str()} and the built-in functions
     * {@code format()} and {@code print()} to compute the "informal" or
     * nicely printable string representation of an object.
     * {@code object.__str__}, i.e. the behaviour is the subclass does
     * not define {@code __str__}, calls the {@code __repr__} of the
     * object.
     *
     * @param self target of the operation
     * @return string form
     * @throws Throwable
     */
    // Compare CPython object_str in typeobject.c
    static Object __str__(Object self) throws Throwable {
        Representation rep = SimpleType.getRepresentation(self);
        try {
            return rep.op_repr().invokeExact(self);
        } catch (EmptyException ee) {
            return __repr__(self);
        }
    }

    /**
     * Create a new Python {@code object}. {@code __new__} is a special
     * method in the data model, but not a {@link SpecialMethod} (there
     * is no {@code SpecialMethod.op_new}) in this implementation.
     *
     * @param cls actual Python sub-class being created
     * @return newly-created object
     */
    @Exposed.PythonNewMethod
    static Object __new__(PyType cls) {
        /*
         * We normally arrive here from PyType.__call__, where this/self
         * is the the type we're asked to construct, and gets passed
         * here as the 'cls' argument.
         */
        if (cls == PyObject.TYPE) {
            return new Object();
        } else {
            /*
             * We need an instance of a Python subclass C, which means
             * creating an instance of C's Java representation.
             */
            try {
                // Look up a constructor with the right parameters
                MethodHandle cons = cls.constructor(PyType.class)
                        .constructorHandle();
                // cons should be reliably (T)O
                return cons.invokeExact(cls);
            } catch (PyBaseException e) {
                // Usually signals no matching constructor
                throw e;
            } catch (Throwable e) {
                // Failed while finding/invoking constructor
                PyBaseException err = PyErr.format(PyExc.TypeError,
                        CANNOT_CONSTRUCT_INSTANCE, cls.getName(),
                        PyObject.TYPE.getName());
                err.initCause(e);
                throw err;
            }
        }
    }

    // plumbing -------------------------------------------------------

    /**
     * Signature of {@link #PyBaseException} constructor. We have to
     * assume this signature may be used to construct an instance of the
     * Java representation class of a subclass if it has no
     * {@code __new__}.
     */
    private static final Class<?>[] CONSTRUCTOR_ARGS = {PyType.class};
    private static final String CANNOT_CONSTRUCT_INSTANCE =
            "Cannot construct instance of '%s' in %s.__new__ ";
}
