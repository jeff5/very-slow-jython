package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.LinkedList;
import java.util.ListIterator;

import uk.co.farowl.vsj3.evo1.Exposed.KeywordOnly;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PositionalOnly;
import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.Slot.Signature.ArgumentError;

/**
 * Descriptor for a method defined in Java, specified by a
 * {@link MethodDef}. A {@code PyMethodDescr} is a callable object
 * itself, and provides binding behaviour through
 * {@link #__get__(Object, PyType) __get__}, which usually creates a
 * {@link PyJavaMethod}.
 */
abstract class PyMethodDescr extends Descriptor {

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
    abstract MethodHandle getMethod(Class<?> selfClass);

    /**
     * A {@link PyMethodDescr} to use for an instance method when the
     * owning Python type has just one accepted implementation.
     */
    static class Single extends PyMethodDescr {

        /**
         * A handle for the particular implementation of a method. In an
         * instance method, {@link #method} has type {@code (O, O[])O},
         * or {@code (O)O}.
         */
        protected final MethodHandle method;

        /**
         * Construct a method descriptor, identifying by a method handle
         * the implementation method in {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param method a handle to an implementation of that slot
         */
        // Compare CPython PyDescr_NewMethod in descrobject.c
        Single(PyType objclass, MethodDef methodDef,
                MethodHandle method) {
            super(objclass, methodDef);
            this.method = method;
        }

        @Override
        MethodHandle getMethod(Class<?> selfClass) {
            // Make sure that the first argument is acceptable as 'self'
            if (objclass.getJavaClass().isAssignableFrom(selfClass))
                return method;
            else
                // XXX Implement
                return null;
        }
    }

    /**
     * A {@link PyMethodDescr} to use for an instance method when the
     * owning Python type has multiple accepted implementation.
     */
    static class Multiple extends PyMethodDescr {

        /**
         * Handles for the particular implementations of a special
         * method being method. The method type of each is the same. In
         * an instance method, {@link #method} entries have type
         * {@code (O, O[])O}, or {@code (O)O}.
         */
        protected final MethodHandle[] method;

        /**
         * Construct a slot wrapper descriptor, identifying by an array
         * of method handles the implementation methods for the
         * {@code slot} in {@code objclass}.
         *
         * @param objclass the class declaring the special method
         *
         * @param method handles to the implementation of that slot
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
        MethodHandle getMethod(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return method[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                // XXX Implement
                return null;
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
     * @param kwargs of the method call
     * @return result of the method call
     * @throws TypeError if the arguments do not fit the method
     * @throws Throwable from the implementation of the method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    Object callWrapped(Object self, PyTuple args, PyDict kwargs)
            throws Throwable {
        try {
            // Call through the correct wrapped handle
            MethodHandle wrapped = getMethod(self.getClass());
            return methodDef.callMethod(wrapped, self, args, kwargs);
        } catch (ArgumentError ae) {
            throw signatureTypeError(ae, args);
        }
    }

    // Compare CPython method_call in descrobject.c
    public Object call(Object... args) throws Throwable {
        // XXX Implement
        return null;
    }

    /**
     * @implNote Currently not supporting keyword arguments.
     */
    public Object call(Object[] args, PyTuple kwnames)
            throws Throwable {
        if (kwnames == null || kwnames.size() == 0) {
            // XXX Implement
            return null;
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
    Object __get__(Object obj, PyType type) {
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
            throw new TypeError("%.200s() takes no keyword arguments",
                    methodDef.name);
        }
    }

    /**
     * Translate a problem with the number and pattern of arguments, in
     * a failed attempt to call the wrapped method, to a Python
     * {@link TypeError}.
     *
     * @param ae expressing the problem
     * @param args positional arguments (only the number will matter)
     * @return a {@code TypeError} to throw
     */
    private TypeError signatureTypeError(ArgumentError ae,
            PyTuple args) {
        int n = args.value.length;
        switch (ae.mode) {
            case NOARGS:
                return new TypeError(TAKES_NO_ARGUMENTS, name, n);
            case NUMARGS:
                int N = ae.minArgs;
                return new TypeError(TAKES_ARGUMENTS, name, N, n);
            case MINMAXARGS:
                String range = String.format("from %d to %d",
                        ae.minArgs, ae.maxArgs);
                return new TypeError(TAKES_ARGUMENTS, name, range, n);
            case NOKWARGS:
            default:
                return new TypeError(TAKES_NO_KEYWORDS, name);
        }
    }

    private static final String TAKES_NO_ARGUMENTS =
            "method %s() takes no arguments (%d given)";
    private static final String TAKES_ARGUMENTS =
            "method %s() takes %s arguments (%d given)";
    private static final String TAKES_NO_KEYWORDS =
            "method %s() takes no keyword arguments";

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

    /**
     * {@code MethodDef} represents one or more methods of a Java class
     * that are to be exposed as a single method of a Python
     * {@code object}. The exporting class provides definitions for the
     * method, that appear here as {@code java.lang.reflect.Method}s
     * with different signatures.
     */
    static class MethodSpecification extends DescriptorSpecification {

        /** The name of the method being defined */
        final String name;

        /**
         * Names of arguments not including the {@code self} argument of
         * instance methods. (The names are the arguments to the method
         * in the first call to {@link #add(Method)}).
         */
        String[] argumentNames;

        /**
         * The number of positional-only arguments (after {@code self}).
         * (The value reflects the method in the first call to
         * {@link #add(Method)}).
         */
        int posonlyargcount;

        /**
         * The number of keyword-only arguments. (The value reflects the
         * method in the first call to {@link #add(Method)}).
         */
        int kwonlyargcount;

        boolean isStatic;

        /** @param name of method. */
        MethodSpecification(String name) {
            this.name = name;
        }

        /**
         * Add a method implementation. (A test that the signature is
         * acceptable follows when we construct the
         * {@link PyMethodDescr}.)
         *
         * @param method to add to {@link #methods}
         */
        @Override
        void add(Method method) throws InterpreterError {
            boolean first = methods.isEmpty();
            super.add(method);

            // Check for defined static (in Java)
            int modifiers = method.getModifiers();
            boolean javaStatic = (modifiers & Modifier.STATIC) != 0;

            // Skip "self" that will appear first if static in Java
            int n = method.getParameterCount() - (javaStatic ? 1 : 0);

            if (first) {
                // First encounter defines the signature
                argumentNames = new String[n];
                // Skip an initial "self" argument name
                int i = javaStatic ? -1 : 0;
                // Capture arg names: must compile with -parameters.
                for (Parameter p : method.getParameters()) {
                    if (i >= 0) {
                        argumentNames[i] = processAnnotations(p, i);
                    }
                    i += 1;
                }

            } else if (n != argumentNames.length) {
                throw new InterpreterError(FURTHER_DEF_ARGS,
                        method.getName(), n, argumentNames.length);
            }
        }

        private static final String FURTHER_DEF_ARGS =
                "Further definition of '%s' has %d (not %d) arguments";

        /**
         * Process the <i>i</i> th parameter for its
         * annotations @{@link Name},
         *
         * @{@link PositionalOnly}, and @{@link KeywordOnly}.
         * @param p to process
         * @param i index in {@link #argumentNames}
         * @return the name of the parameter
         */
        private String processAnnotations(Parameter p, int i) {

            // Parameter may be the last positional-only argument (/)
            PositionalOnly pos = p.getAnnotation(PositionalOnly.class);
            if (pos != null) { posonlyargcount = i + 1; }

            // Parameter may be the first keyword-only argument (*)
            KeywordOnly kwd = p.getAnnotation(KeywordOnly.class);
            if (kwd != null && kwonlyargcount == 0) {
                kwonlyargcount = argumentNames.length - i;
            }

            Name name = p.getAnnotation(Name.class);
            return name == null ? p.getName() : name.value();
        }

        @Override
        PyMethodDescr createDescr(PyType type, Lookup lookup) {

            // XXX Create a MethodDef and work from that?

            if (isStatic) {
                throw new MissingFeature("expose method as static");
            } else {
                return createDescrForInstanceMethod(type, lookup);
            }
        }

        @Override
        String getType() { return "JavaMethod"; }

        /**
         * Create a {@code PyMethodDescr} from this specification. Note
         * that a specification describes the methods as declared, and
         * that there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyMethodDescr createDescrForInstanceMethod(
                PyType objclass, Lookup lookup)
                throws InterpreterError {

            // Methods have self + this many args:
            final int L = argumentNames.length;

            // Specialise the MethodDef according to the signature.
            String doc = getDoc();
            MethodDef methodDef;
            switch (L) {
                case 0:
                    // MT = (S)O
                    methodDef = new MethodDef.NoArgs(name, doc);
                    break;
                case 1:
                    // MT = (S,O)O
                    methodDef = new MethodDef.OneArg(name,
                            argumentNames, doc);
                    break;
                default:
                    // MT = (S,O[])O
                    methodDef = new MethodDef.FixedArgs(name,
                            argumentNames, doc);
            }

            /*
             * There could be any number of candidates in the
             * implementation. An implementation method could match
             * multiple accepted implementations of the type (e.g.
             * Number matching Long and Integer).
             */
            LinkedList<MethodHandle> candidates = new LinkedList<>();
            for (Method m : methods) {
                // Convert m to a handle (if L args and accessible)
                try {
                    MethodHandle mh = lookup.unreflect(m);
                    if (mh.type().parameterCount() == 1 + L)
                        addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw cannotGetHandle(objclass, m, e);
                }
            }

            /*
             * We will try to create a handle for each implementation of
             * an instance method.
             */
            final int N = objclass.acceptedCount;
            MethodHandle[] method = new MethodHandle[N];

            // Fill the method array with matching method handles
            for (int i = 0; i < N; i++) {
                Class<?> acceptedClass = objclass.classes[i];
                /*
                 * Fill method[i] with the method handle where the first
                 * parameter is the most specific match for class
                 * accepted[i].
                 */
                // Try the candidate method until one matches
                for (MethodHandle mh : candidates) {
                    MethodType mt = mh.type();
                    if (mt.parameterType(0)
                            .isAssignableFrom(acceptedClass)) {
                        /*
                         * Each sub-type of MethodDef handles
                         * callMethod(self, args, kwargs) in its own
                         * way, and must prepare the arguments of the
                         * generic method handle to match.
                         */
                        try {
                            method[i] = methodDef.prepare(mh);
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(mh);
                        }
                        break;
                    }
                }

                // We should have a value in each of method[]
                if (method[i] == null) {
                    throw new InterpreterError(
                            "'%s.%s' not defined for %s", objclass.name,
                            name, objclass.classes[i]);
                }
            }

            if (N == 1)
                /*
                 * There is only one definition so use the simpler form
                 * of built-in method. This is the frequent case.
                 */
                return new PyMethodDescr.Single(objclass, methodDef,
                        method[0]);

            else
                /*
                 * There are multiple definitions so use the array form
                 * of built-in method. This is the case for types that
                 * have multiple accepted implementations and methods on
                 * them that are not static or "Object self".
                 */
                return new PyMethodDescr.Multiple(objclass, methodDef,
                        method);
        }

        /**
         * Insert a {@code MethodHandle h} into a list, such that every
         * handle in the list, of which the first parameter type is
         * assignable from the first parameter type of {@code h}, will
         * appear after {@code h} in the list. If there are none such,
         * {@code h} is added at the end. The resulting list is
         * partially ordered, and has the property that, in a forward
         * search for a handle applicable to a given class, the most
         * specific match is found first.
         *
         * @param list to add h into
         * @param h to insert/add
         */
        private void addOrdered(LinkedList<MethodHandle> list,
                MethodHandle h) {
            // Type of first parameter of h
            Class<?> c = h.type().parameterType(0);
            // We'll scan until a more general type is found
            ListIterator<MethodHandle> iter = list.listIterator(0);
            while (iter.hasNext()) {
                MethodHandle i = iter.next();
                Class<?> d = i.type().parameterType(0);
                if (d.isAssignableFrom(c)) {
                    /*
                     * d is more general than c (i is more general than
                     * h): back up and position just before i.
                     */
                    iter.previous();
                    break;
                }
            }
            // Insert h where the iterator stopped. Could be the end.
            iter.add(h);
        }

        /** Convenience function to compose error in createDescr(). */
        private InterpreterError cannotGetHandle(PyType objclass,
                Method m, IllegalAccessException e) {
            return new InterpreterError(e,
                    "cannot get method handle for '%s' in '%s'", m,
                    objclass.definingClass);
        }

        /** Convenience function to compose error in createDescr(). */
        private InterpreterError methodSignatureError(MethodHandle mh) {
            return new InterpreterError(UNSUPPORTED_SIG, name,
                    mh.type());
        }

        private static final String UNSUPPORTED_SIG =
                "method %.50s has wrong signature %.50s for spec";

        @Override
        public String toString() {
            return String.format("MethodSpec(%s[%d])", name,
                    methods.size());
        }

    }
}
