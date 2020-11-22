package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj2.evo4.PyType.Spec;

/** Holder for objects appearing in the closure of a function. */
class PyCell implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new Spec("cell", PyCell.class, MethodHandles.lookup()));

    PyObject obj;

    PyCell(PyObject obj) { this.obj = obj; }

    static final PyCell[] EMPTY_ARRAY = new PyCell[0];

    // Type admits no subclasses.
    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private PyObject __repr__() {
        return Py.str(String.format("<cell [%.80s]>", obj));
    }

}
