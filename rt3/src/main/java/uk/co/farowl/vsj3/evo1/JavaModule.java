package uk.co.farowl.vsj3.evo1;

/** Common mechanisms for all Python modules defined in Java. */
abstract class JavaModule extends PyModule {

    @Deprecated
    JavaModule(String name) { super(name); }

    JavaModule(ModuleDef def) {
        super(def.name);
        def.addMembers(this);
    }
}
