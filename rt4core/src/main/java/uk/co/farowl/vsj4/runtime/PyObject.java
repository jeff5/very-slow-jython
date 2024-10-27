// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.security.Signature;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyObject;
import uk.co.farowl.vsj4.support.MissingFeature;

/**
 * The Python {@code object} object: all Python objects by default
 * inherit its Python method implementations. The Python {@code object}
 * type is represented by {@code java.lang.Object} but it gets its
 * Python behaviour from methods defined here or in the superclass.
 * <p>
 * Although all Python objects are sub-classes in Python of
 * {@code object}, their Java representation classes will not generally
 * be sub-classes in Java of this one. In particular, many built-in
 * types are not.
 * <p>
 * The Java implementation class of a type defined in Python <i>will</i>
 * be derived from the canonical implementation class of the "solid
 * base" it inherits in Python. This <i>may</i> be {@code object}, in
 * which case the implementation class will be a sub-class in Java of
 * this class.
 *
 * @implNote All exposed methods, special methods and attribute get, set
 *     and delete methods defined here must be declared {@code static}
 *     in Java, with an explicit {@code Object self} argument.
 *     ({@code __new__} is excepted from this rule as it is
 *     {@code static} anyway.) This is so that methods defined here on
 *     {@code object} operate correctly on receiving Python objects
 *     whatever their Java class. Methods and fields must be package
 *     visible so that the type factory is able to form
 *     {@code MethodHandle}s to them using its default lookup object.
 */
// Compare CPython PyBaseObject_Type in typeobject.c
public class PyObject extends AbstractPyObject {

    /** The type object {@code object}. */
    public static final PyType TYPE =
            /*
             * This looks a bit weird, but we need to make sure PyType
             * gets initialised, and the whole type system behind it,
             * before the first type object becomes visible.
             */
            PyType.of(new Object());

    /** One cannot make instances of this class. */
    private PyObject() {}

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
        if (type == TYPE) {
            return new Object();
        } else {
            // TODO Support subclass constructor
            throw new MissingFeature("type.createInstance()");
        }
    }
}
