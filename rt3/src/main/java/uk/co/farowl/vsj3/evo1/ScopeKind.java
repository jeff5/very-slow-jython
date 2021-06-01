package uk.co.farowl.vsj3.evo1;

/** Exposers are of type or module kind. */
enum ScopeKind {

    MODULE("$module"), //
    TYPE("$self");

    ScopeKind(String selfName) {
        this.selfName = selfName;
    }

    /** Name of a "self" parameter in instance methods. */
    String selfName;
}
