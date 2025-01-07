// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.internal;

import uk.co.farowl.vsj4.runtime.Exposed;
import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.MissingFeature;

/**
 * Additional method definitions for the Python {@code int} type.
 */
public class PyLongMethods {

    /**
     * Create a new instance of Python {@code float}, or of a subclass.
     *
     * @param type actual Python sub-class being created
     * @return newly-created implementation object
     */
    @Exposed.PythonNewMethod
    public static Object __new__(PyType type) {
        /*
         * We normally arrive here from PyType.__call__, where this/self
         * is the the type we're asked to construct, and gets passed
         * here as the 'type' argument. __call__ will have received the
         * arguments matching the canonical signature, that is, self,
         * args, kwnames. The descriptor could match argument to this
         * __new__,
         */
        if (type == PyObject.TYPE) {
            return new Object();
        } else {
            // TODO Support subclass constructor
            throw new MissingFeature("subclasses in __new__");
        }
    }

}
