package uk.co.farowl.vsj2.evo2;

/** Runtime */
class Py {

    private static class Singleton implements PyObject {

        final PyType type;

        @Override
        public PyType getType() { return type; }
        String name;

        Singleton(String name) {
            this.name = name;
            type = new PyType(name, getClass());
        }

        @Override
        public String toString() { return name; }
    }

    /** Python {@code None} object. */
    static final PyObject None = new Singleton("None") {};
    /** Python {@code NotImplemented} object. */
    static final PyObject NotImplemented =
            new Singleton("NotImplemented") {};
}
