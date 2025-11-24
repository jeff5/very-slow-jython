// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.type.Exposed;
import uk.co.farowl.vsj4.type.FastCall;
import uk.co.farowl.vsj4.type.Feature;
import uk.co.farowl.vsj4.type.TypeSpec;
import uk.co.farowl.vsj4.type.WithClass;

/**
 * The Python type {@code types.MethodWrapperType} (seen also as
 * {@code <class 'method-wrapper'>}) represents a special method bound
 * to a particular target "self" object. It is part of the mechanism
 * which allows a special method defined in Java to be called from
 * Python using its name as an attribute.
 * <p>
 * An example from the wild is to evaluate: {@code (42).__add__},
 * binding the method {@code __add__} to the target {@code 42}, to
 * produce a callable equivalent to <i>λ x . 42+x</i>. An instance of
 * this class results from a call to
 * {@link PyWrapperDescr#__get__(Object, PyType)}.
 */
// Compare CPython wrapperobject in descrobject.c
// and _PyMethodWrapper_Type in descrobject.c
public class PyMethodWrapper implements WithClass, FastCall {

    /** Only referenced during bootstrap by {@link TypeSystem}. */
    static class Spec {
        /** @return the type specification. */
        static TypeSpec get() {
            return new TypeSystem.BootstrapSpec("method-wrapper",
                    MethodHandles.lookup(), PyMethodWrapper.class)
                            .remove(Feature.INSTANTIABLE);
        }
    }

    /**
     * Return the Python type of {@code method-wrapper} objects,
     * {@code types.MethodWrapperType}.
     *
     * @return {@code <class 'method-wrapper'>}
     */
    public static final PyType TYPE() {
        return TypeSystem.TYPE_method_wrapper;
    }

    // No subclasses so always this type
    @Override
    public PyType getType() { return TYPE(); }

    /** Descriptor for the method being bound. */
    @Exposed.Member
    PyWrapperDescr descr;

    /**
     * The target object of the method call that results when
     * {@link #__call__(Object[], String[]) __call__} is invoked on this
     * object. This is exposed to Python as {@code __self__}.
     */
    @Exposed.Member("__self__")
    final Object self;

    /**
     * Bind a slot wrapper descriptor to its target. The result is a
     * callable object e.g. in {@code bark = "Woof!".__mul__},
     * {@code bark} will be an instance of this class, {@code "Woof!}"
     * is {@code self} and {@code str.__mul__} is the descriptor.
     *
     * @param descr for the special method to bind
     * @param self to which this method call is bound
     */
    PyMethodWrapper(PyWrapperDescr descr, Object self) {
        this.descr = descr;
        this.self = self;
    }

    // CPython method table (to convert to annotations):
    // static MethodDef wrapper_methods[] = {
    // {"__reduce__", (PyCFunction)wrapper_reduce, METH_NOARGS,
    // null},
    // {null, null}
    // };

    // CPython member table (to convert to annotations):
    // static MemberDef wrapper_members[] = {
    // {"__self__", T_OBJECT, offsetof(wrapperobject, self), READONLY},
    // {0}
    // };

    // CPython get-set table (to convert to annotations):
    // static GetSetDef wrapper_getsets[] = {
    // {"__objclass__", (getter)wrapper_objclass},
    // {"__name__", (getter)wrapper_name},
    // {"__qualname__", (getter)wrapper_qualname},
    // {"__doc__", (getter)wrapper_doc},
    // {"__text_signature__", (getter)wrapper_text_signature},
    // {0}
    // };

    // CPython type object (to convert to special method names):
    // PyType _PyMethodWrapper_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "method-wrapper", /* tp_name */
    // sizeof(wrapperobject), /* tp_basicsize */
    // 0, /* tp_itemsize */
    // /* methods */
    // (destructor)wrapper_dealloc, /* tp_dealloc */
    // 0, /* tp_vectorcall_offset */
    // 0, /* tp_getattr */
    // 0, /* tp_setattr */
    // 0, /* tp_as_async */
    // (reprfunc)wrapper_repr, /* tp_repr */
    // 0, /* tp_as_number */
    // 0, /* tp_as_sequence */
    // 0, /* tp_as_mapping */
    // (hashfunc)wrapper_hash, /* tp_hash */
    // (ternaryfunc)wrapper_call, /* tp_call */
    // 0, /* tp_str */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // 0, /* tp_setattro */
    // 0, /* tp_as_buffer */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, /* tp_flags */
    // 0, /* tp_doc */
    // wrapper_traverse, /* tp_traverse */
    // 0, /* tp_clear */
    // wrapper_richcompare, /* tp_richcompare */
    // 0, /* tp_weaklistoffset */
    // 0, /* tp_iter */
    // 0, /* tp_iternext */
    // wrapper_methods, /* tp_methods */
    // wrapper_members, /* tp_members */
    // wrapper_getsets, /* tp_getset */
    // 0, /* tp_base */
    // 0, /* tp_dict */
    // 0, /* tp_descr_get */
    // 0, /* tp_descr_set */
    // };

    // Exposed attributes --------------------------------------------

    /*
     * While superficially identical to members that other classes
     * inherit from Descriptor, note that __objclass__ and __name__ are
     * here read-only *attributes* (getter methods) rather than
     * *members* so that we can get them from the descriptor of the
     * wrapped special method. This the same in CPython.
     */

    /**
     * Return the class where this method was defined. This is exposed
     * to Python as {@code __objclass__}. It is the type of the object
     * that is expected as the first (that is, {@code self}) argument,
     * and that is bound (as {@code obj}) when {@code __get__} is
     * called.
     *
     * @return the class where this object was defined
     */
    @Exposed.Getter
    // Compare CPython wrapper_objclass in descrobject.c
    public Object __objclass__() { return descr.__objclass__(); }

    /**
     * Return the name of the member, attribute or method described. For
     * example it is {@code "__add__"} or {@code "__str__"}. This is
     * exposed to Python as {@code __name__}.
     *
     * @return plain name of the (special) method.
     */
    @Exposed.Getter
    // Compare CPython wrapper_name in descrobject.c
    public Object __name__() { return descr.sm.methodName; }

    /** @return documentation string formatted for external reader. */
    @Exposed.Getter
    // Compare CPython wrapper_doc in descrobject.c
    private Object __doc__() {
        return Descriptor.getDocFromInternalDoc(descr.sm.methodName,
                descr.sm.doc);
    }

    /** @return signature string based on internal documentation. */
    @Exposed.Getter
    // Compare CPython wrapper_text_signature in descrobject.c
    private Object __text_signature__() {
        return Descriptor.getTextSignatureFromInternalDoc(
                descr.sm.methodName, descr.sm.doc);
    }

    /**
     * Return the qualified name attribute.
     *
     * @return qualified name of the method.
     * @throws PyAttributeError if the attribute does not exist
     * @throws Throwable from other implementation errors
     */
    @Exposed.Getter
    // Compare CPython wrapper_qualname in descrobject.c
    public Object __qualname__() throws PyAttributeError, Throwable {
        return descr.__qualname__();
    }

    // Special methods ------------------------------------------------

    // Compare CPython wrapper_repr in descrobject.c
    private Object __repr__() {
        return String.format("<method-wrapper '%s' of %s>",
                descr.sm.methodName, _PyUtil.toAt(self));
    }

    // Compare CPython wrapper_richcompare in descrobject.c
    private Object __eq__(Object b) {
        // Both arguments should be exactly PyMethodWrapper
        if (b instanceof PyMethodWrapper) {
            PyMethodWrapper wb = (PyMethodWrapper)b;
            return descr == wb.descr && self == wb.self;
        }
        return Py.NotImplemented;
    }

    // Compare CPython wrapper_richcompare in descrobject.c
    private Object __ne__(Object b) {
        // Both arguments should be exactly PyMethodWrapper
        if (b instanceof PyMethodWrapper) {
            PyMethodWrapper wb = (PyMethodWrapper)b;
            return descr != wb.descr || self != wb.self;
        }
        return Py.NotImplemented;
    }

    // Compare CPython wrapper_hash in descrobject.c
    private int __hash__() {
        int x = self.hashCode() ^ descr.hashCode();
        return x == -1 ? -2 : x;
    }

    // protected PyObject __reduce__(PyObject ignored) {
    // return Py_BuildValue("N(OO)",
    // _PyEval_GetBuiltinId(ID.getattr), self,
    // PyDescr_NAME(descr));
    // }

    // Compare CPython wrapper_call in descrobject.c
    Object __call__(Object[] args, String[] names) throws Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            /*
             * FastCall.call() methods may encode a TypeError as an
             * ArgumentError with limited context.
             */
            throw typeError(ae, args, names);
        }
    }

    @Override
    public Object call(Object[] args, String[] names) throws Throwable {
        return descr.call(self, args, names);
    }

    @Override
    public Object call() throws ArgumentError, Throwable {
        return descr.call(self);
    }

    @Override
    public Object call(Object a1) throws ArgumentError, Throwable {
        return descr.call(self, a1);
    }

    @Override
    public Object call(Object a1, Object a2)
            throws ArgumentError, Throwable {
        return descr.call(self, a1, a2);
    }

    @Override
    public PyBaseException typeError(ArgumentError ae, Object[] args,
            String[] names) {
        return descr.typeError(ae, args, names);
    }
}
