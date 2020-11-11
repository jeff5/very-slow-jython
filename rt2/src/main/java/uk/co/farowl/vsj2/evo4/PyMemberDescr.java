package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;

/**
 * Descriptor for an attribute that has been defined (by a
 * {@link Member} annotation) to get and optionally set the value (with
 * default type conversions).
 */
abstract class PyMemberDescr extends DataDescriptor {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("member_descriptor", PyMemberDescr.class));

    /** Reference to the field (offset) to access. */
    // CPython PyMemberDef: int type; int offset;
    VarHandle handle;

    /** Documentation string for the member (or {@code null}). */
    String doc;

    /**
     * Construct a MemberDef from a client-supplied handle. This allows
     * all JVM-supported access modes, but you have to make your own
     * handle.
     *
     * @param name by which the member is known to Python
     * @param handle to the Java member
     * @param flags
     * @param doc
     */
    PyMemberDescr(PyType objclass, String name, VarHandle handle,
            EnumSet<Flag> flags, String doc) {
        super(TYPE, objclass, name, flags);
        this.handle = handle;
        // Allow null to represent empty doc
        this.doc = doc != null && doc.length() > 0 ? doc : null;
    }

    /** If the given name is blank, use the one from the field. */
    private static String decideName(String name, Field f) {
        if (name == null || name.length() == 0)
            return f.getName();
        else
            return name;
    }

    private static VarHandle varHandle(Field f, Lookup lookup) {
        try {
            return lookup.unreflectVarHandle(f);
        } catch (IllegalAccessException e) {
            throw new InterpreterError(e,
                    "cannot get method handle for '%s'", f);
        }

    }

    /** If the field is {@code final}, <i>modify</i> to add READONLY. */
    private static EnumSet<Flag> decideFlags(EnumSet<Flag> flags,
            Field f) {
        if (flags == null) { flags = EnumSet.noneOf(Flag.class); }
        if ((f.getModifiers() & Modifier.FINAL) != 0)
            flags.add(Flag.READONLY);
        return flags;
    }

    /**
     * A method to get {@code o.name}. If the variable type of the
     * handle does not implement {@link PyObject} a conversion will be
     * applied to the value returned.
     */
    // Compare CPython PyMember_GetOne in structmember.c
    abstract PyObject get(PyObject o);

    /**
     * A method to set {@code o.name = v}. If the variable type of the
     * handle does not implement {@link PyObject} a conversion will be
     * applied to the value provided.
     *
     * @throws TypeError
     * @throws Throwable
     */
    // Compare CPython PyMember_SetOne in structmember.c
    abstract void set(PyObject o, PyObject v)
            throws TypeError, Throwable;

    // CPython get-set table (to convert to annotations):
    // private GetSetDef member_getset[] = {
    // {"__doc__", (getter)member_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {0}
    // };

    // CPython type object (to convert to special method names):
    // PyType PyMemberDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "member_descriptor",
    // sizeof(PyMemberDescr),
    // 0,
    // (destructor)descr_dealloc, /* tp_dealloc */
    // 0, /* tp_vectorcall_offset */
    // 0, /* tp_getattr */
    // 0, /* tp_setattr */
    // 0, /* tp_as_async */
    // (reprfunc)member_repr, /* tp_repr */
    // 0, /* tp_as_number */
    // 0, /* tp_as_sequence */
    // 0, /* tp_as_mapping */
    // 0, /* tp_hash */
    // 0, /* tp_call */
    // 0, /* tp_str */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // 0, /* tp_setattro */
    // 0, /* tp_as_buffer */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, /* tp_flags */
    // 0, /* tp_doc */
    // descr_traverse, /* tp_traverse */
    // 0, /* tp_clear */
    // 0, /* tp_richcompare */
    // 0, /* tp_weaklistoffset */
    // 0, /* tp_iter */
    // 0, /* tp_iternext */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // member_getset, /* tp_getset */
    // 0, /* tp_base */
    // 0, /* tp_dict */
    // (descrgetfunc)member_get, /* tp_descr_get */
    // (descrsetfunc)member_set, /* tp_descr_set */
    // };

    // Compare CPython member_repr in descrobject.c
    static PyObject __repr__(PyMemberDescr descr) {
        return descr.descr_repr("<member '%s' of '%s' objects>");
    }

    // Compare CPython member_get in descrobject.c
    static PyObject __get__(PyMemberDescr descr, PyObject obj,
            PyType type) {
        if (obj == null)
            /*
             * null 2nd argument to __get__ indicates the descriptor was
             * found on the target object itself (or a base), see
             * CPython type_getattro in typeobject.c
             */
            return descr;
        else {
            descr.check(obj);
            return descr.get(obj);
        }
    }

    // Compare CPython member_set in descrobject.c
    static void __set__(PyMemberDescr descr, PyObject obj,
            PyObject value) throws TypeError, Throwable {
        descr.checkSet(obj);
        descr.set(obj, value);
    }

    // Compare CPython member_get_doc in descrobject.c
    static PyObject member_get_doc(PyMemberDescr descr) {
        if (descr.doc == null) { return Py.None; }
        return Py.str(descr.doc);
    }

    /**
     * Create a {@code MethodDef} with behaviour specific to the class
     * of object being exposed.
     *
     * @param objclass Python type that owns the descriptor
     * @param name by which member known externally
     * @param field field to expose through this descriptor
     * @param lookup authorisation to access fields
     * @param flags supplying additional characteristics
     * @param doc documentation string (may be {@code null})
     * @return descriptor for access to the field
     * @throws InterpreterError if the field type is not supported
     */
    static PyMemberDescr forField(PyType objclass, String name,
            Field field, Lookup lookup, EnumSet<Flag> flags, String doc)
            throws InterpreterError {
        Class<?> fieldType = field.getType();
        name = decideName(name, field);
        VarHandle handle = varHandle(field, lookup);
        flags = decideFlags(flags, field);
        if (fieldType == int.class)
            return new _int(objclass, name, handle, flags, doc);
        else if (fieldType == double.class)
            return new _double(objclass, name, handle, flags, doc);
        else if (fieldType == String.class)
            return new _String(objclass, name, handle, flags, doc);
        else if (fieldType == String.class)
            return new _String(objclass, name, handle, flags, doc);
        else {
            throw new InterpreterError(UNSUPPORTED_TYPE, name,
                    fieldType.getSimpleName());
        }
    }

    protected static final String UNSUPPORTED_TYPE =
            "@Member target %.50s has unsupported type %.50s";

    private static class _int extends PyMemberDescr {

        _int(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);

        }

        @Override
        PyObject get(PyObject obj) {
            int value = (int) handle.get(obj);
            return Py.val(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            int v = Number.asSize(value, null);
            handle.set(obj, v);
        }
    }

    private static class _double extends PyMemberDescr {

        _double(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);
        }

        @Override
        PyObject get(PyObject obj) {
            double value = (double) handle.get(obj);
            return Py.val(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            double v = Number.toFloat(value).doubleValue();
            handle.set(obj, v);
        }
    }

    private static class _String extends PyMemberDescr {

        _String(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);
        }

        @Override
        PyObject get(PyObject obj) {
            String value = (String) handle.get(obj);
            return Py.str(value);
        }

        @Override
        void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            String v = value.toString();
            handle.set(obj, v);
        }
    }

}