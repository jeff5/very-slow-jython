package uk.co.farowl.vsj2.evo3;

/** The Python {@code module} object. */
abstract class PyModule implements PyObject {

    static final PyType TYPE = new PyType("module", PyModule.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Name of this module. **/
    final String name;

    /** Dictionary (globals) of this module. **/
    final PyDictionary dict = new PyDictionary();

    /** The interpreter that created this module. **/
    final Interpreter interpreter;

    PyModule(Interpreter interpreter, String name) {
        this.interpreter = interpreter;
        this.name = name;
    }

    /**
     * Initialise the module instance. This is the Java equivalent of
     * the module body. The main action will be to add entries to
     * {@link #dict}. These become the members (globals) of the module.
     */
    abstract void init();
}
