package uk.co.farowl.vsj3.evo1.modules;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import uk.co.farowl.vsj3.evo1.Abstract;
import uk.co.farowl.vsj3.evo1.EOFError;
import uk.co.farowl.vsj3.evo1.Exposed;
import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.OSError;
import uk.co.farowl.vsj3.evo1.Py;
import uk.co.farowl.vsj3.evo1.PyBool;
import uk.co.farowl.vsj3.evo1.PyBytes;
import uk.co.farowl.vsj3.evo1.PyException;
import uk.co.farowl.vsj3.evo1.PyLong;
import uk.co.farowl.vsj3.evo1.PyObjectUtil;
import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.PyType;
import uk.co.farowl.vsj3.evo1.ValueError;
import uk.co.farowl.vsj3.evo1.stringlib.ByteArrayBuilder;

/**
 * Write Python objects to files and read them back. This is primarily
 * intended for writing and reading compiled Python code, even though
 * {@code dict}s, {@code list}s, {@code set}s a nd {@code frozenset}s,
 * not commonly seen in {@code code} objects, are supported. Version 3
 * of this protocol properly supports circular links and sharing.
 */

public class marshal /* extends JavaModule */ {

    @Exposed.Member("version")
    final static int VERSION = 4;

    /*
     * High water mark to determine when the marshalled object is
     * dangerously deep and risks coring the interpreter. When the
     * object stack gets this deep, raise an exception instead of
     * continuing.
     */
    private final static int MAX_MARSHAL_STACK_DEPTH = 2000;

    /*
     * Enumerate all the legal record types. Each corresponds to a type
     * of data, or a specific value, except {@code NULL}.
     */
    private final static int TYPE_NULL = '0';
    /** The record encodes None (in one byte) */
    private final static int TYPE_NONE = 'N';
    /** The record encodes False (in one byte) */
    private final static int TYPE_FALSE = 'F';
    /** The record encodes True (in one byte) */
    private final static int TYPE_TRUE = 'T';
    /** The record encodes StopIteration (in one byte) */
    private final static int TYPE_STOPITER = 'S';
    /** The record encodes Ellipsis (in one byte) */
    private final static int TYPE_ELLIPSIS = '.';
    /** The record encodes an int (4 bytes follow) */
    private final static int TYPE_INT = 'i';
    /*
     * TYPE_INT64 is not generated anymore. Supported for backward
     * compatibility only.
     */
    private final static int TYPE_INT64 = 'I';
    private final static int TYPE_FLOAT = 'f';
    private final static int TYPE_BINARY_FLOAT = 'g';
    private final static int TYPE_COMPLEX = 'x';
    private final static int TYPE_BINARY_COMPLEX = 'y';
    private final static int TYPE_LONG = 'l';
    private final static int TYPE_STRING = 's';
    private final static int TYPE_INTERNED = 't';
    private final static int TYPE_REF = 'r';
    private final static int TYPE_TUPLE = '(';
    private final static int TYPE_LIST = '[';
    private final static int TYPE_DICT = '{';
    private final static int TYPE_CODE = 'c';
    private final static int TYPE_UNICODE = 'u';
    private final static int TYPE_UNKNOWN = '?';
    private final static int TYPE_SET = '<';
    private final static int TYPE_FROZENSET = '>';

    private final static int TYPE_ASCII = 'a';
    private final static int TYPE_ASCII_INTERNED = 'A';
    private final static int TYPE_SMALL_TUPLE = ')';
    private final static int TYPE_SHORT_ASCII = 'z';
    private final static int TYPE_SHORT_ASCII_INTERNED = 'Z';

    /**
     * We add this to a {@code TYPE_*} code to indicate that the encoded
     * object is cached at the next free index. When reading, each
     * occurrence appends its object to a list (thus the encounter order
     * defines the index). When writing, we look in a cache to see if
     * the object has already been encoded (therefore given an index)
     * and if it has, we record the index instead in a {@link #TYPE_REF}
     * record. IF it is new, we assign it the next index.
     */
    private final static int FLAG_REF = 0x80;

    // From the CPython version: expect to need!
    private final static int WFERR_OK = 0;
    private final static int WFERR_UNMARSHALLABLE = 1;
    private final static int WFERR_NESTEDTOODEEP = 2;
    private final static int WFERR_NOMEMORY = 3;

    /** A mask for the low 15 bits. */
    private final static int MASK15 = 0x7fff;
    /** A mask for the low 15 bits. */
    private final static BigInteger BIG_MASK15 =
            BigInteger.valueOf(MASK15);

    /**
     * We apply a particular {@code Decoder} to the stream after we read
     * a type code byte that tells us which one to use, to decode the
     * data following. If that code has no data following, then the
     * corresponding {@link Decoder#read(Reader)} returns a constant.
     */
    @FunctionalInterface
    private interface Decoder {
        /**
         * Read an object value, of a particular Python type, from the
         * input managed by a given {@link Reader}, the matching type
         * code having been read from it already.
         *
         * @param r from which to read
         * @return the object value read
         */
        Object read(Reader r);
    }

    /**
     * A {@code Codec} groups together the code for writing and reading
     * instances of a particular Python type. The {@code write()} method
     * encodes a value of that type onto the stream, choosing from
     * available representations when there is more than one. The
     * {@code Codec} provides (potentially) multiple {@link Decoder}s,
     * one for each representation (type code), in a {@link Map}
     * supplied by the decoders() method.
     */
    interface Codec {
        /**
         * The Python type this codec is implemented to encode and
         * decode.
         *
         * @return target Python type
         */
        PyType type();

        /**
         * Write a value, of a particular Python type, onto the output
         * managed by the {@link Writer}.
         *
         * @param w to receive the data
         * @param v to be written
         * @throws IOException on file write errors
         * @throws ArrayIndexOutOfBoundsException on byte array write
         *     errors
         * @throws Throwable
         */
        void write(Writer w, Object v) throws IOException, Throwable;

        /**
         * Return a mapping from each type code supported to a function
         * that is able to read the object following that type code, in
         * the input managed by a given {@link Reader}.
         *
         * @param code for which this is a read operation
         * @return the value read
         */
        Iterable<Map.Entry<Integer, Decoder>> decoders();

    }

    /**
     * A mapping from Python type to the Codec that is able to encode
     * and decode that type.
     */
    private static HashMap<PyType, Codec> codecForType =
            new HashMap<>();

    /**
     * A mapping from the type code to the {@link Decoder} able to
     * render the record as a Python object.
     */
    private static HashMap<Integer, Decoder> decoderForCode =
            new HashMap<>();

    /**
     * Associate a codec with its target Python type in
     * {@link #codecForType} and eachread method it supplies with the
     * type code it supports.
     *
     * @param codec to register
     */
    private static void register(Codec codec) {
        codecForType.put(codec.type(), codec);
        for (Map.Entry<Integer, Decoder> e : codec.decoders()) {
            Decoder d = decoderForCode.put(e.getKey(), e.getValue());
            assert d == null; // No codec should duplicate a code
        }
    }

    // Register all the defined codecs
    static {
        register(new BoolCodec());
        register(new IntCodec());
    }

    /**
     * {@code marshal.dump(value, file, version=4)}: Write the value on
     * the open file. The value must be a supported type. The file must
     * be a writable binary file.
     *
     * @param value to write
     * @param file on which to write
     * @param version of the format to use
     * @throws ValueError if the value has (or contains an object that
     *     has) an unsupported type
     * @throws OSError from file operations
     */
    @Exposed.PythonStaticMethod
    static void dump(Object value, Object file,
            @Default("4") int version) throws ValueError, OSError {
        try (OutputStream os = StreamWriter.adapt(file)) {
            Writer writer = new StreamWriter(os, version);
            writer.dump(value);
        } catch (NoConversion | IOException e) {
            throw Abstract.argumentTypeError("dump", "file",
                    "a file-like object with write", file);
        }
    }

    /**
     * {@code marshal.load(file)}: read one value from an open file and
     * return it. If no valid value is read (e.g. because the data has
     * an incompatible marshal format), raise {@code EOFError},
     * {@code ValueError} or {@code TypeError}. The file must be a
     * readable binary file.
     *
     * @param file to read
     * @return the object read
     * @throws ValueError when an object being read is over-size or
     *     contains values out of range.
     * @throws TypeError when file reading returns non-byte data or a
     *     container contains a null element.
     * @throws EOFError when a partial object is read
     * @throws OSError from file operations generally
     */
    @Exposed.PythonStaticMethod
    static Object load(Object file) {
        try (InputStream is = StreamReader.adapt(file)) {
            Reader reader = new StreamReader(is);
            return reader.load();
        } catch (NoConversion | IOException e) {
            throw Abstract.argumentTypeError("load", "file",
                    "a file-like object with read", file);
        }
    }

    /**
     * {@code marshal.dumps(value, version=4)}: Return a {@code bytes}
     * object into which the given value has been written, as to a file
     * using {@link #dump(Object, Object, int)}. The value must be a
     * supported type.
     *
     * @param value to write
     * @param version of the format to use
     * @throws ValueError if the value has (or contains an object that
     *     has) an unsupported type
     */
    @Exposed.PythonStaticMethod
    static PyBytes dumps(Object value, @Default("4") int version)
            throws ValueError {
        ByteArrayBuilder bb = new ByteArrayBuilder();
        Writer writer = new BytesWriter(bb, version);
        writer.dump(value);
        return new PyBytes(bb);
    }

    /**
     * {@code marshal.loads(bytes)}: read one value from a bytes-like
     * object and return it. If no valid value is read, raise
     * {@code EOFError}, {@code ValueError} or {@code TypeError}.
     *
     * @param bytes to read
     * @return the object read
     * @throws ValueError when an object being read is over-size or
     *     contains values out of range.
     * @throws TypeError when a container contains a null element.
     * @throws EOFError when a partial object is read
     */
    @Exposed.PythonStaticMethod
    static Object loads(Object bytes) {
        try {
            ByteBuffer bb = BytesReader.adapt(bytes);
            Reader reader = new BytesReader(bb);
            return reader.load();
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError("loads", "bytes",
                    "a bytes-like object", bytes);
        }
    }

    /**
     * A {@code marshal.Writer} holds an {@code OutputStream} during the
     * time that the {@code marshal} module is serialising an object to
     * it. It provides operations to write individual field values to
     * the stream, that support classes extending {@link Codec} in their
     * implementation of {@link Codec#write(Writer, Object) write()}.
     * <p>
     * The wrapped {@code OutputStream} may be writing to a file or to
     * an array.
     *
     *
     */
    abstract static class Writer {

        /**
         * Version of the protocol this {@code Writer} is supposed to
         * write.
         */
        private final int version;

        /**
         * Create with specified version of the protocol.
         *
         * @param version to write
         */
        public Writer(int version) { this.version = version; }

        /**
         * Encode a complete object.
         *
         * @param obj to encode
         */
        public void dump(Object obj) {}

        /**
         * Write one {@code byte} onto the destination. The parameter is
         * an {@code int} because it may be the result of a calculation,
         * but only the the low 8 bits are used.
         *
         * @param v to write
         */
        abstract void writeByte(int v);

        /**
         * Write one {@code short} onto the destination. The parameter
         * is an {@code int} because it may be the result of a
         * calculation, but only the the low 16 bits are used.
         *
         * @param v to write
         */
        abstract void writeShort(int v);

        /**
         * Write one {@code int} onto the destination.
         *
         * @param v to write
         */
        abstract void writeInt(int v);

        /**
         * Write one {@code long} onto the destination.
         *
         * @param v to write
         */
        abstract void writeLong(long v);

        /**
         * Write a {@code BigInteger} as a counted sequence of 15-bit
         * units (the form Python expects).
         *
         * @param v
         */
        void writeBigInteger(BigInteger v) {
            boolean negative = v.signum() < 0;
            if (negative) { v = v.negate(); }
            int size = (v.bitLength() + 14) / 15;
            writeInt(negative ? -size : size);
            for (int i = 0; i < size; i++) {
                writeShort(v.and(BIG_MASK15).intValue());
                v = v.shiftRight(15);
            }
        }
    }

    /**
     * A {@link Writer} that has a {@code java.io.OutputStream} as its
     * destination. When the underlying destination is a file, it is
     * preferable for efficiency that this be a
     * {@code java.io.BufferedOutputStream}. A
     * {@code java.io.ByteArrayOutputStream} needs no additional
     * buffering.
     */
    static class StreamWriter extends Writer {

        /**
         * The destination wrapped in a {@code DataOutputStream} on
         * which we shall call {@code getInt()} etc. to write items. A
         * Python marshal stream is little-endian, while Java will write
         * big-endian data. However, note that
         * {@code Integer.reverseBytes()} and friends are HotSpot
         * intrinsics.
         */
        private final DataOutputStream file;

        /**
         * Form a {@link Reader} on a {@code java.io.InputStream}.
         *
         * @param file input
         */
        StreamWriter(OutputStream file, int version) {
            super(version);
            this.file = new DataOutputStream(file);
        }

        @Override
        void writeByte(int b) {
            try {
                file.write(b);
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        void writeShort(int v) {
            try {
                file.writeShort(Short.reverseBytes((short)v));
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        void writeInt(int v) {
            try {
                file.writeInt(Integer.reverseBytes(v));
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        void writeLong(long v) {
            try {
                file.writeLong(Long.reverseBytes(v));
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        /**
         * Recognise or wrap an eligible file-like data sink as an
         * {@code OutputStream}.
         */
        private static OutputStream adapt(Object file)
                throws NoConversion {
            if (file instanceof OutputStream) {
                return (OutputStream)file;
            } else {
                // Adapt any object with write accepting a byte
                // But for now ...
                throw PyObjectUtil.NO_CONVERSION;
            }
        }
    }

    /**
     * A {@link Writer} that has a {@link ByteArrayBuilder} as its
     * destination.
     */
    static class BytesWriter extends Writer {

        /**
         * The destination {@link ByteArrayBuilder} on which we write
         * little-endian
         */
        final ByteArrayBuilder builder;

        /**
         * Form a {@link Reader} on a byte array.
         *
         * @param builder destination
         */
        BytesWriter(ByteArrayBuilder builder, int version) {
            super(version);
            this.builder = builder;
        }

        @Override
        void writeByte(int v) { builder.append(v); }

        @Override
        void writeShort(int v) { builder.appendShortLE(v); }

        @Override
        void writeInt(int v) { builder.appendIntLE(v); }

        @Override
        void writeLong(long v) { builder.appendLongLE(v); }
    }

    abstract static class Reader {

        /**
         * Decode a complete object from the source.
         *
         * @return the object read
         */
        Object load() {
            // XXX dummy
            return new Object();
        }

        /**
         * Read one {@code byte} from the source (as an unsigned
         * integer). advancing the stream one byte.
         *
         * @return byte read unsigned
         */
        // Compare CPython r_byte in marshal.c
        abstract int readByte();

        /**
         * Read one {@code short} value from the source, advancing the
         * stream 2 bytes.
         *
         * @return value read
         */
        // Compare CPython r_int in marshal.c
        abstract int readShort();

        /**
         * Read one {@code int} value from the source, advancing the
         * stream 4 bytes.
         *
         * @return value read
         */
        // Compare CPython r_long in marshal.c
        abstract int readInt();

        /**
         * Read one {@code long} value from the source, advancing the
         * stream 8 bytes.
         *
         * @return value read
         */
        // Compare CPython r_long64 in marshal.c
        abstract long readLong();

        /**
         * Read one {@code BigInteger} value from the source, advancing
         * the stream a variable number of bytes.
         *
         * @return value read
         */
        // Compare CPython r_PyLong in marshal.c
        BigInteger readBigInteger() throws ValueError {
            // Encoded as size and 15-bit digits
            int size = readInt();
            if (size == Integer.MIN_VALUE) {
                throw badData("size out of range in big int");
            }

            // Size carries the sign
            boolean negative = size < 0;
            size = Math.abs(size);

            // Or each digit as we read it into v
            BigInteger v = BigInteger.ZERO;
            for (int i = 0, shift = 0; i < size; i++, shift += 15) {
                int digit = readShort();
                if ((digit & ~MASK15) != 0) {
                    // Bits set where they shouldn't be
                    throw badData("digit out of range in big int");
                }
                BigInteger d = BigInteger.valueOf(digit);
                v = (i == 0) ? d : v.or(d.shiftLeft(shift));
            }

            // Sign from size
            if (negative) { v = v.negate(); }
            return v;
        }

        /**
         * Prepare a Python {@link PyException} for throwing, based on
         * the Java {@code IOException}. We may return a Python
         * {@link EOFError} or {@link OSError}.
         *
         * @param ioe to convert
         * @return the chosen Python exception
         */
        protected PyException pyException(IOException ioe) {
            if (ioe instanceof EOFException) {
                return endOfData();
            } else {
                return new OSError(ioe);
            }
        }

        /**
         * Prepare a Python {@link EOFError} for throwing. with the
         * message that the data is too short. We throw one of these on
         * encountering and end of file or buffer where more of the
         * object was expected.
         *
         * @param ioe to convert
         * @return the chosen Python exception
         */
        protected static EOFError endOfData() {
            return new EOFError("marshal data too short");
        }

        /**
         * Create a {@link ValueError} to throw, with a message along
         * the lines "bad marshal data (REASON)"
         *
         * @param reason to insert
         * @return to throw
         */
        protected static ValueError badData(String reason) {
            return new ValueError("bad marshal data (%s)", reason);
        }
    }

    /**
     * A {@link Reader} that has a {@code java.io.InputStream} as its
     * source. When the underlying source is a file, it is preferable
     * for efficiency that this be a
     * {@code java.io.BufferedInputStream}. A
     * {@code java.io.ByteArrayInputStream} needs no additional
     * buffering.
     */
    static class StreamReader extends Reader {

        /**
         * The source wrapped in a {@code DataInputStream} on which we
         * shall call {@code getInt()} etc. to read items. A Python
         * marshal stream is little-endian, while Java will read
         * big-endian data. However, note that
         * {@code Integer.reverseBytes()} and friends are HotSpot
         * intrinsics.
         */
        private final DataInputStream file;

        /**
         * Form a {@link Reader} on a {@code java.io.InputStream}.
         *
         * @param file input
         */
        StreamReader(InputStream file) {
            this.file = new DataInputStream(file);
        }

        @Override
        int readByte() {
            try {
                return file.readByte() & 0xff;
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        int readShort() {
            try {
                return Short.reverseBytes(file.readShort());
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        int readInt() {
            try {
                return Integer.reverseBytes(file.readInt());
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        @Override
        long readLong() {
            try {
                return Long.reverseBytes(file.readLong());
            } catch (IOException ioe) {
                throw new OSError(ioe);
            }
        }

        /**
         * Recognise or wrap an eligible file-like data source as an
         * {@code InputStream}.
         */
        private static InputStream adapt(Object file)
                throws NoConversion {
            if (file instanceof InputStream) {
                return (InputStream)file;
            } else {
                // Adapt any object with read returning bytes
                // But for now ...
                throw PyObjectUtil.NO_CONVERSION;
            }
        }
    }

    /**
     * A {@link Reader} that has a {@code ByteBuffer} as its source.
     */
    static class BytesReader extends Reader {

        /**
         * The source as little-endian a {@code ByteBuffer} on which we
         * shall call {@code getInt()} etc. to read items. A Python
         * marshal stream is little-endian
         */
        final ByteBuffer buf;

        /**
         * Form a {@link Reader} on a byte array.
         *
         * @param bytes input
         */
        BytesReader(byte[] bytes) { this(ByteBuffer.wrap(bytes)); }

        /**
         * Form a {@link Reader} on an existing {@code ByteBuffer}. This
         * {@code ByteBuffer} will have its order set to
         * {@code ByteOrder.LITTLE_ENDIAN}.
         *
         * @param buf input
         */
        BytesReader(ByteBuffer buf) {
            this.buf = buf;
            buf.order(ByteOrder.LITTLE_ENDIAN);
        }

        @Override
        int readByte() {
            try {
                return buf.get() & 0xff;
            } catch (BufferUnderflowException boe) {
                throw endOfData();
            }
        }

        @Override
        int readShort() {
            try {
                return buf.getShort();
            } catch (BufferUnderflowException boe) {
                throw endOfData();
            }
        }

        @Override
        int readInt() {
            try {
                return buf.getInt();
            } catch (BufferUnderflowException boe) {
                throw endOfData();
            }
        }

        @Override
        long readLong() {
            try {
                return buf.getLong();
            } catch (BufferUnderflowException boe) {
                throw endOfData();
            }
        }

        /**
         * Recognise or wrap an eligible file-like data source as a
         * {@code ByteBuffer}.
         */
        private static ByteBuffer adapt(Object bytes)
                throws NoConversion {
            if (bytes instanceof ByteBuffer) {
                return (ByteBuffer)bytes;
            } else if (bytes instanceof PyBytes) {
                return ((PyBytes)bytes).getNIOByteBuffer();
            } else {
                if (bytes instanceof byte[]) {
                    ByteBuffer bb = ByteBuffer.wrap((byte[])bytes);
                    return bb;
                } else {
                    // Adapt any object with read returning bytes
                    // But for now ...
                    throw PyObjectUtil.NO_CONVERSION;
                }
            }
        }
    }

    /** {@link Codec} for Python {@code bool}. */
    private static class BoolCodec implements Codec {
        @Override
        public PyType type() { return PyBool.TYPE; }

        @Override
        public void write(Writer w, Object v)
                throws IOException, Throwable {
            assert type().checkExact(v);
            // Must be Boolean
            w.writeByte((Boolean)v ? TYPE_TRUE : TYPE_FALSE);
        }

        @Override
        public Iterable<Entry<Integer, Decoder>> decoders() {
            Map<Integer, Decoder> m = new HashMap<>();
            m.put(TYPE_FALSE, r -> Py.False);
            m.put(TYPE_TRUE, r -> Py.True);
            return m.entrySet();
        }
    }

    /** {@link Codec} for Python {@code int}. */
    private static class IntCodec implements Codec {
        @Override
        public PyType type() { return PyLong.TYPE; }

        @Override
        public void write(Writer w, Object v)
                throws IOException, Throwable {
            assert type().checkExact(v);
            // May be Integer or BigInteger
            if (v instanceof Integer) {
                w.writeByte(TYPE_INT);
                w.writeInt(((Integer)v).intValue());
            } else {
                w.writeByte(TYPE_LONG);
                w.writeBigInteger((BigInteger)v);
            }
        }

        @Override
        public Iterable<Entry<Integer, Decoder>> decoders() {
            Map<Integer, Decoder> m = new HashMap<>();
            m.put(TYPE_INT, r -> r.readInt());
            m.put(TYPE_LONG, r -> r.readBigInteger());
            return m.entrySet();
        }
    }
}
