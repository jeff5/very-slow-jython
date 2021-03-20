package uk.co.farowl.vsj3.evo1;

/**
 * Sequences exhibit certain common behaviours and utilities that
 * implement them need to call back into the object by this interface.
 * This interface cannot be used as a marker for objects that implement
 * the sequence protocol because it is perfectly possible for a
 * user-defined Python type to do so without its Java implementation
 * implementing {@code PySequence}. A proxy class could be created that
 * holds such an object and does implement {@code PySequence}.
 */
interface PySequenceInterface<T>
        extends Iterable<T>, Comparable<PySequenceInterface<T>>

{

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
     * Return a new sequence a type determined by the implementer, that
     * is the concatenation of the target object with a sequence of the
     * same type. It is expected that the returned value be a Python
     * object.
     *
     * @param other to follow values of {@code this} in the result
     * @return the concatenation {@code this + other}
     */
    Object concat(PySequenceInterface<T> other);

    /**
     * Return a sequence of the target type, by repeatedly concatenating
     * {@code n} copies of the present value.
     *
     * @param n number of repeats
     * @return repeating sequence
     */
    Object repeat(int n);
}
