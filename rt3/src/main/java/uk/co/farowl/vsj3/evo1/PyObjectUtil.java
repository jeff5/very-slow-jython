package uk.co.farowl.vsj3.evo1;

import java.util.Map;
import java.util.StringJoiner;

/** Miscellaneous static helpers common to built-in objects. */
class PyObjectUtil {

    private PyObjectUtil() {} // no instances

    /**
     * Convenient wrapper for sequence types implementing
     * {@code __mul__}, so that they need only provide a
     * {@link PySequence#repeat(int)} implementation. The wrapper takes
     * care of object conversion and errors that arise from it.
     */
    static PySequence repeat(PySequence self, Object n)
            throws TypeError, Throwable {
        if (Number.indexCheck(n)) {
            int count = Number.asSize(n, OverflowError::new);
            return self.repeat(count);
        } else {
            throw Abstract.typeError(CANT_MULTIPLY, n);
        }
    }

    private static final String CANT_MULTIPLY =
            "can't multiply sequence by non-int of type '%.200s'";

    /**
     * An implementation of {@code dict.__repr__} that may be applied to
     * any Java {@code Map} between {@code Object}s, in which kets and
     * values are represented as with {@code repr()}.
     *
     * @param map to be reproduced
     * @return a string like <code>{'a': 2, 'b': 3}</code>
     * @throws Throwable
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
}
