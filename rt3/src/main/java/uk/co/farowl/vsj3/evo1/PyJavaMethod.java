package uk.co.farowl.vsj3.evo1;

import static uk.co.farowl.vsj3.evo1.MethodDescriptor.checkNoArgs;
import static uk.co.farowl.vsj3.evo1.MethodDescriptor.checkArgs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj3.evo1.base.InterpreterError;
import uk.co.farowl.vsj3.evo1.base.MethodKind;

/**
 * The Python {@code builtin_function_or_method} object. Java
 * sub-classes represent either a built-in function or a built-in method
 * bound to a particular object.
 */
public abstract class PyJavaMethod
        implements CraftedPyObject, FastCall {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("builtin_function_or_method",
                    MethodHandles.lookup()));

    @Override
    public PyType getType() { return TYPE; }

    /** Name of the containing module (or {@code null}). */
    final String module;

    /**
     * The object to which this is bound as target (or {@code null}).
     * Conventions (adopted from CPython) around this field are that it
     * should be {@code null} when representing a static method of a
     * built-in class, and otherwise contain the bound target
     * ({@code object} or {@code type}). A function obtained from a
     * module may be a method bound to an instance of that module.
     */
    final Object self;

    /**
     * A Java {@code MethodHandle} that implements the function or bound
     * method. The type of this handle varies according to the sub-class
     * of {@code PyJavaMethod}, but it is definitely "prepared" to
     * accept {@code Object.class} instances or arrays, not the actual
     * parameter types of the method definition in Java.
     */
    final MethodHandle handle;

    /**
     * An argument parser supplied to this {@code PyJavaMethod} at
     * construction, from Java reflection of the definition in Java and
     * from annotations on it. Full information on the signature is
     * available from this structure, and it is available to parse the
     * arguments to {@link #__call__(Object[], String[])}.
     */
    final ArgParser argParser;

    /**
     * Construct a Python {@code builtin_function_or_method} object,
     * optionally bound to a particular "self" object, specifying the
     * prepared method handle. The {@code self} object to which this is
     * bound should be {@code null} if the method is Python static in a
     * type. Otherwise, we will create a method bound to {@code self} as
     * target. This may be any {@code object} in the case of an instance
     * method, is a {@code type} in the case of a class method, and is a
     * {@code module} in the case of a function in a module (whether the
     * Java signature is Java static or not).
     *
     * @param argParser parser defining the method
     * @param handle a prepared prepared to the method defined
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition
     */
    protected PyJavaMethod(ArgParser argParser, MethodHandle handle,
            Object self, String module) {
        this.argParser = argParser;
        this.handle = handle;
        this.self = self;
        this.module = module;
    }

    /**
     * Construct a {@code PyJavaMethod} from an {@link ArgParser} and
     * {@code MethodHandle} for the implementation method. The arguments
     * described by the parser do not include "self". This is the
     * factory we use to create a function in a module.
     *
     * @param ap argument parser (provides name etc.)
     * @param method raw handle to the method defined
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition (or
     *     {@code null} if representing a bound method of a type)
     * @return A method descriptor supporting the signature
     */
    // Compare CPython PyCFunction_NewEx in methodobject.c
    static PyJavaMethod fromParser(ArgParser ap, MethodHandle method,
            Object self, String module) {
        /*
         * Note this is a recommendation on the assumption all
         * optimisations are supported. The actual choice is made in the
         * switch statement.
         */
        MethodSignature sig = MethodSignature.fromParser(ap);

        assert ap.methodKind != MethodKind.CLASS;

        /*
         * In each case, we must prepare a method handle of the chosen
         * shape.
         */
        switch (sig) {
            case NOARGS:
                method = MethodSignature.NOARGS.prepareBound(ap, method,
                        self);
                return new NoArgs(ap, method, self, module);
            case O1:
                method = MethodSignature.O1.prepareBound(ap, method,
                        self);
                return new O1(ap, method, self, module);
            case O2:
            case O3:
            case POSITIONAL:
                method = MethodSignature.POSITIONAL.prepareBound(ap,
                        method, self);
                return new Positional(ap, method, self, module);
            default:
                method = MethodSignature.GENERAL.prepareBound(ap,
                        method, self);
                return new General(ap, method, self, module);
        }
    }

    /**
     * Construct a {@code PyJavaMethod} from a {@link PyMethodDescr} and
     * optional object to bind. The {@link PyMethodDescr} provides the
     * parser and unbound prepared {@code MethodHandle}. The arguments
     * described by the parser do not include "self". This is the
     * factory that supports descriptor {@code __get__}.
     *
     * @param descr descriptor being bound
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @return a Java method object supporting the signature
     * @throws TypeError if {@code self} is not compatible with
     *     {@code descr}
     * @throws Throwable on other errors while chasing the MRO
     */
    // Compare CPython PyCFunction_NewEx in methodobject.c
    static PyJavaMethod from(PyMethodDescr descr, Object self)
            throws TypeError, Throwable {
        ArgParser ap = descr.argParser;
        assert ap.methodKind == MethodKind.INSTANCE;
        MethodHandle handle = descr.getHandle(self).bindTo(self);
        // We must support the same optimisations as PyMethodDescr
        switch (descr.signature) {
            case NOARGS:
                return new NoArgs(ap, handle, self, null);
            case O1:
                return new O1(ap, handle, self, null);
            case O2:
                // return new O2(ap, handle, self, null);
            case O3:
                // return new O3(ap, handle, self, null);
            case POSITIONAL:
                return new Positional(ap, handle, self, null);
            case GENERAL:
                return new General(ap, handle, self, null);
            default:
                throw new InterpreterError(
                        "Optimisation not supported: %s",
                        descr.signature);
        }
    }

    /**
     * Invoke the method handle for the bound {@code self} (if any), and
     * standard arguments ({@code Object[]} and {@code String[]}). The
     * implementation will arrange {@code self} and the arguments as
     * expected by the handle, or throw if they are not correct for
     * that. In the general case, a call to {@link #argParser} is
     * involved. We create sub-classes of {@link PyJavaMethod} to
     * represent the finite repertoire of {@code MethodSignature}s, that
     * override this method with simplified logic.
     *
     * @param args of the method call
     * @param names of args given by keyword or {@code null}
     * @return result of the method call
     * @throws TypeError when the arguments ({@code args},
     *     {@code kwargs}) are not correct for the method signature
     * @throws ArgumentError as a shorthand for {@link TypeError}, which
     *     the caller must convert with
     *     {@link MethodDescriptor#typeError(ArgumentError, Object[])}
     * @throws Throwable from the implementation of the special method
     */
    // XXX or should this just be called "call"?
    abstract Object callBound(Object[] args, String[] names)
            throws ArgumentError, TypeError, Throwable;

    // slot functions -------------------------------------------------

    protected Object __repr__() throws Throwable {
        if (self == null || self instanceof PyModule)
            return String.format("<built-in function %s>", __name__());
        else
            return String.format("<built-in method %s of %s>",
                    __name__(), PyObjectUtil.toAt(self));
    }

    public Object __call__(Object[] args, String[] names)
            throws Throwable {
        /*
         * XXX Consider specialising to numbers of arguments and keyword
         * use, to call the optimised call(...), as in PyMethodDescr.
         */
        /*
         * The method handle type is {@code (O,O[])O}. The parser will
         * make an array of the args, and where allowed, gather excess
         * arguments into a tuple or dict, and fill missing ones from
         * defaults.
         */
        try {
            // Call through the correct wrapped handle
            return callBound(args, names);
        } catch (ArgumentError ae) {
            /*
             * Implementations may throw ArgumentError as a simplified
             * encoding of a TypeError.
             */
            throw typeError(ae, args);
        }
    }

    /*
     * Although this class implements FastCall, it does not yet properly
     * take advantage of the specialisation to number of arguments in
     * signatures of call(...), overridden in the sub-classes and
     * selected in __call__. See PyMethodDescr for a fully-worked
     * application of the FastCall idea.
     */

    @Override
    public Object call(Object[] args, String[] names) throws Throwable {
        // This should *not* specialise to numbers of arguments.
        /*
         * The method handle type is {@code (O,O[])O}. The parser will
         * make an array of the args, and where allowed, gather excess
         * arguments into a tuple or dict, and fill missing ones from
         * defaults.
         */
        try {
            // Call through the correct wrapped handle
            return callBound(args, names);
        } catch (ArgumentError ae) {
            /*
             * Implementations may throw ArgumentError as a simplified
             * encoding of a TypeError.
             */
            throw typeError(ae, args);
        }
    }

    // exposed methods -----------------------------------------------

    /** @return name of the function or method */
    // Compare CPython meth_get__name__ in methodobject.c
    @Exposed.Getter
    String __name__() { return argParser.name; }

    // plumbing ------------------------------------------------------

    @Override
    public String toString() { return Py.defaultToString(this); }

    /**
     * Translate a problem with the number and pattern of arguments, in
     * a failed attempt to call the wrapped method, to a Python
     * {@link TypeError}.
     *
     * @param ae expressing the problem
     * @param args positional arguments (only the number will matter)
     * @return a {@code TypeError} to throw
     */
    // XXX Compare MethodDescriptor.typeError : unify?
    protected TypeError typeError(ArgumentError ae, Object[] args) {
        int n = args.length;
        switch (ae.mode) {
            case NOARGS:
            case NUMARGS:
            case MINMAXARGS:
                return new TypeError("%s() %s (%d given)", __name__(),
                        ae, n);
            case NOKWARGS:
            default:
                return new TypeError("%s() %s", __name__(), ae);
        }
    }

    /**
     * The implementation may have any signature allowed by
     * {@link ArgParser}.
     */
    private static class General extends PyJavaMethod {

        /**
         * Construct a method object, identifying the implementation by
         * a parser and a method handle.
         *
         * @param argParser describing the signature of the method
         * @param handle a prepared prepared to the method defined
         * @param self object to which bound (or {@code null} if a
         *     static method)
         * @param module name of the module supplying the definition (or
         *     {@code null} if representing a bound method of a type)
         */
        General(ArgParser argParser, MethodHandle handle, Object self,
                String module) {
            super(argParser, handle, self, module);
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws TypeError, Throwable {
            /*
             * The method handle type is {@code ([O])O}. The parser will
             * make an array of the args, and where allowed, gather
             * excess arguments into a tuple or dict, and fill missing
             * ones from defaults.
             */
            Object[] frame = argParser.parse(args, names);
            return handle.invokeExact(frame);
        }
    }

    /** The implementation signature accepts no arguments. */
    private static class NoArgs extends PyJavaMethod {

        /**
         * Construct a method object, identifying the implementation by
         * a parser and a prepared method handle.
         *
         * @param argParser describing the signature of the method
         * @param handle a prepared prepared to the method defined
         * @param self object to which bound (or {@code null} if a
         *     static method)
         * @param module name of the module supplying the definition (or
         *     {@code null} if representing a bound method of a type)
         */
        NoArgs(ArgParser argParser, MethodHandle handle, Object self,
                String module) {
            super(argParser, handle, self, module);
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws Throwable {
            // The method handle type is {@code ()O}.
            checkNoArgs(args, names);
            return handle.invokeExact();
        }
    }

    /**
     * The implementation signature accepts one positional arguments,
     * with potentially a default value, specified by the parser.
     */
    private static class O1 extends PyJavaMethod {

        /** Default values of the trailing arguments. */
        private final Object[] defaults;

        /**
         * Construct a method object, identifying the implementation by
         * a parser and a method handle.
         *
         * @param argParser describing the signature of the method
         * @param handle a prepared prepared to the method defined
         * @param self object to which bound (or {@code null} if a
         *     static method)
         * @param module name of the module supplying the definition (or
         *     {@code null} if representing a bound method of a type)
         */
        O1(ArgParser argParser, MethodHandle handle, Object self,
                String module) {
            super(argParser, handle, self, module);
            this.defaults = argParser.getDefaults();
            assert defaults.length <= 1;
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws TypeError, Throwable {
            // The method handle type is {@code (O)O}.
            checkArgs(args, 1 - defaults.length, 1, names);
            return handle.invokeExact(
                    (args.length == 1 ? args : defaults)[0]);
        }
    }

    /**
     * The implementation signature accepts a number of positional
     * arguments, with potential trailing defaults, specified by the
     * parser.
     */
    private static class Positional extends PyJavaMethod {

        /** Default values of the trailing arguments. */
        private final Object[] defaults;

        /**
         * Construct a method object, identifying the implementation by
         * a parser and a method handle.
         *
         * @param argParser describing the signature of the method
         * @param handle a prepared prepared to the method defined
         * @param self object to which bound (or {@code null} if a
         *     static method)
         * @param module name of the module supplying the definition (or
         *     {@code null} if representing a bound method of a type)
         */
        Positional(ArgParser argParser, MethodHandle handle,
                Object self, String module) {
            super(argParser, handle, self, module);
            this.defaults = argParser.getDefaults();
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws TypeError, Throwable {
            // The method handle type is {@code (O[])O}.
            int max = argParser.argcount;
            int min = max - defaults.length;
            checkArgs(args, min, max, names);
            // May need to fill a gap from defaults
            int n = args.length, gap = max - n;
            if (gap == 0) {
                return handle.invokeExact(args);
            } else {
                Object[] frame = new Object[max];
                System.arraycopy(args, 0, frame, 0, n);
                System.arraycopy(defaults, n - min, frame, n, gap);
                return handle.invokeExact(frame);
            }
        }
    }
}
