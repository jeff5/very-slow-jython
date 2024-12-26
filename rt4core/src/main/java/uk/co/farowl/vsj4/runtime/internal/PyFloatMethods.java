// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj4.runtime.Exposed;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyType;

/**
 * Additional method definitions for the Python {@code float} type.
 */
public class PyFloatMethods {

    /**
     * Create a new instance of Python {@code float}, or of a subclass.
     *
     * @param cls actual Python sub-class being created
     * @return newly-created object
     */
    @Exposed.PythonNewMethod
    public static Object __new__(PyType cls, double x) {
        /*
         * We normally arrive here from PyType.__call__, where this/self
         * is the the type we're asked to construct, and gets passed
         * here as the 'cls' argument.
         */
        if (cls == PyFloat.TYPE) {
            return Double.valueOf(x);
        } else {
            /*
             * We need an instance of a Python subclass C, which means
             * creating an instance of C's Java representation.
             */
            try {
                // Look up a constructor with the right parameters
                MethodHandle cons =
                        cls.constructor(PyType.class, double.class)
                                .constructorHandle();
                return cons.invokeExact(cls, x);
            } catch (PyBaseException e) {
                // Usually signals no matching constructor
                throw e;
            } catch (Throwable e) {
                // Failed while finding/invoking constructor
                PyBaseException err = PyErr.format(PyExc.TypeError,
                        CANNOT_CONSTRUCT_INSTANCE, cls.getName(),
                        PyFloat.TYPE.getName());
                err.initCause(e);
                throw err;
            }
        }
    }

    private static final String CANNOT_CONSTRUCT_INSTANCE =
            "Cannot construct instance of '%s' in %s.__new__ ";
}
