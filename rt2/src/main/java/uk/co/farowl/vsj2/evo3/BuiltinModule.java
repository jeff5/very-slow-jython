package uk.co.farowl.vsj2.evo3;

class BuiltinModule extends PyModule {

    BuiltinModule(Interpreter interpreter) {
        super(interpreter, "builtins");
    }

    @Override
    void init() {}

}
