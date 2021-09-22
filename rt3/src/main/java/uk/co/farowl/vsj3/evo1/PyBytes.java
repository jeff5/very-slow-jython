package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.AbstractList;

/** The Python {@code bytes} object. */
class PyBytes extends AbstractList<Integer> implements CraftedPyObject {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bytes", MethodHandles.lookup()));
    private static final byte[] EMPTY_BYTE_ARRAY = new byte[] {};
    static final PyBytes EMPTY = new PyBytes(EMPTY_BYTE_ARRAY);

    /** The Python type of this instance. */
    protected final PyType type;

    /** The elements of the {@code bytes}. */
    final byte[] value;

    /**
     * As {@link #PyBytes(byte[])} for Python sub-class specifying
     * {@link #type}.
     *
     * @param type sub-type for which this is being created
     * @param value of the bytes
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
     *
     * @param type sub-type for which this is being created
     * @param value of the bytes
     */
    protected PyBytes(PyType type, int... value) {
        this.type = type;
        int n = value.length;
        if (n == 0)
            this.value = EMPTY_BYTE_ARRAY;
        else {
            byte[] b = new byte[n];
            for (int i = 0; i < n; i++) {
                b[i] = (byte)(value[i] & 0xff);
            }
            this.value = b;
        }
    }

    /**
     * Construct a Python {@code bytes} object from bytes treated as
     * unsigned.
     *
     * @param value of the bytes
     */
    PyBytes(byte[] value) { this(TYPE, value); }

    /**
     * Construct a Python {@code bytes} object from Java {@code int}s
     * treated as unsigned.
     *
     * @param value of the bytes
     */
    PyBytes(int... value) { this(TYPE, value); }

    // Special methods -----------------------------------------------

    /* Slot.op_len */
    int __len__() { return value.length; }

    /* Slot.op_getitem */
    Object __getitem__(Object item) throws Throwable {
        Operations itemOps = Operations.of(item);
        if (Slot.op_index.isDefinedFor(itemOps)) {
            int i = PyNumber.asSize(item, IndexError::new);
            if (i < 0) { i += value.length; }
            return getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    // AbstractList methods ------------------------------------------

    @Override
    public Integer get(int i) { return 0xff & value[i]; }

    @Override
    public int size() { return value.length; }

    // Sequence interface --------------------------------------------

    Object getItem(int i) {
        try {
            return 0xff & value[i];
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("bytes");
        }
    }

    // Plumbing ------------------------------------------------------

    @Override
    public PyType getType() { return type; }
}
