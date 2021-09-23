package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;

/** The Python {@code bytes} object. */
class PyBytes extends AbstractList<Integer>
        implements PySequenceInterface<Integer>, CraftedPyObject {

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
     * {@link #type}. Construct an instance of {@code PyBytes} or a
     * sub-class, from a given array of bytes, with the option to re-use
     * that array as the implementation. If the actual array is is
     * re-used the caller must give up ownership and never modify it
     * after the call. See {@link #concat(PySequenceInterface)} for a
     * correct use.
     *
     * @param type sub-type for which this is being created
     * @param iPromiseNotToModify if {@code true}, the array becomes the
     *     implementation array, otherwise the constructor takes a copy.
     * @param value the array of the bytes to contain
     */
    private PyBytes(PyType type, boolean iPromiseNotToModify,
            byte[] value) {
        this.type = type;
        if (value.length == 0)
            this.value = EMPTY_BYTE_ARRAY;
        else if (iPromiseNotToModify)
            this.value = value;
        else
            this.value = Arrays.copyOf(value, value.length);

    }

    /**
     * As {@link #PyBytes(byte[])} for Python sub-class specifying
     * {@link #type}.
     *
     * @param type sub-type for which this is being created
     * @param value of the bytes
     */
    protected PyBytes(PyType type, byte[] value) {
        this(type, false, value);
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
    PyBytes(byte[] value) { this(TYPE, false, value); }

    /**
     * Construct a Python {@code bytes} object from Java {@code int}s
     * treated as unsigned.
     *
     * @param value of the bytes
     */
    PyBytes(int... value) { this(TYPE, value); }

    // Special methods -----------------------------------------------

    @SuppressWarnings("unused")
    private Object __mul__(Object n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private Object __rmul__(Object n) throws Throwable {
        return __mul__(n);
    }

    @SuppressWarnings("unused")
    private int __len__() { return value.length; }

    @SuppressWarnings("unused")
    private Object __getitem__(Object item) throws Throwable {
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

    @Override
    public int length() { return value.length; };

    @Override
    public Integer getItem(int i) {
        try {
            return 0xff & value[i];
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("bytes");
        }
    }

    @Override
    public PyBytes concat(PySequenceInterface<Integer> other) {
        int n = length(), m = other.length();
        byte[] b = new byte[n + m];
        System.arraycopy(value, 0, b, 0, n);
        for (int x : other) { b[n++] = (byte)(0xff & x); }
        return new PyBytes(TYPE, true, b);
    }

    @Override
    public Object repeat(int n) {
        if (n == 0)
            return EMPTY;
        else if (n == 1)
            return this;
        else {
            try {
                int m = value.length;
                byte[] b = new byte[n * m];
                for (int i = 0, p = 0; i < n; i++, p += m) {
                    System.arraycopy(value, 0, b, p, m);
                }
                return new PyBytes(TYPE, true, b);
            } catch (OutOfMemoryError e) {
                throw new OverflowError("repeated bytes are too long");
            }
        }
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {

            private int i = 0;

            @Override
            public boolean hasNext() { return i < value.length; }

            @Override
            public Integer next() { return 0xff & value[i++]; }
        };
    }

    @Override
    public int compareTo(PySequenceInterface<Integer> other) {
        Iterator<Integer> ib = other.iterator();
        for (int a : value) {
            if (ib.hasNext()) {
                int b = ib.next();
                // if a != b, then we've found an answer
                if (a > b)
                    return 1;
                else if (a < b)
                    return -1;
            } else
                // value has not run out, but other has. We win.
                return 1;
        }
        /*
         * The sequences matched over the length of value. The other is
         * the winner if it still has elements. Otherwise it's a tie.
         */
        return ib.hasNext() ? -1 : 0;
    }

    // Plumbing ------------------------------------------------------

    @Override
    public PyType getType() { return type; }
}
