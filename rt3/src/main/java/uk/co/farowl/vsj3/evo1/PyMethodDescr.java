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
import uk.co.farowl.vsj3.evo1.Exposed.JavaMethod;
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
         * A handle for the particular implementation of a method. In an
         * instance method, {@link #method} has type {@code (O, O[])O},
         * or {@code (O)O}.
         */
        // Compare CPython PyMethodDescrObject::vectorcall
        protected final MethodHandle method;

        /**
         * Construct a method descriptor, identifying by a method handle
         * the implementation method in {@code objclass}.
         *
         * @param objclass the class declaring the method
         * @param methodDef describing the signature of the method
         * @param method a handle to an implementation of that method
         */
        // Compare CPython PyDescr_NewMethod in descrobject.c
        Single(PyType objclass, MethodDef methodDef,
                MethodHandle method) {
            super(objclass, methodDef);
            this.method = method;
        }

        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // Make sure that the first argument is acceptable as 'self'
            if (objclass.getJavaClass().isAssignableFrom(selfClass))
                return method;
            else
                // XXX Implement empty slot handle
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
                // XXX Implement empty slot handle
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
             * because the vector form of the call allow optimisations.
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

    /**
     * {@code MethodDef} represents one or more methods of a Java class
     * that are to be exposed as a single method of a Python
     * {@code object}. The exporting class provides definitions for the
     * method, that appear here as {@code java.lang.reflect.Method}s
     * with different signatures.
     */
    static class MethodSpecification extends DescriptorSpecification {

        /** The Python name of the method being defined. */
        final String name;

        /**
         * Names of parameters not including the {@code self} of
         * instance methods. (The names are the parameters to the method
         * in the first call to {@link #add(Method)}).
         */
        String[] parameterNames;

        /**
         * The number of positional or keyword parameters, excluding the
         * "collector" ({@code *args} and {@code **kwargs}) arguments.
         * Its value is {@code Integer.MAX_VALUE} until the primary
         * definition of the method has been encountered.
         */
        int regargcount = Integer.MAX_VALUE;

        /**
         * The number of positional-only arguments (after {@code self}).
         * This must be specified in the method declaration marked as
         * primary if more than one declaration of the same name is
         * annotated {@link JavaMethod}. Its value is
         * {@code Integer.MAX_VALUE} until the primary definition of the
         * method has been encountered, after which it is somewhere
         * between 0 and {@link #regargcount} inclusive.
         */
        int posonlyargcount = Integer.MAX_VALUE;

        /**
         * The number of keyword-only parameters (equals the number of
         * positional parameters. This is derived from the
         * {@link KeywordOnly} annotation. If more than one declaration
         * of the same name is annotated {@link JavaMethod}, it must be
         * specified in the method declaration marked as primary.
         */
        int kwonlyargcount;

        /**
         * Default values supplied on positional parameters (not just
         * positional-only parameters), or {@code null}.
         */
        Object[] defaults = null;

        /**
         * Default values supplied on keyword-only parameters, or
         * {@code null}.
         */
        Map<Object, Object> kwdefaults = null;

        /**
         * Position of the excess positional collector in
         * {@link #parameterNames} or {@code -1} if there isn't one.
         */
        int varArgsIndex = -1;

        /**
         * Position of the excess keywords collector in
         * {@link #parameterNames} or {@code -1} if there isn't one.
         */
        int varKeywordsIndex = -1;

        /** This is a static method (from a Python perspective). */
        boolean isStatic;

        /** Empty names array. */
        private static final String[] NO_STRINGS = new String[0];

        /** @param name of method. */
        MethodSpecification(String name) {
            this.name = name;
        }

        /**
         * Check that {@link #processParameters(Method, boolean)} has
         * been called for a primary definition.
         */
        boolean isDefined() {
            return parameterNames != null
                    && regargcount <= parameterNames.length;
        }

        /**
         * @return true if positional argument collector defined.
         */
        boolean hasVarArgs() {
            return varArgsIndex >= 0;
        }

        /**
         * @return true if keyword argument collector defined.
         */
        boolean hasVarKeywords() {
            return varKeywordsIndex >= 0;
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

            JavaMethod anno = method.getAnnotation(JavaMethod.class);
            boolean primary = anno.primary();

            // Check for defined static (in Java, not Python)
            int modifiers = method.getModifiers();
            boolean javaStatic = (modifiers & Modifier.STATIC) != 0;

            // Skip "self" that will appear first if static in Java
            int n = method.getParameterCount() - (javaStatic ? 1 : 0);

            if (first) {
                /*
                 * Allocate storage for argument names. We shall store
                 * the names only if this is also the primary
                 * definition, (as well as the first), but will always
                 * check the number of parameters against this size.
                 */
                parameterNames = n == 0 ? NO_STRINGS : new String[n];

            } else if (n != parameterNames.length) {
                // Number of arguments differs.
                // XXX Is it correct to check the types (after self)?
                throw new InterpreterError(FURTHER_DEF_ARGS,
                        getJavaMethodName(), n, parameterNames.length);
            }

            if (primary) {
                // Primary definition defines the signature
                if (isDefined())
                    throw new InterpreterError(ONE_PRIMARY,
                            getJavaMethodName());
                /*
                 * If annotated positionalOnly=false, the method has no
                 * positional-only parameters. if not so annotated, then
                 * positionalOnly=true, and all arguments are
                 * positional-only (until an annotation of a parameter
                 * with @PositionalOnly puts an end to that).
                 */
                if (!anno.positionalOnly()) { posonlyargcount = 0; }

                // THere may be a @DocString annotation
                DocString docAnno =
                        method.getAnnotation(DocString.class);
                if (docAnno != null) { doc = docAnno.value(); }

                /*
                 * Process the sequence of parameters and their
                 * annotations.
                 */
                processParameters(method, javaStatic);

            } else {
                // This is not the primary definition
                disallowAnnotation(method, DocString.class);
                for (Parameter p : method.getParameters()) {
                    disallowAnnotations(p);
                }
            }
        }

        private static final String FURTHER_DEF_ARGS =
                "Further definition of '%s' has %d (not %d) arguments";

        private static final String ONE_PRIMARY =
                "All but one definition of '%s' should have "
                        + "element primary=false";

        /**
         * Scan the parameters of the method being defined looking for
         * annotations that determine the specification of the method as
         * exposed to Python, and which are held temporarily by this
         * {@code MethodSpecification}. Although the annotations do not
         * all work in isolation, their effect may be summarised:
         * <table>
         * <tr>
         * <th>Annotation</th>
         * <th>Effect on fields</th>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link Name}</td>
         * <td>Renames the parameter where needed (e.g. we want to call
         * it "new"). This, or the simple parameter name, appear in at
         * the correct position in {@link #parameterNames}</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link Default}</td>
         * <td>Provides the default value in {@link #defaults} or
         * {@link #kwdefaults}.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link PositionalOnly}</td>
         * <td>Sets {@link #posonlyargcount} to that parameter.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link KeywordOnly}</td>
         * <td>Determines {@link #kwonlyargcount} from a count of this
         * and the regular (non-collector) arguments following.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link PositionalCollector}</td>
         * <td>Designates the collector of excess arguments given by
         * position. (Must follow all regular arguments.) Sets
         * {@link #haveVarargs}.</td>
         * </tr>
         *
         * <tr>
         * <td>&#064;{@link KeywordCollector}</td>
         * <td>Designates the collector of excess arguments given by
         * keyword. (Must follow all regular arguments and any
         * positional collector.) Sets {@link #haveVarkwargs}.</td>
         * </tr>
         * </table>
         *
         * @param method being defined
         * @param skip if {@code true} skip the first parameter
         */
        private void processParameters(Method method, boolean skip) {
            /*
             * This should have the same logic as
             * ArgParser.fromSignature, except that in the absence of
             * a @PositionalOnly annotation, the default is as supplied
             * by the method annotation (already processed). Rather than
             * "/" and "*" markers in the parameter sequence, we find
             * annotations on the parameters themselves.
             */

            // Collect the names of the arguments here
            ArrayList<String> paramNames = new ArrayList<>();

            // Count regular (non-collector) parameters
            int count = 0;

            // Collect the default values here
            ArrayList<Object> posDefaults = null;

            // Indices of specific markers
            // int posOnlyIndex = Integer.MAX_VALUE;
            int kwOnlyIndex = Integer.MAX_VALUE;

            /*
             * Scan parameters, looking out for Name, Default,
             * PositionalOnly, KeywordOnly, PositionalCollector and
             * KeywordCollector annotations.
             */
            Parameter[] paramArray = method.getParameters();
            int paramIndex = skip ? 1 : 0;  // Skip "self" parameter

            while (paramIndex < paramArray.length) {

                // The parameter currently being processed
                Parameter p = paramArray[paramIndex++];

                // index of parameter in Python != paramIndex, possibly
                int i = paramNames.size();

                // Use a replacement Python name if annotated @Name
                Name name = p.getAnnotation(Name.class);
                String paramName =
                        name == null ? p.getName() : name.value();
                paramNames.add(paramName);

                // Pick up all the other annotations on p
                PositionalOnly pos =
                        p.getAnnotation(PositionalOnly.class);
                KeywordOnly kwd = p.getAnnotation(KeywordOnly.class);
                Default def = p.getAnnotation(Default.class);
                PositionalCollector posColl =
                        p.getAnnotation(PositionalCollector.class);
                KeywordCollector kwColl =
                        p.getAnnotation(KeywordCollector.class);

                // Disallow these on the same parameter
                notUsedTogether(method, paramName, pos, kwd, posColl,
                        kwColl);
                notUsedTogether(method, paramName, def, posColl);
                notUsedTogether(method, paramName, def, kwColl);

                /*
                 * We have eliminated the possibility of disallowed
                 * combinations of annotations, so we can process the
                 * parameter types as alternatives.
                 */
                if (pos != null) {
                    // p is the (last) @PositionalOnly parameter
                    posonlyargcount = i + 1;

                } else if (kwd != null
                        && kwOnlyIndex == Integer.MAX_VALUE) {
                    // p is the (first) @KeywordOnly parameter
                    kwOnlyIndex = i;

                } else if (posColl != null) {
                    // p is the @PositionalCollector
                    varArgsIndex = i;

                } else if (kwColl != null) {
                    // p is the @KeywordCollector
                    varKeywordsIndex = i;
                }

                /*
                 * Check for a default value @Default. The value is a
                 * String we must interpret to Python.
                 */
                if (def != null) {
                    /*
                     * We know p is not a *Collector parameter, but our
                     * actions depend on whether it is positional or
                     * keyword-only.
                     */
                    if (i < kwOnlyIndex) {
                        // p is a positional parameter with a default
                        if (posDefaults == null)
                            posDefaults = new ArrayList<>();
                        posDefaults.add(eval(def.value()));
                    } else { // i >= kwOnlyIndex
                        // p is a keyword-only parameter with a default
                        if (kwdefaults == null)
                            kwdefaults = new HashMap<Object, Object>();
                        kwdefaults.put(paramName, eval(def.value()));
                    }

                } else if (posDefaults != null && i < kwOnlyIndex) {
                    /*
                     * Once we have started collecting positional
                     * default values, all subsequent positional
                     * parameters must have a default.
                     */
                    throw new InterpreterError(MISSING_DEFAULT,
                            getJavaMethodName(), paramName);
                }

                /*
                 * Parameters not having *Collector annotations are
                 * "regular". Keep count of them, and check we have not
                 * yet defined either collector.
                 */
                if (kwColl == null) {
                    // Note this also catches positional collector
                    if (hasVarKeywords())
                        throw new InterpreterError(FOLLOWS_KW_COLLECTOR,
                                getJavaMethodName(), paramName);
                    if (posColl == null) {
                        if (hasVarArgs())
                            throw new InterpreterError(
                                    FOLLOWS_POS_COLLECTOR,
                                    getJavaMethodName(), paramName);
                        count = i + 1;
                    }
                }
            }

            /*
             * Some checks and assignments we can only do when we've
             * seen all the parameters.
             */
            regargcount = count;
            posonlyargcount = Math.min(posonlyargcount, count);
            kwonlyargcount = count - Math.min(paramIndex, count);

            if (posDefaults != null) {
                defaults = posDefaults.toArray();
            }

            int n = paramNames.size();
            assert n == parameterNames.length;
            if (n > 0) { paramNames.toArray(parameterNames); }
        }

        private static final String PARAM = "'%s' parameter '%s' ";
        private static final String MISSING_DEFAULT =
                PARAM + "missing default value";
        private static final String FOLLOWS_POS_COLLECTOR =
                PARAM + "follows postional argument collector";
        private static final String FOLLOWS_KW_COLLECTOR =
                PARAM + "follows keyword argument collector";
        private static final String ANNOTATIONS_TOGETHER =
                PARAM + "annotations %s may not appear together";

        /**
         * Check that only one of the annotations (on a given parameter)
         * is null.
         *
         * @param method within which parameter appears
         * @param paramName its name
         * @param anno the annotations to check
         * @throws InterpreterError if more than one not {@code null}.
         */
        private void notUsedTogether(Method method, String paramName,
                Annotation... anno) throws InterpreterError {
            // Is there a problem?
            int count = 0;
            for (Annotation a : anno) { if (a != null) { count++; } }
            if (count > 1) {
                // There is a problem: collect the details.
                StringJoiner sj = new StringJoiner(",");
                for (Annotation a : anno) {
                    String name = a.annotationType().getSimpleName();
                    if (a != null) { sj.add(name); }
                }
                throw new InterpreterError(ANNOTATIONS_TOGETHER,
                        getJavaMethodName(), paramName, sj);
            }
        }

        /**
         * Poor man's eval() specifically for default values in built-in
         * methods.
         */
        private Object eval(String s) {
            if (s == null || s.equals("None")) {
                return Py.None;
            } else if (s.matches(REGEX_INT)) {
                // Small integer if we can; big if we can't
                BigInteger b = new BigInteger(s);
                try {
                    return b.intValueExact();
                } catch (ArithmeticException e) {
                    return b;
                }
            } else if (s.matches(REGEX_FLOAT)) {
                return Float.valueOf(s);
            } else if (s.matches(REGEX_STRING)) {
                return Float.valueOf(s);
            } else {
                // A somewhat lazy fall-back
                return s;
            }
        }

        private static String REGEX_INT = "-?\\d+";
        private static String REGEX_FLOAT =
                "[-+]?\\d+\\.\\d*((e|E)[-+]?\\d+)?";
        private static String REGEX_STRING = "('[~']*'|\"[~\"]*\")";

        /**
         * Check that the method has no annotation of the given type.
         *
         * @param method to process
         * @parame annoClass type of annotation disallowed
         */
        private void disallowAnnotation(Method method,
                Class<? extends Annotation> annoClass) {
            Annotation a = method.getAnnotation(annoClass);
            if (a != null) {
                String annoName = a.annotationType().getSimpleName();
                throw new InterpreterError(SECONDARY_DEF_ANNO,
                        getJavaMethodName(), annoName);
            }
        }

        private static final String SECONDARY_DEF_ANNO =
                "Secondary definition of '%s' "
                        + "has disallowed annotation '%s'";

        /**
         * Check that the parameter has no annotations
         * &#064;{@link Name}, &#064;{@link PositionalOnly}, and
         * &#064;{@link KeywordOnly}.
         *
         * @param p to process
         */
        private void disallowAnnotations(Parameter p) {
            for (Class<? extends Annotation> annoClass : DISALLOWED_PAR_ANNOS) {
                Annotation a = p.getAnnotation(annoClass);
                if (a != null) {
                    String annoName =
                            a.annotationType().getSimpleName();
                    throw new InterpreterError(SECONDARY_DEF_PAR_ANNO,
                            getJavaMethodName(), p.getName(), annoName);
                }
            }
        }

        /**
         * Parameter annotations disallowed on a secondary definition.
         */
        private static final List<Class<? extends Annotation>> //
        DISALLOWED_PAR_ANNOS = List.of(Name.class, PositionalOnly.class,
                KeywordOnly.class, Default.class);

        private static final String SECONDARY_DEF_PAR_ANNO =
                "Secondary definition of '%s' parameter '%s' "
                        + "has disallowed annotation '%s'";

        @Override
        PyMethodDescr createDescr(PyType type, Lookup lookup) {

            // XXX Create a MethodDef and work from that?
            // XXX Process defaults to array/tuple and map/dict

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
         * that there may be any number of them. This method matches
         * them to the supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyMethodDescr createDescrForInstanceMethod(
                PyType objclass, Lookup lookup)
                throws InterpreterError {

            ArgParser ap = new ArgParser(name, varArgsIndex >= 0,
                    varKeywordsIndex >= 0, posonlyargcount,
                    kwonlyargcount, parameterNames, regargcount);

            // Methods have self + this many args:
            final int L = regargcount;

            // Specialise the MethodDef according to the signature.
            MethodDef methodDef =
                    MethodDef.forInstance(ap, defaults, kwdefaults);

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
