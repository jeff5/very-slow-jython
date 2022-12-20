package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.StringJoiner;

import uk.co.farowl.vsj2.evo4.PyType.Spec;

/** The Python {@code list} object. */
class PyList extends ArrayList<PyObject> implements PySequence {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new Spec("list", PyList.class, MethodHandles.lookup()));

    @Override
    public PyType getType() { return TYPE; }

    /** Construct empty. */
    PyList() { super(); }

    /**
     * Construct empty, with specified capacity.
     *
     * @param capacity initial capacity (not length)
     */
    PyList(int capacity) { super(capacity); }

    /**
     * Construct from an array slice.
     *
     * @param a array containing objects for the list
     * @param start index of first element to take
     * @param count number of elements to take
     */
    PyList(PyObject a[], int start, int count) {
        super(count);
        for (int i = start; i < start + count; i++) { add(a[i]); }
    }

    // Sequence interface ---------------------------------------------

    PyObject getItem(int i) {
        try {
            return get(i);
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("list");
        }
    }

    @Override
    public PyList repeat(int n) {
        PyList r = new PyList(n * size());
        for (int i = 0; i < n; i++) { r.addAll(this); }
        return r;
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private PyObject __repr__() throws Throwable {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (PyObject e : this) { sj.add(e.toString()); }
        return Py.str(sj.toString());
    }

    @SuppressWarnings("unused")
    private PyObject __mul__(PyObject n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private PyObject __rmul__(PyObject n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private int __len__() { return size(); }

    private void __setitem__(int i, PyObject o) {
        try {
            set(i, o);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        }
    }

    @SuppressWarnings("unused")
    private PyObject __getitem__(PyObject item) throws Throwable {
        PyType itemType = item.getType();
        if (Slot.op_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += this.size(); }
            return getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    @SuppressWarnings("unused")
    private void __setitem__(PyObject item, PyObject value)
            throws Throwable {
        PyType itemType = item.getType();
        if (Slot.op_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += this.size(); }
            __setitem__(i, value);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    // methods -------------------------------------------------

    PyObject extend(PyObject iterable) {
        // ...: stop-gap implementation until iterables supported
        if (iterable instanceof PyList)
            addAll((PyList) iterable);
        else if (iterable instanceof PyTuple) {
            PyObject[] src = ((PyTuple) iterable).value;
            addAll(Arrays.asList(src));
        } else
            throw Abstract.typeError("Unsupported iterable", iterable);
        return Py.None;
    }
}
