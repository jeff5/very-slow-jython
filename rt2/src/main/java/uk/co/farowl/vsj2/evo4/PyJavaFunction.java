package uk.co.farowl.vsj2.evo4;

/**
 * A specialisation of the Python {@code builtin_function_or_method}
 * object, used to represent a function defined in Java.
 */
class PyJavaFunction extends PyJavaCallable {

    // Compare CPython PyCFunction_NewEx in methodobject.c
    PyJavaFunction(MethodDef methodDef, PyUnicode moduleName) {
        super(methodDef, null, methodDef.getVectorHandle(), moduleName);
    }

    PyJavaFunction(MethodDef methodDef) { this(methodDef, null); }

    @Override
    public PyObject __call__(PyTuple args, PyDict kwargs)
            throws Throwable {
        try {
            return (PyObject) opCall.invokeExact(args, kwargs);
        } catch (MethodDef.BadCallException bce) {
            // After the BCE, check() should always throw.
            methodDef.check(args, kwargs);
            // It didn't :( so this is an internal error
            throw new InterpreterError(bce,
                    "Unexplained BadCallException in __call__");
        }
    }

}
