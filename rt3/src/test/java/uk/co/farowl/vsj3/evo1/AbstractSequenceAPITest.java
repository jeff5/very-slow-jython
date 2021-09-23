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
import org.junit.jupiter.api.Nested;
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

    @DisplayName("Abstract.size")
    @ParameterizedTest(name = "size({1})")
    @MethodSource("readableProvider")
    void supports_size(List<Object> ref, Object obj) throws Throwable {
        Object r = Abstract.size(obj);
        assertEquals(ref.size(), r);
    }

    /// **
    // * Test {@link Abstract#concat(Object, Object) Abstract.concat}
    // */
    // void supports_concat(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link Abstract#repeat(Object, int) Abstract.repeat} */
    // void supports_repeat(List<Object> ref, Object obj) throws
    // Throwable;

    @DisplayName("Abstract.getItem")
    @ParameterizedTest(name = "getItem({1}, i)")
    @MethodSource("readableProvider")
    void supports_getItem(List<Object> ref, Object obj)
            throws Throwable {
        final int N = ref.size();
        for (int i = 0; i < N; i++) {
            Object r = Abstract.getItem(obj, i);
            assertEquals(ref.get(i), r);
        }
        // And again relative to the end -1...-N
        for (int i = 1; i <= N; i++) {
            Object r = Abstract.getItem(obj, -i);
            assertEquals(ref.get(N - i), r);
        }
        Class<IndexError> ie = IndexError.class;
        assertThrows(ie, () -> Abstract.getItem(obj, -(N + 1)));
        assertThrows(ie, () -> Abstract.getItem(obj, N));
    }

    /// **
    // * Test {@link Abstract#setItem(Object, int, Object)
    // * Abstract.setItem}
    // */
    // void supports_setItem(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#delItem(Object, int) Abstract.delItem}
    // */
    // void supports_delItem(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#getSlice(Object, int, int)
    // * Abstract.getSlice}
    // */
    // void supports_getSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#setSlice(Object, int, int, Object)
    // * Abstract.setSlice}
    // */
    // void supports_setSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#delSlice(Object, int, int)
    // * Abstract.delSlice}
    // */
    // void supports_delSlice(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link Abstract#tuple(Object) Abstract.tuple} */
    // void supports_tuple(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link Abstract#list(Object) Abstract.list} */
    // void supports_list(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#count(Object, Object) Abstract.count}
    // */
    // void supports_count(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// **
    // * Test {@link Abstract#contains(Object, Object)
    // * Abstract.contains}
    // */
    // void supports_contains(List<Object> ref, Object obj) throws
    // Throwable;
    //
    /// ** Test {@link Abstract#in(Object, Object) Abstract.in} */
    // void supports_in(List<Object> ref, Object obj) throws Throwable;
    //
    //// Not to be confused with PyNumber.index
    /// **
    // * Test {@link Abstract#index(Object, Object) Abstract.index}
    // */
    // void supports_index(List<Object> ref, Object obj) throws
    // Throwable;

}
