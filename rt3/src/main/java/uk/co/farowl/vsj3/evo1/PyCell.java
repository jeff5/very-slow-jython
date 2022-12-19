package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.function.Supplier;

/**
 * Holder for objects appearing in the closure of a function. There is
 * only a default constructor {@code PyCell()} because cells always
 * start life empty.
 */
public class PyCell implements Supplier<Object>, CraftedPyObject {

    /** The Python type {@code cell}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("cell", MethodHandles.lookup())
                    // Type admits no Python subclasses.
                    .flagNot(PyType.Flag.BASETYPE));

    /** The object currently held. */
    Object obj;

    /** Handy constant where no cells are needed in a frame. */
    static final PyCell[] EMPTY_ARRAY = new PyCell[0];

    // Java API -------------------------------------------------------

    /**
     * Get an array of {@code PyCell}s, some empty and the rest
     * initialised from an existing array, in that order. Create a new
     * array if the overall length will not be zero.
     *
     * @param n number of cells in array to be empty
     * @param closure to append (or {@code null})
     * @return the array
     */
    static PyCell[] array(int n, PyCell[] closure) {
        // We will copy m elements to location n, overall length is L
        int m = closure == null ? 0 : closure.length, L = m + n;
        if (L <= 0) {
            return EMPTY_ARRAY;
        } else {
            PyCell[] a = new PyCell[L];
            for (int i = 0; i < n; i++) { a[i] = new PyCell(); }
            if (m > 0) { System.arraycopy(closure, 0, a, n, m); }
            return a;
        }
    }

    @Override
    public PyType getType() { return TYPE; }

    @Exposed.Getter
    private Object cell_contents() {
        return PyObjectUtil.errorIfNull(obj,
                () -> new ValueError("Cell is empty"));
    }

    @Override
    public Object get() { return obj; }

    @Exposed.Setter("cell_contents")
    public void set(Object v) { obj = v; }

    @Exposed.Deleter("cell_contents")
    public void del() { obj = null; }

    // Compare CPython cell_repr in cellobject.c
    @Override
    public String toString() {
        if (obj == null) {
            return String.format("<cell at %#x: empty>", Py.id(this));
        } else {
            return String.format("<cell at %#x: [%.100s]>", Py.id(this),
                    obj);
            // Or as in CPython, but less informative:
            // return String.format(
            // "<cell at %#x: %.80s object at %#x>",
            // Py.id(this), PyType.of(obj).getName(), Py.id(obj));
        }
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private Object __repr__() { return toString(); }

    @SuppressWarnings("unused")
    private Object __str__() { return toString(); }
}
