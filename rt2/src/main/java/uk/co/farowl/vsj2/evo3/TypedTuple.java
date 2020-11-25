package uk.co.farowl.vsj2.evo3;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.StringJoiner;

/**
 * Implementation class behind {@link PyTuple}, providing a type-safe
 * access to elements.
 *
 * @param <E> the type (or super-type) of the elements.
 */
class TypedTuple<E extends PyObject> extends AbstractList<E>
        implements Tuple<E> {

    final E[] value;

    /**
     * Construct from an array or multiple values passed as arguments.
     *
     * @param cls class of elements
     * @param value of the tuple
     * @throws ArrayStoreException if any element of {@code value} is
     *             not assignment compatible with {@code cls}
     */
    TypedTuple(Class<E> cls, PyObject... value)
            throws ArrayStoreException {
        // Use the "unsafe" constructor in safe mode.
        this(cls, false, value);
    }

    /**
     * Construct from an array slice.
     *
     * @param cls class of elements
     * @param a from which to take values
     * @param start index of first value
     * @param count number of values to take
     * @throws ArrayStoreException if any element of {@code value} is
     *             not assignment compatible with {@code cls}
     */
    @SuppressWarnings("unchecked")
    TypedTuple(Class<E> cls, PyObject a[], int start, int count)
            throws ArrayStoreException {
        this.value = (E[]) Array.newInstance(cls, count);
        System.arraycopy(a, start, this.value, 0, count);
    }

    /**
     * Construct from collection.
     *
     * @param cls class of elements
     * @param c value of the tuple
     * @throws ArrayStoreException if any element of {@code c} is not
     *             assignment compatible with {@code cls}
     */
    @SuppressWarnings("unchecked")
    TypedTuple(Class<E> cls, Collection<?> c)
            throws ArrayStoreException {
        int n = c.size();
        E[] a = (E[]) Array.newInstance(cls, n);
        this.value = c.toArray(a);
    }

    @Override
    public E get(int i) { return value[i]; } // was: getItem(i)

    @Override
    public int size() { return value.length; }

    @SuppressWarnings("unchecked")
    <T extends PyObject> T[] items(Class<T> cls) {
        if (cls.isAssignableFrom(value.getClass().getComponentType()))
            return (T[]) value;
        else
            throw new ClassCastException();
    }

    // Plumbing -------------------------------------------------

    /**
     * Potentially unsafe constructor, capable of creating a
     * "{@code tuple} view" of an array, or a copy. We make a copy (the
     * safe option) if the caller is <b>not</b> prepared to promise
     * <b>not</b> to modify the array. The arguments begin with a
     * claimed element type for the array, or the element type of the
     * array to create.
     *
     * @param cls class of elements
     * @param iPromiseNotToModifyTheArray if {@code true} try to re-use
     *            the array, otherwise make a copy.
     * @param value of the tuple
     * @throws ArrayStoreException if any element of {@code value} is
     *             not assignment compatible with {@code cls}
     */
    @SuppressWarnings("unchecked")
    protected TypedTuple(Class<E> cls,
            boolean iPromiseNotToModifyTheArray, PyObject[] value)
            throws ArrayStoreException {
        int n = value.length;
        if (iPromiseNotToModifyTheArray && cls.isAssignableFrom(
                value.getClass().getComponentType())) {
            // The array may be safely cast to match the requested type.
            this.value = (E[]) value;
        } else {
            // We make a new array of element type E.
            this.value = (E[]) Array.newInstance(cls, n);
            // The copy may throw ArrayStoreException.
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /**
     * Produce slightly unsafe "tuple view" of an array, each of whose
     * elements must be assignable to the given class. The method is
     * unsafe because of the possibility that the client will
     * subsequently modify the array. It will therefore never be public
     * API. The main use for this is to return as a {@code tuple} to
     * Python, an attribute that is kept internally as an array,
     * possibly guaranteeing a particular component type (e.g.
     * {@link PyCode#names} returned by {@link PyCode#getNames()}).
     *
     * @param <T> the type of the elements.
     * @param cls class of elements
     * @param value of the tuple
     * @return wrapped array
     * @throws ArrayStoreException if any element of {@code value} is
     *             not assignment compatible with {@code cls}
     */
    static <T extends PyObject> TypedTuple<T> wrap(Class<T> cls,
            T[] value) {
        return new TypedTuple<T>(cls, true, value);
    }

    @Override
    public String toString() {
        // Support the expletive comma "(x,)" for one element.
        String suffix = value.length == 1 ? ",)" : ")";
        StringJoiner sj = new StringJoiner(", ", "(", suffix);
        for (E v : value) { sj.add(v.toString()); }
        return sj.toString();
    }
}
