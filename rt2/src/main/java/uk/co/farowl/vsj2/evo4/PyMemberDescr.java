package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.EnumSet;

/**
 * Descriptor for an attribute that has been defined (by a
 * {@code @Member} annotations) to get and optionally set or delete the
 * value, with default type conversions.
 */
abstract class PyMemberDescr extends DataDescriptor {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("member_descriptor", PyMemberDescr.class)
                    .flagNot(PyType.Flag.BASETYPE));

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
     * A method to get {@code o.name}, with conversion from the internal
     * field value if necessary (which will always succeed). This method
     * is called from {@link #__get__(PyMemberDescr, PyObject, PyType)},
     * after checks, to implement the type-specific conversion.
     *
     * @param obj object to access via {@link #handle} (never null)
     * @return field value
     */
    // Compare CPython PyMember_GetOne in structmember.c
    protected abstract PyObject get(PyObject obj) throws AttributeError;

    /**
     * A method to set {@code o.name = v}, with conversion to the
     * internal field value if necessary. This method is called from
     * {@link #__set__(PyMemberDescr, PyObject, PyObject)}, after
     * checks, to implement the type-specific conversion.
     *
     * @param obj object to access via {@link #handle} (never null)
     * @param v value to assign: never null, may be {@code None}
     * @throws TypeError if v cannot be converted
     * @throws Throwable potentially from conversion
     */
    // Compare CPython PyMember_SetOne in structmember.c
    protected abstract void set(PyObject obj, PyObject v)
            throws AttributeError, TypeError, Throwable;

    /**
     * A method to delete {@code del o.name}. This method is called from
     * {@link #__delete__(PyMemberDescr, PyObject)}, after checks, to
     * implement the type-specific conversion.
     *
     * @implNote The default implementation is correct for primitive
     *           types (i.e. the majority) in raising {@link TypeError}
     *           with the message that the attribute cannot be deleted.
     * @param obj object to access via {@link #handle} (never null)
     * @throws TypeError when not a type that can be deleted
     * @throws AttributeError when already deleted/undefined
     */
    // Compare CPython PyMember_SetOne in structmember.c with NULL
    protected void delete(PyObject obj)
            throws TypeError, AttributeError {
        throw cannotDeleteAttr();
    }

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
        return descr.descrRepr("member");
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
        if (value == null) {
            // This ought to be an error, but allow for CPython idiom.
            __delete__(descr, obj);
        } else {
            descr.checkSet(obj);
            descr.set(obj, value);
        }
    }

    // Compare CPython member_set in descrobject.c with NULL
    static void __delete__(PyMemberDescr descr, PyObject obj)
            throws TypeError, Throwable {
        descr.checkDelete(obj);
        descr.delete(obj);
    }

    // XXX GetSetDef in CPython, but @Member appropriate in our case
    // Compare CPython member_get_doc in descrobject.c
    static PyObject member_get_doc(PyMemberDescr descr) {
        if (descr.doc == null) { return Py.None; }
        return Py.str(descr.doc);
    }

    /**
     * Create a {@code PyMemberDescr} with behaviour specific to the
     * class of object being exposed.
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
        VarHandle vh = varHandle(field, lookup);
        // Note remove to minimise work in checkSet/checkDelete
        boolean opt = flags.remove(Flag.OPTIONAL);
        // This also treats Java final as READONLY (adds the flag)
        flags = decideFlags(flags, field);
        if (fieldType == int.class)
            return new _int(objclass, name, vh, flags, doc);
        else if (fieldType == double.class)
            return new _double(objclass, name, vh, flags, doc);
        else if (fieldType == String.class) {
            return new _String(objclass, name, vh, flags, doc, opt);
        } else {
            throw new InterpreterError(UNSUPPORTED_TYPE, name,
                    fieldType.getSimpleName());
        }
    }

    private static final String UNSUPPORTED_TYPE =
            "@Member target %.50s has unsupported type %.50s";

    private static class _int extends PyMemberDescr {

        _int(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);

        }

        @Override
        protected PyObject get(PyObject obj) {
            int value = (int) handle.get(obj);
            return Py.val(value);
        }

        @Override
        protected void set(PyObject obj, PyObject value)
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
        protected PyObject get(PyObject obj) {
            double value = (double) handle.get(obj);
            return Py.val(value);
        }

        @Override
        protected void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            double v = Number.toFloat(value).doubleValue();
            handle.set(obj, v);
        }
    }

    /** Behaviour for reference types. */
    private static abstract class Reference extends PyMemberDescr {

        /**
         * Controls what happens when the attribute implementation is
         * {@code null}, If {@code true}, {@link #get(PyObject)} will
         * raise {@link AttributeError}. If {@code false},
         * {@link #get(PyObject)} will return {@code None}.
         *
         * Delete sets the attribute implementation to {@code null}.
         */
        protected final boolean optional;

        Reference(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc, boolean optional) {
            super(objclass, name, handle, flags, doc);
            this.optional = optional;
        }

        /**
         * {@inheritDoc}
         * <p>
         * If {@link #optional} and the attribute is {@code null},
         * reference types raise an {@link AttributeError}.
         */
        @Override
        protected void delete(PyObject obj) {
            if (optional && handle.get(obj) == null)
                throw Abstract.noAttributeOnType(objclass, name);
            handle.set(obj, null);
        }
    }

    /**
     * A string attribute that may be deleted (represented by
     * {@code null} in Java).
     */
    private static class _String extends Reference {

        _String(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc, boolean optional) {
            super(objclass, name, handle, flags, doc, optional);
        }

        @Override
        protected PyObject get(PyObject obj) {
            String value = (String) handle.get(obj);
            if (value == null) {
                if (optional)
                    throw Abstract.noAttributeOnType(objclass, name);
                else
                    return Py.None;
            }
            return Py.str(value);
        }

        @Override
        protected void set(PyObject obj, PyObject value)
                throws TypeError, Throwable {
            if (!Abstract.typeCheck(value, PyUnicode.TYPE))
                throw attrMustBe("a string", value);
            String v = value.toString();
            handle.set(obj, v);
        }

    }

}
