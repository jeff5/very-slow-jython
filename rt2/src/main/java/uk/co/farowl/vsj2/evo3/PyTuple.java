package uk.co.farowl.vsj2.evo3;

import java.util.Arrays;
import java.util.Collection;
import java.util.StringJoiner;
import java.util.AbstractList;

/** The Python {@code tuple} object. */
class PyTuple /* <E extends PyObject> */ extends AbstractList<PyObject>
        implements PyObject {

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

    private PyTuple(Collection<PyObject> c) {
        int n = c.size();
        if (n == 0)
            this.value = EMPTY_PYOBJECT_ARRAY;
        else {
            this.value = new PyObject[n];
            c.toArray(this.value);
        }
    }

    @Override
    public PyObject get(int i) { return value[i]; } // was: getItem(i)

    @Override
    public int size() { return value.length; }

    /**
     * Return a copy of the contents as an array of the given type, in
     * the provided destination (if not too short) or as a new array of
     * the destination type.
     *
     * @param <T> Type of array element
     * @param a destination array
     * @return the array copy (new or argument)
     * @throws ArrayStoreException if an actual element of the tuple is
     *             not assignment compatible with {@code T}.
     */
    @SuppressWarnings("unchecked")
    <T> T[] asArray(T[] a) throws ArrayStoreException {
        final int na = a.length, nv = value.length;
        if (na < nv) {
            // Destination too short: create an array
            return (T[]) Arrays.copyOf(value, nv, a.getClass());
        } else {
            System.arraycopy(value, 0, a, 0, nv);
        }
        return a;
    }

    @Override
    public String toString() {
        StringJoiner sj = new StringJoiner(", ", "(",
                value.length == 1 ? ",)" : ")");
        for (PyObject v : value)
            sj.add(v.toString());
        return sj.toString();
    }

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

    static PyTuple fromList(Collection<PyObject> c) {
        return c.isEmpty() ? EMPTY : new PyTuple(c);
    }
}
