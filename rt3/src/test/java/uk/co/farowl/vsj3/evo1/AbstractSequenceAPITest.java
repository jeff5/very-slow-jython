package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Test the {@link PySequence} API class on a variety of types. We are
 * looking for correct behaviour in the cases attempted but mostly
 * testing the invocation of special methods through the operations
 * objects of the particular implementation classes.
 * <p>
 * To reach our main goal, we need only try enough types to exercise
 * every abstract method once in some type.
 */
@DisplayName("In the Abstract API for sequences")
class AbstractSequenceAPITest extends UnitTestSupport {

    /**
     * Provide a stream of examples as parameter sets to the tests of
     * methods that do not mutate their arguments. Each argument object
     * provides a reference value and a test object compatible with the
     * parameterised test methods.
     *
     * @return the examples for non-mutating tests.
     */
    static Stream<Arguments> readableProvider() {
        return Stream.of(//
                bytesExample(), //
                bytesExample("a"), //
                bytesExample("café crème"), //
                tupleExample(), //
                tupleExample(42), //
                tupleExample(Py.None, 1, PyLong.TYPE) //
        );
    }

    /**
     * Construct an example with a Python {@code bytes}, from text.
     *
     * @param s to encode to bytes
     * @return the example (a reference value and a test object)
     */
    static Arguments bytesExample(String s) {
        try {
            return bytesExample(s.getBytes("UTF-8"));
        } catch (UnsupportedEncodingException e) {
            fail("failed to encode bytes");
            return arguments();
        }
    }

    /**
     * Construct an example with a Python {@code bytes}, from bytes.
     *
     * @param a the bytes
     * @return the example (a reference value and a test object)
     */
    static Arguments bytesExample(byte... a) {
        PyBytes obj = new PyBytes(a);
        ArrayList<Object> ref = new ArrayList<>(a.length);
        for (byte b : a) { ref.add(b & 0xff); }
        return arguments(ref, obj);
    }

    /**
     * Construct an example with a Python {@code tuple}, from arbitrary
     * objects.
     *
     * @param a the objects
     * @return the example (a reference value and a test object)
     */
    static Arguments tupleExample(Object... a) {
        PyTuple obj = new PyTuple(a);
        return arguments(List.of(a), obj);
    }

    /**
     * Test {@link PySequence#size(Object) PySequence.size}
     *
     * @param ref a list having elements equal to those of {@code obj}
     * @param obj Python object under test
     * @throws Throwable from the implementation
     */
    @DisplayName("PySequence.size")
    @ParameterizedTest(name = "size({1})")
    @MethodSource("readableProvider")
    void supports_size(List<Object> ref, Object obj) throws Throwable {
        Object r = PySequence.size(obj);
        assertEquals(ref.size(), r);
    }

    /// **
    // * Test {@link PySequence#concat(Object, Object)
    /// PySequence.concat}
    // */
    // void supports_concat(List<Object> ref, Object obj) throws
    // Throwable;

    /**
     * Test {@link PySequence#repeat(Object, int) PySequence.repeat}
     *
     * @param ref a list having elements equal to those of {@code obj}
     * @param obj Python object under test
     * @throws Throwable from the implementation
     */
    @DisplayName("PySequence.repeat")
    @ParameterizedTest(name = "repeat({1}, n)")
    @MethodSource("readableProvider")
    void supports_repeat(List<Object> ref, Object obj)
            throws Throwable {
        final int N = ref.size();
        // Try this for a few repeat sizes.
        for (int n = 0; n <= 3; n++) {
            Object r = PySequence.repeat(obj, n);
            assertEquals(PyType.of(obj), PyType.of(r)); // Same type
            assertEquals(N * n, PySequence.size(r));    // Right length
            // Now check all the elements (if n*N != 0).
            for (int i = 0; i < N * n; i++) {
                Object e = PySequence.getItem(r, i);
                assertEquals(ref.get(i % N), e);
            }
        }
    }

    /**
     * Test {@link PySequence#getItem(Object, int) PySequence.repeat}
     *
     * @param ref a list having elements equal to those of {@code obj}
     * @param obj Python object under test
     * @throws Throwable from the implementation
     */
    @DisplayName("PySequence.getItem")
    @ParameterizedTest(name = "getItem({1}, i)")
    @MethodSource("readableProvider")
    void supports_getItem(List<Object> ref, Object obj)
            throws Throwable {
        final int N = ref.size();
        for (int i = 0; i < N; i++) {
            Object r = PySequence.getItem(obj, i);
            assertEquals(ref.get(i), r);
        }
        // And again relative to the end -1...-N
        for (int i = 1; i <= N; i++) {
            Object r = PySequence.getItem(obj, -i);
            assertEquals(ref.get(N - i), r);
        }
        Class<IndexError> ie = IndexError.class;
        assertThrows(ie, () -> PySequence.getItem(obj, -(N + 1)));
        assertThrows(ie, () -> PySequence.getItem(obj, N));
    }

    /// **
    // * Test {@link PySequence#setItem(Object, int, Object)
    // * PySequence.setItem}
    // */
    // void supports_setItem(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#delItem(Object, int) PySequence.delItem}
    // */
    // void supports_delItem(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#getSlice(Object, int, int)
    // * PySequence.getSlice}
    // */
    // void supports_getSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#setSlice(Object, int, int, Object)
    // * PySequence.setSlice}
    // */
    // void supports_setSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#delSlice(Object, int, int)
    // * PySequence.delSlice}
    // */
    // void supports_delSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link PySequence#tuple(Object) PySequence.tuple} */
    // void supports_tuple(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link PySequence#list(Object) PySequence.list} */
    // void supports_list(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#count(Object, Object) PySequence.count}
    // */
    // void supports_count(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link PySequence#contains(Object, Object)
    // * PySequence.contains}
    // */
    // void supports_contains(List<Object> ref, Object obj) throws
    // Throwable;
    //
    //// Not to be confused with PyNumber.index
    /// **
    // * Test {@link PySequence#index(Object, Object) PySequence.index}
    // */
    // void supports_index(List<Object> ref, Object obj) throws
    // Throwable;

}
