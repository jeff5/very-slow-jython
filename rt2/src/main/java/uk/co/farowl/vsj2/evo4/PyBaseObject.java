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
class PyBaseObject implements PyObject {

    /** The type object of {@code object} objects. */
    static final PyType TYPE = PyType.OBJECT_TYPE;

    protected final PyType type;

    /** Constructor for Python sub-class specifying {@link #type}. */
    protected PyBaseObject(PyType type) {
        this.type = type;
    }

    /** Constructor {@code object}. */
    public PyBaseObject() {
        this(TYPE);
    }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() { return Py.defaultToString(this); }

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
}
