package uk.co.farowl.vsj2.evo4;

/**
 * The base class of many built-in descriptors.
 */
abstract class Descriptor extends AbstractPyObject
        implements ClassShorthand {

    protected static final String DESCRIPTOR_DOESNT_APPLY =
            "descriptor '%s' for '%.100s' objects doesn't apply to a '%.100s' object";
    protected static final String DESCRIPTOR_NEEDS_ARGUMENT =
            "descriptor '%s' of '%.100s' object needs an argument";
    protected static final String DESCRIPTOR_REQUIRES =
            "descriptor '%s' requires a '%.100s' object but received a '%.100s'";

    /**
     * Python {@code type} that defines the attribute being described
     * (e.g. for a method, the Python type of the object that will be
     * "self" in a call). This is exposed to Python as
     * {@code __objclass__}.
     */
    // In CPython, called d_type
    protected final PyType objclass;

    /**
     * Name of the object described, e.g. "__add__" or "to_bytes". This
     * is exposed to Python as {@code __name__}.
     */
    // In CPython, called d_name
    protected final String name;

    /**
     * Qualified name of the object described, e.g. "float.__add__" or
     * "int.to_bytes". This is exposed to Python as
     * {@code __qualname__}.
     */
    // In CPython, called d_qualname. Where used? Better computed?
    protected String qualname = null;

    Descriptor(PyType descrtype, PyType objclass, String name) {
        super(descrtype);
        this.objclass = objclass;
        this.name = name;
    }

    // CPython method table (to convert to annotations):
    // private MethodDef descr_methods[] = {
    // {"__reduce__", (PyCFunction)descr_reduce, METH_NOARGS, null},
    // {null, null}
    // };

    // CPython member table (to convert to annotations):
    // private MemberDef descr_members[] = {
    // {"__objclass__", T_OBJECT, offsetof(PyDescr, objclass),
    // READONLY},
    // {"__name__", T_OBJECT, offsetof(PyDescr, d_name), READONLY},
    // {0}
    // };

    /**
     * Helper for {@code __repr__} implementation. The format will be
     * fed {@code this.name} and {@code this.objclass.name}.
     *
     * @param format with two {@code %s} fields
     * @return repr as a {@code str}
     */
    protected PyObject descr_repr(String format) {
        return PyUnicode.fromFormat(format, name, objclass.name);
    }

    /**
     * {@code descr.__get__(obj, type)} has been called on this
     * descriptor. We must check that the descriptor applies to the type
     * of object supplied as the {@code obj} argument. From Python,
     * anything could be presented, but when we operate on it, we'll be
     * assuming the particular {@link #objclass} type.
     *
     * @param obj target object (non-null argument to {@code __get__})
     * @throws TypeError if descriptor doesn't apply to {@code obj}
     */
    // Compare CPython descr_check in descrobject.c
    /*
     * We differ from CPython in that: 1. We either throw or return
     * void: there is no FALSE->error or descriptor. 2. The test
     * obj==null (implying found on a type) is the caller's job. 3. In a
     * data descriptor, we fold the auditing into this check.
     */
    protected void check(PyObject obj) throws TypeError {
        PyType objType = obj.getType();
        if (!objType.isSubTypeOf(objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, this.name,
                    objclass.name, objType.name);
        }
    }

    // Compare CPython calculate_qualname in descrobject.c
    private String calculate_qualname()
            throws AttributeError, Throwable {
        PyObject type_qualname =
                Abstract.getAttr(objclass, ID.__qualname__);
        if (type_qualname == null)
            return null;
        if (!(type_qualname.getType().isSubTypeOf(PyUnicode.TYPE))) {
            throw new TypeError(
                    "<descriptor>.__objclass__.__qualname__ is not a unicode object");
        }
        return String.format("%s.%s", type_qualname, name);
    }

    // Compare CPython descr_get_qualname in descrobject.c
    static PyObject descr_get_qualname(Descriptor descr, Object ignored)
            throws AttributeError, Throwable {
        if (descr.qualname == null)
            descr.qualname = descr.calculate_qualname();
        return Py.str(descr.qualname);
    }

    // Incompletely ported from CPython:
    // static PyObject descr_reduce(PyDescr descr, PyObject ignored)
    // {
    // return Py_BuildValue("N(OO)", _PyEval_GetBuiltinId(ID.getattr),
    // descr.objclass, PyDescr_NAME(descr));
    // }

}
