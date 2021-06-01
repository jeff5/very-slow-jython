package uk.co.farowl.vsj3.evo1;

/**
 * Enum describing whether a method is an instance, static or class
 * method (in Python).
 */
enum MethodKind {
    /** An initial self or module argument is not expected. */
    // In CPython STATIC cannot be used for functions in modules.
    STATIC,
    /** The first argument is self or a module. */
    INSTANCE,
    /** The first argument is the Python type of the target. */
    // In CPython CLASS cannot be used for functions in modules.
    CLASS
}
