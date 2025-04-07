// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.cantSetAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.mandatoryAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.noAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.readonlyAttributeError;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.runtime.kernel.KernelTypeFlag;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod.Signature;
import uk.co.farowl.vsj4.support.internal.EmptyException;

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
public class PyObject {

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
        Representation rep = PyType.getRepresentation(self);
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
                MethodHandle cons = cls.constructor(T).handle();
                // cons should be reliably (T)O
                return cons.invokeExact(cls);
            } catch (Throwable e) {
                throw PyUtil.cannotConstructInstance(cls, PyObject.TYPE,
                        e);
            }
        }
    }

    /**
     * {@link SpecialMethod#op_getattribute} has signature
     * {@link Signature#GETATTR} and provides attribute read access on
     * the object and its type. The default instance
     * {@code __getattribute__} slot implements dictionary look-up on
     * the type and the instance. It is the starting point for
     * activating the descriptor protocol. The following order of
     * precedence applies when looking for the value of an attribute:
     * <ol>
     * <li>a data descriptor from the dictionary of the type</li>
     * <li>a value in the instance dictionary of {@code obj}</li>
     * <li>a non-data descriptor from dictionary of the type</li>
     * <li>a value from the dictionary of the type</li>
     * </ol>
     * If a matching entry on the type is a data descriptor (case 1),
     * but throws {@link PyAttributeError AttributeError}, the instance
     * dictionary (if any) will be consulted, and the subsequent cases
     * (3 and 4) skipped. A non-data descriptor that throws an
     * {@link PyAttributeError AttributeError} (case 3) causes case 4 to
     * be skipped.
     *
     * @param obj the target of the get
     * @param name of the attribute
     * @return attribute value
     * @throws PyAttributeError if no such attribute
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython PyObject_GenericGetAttr in object.c
    // Also _PyObject_GenericGetAttrWithDict without the tricks.
    static Object __getattribute__(Object obj, String name)
            throws PyAttributeError, Throwable {

        PyType objType = PyType.of(obj);
        MethodHandle descrGet = null;

        // Look up the name in the type (null if not found).
        Object typeAttr = objType.lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor
            Representation typeAttrRep =
                    PyType.getRepresentation(typeAttr);
            descrGet = typeAttrRep.op_get();
            if (typeAttrRep.isDataDescr(typeAttr)) {
                // typeAttr is a data descriptor so call its __get__.
                try {
                    return descrGet.invokeExact(typeAttr, obj, objType);
                } catch (EmptyException e) {
                    /*
                     * Only __set__ or __delete__ was defined. We do not
                     * catch PyAttributeError: it's definitive. Suppress
                     * trying __get__ again.
                     */
                    descrGet = null;
                }
            }
        }

        /*
         * At this stage: typeAttr is the value from the type, or a
         * non-data descriptor, or null if the attribute was not found.
         * It's time to give the object instance dictionary a chance.
         */
        if (obj instanceof WithDict d) {
            Object instanceAttr = d.getDict().get(name);
            if (instanceAttr != null) {
                // Found the answer in the instance dictionary.
                return instanceAttr;
            }
        }

        /*
         * The name wasn't in the instance dictionary (or there wasn't
         * an instance dictionary). typeAttr is the result of look-up on
         * the type: a value, a non-data descriptor, or null if the
         * attribute was not found.
         */
        if (descrGet != null) {
            // typeAttr may be a non-data descriptor: call __get__.
            try {
                return descrGet.invokeExact(typeAttr, obj, objType);
            } catch (EmptyException e) {}
        }

        if (typeAttr != null) {
            /*
             * The attribute obtained from the type, and that turned out
             * not to be a descriptor, is the return value.
             */
            return typeAttr;
        }

        // All the look-ups and descriptors came to nothing :(
        throw noAttributeError(obj, name);
    }

    /**
     * {@link SpecialMethod#op_setattr} has signature
     * {@link Signature#SETATTR} and provides attribute write access on
     * the object. The default instance {@code __setattr__} slot
     * implements dictionary look-up on the type and the instance. It is
     * the starting point for activating the descriptor protocol. The
     * following order of precedence applies when setting the value of
     * an attribute:
     * <ol>
     * <li>call a data descriptor from the dictionary of the type</li>
     * <li>place a value in the instance dictionary of {@code obj}</li>
     * </ol>
     * If a matching entry on the type is a data descriptor (case 1) ,
     * but it throws {@link PyAttributeError AttributeError}, this is
     * definitive and the instance dictionary (if any) will not be
     * updated.
     *
     * @param obj the target of the set
     * @param name of the attribute
     * @param value to give the attribute
     * @throws PyAttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython PyObject_GenericSetAttr in object.c
    // Also _PyObject_GenericSetAttrWithDict without the extras.
    static void __setattr__(Object obj, String name, Object value)
            throws PyAttributeError, Throwable {

        // Accommodate CPython idiom that set null means delete.
        if (value == null) {
            // Do this to help porting. Really this is an error.
            __delattr__(obj, name);
            return;
        }

        // Look up the name in the type (null if not found).
        Object typeAttr = PyType.of(obj).lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor.
            Representation typeAttrRep =
                    PyType.getRepresentation(typeAttr);
            // Try descriptor __set__
            try {
                typeAttrRep.op_set().invokeExact(typeAttr, obj, value);
                return;
            } catch (EmptyException e) {
                /*
                 * We do not catch PyAttributeError: it's definitive.
                 * __set__ was not defined, but typeAttr is still a data
                 * descriptor if (unusually) it has __delete__.
                 */
                if (typeAttrRep.hasFeature(typeAttr,
                        KernelTypeFlag.HAS_DELETE)) {
                    throw readonlyAttributeError(obj, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will place the value in
         * the object instance dictionary directly.
         */
        if (obj instanceof WithDict d) {
            try {
                // There is a dictionary, and this is a put.
                d.getDict().put(name, value);
            } catch (UnsupportedOperationException e) {
                // But the dictionary is unmodifiable
                throw cantSetAttributeError(obj);
            }
        } else {
            // Object has no dictionary (and won't support one).
            if (typeAttr == null) {
                // Neither had the type an entry for the name.
                throw noAttributeError(obj, name);
            } else {
                /*
                 * The type had either a value for the attribute or a
                 * non-data descriptor. Either way, it's read-only when
                 * accessed via the instance.
                 */
                throw readonlyAttributeError(obj, name);
            }
        }
    }

    /**
     * {@link SpecialMethod#op_delattr} has signature
     * {@link Signature#DELATTR} and provides attribute deletion on the
     * object. The default instance {@code __delattr__} slot implements
     * dictionary look-up on the type and the instance. It is the
     * starting point for activating the descriptor protocol. The
     * following order of precedence applies when deleting an attribute:
     * <ol>
     * <li>call a data descriptor from the dictionary of the type</li>
     * <li>remove an entry from the instance dictionary of
     * {@code obj}</li>
     * </ol>
     * If a matching entry on the type is a data descriptor (case 1) ,
     * but it throws {@link PyAttributeError AttributeError}, this is
     * definitive and the instance dictionary (if any) will not be
     * updated.
     *
     * @param obj the target of the delete
     * @param name of the attribute
     * @throws PyAttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython PyObject_GenericSetAttr in object.c
    static void __delattr__(Object obj, String name)
            throws PyAttributeError, Throwable {

        // Look up the name in the type (null if not found).
        Object typeAttr = PyType.of(obj).lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor.
            Representation typeAttrRep =
                    PyType.getRepresentation(typeAttr);
            // Try descriptor __delete__
            try {
                typeAttrRep.op_delete().invokeExact(typeAttr, obj);
                return;
            } catch (EmptyException e) {
                /*
                 * We do not catch PyAttributeError: it's definitive.
                 * __delete__ was not defined, but typeAttr is still a
                 * data descriptor if it has __set__.
                 */
                if (typeAttrRep.hasFeature(typeAttr,
                        KernelTypeFlag.HAS_SET)) {
                    throw mandatoryAttributeError(obj, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will remove the name from
         * the object instance dictionary directly.
         */
        if (obj instanceof WithDict d) {
            try {
                // There is a dictionary, and this is a delete.
                Object previous = d.getDict().remove(name);
                if (previous == null) {
                    // A null return implies it didn't exist
                    throw noAttributeError(obj, name);
                }
            } catch (UnsupportedOperationException e) {
                // But the dictionary is unmodifiable
                throw cantSetAttributeError(obj);
            }
        } else {
            // Object has no dictionary (and won't support one).
            if (typeAttr == null) {
                // Neither has the type an entry for the name.
                throw noAttributeError(obj, name);
            } else {
                /*
                 * The type had either a value for the attribute or a
                 * non-data descriptor. Either way, it's read-only when
                 * accessed via the instance.
                 */
                throw readonlyAttributeError(obj, name);
            }
        }
    }

}
