package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * The Python {@code dict} object. The Java API is provided directly by
 * the base class implementing {@code Map}, while the Python API has
 * been implemented on top of the Java one.
 */
class PyDict extends LinkedHashMap<PyObject, PyObject>
        implements Map<PyObject, PyObject>, PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("dict", PyDict.class,
                    MethodHandles.lookup()));

    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    /**
     * Specialisation of {@code Map.get} allowing Java {@code String}
     * keys.
     *
     * @param key whose associated value is to be returned
     * @return value at {@code key} or {@code null} if not found
     */
    PyObject get(String key) { return this.get(Py.str(key)); }

    /**
     * Specialisation of {@code Map.put()} allowing Java {@code String}
     * keys.
     *
     * @param key with which the specified value is to be associated
     * @param value to be associated
     * @return previous value associated
     */
    PyObject put(String key, PyObject value) {
        return this.put(Py.str(key), value);
    }

    /**
     * Specialisation of {@code Map.putIfAbsent()} allowing Java
     * {@code String} keys.
     *
     * @param key with which the specified value is to be associated
     * @param value to be associated
     * @return previous value associated
     */
    PyObject putIfAbsent(String key, PyObject value) {
        return this.putIfAbsent(Py.str(key), value);
    }

    /** Modes for use with {@link #merge(PyObject, MergeMode)}. */
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
    void merge(PyObject src, MergeMode mode) throws KeyError {
        // XXX: stop-gap implementation
        if (src instanceof PyDict) {
            Set<Map.Entry<PyObject, PyObject>> entries =
                    ((PyDict) src).entrySet();
            for (Map.Entry<PyObject, PyObject> e : entries) {
                PyObject k = e.getKey();
                PyObject v = e.getValue();
                if (mode == MergeMode.PUT)
                    put(k, v);
                else {
                    PyObject u = putIfAbsent(k, v);
                    if (u != null && mode == MergeMode.UNIQUE)
                        throw new KeyError(k, "duplicate");
                }
            }
        } else
            throw new AttributeError("Unsupported mapping type %s",
                    src.getType().getName());
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private PyObject __repr__() throws Throwable {
        return Py.str(PyObjectUtil.mapRepr(this));
    }

    @SuppressWarnings("unused")
    private PyObject __getitem__(PyObject key) {
        // This may be over-simplifying things but ... :)
        return get(key);
    }

    // methods -------------------------------------------------

    PyObject update(PyObject args) {
        // XXX: stop-gap implementation
        if (args instanceof PyDict)
            merge(args, MergeMode.PUT);
        else
            throw new AttributeError("Unsupported mapping", args);
        return Py.None;
    }
}
