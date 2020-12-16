package uk.co.farowl.vsj2.evo4;

/**
 * A specialisation of the Python {@code builtin_function_or_method}
 * object, used to represent a method defined in Java and bound to a
 * particular target.
 */

class PyJavaMethod extends PyJavaFunction {

    /** The object to which this is bound as target. */
    final PyObject self;

    // Compare CPython PyCFunction_NewEx in methodobject.c
    PyJavaMethod(MethodDef methodDef, PyObject self,
            PyUnicode moduleName) {
        super(methodDef, moduleName);
        this.self = self;
    }

    PyJavaMethod(MethodDef methodDef, PyObject self) {
        this(methodDef, self, null);
    }

    @Override
    protected PyUnicode __repr__() {
        if (self == null)  // || PyModule_Check(self)
            return PyUnicode.fromFormat("<built-in function %s>",
                    methodDef.name);
        else
            return PyUnicode.fromFormat(
                    "<built-in method %s of %s object at %s>",
                    methodDef.name, self.getType().name,
                    Integer.toHexString(((Object) self).hashCode()));
    }

}
