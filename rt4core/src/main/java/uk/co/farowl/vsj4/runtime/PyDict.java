package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Python {@code dict} object. The Java API is provided directly by
 * the base class implementing {@code Map}, while the Python API has
 * been implemented on top of the Java one.
 */
public class PyDict extends LinkedHashMap<Object, Object>
        implements WithClass {

    /** The type of Python object this class implements. */
    public static final PyType TYPE = PyType.fromSpec( //
            new TypeSpec("dict", MethodHandles.lookup()));

    /*
     * We know from vsj3 that this is inadequate as an implementation
     * because of the hashing and equality of keys. It is a stop-gap
     * while we develop the interfaces around Python object and type
     * definition. When the time comes, copy the one in vsj3.
     */

    /**
     * Construct a dictionary filled by copying from a given Java map.
     *
     * @param <K> key type of incoming map
     * @param <V> value type of incoming map
     * @param map Java map from which to copy
     */
    protected <K, V> PyDict(PyType type, Map<K, V> map) {
        // Cannot bulk add since keys may need Pythonising
        for (Map.Entry<K, V> e : map.entrySet()) {
            put(e.getKey(), e.getValue());
        }
    }

    /** Construct an empty {@code dict}. */
    public PyDict() {}

    @Override
    public PyType getType() { return TYPE; }
}
