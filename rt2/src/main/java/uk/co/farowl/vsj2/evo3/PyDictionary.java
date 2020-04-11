package uk.co.farowl.vsj2.evo3;

import java.util.HashMap;

/**
 * The Python {@code dict} object. The Java API is provided directly by
 * the base class implementing {@code Map}, while the Python API has
 * been implemented on top of the Java one.
 */
class PyDictionary extends HashMap<PyObject, PyObject>
        implements PyObject {

    static final PyType TYPE = new PyType("dict", PyDictionary.class);

    @Override
    public PyType getType() { return TYPE; }

    /**
     * Specialisation of {@code Map.get} allowing Java {@code String}
     * keys.
     */
    PyObject get(String key) { return this.get(Py.str(key)); }

    /**
     * Specialisation of {@code Map.put()} allowing Java {@code String}
     * keys.
     */
    PyObject put(String key, PyObject value) {
        return this.put(Py.str(key), value);
    }

    /**
     * Specialisation of {@code Map.putIfAbsent()} allowing Java
     * {@code String} keys.
     */
    PyObject putIfAbsent(String key, PyObject value) {
        return this.putIfAbsent(Py.str(key), value);
    }
}
