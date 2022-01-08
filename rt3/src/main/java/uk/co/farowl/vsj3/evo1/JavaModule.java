package uk.co.farowl.vsj3.evo1;

/** Common mechanisms for all Python modules defined in Java. */
public abstract class JavaModule extends PyModule {

    @Deprecated
    JavaModule(String name) { super(name); }

    protected JavaModule(ModuleDef def) {
        super(def.name);
        def.addMembers(this);
    }
}
