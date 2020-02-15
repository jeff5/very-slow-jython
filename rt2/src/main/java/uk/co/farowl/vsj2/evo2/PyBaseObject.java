package uk.co.farowl.vsj2.evo2;

/** The Python {@code object} object. */
class PyBaseObject implements PyObject {

    static PyType TYPE = new PyType("object", null, PyBaseObject.class);

    @Override
    public PyType getType() { return TYPE; }

    PyBaseObject() { }

    @Override
    public String toString() { return "<'"+TYPE.name + "' object>"; }

    // slot functions -------------------------------------------------


}
