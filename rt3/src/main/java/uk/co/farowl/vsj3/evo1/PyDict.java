package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.AbstractMap;
import java.util.AbstractSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Python {@code dict} object. The Java API is provided directly by
 * the base class implementing {@code Map}, while the Python API has
 * been implemented on top of the Java one.
 */
class PyDict extends AbstractMap<Object, Object>
        implements CraftedType {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("dict", MethodHandles.lookup()));

    /** The dictionary as a hash map preserving insertion order. */
    private final LinkedHashMap<Key, Object> map =
            new LinkedHashMap<Key, Object>();

    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() {
        return Py.defaultToString(this);
    }

    /**
     * Override {@code Map.get} to give keys Python semantics.
     *
     * @param key whose associated value is to be returned
     * @return value at {@code key} or {@code null} if not found
     */
    @Override
    public Object get(Object key) {
        try {
            return map.get(new Key(key));
        } catch (PyException e) {
            // A PyException is allowed to propagate as itself
            throw e;
        } catch (Throwable e) {
            // Tunnel out non-Python errors as InterpreterError
            throw new InterpreterError(e, "during PyDict.get(%s)",
                    PyType.of(key));
        }
    }

    /**
     * Override {@code Map.put} to give keys Python semantics.
     *
     * @param key with which the specified value is to be associated
     * @param value to be associated
     * @return previous value associated
     */
    @Override
    public Object put(Object key, Object value) {
        try {
            return map.put(new Key(key), value);
        } catch (PyException e) {
            // A PyException is allowed to propagate as itself
            throw e;
        } catch (Throwable e) {
            // Tunnel out non-Python errors as InterpreterError
            throw new InterpreterError(e, "during dict.put(%s, %s)",
                    PyType.of(key), PyType.of(value));
        }
    }

    /**
     * Override {@code Map.putIfAbsent} to give keys Python semantics.
     *
     * @param key with which the specified value is to be associated
     * @param value to be associated
     * @return previous value associated
     */
    @Override
    public Object putIfAbsent(Object key, Object value) {
        try {
            return map.putIfAbsent(new Key(key), value);
        } catch (PyException e) {
            // A PyException is allowed to propagate as itself
            throw e;
        } catch (Throwable e) {
            // Tunnel out non-Python errors as InterpreterError
            throw new InterpreterError(e, "during putIfAbsent(%s, %s)",
                    PyType.of(key), PyType.of(value));
        }
    }

    /** Modes for use with {@link #merge(Object, MergeMode)}. */
    enum MergeMode {
        PUT, IF_ABSENT, UNIQUE
    }

    /**
     * Merge the mapping {@code src} into this {@code dict}. This
     * supports the {@code BUILD_MAP_UNPACK_WITH_CALL} opcode.
     *
     * @param src to merge in
     * @param mode what to do about duplicates
     * @throws KeyError on duplicate key (and {@link MergeMode#UNIQUE})
     */
    // Compare CPython dict_merge and _PyDict_MergeEx in dictobject.c
    void merge(Object src, MergeMode mode) throws KeyError {
        // XXX: stop-gap implementation
        if (src instanceof PyDict) {
            Set<Map.Entry<Object, Object>> entries =
                    ((PyDict) src).entrySet();
            for (Map.Entry<Object, Object> e : entries) {
                Object k = e.getKey();
                Object v = e.getValue();
                if (mode == MergeMode.PUT)
                    put(k, v);
                else {
                    Object u = putIfAbsent(k, v);
                    if (u != null && mode == MergeMode.UNIQUE)
                        throw new KeyError(k, "duplicate");
                }
            }
        } else
            throw new AttributeError("Unsupported mapping type %s",
                    PyType.of(src).getName());
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private Object __repr__() throws Throwable {
        return Py.str(PyObjectUtil.mapRepr(this));
    }

    @SuppressWarnings("unused")
    private Object __getitem__(Object key) {
        // This may be over-simplifying things but ... :)
        return get(key);
    }

    // methods --------------------------------------------------------

    Object update(Object args) {
        // XXX: stop-gap implementation
        if (args instanceof PyDict)
            merge(args, MergeMode.PUT);
        else
            throw new AttributeError("Unsupported mapping", args);
        return Py.None;
    }

    // Non-Python API -------------------------------------------------

    /**
     * In order to give Python semantics to objects used as keys, when
     * using a Java container as the implementation, it is necessary to
     * intercept the calls Java will make to {@code Object.hashCode} and
     * {@code Object.equals}, and direct them to {@code __hash__} and
     * {@code __eq__}.
     */
    static class Key {

        Object obj;
        int hash;

        /**
         * Create a key on the given object Python {@code __eq__}
         * definitions on objects offered as keys.
         *
         * @throws PyException from {@code __eq__}
         */
        Key(Object obj) throws TypeError, Throwable {
            this.obj = obj;
            this.hash = Abstract.hash(obj);
        }

        @Override
        public int hashCode() {
            return hash;
        }

        /**
         * Impose Python {@code __eq__} definitions on objects offered
         * as keys.
         *
         * @throws PyException from {@code __eq__}
         */
        @Override
        public boolean equals(Object other) throws PyException {
            // De-reference the key to its contents
            if (!(other instanceof Key)) { return false; }
            Object otherObj = ((Key) other).obj;
            // Quick answer if it contains the same object
            if (otherObj == obj) { return true; }
            // Otherwise, make a full comparison
            try {
                Object r = Comparison.EQ.apply(obj, otherObj);
                return Abstract.isTrue(r);
            } catch (PyException e) {
                // A PyException is allowed to propagate as itself
                throw e;
            } catch (Throwable e) {
                // Tunnel out non-Python errors as internal
                throw new InterpreterError(e, "during equals(%s, %s)",
                        PyType.of(obj), PyType.of(otherObj));
            }
        }

        @Override
        public String toString() {
            return String.format("Key(%s)", obj);
        }
    }

    // Map interface --------------------------------------------------

    @Override
    public Set<Entry<Object, Object>> entrySet() {
        return new EntrySetImpl();
    }

    /**
     * An instance of this class is returned by
     * {@link PyDict#entrySet()}, and provides the view of the entries
     * in the {@code PyDict} mentioned there.
     * <p>
     * It is probably also the backing for a {@code dict_keys}.
     */
    private class EntrySetImpl
            extends AbstractSet<Entry<Object, Object>> {

        @Override
        public Iterator<Entry<Object, Object>> iterator() {
            return new EntrySetIteratorImpl();
        }

        @Override
        public int size() {
            return map.size();
        }
    }

    /**
     * An instance of this class is returned by
     * {@link EntrySetImpl#iterator()}. It is backed by an iterator on
     * the underlying {@link #map}, and its job is to return an entry in
     * which the {@link PyDict#Key} has been replaced with its contained
     * object, the true key at the Python level.
     */
    private class EntrySetIteratorImpl
            implements Iterator<Entry<Object, Object>> {

        /** Backing iterator on the "real" implementation. */
        private final Iterator<Entry<Key, Object>> mapIterator =
                map.entrySet().iterator();

        @Override
        public boolean hasNext() {
            return mapIterator.hasNext();
        }

        /**
         * {@inheritDoc} The difference from the underlying
         * {@link mapIterator} is that the key in the entry returned by
         * this method is the object embedded in the {@link Key}, which
         * is the key as far as Python is concerned.
         */
        @Override
        public Entry<Object, Object> next() {
            Entry<Key, Object> e = mapIterator.next();
            return new SimpleEntry<Object, Object>(e.getKey().obj,
                    e.getValue());
        }

        @Override
        public void remove() {
            mapIterator.remove();
        }
    }

    // plumbing -------------------------------------------------------

}
