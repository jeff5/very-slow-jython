package uk.co.farowl.vsj2.evo3;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

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
