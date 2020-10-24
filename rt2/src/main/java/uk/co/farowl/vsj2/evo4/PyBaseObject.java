package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo4.Slot.Signature;

/**
 * The Python {@code object} object: all Python objects by default
 * inherit its Python method implementations. Although all Python
 * objects are sub-types in Python of {@code object}, their
 * implementation classes need not be sub-classes in Java of this one.
 * In particular, many built-in types are not, although they implement
 * {@link PyObject}.
 * <p>
 * The Java implementation class of a type defined in Python will be
 * derived from the implementation class of the "solid base" it inherits
 * in Python. This <i>may</i> be {@code object}, in which case the
 * implementation class will be s sub-class in Java of this class.
 * <p>
 * <b>Implementation note:</b> The {@code self} argument of slot
 * functions defined here should have type {@link PyObject} so that
 * method handles copied from the slots of {@code object} function
 * correctly in the type slots of receiving Python objects.
 */
class PyBaseObject extends AbstractPyObject {

    /** The type object of {@code object} objects. */
    static final PyType TYPE = PyType.OBJECT_TYPE;

    /** Constructor for Python sub-class specifying {@link #type}. */
    protected PyBaseObject(PyType type) {
        super(type);
    }

    /** Constructor {@code object}. */
    public PyBaseObject() {
        super(TYPE);
    }

    // slot functions -------------------------------------------------

    /*
     * The "self" argument of methods defined here should have type
     * PyObject so that method handles copied from the slots of "object"
     * function correctly in the type slots of Python objects.
     *
     * It follows that operations performed here must be feasible for
     * any Python object.
     */

    /**
     * {@link Slot#tp_repr} has signature {@link Signature#UNARY} and
     * sometimes reproduces the source-code representation of the
     * object.
     */
    static PyObject __repr__(PyObject self) {
        // XXX Ought to prefix with module from type and add id().
        return Py.str("<'" + self.getType().name + "' object>");
    }

    /**
     * {@link Slot#tp_str} has signature {@link Signature#UNARY} and
     * returns a human-readable presentation of the object. The default
     * definition of the {@code __str__} slot is to invoke the
     * {@code __repr__} slot.
     */
    static PyObject __str__(PyObject self) {
        MethodHandle repr = self.getType().tp_repr;
        // Be more bullet-proof than usual
        try {
            if (repr != null)
                return (PyObject) repr.invoke(self);
        } catch (Throwable e) {}
        // Fall back on a direct call
        return __repr__(self);
    }

    /**
     * {@link Slot#tp_getattribute} has signature
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
     * If a matching entry on the type is a data descriptor (case 1) ,
     * but throws {@link AttributeError}, the instance dictionary (if
     * any) will be consulted, and the subsequent cases (3 and 4)
     * skipped. A non-data descriptor that throws an
     * {@link AttributeError} (case 3) causes case 4 to be skipped.
     *
     * @param obj the target of the get
     * @param name of the attribute
     * @return attribute value
     * @throws AttributeError if no such attribute
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython PyObject_GenericGetAttr in object.c
    // Also _PyObject_GenericGetAttrWithDict without the tricks.
    static PyObject __getattribute__(PyObject obj, PyUnicode name)
            throws AttributeError, Throwable {

        PyType objType = obj.getType();
        MethodHandle descrGet = null;

        // Look up the name in the type (null if not found).
        PyObject typeAttr = objType.lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor
            PyType typeAttrType = typeAttr.getType();
            descrGet = typeAttrType.tp_descr_get;
            if (typeAttrType.isDataDescr()) {
                // typeAttr is a data descriptor so call its __get__.
                try {
                    return (PyObject) descrGet.invokeExact(typeAttr,
                            obj, objType);
                } catch (AttributeError | Slot.EmptyException e) {
                    /*
                     * Not found via descriptor: fall through to try the
                     * instance dictionary, but prevent trying the
                     * descriptor again.
                     */
                    descrGet = null;
                }
            }
        }

        /*
         * At this stage: typeAttr is the value from the type, or null
         * if we didn't get one, and descrGet is an untried non-data
         * descriptor, or null or empty if there wasn't one. It's time
         * to give the object instance dictionary a chance.
         */
        PyObject.Mapping dict = obj.getDict(false);
        PyObject instanceAttr;
        if (dict != null && (instanceAttr = dict.get(name)) != null) {
            // Found something
            return instanceAttr;
        }

        /*
         * The name wasn't in the instance dictionary (or there wasn't
         * an instance dictionary). We are now left with the results of
         * look-up on the type.
         */
        if (descrGet != null) {
            // typeAttr is a non-data descriptor so call its __get__.
            try {
                return (PyObject) descrGet.invokeExact(typeAttr, obj,
                        objType);
            } catch (AttributeError | Slot.EmptyException e) {
                // Not found via descriptor (or empty?)
            }
        } else if (typeAttr != null) {
            // The attribute obtained from the type is a plain value.
            return typeAttr;
        }

        // All the look-ups came and descriptors to nothing :(
        throw Abstract.noAttributeError(obj, name);
    }

    /**
     * {@link Slot#tp_setattr} has signature {@link Signature#SETATTR}
     * and provides attribute write access on the object. The default
     * instance {@code __setattr__} slot implements dictionary look-up
     * on the type and the instance. It is the starting point for
     * activating the descriptor protocol. The following order of
     * precedence applies when setting the value of an attribute:
     * <ol>
     * <li>call a data descriptor from the dictionary of the type</li>
     * <li>place a value in the instance dictionary of {@code obj}</li>
     * </ol>
     * If a matching entry on the type is a data descriptor (case 1) ,
     * but it throws {@link AttributeError}, this is definitive and the
     * instance dictionary (if any) will not be updated.
     *
     * @param obj the target of the set
     * @param name of the attribute
     * @param value to give the attribute
     * @throws AttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython PyObject_GenericSetAttr in object.c
    // Also _PyObject_GenericSetAttrWithDict without the extras.
    static void __setattr__(PyObject obj, PyUnicode name,
            PyObject value) throws AttributeError, Throwable {

        // Look up the name in the type (null if not found).
        PyObject typeAttr = obj.getType().lookup(name);
        if (typeAttr != null) {
            // Found in the type, it might be a descriptor.
            MethodHandle descrSet = typeAttr.getType().tp_descr_set;
            if (descrSet != EMPTY_SET) {
                /*
                 * Descriptor has a __set__: use that. The action may
                 * throw AttributeError
                 */
                descrSet.invokeExact(typeAttr, obj, value);
                return;
            }
        }

        /*
         * At this stage: typeAttr is the value from the type, or null
         * if we didn't get one, and descrGet is null or empty if there
         * wasn't one. It's time to give the object instance dictionary
         * a chance.
         */
        PyObject.Mapping dict = obj.getDict(true);
        if (dict == null) {
            // Object has no dictionary (and can't be given one)
            if (typeAttr == null) {
                throw Abstract.noAttributeError(obj, name);
            } else {
                // No dict and a descriptor but no __set__
                throw Abstract.readonlyAttributeError(obj, name);
            }
        } else if (value == null) {
            // There is a dictionary, and this is a delete.
            PyObject previous = dict.remove(name);
            if (previous == null) {
                // A null return implies it didn't exist
                throw Abstract.noAttributeError(obj, name);
            }
        } else {
            // There is a dictionary, and this is a put.
            // XXX dict might be read-only, e.g. obj is a type.
            dict.put(name, value);
        }
        return;
    }

    private static final MethodHandle EMPTY_SET =
            Slot.tp_descr_set.getEmpty();
}
