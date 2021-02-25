package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code KeyError} exception. */
class KeyError extends LookupError {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("KeyError", MethodHandles.lookup())
                    .base(LookupError.TYPE));

    final Object key;

    protected KeyError(Object key, PyType type, String msg,
            Object... args) {
        super(type, msg, args);
        this.key = key;
    }

    public KeyError(Object key, String msg, Object... args) {
        this(key, TYPE, msg, key.toString(), args);
    }

    static class Duplicate extends KeyError {

        public Duplicate(Object key) {
            super(key, "duplicate key %s", key.toString());
        }
    }
}
