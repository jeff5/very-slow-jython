package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.PyType.Flag;

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
/*
 * Difference from CPython: In CPython, a PyWrapperDescr is created
 * because the slot at the corresponding offset in the PyTypeObject of
 * the owning Python type is filled, statically or by PyType_FromSpec.
 *
 * In this implementation, we create a PyWrapperDescr as an attribute
 * because the Java implementation of the owning type defines a method
 * with that slot's name. Then we fill the slot because the type has an
 * attribute with the matching name. The result should be the same but
 * the process is more regular.
 */
abstract class PyWrapperDescr extends Descriptor {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("wrapper_descriptor", PyWrapperDescr.class,
                    MethodHandles.lookup()).flagNot(Flag.BASETYPE));

    /**
     * The {@link Slot} ({@code enum}) describing the generic
     * characteristics the special method of which
     * {@link Descriptor#objclass} provides a particular implementation.
     */
    final Slot slot;

    /**
     * A handle for the particular implementation (special method) being
     * wrapped. The method type is that of
     * {@link #slot}{@code .signature}.
     */
    final MethodHandle wrapped;

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
    PyWrapperDescr(PyType objclass, Slot slot, MethodHandle wrapped) {
        super(TYPE, objclass, slot.methodName);
        this.slot = slot;
        this.wrapped = wrapped;
    }

    /**
     * Invoke the wrapped method handle, having arranged the arguments
     * as expected by a slot. When we create sub-classes of
     * {@code PyWrapperDescr} to handle different slot signatures, this
     * is method that accepts arguments in a generic way (from the
     * interpreter, say) and adapts them to the specific needs of the
     * method handle {@link #wrapped}.
     *
     * @param self target object of the method call
     *
     * @param args of the method call
     * @param kwargs of the method call
     * @return result of the method call
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    abstract Object callWrapped(Object self, PyTuple args,
            PyDict kwargs) throws Throwable;

    // Exposed attributes ---------------------------------------------

    // CPython get-set table (to convert to annotations):
    // private GetSetDef wrapperdescr_getset[] = {
    // {"__doc__", (getter)wrapperdescr_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {"__text_signature__",
    // (getter)wrapperdescr_get_text_signature},
    // {0}
    // };

    @Getter
    // Compare CPython wrapperdescr_get_doc in descrobject.c
    protected Object __doc__() {
        return PyType.getDocFromInternalDoc(slot.methodName, slot.doc);
    }

    @Getter
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
    private Object __repr__() {
        return descrRepr("slot wrapper");
    }

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

    // Compare CPython wrapperdescr_call in descrobject.c
    protected Object __call__(PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {
        // Make sure that the first argument is acceptable as 'self'
        int argc = args.value.length;
        if (argc < 1) {
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.name);
        }
        Object self = args.value[0];
        PyType selfType = PyType.of(self);
        if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
            throw new TypeError(DESCRIPTOR_REQUIRES, name,
                    objclass.name, selfType.name);
        }
        args = new PyTuple(args.value, 1, argc - 1);
        return callWrapped(self, args, kwargs);
    }

    // Plumbing ------------------------------------------------------

    /**
     * Check that no positional or keyword arguments are supplied. This
     * is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkNoArgs(PyTuple args, PyDict kwargs)
            throws TypeError {
        if (args.value.length != 0)
            throw new TypeError(TAKES_NO_ARGUMENTS, name,
                    args.value.length);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param expArgs expected number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int expArgs,
            PyDict kwargs) throws TypeError {
        int n = args.value.length;
        if (n != expArgs)
            throw new TypeError(TAKES_ARGUMENTS, name, expArgs, n);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callWrapped(Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param minArgs minimum number of positional arguments
     * @param maxArgs maximum number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int minArgs,
            int maxArgs, PyDict kwargs) throws TypeError {
        int n = args.value.length;
        if (n < minArgs || n > maxArgs)
            throw new TypeError(TAKES_ARGUMENTS, name,
                    String.format("from %d to %d", minArgs, maxArgs),
                    n);
        else if (kwargs != null && kwargs.isEmpty())
            throw new TypeError(TAKES_NO_KEYWORDS, name);
    }

    private static final String TAKES_NO_ARGUMENTS =
            "wrapper %s() takes no arguments (%d given)";
    private static final String TAKES_ARGUMENTS =
            "wrapper %s() takes %s arguments (%d given)";
    private static final String TAKES_NO_KEYWORDS =
            "wrapper %s() takes no keyword arguments";
}
