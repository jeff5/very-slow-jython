package uk.co.farowl.vsj3.evo1;

import static uk.co.farowl.vsj3.evo1.MethodDescriptor.checkNoArgs;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * The Python {@code builtin_function_or_method} object. Java
 * sub-classes represent either a built-in function or a built-in method
 * bound to a particular object.
 */
public class PyJavaMethod implements CraftedPyObject, FastCall {

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
     * of {@code PyJavaMethod}.
     */
    final MethodHandle callHandle;

    /**
     * An argument parser supplied to this {@code PyJavaMethod} at
     * construction, from Java reflection of the implementation and from
     * annotations on it. Full information on the signature is available
     * from this structure, and it is available to parse the arguments
     * to {@link #__call__(Object[], String[])}.
     */
    final ArgParser argParser;

    /**
     * Construct a Python {@code builtin_function_or_method} object,
     * optionally bound to a particular "self" object, specifying the
     * method handle. The {@code self} object to which this is bound
     * should be {@code null} if the method is static. Otherwise, we
     * will create a method bound to {@code self} as target. This may be
     * any {@code object} in the case of an instance method, is a
     * {@code type} in the case of a class method, and is a
     * {@code module} in the case of a module member function.
     *
     * @param argParser parser defining the method
     * @param handle to the method defined
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition
     */
    PyJavaMethod(ArgParser argParser, MethodHandle handle, Object self,
            String module) {
        this.argParser = argParser;
        this.callHandle = handle;
        this.self = self;
        this.module = module;
    }

    /**
     * Construct a Python {@code builtin_function_or_method} object,
     * optionally bound to a particular "self" object. The {@code self}
     * object to which this is bound should be {@code null} if the
     * method is static. Otherwise, we will create a method bound to
     * {@code self} as target. This may be any {@code object} in the
     * case of an instance method, is a {@code type} in the case of a
     * class method, and is a {@code module} in the case of a module
     * member function.
     *
     * @param def definition from which to construct this method
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition
     */
    PyJavaMethod(MethodDef def, Object self, String module) {
        this(def.argParser, def.method, self, module);
    }

    @Override
    public String toString() { return Py.defaultToString(this); }

    /**
     * Construct a {@code PyJavaMethod} from an {@link ArgParser} and
     * {@code MethodHandle}s for the implementation method. The
     * arguments described by the parser do not include "self".
     *
     * @param ap argument parser (provides name etc.)
     * @param mh to the method defined
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition
     * @return A method descriptor supporting the signature
     */
    // Compare CPython PyCFunction_NewEx in methodobject.c
    static PyJavaMethod fromParser(ArgParser ap, MethodHandle mh,
            Object self, String module) {

        MethodSignature sig = MethodSignature.fromParser(ap);

        // For now, just go by the number of implementations
        switch (sig) {
            case NOARGS:
                return new NoArgs(ap, mh, self, module);
            case O1:
            case O2:
            case O3:
            case POSITIONAL:
                return new Positional(ap, mh, self, module);
            default:
                return new General(ap, mh, self, module);
        }
    }

    /**
     * Invoke the wrapped method handle for the bound {@code self} (if
     * any), and standard arguments ({@code Object[]} and
     * {@code String[]}). The implementation will arrange {@code self}
     * and the arguments as expected by the handle, or throw if they are
     * not correct for that. In the general case, a call to
     * {@link #argParser} is involved. We create sub-classes of
     * {@link PyJavaMethod} to represent the finite repertoire of
     * {@code MethodSignature}s, that override this method with
     * simplified logic.
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
    Object callBound(Object[] args, String[] names)
            throws ArgumentError, TypeError, Throwable {
        /*
         * The method handle type is {@code (O,[O])O}. The parser will
         * make an array of the args, and where allowed, gather excess
         * arguments into a tuple or dict, and fill missing ones from
         * defaults.
         */
        Object[] frame = argParser.parse(args, names);
        return callHandle.invoke(self, frame);
    }

    // slot functions -------------------------------------------------

    protected Object __repr__() throws Throwable {
        if (self == null || self instanceof PyModule)
            return String.format("<built-in function %s>", __name__());
        else
            return String.format("<built-in method %s of %s>",
                    __name__(), PyObjectUtil.toAt(self));
    }

    @Override
    public Object __call__(Object[] args, String[] names)
            throws Throwable {
        /*
         * The method handle type is {@code (O,[O])O}. The parser will
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

        General(ArgParser argParser, MethodHandle handle, Object self,
                String module) {
            super(argParser, handle, self, module);
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws TypeError, Throwable {
            /*
             * The method handle type is {@code (O,[O])O}. The parser
             * will make an array of the args, and where allowed, gather
             * excess arguments into a tuple or dict, and fill missing
             * ones from defaults.
             */
            Object[] frame = argParser.parse(args, names);
            return callHandle.invoke(self, frame);
        }
    }

    private static class NoArgs extends PyJavaMethod {

        NoArgs(ArgParser argParser, MethodHandle handle, Object self,
                String module) {
            super(argParser, handle, self, module);
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws Throwable {
            // The method handle type is {@code (O)O}.
            checkNoArgs(args, names);
            return callHandle.invoke(self);
        }
    }

    private static class Positional extends PyJavaMethod {

        Positional(ArgParser argParser, MethodHandle handle,
                Object self, String module) {
            super(argParser, handle, self, module);
        }

        @Override
        Object callBound(Object[] args, String[] names)
                throws TypeError, Throwable {
            /*
             * The method handle type is {@code (O,[O])O}. The parser
             * will make an array of the args, and where allowed, gather
             * excess arguments into a tuple or dict, and fill missing
             * ones from defaults.
             */
            Object[] frame = argParser.parse(args, names);
            return callHandle.invoke(self, frame);
        }
    }

}
