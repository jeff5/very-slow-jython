// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;

import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.internal.Util;

/**
 * A {@link Descriptor} for a particular definition <b>in Java</b> of
 * one of the special methods of the Python data model (such as
 * {@code __sub__}). The type also appears as
 * {@code <class 'wrapper_descriptor'>}.
 * <p>
 * The owner of the descriptor is the Python type providing the
 * definition. Type construction places a {@code PyWrapperDescr} in the
 * dictionary of the defining {@link PyType}, against a key that is the
 * "dunder name" of the special method it wraps. (This does not preclude
 * client code moving it around afterwards!)
 * <p>
 * The {@code PyWrapperDescr} provides a {@code MethodHandle} for the
 * defining method. In every Python type where a {@code PyWrapperDescr}
 * appears as the attribute value corresponding to a special method, the
 * handle will fill the corresponding type slot. This may happen because
 * the type is the defining type, by inheritance, or by insertion of the
 * {@code PyWrapperDescr} as an attribute of the type. (In the last
 * case, the signature of the wrapped and destination slots must match.)
 */
// Compare CPython: PyWrapperDescrObject in descrobject.h
// and methods wrapperdescr_* in descrobject.c.
/*
 * Difference from CPython: In CPython, a PyWrapperDescr is created
 * because the slot at the corresponding offset in the PyTypeObject of
 * the owning Python type is filled, statically or by PyType_FromSpec.
 * It is then made an attribute in the dictionary of the type.
 *
 * In this implementation, we create a PyWrapperDescr as an attribute
 * because the Java implementation of the owning type defines a method
 * with the corresponding "dunder name". Then we (may) fill a slot in
 * the Representation because the type has an attribute with this name.
 * This second step is the same process covers special methods defined
 * in Python.
 */
public abstract class PyWrapperDescr extends MethodDescriptor {

    static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("wrapper_descriptor", MethodHandles.lookup()));

    /**
     * The {@link SpecialMethod} ({@code enum}) describing the generic
     * characteristics of the special method, of which
     * {@link Descriptor#objclass} provides a particular implementation.
     */
    final SpecialMethod slot;

    /**
     * Construct a slot wrapper descriptor for the {@code slot} in
     * {@code objclass}.
     *
     * @param objclass the class declaring the special method
     * @param slot for the generic special method
     */
    // Compare CPython PyDescr_NewClassMethod in descrobject.c
    PyWrapperDescr(PyType objclass, SpecialMethod slot) {
        super(TYPE, objclass, slot.methodName);
        this.slot = slot;
    }

    // Exposed attributes ---------------------------------------------

    // CPython get-set table (to convert to annotations):
    // private GetSetDef wrapperdescr_getset[] = {
    // {"__doc__", (getter)wrapperdescr_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {"__text_signature__",
    // (getter)wrapperdescr_get_text_signature},
    // {0}
    // };

    @Exposed.Getter
    // Compare CPython wrapperdescr_get_doc in descrobject.c
    protected Object __doc__() {
        return PyType.getDocFromInternalDoc(slot.methodName, slot.doc);
    }

    @Exposed.Getter
    // Compare CPython wrapperdescr_get_text_signature in descrobject.c
    protected Object __text_signature__() {
        return PyType.getTextSignatureFromInternalDoc(slot.methodName,
                slot.doc);
    }

    // Special methods ------------------------------------------------

    // CPython type object (to convert to special method names):
    // PyType PyWrapperDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "wrapper_descriptor",
    // sizeof(PyWrapperDescr),
    // (reprfunc)wrapperdescr_repr, /* tp_repr */
    // (ternaryfunc)wrapperdescr_call, /* tp_call */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
    // Py_TPFLAGS_METHOD_DESCRIPTOR, /* tp_flags */
    // descr_traverse, /* tp_traverse */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // wrapperdescr_getset, /* tp_getset */
    // (descrgetfunc)wrapperdescr_get, /* tp_descr_get */
    // };

    // Compare CPython wrapperdescr_repr in descrobject.c
    @SuppressWarnings("unused")
    private Object __repr__() { return descrRepr("slot wrapper"); }

    // Compare CPython wrapperdescr_get in descrobject.c
    @Override
    protected Object __get__(Object obj, PyType type) {
        if (obj == null)
            /*
             * obj==null indicates the descriptor was found on the
             * target object itself (or a base), see CPython
             * type_getattro in typeobject.c
             */
            return this;
        else {
            // Return callable binding this and obj
            check(obj);
            return new PyMethodWrapper(this, obj);
        }
    }

    /**
     * Call the wrapped method with positional arguments (the first
     * being the target object) and optionally keywords arguments. The
     * arguments, in type and number, must match the signature of the
     * special function slot.
     *
     * @param args positional arguments beginning with {@code self}
     * @param names of keywords in the method call
     * @return result of calling the wrapped method
     * @throws PyBaseException (TypeError) if {@code args[0]} is the
     *     wrong type
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_call in descrobject.c
    public Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            /*
             * FastCall.call() methods may encode a TypeError as an
             * ArgumentError with limited context.
             */
            assert args.length > 0;
            Object[] rest = Arrays.copyOfRange(args, 1, args.length);
            // Add local context to make a TypeError.
            throw typeError(ae, rest, names);
        }
    }

    @Override
    public Object call(Object[] args, String[] names)
            throws ArgumentError, Throwable {

        int n = args.length, m = n - 1;

        if (m < 0) {
            // Not even one argument
            throw PyErr.format(PyExc.TypeError,
                    DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.getName());

        } else {
            // Split the leading element self from rest of args
            Object self = args[0], rest[];
            if (m == 0) {
                rest = Util.EMPTY_ARRAY;
            } else {
                rest = new Object[m];
                System.arraycopy(args, 1, rest, 0, m);
            }

            return callWrapped(self, rest, names);
        }
    }

    /**
     * Invoke the method described by this {@code PyWrapperDescr}, for
     * the given target {@code self}, having arranged the arguments
     * according to the signature of the wrapped special method.
     *
     * @param self target object of the method call
     * @param args of the method call
     * @param names of keywords in the method call
     * @return result of the method call
     * @throws PyBaseException (TypeError) if the arguments do not fit
     *     the special method
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    // Also, CPython wrap_* functions in typeobject.c
    Object callWrapped(Object self, Object[] args, String[] names)
            throws PyBaseException, Throwable {
        // Call through the correct wrapped handle for self
        MethodHandle mh = getHandle(self);
        String name;
        try {
            switch (slot.signature) {
                case UNARY:
                    checkNoArgs(args, names);
                    return mh.invokeExact(self);
                case BINARY:
                    checkArgs(args, 1, names);
                    return mh.invokeExact(self, args[0]);
                case TERNARY:
                    checkArgs(args, 2, names);
                    return mh.invokeExact(self, args[0], args[1]);
                case CALL:
                    return mh.invokeExact(self, args, names);
                case PREDICATE:
                    checkNoArgs(args, names);
                    return (boolean)mh.invokeExact(self);
                case BINARY_PREDICATE:
                    checkArgs(args, 1, names);
                    return (boolean)mh.invokeExact(self, args[0]);
                case LEN:
                    checkNoArgs(args, names);
                    return (int)mh.invokeExact(self);
                case SETITEM:
                    checkArgs(args, 2, names);
                    mh.invokeExact(self, args[0], args[1]);
                    return Py.None;
                case DELITEM:
                    checkArgs(args, 1, names);
                    mh.invokeExact(self, args[0]);
                    return Py.None;
                case GETATTR:
                    checkArgs(args, 1, names);
                    name = PyUnicode.asString(args[0],
                            Abstract::attributeNameTypeError);
                    return mh.invokeExact(self, name);
                case SETATTR:
                    checkArgs(args, 2, names);
                    name = PyUnicode.asString(args[0],
                            Abstract::attributeNameTypeError);
                    mh.invokeExact(self, name, args[1]);
                    return Py.None;
                case DELATTR:
                    checkArgs(args, 1, names);
                    name = PyUnicode.asString(args[0],
                            Abstract::attributeNameTypeError);
                    mh.invokeExact(self, name);
                    return Py.None;
                case DESCRGET:
                    checkArgs(args, 1, 2, names);
                    Object obj = args[0];
                    if (obj == Py.None) { obj = null; }
                    PyType type = null;
                    if (args.length == 2) {
                        if (args[1] instanceof PyType t) { type = t; }
                        // Following CPython in ignoring other types.
                    }
                    if (type == null && obj == null) {
                        throw PyErr.format(PyExc.TypeError, GET_NONE);
                    }
                    return mh.invokeExact(self, obj, type);
                case INIT:
                    mh.invokeExact(self, args, names);
                    return Py.None;
                default:
                    assert false;  // switch should be exhaustive
                    return Py.None;
            }
        } catch (ArgumentError ae) {
            throw typeError(ae, args, names);
        }
    }

    @Override
    public Object call(Object self) throws ArgumentError, Throwable {
        MethodHandle mh = getHandle(self);
        return switch (slot.signature) {
            case UNARY -> mh.invokeExact(self);
            case PREDICATE -> (boolean)mh.invokeExact(self);
            case LEN -> (int)mh.invokeExact(self);
            default -> super.call(self);
        };
    }

    @Override
    public Object call(Object self, Object a1)
            throws ArgumentError, Throwable {
        MethodHandle mh = getHandle(self);
        String name;
        switch (slot.signature) {
            case BINARY:
                return mh.invokeExact(self, a1);
            case BINARY_PREDICATE:
                return (boolean)mh.invokeExact(self, a1);
            case DELITEM:
                mh.invokeExact(self, a1);
                return Py.None;
            case GETATTR:
                name = PyUnicode.asString(a1,
                        Abstract::attributeNameTypeError);
                return mh.invokeExact(self, name);
            case DELATTR:
                name = PyUnicode.asString(a1,
                        Abstract::attributeNameTypeError);
                mh.invokeExact(self, name);
                return Py.None;
            case DESCRGET:
                if (a1 == Py.None || a1 == null) {
                    throw PyErr.format(PyExc.TypeError, GET_NONE);
                }
                return mh.invokeExact(self, a1, (PyType)null);
            default:
                return super.call(self, a1);
        }
    }

    @Override
    public Object call(Object self, Object a1, Object a2)
            throws ArgumentError, Throwable {
        MethodHandle mh = getHandle(self);
        String name;
        switch (slot.signature) {
            case TERNARY:
                return mh.invokeExact(self, a1, a2);
            case SETITEM:
                mh.invokeExact(self, a1, a2);
                return Py.None;
            case SETATTR:
                name = PyUnicode.asString(a1,
                        Abstract::attributeNameTypeError);
                mh.invokeExact(self, name, a2);
                return Py.None;
            case DESCRGET:
                if (a1 == Py.None) { a1 = null; }
                if (a2 instanceof PyType type) {
                    return mh.invokeExact(self, a1, type);
                } else if (a1 == null) {
                    // Following CPython in treating non-type as null.
                    throw PyErr.format(PyExc.TypeError, GET_NONE);
                }
                return mh.invokeExact(self, a1, null);
            default:
                return super.call(self, a1, a2);
        }
    }

    /**
     * Return the correct handle for the {@code self} argument, taking
     * into account the possibility that the type of {@code self} is a
     * proper sub-type of {@code __objclass__} or not a sub-type at all.
     *
     * @param self by which to select the handle
     * @return the handle to call with {@code self} as first argument
     * @throws PyBaseException (TypeError) if the type of {@code self}
     *     does not match the wrapped handle.
     * @throws Throwable propagated from subclass check
     */
    abstract MethodHandle getHandle(Object self)
            throws PyBaseException, Throwable;

    /**
     * Check that the given type is acceptable for the {@code self}
     * argument, that is, it is a subclass of
     * {@link Descriptor#objclass}.
     *
     * @param selfType Python type of {@code self}
     * @throws PyBaseException (TypeError) if {@code self} is not a
     *     subclass of {@code __objclass__}.
     * @throws Throwable propagated from subclass check
     */
    void checkPythonType(PyType selfType)
            throws PyBaseException, Throwable {
        if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
            throw PyErr.format(PyExc.TypeError, DESCRIPTOR_REQUIRES,
                    name, objclass.getName(), selfType.getName());
        }
    }

    private static final String GET_NONE =
            "__get__(None, None) is invalid";

    /**
     * A {@link PyWrapperDescr} for use when the owning Python type has
     * just one accepted implementation.
     */
    static class Single extends PyWrapperDescr {

        /**
         * A handle for the particular implementation of a special
         * method being wrapped. The method type is that of
         * {@link #slot}{@code .signature}.
         */
        protected final MethodHandle wrapped;

        /**
         * Construct a slot wrapper descriptor, identifying by a method
         * handle the implementation method for the {@code slot} in
         * {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped a handle to an implementation of that slot
         */
        // Compare CPython PyDescr_NewClassMethod in descrobject.c
        Single(PyType objclass, SpecialMethod slot,
                MethodHandle wrapped) {
            super(objclass, slot);
            this.wrapped = wrapped;
        }

        @Override
        public MethodHandle getHandle(int selfClassIndex) {
            assert selfClassIndex == 0;
            return wrapped;
        }

        @Override
        MethodHandle getHandle(Object self)
                throws PyBaseException, Throwable {
            Representation rep = PyType.registry.get(self.getClass());
            // rep.index can only validly be zero
            // Check acceptability at the Python level
            checkPythonType(rep.pythonType(self));
            return wrapped;
        }
    }

    /**
     * A {@link PyWrapperDescr} for use when the owning Python type has
     * multiple accepted implementations.
     */
    static class Multiple extends PyWrapperDescr {

        /**
         * Handles for the particular implementations of a special
         * method being wrapped. The method type of each is that of
         * {@link #slot}{@code .signature}.
         */
        protected final MethodHandle[] wrapped;

        /**
         * Construct a slot wrapper descriptor, identifying by an array
         * of method handles the implementation methods for the
         * {@code slot} in {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped handles to the implementation of that slot
         */
        // Compare CPython PyDescr_NewClassMethod in descrobject.c
        Multiple(PyType objclass, SpecialMethod slot,
                MethodHandle[] wrapped) {
            super(objclass, slot);
            assert wrapped.length == objclass.selfClasses().size();
            this.wrapped = wrapped;
        }

        @Override
        public MethodHandle getHandle(int selfClassIndex) {
            return wrapped[selfClassIndex];
        }

        @Override
        MethodHandle getHandle(Object self)
                throws PyBaseException, Throwable {
            Representation rep = PyType.registry.get(self.getClass());
            PyType selfType = rep.pythonType(self);
            if (selfType == objclass) {
                // selfType defined the method so it must be ok
                int index = rep.getIndex();
                return wrapped[index];
            } else {
                // Check acceptability at the Python level
                checkPythonType(selfType);
                // A super-type of selfType defined the method,
                // so self class must subclass its primary class.
                assert objclass.javaClass()
                        .isAssignableFrom(self.getClass());
                return wrapped[0];
            }
        }
    }
}
