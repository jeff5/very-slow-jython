package uk.co.farowl.vsj3.evo1;

import static java.lang.invoke.MethodHandles.filterArguments;
import static java.lang.invoke.MethodHandles.filterReturnValue;
import static java.lang.invoke.MethodType.genericMethodType;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Map;

import uk.co.farowl.vsj3.evo1.MethodDescriptor.ArgumentError;

/**
 * A {@code MethodDef} describes a built-in function or method as it is
 * declared in Java, and provides mechanisms that support calling it
 * from Python. When an instance method is described by a
 * {@code MethodDef}, the signature discounts the {@code self} argument,
 * so that it is is correct for a call to the bound method, but not (in
 * just this respect) for a call to the descriptor that references it.
 * This is consistent with CPython.
 */
/*
 * We make a significant departure from CPython, in that we reify the
 * argument information as fields of this object. This makes the
 * processing of a call to a built-in function quite like a call to one
 * defined in Python. In CPython is processed by Argument Clinic into
 * generated source-code.
 */
// Compare CPython struct PyMethodDef
abstract class MethodDef {

    /** The name of the built-in function or method */
    // CPython PyMethodDef: ml_name
    final String name;

    /** The {@code __doc__} attribute, or {@code null} */
    final String doc;

    /*
     * Here we depart from CPython in reifying information from the
     * declaration in Java, and associated annotations. In CPython, this
     * knowledge is present at run-time in the structure of the code
     * generated by Argument Clinic, incompletely in the flags of the
     * PyMethodDef, and textually in the signature that begins the
     * documentation string. We do that by holding an ArgParser.
     */

    /**
     * An argument parser constructed with this {@code MethodDef} from
     * the description of the signature. Full information on the
     * signature is available from this structure, and it is available
     * to parse the arguments to
     * {@link #callMethod(MethodHandle, Object, PyTuple, PyDict)} in
     * complex sub-classes of {@code MethodDef}. (In simple sub-classes
     * it is only used to generate error messages once simple checks
     * fail.)
     */
    final ArgParser argParser;

    /** Whether an instance, static or class method (in Python). */
    final Kind kind;

    enum Kind {
        /** The first argument represents a self or module argument. */
        INSTANCE,
        /** An initial self or module argument is not expected. */
        // In CPython STATIC cannot be used for functions in modules.
        STATIC,
        /** Not currently used */
        // In CPython CLASS cannot be used for functions in modules.
        CLASS
    }

    /** Empty names array. */
    private static final String[] NO_STRINGS = new String[0];

    /** Empty values array. */
    private static final Object[] NO_OBJECTS = new Object[0];

    /**
     * The type of exception thrown by invoking
     * {@link PyJavaFunction#opCall} with the wrong size of arguments
     * (unacceptable tuple size or keyword dictionary unexpectedly
     * present or null. It doesn't tell us what went wrong: instead, we
     * catch it and work out what kind of {@link TypeError} to throw.
     */
    static class BadCallException extends Exception {

        // Suppression and stack trace disabled since singleton.
        BadCallException() {
            super(null, null, false, false);
        }
    }

    /**
     *
     * @param kind whether static, etc.
     * @param name of the method
     * @param argnames names of the arguments
     * @param posonlyargcount how many are positional-only
     * @param kwonlyargcount how many are keyword-only (from the end)
     * @param defaults positional defaults
     * @param kwdefaults keyword defaults
     * @param doc documentation string
     */
    MethodDef(Kind kind, String name, String[] argnames,
            int posonlyargcount, int kwonlyargcount, Object[] defaults,
            Map<Object, Object> kwdefaults, String doc) {
        this.kind = kind;
        this.name = name;
        if (argnames == null) { argnames = NO_STRINGS; }

        this.doc = doc == null ? "" : doc;

        // argParser largely duplicates the MethodDef.
        // XXX note this correct for the bound method (excludes self)
        // XXX Consider making MethodDef extend ArgParser
        this.argParser =
                new ArgParser(name, false, false, posonlyargcount,
                        kwonlyargcount, argnames, argnames.length)
                                .defaults(defaults)
                                .kwdefaults(kwdefaults);
    }

    MethodDef(Kind kind, String name, String[] argnames, String doc) {
        this(kind, name, argnames, argnames.length, 0, null, null, doc);
    }

    /**
     * Invoke the given method handle for the given target {@code self},
     * having arranged the arguments as expected by the method. We
     * create sub-classes of {@link MethodDef} to represent the finite
     * repertoire of {@code MethodType}s to which exposed methods may be
     * converted by the {@link Exposer}. This method accepts arguments
     * in a generic way (from the interpreter, say) and adapts them to
     * the specific needs of a wrapped method. The caller guarantees
     * that the wrapped method has the {@code MethodType} to which the
     * call is addressed.
     *
     * @param wrapped handle of the method to call
     * @param self target object of the method call
     * @param args of the method call
     * @param kwargs of the method call
     * @return result of the method call
     * @throws TypeError when the arguments ({@code args},
     *     {@code kwargs}) are not correct for the {@code MethodType}
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrap_* in typeobject.c
    abstract Object callMethod(MethodHandle method, Object self,
            PyTuple args, PyDict kwargs) throws TypeError, Throwable;

    /**
     * Each sub-type of {@link MethodDef} implements
     * {@code callMethod(mh, self, args, kwargs)} in its own way, and
     * must prepare the method handle to accept the number and type of
     * arguments it will supply. Typically, the returned handle will
     * expect a "self" object and an array of objects, but not
     * universally.
     *
     * @param raw the handle to be prepared
     * @return handle compatible with {@code methodDef}
     */
    final MethodHandle prepare(MethodHandle raw)
            throws WrongMethodTypeException {
        /*
         * To begin with, adapt the arguments after self to expect a
         * java.lang.Object, if Clinic knows how to convert them.
         */
        MethodType mt = raw.type();
        int pos = 1;
        MethodHandle[] af = Clinic.argumentFilter(mt, pos);
        MethodHandle mh = filterArguments(raw, pos, af);
        MethodHandle rf = Clinic.returnFilter(mt);
        if (rf != null) { mh = filterReturnValue(mh, rf); }
        /*
         * Let the method definition enforce specific constraints and
         * conversions on the handle.
         */
        return prepareSpecific(mh);
    }

    /**
     * This method is called at the end of
     * {@link #prepare(MethodHandle)}. At this point, the argument
     * filters {@link Clinic} is able to supply have been applied and
     * most or all arguments to the right of "self" expect an
     * {@code Object}.
     * <p>
     * The MethodHandle passed to this method will normally have the
     * signature {@code (S,...)R}, where {@code S} is the defining type
     * and {@code T} is a reference type derived from the return type,
     * and the {@code ...} part is often a sequence of {@code O}.
     *
     * the number Each sub-type of {@link MethodDef} implements
     * {@code callMethod(mh, self, args, kwargs)} in its own way, and
     * must prepare the method handle to accept the number and type of
     * arguments it will supply. Typically, the returned handle will
     * expect a "self" object and an array of objects, but not
     * universally.
     *
     * @param mh the handle to be prepared
     * @return handle of form {@code (O,...)O} compatible with
     *     {@code methodDef} where the {@code ...} part is up to the
     *     specific sub-class to choose
     */
    abstract MethodHandle prepareSpecific(MethodHandle mh);

    private static final String ARG_NOT_CONVERTIBLE =
            "in %s argument '%s' is not convertible from Object";

    boolean isStatic() { return kind == Kind.STATIC; }

    @Override
    public String toString() {
        return String.format("%s [name=%s]", getClass().getSimpleName(),
                name);
    }

    /**
     * Create a {@code MethodHandle} with the signature
     * {@code (TUPLE, DICT) O} that will make a "classic call" to the
     * method described in this {@code MethodDef}.
     *
     * @return required handle
     */
    @Deprecated
    MethodHandle getOpCallHandle() {
        // XXX implement maybe
        return null;
    }

    /**
     * Create a {@code MethodHandle} with the signature {@code (O[])O}
     * that will make a "vector call" to the method described in this
     * {@code MethodDef}.
     *
     * @return required handle
     */
    @Deprecated
    MethodHandle getVectorHandle() {
        // XXX implement maybe
        return null;
        // int n = meth.type().parameterCount();
        // MethodHandle vec = meth.asSpreader(MHUtil.OA, n);
        // return vec;
    }

    /**
     * Create a {@code MethodHandle} with the signature {@code (O,O[])O}
     * that will make a "bound method call" to the method described in
     * this {@code MethodDef}.
     *
     * @param o to bind as "self"
     * @return required handle
     */
    @Deprecated
    MethodHandle getBoundHandle(Object o) {
        // XXX implement maybe
        return null;
        // // XXX Defend against n = 0
        // int n = meth.type().parameterCount();
        // MethodHandle vec = meth.bindTo(o).asSpreader(MHUtil.OA, n -
        // 1);
        // return vec;
    }

    /**
     * All the argument types (from the given position on) in the type
     * of the given handle should be {@code Object.class}. If they
     * aren't, it is because no conversion was available in
     * {@link Clinic#argumentFilter(MethodType, int)}.
     *
     * @param mh {@code .type()[pos:]} handle to check
     * @param pos first element to check
     */
    protected void checkConvertible(MethodHandle mh, int pos) {
        MethodType mt = mh.type();
        int n = mt.parameterCount();
        for (int i = pos; i < n; i++) {
            if (mt.parameterType(i) != Object.class) {
                throw new InterpreterError(ARG_NOT_CONVERTIBLE, name,
                        argParser.argnames[i]);
            }
        }
    }

    /**
     * Check that no positional or keyword arguments are supplied. This
     * is for use when implementing
     * {@link #callMethod(MethodHandle, Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param kwargs to be checked
     * @throws TypeError if positional arguments are given or
     *     {@code kwargs} is not {@code null} or empty
     */
    final protected void checkNoArgs(PyTuple args, PyDict kwargs)
            throws TypeError {
        if (args.value.length != 0
                || (kwargs != null && !kwargs.isEmpty())) {
            // This will raise the TypeError by a slow path
            argParser.parse(args, kwargs);
        }
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callMethod(MethodHandle, Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param expArgs expected number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if the wrong number of positional arguments are
     *     given or {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int expArgs,
            PyDict kwargs) throws TypeError {
        if (args.value.length != expArgs
                || (kwargs != null && !kwargs.isEmpty())) {
            // This will raise the TypeError by a slow path
            argParser.parse(args, kwargs);
        }
    }

    /**
     * Check the number of positional arguments and that no keywords are
     * supplied. This is for use when implementing
     * {@link #callMethod(MethodHandle, Object, PyTuple, PyDict)}.
     *
     * @param args positional argument tuple to be checked
     * @param minArgs minimum number of positional arguments
     * @param maxArgs maximum number of positional arguments
     * @param kwargs to be checked
     * @throws TypeError if the wrong number of positional arguments are
     *     given or {@code kwargs} is not {@code null} or empty
     */
    final protected void checkArgs(PyTuple args, int minArgs,
            int maxArgs, PyDict kwargs) throws TypeError {
        int n = args.value.length;
        if (n < minArgs || n > maxArgs
                || (kwargs != null && !kwargs.isEmpty())) {
            // This will raise the TypeError by a slow path
            argParser.parse(args, kwargs);
        }
    }

    /** A method with signature {@code (S)O}. */
    static class NoArgs extends MethodDef {

        private static MethodType GENERIC = genericMethodType(1);

        NoArgs(String name, String doc) {
            super(Kind.INSTANCE, name, NO_STRINGS, doc);
        }

        @Override
        MethodHandle prepareSpecific(MethodHandle mh) {
            return mh.asType(GENERIC);
        }

        @Override
        Object callMethod(MethodHandle method, Object self,
                PyTuple args, PyDict kwargs)
                throws ArgumentError, Throwable {
            checkNoArgs(args, kwargs);
            return method.invokeExact(self);
        }
    }

    /** A method with signature {@code (S,O)O}. */
    static class OneArg extends MethodDef {

        private static MethodType GENERIC = genericMethodType(2);

        OneArg(String name, String[] argnames, String doc) {
            super(Kind.INSTANCE, name, argnames, doc);
        }

        @Override
        MethodHandle prepareSpecific(MethodHandle mh) {
            checkConvertible(mh, 1);
            return mh.asType(GENERIC);
        }

        @Override
        Object callMethod(MethodHandle method, Object self,
                PyTuple args, PyDict kwargs)
                throws ArgumentError, Throwable {
            checkArgs(args, 1, kwargs);
            return method.invoke(self, args.value[0]);
        }
    }

    /** A method with signature {@code (S,O[])O}. */
    static class FixedArgs extends MethodDef {

        private static MethodType GENERIC = genericMethodType(1, true);

        FixedArgs(String name, String[] argnames, String doc) {
            super(Kind.INSTANCE, name, argnames, doc);
        }

        @Override
        MethodHandle prepareSpecific(MethodHandle mh) {
            // All but the self argument will be presented as an array
            checkConvertible(mh, 1);
            int n = mh.type().parameterCount() - 1;
            return mh.asSpreader(Object[].class, n).asType(GENERIC);
        }

        @Override
        Object callMethod(MethodHandle method, Object self,
                PyTuple args, PyDict kwargs)
                throws ArgumentError, Throwable {
            checkArgs(args, argParser.posonlyargcount, kwargs);
            return method.invoke(self, args.value);
        }
    }
}
