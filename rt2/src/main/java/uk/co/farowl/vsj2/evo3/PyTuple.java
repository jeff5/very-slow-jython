package uk.co.farowl.vsj2.evo3;

import java.util.Collection;

/** The Python {@code tuple} object. */
public class PyTuple extends TypedTuple<PyObject> {

    /**
     * Construct a {@code PyTuple} from an array of {@link PyObject}s or
     * zero or more {@link PyObject} arguments. The argument is copied
     * for use, so it is safe to modify an array passed in.
     *
     * @param value source of element values for this {@code tuple}
     */
    PyTuple(PyObject... value) { super(PyObject.class, value); }

    /**
     * Construct a {@code PyTuple} from the elements of a collection.
     *
     * @param value source of element values for this {@code tuple}
     */
    PyTuple(Collection<? extends PyObject> c) {
        super(PyObject.class, c);
    }

    /**
     * Construct a {@code PyTuple} from an array of {@link PyObject}s or
     * zero or more {@link PyObject} arguments provided as a slice of an
     * array. The argument is copied for use, so it is safe to modify an
     * array passed in.
     *
     * @param a source of element values
     * @param start first element to include
     * @param count number of elements to take
     */
    PyTuple(PyObject a[], int start, int count) {
        super(PyObject.class, a, start, count);
    }

    /**
     * Construct a {@code PyTuple} from the elements of a collection, or
     * if the collection is empty, return {@link #EMPTY}. In
     * circumstances where the argument will often be empty, this has
     * space and time advantages over the constructor
     * {@link #PyTuple(Collection)}.
     */
    static <E extends PyObject> PyTuple fromList(Collection<E> c) {
        if (c.size() == 0)
            return EMPTY;
        else
            return new PyTuple(c);
    }

    /** Convenient constant for a {@code tuple} with zero elements. */
    static final PyTuple EMPTY = new PyTuple();
}
