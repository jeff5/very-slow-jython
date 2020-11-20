package uk.co.farowl.vsj2.evo2;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

    /** Helper to create an exception for internal type error.
     *
     * @param v mismatching object
     * @param expected type expected
     * @return exception to throw
     */
    static InterpreterError typeMismatch(PyObject v, PyType expected) {
        String fmt = "'%s' argument to slot where '%s' expected";
        return new InterpreterError(fmt, v.getType().name,
                expected.name);
    }

    /**
     * Convert comparison result (int) to rich comparison result.
     * Typically, {@code u} is the result of
     * {@link Comparable#compareTo(Object)}.
     *
     * @param u comparison result
     * @param op kind of rich comparison requested
     * @return rich comparison result (Python {@code bool})
     */
    static PyObject richCompareHelper(int u, Comparison op) {
        boolean r = false;
        switch (op) {
            case LE: r = u <= 0; break;
            case LT: r = u < 0; break;
            case EQ: r = u == 0; break;
            case NE: r = u != 0; break;
            case GE: r = u >= 0; break;
            case GT: r = u > 0; break;
            default: // pass
        }
        return r ? PyBool.True : PyBool.False;
    }
}
