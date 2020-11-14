package uk.co.farowl.vsj2.evo4;

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
    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("dict", PyDict.class));

    @Override
    public PyType getType() { return TYPE; }

    @Override
    public String toString() { return Py.defaultToString(this); }

    /**
     * Specialisation of {@code Map.get} allowing Java {@code String}
     * keys. Returns {@code null} if the key is not found.
     */
    PyObject get(String key) { return this.get(Py.str(key)); }

    /**
     * Specialisation of {@code Map.put()} allowing Java {@code String}
     * keys.
     */
    PyObject put(String key, PyObject value) {
        return this.put(Py.str(key), value);
    }

    /**
     * Specialisation of {@code Map.putIfAbsent()} allowing Java
     * {@code String} keys.
     */
    PyObject putIfAbsent(String key, PyObject value) {
        return this.putIfAbsent(Py.str(key), value);
    }

    enum MergeMode { PUT, IF_ABSENT, UNIQUE }

    /** Merge the mapping {@code src} into this {@code dict}. */
    PyObject merge(PyObject src, MergeMode mode) {
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
        return Py.None;
    }

    // slot functions -------------------------------------------------

    static PyObject __repr__(PyDict self) throws Throwable {
        return Py.str(PyObjectUtil.mapRepr(self));
    }

    static PyObject __getitem__(PyDict self, PyObject key)
            throws Throwable {
        // This may be over-simplifying things but ... :)
        return self.get(key);
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
