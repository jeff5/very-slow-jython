package uk.co.farowl.vsj2.evo3;

/** The Python {@code bytes} object. */
class PyBytes implements PyObject {

    static final PyType TYPE = new PyType("bytes", PyBytes.class);
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    static final PyBytes EMPTY = new PyBytes(EMPTY_BYTE_ARRAY);

    @Override
    public PyType getType() { return TYPE; }
    final byte[] value;

    PyBytes(byte[] value) {
        if (value.length == 0)
            this.value = EMPTY_BYTE_ARRAY;
        else {
            this.value = new byte[value.length];
            System.arraycopy(value, 0, this.value, 0, value.length);
        }
    }

    PyBytes(int... value) {
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
}
