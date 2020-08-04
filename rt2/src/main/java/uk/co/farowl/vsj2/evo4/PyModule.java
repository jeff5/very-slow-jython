package uk.co.farowl.vsj2.evo4;

/** The Python {@code module} object. */
class PyModule implements PyObject {

    static final PyType TYPE = new PyType("module", PyModule.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Name of this module. **/
    final String name;

    /** Dictionary (globals) of this module. **/
    final PyDict dict = new PyDict();

    /** Construct an instance of the named module. */
    PyModule(String name) { this.name = name; }

    /**
     * Add a type by name to the dictionary.
     */
    void add(PyType t) {
        // XXX should type names be PyUnicode?
        dict.put(t.getName(), t);
    }

    /**
     * Add an object by name to the module dictionary.
     * @param name to use as key
     * @param o value for key
     */
    void add(PyUnicode name, PyObject o) {
        dict.put(name, o);
    }

    /**
     * Initialise the module instance. This is the Java equivalent of
     * the module body. The main action will be to add entries to
     * {@link #dict}. These become the members (globals) of the module.
     */
    void init() {}

    @Override
    public String toString() {
        return String.format("<module '%s'>", name);
    }
}
