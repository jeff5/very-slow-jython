package uk.co.farowl.vsj2.evo3;

import java.math.BigInteger;

/** The Python {@code bool} object. */
class PyBool extends PyLong {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bool", PyBool.class) //
                    .base(PyLong.TYPE) //
                    .flagNot(PyType.Flag.BASETYPE));

    @Override
    public PyType getType() { return TYPE; }

    // Private so we can guarantee the doubleton. :)
    private PyBool(boolean value) { super(value ? 1 : 0); }

    /** Python {@code False} object. */
    static final PyBool False = new PyBool(false);

    /** Python {@code True} object. */
    static final PyBool True = new PyBool(true);

    @Override
    public String toString() {
        return asSize() == 0 ? "False" : "True";
    }


    // slot functions -------------------------------------------------

    static PyObject and(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v == True && w == True);
        else
            // v is not a bool, or w is not.
            return PyLong.and(v, w);
    }

    static PyObject or(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v == True || w == True);
        else
            // v is not a bool, or w is not.
            return PyLong.or(v, w);
    }

    static PyObject xor(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v != w);
        else
            // v is not a bool, or w is not.
            return PyLong.xor(v, w);
    }

}
