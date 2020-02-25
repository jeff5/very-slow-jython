package uk.co.farowl.vsj2.evo3;

/** The Python {@code str} object. */
class PyUnicode implements PyObject {

    static final PyType TYPE = new PyType("unicode", PyUnicode.class);

    @Override
    public PyType getType() { return TYPE; }
    final String value; // only supporting BMP for now

    PyUnicode(String value) { this.value = value; }

    PyUnicode(char c) { this.value = String.valueOf(c); }

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public String toString() { return value.toString(); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyUnicode) {
            PyUnicode other = (PyUnicode) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static int length(PyObject s) {
        try {
            return ((PyUnicode) s).value.length();
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject item(PyObject s, int i) {
        try {
            return new PyUnicode(((PyUnicode) s).value.charAt(i));
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("str index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject subscript(PyObject s, PyObject item)
            throws Throwable {
        try {
            PyUnicode self = (PyUnicode) s;
            PyType itemType = item.getType();
            if (Slot.NB.index.isDefinedFor(itemType)) {
                int i = Number.asSize(item, IndexError::new);
                if (i < 0) { i += self.value.length(); }
                return item(self, i);
            }
            // else if item is a PySlice { ... }
            else
                throw Abstract.indexTypeError(self, item);
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }
}
