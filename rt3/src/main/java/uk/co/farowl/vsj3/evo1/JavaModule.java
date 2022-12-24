package uk.co.farowl.vsj3.evo1;

/** Common mechanisms for all Python modules defined in Java. */
public abstract class JavaModule extends PyModule {

    /**
     * Construct the base {@code JavaModule} and fill the module
     * dictionary from the given module definition, which is normally
     * created during static initialisation of the concrete class
     * defining the module.
     *
     * @param def the module definition
     */
    protected JavaModule(ModuleDef def) {
        super(def.name);
        def.addMembers(this);
    }
}
