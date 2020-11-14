package uk.co.farowl.vsj2.evo4;

import java.math.BigInteger;

/** The Python {@code bool} object. */
class PyBool extends PyLong {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bool", PyBool.class) //
                    .base(PyLong.TYPE) //
                    .flagNot(PyType.Flag.BASETYPE));

    // Private so we can guarantee the doubleton. :)
    private PyBool(boolean value) {
        super(TYPE, value ? BigInteger.ONE : BigInteger.ZERO);
    }

    /** Python {@code False} object. */
    static final PyBool False = new PyBool(false);

    /** Python {@code True} object. */
    static final PyBool True = new PyBool(true);

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyBool v) {
        return Py.str(v.value == BigInteger.ZERO ? "False" : "True");
    }

    static PyObject __and__(PyBool v, PyObject w) {
        if (w instanceof PyBool)
            return Py.val(v == True && w == True);
        else
            // w is not a bool, go arithmetic.
            return PyLong.__and__(v, w);
    }

    static PyObject __rand__(PyBool w, PyObject v) {
        if (v instanceof PyBool)
            return Py.val(v == True && w == True);
        else
            // v is not a bool, go arithmetic.
            return PyLong.__rand__(w, v);
    }

    static PyObject __or__(PyBool v, PyObject w) {
        if (w instanceof PyBool)
            return Py.val(v == True || w == True);
        else
            // v is not a bool, go arithmetic.
            return PyLong.__or__(v, w);
    }

    static PyObject __ror__(PyBool w, PyObject v) {
        if (v instanceof PyBool)
            return Py.val(v == True || w == True);
        else
            // v is not a bool, go arithmetic.
            return PyLong.__ror__(w, v);
    }

    static PyObject __xor__(PyBool v, PyObject w) {
        if (w instanceof PyBool)
            return Py.val(v != w);
        else
            // w is not a bool, go arithmetic.
            return PyLong.__xor__(v, w);
    }

    static PyObject __rxor__(PyBool w, PyObject v) {
        if (v instanceof PyBool)
            return Py.val(v != w);
        else
            // v is not a bool, go arithmetic.
            return PyLong.__rxor__(w, v);
    }

}
