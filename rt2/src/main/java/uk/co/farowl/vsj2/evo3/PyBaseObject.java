package uk.co.farowl.vsj2.evo3;

/** The Python {@code object} object. */
class PyBaseObject implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.OBJECT_TYPE;

    @Override
    public PyType getType() { return TYPE; }

    PyBaseObject() {}

    @Override
    public String toString() { return "<'" + TYPE.name + "' object>"; }

    // slot functions -------------------------------------------------

}
