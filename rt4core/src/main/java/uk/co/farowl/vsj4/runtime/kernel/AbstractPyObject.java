// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.security.Signature;

import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUtil;
import uk.co.farowl.vsj4.support.MissingFeature;

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
     * {@link SpecialMethod#op_repr} has signature
     * {@link Signature#UNARY} and sometimes reproduces the source-code
     * representation of the object.
     *
     * @param self target of the operation
     * @return string form
     */
    // Compare CPython object_repr in typeobject.c
    static Object __repr__(Object self) {
        return "<" + PyUtil.toAt(self) + ">";
    }

    /**
     * Create a new Python {@code object}. {@code __new__} is a special
     * method, but not a slot (there is no {@code Slot.op_new} in this
     * implementation.
     *
     * @param type actual Python sub-class being created
     * @return newly-created implementation object
     */
    // @PythonNewMethod
    static Object __new__(PyType type) {
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
            throw new MissingFeature("type.createInstance()");
        }
    }

}
