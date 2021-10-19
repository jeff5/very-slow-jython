package uk.co.farowl.vsj3.evo1;

import java.util.Map;
import java.util.StringJoiner;

import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

/**
 * Miscellaneous static helpers commonly needed to implement Python
 * objects in Java.
 */
class PyObjectUtil {

    private PyObjectUtil() {} // no instances

    /**
     * Convenient wrapper for sequence types implementing
     * {@code __getitem__}, so that they need only provide a
     * {@link PySequenceInterface#getItem(int)} implementation. The
     * wrapper takes care of index argument processing and range checks,
     * and errors that arise from it.
     * <p>
     * This method only deals with simple item indices that may be
     * converted to an {@code int}. Slices must be detected by the
     * caller.
     *
     * @param <T> target type of sequence
     * @param seq to get an item from
     * @param item indexing the required elements
     * @return the result
     * @throws TypeError if {@code n} has no {@code __index__}
     * @throws Throwable from implementation of {@code __index__} etc..
     */
    static <T> T getItem(PySequenceInterface<T> seq, Object item)
            throws Throwable {
        final int L = seq.length();
        int i = PyNumber.asSize(item, IndexError::new);
        if (i < 0) { i += L; }
        if (i < 0 || i >= L)
            throw new IndexError("%s index out of range",
                    seq.getType().getName());
        return seq.getItem(i);
    }

    // XXX Separate method needed to deal with slice.

    /**
     * Convenient wrapper for sequence types implementing
     * {@code __mul__}, so that they need only provide a
     * {@link PySequenceInterface#repeat(int)} implementation. The
     * wrapper takes care of object conversion and errors that arise
     * from it.
     *
     * @param <T> target type of sequence
     * @param seq to repeat
     * @param n number of repetitions in result
     * @return the repeating result
     * @throws TypeError if {@code n} has no {@code __index__}
     * @throws Throwable from implementation of {@code __index__} etc..
     */
    static <T> Object repeat(PySequenceInterface<T> seq, Object n)
            throws TypeError, Throwable {
        if (PyNumber.indexCheck(n)) {
            int count = PyNumber.asSize(n, OverflowError::new);
            try {
                return seq.repeat(count);
            } catch (OutOfMemoryError e) {
                throw repeatedOverflow(seq);
            }
        } else {
            throw Abstract.typeError(CANT_MULTIPLY, n);
        }
    }

    /**
     * Convenient wrapper for sequence types implementing
     * {@code __add__}, so that they need only provide a
     * {@link PySequenceInterface#concat(PySequenceInterface<T>)}
     * implementation. The wrapper takes care of object conversion and
     * errors that arise from it.
     *
     * @param <T> target type of sequence
     * @param v left operand
     * @param w right operand
     * @return the concatenated result
     * @throws Throwable from implementation.
     */
    static <T> Object concat(PySequenceInterface<T> v,
            PySequenceInterface<T> w) throws TypeError, Throwable {
        try {
            return v.concat(w);
        } catch (OutOfMemoryError e) {
            throw concatenatedOverflow(v);
        }
    }

    private static final String CANT_MULTIPLY =
            "can't multiply sequence by non-int of type '%.200s'";

    /**
     * An implementation of {@code dict.__repr__} that may be applied to
     * any Java {@code Map} between {@code Object}s, in which keys and
     * values are represented as with {@code repr()}.
     *
     * @param map to be reproduced
     * @return a string like <code>{'a': 2, 'b': 3}</code>
     * @throws Throwable from the {@code repr()} implementation
     */
    static String mapRepr(Map<? extends Object, ?> map)
            throws Throwable {
        StringJoiner sj = new StringJoiner(", ", "{", "}");
        for (Map.Entry<? extends Object, ?> e : map.entrySet()) {
            String key = Abstract.repr(e.getKey()).toString();
            String value = Abstract.repr(e.getValue()).toString();
            sj.add(key + ": " + value);
        }
        return sj.toString();
    }

    /**
     * A string along the lines "T object at 0xhhh", where T is the type
     * of {@code o}. This is for creating default {@code __repr__}
     * implementations seen around the code base and containing this
     * form. By implementing it here, we encapsulate the problem of
     * qualified type name and what "address" or "identity" should mean.
     *
     * @param o the object (not its type)
     * @return string denoting {@code o}
     */
    static String toAt(Object o) {
        // For the time being identity means:
        int id = System.identityHashCode(o);
        // For the time being type name means:
        String typeName = PyType.of(o).name;
        return String.format("%s object at %#x", typeName, id);
    }

    /**
     * The type of exception thrown when an attempt to convert an object
     * to a common data type fails. This type of exception carries no
     * stack context, since it is used only as a sort of "alternative
     * return value".
     */
    static class NoConversion extends Exception {
        private static final long serialVersionUID = 1L;

        private NoConversion() { super(null, null, false, false); }
    }

    /**
     * A statically allocated {@link NoConversion} used in conversion
     * methods to signal "cannot convert". No stack context is preserved
     * in the exception.
     */
    static final NoConversion NO_CONVERSION = new NoConversion();

    /**
     * An overflow error with the message "concatenated S is too long",
     * where S is the type mane of the argument.
     *
     * @param seq the sequence operated on
     * @return an exception to throw
     */
    static <T> OverflowError
            concatenatedOverflow(PySequenceInterface<T> seq) {
        return new OverflowError("concatenated %s is too long",
                seq.getType().getName());
    }

    /**
     * An overflow error with the message "repeated S is too long",
     * where S is the type mane of the argument.
     *
     * @param seq the sequence operated on
     * @return an exception to throw
     */
    static <T> OverflowError
            repeatedOverflow(PySequenceInterface<T> seq) {
        return new OverflowError("repeated %s is too long",
                seq.getType().getName());
    }
}
