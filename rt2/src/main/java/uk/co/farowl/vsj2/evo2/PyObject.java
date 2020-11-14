package uk.co.farowl.vsj2.evo2;

/** All Python object implementations implement this interface. */
interface PyObject {

    /**
     * The Python {@code type} of this object.
     *
     * @return {@code type} of this object
     */
    PyType getType();
}
