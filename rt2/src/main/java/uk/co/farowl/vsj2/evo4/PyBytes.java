package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandles;

/** The Python {@code bytes} object. */
class PyBytes extends AbstractPyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bytes", PyBytes.class,
                    MethodHandles.lookup()));
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    static final PyBytes EMPTY = new PyBytes(EMPTY_BYTE_ARRAY);
    final byte[] value;

    /**
     * As {@link #PyBytes(byte[])} for Python sub-class specifying
     * {@link #type}.
     */
    protected PyBytes(PyType type, byte[] value) {
        super(type);
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
        super(type);
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
}
