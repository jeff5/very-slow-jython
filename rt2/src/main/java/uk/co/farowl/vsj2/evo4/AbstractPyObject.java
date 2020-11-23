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
     * It should be possible to declare special (instance) methods in
     * this class to save work in implementation classes of Python
     * types. The processing of special methods would treat them as
     * defined afresh by each exposed implementation (each class that
     * calls PyType.fromSpec()). This may be undesirable where
     * sub-classes that are object implementations should instead
     * Python-inherit their definition.
     */
}
