package uk.co.farowl.vsj2.evo4;

/**
 * Class that may be used as a base for Python objects (but doesn't have
 * to be) to supply some universally needed methods and the type.
 */
abstract class AbstractPyObject implements PyObject {

    private PyType type;

    protected AbstractPyObject(PyType type) { this.type = type; }

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    // slot functions -------------------------------------------------
    /*
     * Do not declare slot functions in this class as it will interfere
     * with mechanisms for Python inheritance.
     */
}
