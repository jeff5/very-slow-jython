package uk.co.farowl.vsj2.evo2;

/** The Python {@code str} object. */
class PyUnicode implements PyObject {
    static final PyType TYPE = new PyType("unicode", PyUnicode.class);
    @Override
    public PyType getType() { return TYPE; }
    final String value; // only supporting BMP for now
    PyUnicode(String value) { this.value = value; }
    @Override
    public int hashCode() { return value.hashCode(); }
    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyUnicode) {
            PyUnicode other = (PyUnicode) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }
}
