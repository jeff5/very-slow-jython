package uk.co.farowl.vsj2.evo2;

/** The Python {@code tuple} object. */
class PyTuple implements PyObject {

    static final PyType TYPE = new PyType("tuple", PyTuple.class);

    @Override
    public PyType getType() { return TYPE; }
    final PyObject[] value;

    PyTuple(PyObject... value) {
        this.value = new PyObject[value.length];
        System.arraycopy(value, 0, this.value, 0, value.length);
    }

    /** Construct from an array slice. */
    PyTuple(PyObject a[], int start, int end) {
        this.value = new PyObject[end - start];
        System.arraycopy(a, start, this.value, 0, value.length);
    }

    public PyObject getItem(int i) { return value[i]; }

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

}
