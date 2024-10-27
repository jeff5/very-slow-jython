package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.NoSuchElementException;

/** Placeholder until implemented. */
// FIXME implement me
class PyTuple extends AbstractList<Object> implements WithClass {

    /** The Python type object for {@code tuple}. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("tuple", MethodHandles.lookup()));

    /** The elements of the {@code tuple}. */
    private final Object[] value;

    /**
     * Construct a {@code PyTuple} from a slice of an array of
     * {@link Object}s specified by position and count. The argument is
     * copied for use, so it is safe to modify an array passed in.
     *
     * @param a source of element values
     * @param start first element to include
     * @param count number of elements to take
     */
    PyTuple(Object a[], int start, int count) {
        // We make a new array.
        this.value = new Object[count];
        System.arraycopy(a, start, this.value, 0, count);
    }

    /**
     * Construct a {@code PyTuple} from a slice of another
     * {@code PyTuple} specified by position and count.
     *
     * @param a source of element values
     * @param start first element to include
     * @param count number of elements to take
     */
    PyTuple(PyTuple a, int start, int count) {
        this(a.value, start, count);
    }

    public PyTuple(Object... v) { this(v, 0, v.length); }

    @Override
    public PyType getType() { return TYPE; }

    /** Convenient constant for a {@code tuple} with zero elements. */
    static final PyTuple EMPTY = new PyTuple();

    // Java API ------------------------------------------------------

    /**
     * Copy from the tuple value to a destination array provided by the
     * caller.
     *
     * @param srcPos
     * @param dst
     * @param dstPos
     * @param length
     */
    public void copyTo(int srcPos, Object[] dst, int dstPos,
            int length) {
        System.arraycopy(value, srcPos, dst, dstPos, length);
    }

    // AbstractList methods ------------------------------------------

    @Override
    public Object get(int i) { return value[i]; }

    @Override
    public int size() { return value.length; }

    @Override
    public Iterator<Object> iterator() { return listIterator(0); }

    @Override
    public ListIterator<Object> listIterator(final int index) {

        if (index < 0 || index > value.length)
            throw new IndexOutOfBoundsException(String
                    .format("%d outside [0, %d)", index, value.length));

        return new ListIterator<Object>() {

            private int i = index;

            @Override
            public boolean hasNext() { return i < value.length; }

            @Override
            public Object next() {
                if (i < value.length)
                    return value[i++];
                else
                    throw new NoSuchElementException();
            }

            @Override
            public boolean hasPrevious() { return i > 0; }

            @Override
            public Object previous() {
                if (i > 0)
                    return value[--i];
                else
                    throw new NoSuchElementException();
            }

            @Override
            public int nextIndex() { return i; }

            @Override
            public int previousIndex() { return i - 1; }

            @Override
            public void remove() {
                throw new UnsupportedOperationException();
            }

            @Override
            public void set(Object o) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void add(Object o) {
                throw new UnsupportedOperationException();
            }
        };
    }

    @Override
    public String toString() { return PyUtil.defaultToString(this); }
}
