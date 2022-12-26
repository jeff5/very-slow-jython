package uk.co.farowl.vsj3.evo1;

/** Common mechanisms for all Python modules defined in Java. */
public abstract class JavaModule extends PyModule {

    final ModuleDef definition;

    /**
     * Construct the base {@code JavaModule} and fill the module
     * dictionary from the given module definition, which is normally
     * created during static initialisation of the concrete class
     * defining the module.
     *
     * @param definition of the module
     */
    protected JavaModule(ModuleDef definition) {
        super(definition.name);
        this.definition = definition;
        definition.addMembers(this);
    }
}
