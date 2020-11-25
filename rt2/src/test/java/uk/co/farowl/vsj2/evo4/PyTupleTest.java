package uk.co.farowl.vsj2.evo4;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class PyTupleTest {

    /** One element 42 */
    /** Elements 10..16: use a stream for the heck of it. */
    final static PyLong[] A = IntStream.range(40, 46)
            .mapToObj((i) -> Py.val(i)).toArray((n) -> new PyLong[n]);
    final static PyObject[] B = {A[2]};
    /** No elements (e for empty) */
    final static PyObject[] E = {};
    /** {@code str} elements */
    final static PyUnicode[] U = {Py.str("Hello"), Py.str("world!")};

    PyTuple a, b, e, u;

    void createTuples() {
        a = new PyTuple(A);
        b = new PyTuple(B);
        e = new PyTuple(E);
        u = new PyTuple(U);
    }

    void checkAllTuples() {
        checkTuple(A, a, "a");
        checkTuple(B, b, "b");
        checkTuple(E, e, "e");
        checkTuple(U, u, "u");
    }

    /**
     * Check a tuple. This implicitly tests {@link PyTuple#size()},
     * {@link PyTuple#getType()} and {@link PyTuple#get(int)} for tuples
     * created by all feasible routes.
     *
     * @param expected value as array
     * @param actual constructed tuple
     * @param msg to issue on error
     */
    <T extends PyObject> void checkTuple(T[] expected, PyTuple actual,
            String msg) {
        int n = expected.length;
        assertEquals(n, actual.size(), String.format("%s.size()", msg));
        assertEquals(PyTuple.TYPE, actual.getType());
        for (int i = 0; i < n; i++)
            assertEquals(expected[i], actual.get(i),
                    String.format("%s[%d]", msg, i));
    }

    // Constructor from PyObject[]
    @Test
    void testPyTuplePyObjectArray() {
        createTuples();
        checkAllTuples();
        // The tuple is *not* a view on the array but a copy.
        final PyLong[] x = Arrays.copyOf(A, A.length);
        PyTuple t = new PyTuple(x);
        checkTuple(A, t, "t");
        x[x.length / 2] = Py.val(-1);  // Change an element.
        checkTuple(A, t, "t");
    }

    @Test
    void testWrap() {
        a = PyTuple.wrap(A);
        b = PyTuple.wrap(B);
        e = PyTuple.wrap(E);
        u = PyTuple.wrap(U);
        checkAllTuples();
        // The tuple *is* a view on the array, not a copy.
        final PyLong[] x = Arrays.copyOf(A, A.length);
        PyTuple t = PyTuple.wrap(x);
        checkTuple(A, t, "t");
        x[x.length / 2] = Py.val(-1);  // Change an element.
        checkTuple(x, t, "t");
    }

    // Constructor from PyObject[] slice
    @Test
    void testPyTuplePyObjectArrayIntInt() {
        int N = A.length;
        PyObject[] big = new PyObject[4 * N];
        Arrays.fill(big, Py.None);
        System.arraycopy(A, 0, big, 2 * N, N);  // third quarter
        System.arraycopy(U, 0, big, 1, U.length);
        // Tuples from slices of that array.
        a = new PyTuple(big, 2 * N, N);
        b = new PyTuple(big, 2 * N + 2, 1);
        e = new PyTuple(big, 2, 0);
        u = new PyTuple(big, 1, 2);
        checkAllTuples();
    }

    // Constructor from PyObject collection
    @Test
    void testPyTupleCollectionOfQextendsPyObject() {
        a = new PyTuple(Arrays.asList(A));
        b = new PyTuple(Collections.singleton(B[0]));
        e = new PyTuple(Collections.emptySet());
        u = new PyTuple(List.of(U));
        checkAllTuples();
    }

    @Test
    void testFrom() {
        a = PyTuple.from(Arrays.asList(A));
        b = PyTuple.from(Collections.singleton(B[0]));
        e = PyTuple.from(Collections.emptySet());
        u = PyTuple.from(List.of(U));
        checkAllTuples();

    }

    @Test
    void testImmutable() {
        final PyTuple x = new PyTuple(A);
        assertThrows(UnsupportedOperationException.class,
                () -> x.set(1, Py.None));
    }

    @Test
    void testImmutableToPython() {
        final PyTuple x = new PyTuple(A);
        assertThrows(TypeError.class,
                () -> Sequence.setItem(x, 2, Py.val(-1)));
    }

    /**
     * Check a tuple toString by splitting at the commas.
     * @param t tuple to test
     * @param commas how many commas to expect
     * @param msg identifying variable
     */
    void checkToString(PyTuple t, int commas, String msg) {
        String s = t.toString();
        assertTrue(s.length() >= 2);
        assertEquals(s.charAt(0), '(');
        assertEquals(s.charAt(s.length() - 1), ')');
        String[] parts = s.split(",", s.length());
        assertEquals(commas, parts.length-1, msg);
    }

    @Test
    void testToString() {
        createTuples();
        checkToString(a, A.length-1, "a");
        checkToString(b, 1, "b");
        checkToString(e, 0, "e");
        checkToString(u, U.length-1, "u");
    }

}
