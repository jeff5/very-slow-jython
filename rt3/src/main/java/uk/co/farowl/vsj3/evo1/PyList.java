package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.Collection;

import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyType.Spec;

/** Stop-gap definition to satisfy references in the project. */
public class PyList extends ArrayList<Object> {
    private static final long serialVersionUID = 1L;

    public static PyType TYPE =
            PyType.fromSpec(new Spec("list", MethodHandles.lookup()));

    public PyList() {}

    public PyList(Collection<?> c) { super(c); }

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

    Object __getitem__(Object index) throws Throwable {
        return get(PyNumber.asSize(index, null));
    }

}
