package uk.co.farowl.vsj2.evo3;

/** Holder for objects appearing in the closure of a function. */
class PyCell implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("cell", PyCell.class);

    @Override
    public PyType getType() { return TYPE; }

    PyObject obj;

    PyCell(PyObject obj) { this.obj = obj; }

    @Override
    public String toString() {
        return String.format("<cell [%.80s]>", obj);
    }
    static final PyCell[] EMPTY_ARRAY = new PyCell[0];
}
