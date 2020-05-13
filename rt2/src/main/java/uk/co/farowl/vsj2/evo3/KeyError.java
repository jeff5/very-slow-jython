package uk.co.farowl.vsj2.evo3;

/** The Python {@code KeyError} exception. */
class KeyError extends LookupError {

    static final PyType TYPE = new PyType("KeyError", KeyError.class);

    final PyObject key;

    protected KeyError(PyObject key, PyType type, String msg,
            Object... args) {
        super(type, msg, args);
        this.key = key;
    }

    public KeyError(PyObject key, String msg, Object... args) {
        this(key, TYPE, msg, key.toString(), args);
    }

    static class Duplicate extends KeyError {
        public Duplicate(PyObject key) {
            super(key, "duplicate key %s", key.toString());
        }
    }
}
