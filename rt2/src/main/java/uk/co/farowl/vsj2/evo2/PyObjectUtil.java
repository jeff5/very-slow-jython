package uk.co.farowl.vsj2.evo2;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

    /** Helper to create an exception for internal type error. */
    static InterpreterError typeMismatch(PyObject v, PyType expected) {
        String fmt = "'%s; argument to slot where '%s' expected";
        return new InterpreterError(fmt, v.getType().name,
                expected.name);
    }
}
