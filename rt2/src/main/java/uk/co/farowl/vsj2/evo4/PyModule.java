package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;

/** The Python {@code module} object. */
class PyModule implements PyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("module", PyModule.class,
                    MethodHandles.lookup()));

    protected final PyType type;

    /** Name of this module. **/
    final String name;

    /** Dictionary (globals) of this module. **/
    final PyDict dict = new PyDict();

    /**
     * As {@link #PyModule(String)} for Python sub-class specifying
     * {@link #type}.
     *
     * @param type actual Python sub-class to being created
     * @param name of module
     */

    PyModule(PyType type, String name) {
        this.type = type;
        this.name = name;
    }

    /**
     * Construct an instance of the named module.
     *
     * @param name of module
     */
    PyModule(String name) { this(TYPE, name); }

    @Override
    public PyType getType() { return type; }

    /**
     * Add a type by name to the dictionary.
     *
     * @param t the type
     */
    void add(PyType t) {
        // XXX should type names be PyUnicode?
        dict.put(t.getName(), t);
    }

    /**
     * Add an object by name to the module dictionary.
     *
     * @param name to use as key
     * @param o value for key
     */
    void add(PyUnicode name, PyObject o) { dict.put(name, o); }

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
