// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyObject;

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
     * For technical reasons to do with bootstrapping the type system,
     * the methods and attributes of 'object' that are exposed to Python
     * have to be defined in the superclass.
     */
}
