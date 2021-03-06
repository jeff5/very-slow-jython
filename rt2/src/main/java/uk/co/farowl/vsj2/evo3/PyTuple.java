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
     * @param c source of element values for this {@code tuple}
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
     *
     * @param <E> component type
     * @param c value of new tuple
     * @return a tuple with the given contents or {@link #EMPTY}
     */
    static <E extends PyObject> PyTuple from(Collection<E> c) {
        if (c.size() == 0)
            return EMPTY;
        else
            return new PyTuple(c);
    }

    /** Convenient constant for a {@code tuple} with zero elements. */
    static final PyTuple EMPTY = new PyTuple();

    private PyTuple(boolean iPromiseNotToModifyTheArray,
            PyObject[] value) throws ArrayStoreException {
        super(PyObject.class, iPromiseNotToModifyTheArray, value);
    }

    /**
     * Unsafely wrap an array of {@code PyObject} (or of a type
     * assignable to {@code PyObject}) in a "tuple view".
     * <p>
     * The method is unsafe insofar as the array becomes embedded as the
     * value of the tuple. <b>The client therefore promises not to
     * modify the content.</b> For this reason, this method should only
     * ever have package visibility.
     *
     * @param <T> component type of the array in the new tuple
     * @param value of the new tuple or {@code null}
     * @return a tuple with the given contents or {@link #EMPTY}
     */
    static <T extends PyObject> PyTuple wrap(T[] value) {
        if (value == null)
            return EMPTY;
        else
            return new PyTuple(true, value);
    }

}
