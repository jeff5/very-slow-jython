package uk.co.farowl.vsj3.evo1;

import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

/**
 * Abstract API for operations on mapping types, corresponding to
 * CPython methods defined in {@code abstract.h} and with names like:
 * {@code PyMapping_*}.
 */
public class PyMapping extends PySequence {

    protected PyMapping() {}    // only static methods here

    /**
     * Return the mapping object {@code o} as a Java {@code Map}. If
     * {@code o} is one of several built-in types that implement Java
     * {@code Map<Object, Object>}, this will be the object itself.
     * Otherwise, it will be an adapter on the provided object.
     *
     * @param <K> Object (distinguished here for readability)
     * @param <V> Object (distinguished here for readability)
     * @param o to present as a map
     * @return the map
     */
    static <K, V> Map<K, V> map(Object o) {
        if (PyDict.TYPE.check(o)) {
            return (Map<K, V>)o;
        } else {
            return new MapWrapper<K, V>(o);
        }
    }

    static class MapWrapper<K, V> extends AbstractMap<K, V> {
        private final Object map;

        MapWrapper(Object map) { this.map = map; }

        @Override
        public Set<Entry<K, V>> entrySet() {
            return new EntrySetImpl();
        }

        /**
         * An instance of this class is returned by
         * {@link MapWrapper#entrySet()}, and provides the view of the
         * entries in the object supplied to the constructor.
         */
        private class EntrySetImpl extends AbstractSet<Entry<K, V>> {

            @Override
            public Iterator<Entry<K, V>> iterator() {
                return new EntrySetIteratorImpl();
            }

            @Override
            public int size() {
                try {
                    return PySequence.size(map);
                } catch (Throwable t) {
                    throw asUnchecked(t);
                }
            }
        }

        /**
         * An instance of this class is returned by
         * {@link EntrySetImpl#iterator()}.
         *
         * It is backed by a Python iterator on the underlying map.
         */
        private class EntrySetIteratorImpl
                implements Iterator<Entry<K, V>> {

            /**
             * A key object waits here that has been read from the map,
             * e.g. to answer {@link #hasNext()}, but has not yet been
             * returned in a pair by {@link #next()},
             */
            private final Object keyIterator;
            private K nextKey = null;
            private K currKey = null;
            private boolean exhausted = false;

            EntrySetIteratorImpl() throws TypeError {
                try {
                    this.keyIterator = Abstract.getIterator(map);
                } catch (Throwable t) {
                    throw asUnchecked(t);
                }
            }

            /**
             * {@inheritDoc} If necessary, this method reads from the
             * underlying iterator, and the return value depends upon
             * whether that raises a Python {@code StopIteration}.
             */
            @Override
            public boolean hasNext() {
                if (exhausted) {
                    assert nextKey == null;
                    return false;
                } else if (nextKey == null) {
                    // This does not advance this iterator, but ...
                    try {
                        // ... we advance keyIterator to peek at next.
                        nextKey = (K)Abstract.next(keyIterator);
                    } catch (StopIteration si) {
                        exhausted = true;
                        return false;
                    } catch (Throwable t) {
                        throw asUnchecked(t);
                    }
                }
                assert nextKey != null;
                assert exhausted == false;
                return true;
            }

            @Override
            public Entry<K, V> next() {
                if (hasNext()) {
                    // hasNext()==true has already set nextKey
                    K k = nextKey;
                    try {
                        V v = (V)getItem(map, k);
                        currKey = k;
                        nextKey = null;
                        return new SimpleEntry<K, V>(k, v);
                    } catch (Throwable t) {
                        throw asUnchecked(t);
                    }
                } else {
                    throw new NoSuchElementException("Python iterator");
                }
            }

            @Override
            public void remove() {
                if (currKey == null) {
                    // What java.util containers do
                    throw new IllegalStateException();
                } else {
                    try {
                        delItem(map, currKey);
                        currKey = null;
                    } catch (Throwable t) {
                        throw asUnchecked(t);
                    }
                }
            }
        }

    }
}
