// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.lang.invoke.MethodHandles;
import java.util.Map;

import uk.co.farowl.vsj4.internal.Util;
import uk.co.farowl.vsj4.type.Exposed.Getter;
import uk.co.farowl.vsj4.type.FastCall;
import uk.co.farowl.vsj4.type.Feature;
import uk.co.farowl.vsj4.type.TypeSpec;
import uk.co.farowl.vsj4.type.WithDict;

/**
 * The Python {@code staticmethod} class, that is most often encountered
 * as a decorator on a method in a class definition, is also implicated
 * in the exposure of static methods from built-in types.
 * {@code staticmethod} is a callable that passes {@code __call__} on to
 * the object that it wraps. It is technically a non-data descriptor as
 * it has a {@code __get__} method but not a {@code __set__}.
 * <p>
 * {@code staticmethod} is valued principally for what it <i>doesn't</i>
 * do, which is hard to spot. When encountered as an attribute by
 * {@code object.__getattribute__}, its {@code __get__} is called in the
 * normal way, but it <i>doesn't</i> bind the target object (contrast
 * with {@link PyMethodDescr#__get__(Object, PyType)}). When the
 * subsequent {@code staticmethod.__call__} comes, only the arguments to
 * that call go through to the wrapped callable and no {@code self} is
 * inserted before them.
 */
public class PyStaticMethod implements WithDict {

    static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("staticmethod", MethodHandles.lookup())
                    .add(Feature.IMMUTABLE, Feature.BASETYPE));
    /**
     * The underlying object exposed as {@code __func__}, or the
     * underlying object wrapped as a {@link FastCall} if the original
     * object does not implement that.
     */
    private Object callable;
    /** Actual Python type of the object. */
    // TODO enforce BaseType
    final PyType type;
    /** Instance dictionary also exposed as {@code __dict__} */
    PyDict dict = new PyDict();

    /**
     * Construct a {@code staticmethod} wrapping the given object as the
     * callable. Any object is accepted: only later (during
     * {@code staticmethod.__call__}) will it be discovered whether it
     * is actually callable.
     *
     * @param descrtype actual Python type of this object
     * @param callable to be wrapped
     */
    PyStaticMethod(PyType descrtype, Object callable) {
        this.type = descrtype;
        this.setCallable(callable);
    }

    /**
     * Construct a {@code staticmethod} wrapping the given object as the
     * callable. Any object is accepted: only later (during
     * {@code staticmethod.__call__}) will it be discovered whether it
     * is actually callable.
     *
     * @param callable to be wrapped
     */
    public PyStaticMethod(Object callable) { this(TYPE, callable); }

    @Override
    public PyType getType() { return type; }

    @Override
    public Map<Object, Object> getDict() { return dict; }

    // Special methods -----------------------------------------------

    @SuppressWarnings("unused")
    private String __repr__() throws PyBaseException, Throwable {
        return String.format("staticmethod(%s)",
                Abstract.repr(getCallable()));
    }

    Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {
        return Callables.call(getCallable(), args, names);
    }

    Object __get__(Object obj, PyType type) {
        assert callable != null;
        return getCallable();
    }

    // Attributes ----------------------------------------------------

    /** Get the underlying callable. */
    @Getter("__func__")
    private Object getCallable() { return callable; }

    /** Set the underlying callable. */
    private void setCallable(Object callable) {
        this.callable = callable;
        // FIXME type(callable) not Python ready at this point
        // functools_wraps(callable);
    }

    // Plumbing ------------------------------------------------------

    private static String[] ATTRS = new String[] {"__module__",
            "__name__", "__qualname__", "__doc__", "__annotations__"};

    // Compare CPython functools_wraps in funcobject.c
    private void functools_wraps(Object wrapped) {
        for (String name : ATTRS) {
            Object v;
            try {
                v = Abstract.lookupAttr(wrapped, name);
                if (v != null) { Abstract.setAttr(this, name, v); }
            } catch (Throwable e) {
                throw Util.asUnchecked(e, "staticmethod wrapping");
            }
        }
    }
}
