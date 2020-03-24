package uk.co.farowl.vsj2.evo3;

/** The Python {@code tuple} object. */
class PyTuple implements PyObject {

    static final PyType TYPE = new PyType("tuple", PyTuple.class);
    private static final PyObject[] EMPTY_PYOBJECT_ARRAY =
            new PyObject[] {};
    static final PyTuple EMPTY = new PyTuple(EMPTY_PYOBJECT_ARRAY);

    @Override
    public PyType getType() { return TYPE; }
    final PyObject[] value;

    PyTuple(PyObject... value) {
        if (value.length == 0)
            this.value = EMPTY_PYOBJECT_ARRAY;
        else {
            this.value = new PyObject[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /** Construct from an array slice. */
    PyTuple(PyObject a[], int start, int count) {
        this.value = new PyObject[count];
        System.arraycopy(a, start, this.value, 0, count);
    }

    public PyObject getItem(int i) { return value[i]; }

    // slot functions -------------------------------------------------

    static int length(PyTuple self) { return self.value.length; }

    static PyObject sq_item(PyTuple self, int i) {
        try {
            return self.value[i];
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("tuple index out of range");
        }
    }

    static PyObject mp_subscript(PyTuple self, PyObject item)
            throws Throwable {
        PyType itemType = item.getType();
        if (Slot.nb_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.value.length; }
            return sq_item(self, i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }
}
