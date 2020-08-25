package uk.co.farowl.vsj2.evo4;

/** The Python {@code bytes} object. */
class PyBytes implements PyObject {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bytes", PyBytes.class));
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    static final PyBytes EMPTY = new PyBytes(EMPTY_BYTE_ARRAY);

    protected final PyType type;
    final byte[] value;

    /**
     * As {@link #PyBytes(byte[])} for Python sub-class specifying
     * {@link #type}.
     */
    protected PyBytes(PyType type, byte[] value) {
        this.type = type;
        if (value.length == 0)
            this.value = EMPTY_BYTE_ARRAY;
        else {
            this.value = new byte[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    /**
     * As {@link #PyBytes(int...)} for Python sub-class specifying
     * {@link #type}.
     */
    protected PyBytes(PyType type, int... value) {
        this.type = type;
        int n = value.length;
        if (n == 0)
            this.value = EMPTY_BYTE_ARRAY;
        else {
            byte[] b = new byte[n];
            for (int i = 0; i < n; i++) {
                b[i] = (byte) (value[i] & 0xff);
            }
            this.value = b;
        }
    }

    /**
     * Construct a Python {@code bytes} object from bytes treated as
     * unsigned.
     */
    PyBytes(byte[] value) { this(TYPE, value); }

    /**
     * Construct a Python {@code bytes} object from Java {@code int}s
     * treated as unsigned.
     */
    PyBytes(int... value) { this(TYPE, value); }

    @Override
    public PyType getType() { return type; }
}
