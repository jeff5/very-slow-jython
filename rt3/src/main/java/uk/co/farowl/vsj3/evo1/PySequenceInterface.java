package uk.co.farowl.vsj3.evo1;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Sequences exhibit certain common behaviours and utilities that
 * implement them need to call back into the object by this interface.
 * This interface cannot be used as a marker for objects that implement
 * the sequence protocol because it is perfectly possible for a
 * user-defined Python type to do so without its Java implementation
 * implementing {@code PySequenceInterface}. A proxy class could be
 * created that holds such an object and does implement
 * {@code PySequenceInterface}.
 *
 * @param <T> the type of element returned by the iterators
 */
interface PySequenceInterface<T>
        extends Iterable<T>, Comparable<PySequenceInterface<T>> {

    /**
     * Provide the type of sequence object, primarily for use in error
     * messages e.g. "&lt;TYPE> index out of bounds".
     *
     * @implNote This can simply return a constant characteristic of the
     *     the implementing class, the Python type implements or
     *     supports. E.g the adaptor for a Java String returns
     *     {@code PyUnicode.TYPE} which is {@code str}.
     *
     * @return the type of sequence
     */
    PyType getType();

    /**
     * The length of this sequence.
     *
     * @return the length of {@code this} sequence
     */
    int length();

    /**
     * Get one element from the sequence.
     *
     * @param i index of element to get
     * @return value at {@code i}th position
     */
    T getItem(int i);

    // /** Set one element from the sequence (if mutable). */
    // void setItem(int i, PyObject v);

    /**
     * {@inheritDoc} The characteristics {@code SIZED} and
     * {@code SUBSIZED} are additionally reported.
     */
    @Override
    default Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), length(), 0);
    }

    /**
     * @return the elements of this sequence as a {@code Stream}
     */
    default Stream<T> asStream() {
        return StreamSupport.stream(spliterator(), false);
    }

    /**
     * Return a new sequence of a type determined by the implementer,
     * that is the concatenation of the target object with a sequence of
     * the same type. It is expected that the returned value be a Python
     * object.
     *
     * @param other to follow values of {@code this} in the result
     * @return the concatenation {@code this + other}
     * @throws OutOfMemoryError from allocating space for the result
     */
    Object concat(PySequenceInterface<T> other) throws OutOfMemoryError;

    /**
     * Return a sequence of the target type, by repeatedly concatenating
     * {@code n} copies of the present value.
     *
     * @param n number of repeats
     * @return repeating sequence
     * @throws OutOfMemoryError from allocating space for the result
     */
    Object repeat(int n) throws OutOfMemoryError;

    /**
     * A specialisation of PySequenceInterface<Integer> where the
     * elements may be provided as primitive {@code int}.
     */
    interface OfInt extends PySequenceInterface<Integer> {

         @Override
         Spliterator.OfInt spliterator();

        /**
         * Provide a stream specialised to primitive {@code int}.
         *
         * @return a stream of primitive {@code int}
         */
        IntStream asIntStream();

        /**
         * {@inheritDoc}
         *
         * @implNote The default implementation is the stream of values
         *     from {@link #asIntStream()}, boxed to {@code Integer}.
         *     Consumers that are able to will obtain improved
         *     efficiency by preferring {@link #asIntStream()} and
         *     specialising intermediate processing to {@code int}.
         */
        @Override
        default Stream<Integer> asStream() {
            return asIntStream().boxed();
        }
    }
}
