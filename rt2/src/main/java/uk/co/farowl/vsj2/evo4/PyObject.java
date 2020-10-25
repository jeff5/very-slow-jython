package uk.co.farowl.vsj2.evo4;

import java.util.Map;

/** All Python object implementations implement this interface. */
interface PyObject {

    // /** Sub-interface to which built-in dict-like objects conform. */
    // interface Mapping extends PyObject, Map<PyObject, PyObject> {}

    /** The Python {@code type} of this object. */
    PyType getType();

    /**
     * The dictionary of the instance, (not necessarily a Python
     * {@code dict} or writable. By default, returns {@code null},
     * meaning no instance dictionary. If the returned {@code Map} is
     * not writable, it should throw a Java
     * {@code UnsupportedOperationException} on attempts to modify it.
     *
     * @param create if the object may have a dictionary, but doesn't
     *            have one yet, create it now
     * @return a mapping to treat like a dictionary
     */
    default Map<PyObject, PyObject> getDict(boolean create) {
        return null;
    }

    /**
     * The dictionary of the instance, (not necessarily a Python
     * {@code dict} or writable. By default the same as
     * {@code getDict(false)}.
     *
     * @return a mapping to treat like a dictionary
     */
    // default Map<PyObject, PyObject> getDict() {
    // return getDict(false);
    // }
}
