package uk.co.farowl.vsj2.evo4;

import java.util.EnumSet;

abstract class DataDescriptor extends Descriptor {

    /** Acceptable values in the {@link #flags}. */
    enum Flag {
        READONLY, READ_RESTRICTED, WRITE_RESTRICTED
    }

    /**
     * Attributes controlling access and audit. (In CPython, the
     * RESTRICTED forms cause a call to {@code sys.audit} and are here
     * for compatibility with that eventual idea.)
     */
    final EnumSet<Flag> flags;

    DataDescriptor(PyType descrtype, PyType objclass, String name,
            EnumSet<Flag> flags) {
        super(descrtype, objclass, name);
        this.flags = flags;
    }

    /**
     * {@inheritDoc}
     *
     * @implNote Compare CPython {@code descr_check} in
     *           {@code descrobject.c}. We differ in that: (1) We throw
     *           directly on failure. (2) The condition
     *           {@code obj==null} (when found on a type) is the
     *           caller's job. (3) We fold the {@code sys.audit} call
     *           into this check.
     */
    @Override
    protected void check(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, name,
                    objclass.name, objType.name);
            /*
             * It is not sufficient to skip the test and catch the
             * class cast from VarHandle.get, because the wrong obj
             * class is not necessarily the wrong Java class.
             */
        } else if (flags.contains(Flag.READ_RESTRICTED)) {
            // Sys.audit("object.__getattr__", "Os",
            // obj != null ? obj : Py.None, name);
        }
    }

    /**
     * {@code descr.__set__(obj, value)} has been called on this
     * descriptor. We must check that the descriptor applies to the type
     * of object supplied as the {@code obj} argument. From Python,
     * anything could be presented, but when we operate on it, we'll be
     * assuming the particular {@link #objclass} type.
     *
     * @param obj target object (argument to {@code __set__})
     * @throws TypeError if descriptor doesn't apply to {@code obj}
     */
    // Compare CPython descr_setcheck in descrobject.c
    protected void checkSet(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, name,
                    objclass.name, objType.name);
        }
        if (!flags.isEmpty()) {
            if (flags.contains(Flag.READONLY)) {
                throw Abstract.readonlyAttributeOnType(objclass, name);
            } else if (flags.contains(Flag.WRITE_RESTRICTED)) {
                // Sys.audit("object.__setattr__", "Os",
                // obj != null ? obj : Py.None, name);
            }
        }
    }

    /**
     * {@code descr.__delete__(obj)} has been called on this descriptor.
     * We must check that the descriptor applies to the type of object
     * supplied as the {@code obj} argument. From Python, anything could
     * be presented, but when we operate on it, we'll be
     * assuming the particular {@link #objclass} type.
     */
    // Compare CPython descr_setcheck in descrobject.c
     protected void checkDelete(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, name,
                    objclass.name, objType.name);
        }
        if (!flags.isEmpty()) {
            if (flags.contains(Flag.READONLY)) {
                throw Abstract.mandatoryAttributeOnType(objclass, name);
            } else if (flags.contains(Flag.WRITE_RESTRICTED)) {
                // Sys.audit("object.__delattr__", "Os",
                // obj != null ? obj : Py.None, name);
            }
        }
    }

}
