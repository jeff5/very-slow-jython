package uk.co.farowl.vsj2.evo2;

import java.util.HashMap;

/**
 * The Python {@code dict} object. The Java API is provided directly by
 * the base class implementing {@code Map}, while the Python API has
 * been implemented on top of the Java one.
 */
class PyDictionary extends HashMap<PyObject, PyObject>
        implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("dict", PyDictionary.class);

    @Override
    public PyType getType() { return TYPE; }
}
