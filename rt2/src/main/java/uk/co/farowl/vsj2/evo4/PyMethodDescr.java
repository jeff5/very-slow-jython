package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj2.evo4.ThreadState.RecursionState;

/**
 * Descriptor for a method defined in Java, specified by a
 * {@link MethodDef}. A {@code PyMethodDescr} is a callable object
 * itself, and provides binding behaviour through
 * {@link #__get__(PyObject, PyType) __get__}, which usually creates a
 * {@link PyJavaMethod}.
 */
class PyMethodDescr extends Descriptor implements VectorCallable {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("method_descriptor", PyMethodDescr.class));

    /** Description of the method as defined in Java. */
    final MethodDef methodDef;
    // CPython: vectorcallfunc vectorcall;

    /** Supports vector call methods. */ // MT: (O[])O
    private final MethodHandle callHandle;

    // Compare CPython PyDescr_NewMethod in descrobject.c
    PyMethodDescr(PyType descrtype, PyType objclass, MethodDef method) {
        super(descrtype, objclass, method.name);
        this.methodDef = method;

        // XXX What does the vectorcall stuff turn into?
        MethodHandle vectorcall = method.getVectorHandle();

        /*
         * Following Python, at this point the MethodDef flags are used
         * to choose a method to install as 'vectorcall', that has the
         * signature (PyMethodDescr, PyObject[], int, int, PyTuple )
         * PyObject. This is intended as a vector call target. Each
         * method installed is particular to the actual signature of
         * 'method', which the flags describe.
         */

        // Figure out correct vectorcall function to use

        // switch (method.flags & (METH_VARARGS | METH_FASTCALL
        // | METH_NOARGS | METH_O | METH_KEYWORDS)) {
        // case METH_VARARGS:
        // vectorcall = method_vectorcall_VARARGS;
        // break;
        // case METH_VARARGS | METH_KEYWORDS:
        // vectorcall = method_vectorcall_VARARGS_KEYWORDS;
        // break;
        // case METH_FASTCALL:
        // vectorcall = method_vectorcall_FASTCALL;
        // break;
        // case METH_FASTCALL | METH_KEYWORDS:
        // vectorcall = method_vectorcall_FASTCALL_KEYWORDS;
        // break;
        // case METH_NOARGS:
        // vectorcall = method_vectorcall_NOARGS;
        // break;
        // case METH_O:
        // vectorcall = method_vectorcall_O;
        // break;
        // default:
        // throw new SystemError("%s() method: bad call flags",
        // method.name);
        // }

        this.callHandle = vectorcall;
    }

    PyMethodDescr(PyType objclass, MethodDef method) {
        this(TYPE, objclass, method);
    }

    // XXX Think I don't need: have Java constructor.
    // XXX But what does the vectorcall stuff turn into?
    // PyObject PyDescr_NewMethod(PyType type, MethodDef method)
    // {
    // /* Figure out correct vectorcall function to use */
    // MethodHandle vectorcall;
    // switch (method.flags & (METH_VARARGS | METH_FASTCALL |
    // METH_NOARGS | METH_O | METH_KEYWORDS))
    // {
    // case METH_VARARGS:
    // vectorcall = method_vectorcall_VARARGS;
    // break;
    // case METH_VARARGS | METH_KEYWORDS:
    // vectorcall = method_vectorcall_VARARGS_KEYWORDS;
    // break;
    // case METH_FASTCALL:
    // vectorcall = method_vectorcall_FASTCALL;
    // break;
    // case METH_FASTCALL | METH_KEYWORDS:
    // vectorcall = method_vectorcall_FASTCALL_KEYWORDS;
    // break;
    // case METH_NOARGS:
    // vectorcall = method_vectorcall_NOARGS;
    // break;
    // case METH_O:
    // vectorcall = method_vectorcall_O;
    // break;
    // default:
    // throw new SystemError(
    // "%s() method: bad call flags", method.name);
    // return null;
    // }
    //
    // PyMethodDescr descr = descr_new(PyMethodDescr.TYPE, type,
    // method.name);
    // if (descr != null) {
    // descr.d_method = method;
    // descr.vectorcall = vectorcall;
    // }
    // return descr;
    // }

    // CPython get-set table (to convert to annotations):
    // private GetSetDef method_getset[] = {
    // {"__doc__", (getter)method_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {"__text_signature__", (getter)method_get_text_signature},
    // {0}
    // };

    // CPython type object (to convert to special method names):
    // PyType PyMethodDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "method_descriptor",
    // sizeof(PyMethodDescr),
    // 0,
    // (destructor)descr_dealloc, /* tp_dealloc */
    // offsetof(PyMethodDescr, vectorcall), /* tp_vectorcall_offset
    // */
    // 0, /* tp_getattr */
    // 0, /* tp_setattr */
    // 0, /* tp_as_async */
    // (reprfunc)method_repr, /* tp_repr */
    // 0, /* tp_as_number */
    // 0, /* tp_as_sequence */
    // 0, /* tp_as_mapping */
    // 0, /* tp_hash */
    // PyVectorcall_Call, /* tp_call */
    // 0, /* tp_str */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // 0, /* tp_setattro */
    // 0, /* tp_as_buffer */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
    // _Py_TPFLAGS_HAVE_VECTORCALL |
    // Py_TPFLAGS_METHOD_DESCRIPTOR, /* tp_flags */
    // 0, /* tp_doc */
    // descr_traverse, /* tp_traverse */
    // 0, /* tp_clear */
    // 0, /* tp_richcompare */
    // 0, /* tp_weaklistoffset */
    // 0, /* tp_iter */
    // 0, /* tp_iternext */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // method_getset, /* tp_getset */
    // 0, /* tp_base */
    // 0, /* tp_dict */
    // (descrgetfunc)method_get, /* tp_descr_get */
    // 0, /* tp_descr_set */
    // };

    // Compare CPython method_repr in descrobject.c
    PyObject __repr__() { return descrRepr("method"); }

    /**
     * Invoke the instance as a vector call constructed from the classic
     * arguments.
     *
     * @throws Throwable from {@link #call(PyObject[], PyTuple)}
     */
    // Compare CPython PyVectorcall_Call in call.c
    @Override
    public PyObject __call__(PyTuple args, PyDict kwargs)
            throws Throwable {
        if (kwargs == null || kwargs.size() == 0) {
            return call(args.value);
        } else {
            PyObject[] a = args.value;
            PyObject[] stack = new PyObject[a.length + kwargs.size()];
            // XXX must order the names as declaration expects
            PyTuple kwnames = Callables.unpackDict(a, kwargs, stack);
            return call(stack, kwnames);
        }
    }

    // Compare CPython method_call in descrobject.c
    @Override
    public PyObject call(PyObject... args) throws Throwable {
        // XXX where do we deal with default arguments? Here?
        return (PyObject) callHandle.invokeExact(args);
    }

    /**
     * @implNote Currently not supporting keyword arguments.
     */
    @Override
    // Compare CPython method_call in descrobject.c
    public PyObject call(PyObject[] args, PyTuple kwnames)
            throws Throwable {
        if (kwnames == null || kwnames.size() == 0) {
            return (PyObject) callHandle.invokeExact(args);
        } else {
            throw new MissingFeature("Keywords in vector call");
        }
    }

    /**
     * Return the described method, bound to {@code obj} as its "self"
     * argument, or if {@code obj==null}, return this descriptor. In the
     * non-null case, {@code __get__} returns a {@link PyJavaMethod}.
     * Calling this invokes the Java method from the {@code MethodDef},
     * with the object as first argument, and other arguments to the
     * call appended.
     *
     * @param obj target (self) of the method, or {@code null}
     * @param type ignored
     * @return method bound to {@code obj} or this descriptor.
     */
    @Override
    // Compare CPython method_get in descrobject.c
    PyObject __get__(PyObject obj, PyType type) {
        if (obj == null)
            // Return the descriptor itself.
            return this;
        else {
            // Return a callable binding the method and the target
            check(obj);
            // return PyCFunction_NewEx(descr.d_method, obj, null);
            return new PyJavaMethod(methodDef, obj);
        }
    }

    // Compare CPython method_get_doc in descrobject.c
    PyObject get_doc() {
        return PyType.getDocFromInternalDoc(methodDef.name,
                methodDef.doc);
    }

    // Compare CPython method_get_text_signature in descrobject.c
    PyObject get_text_signature() {
        return PyType.getTextSignatureFromInternalDoc(methodDef.name,
                methodDef.doc);
    }

    /*
     * Vectorcall functions for each of the PyMethodDescr calling
     * conventions.
     */

    // CPython: typedef void (*funcptr)(void);

    /**
     * Examine the arguments to a vector call being made on this
     * descriptor as a <em>method</em> to verify that:
     * <nl>
     * <li>There is at least one argument, {@code self}.</li>
     * <li>{@code self}) is compatible in type with
     * {@link Descriptor#objclass}.</li>
     * <li>{@code kwnames}</li> is null or empty. (The caller knows
     * whether this should be enforced and may pass a literal
     * {@code null}.)
     * </nl>
     *
     * @param stack self, positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments
     * @param kwnames names of keyword arguments or {@code null}.
     * @throws TypeError for mis-match with expectations
     * @throws Throwable propagated from __subclasscheck__ or other
     *             causes
     */
    // Compare CPython method_check_args in descrobject.c
    void check_args(PyObject[] stack, int start, int nargs,
            PyTuple kwnames) throws TypeError, Throwable {
        // Must have a first argument
        if (nargs < 1) {
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT,
                    methodDef.name, objclass.name);
        }
        // The first argument is the target as a method call
        PyObject self = stack[start];
        PyType selfType = self.getType();
        if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, methodDef.name,
                    objclass.name, selfType.name);
        }
        if (kwnames != null && kwnames.size() != 0) {
            throw new TypeError("%.200s() takes no keyword arguments",
                    methodDef.name);
        }
    }

    /* Now the actual vectorcall functions */
    // Compare CPython method_vectorcall_VARARGS in descrobject.c
    PyObject vectorcall_VARARGS(PyObject[] stack, int start, int nargs,
            PyTuple kwnames) throws Throwable {
        check_args(stack, start, nargs, kwnames);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            PyObject argstuple =
                    new PyTuple(stack, start + 1, nargs - 1);
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[0], argstuple);
        }
    }

    // Compare CPython method_vectorcall_VARARGS_KEYWORDS in
    // descrobject.c
    PyObject vectorcall_VARARGS_KEYWORDS(PyObject[] stack, int start,
            int nargs, PyTuple kwnames) throws TypeError, Throwable {
        check_args(stack, start, nargs, null);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            PyObject argstuple = new PyTuple(stack, 1, nargs - 1);
            // Create a temporary dict for keyword arguments
            PyDict kwargs =
                    Callables.stackAsDict(stack, start, nargs, kwnames);
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[0], argstuple,
                    kwargs);
        }
    }

    // Compare CPython method_vectorcall_FASTCALL in descrobject.c
    PyObject vectorcall_FASTCALL(PyObject[] stack, int start, int nargs,
            PyTuple kwnames) throws TypeError, Throwable {
        check_args(stack, start, nargs, kwnames);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[0], stack, 1,
                    nargs - 1);
        }
    }

    // Compare CPython method_vectorcall_FASTCALL_KEYWORDS in
    // descrobject.c
    PyObject vectorcall_FASTCALL_KEYWORDS(PyObject[] stack, int start,
            int nargs, PyTuple kwnames) throws Throwable {
        check_args(stack, start, nargs, null);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[0], stack, 1,
                    nargs - 1, kwnames);
        }
    }

    /**
     * The descriptor is for a method that takes no arguments at the
     * call site. The argument array slice contains only {@code self}.
     *
     * @param stack self, positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments, should be 1
     * @param kwnames names of keyword arguments must be empty or null
     * @return the return from the call to {@code this.meth}
     */
    // Compare CPython method_vectorcall_NOARGS in descrobject.c
    PyObject vectorcall_NOARGS(PyObject[] stack, int start, int nargs,
            PyTuple kwnames) throws Throwable {
        check_args(stack, start, nargs, kwnames);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            if (nargs != 1) {
                throw new TypeError(
                        "%.200s() takes no arguments (%zd given)",
                        methodDef.name, nargs - 1);
            }
            // Invoke with just the target
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[start]);
        }
    }

    /**
     * The descriptor is for a method that takes exactly one argument at
     * the call site. The argument array slice contains only
     * {@code self, arg}.
     *
     * @param stack self, positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments, should be 2
     * @param kwnames names of keyword arguments must be empty or null
     * @return the return from the call to {@code this.meth}
     */
    // Compare CPython method_vectorcall_O in descrobject.c
    PyObject vectorcall_O(PyObject[] stack, int start, int nargs,
            PyTuple kwnames) throws Throwable {
        check_args(stack, start, nargs, kwnames);
        try (RecursionState r = ThreadState
                .enterRecursiveCall(" while calling a Python object")) {
            if (nargs != 2) {
                throw new TypeError(
                        "%.200s() takes exactly one argument (%zd given)",
                        methodDef.name, nargs - 1);
            }
            // Invoke with the target and one argument
            MethodHandle meth = methodDef.meth;
            return (PyObject) meth.invokeExact(stack[0], stack[1]);
        }
    }

}
