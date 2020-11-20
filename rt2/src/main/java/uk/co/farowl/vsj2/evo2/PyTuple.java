package uk.co.farowl.vsj2.evo2;

/** The Python {@code tuple} object. */
class PyTuple implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = new PyType("tuple", PyTuple.class);

    @Override
    public PyType getType() { return TYPE; }
    final PyObject[] value;

    PyTuple(PyObject... value) {
        this.value = new PyObject[value.length];
        System.arraycopy(value, 0, this.value, 0, value.length);
    }

    /** Construct from an array slice.
     *
     * @param a array containing objects for the tuple
     * @param start index of first element to take
     * @param count number of elements to take
     */
    PyTuple(PyObject a[], int start, int count) {
        this.value = new PyObject[count];
        System.arraycopy(a, start, this.value, 0, count);
    }

    public PyObject getItem(int i) { return value[i]; }

    // slot functions -------------------------------------------------

    static int length(PyObject s) {
        try {
            return ((PyTuple) s).value.length;
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject item(PyObject s, int i) {
        try {
            return ((PyTuple) s).value[i];
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("tuple index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject subscript(PyObject s, PyObject item)
            throws Throwable {
        try {
            PyTuple self = (PyTuple) s;
            PyType itemType = item.getType();
            if (Slot.NB.index.isDefinedFor(itemType)) {
                int i = Number.asSize(item, IndexError::new);
                if (i < 0) { i += self.value.length; }
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
