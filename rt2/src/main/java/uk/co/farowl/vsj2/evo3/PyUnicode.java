package uk.co.farowl.vsj2.evo3;

/** The Python {@code str} object. */
class PyUnicode implements PyObject, Comparable<PyUnicode> {

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
    public int compareTo(PyUnicode o) {
        return value.compareTo(o.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyUnicode) {
            return this.value.equals(((PyUnicode) obj).value);
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static int length(PyUnicode s) { return s.value.length(); }

    static PyObject tp_richcompare(PyUnicode self, PyObject other, Comparison op) {
        if (other instanceof PyUnicode)
            return  op.toBool(self.compareTo((PyUnicode)other));
        else
            return Py.NotImplemented;
    }

    static PyObject sq_item(PyUnicode self, int i) {
        try {
            return new PyUnicode(self.value.charAt(i));
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("str index out of range");
        }
    }

    static PyObject mp_subscript(PyUnicode self, PyObject item)
            throws Throwable {
        PyType itemType = item.getType();
        if (Slot.nb_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.value.length(); }
            return sq_item(self, i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }
}
