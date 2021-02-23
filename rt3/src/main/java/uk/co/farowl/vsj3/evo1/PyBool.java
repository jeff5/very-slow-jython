package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code bool} object. */
class PyBool {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bool", MethodHandles.lookup()) //
                    .base(PyLong.TYPE).flagNot(PyType.Flag.BASETYPE));

    private PyBool() {}  // No instances

    // special methods ------------------------------------------------

    static Object __repr__(Boolean self ) {
        return self ? "True" :"False" ;
    }

    static Object __and__(Boolean v, Object w) {
        if (w instanceof Boolean)
            return v && ((Boolean) w);
        else
            // w is not a bool, go arithmetic.
            return PyLongMethods.__and__(v, w);
    }

    static Object __rand__(Boolean w, Object v) {
        if (v instanceof Boolean)
            return ((Boolean) v) && w;
        else
            // v is not a bool, go arithmetic.
            return PyLongMethods.__rand__(w, v);
    }

    static Object __or__(Boolean v, Object w) {
        if (w instanceof Boolean)
            return v || ((Boolean) w);
        else
            // w is not a bool, go arithmetic.
            return PyLongMethods.__or__(v, w);
    }

    static Object __ror__(Boolean w, Object v) {
        if (v instanceof Boolean)
            return ((Boolean) v) || w;
        else
            // v is not a bool, go arithmetic.
            return PyLongMethods.__ror__(w, v);
    }

    static Object __xor__(Boolean v, Object w) {
        if (w instanceof Boolean)
            return v != ((Boolean) w);
        else
            // w is not a bool, go arithmetic.
            return PyLongMethods.__xor__(v, w);
    }

    static Object __rxor__(Boolean w, Object v) {
        if (v instanceof Boolean)
            return ((Boolean) v) != w;
        else
            // v is not a bool, go arithmetic.
            return PyLongMethods.__rxor__(w, v);
    }

}
