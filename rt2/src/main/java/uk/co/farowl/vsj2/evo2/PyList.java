package uk.co.farowl.vsj2.evo2;

import java.util.ArrayList;

/** The Python {@code list} object. */
class PyList extends ArrayList<PyObject> implements PyObject {

    static final PyType TYPE = new PyType("list", PyList.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Construct empty. */
    PyList() { super(); }

    /** Construct from an array slice. */
    PyList(PyObject a[], int start, int end) {
        super(end - start);
        for (int i = start; i < end; i++) { add(a[i]); }
    }

    static int length(PyObject s) {
        try {
            return ((PyList) s).size();
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static PyObject item(PyObject s, int i) {
        try {
            return ((PyList) s).get(i);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }

    static void ass_item(PyObject s, int i, PyObject o) {
        try {
            ((PyList) s).set(i, o);
        } catch (IndexOutOfBoundsException e) {
            throw new IndexError("list index out of range");
        } catch (ClassCastException e) {
            throw PyObjectUtil.typeMismatch(s, TYPE);
        }
    }
}
