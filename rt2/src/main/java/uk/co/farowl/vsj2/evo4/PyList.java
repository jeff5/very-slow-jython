package uk.co.farowl.vsj2.evo4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;
import java.util.StringJoiner;

/** The Python {@code list} object. */
class PyList extends ArrayList<PyObject> implements PyObject {

    static final PyType TYPE = new PyType("list", PyList.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Construct empty. */
    PyList() { super(); }

    /** Construct empty, with specified capacity. */
    PyList(int capacity) {
        super(capacity);
    }

    /** Construct from an array slice. */
    PyList(PyObject a[], int start, int count) {
        super(count);
        for (int i = start; i < start + count; i++) { add(a[i]); }
    }

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyList self) throws Throwable {
        StringJoiner sj = new StringJoiner(", ", "[", "]");
        for (PyObject e: self) {
            sj.add(e.toString());
        }
        return Py.str(sj.toString());
    }

    static PyObject __mul__(PyList self, int n) {
        PyList r = new PyList(n * self.size());
        for (int i = 0; i < n; i++)
            r.addAll(self);
        return r;
    }

    static int __len__(PyList s) { return s.size(); }

    static PyObject __getitem__(PyList self, int i) {
        try {
            return ((PyList) self).get(i);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        }
    }

    static void __setitem__(PyList self, int i, PyObject o) {
        try {
            self.set(i, o);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        }
    }

    static PyObject __getitem__(PyList self, PyObject item)
            throws Throwable {
        PyType itemType = item.getType();
        if (Slot.nb_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.size(); }
            return __getitem__(self, i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }

    static void __setitem__(PyList self, PyObject item,
            PyObject value) throws Throwable {
        PyType itemType = item.getType();
        if (Slot.nb_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.size(); }
            __setitem__(self, i, value);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }

    // methods -------------------------------------------------

    PyObject extend(PyObject iterable) {
        // XXX: stop-gap implementation until iterables supported
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
