package uk.co.farowl.vsj2.evo4;

/** Holder for objects appearing in the closure of a function. */
class PyCell implements PyObject {

    static final PyType TYPE = new PyType("cell", PyCell.class);

    PyObject obj;

    PyCell(PyObject obj) { this.obj = obj; }

    static final PyCell[] EMPTY_ARRAY = new PyCell[0];

    // Type admits no subclasses.
    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyCell self) {
        return Py.str(String.format("<cell [%.80s]>", self.obj));
    }

}
