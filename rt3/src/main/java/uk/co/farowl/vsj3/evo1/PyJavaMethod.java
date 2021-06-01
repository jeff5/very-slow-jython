package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * The Python {@code builtin_function_or_method} object. Java
 * sub-classes represent either a built-in function or a built-in method
 * bound to a particular object.
 */
public class PyJavaMethod implements CraftedType, VectorCallable {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("builtin_function_or_method",
                    MethodHandles.lookup()));

    @Override
    public PyType getType() { return TYPE; }

    /** Description of the function to call */
    final MethodDef methodDef;

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
     * A Java MethodHandle that implements the function or bound method.
     * The type of this handle is {@code (O,O[])O}. It may have been
     * taken from {@link #methodDef} or overridden, according to the
     * constructor.
     */
    final MethodHandle callHandle;

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
     * @param def definition from which to construct this method
     * @param self object to which bound (or {@code null} if a static
     *     method)
     * @param module name of the module supplying the definition
     */
    PyJavaMethod(MethodDef def, MethodHandle handle, Object self,
            String module) {
        this.methodDef = def;
        this.self = self;
        this.module = module;
        this.callHandle = handle;
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
        this(def, def.meth, self, module);
    }

    @Override
    public String toString() {
        return Py.defaultToString(this);
    }

    // VectorCallable interface ---------------------------------------

    @Override
    public Object call(Object[] args, PyTuple kwnames)
            throws Throwable {
        if (kwnames == null || kwnames.size() == 0) {
            return callHandle.invokeExact(self, args);
        } else {
            throw new MissingFeature("Keywords in vector call");
        }
    }

    // slot functions -------------------------------------------------

    protected Object __repr__() throws Throwable {
        if (self == null || self instanceof PyModule)
            return PyUnicode.fromFormat("<built-in function %s>",
                    methodDef.name);
        else
            return PyUnicode.fromFormat("<built-in method %s of %s>",
                    methodDef.name, PyObjectUtil.toAt(self));
    }

    @Override
    public Object __call__(PyTuple args, PyDict kwargs)
            throws Throwable {
        return methodDef.callMethod(callHandle, self, args, kwargs);
    }

    // plumbing -------------------------------------------------------

}
