package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test the {@link PySequence} API class on a variety of types. We are
 * looking for correct behaviour in the cases attempted but mostly
 * testing the invocation of special methods through the operations
 * objects of the particular implementation classes.
 * <p>
 * To reach our main goal, we need only try enough types to exercise
 * every abstract method once in some type.
 */
@DisplayName("The API class Abstract")
class AbstractSequenceAPITest extends UnitTestSupport {

    /**
     * This abstract base forms a check-list of methods we mean to test.
     */
    abstract static class Standard {

        /** Test {@link Abstract#size(Object) Abstract.size} */
        abstract void supports_size() throws Throwable;

/// **
// * Test {@link Abstract#concat(Object, Object) Abstract.concat}
// */
// abstract void supports_concat() throws Throwable;
//
/// ** Test {@link Abstract#repeat(Object, int) Abstract.repeat} */
// abstract void supports_repeat() throws Throwable;

        /**
         * Test {@link Abstract#getItem(Object, int) Abstract.getItem}
         */
        abstract void supports_getItem() throws Throwable;

/// **
// * Test {@link Abstract#setItem(Object, int, Object)
// * Abstract.setItem}
// */
// abstract void supports_setItem() throws Throwable;
//
/// **
// * Test {@link Abstract#delItem(Object, int) Abstract.delItem}
// */
// abstract void supports_delItem() throws Throwable;
//
/// **
// * Test {@link Abstract#getSlice(Object, int, int)
// * Abstract.getSlice}
// */
// abstract void supports_getSlice() throws Throwable;
//
/// **
// * Test {@link Abstract#setSlice(Object, int, int, Object)
// * Abstract.setSlice}
// */
// abstract void supports_setSlice() throws Throwable;
//
/// **
// * Test {@link Abstract#delSlice(Object, int, int)
// * Abstract.delSlice}
// */
// abstract void supports_delSlice() throws Throwable;
//
/// ** Test {@link Abstract#tuple(Object) Abstract.tuple} */
// abstract void supports_tuple() throws Throwable;
//
/// ** Test {@link Abstract#list(Object) Abstract.list} */
// abstract void supports_list() throws Throwable;
//
/// **
// * Test {@link Abstract#count(Object, Object) Abstract.count}
// */
// abstract void supports_count() throws Throwable;
//
/// **
// * Test {@link Abstract#contains(Object, Object)
// * Abstract.contains}
// */
// abstract void supports_contains() throws Throwable;
//
/// ** Test {@link Abstract#in(Object, Object) Abstract.in} */
// abstract void supports_in() throws Throwable;
//
//// Not to be confused with PyNumber.index
/// **
// * Test {@link Abstract#index(Object, Object) Abstract.index}
// */
// abstract void supports_index() throws Throwable;
    }

    /** There is just one implementation of {@code byte}. */
    static abstract class SequenceTest extends Standard {

        // Working variables for the tests
        final ArrayList<Object> ref;
        final Object obj;

        SequenceTest(List<?> ref, Object obj) {
            this.ref = new ArrayList<>(ref);
            this.obj = obj;
        }

        @Override
        @Test
        void supports_size() throws Throwable {
            Object r = Abstract.size(obj);
            assertEquals(ref.size(), r);
        }

        @Override
        @Test
        void supports_getItem() throws Throwable {
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
    }

    @Nested
    @DisplayName("the bytes object")
    static abstract class BytesTest extends SequenceTest {
        BytesTest(byte[] ref) { super(asList(ref), new PyBytes(ref)); }

        BytesTest(String s, String enc)
                throws UnsupportedEncodingException {
            this(s.getBytes(enc));
        }

        static ArrayList<Integer> asList(byte[] a) {
            ArrayList<Integer> c = new ArrayList<>(a.length);
            for (byte b : a) { c.add(b & 0xff); }
            return c;
        }

        @Nested
        @DisplayName("b''")
        static class EmptyBytesTest extends BytesTest {
            EmptyBytesTest() { super(new byte[0]); }
        }

        @Nested
        @DisplayName("b'a'")
        static class SingleBytesTest extends BytesTest {
            SingleBytesTest() { super(new byte[] {97}); }
        }

        @Nested
        @DisplayName("b'café crème' (utf-8)")
        static class ShortBytesTest extends BytesTest {
            ShortBytesTest() throws UnsupportedEncodingException {
                super("café crème", "UTF-8");
            }
        }
    }

    @Nested
    @DisplayName("the tuple object")
    static abstract class TupleTest extends SequenceTest {
        TupleTest(Object... ref) {
            super(asList(ref), new PyTuple(ref));
        }

        static ArrayList<Object> asList(Object[] a) {
            ArrayList<Object> c = new ArrayList<>(a.length);
            for (Object o : a) { c.add(o); }
            return c;
        }

        @Nested
        @DisplayName("()")
        static class EmptyTupleTest extends TupleTest {
            EmptyTupleTest() { super(); }
        }

        @Nested
        @DisplayName("('hello',)")
        static class SingleTupleTest extends TupleTest {
            SingleTupleTest() { super("hello"); }
        }

        @Nested
        @DisplayName("(None, 1, int)")
        static class ShortTupleTest extends TupleTest {
            ShortTupleTest() throws UnsupportedEncodingException {
                super(Py.None, 1, PyLong.TYPE);
            }
        }
    }
}
