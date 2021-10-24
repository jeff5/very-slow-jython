package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.AbstractList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

/** The Python {@code bytes} object. */
class PyBytes extends AbstractList<Integer>
        implements PySequence.OfInt, CraftedPyObject {

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

    // Special methods ------------------------------------------------

    @SuppressWarnings("unused")
    private Object __add__(Object other) {
        try {
            return concatBytes(this, adapt(other));
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    @SuppressWarnings("unused")
    private Object __radd__(Object other) {
        try {
            return concatBytes(adapt(other), this);
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

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
        if (PyNumber.indexCheck(item)) {
            return PyObjectUtil.getItem(this, item);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    // AbstractList methods -------------------------------------------

    @Override
    public Integer get(int i) { return 0xff & value[i]; }

    @Override
    public int size() { return value.length; }

    // PySequence.OfInt interface ----------------------------

    @Override
    public PyType getType() { return type; }

    @Override
    public int length() { return value.length; };

    @Override
    public Spliterator.OfInt spliterator() {
        return new BytesSpliterator();
    }

    @Override
    public IntStream asIntStream() {
        return StreamSupport.intStream(spliterator(), false);
    }

    @Override
    public Integer getItem(int i) {
        try {
            return 0xff & value[i];
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("bytes");
        }
    }

    @Override
    public PyBytes concat(PySequence.Of<Integer> other) {
        int n = value.length, m = other.length();
        byte[] b = new byte[n + m];
        // Copy the data from this array
        System.arraycopy(value, 0, b, 0, n);
        // Append the data from the other stream
        IntStream s = other instanceof PySequence.OfInt
                ? ((PySequence.OfInt)other).asIntStream()
                : other.asStream().mapToInt(Integer::valueOf);
        s.forEach(new ByteStore(b, n));
        return new PyBytes(TYPE, true, b);
    }

    @Override
    public Object repeat(int n) throws OutOfMemoryError {
        if (n == 0)
            return EMPTY;
        else if (n == 1)
            return this;
        else {
            int m = value.length;
            byte[] b = new byte[n * m];
            for (int i = 0, p = 0; i < n; i++, p += m) {
                System.arraycopy(value, 0, b, p, m);
            }
            return new PyBytes(TYPE, true, b);
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
    public int compareTo(PySequence.Of<Integer> other) {
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

    // Plumbing -------------------------------------------------------

    private static PyBytes concatBytes(PySequence.OfInt v,
            PySequence.OfInt w) {
        try {
            int n = v.length(), m = w.length();
            byte[] b = new byte[n + m];
            IntStream.concat(v.asIntStream(), w.asIntStream())
                    .forEach(new ByteStore(b, 0));
            return new PyBytes(TYPE, true, b);
        } catch (OutOfMemoryError e) {
            throw concatenatedOverflow();
        }
    }

    /**
     * Inner class defining the return type of
     * {@link PyBytes#spliterator()}. We need this only because
     * {@link #tryAdvance(IntConsumer) tryAdvance} deals in java
     * {@code int}s, while our array is {@code byte[]}. There is no
     * ready-made {@code Spliterator.OfByte}, and if there were, it
     * would return signed values.
     */
    private class BytesSpliterator
            extends Spliterators.AbstractIntSpliterator {

        static final int flags = Spliterator.IMMUTABLE
                | Spliterator.SIZED | Spliterator.ORDERED;
        private int i = 0;

        BytesSpliterator() { super(value.length, flags); }

        @Override
        public boolean tryAdvance(IntConsumer action) {
            if (i < value.length) {
                action.accept(0xff & value[i++]);
                return true;
            } else
                return false;
        }
    }

    /**
     * A consumer of primitive int values that stores them in an array
     * given it at construction.
     */
    private static class ByteStore implements IntConsumer {

        private final byte[] b;
        private int i = 0;

        ByteStore(byte[] bytes, int start) {
            this.b = bytes;
            this.i = start;
        }

        @Override
        public void accept(int value) { b[i++] = (byte)value; }
    }

    /**
     * Adapt a Python object to a sequence of Java {@code int} values or
     * throw an exception. If the method throws the special exception
     * {@link NoConversion}, the caller must catch it and deal with it,
     * perhaps by throwing a {@link TypeError}. A binary operation will
     * normally return {@link Py#NotImplemented} in that case.
     * <p>
     * Note that implementing {@link PySequence.OfInt} is not
     * enough, which other types may, but be incompatible in Python.
     *
     * @param v to wrap or return
     * @return adapted to a sequence
     * @throws NoConversion if {@code v} is not a Python {@code str}
     */
    static PySequence.OfInt adapt(Object v)
            throws NoConversion {
        // Check against supported types, most likely first
        if (v instanceof PyBytes /* || v instanceof PyByteArray */)
            return (PySequence.OfInt)v;
        throw PyObjectUtil.NO_CONVERSION;
    }

    private static OverflowError concatenatedOverflow() {
        return PyObjectUtil.concatenatedOverflow(EMPTY);
    }
}
