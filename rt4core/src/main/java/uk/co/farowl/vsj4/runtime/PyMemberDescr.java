// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.lang.reflect.Field;
import java.util.EnumSet;

import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Descriptor for an attribute that has been defined (by a
 * {@code @Member} annotations) to get and optionally set or delete the
 * value, with default type conversions.
 */
public abstract class PyMemberDescr extends DataDescriptor {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("member_descriptor", MethodHandles.lookup())
                    .remove(Feature.BASETYPE));

    /** Acceptable values in the {@link #flags}. */
    enum Flag {
        READONLY, OPTIONAL, READ_RESTRICTED, WRITE_RESTRICTED
    }

    /**
     * Attributes controlling access and audit. (In CPython, the
     * RESTRICTED forms cause a call to {@code sys.audit} and are here
     * for compatibility with that eventual idea.) Note that
     * {@code OPTIONAL}, although in the API, is represented as a
     * sub-class attribute, to minimise work in
     * {@link #checkSet(Object)} and {@link #checkDelete(Object)}
     */
    protected final EnumSet<Flag> flags;

    /** Reference to the field (offset) to access. */
    // Compare CPython PyMemberDef: int type; int offset;
    protected VarHandle field;

    /** Documentation string for the member (or {@code null}). */
    String doc;

    /**
     * Construct a {@code PyMemberDescr} from a client-supplied handle.
     * This allows all JVM-supported access modes, but you have to make
     * your own handle.
     *
     * @param objclass Python type containing this member
     * @param name by which the member is known to Python
     * @param handle to the Java member
     * @param flags characteristics controlling access
     * @param doc documentation string
     */
    PyMemberDescr(PyType objclass, String name, VarHandle handle,
            EnumSet<Flag> flags, String doc) {
        super(TYPE, objclass, name);
        this.flags = flags;
        this.field = handle;
        // Allow null to represent empty doc
        this.doc = doc != null && doc.length() > 0 ? doc : null;
    }

    private static VarHandle varHandle(Field f, Lookup lookup) {
        try {
            return lookup.unreflectVarHandle(f);
        } catch (IllegalAccessException e) {
            throw new InterpreterError(e, "cannot get handle for '%s'",
                    f);
        }

    }

    /**
     * {@inheritDoc}
     *
     * @implNote Compare CPython {@code descr_check} in
     *     {@code descrobject.c}. We differ in that: (1) We throw
     *     directly on failure. (2) The condition {@code obj==null}
     *     (when found on a type) is the caller's job. (3) We fold the
     *     {@code sys.audit} call into this check.
     */
    @Override
    protected void check(Object obj) throws PyBaseException {
        PyType objType = PyType.of(obj);
        if (!objType.isSubTypeOf(objclass)) {
            throw selfTypeError(objType);
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
     * @throws PyBaseException (TypeError) if descriptor doesn't apply
     *     to {@code obj}
     */
    // Compare CPython descr_setcheck in descrobject.c
    @Override
    protected void checkSet(Object obj) throws PyBaseException {
        PyType objType = PyType.of(obj);
        if (!objType.isSubTypeOf(objclass)) {
            throw selfTypeError(objType);
        }
        if (!flags.isEmpty()) {
            if (flags.contains(Flag.READONLY)) {
                throw _PyUtil.readonlyAttributeOnType(objclass, name);
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
    @Override
    protected void checkDelete(Object obj) throws PyBaseException {
        PyType objType = PyType.of(obj);
        if (!objType.isSubTypeOf(objclass)) {
            throw selfTypeError(objType);
        }
        if (!flags.isEmpty()) {
            if (flags.contains(Flag.READONLY)) {
                throw _PyUtil.readonlyAttributeOnType(objclass, name);
            } else if (flags.contains(Flag.WRITE_RESTRICTED)) {
                // Sys.audit("object.__delattr__", "Os",
                // obj != null ? obj : Py.None, name);
            }
        }
    }

    /**
     * A method to get {@code o.name}, with conversion from the internal
     * field value if necessary (which will always succeed). This method
     * is called from {@link #__get__(Object, PyType)}, after checks, to
     * implement the type-specific conversion.
     *
     * @param obj object to access via {@link #field} (never null)
     * @return field value
     */
    // Compare CPython PyMember_GetOne in structmember.c
    protected abstract Object get(Object obj) throws PyAttributeError;

    /**
     * A method to set {@code o.name = v}, with conversion to the
     * internal field value if necessary. This method is called from
     * {@link #__set__(Object, Object)}, after checks, to implement the
     * type-specific conversion.
     *
     * @param obj object to access via {@link #field} (never null)
     * @param v value to assign: never null, may be {@code None}
     * @throws PyBaseException (TypeError) if v cannot be converted
     * @throws Throwable potentially from conversion
     */
    // Compare CPython PyMember_SetOne in structmember.c
    protected abstract void set(Object obj, Object v)
            throws PyAttributeError, PyBaseException, Throwable;

    /**
     * A method to delete {@code del o.name}. This method is called from
     * {@link #__delete__(Object)}, after checks, to implement the
     * type-specific delete.
     *
     * @implNote The default implementation is correct for primitive
     *     types (i.e. the majority) in raising {@link PyBaseException
     *     TypeError} with the message that the attribute cannot be
     *     deleted.
     * @param obj object to access via {@link #field} (never null)
     * @throws PyBaseException (TypeError) when not a type that can be
     *     deleted
     * @throws PyAttributeError when already deleted/undefined
     */
    // Compare CPython PyMember_SetOne in structmember.c with NULL
    protected void delete(Object obj)
            throws PyBaseException, PyAttributeError {
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
    @SuppressWarnings("unused")
    private Object __repr__() { return descrRepr("member"); }

    /**
     * {@inheritDoc}
     *
     * If {@code obj != null} call {@link #get} on it to return a value.
     * {@code obj} must be of type {@link #objclass}. A call made with
     * {@code obj == null} returns {@code this} descriptor.
     *
     * @param type is ignored
     */
    @Override
    // Compare CPython member_get in descrobject.c
    Object __get__(Object obj, PyType type) {
        if (obj == null)
            /*
             * obj==null indicates the descriptor was found on the
             * target object itself (or a base), see CPython
             * type_getattro in typeobject.c
             */
            return this;
        else {
            check(obj);
            return get(obj);
        }
    }

    // Compare CPython member_set in descrobject.c
    @Override
    void __set__(Object obj, Object value)
            throws PyBaseException, Throwable {
        if (value == null) {
            // This ought to be an error, but allow for CPython idiom.
            __delete__(obj);
        } else {
            checkSet(obj);
            set(obj, value);
        }
    }

    // Compare CPython member_set in descrobject.c with NULL
    @Override
    void __delete__(Object obj) throws PyBaseException, Throwable {
        checkDelete(obj);
        delete(obj);
    }

    // XXX GetSetDef in CPython, but @Member appropriate in our case
    // Compare CPython member_get_doc in descrobject.c
    static Object member_get_doc(PyMemberDescr descr) {
        if (descr.doc == null) { return Py.None; }
        return descr.doc;
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
        VarHandle vh = varHandle(field, lookup);
        // Note remove to minimise work in checkSet/checkDelete
        boolean opt = flags.remove(Flag.OPTIONAL);
        if (fieldType == int.class)
            return new _int(objclass, name, vh, flags, doc);
        else if (fieldType == double.class)
            return new _double(objclass, name, vh, flags, doc);
        else if (fieldType == String.class)
            return new _String(objclass, name, vh, flags, doc, opt);
        else if (fieldType.isPrimitive() || fieldType.isArray())
            // Identifiable exclusions as or-ed above. Enough?
            throw new InterpreterError(UNSUPPORTED_TYPE, name,
                    field.getDeclaringClass().getName(),
                    fieldType.getSimpleName());
        else
            // Should be able to handle any object type not excluded.
            return new _Object(objclass, name, vh, flags, doc, opt);
    }

    private static final String UNSUPPORTED_TYPE =
            "@Member target %.50s in %.100s has unsupported type %.50s";

    private static class _int extends PyMemberDescr {

        _int(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);

        }

        @Override
        protected Object get(Object obj) { return (int)field.get(obj); }

        @Override
        protected void set(Object obj, Object value)
                throws PyBaseException, Throwable {
            int v = PyNumber.asSize(value, null);
            field.set(obj, v);
        }
    }

    private static class _double extends PyMemberDescr {

        _double(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc) {
            super(objclass, name, handle, flags, doc);
        }

        @Override
        protected Object get(Object obj) {
            return (double)field.get(obj);
        }

        @Override
        protected void set(Object obj, Object value)
                throws PyBaseException, Throwable {
            double v = PyFloat.asDouble(value);
            field.set(obj, v);
        }
    }

    /** Behaviour for reference types. */
    private static abstract class Reference extends PyMemberDescr {

        /**
         * Controls what happens when the attribute implementation is
         * {@code null}, If {@code true}, {@link #get(Object)} will
         * raise {@link PyAttributeError AttributeError}. If
         * {@code false}, {@link #get(Object)} will return {@code None}.
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
         * reference types raise an {@link PyAttributeError
         * AttributeError}.
         */
        @Override
        protected void delete(Object obj) {
            if (optional && field.get(obj) == null)
                throw _PyUtil.noAttributeOnType(objclass, name);
            field.set(obj, null);
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
        protected Object get(Object obj) {
            String value = (String)field.get(obj);
            if (value == null) {
                if (optional)
                    throw _PyUtil.noAttributeOnType(objclass, name);
                else
                    return Py.None;
            }
            return value;
        }

        @Override
        protected void set(Object obj, Object value)
                throws PyBaseException, Throwable {
            // Special-case None if *not* an optional attribute
            if (value == Py.None && !optional) {
                delete(obj);
            } else {
                String v = PyUnicode.asString(value,
                        (o) -> attrMustBe("a string", o));
                // String v = value.toString();
                field.set(obj, v);
            }
        }
    }

    /**
     * An {@code object} attribute that may be deleted (represented by
     * {@code null} in Java).
     */
    private static class _Object extends Reference {

        _Object(PyType objclass, String name, VarHandle handle,
                EnumSet<Flag> flags, String doc, boolean optional) {
            super(objclass, name, handle, flags, doc, optional);
        }

        @Override
        protected Object get(Object obj) {
            Object value = field.get(obj);
            if (value == null) {
                if (optional)
                    throw _PyUtil.noAttributeOnType(objclass, name);
                else
                    return Py.None;
            }
            return value;
        }

        @Override
        protected void set(Object obj, Object value)
                throws PyBaseException, Throwable {
            // Special-case None if *not* an optional attribute
            if (value == Py.None && !optional) { delete(obj); return; }

            try {
                field.set(obj, value);
            } catch (ClassCastException cce) {
                // Raise TypeError mentioning Python type of field.
                throw attrMustBe(field.varType(), value);
            }
        }
    }
}
