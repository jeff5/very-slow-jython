package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.util.AbstractList;
import java.util.Collection;
import java.util.StringJoiner;

import uk.co.farowl.vsj2.evo4.PyType.Spec;

/** The Python {@code tuple} object. */
class PyTuple extends AbstractList<PyObject>
        implements /* PySequence */ PyObject {

    /** The Python type object for {@code tuple}. */
    static final PyType TYPE = PyType.fromSpec( //
            new Spec("tuple", PyTuple.class, MethodHandles.lookup()));

    /** The Python type of this instance. */
    protected final PyType type;

    /** The elements of the {@code tuple}. */
    final PyObject[] value;

    /**
     * Potentially unsafe constructor, capable of creating a
     * "{@code tuple} view" of an array, or a copy. We make a copy (the
     * safe option) if the caller is <b>not</b> prepared to promise
     * <b>not</b> to modify the array. The arguments begin with a
     * claimed element type for the array, or the element type of the
     * array to create.
     *
     * @param type sub-type for which this is being created
     * @param cls class of elements
     * @param iPromiseNotToModifyTheArray if {@code true} try to re-use
     *            the array, otherwise make a copy.
     * @param value of the tuple
     * @throws ArrayStoreException if any element of {@code value} is
     *             not assignment compatible with {@code PyObject}.
     *             Caller would have to have cast {@code value} to avoid
     *             static checks.
     */
    // @SuppressWarnings("unchecked")
    private <E extends PyObject> PyTuple(PyType type,
            boolean iPromiseNotToModifyTheArray, E[] value)
            throws ArrayStoreException {
        this.type = type;
        if (iPromiseNotToModifyTheArray) {
            // The array may safely be cast to match the requested type.
            this.value = (PyObject[]) value;
        } else {
            // We make a new array .
            int n = value.length;
            this.value = new PyObject[n];
            // The copy may throw ArrayStoreException.
            System.arraycopy(value, 0, this.value, 0, n);
        }
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
     * @param <E> component type of the array in the new tuple
     * @param value of the new tuple or {@code null}
     * @return a tuple with the given contents or {@link #EMPTY}
     */
    static <E extends PyObject> PyTuple wrap(E[] value)
            throws ArrayStoreException {
        if (value == null)
            return EMPTY;
        else
            return new PyTuple(TYPE, true, value);
    }

    /**
     * As {@link #PyTuple(PyObject...)} for Python sub-class specifying
     * {@link #type}.
     *
     * @param type actual Python sub-class to being created
     * @param value elements of the tuple
     */
    protected PyTuple(PyType type, PyObject... value) {
        this(type, false, value);
    }

    /**
     * As {@link #PyTuple(Collection)} for Python sub-class specifying
     * {@link #type}.
     *
     * @param type actual Python sub-class to being created
     * @param c elements of the tuple
     */
    protected PyTuple(PyType type, Collection<? extends PyObject> c) {
        this.type = type;
        this.value = c.toArray(new PyObject[c.size()]);
    }

    /**
     * As {@link #PyTuple(PyObject[], int, int)} for Python sub-class
     * specifying {@link #type}.
     *
     * @param type actual Python type to construct
     * @param a source of element values
     * @param start first element to include
     * @param count number of elements to take
     */
    protected PyTuple(PyType type, PyObject a[], int start, int count) {
        this.type = type;
        // We make a new array.
        this.value = new PyObject[count];
        System.arraycopy(a, start, this.value, 0, count);
    }

    /**
     * Construct a {@code PyTuple} from an array of {@link PyObject}s or
     * zero or more {@link PyObject} arguments. The argument is copied
     * for use, so it is safe to modify an array passed in.
     *
     * @param value source of element values for this {@code tuple}
     */
    PyTuple(PyObject... value) { this(TYPE, value); }

    /**
     * Construct a {@code PyTuple} from the elements of a collection.
     *
     * @param c source of element values for this {@code tuple}
     */
    PyTuple(Collection<? extends PyObject> c) { this(TYPE, c); }

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
        this(TYPE, a, start, count);
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

    // Special methods -----------------------------------------------

    int __len__() { return size(); }

    PyObject __getitem__(PyObject item) throws Throwable {
        PyType itemType = item.getType();
        if (Slot.op_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += size(); }
            return getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    // AbstractList methods ------------------------------------------

    @Override
    public PyObject get(int i) { return value[i]; }

    @Override
    public int size() { return value.length; }

    // Sequence interface --------------------------------------------

    PyObject getItem(int i) {
        try {
            return get(i);
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("tuple");
        }
    }

    // Plumbing ------------------------------------------------------

    @Override
    public PyType getType() { return type; }

    @Override
    public String toString() {
        // Support the expletive comma "(x,)" for one element.
        String suffix = value.length == 1 ? ",)" : ")";
        StringJoiner sj = new StringJoiner(", ", "(", suffix);
        for (Object v : value) { sj.add(v.toString()); }
        return sj.toString();
    }
}
