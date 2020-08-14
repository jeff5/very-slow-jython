package uk.co.farowl.vsj2.evo4;

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

    static PyObject __and__(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v == True && w == True);
        else
            // v is not a bool, or w is not.
            return PyLong.__and__(v, w);
    }

    static PyObject __or__(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v == True || w == True);
        else
            // v is not a bool, or w is not.
            return PyLong.__or__(v, w);
    }

    static PyObject __xor__(PyObject v, PyObject w) {
        if (v instanceof PyBool && w instanceof PyBool)
            return Py.val(v != w);
        else
            // v is not a bool, or w is not.
            return PyLong.__xor__(v, w);
    }

}
