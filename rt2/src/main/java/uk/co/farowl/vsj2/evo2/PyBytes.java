package uk.co.farowl.vsj2.evo2;

/** The Python {@code bytes} object. */
class PyBytes implements PyObject {
    static final PyType TYPE = new PyType("bytes", PyType.class);
    @Override
    public PyType getType() { return TYPE; }
    final byte[] value;
    PyBytes(byte[] value) {
        this.value = new byte[value.length];
        System.arraycopy(value, 0, this.value, 0, value.length);
    }
}
