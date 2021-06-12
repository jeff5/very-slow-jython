package uk.co.farowl.vsj3.evo1;

import java.lang.annotation.Annotation;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.StringJoiner;

import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.Exposed.DocString;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.Exposed.KeywordCollector;
import uk.co.farowl.vsj3.evo1.Exposed.KeywordOnly;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalCollector;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.PyType.Flag;

/**
 * Descriptor for a method defined in Java, specified by a
 * {@link MethodDef}. A {@code PyMethodDescr} is a callable object
 * itself, and provides binding behaviour through
 * {@link #__get__(Object, PyType) __get__}, which usually creates a
 * {@link PyJavaMethod}.
 */
/*
 * We differ from CPython in holding the MethodHandle of the wrapped
 * method(s) directly in the PyMethodDescr, rather than in the
 * MethodDef. This is similar to how PyWrapperDescr works. Here as
 * there, it suits us to sub-class PyMethodDescr to express the
 * multiplicity of implementations, and to sub-class MethodDef to
 * express the signature of the method, and its data flow to arguments.
 */
abstract class PyMethodDescr extends MethodDescriptor {

    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("method_descriptor", MethodHandles.lookup())
                    .flagNot(Flag.BASETYPE));

    /** Description of the method as defined in Java. */
    final MethodDef methodDef;

    /**
     * Construct a {@code PyMethodDescr} from its {@link MethodDef}.
     *
     * @param objclass owning class
     * @param method definition (provides name etc.)
     */
    // Compare CPython PyDescr_NewMethod in descrobject.c
    PyMethodDescr(PyType objclass, MethodDef method) {
        super(TYPE, objclass, method.name);
        this.methodDef = method;
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
     * A {@link PyMethodDescr} to use for an instance method when the
     * owning Python type has just one accepted implementation.
     */
    static class Single extends PyMethodDescr {

        /**
         * Construct a method descriptor, identifying by a method handle
         * the implementation method in {@code objclass}.
         *
         * @param objclass the class declaring the method
         * @param methodDef describing the signature of the method
         */
        // Compare CPython PyDescr_NewMethod in descrobject.c
        Single(PyType objclass, MethodDef methodDef) {
            super(objclass, methodDef);
        }

        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // The first argument is acceptable as 'self'
            assert objclass.getJavaClass().isAssignableFrom(selfClass);
            return methodDef.meth;
        }
    }

    /**
     * A {@link PyMethodDescr} to use for an instance method when the
     * owning Python type has multiple accepted implementation.
     */
    static class Multiple extends PyMethodDescr {

        /**
         * Handles for the particular implementations of a special
         * method. The method type of each is the same. In an instance
         * method, {@link #method} entries have type {@code (O, O[])O},
         * or {@code (O)O}.
         */
        // Compare CPython PyMethodDescrObject::vectorcall
        protected final MethodHandle[] method;

        /**
         * Construct a method descriptor, identifying by an array of
         * method handles the implementation methods in
         * {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param methodDef describing the signature of the method
         * @param method handles to the implementation of that method
         */
        // Compare CPython PyDescr_NewMethod in descrobject.c
        Multiple(PyType objclass, MethodDef methodDef,
                MethodHandle[] method) {
            super(objclass, methodDef);
            this.method = method;
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
                return method[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                // This will behave as an empty slot
                return methodDef.meth;
            }
        }
    }

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
    // offsetof(PyMethodDescr, vectorcall), /* tp_vectorcall_offset */
    // (reprfunc)method_repr, /* tp_repr */
    // PyVectorcall_Call, /* tp_call */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
    // _Py_TPFLAGS_HAVE_VECTORCALL |
    // Py_TPFLAGS_METHOD_DESCRIPTOR, /* tp_flags */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // method_getset, /* tp_getset */
    // (descrgetfunc)method_get, /* tp_descr_get */
    // };

    // Compare CPython method_repr in descrobject.c
    Object __repr__() {
        return descrRepr("method");
    }

    /**
     * Invoke the Java method this method descriptor points to, using
     * arguments derived from the classic call arguments supplied,
     * default arguments and other information described for the method.
     *
     * Call the wrapped method with positional arguments (the first
     * being the target object) and optionally keywords arguments. The
     * arguments, in type and number, must match the signature of the
     * special function slot.
     *
     * @param args positional arguments beginning with {@code self}
     * @param kwargs keyword arguments
     * @return result of calling the wrapped method
     * @throws TypeError if {@code args[0]} is the wrong type
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_call in descrobject.c
    protected Object __call__(PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {

        int argc = args.value.length;
        if (argc > 0) {
            // Split the leading element self from args
            Object self = args.value[0];
            if (argc == 1) {
                args = PyTuple.EMPTY;
            } else {
                args = new PyTuple(args.value, 1, argc - 1);
            }

            // Make sure that the first argument is acceptable as 'self'
            PyType selfType = PyType.of(self);
            if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
                throw new TypeError(DESCRIPTOR_REQUIRES, name,
                        objclass.name, selfType.name);
            }

            return callWrapped(self, args, kwargs);

        } else {
            // Not even one argument
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.name);
        }
    }

    /**
     * Invoke the method described by this {@code PyMethodDescr} the
     * given target {@code self}, and the arguments supplied.
     *
     * @param self target object of the method call
     * @param args of the method call
     * @param kwargs of the method call (may be {@code null} if empty)
     * @return result of the method call
     * @throws TypeError if the arguments do not fit the method
     * @throws Throwable from the implementation of the method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    Object callWrapped(Object self, PyTuple args, PyDict kwargs)
            throws Throwable {
        try {
            // Call through the correct wrapped handle
            MethodHandle wrapped = getWrapped(self.getClass());
            return methodDef.callMethod(wrapped, self, args, kwargs);
        } catch (MethodDescriptor.ArgumentError ae) {
            throw signatureTypeError(ae, args);
        }
    }

    // Compare CPython method_call in descrobject.c
    public Object call(Object self, Object... args) throws Throwable {
        return call(self, args, null);
    }

    /**
     * Call with vector semantics: an array of all the argument values
     * (after the self argument) and a tuple of the names of those given
     * by keyword.
     *
     * @param self target object of the method call
     * @param args arguments of the method call
     * @param kwnames tuple of names (may be {@code null} if empty)
     * @return result of the method call
     * @throws TypeError if the arguments do not fit the method
     * @throws Throwable from the implementation of the method
     */
    public Object call(Object self, Object[] args, PyTuple kwnames)
            throws Throwable {

        if (self == null) {
            // Not even the self argument
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.name);

        } else {
            // Manage the argument vector and names into classic form
            /*
             * XXX We wouldn't want to do this long-term, unless the
             * ultimate receiving method has a classic signature,
             * because the vector form of the call allows optimisation.
             */
            PyTuple argTuple;
            PyDict kwargs;

            if (args == null || args.length == 0) {
                // No arguments (easy)
                argTuple = PyTuple.EMPTY;
                kwargs = null;
            } else if (kwnames == null) {
                // No keyword arguments
                argTuple = new PyTuple(args);
                kwargs = null;
            } else {
                // Args given by position and keyword
                int pos = args.length - kwnames.size();
                kwargs = Callables.stackAsDict(args, 0, pos, kwnames);
                argTuple = new PyTuple(args, 0, pos);
            }

            // Make sure that the first argument is acceptable as 'self'
            PyType selfType = PyType.of(self);

            if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
                throw new TypeError(DESCRIPTOR_REQUIRES, name,
                        objclass.name, selfType.name);
            }
            return callWrapped(self, argTuple, kwargs);
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
    Object __get__(Object obj, PyType type) {
        if (obj == null)
            // Return the descriptor itself.
            return this;
        else {
            // Return a callable binding the method and the target
            check(obj);
            // return PyCFunction_NewEx(descr.d_method, obj, null);
            // Match the handle to the actual class of obj
            MethodHandle mh = getWrapped(obj.getClass());
            return new PyJavaMethod(methodDef, mh, obj, null);
        }
    }

    // Compare CPython method_get_doc in descrobject.c
    Object get_doc() {
        return PyType.getDocFromInternalDoc(methodDef.name,
                methodDef.doc);
    }

    // Compare CPython method_get_text_signature in descrobject.c
    Object get_text_signature() {
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
     * <ol>
     * <li>There is at least one argument, {@code self}.</li>
     * <li>{@code self}) is compatible in type with
     * {@link Descriptor#objclass}.</li>
     * <li>{@code kwnames} is null or empty. (The caller knows whether
     * this should be enforced and may pass a literal
     * {@code null}.)</li>
     * </ol>
     *
     * @param stack self, positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments
     * @param kwnames names of keyword arguments or {@code null}.
     * @throws TypeError for mis-match with expectations
     * @throws Throwable propagated from __subclasscheck__ or other
     *     causes
     */
    // Compare CPython method_check_args in descrobject.c
    void check_args(Object[] stack, int start, int nargs,
            PyTuple kwnames) throws TypeError, Throwable {
        // Must have a first argument
        if (nargs < 1) {
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT,
                    methodDef.name, objclass.name);
        }
        // The first argument is the target as a method call
        Object self = stack[start];
        PyType selfType = PyType.of(self);
        if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
            throw new TypeError(DESCRIPTOR_DOESNT_APPLY, methodDef.name,
                    objclass.name, selfType.name);
        }
        if (kwnames != null && kwnames.size() != 0) {
            throw new TypeError(TAKES_NO_KEYWORDS, methodDef.name);
        }
    }

// /* Now the actual vectorcall functions */
// // Compare CPython method_vectorcall_VARARGS in descrobject.c
// Object vectorcall_VARARGS(Object[] stack, int start, int nargs,
// PyTuple kwnames) throws Throwable {
// check_args(stack, start, nargs, kwnames);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// Object argstuple =
// new PyTuple(stack, start + 1, nargs - 1);
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[0], argstuple);
// }
// }
//
// // Compare CPython method_vectorcall_VARARGS_KEYWORDS in
// // descrobject.c
// Object vectorcall_VARARGS_KEYWORDS(Object[] stack, int start,
// int nargs, PyTuple kwnames) throws TypeError, Throwable {
// check_args(stack, start, nargs, null);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// Object argstuple = new PyTuple(stack, 1, nargs - 1);
// // Create a temporary dict for keyword arguments
// PyDict kwargs =
// Callables.stackAsDict(stack, start, nargs, kwnames);
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[0], argstuple,
// kwargs);
// }
// }
//
// // Compare CPython method_vectorcall_FASTCALL in descrobject.c
// Object vectorcall_FASTCALL(Object[] stack, int start, int nargs,
// PyTuple kwnames) throws TypeError, Throwable {
// check_args(stack, start, nargs, kwnames);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[0], stack, 1,
// nargs - 1);
// }
// }
//
// // Compare CPython method_vectorcall_FASTCALL_KEYWORDS in
// // descrobject.c
// Object vectorcall_FASTCALL_KEYWORDS(Object[] stack, int start,
// int nargs, PyTuple kwnames) throws Throwable {
// check_args(stack, start, nargs, null);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[0], stack, 1,
// nargs - 1, kwnames);
// }
// }
//
// /**
// * The descriptor is for a method that takes no arguments at the
// * call site. The argument array slice contains only {@code self}.
// *
// * @param stack self, positional and keyword arguments
// * @param start position of arguments in the array
// * @param nargs number of positional arguments, should be 1
// * @param kwnames names of keyword arguments must be empty or null
// * @return the return from the call to {@code this.meth}
// * @throws Throwable from invoked implementation.
// */
// // Compare CPython method_vectorcall_NOARGS in descrobject.c
// Object vectorcall_NOARGS(Object[] stack, int start, int nargs,
// PyTuple kwnames) throws Throwable {
// check_args(stack, start, nargs, kwnames);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// if (nargs != 1) {
// throw new TypeError(
// "%.200s() takes no arguments (%zd given)",
// methodDef.name, nargs - 1);
// }
// // Invoke with just the target
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[start]);
// }
// }
//
// /**
// * The descriptor is for a method that takes exactly one argument at
// * the call site. The argument array slice contains only
// * {@code self, arg}.
// *
// * @param stack self, positional and keyword arguments
// * @param start position of arguments in the array
// * @param nargs number of positional arguments, should be 2
// * @param kwnames names of keyword arguments must be empty or null
// * @return the return from the call to {@code this.meth}
// * @throws Throwable from invoked implementation.
// */
// // Compare CPython method_vectorcall_O in descrobject.c
// Object vectorcall_O(Object[] stack, int start, int nargs,
// PyTuple kwnames) throws Throwable {
// check_args(stack, start, nargs, kwnames);
// try (RecursionState r = ThreadState
// .enterRecursiveCall(" while calling a Python object")) {
// if (nargs != 2) {
// throw new TypeError(
// "%.200s() takes exactly one argument (%zd given)",
// methodDef.name, nargs - 1);
// }
// // Invoke with the target and one argument
// MethodHandle meth = methodDef.meth;
// return meth.invokeExact(stack[0], stack[1]);
// }
// }

}
