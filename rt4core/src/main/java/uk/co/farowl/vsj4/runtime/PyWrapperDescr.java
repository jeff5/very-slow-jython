// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod.Signature;
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
            new TypeSpec("wrapper_descriptor",
                    MethodHandles.lookup()));

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
     * Return the handle contained in this descriptor applicable to the
     * Java class supplied (typically that of a {@code self} argument
     * during a call). The {@link Descriptor#objclass} is consulted to
     * make this determination. If the class is not an accepted
     * implementation of {@code objclass}, an empty slot handle (with
     * the correct signature) is returned.
     *
     * @param selfClass Java class of the {@code self} argument
     * @return corresponding handle (or {@code slot.getEmpty()})
     */
    abstract MethodHandle getWrapped(Class<?> selfClass);

    /**
     * Call the wrapped method with positional arguments (the first
     * being the target object) and optionally keywords arguments. The
     * arguments, in type and number, must match the signature of the
     * special function slot.
     *
     * @param args positional arguments beginning with {@code self}
     * @param names of keywords in the method call
     * @return result of calling the wrapped method
     * @throws PyBaseException(TypeError) if {@code args[0]} is the wrong type
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_call in descrobject.c
    public Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {

        int argc = args.length;
        if (argc > 0) {
            // Split the leading element self from args
            Object self = args[0];
            Object[] newargs;
            if (argc == 1) {
                newargs = Util.EMPTY_ARRAY;
            } else {
                newargs = new Object[argc - 1];
                System.arraycopy(args, 1, newargs, 0, newargs.length);
            }

            // Make sure that the first argument is acceptable as 'self'
            PyType selfType = PyType.of(self);
            if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
                throw PyErr.format(PyExc.TypeError,DESCRIPTOR_REQUIRES, name,
                        objclass.getName(), selfType.getName());
            }

            return callWrapped(self, newargs, names);

        } else {
            // Not even one argument
            throw PyErr.format(PyExc.TypeError,DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.getName());
        }
    }

 // XXX Why not just call the handle we already have?
    /*
     * This calls the type slot, but that's derived from information
     * already to hand.
     */
    @Override
    public Object call(Object[] args, String[] names)
            throws PyBaseException, Throwable {

        int n = args.length, m = n - 1;

        if (m < 0) {
            // Not even one argument
            throw PyErr.format(PyExc.TypeError,DESCRIPTOR_NEEDS_ARGUMENT, name,
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

            try {
                // Call this as a method bound to self.
                Signature sig = slot.signature;
                MethodHandle wrapped = getWrapped(self.getClass());
                return sig.callWrapped(wrapped, self, rest, names);
            } catch (ArgumentError ae) {
                /*
                 * Implementations may throw ArgumentError as a
                 * simplified encoding of a TypeError.
                 */
                throw typeError(ae, rest);
            }
        }
    }

    // XXX Why not just call the handle we already have?
    /*
     * This calls the type slot, but that's derived from information
     * already to hand.
     */

    /**
     * Invoke the method described by this {@code PyWrapperDescr} the
     * given target {@code self}, and the arguments supplied.
     *
     * @param self target object of the method call
     * @param args of the method call
     * @param names of keywords in the method call
     * @return result of the method call
     * @throws PyBaseException(TypeError) if the arguments do not fit the special method
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    Object callWrapped(Object self, Object[] args, String[] names)
            throws Throwable {
        try {
            // Call through the correct wrapped handle
            MethodHandle wrapped = getWrapped(self.getClass());
            SpecialMethod.Signature sig = slot.signature;
            return sig.callWrapped(wrapped, self, args, names);
        } catch (ArgumentError ae) {
            throw typeError(ae, args, names);
        }
    }

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
        Single(PyType objclass, SpecialMethod slot, MethodHandle wrapped) {
            super(objclass, slot);
            this.wrapped = wrapped;
        }

        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // Make sure that the first argument is acceptable as 'self'
            if (objclass.javaClass().isAssignableFrom(selfClass))
                return wrapped;
            else
                return slot.getEmpty();
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
        Multiple(PyType objclass, SpecialMethod slot, MethodHandle[] wrapped) {
            super(objclass, slot);
            this.wrapped = wrapped;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method will check that the type of self matches
         * {@link Descriptor#objclass}, according to its
         * {@link PyType#indexAccepted(Class)}.
         */
        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return wrapped[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                return slot.getEmpty();
            }
        }
    }
}
