package uk.co.farowl.vsj2.evo3;

import java.lang.reflect.Array;
import java.util.AbstractList;
import java.util.Collection;
import java.util.List;
import java.util.StringJoiner;

/**
 * Implementation class behind {@link PyTuple}, providing a type-safe
 * access to elements.
 * @param <E> the type (or super-type) of the elements.
 */
class TypedTuple<E extends PyObject> extends AbstractList<E>
        implements Tuple<E> {

    final E[] value;

    /** Construct from an array or multiple values passed as arguments. */
    @SuppressWarnings("unchecked")
    TypedTuple(Class<E> cls, PyObject... value) {
        int n = value.length;
        this.value = (E[]) Array.newInstance(cls, n);
        System.arraycopy(value, 0, this.value, 0, value.length);
    }

    /** Construct from an array slice. */
    @SuppressWarnings("unchecked")
    TypedTuple(Class<E> cls, PyObject a[], int start, int count) {
        this.value = (E[]) Array.newInstance(cls, count);
        System.arraycopy(a, start, this.value, 0, count);
    }

    /** Construct from collection. */
    @SuppressWarnings("unchecked")
    TypedTuple(Class<E> cls, Collection<? extends E> c) {
        int n = c.size();
        E[] a = (E[]) Array.newInstance(cls, n);
        this.value = c.toArray(a);
    }

    @Override
    public E get(int i) { return value[i]; } // was: getItem(i)

    @Override
    public int size() { return value.length; }

    @Override
    public String toString() {
        // Support the expletive comma "(x,)" for one element.
        String suffix = value.length == 1 ? ",)" : ")";
        StringJoiner sj = new StringJoiner(", ", "(", suffix);
        for (E v : value) { sj.add(v.toString()); }
        return sj.toString();
    }
}

