package uk.co.farowl.vsj2.evo2;

/** The Python {@code tuple} object. */
class PyTuple implements PyObject {
    static final PyType TYPE = new PyType("tuple", PyType.class);
    @Override
    public PyType getType() { return TYPE; }
    final PyObject[] value;
    PyTuple(PyObject... value) {
        this.value = new PyObject[value.length];
        System.arraycopy(value, 0, this.value, 0, value.length);
    }
    public PyObject getItem(int i) { return value[i]; }
}
