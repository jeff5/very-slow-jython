package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyType.Spec;

/** Stop-gap definition to satisfy references in the project. */
public class PyList extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;

    public static final PyType TYPE =
            PyType.fromSpec(new Spec("list", MethodHandles.lookup()));

    /** Construct an empty {@code list}. */
    public PyList() {}

    /**
     * Construct an empty {@code list} with the specified initial
     * capacity.
     *
     * @param initialCapacity the initial capacity of the list
     */
    public PyList(int initialCapacity) {
        super(Math.max(0, initialCapacity));
    }

    /**
     * Construct a {@code list} with initial contents from a collection.
     *
     * @param c initial contents
     */
    public PyList(Collection<?> c) { super(c); }

    /**
     * Construct a {@code list} with initial contents from an array
     * slice.
     *
     * @param a the array
     * @param start of slice
     * @param count of elements to take
     */
    PyList(Object[] a, int start, int count) {
        super(count);
        int stop = start + count;
        for (int i = start; i < stop; i++) { add(a[i]); }
    }

    /** Reverse this list in-place. */
    @PythonMethod
    void reverse() {
        final int N = size(), M = N / 2;
        // We can accomplish the reversal in M swaps
        for (int i = 0, j = N; i < M; i++) {
            Object x = get(i);
            set(i, get(--j));
            set(j, x);
        }
    }

    int __len__() { return size(); }

    Object __eq__(Object other) {
        if (other instanceof PyList) {
            // A Python list is comparable only with another list
            return this.equals(other);
        } else {
            return Py.NotImplemented;
        }
    }

    Object __getitem__(Object index) throws Throwable {
        return get(PyNumber.asSize(index, null));
    }

    void __setitem__(Object index, Object v) throws Throwable {
        set(PyNumber.asSize(index, null), v);
    }

}
