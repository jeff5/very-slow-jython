// Copyright (c)2022 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1.modules;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj3.evo1.PyBytes;
import uk.co.farowl.vsj3.evo1.UnitTestSupport;
import uk.co.farowl.vsj3.evo1.modules.marshal.BytesReader;
import uk.co.farowl.vsj3.evo1.modules.marshal.BytesWriter;
import uk.co.farowl.vsj3.evo1.modules.marshal.Reader;
import uk.co.farowl.vsj3.evo1.modules.marshal.StreamReader;
import uk.co.farowl.vsj3.evo1.modules.marshal.StreamWriter;
import uk.co.farowl.vsj3.evo1.modules.marshal.Writer;
import uk.co.farowl.vsj3.evo1.stringlib.ByteArrayBuilder;

class marshalTest extends UnitTestSupport {

    /**
     * Base of tests that read or write elementary values where a
     * reference is available serialised by CPython.
     */
    abstract static class AbstractElementTest {

        /**
         * Test cases for serialising 16-bit ints.
         *
         * @return the examples
         */
        static Stream<Arguments> int16() {
            return Stream.of( //
                    intArguments(0, bytes(0x00, 0x00)), //
                    intArguments(1, bytes(0x01, 0x00)), //
                    intArguments(-42, bytes(0xd6, 0xff)),
                    intArguments(Short.MAX_VALUE, bytes(0xff, 0x7f)));
        }

        /**
         * Test cases for serialising 32-bit ints.
         *
         * @return the examples
         */
        static Stream<Arguments> int32() {
            return Stream.of( //
                    intArguments(0, bytes(0x00, 0x00, 0x00, 0x00)),
                    intArguments(1, bytes(0x01, 0x00, 0x00, 0x00)),
                    intArguments(-42, bytes(0xd6, 0xff, 0xff, 0xff)),
                    intArguments(Integer.MAX_VALUE,
                            bytes(0xff, 0xff, 0xff, 0x7f)));
        }

        /**
         * Test cases for serialising 64-bit ints.
         *
         * @return the examples
         */
        static Stream<Arguments> int64() {
            return Stream.of( //
                    longArguments(0,
                            bytes(0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00)),
                    longArguments(1,
                            bytes(0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00)),
                    longArguments(-42,
                            bytes(0xd6, 0xff, 0xff, 0xff, 0xff, 0xff,
                                    0xff, 0xff)),
                    longArguments(7450580596923828125L,
                            bytes(0x9d, 0x07, 0x10, 0xfa, 0x93, 0xc7,
                                    0x65, 0x67)),
                    longArguments(Long.MAX_VALUE, bytes(0xff, 0xff,
                            0xff, 0xff, 0xff, 0xff, 0xff, 0x7f)));
        }

        /**
         * Test cases for serialising {@code BigInteger}s.
         *
         * @return the examples
         */
        static Stream<Arguments> bigint() {
            return Stream.of( //
                    arguments(new BigInteger("17557851463681"), //
                            bytes(0x03, 0x00, 0x00, 0x00, 0x01, 0x60,
                                    0xff, 0x02, 0xe0, 0x3f)),
                    arguments(new BigInteger("35184372088832"), //
                            bytes(0x04, 0x00, 0x00, 0x00, 0x00, 0x00,
                                    0x00, 0x00, 0x00, 0x00, 0x01,
                                    0x00)),
                    arguments(
                            new BigInteger(
                                    "-2232232135326160725639168"), //
                            bytes(0xfa, 0xff, 0xff, 0xff, 0x00, 0x00,
                                    0xfd, 0x20, 0xa7, 0x39, 0x4b, 0x5f,
                                    0x18, 0x0b, 0x3b, 0x00)));
        }

        /**
         * Wrap a {@code byte}, {@code short} or {@code int} expected
         * value and its marshalled form as a arguments for a test.
         *
         * @param expected result
         * @param bytes containing value to decode
         * @return arguments for the test
         */
        private static Arguments intArguments(int expected,
                byte[] bytes) {
            return arguments(expected, bytes);
        }

        /**
         * Wrap a {@code long} expected value and its marshalled form as
         * a arguments for a test.
         *
         * @param expected result
         * @param bytes containing value to decode
         * @return arguments for the test
         */
        private static Arguments longArguments(long expected,
                byte[] bytes) {
            assert bytes.length == 8;
            return arguments(expected, bytes);
        }
    }

    /**
     * Tests reading from a {@code ByteBuffer}, which is also how we
     * shall address objects with the Python buffer protocol
     * ({@link PyBytes} etc.), and native {@code byte[]}.
     */
    @Nested
    @DisplayName("Read elementary values from bytes")
    class ReadBytesElementary extends AbstractElementTest {

        @DisplayName("r.readShort()")
        @ParameterizedTest(name = "r.readShort() = {0}")
        @MethodSource("int16")
        void int16read(Integer expected, byte[] b) {
            Reader r = new BytesReader(b);
            assertEquals(expected, r.readShort());
        }

        @DisplayName("r.readInt()")
        @ParameterizedTest(name = "r.readInt() = {0}")
        @MethodSource("int32")
        void int32read(Integer expected, byte[] b) {
            Reader r = new BytesReader(b);
            assertEquals(expected, r.readInt());
        }

        @DisplayName("r.readLong()")
        @ParameterizedTest(name = "r.readInt() = {0}")
        @MethodSource("int64")
        void int64read(Long expected, byte[] b) {
            Reader r = new BytesReader(b);
            assertEquals(expected, r.readLong());
        }

        @DisplayName("r.readBigInteger()")
        @ParameterizedTest(name = "r.readBigInteger() = {0}")
        @MethodSource("bigint")
        void bigintread(BigInteger expected, byte[] b) {
            Reader r = new BytesReader(b);
            assertEquals(expected, r.readBigInteger());
        }
    }

    /**
     * Tests reading elementary values from an {@code InputStream},
     * which is also how we shall address file-like objects in Python,
     * and native Java input streams.
     */
    @Nested
    @DisplayName("Read elementary values from a stream")
    class ReadStreamElementary extends AbstractElementTest {

        @DisplayName("r.readShort()")
        @ParameterizedTest(name = "r.readShort() = {0}")
        @MethodSource("int16")
        void int16read(Integer expected, byte[] b) {
            Reader r = new StreamReader(new ByteArrayInputStream(b));
            assertEquals(expected, r.readShort());
        }

        @DisplayName("r.readInt()")
        @ParameterizedTest(name = "r.readInt() = {0}")
        @MethodSource("int32")
        void int32read(Integer expected, byte[] b) {
            Reader r = new StreamReader(new ByteArrayInputStream(b));
            assertEquals(expected, r.readInt());
        }

        @DisplayName("r.readLong()")
        @ParameterizedTest(name = "r.readInt() = {0}")
        @MethodSource("int64")
        void int64read(Long expected, byte[] b) {
            Reader r = new StreamReader(new ByteArrayInputStream(b));
            assertEquals(expected, r.readLong());
        }

        @DisplayName("r.readBigInteger()")
        @ParameterizedTest(name = "r.readBigInteger() = {0}")
        @MethodSource("bigint")
        void bigintread(BigInteger expected, byte[] b) {
            Reader r = new StreamReader(new ByteArrayInputStream(b));
            assertEquals(expected, r.readBigInteger());
        }
    }

    /**
     * Tests writing to a {@code ByteArrayBuilder}, which is how we
     * create a {@link PyBytes} serialising an object. In the test, we
     * recover a native {@code byte[]} to compare with the expected
     * bytes.
     */
    @Nested
    @DisplayName("Write elementary values to bytes")
    class WriteBytesElementary extends AbstractElementTest {

        @DisplayName("w.writeShort()")
        @ParameterizedTest(name = "w.writeShort({0})")
        @MethodSource("int16")
        void int16write(Integer v, byte[] expected) {
            ByteArrayBuilder b = new ByteArrayBuilder(2);
            Writer w = new BytesWriter(b, 4);
            w.writeShort(v);
            assertArrayEquals(expected, b.take());
        }

        @DisplayName("w.writeInt()")
        @ParameterizedTest(name = "w.writeInt({0})")
        @MethodSource("int32")
        void int32write(Integer v, byte[] expected) {
            ByteArrayBuilder b = new ByteArrayBuilder(4);
            Writer w = new BytesWriter(b, 4);
            w.writeInt(v);
            assertArrayEquals(expected, b.take());
        }

        @DisplayName("w.writeLong()")
        @ParameterizedTest(name = "w.writeInt({0})")
        @MethodSource("int64")
        void int64write(Long v, byte[] expected) {
            ByteArrayBuilder b = new ByteArrayBuilder(8);
            Writer w = new BytesWriter(b, 4);
            w.writeLong(v);
            assertArrayEquals(expected, b.take());
        }

        @DisplayName("w.writeBigInteger()")
        @ParameterizedTest(name = "w.writeBigInteger({0})")
        @MethodSource("bigint")
        void bigintwrite(BigInteger v, byte[] expected) {
            ByteArrayBuilder b = new ByteArrayBuilder();
            Writer w = new BytesWriter(b, 4);
            w.writeBigInteger(v);
            assertArrayEquals(expected, b.take());
        }
    }

    /**
     * Tests writing elementary values to an {@code OutputStream}, which
     * is also how we shall address file-like objects in Python, and
     * native Java input streams. In the test, we write to a
     * {@link ByteArrayOutputStream} and recover a native {@code byte[]}
     * to compare with the expected bytes.
     */
    @Nested
    @DisplayName("Write elementary values to a stream")
    class WriteStreamElementary extends AbstractElementTest {

        @DisplayName("w.writeShort()")
        @ParameterizedTest(name = "w.writeShort({0})")
        @MethodSource("int16")
        void int16write(Integer v, byte[] expected) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Writer w = new StreamWriter(b, 4);
            w.writeShort(v);
            assertArrayEquals(expected, b.toByteArray());
        }

        @DisplayName("w.writeInt()")
        @ParameterizedTest(name = "w.writeInt({0})")
        @MethodSource("int32")
        void int32write(Integer v, byte[] expected) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Writer w = new StreamWriter(b, 4);
            w.writeInt(v);
            assertArrayEquals(expected, b.toByteArray());
        }

        @DisplayName("w.writeLong()")
        @ParameterizedTest(name = "w.writeInt({0})")
        @MethodSource("int64")
        void int64write(Long v, byte[] expected) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Writer w = new StreamWriter(b, 4);
            w.writeLong(v);
            assertArrayEquals(expected, b.toByteArray());
        }

        @DisplayName("w.writeBigInteger()")
        @ParameterizedTest(name = "w.writeBigInteger({0})")
        @MethodSource("bigint")
        void bigintwrite(BigInteger v, byte[] expected) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            Writer w = new StreamWriter(b, 4);
            w.writeBigInteger(v);
            assertArrayEquals(expected, b.toByteArray());
        }
    }

    // Support methods ------------------------------------------------

    /**
     * Copy values to a new {@code byte[]} casting each to a
     * {@code byte}.
     *
     * @param v to convert to {@code byte}
     * @return the byte array of cast values
     */
    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < b.length; i++) { b[i] = (byte)v[i]; }
        return b;
    }
}
