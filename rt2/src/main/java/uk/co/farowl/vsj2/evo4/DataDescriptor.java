package uk.co.farowl.vsj2.evo4;

import java.util.EnumSet;

abstract class DataDescriptor extends Descriptor {

    /** Acceptable values in the {@link #flags}. */
    enum Flag {
        READONLY, OPTIONAL, READ_RESTRICTED, WRITE_RESTRICTED
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
             * It is not sufficient to skip the test and catch the class
             * cast from VarHandle.get, because the wrong obj class is
             * not necessarily the wrong Java class.
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
     * be presented, but when we operate on it, we'll be assuming the
     * particular {@link #objclass} type.
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
                throw Abstract.readonlyAttributeOnType(objclass, name);
            } else if (flags.contains(Flag.WRITE_RESTRICTED)) {
                // Sys.audit("object.__delattr__", "Os",
                // obj != null ? obj : Py.None, name);
            }
        }
    }

    /**
     * Create a {@link TypeError} with a message along the lines "cannot
     * delete attribute N from 'T' objects" involving the name N of this
     * attribute and the type T which is {@link Descriptor#objclass}
     * {@code value}, e.g. "cannot delete attribute <u>f_trace_lines</u>
     * from '<u>frame</u>' objects".
     *
     * @return exception to throw
     */
    protected TypeError cannotDeleteAttr() {
        String msg =
                "cannot delete attribute %.50s from '%.50s' objects";
        return new TypeError(msg, name, objclass.getName());
    }

    /**
     * Create a {@link TypeError} with a message along the lines "N must
     * be set to T, not a X object" involving the name N of this
     * attribute, any descriptive phrase T and the type X of
     * {@code value}, e.g. "<u>__dict__</u> must be set to <u>a
     * dictionary</u>, not a '<u>list</u>' object".
     *
     * @param value provided to set this attribute in some object
     * @param kind expected kind of thing
     * @return exception to throw
     */
    protected TypeError attrMustBeSet(PyObject value, String kind) {
        String msg = "%.50s must be set to %s, not a '%.50s' object";
        return new TypeError(msg, name, kind,
                value.getType().getName());
    }

}
