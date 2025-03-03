// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * The base class of many built-in descriptors. Descriptors are a
 * fundamental component of the Python type system, populating the
 * dictionary of every type.
 *
 * @implNote It must be possible to create an instance of any concrete
 *     descriptor (a sub-class of this one) in circumstances where the
 *     only types in existence are {@link PyType#TYPE} and
 *     {@link PyType#OBJECT_TYPE}, and where these have not yet been
 *     given their descriptor attributes or operation slots
 *     ({@code op_*} slots}.
 *     <p>
 *     In order to create a descriptor, the JVM need only complete the
 *     static initialisation of the Java class for that descriptor and
 *     be able to execute the constructor.
 */
abstract class Descriptor implements WithClass {

    /**Single re-used instance of {@link EmptyException}     */
    protected static final EmptyException EMPTY = new EmptyException();

    /**
     * Python {@code type} of this descriptor object (e.g. for a method
     * defined in Java, it will be the type {@code method_descriptor}).
     */
    // In CPython, called ob_type (in ~/include/object.h)
    protected final PyType descrtype;

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
    // @Exposed.Member(value = "__name__", readonly = true)
    protected final String name;

    /**
     * Qualified name of the object described, e.g. "float.__add__" or
     * "int.to_bytes". This is exposed to Python as
     * {@code __qualname__}.
     */
    // In CPython, called d_qualname. Where used? Better computed?
    protected String qualname = null;

    /**
     * Constructor specifying the Python type, as returned by
     * {@link #getType()}. As this is a base for the implementation of
     * all sorts of Python types, it needs to be told which one it is.
     *
     * @param descrtype actual Python type being created
     * @param objclass that defines the attribute being described
     * @param name of the object described as {@code __name__}
     */
    Descriptor(PyType descrtype, PyType objclass, String name) {
        this.descrtype = descrtype;
        this.objclass = objclass;
        this.name = name;
    }

    @Override
    public PyType getType() { return descrtype; }

    /**
     * The {@code __get__} special method of the Python descriptor
     * protocol, implementing {@code obj.name} or possibly
     * {@code type.name}.
     *
     * @apiNote Different descriptor types may have quite different
     *     behaviour. In general, a call made with {@code obj == null}
     *     is seeking a result related to the {@code type}, while in one
     *     where {@code obj != null}, {@code obj} must be of type
     *     {@link #objclass} and {@code type} will be ignored.
     * @param obj object on which the attribute is sought or
     *     {@code null}
     * @param type on which this descriptor was found (may be ignored)
     * @return attribute value, bound object or this attribute
     * @throws Throwable from the implementation of the getter
     */
    // Compare CPython *_get methods in descrobject.c
    abstract Object __get__(Object obj, PyType type) throws Throwable;

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
     * Helper for {@code __repr__} implementation. It formats together
     * the {@code kind} argument ("member", "attribute", "method", or
     * "slot wrapper"), {@code this.name} and
     * {@code this.objclass.name}.
     *
     * @param kind description of type (first word in the repr)
     * @return repr as a {@code str}
     */
    protected String descrRepr(String kind) {
        return String.format("<%s '%.50s' of '%.100s' objects>", kind,
                name, objclass.getName());
    }

    // TODO Rationalise these three checks on self type

    /**
     * {@code descr.__get__(obj, type)} has been called on this
     * descriptor. We must check that the descriptor applies to the type
     * of object supplied as the {@code obj} argument. From Python,
     * anything could be presented, but when we operate on it, we'll be
     * assuming the particular {@link #objclass} type.
     *
     * @param obj target object (non-null argument to {@code __get__})
     * @throws PyBaseException (TypeError) if descriptor doesn't apply
     *     to {@code obj}
     */
    // Compare CPython descr_check in descrobject.c
    // We differ from CPython in that:
    // 1. We either throw or return void: there is no FALSE->error or
    // descriptor.
    // 2. The test obj==null (implying found on a type) is the caller's
    // job.
    // 3. In a data descriptor, we fold the auditing into this check.
    protected void check(Object obj) throws PyBaseException {
        PyType objType = PyType.of(obj);
        if (objType == objclass) { return; }
        if (objType.isSubTypeOf(objclass)) { return; }
        throw selfTypeError(objType);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "descriptor 'D' of 'T' objects doesn't apply to 'S'
     * objects" involving the name of this descriptor, {@link #objclass}
     * and a type which is usually the type of a {@code self} to which
     * the descriptor was erroneously applied.
     *
     * @param selfType the type of object actually received
     * @return exception to throw
     */
    protected PyBaseException selfTypeError(PyType selfType) {
        return PyErr.format(PyExc.TypeError, DESCRIPTOR_DOESNT_APPLY,
                name, objclass.getName(), selfType.getName());
    }

    private static final String DESCRIPTOR_DOESNT_APPLY =
            "descriptor '%s' of '%.100s' objects doesn't apply to '%.100s' objects";

    // Compare CPython calculate_qualname in descrobject.c
    private String calculate_qualname()
            throws PyAttributeError, Throwable {
        Object type_qualname =
                Abstract.getAttr(objclass, "__qualname__");
        if (!PyUnicode.TYPE.check(type_qualname)) {
            throw PyErr.format(PyExc.TypeError,
                    QUALNAME_IS_NOT_A_STRING);
        }
        return String.format("%s.%s", type_qualname, name);
    }

    private static final String QUALNAME_IS_NOT_A_STRING =
            "<descriptor>.__objclass__.__qualname__ is not a string";

    // Compare CPython descr_get_qualname in descrobject.c
    static Object descr_get_qualname(Descriptor descr,
            @SuppressWarnings("unused") Object ignored)
            throws PyAttributeError, Throwable {
        if (descr.qualname == null)
            descr.qualname = descr.calculate_qualname();
        return descr.qualname;
    }

    // Incompletely ported from CPython:
    // static Object descr_reduce(PyDescr descr, Object ignored)
    // {
    // return Py_BuildValue("N(OO)", _PyEval_GetBuiltinId(ID.getattr),
    // descr.objclass, PyDescr_NAME(descr));
    // }


    @Override
    public String toString() { return PyUtil.defaultToString(this); }
}
