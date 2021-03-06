package uk.co.farowl.vsj2.evo4;

/**
 * A specialisation of the Python {@code builtin_function_or_method}
 * object, used to represent a method defined in Java and bound to a
 * particular target.
 */
class PyJavaMethod extends PyJavaCallable {

    // Compare CPython PyCFunction_NewEx in methodobject.c
    PyJavaMethod(MethodDef methodDef, PyObject self) {
        super(methodDef, self, methodDef.getBoundHandle(self), null);
    }

    @Override
    public PyObject __call__(PyTuple args, PyDict kwargs)
            throws Throwable {
        // Prepend self to arguments
        int n = args.size();
        if (n == 0)
            args = Py.tuple(self);
        else {
            PyObject[] a = new PyObject[n + 1];
            a[0] = self;
            System.arraycopy(args.value, 0, a, 1, n);
            args = Py.tuple(a);
        }
        // Make classic call
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
