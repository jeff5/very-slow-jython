// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj3.evo1.PySlice.Indices;
import uk.co.farowl.vsj3.evo1.Slot.EmptyException;

/**
 * Abstract API for operations on sequence types, corresponding to
 * CPython methods defined in {@code abstract.h} and with names like:
 * {@code PySequence_*}.
 */
public class PySequence extends Abstract {

    private PySequence() {} // only static methods here

    /**
     * {@code len(o)} with Python semantics.
     *
     * @param o object to operate on
     * @return {@code len(o)}
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PyObject_Size in abstract.c
    static int size(Object o) throws Throwable {
        // Note that the slot is called op_len but this method, size.
        try {
            return (int)Operations.of(o).op_len.invokeExact(o);
        } catch (Slot.EmptyException e) {
            throw typeError(HAS_NO_LEN, o);
        }
    }

    /**
     * {@code o * count} with Python semantics.
     *
     * @param o object to operate on
     * @param count number of repetitions
     * @return {@code o*count}
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PySequence_Repeat in abstract.c
    public static Object repeat(Object o, int count) throws Throwable {
        // There is no equivalent slot to sq_repeat
        return PyNumber.multiply(o, count);
    }

    /**
     * {@code v + w} for sequences with Python semantics.
     *
     * @param v first object to concatenate
     * @param w second object to concatenate
     * @return {@code v + w}
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PySequence_Concat in abstract.c
    public static Object concat(Object v, Object w) throws Throwable {
        // There is no equivalent slot to sq_concat
        return PyNumber.add(v, w);
    }

    /**
     * {@code o[key]} with Python semantics, where {@code o} may be a
     * mapping or a sequence.
     *
     * @param o object to operate on
     * @param key index
     * @return {@code o[key]}
     * @throws TypeError when {@code o} does not allow subscripting
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PyObject_GetItem in abstract.c
    static Object getItem(Object o, Object key) throws Throwable {
        // Decisions are based on types of o and key
        try {
            Operations ops = Operations.of(o);
            return ops.op_getitem.invokeExact(o, key);
        } catch (EmptyException e) {
            throw typeError(NOT_SUBSCRIPTABLE, o);
        }
    }

    /**
     * {@code o[key] = value} with Python semantics, where {@code o} may
     * be a mapping or a sequence.
     *
     * @param o object to operate on
     * @param key index
     * @param value to put at index
     * @throws TypeError when {@code o} does not allow subscripting
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PyObject_SetItem in abstract.c
    static void setItem(Object o, Object key, Object value)
            throws Throwable {
        // Decisions are based on types of o and key
        Operations ops = Operations.of(o);
        try {
            ops.op_setitem.invokeExact(o, key, value);
            return;
        } catch (EmptyException e) {
            throw typeError(DOES_NOT_SUPPORT_ITEM, o, "assignment");
        }
    }

    /**
     * {@code del o[key]} with Python semantics, where {@code o} may be
     * a mapping or a sequence.
     *
     * @param o object to operate on
     * @param key index at which to delete element
     * @throws TypeError when {@code o} does not allow subscripting
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PyObject_DelItem in abstract.c
    static void delItem(Object o, Object key) throws Throwable {
        // Decisions are based on types of o and key
        Operations ops = Operations.of(o);
        try {
            ops.op_delitem.invokeExact(o, key);
            return;
        } catch (EmptyException e) {
            throw typeError(DOES_NOT_SUPPORT_ITEM, o, "deletion");
        }
    }

    // Convenience functions constructing errors ----------------------

    protected static final String HAS_NO_LEN =
            "object of type '%.200s' has no len()";
    private static final String NOT_SUBSCRIPTABLE =
            "'%.200s' object is not subscriptable";
    protected static final String DOES_NOT_SUPPORT_ITEM =
            "'%.200s' object does not support item %s";

    // Classes supporting implementations of sequence types -----------

    /**
     * Sequences exhibit certain common behaviours and utilities that
     * implement them need to call back into the object by this
     * interface. This interface cannot be used as a marker for objects
     * that implement the sequence protocol because it is perfectly
     * possible for a user-defined Python type to do so without its Java
     * implementation implementing {@code PySequence.Of<T>}. A proxy
     * class could be created that holds such an object and does
     * implement {@code PySequence.Of<T>}.
     *
     * @param <T> the type of element returned by the iterators
     */
    static interface Of<T> extends Iterable<T>, Comparable<Of<T>> {

        /**
         * Provide the type of sequence object, primarily for use in
         * error messages e.g. "&lt;TYPE> index out of bounds".
         *
         * @implNote This can simply return a constant characteristic of
         *     the the implementing class, the Python type implements or
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
        // void setItem(int i, Object v);

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
         * Return a new sequence of a type determined by the
         * implementer, that is the concatenation of the target object
         * with a sequence of the same type. It is expected that the
         * returned value be a Python object.
         *
         * @param other to follow values of {@code this} in the result
         * @return the concatenation {@code this + other}
         * @throws OutOfMemoryError from allocating space for the result
         */
        Object concat(Of<T> other) throws OutOfMemoryError;

        /**
         * Return a sequence of the target type, by repeatedly
         * concatenating {@code n} copies of the present value.
         *
         * @param n number of repeats
         * @return repeating sequence
         * @throws OutOfMemoryError from allocating space for the result
         */
        Object repeat(int n) throws OutOfMemoryError;
    }

    /**
     * A specialisation of {@link Of PySequence.Of&lt;Integer>} where
     * the elements may be consumed as primitive {@code int}.
     */
    static interface OfInt extends Of<Integer> {

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

    /**
     * This is a helper class for implementations of sequence types. A
     * client sequence implementation may privately hold an instance of
     * a sub-class of {@code PySequence.Delegate} to which it delegates
     * certain operations. This sub-class could be an inner class with
     * access to private members and methods of the client.
     * <p>
     * This class declares abstract or overridable methods representing
     * elementary operations on the client sequence (to get, set or
     * delete an element or slice, or to enquire its length or type). It
     * offers methods based on these that are usable implementations of
     * the Python special methods {@code __getitem__},
     * {@code __setitem__} and {@code __delitem__}. It provides the
     * boiler-plate that tends to be the same from one Python type to
     * another &ndash; recognition that an index is a slice,
     * end-relative addressing, range checks, and the raising of
     * index-related Python exceptions. (For examples of this
     * similarity, compare CPython implementations of
     * {@code list_subscript} and {@code bytes_subscript}.)
     * <p>
     * The client must override abstract methods declared here in the
     * sub-class it defines, to specialise the behaviour of the
     * delegate. A sub-class supporting a mutable sequence type must
     * additionally override {@link #setImpl(int)},
     * {@link #setImpl(Indices)}, {@link #delImpl(int)} and
     * {@link #delImpl(Indices)}.
     *
     * @param <E> the element type, and return type of
     *     {@link #getItem(int)} etc..
     * @param <S> the slice type, and return type of
     *     {@link #getSlice(int)} etc..
     */
    /*
     * This has been adapted from Jython 2 SequenceIndexDelegate and
     * documented.
     */
    static abstract class Delegate<E, S> {

        /**
         * Returns the length of the client sequence from the
         * perspective of indexing and slicing operations.
         *
         * @return the length of the client sequence
         */
        public abstract int length();

        /**
         * Return the Python type of the client sequence being served.
         *
         * @return the Python type being served
         */
        public abstract PyType getType();

        /**
         * Return the name of the Python type of the client sequence.
         * This is used in exception messages generated here. By default
         * this is {@code getType().getName()}, which is normally
         * correct, but Python {@code str} likes to call itself
         * "string", exceptionally.
         *
         * @return the name of Python type being served
         */
        public String getTypeName() { return getType().getName(); }

        /**
         * Inner implementation of {@code __getitem__}, called by
         * {@link #__getitem__(Object)} when its argument is an integer.
         * The argument is the equivalent {@code int}, adjusted and
         * checked by {@link #adjustGet(int)}.
         *
         * @param i index of item to return
         * @return the element from the client sequence
         * @throws Throwable from errors other than indexing
         */
        public abstract E getImpl(int i) throws Throwable;

        /**
         * Inner implementation of {@code __getitem__}, called by
         * {@link #__getitem__(Object)} when its argument is a
         * {@link PySlice}. The argument is the return from
         * {@link PySlice#getIndices(int)}, which is guaranteed to be
         * range-compatible with the sequence length {@link #length()}.
         *
         * @param slice containing [start, stop, step, count] of the
         *     slice to return
         * @return the slice from the client sequence
         * @throws Throwable from errors other than indexing
         */
        public abstract S getImpl(PySlice.Indices slice)
                throws Throwable;

        /**
         * Inner implementation of {@code __setitem__}, called by
         * {@link #__setitem__(Object,Object)} when its argument is an
         * integer. The argument is the equivalent {@code int}, adjusted
         * and checked by {@link #adjustSet(int)}.
         * <p>
         * In mutable types, override this to assign a value to the
         * given element of the client sequence. The default
         * implementation (for immutable types) does nothing.
         *
         * @param i index of item to set
         * @throws Throwable from errors other than indexing
         */
        public void setImpl(int i, Object value) throws Throwable {};

        /**
         * Inner implementation of {@code __setitem__}, called by
         * {@link #__setitem__(Object,Object)} when its argument is a
         * {@link PySlice}. The argument is the return from
         * {@link PySlice#getIndices(int)}, which is guaranteed to be
         * range-compatible with the sequence length {@link #length()}.
         * <p>
         * In mutable types, override this to assign a value to the
         * given slice of the client sequence. The default
         * implementation (for immutable types) does nothing.
         *
         * @param slice to assign in the client sequence
         * @param value to assign
         * @throws Throwable from errors other than indexing
         */
        public void setImpl(PySlice.Indices slice, Object value)
                throws Throwable {};

        /**
         * Inner implementation of {@code __delitem__}, called by
         * {@link #__setitem__(Object,Object)} when its argument is an
         * integer. The argument is the equivalent {@code int}, adjusted
         * and checked by {@link #adjustSet(int)}.
         * <p>
         * The default implementation deletes a slice {@code [i:i+1]}
         * using {@link #delImpl(Indices)}.
         *
         * @param i index of item to delete
         * @throws Throwable from errors other than indexing
         */
        public void delImpl(int i) throws Throwable {
            PySlice s = new PySlice(i, i + 1, null);
            delImpl(s.new Indices(length()));
        }

        /**
         * Inner implementation of {@code __delitem__}, called by
         * {@link #__delitem__(Object)} when its argument is a
         * {@link PySlice}. The argument is the return from
         * {@link PySlice#getIndices(int)}, which is guaranteed to be
         * range-compatible with the sequence length {@link #length()}.
         * <p>
         * In mutable types, override this to delete the given slice of
         * the client sequence. The default implementation (for
         * immutable types) does nothing.
         *
         * @param slice containing [start, stop, step, count] of the
         *     slice to delete
         * @throws Throwable
         * @throws TypeError
         */
        public void delImpl(PySlice.Indices slice) throws Throwable {}

        /**
         * Implementation of {@code __getitem__}. Get either an element
         * or a slice of the client sequence, after checks, by calling
         * either {@link #getImpl(int)} or {@link #getImpl(Indices)}.
         *
         * @param item to get from in the client
         * @return the element or slice
         * @throws ValueError if {@code slice.step==0}
         * @throws TypeError from bad slice index types
         * @throws Throwable from errors other than indexing
         */
        public Object __getitem__(Object item)
                throws TypeError, Throwable {
            if (PyNumber.indexCheck(item)) {
                int i = PyNumber.asSize(item, IndexError::new);
                return getImpl(adjustGet(i));
            } else if (item instanceof PySlice) {
                Indices slice = ((PySlice)item).new Indices(length());
                return getImpl(slice);
            } else {
                throw Abstract.indexTypeError(this, item);
            }
        }

        /**
         * Implementation of {@code __setitem__}. Assign a value to
         * either an element or a slice of the client sequence, after
         * checks, by calling either {@link #setImpl(int, Object)} or
         * {@link #setImpl(Indices, Object)}.
         *
         * @param item to assign in the client
         * @param value to assign
         * @throws ValueError if {@code slice.step==0} or
         *     {@code slice.step!=1} (an "extended" slice) and
         *     {@code value} is the wrong length.
         * @throws TypeError from bad slice index types
         * @throws Throwable from errors other than indexing
         */
        public void __setitem__(Object item, Object value)
                throws TypeError, Throwable {
            if (PyNumber.indexCheck(item)) {
                int i = PyNumber.asSize(item, IndexError::new);
                setImpl(adjustSet(i), value);
            } else if (item instanceof PySlice) {
                Indices slice = ((PySlice)item).new Indices(length());
                setImpl(slice, value);
            } else {
                throw Abstract.indexTypeError(this, item);
            }
        }

        /**
         * Implementation of {@code __delitem__}. Delete either an
         * element or a slice of the client sequence, after checks, by
         * calling either {@link #delImpl(int)} or
         * {@link #delImpl(Indices, Object)}.
         *
         * @param item to assign in the client
         * @param value to assign
         * @throws ValueError if {@code slice.step==0} or value is the
         *     wrong length in an extended slice ({@code slice.step!=1}
         * @throws TypeError from bad slice index types
         * @throws Throwable from errors other than indexing
         */
        public void __delitem__(Object item)
                throws TypeError, Throwable {
            if (PyNumber.indexCheck(item)) {
                int i = PyNumber.asSize(item, IndexError::new);
                delImpl(adjustSet(i));
            } else if (item instanceof PySlice) {
                Indices slice = ((PySlice)item).new Indices(length());
                delImpl(slice);
            } else {
                throw Abstract.indexTypeError(this, item);
            }
        }

        /**
         * Check that an index {@code i} is in <i>[0,length())</i>. If
         * the original index is negative, treat it as end-relative by
         * first adding {@link #length()}.
         *
         * @param i to check is valid index
         * @return range-checked {@code i}
         * @throws IndexError if {@code i} out of range
         */
        protected int adjustGet(int i) {
            final int L = length();
            if (i < 0) {
                i += L;
                if (i >= 0) { return i; }
            } else if (i < L) { return i; }
            throw rangeIndexError("");
        }

        /**
         * Check that an index {@code i} is in <i>[0,length())</i>. If
         * the original index is negative, treat it as end-relative by
         * first adding {@link #length()}. This differs from
         * {@link #adjustGet(int)} only in that the message produced
         * mentions "assignment".
         *
         * @param i to check is valid index
         * @return range-checked {@code i}
         * @throws IndexError if {@code i} out of range
         */
        protected int adjustSet(int i) throws IndexError {
            final int L = length();
            if (i < 0) {
                i += L;
                if (i >= 0) { return i; }
            } else if (i < L) { return i; }
            throw rangeIndexError("assignment");
        }

        /**
         * Creates an {@link IndexError} with the message "TYPENAME
         * KIND index out of range", e.g. "list assignment index out of range".
         *
         * @param kind word to insert for KIND: "" or "assignment".
         * @return an exception to throw
         */
        final protected IndexError rangeIndexError(String kind) {
            String space = kind.length() > 0 ? " " : "";
            return new IndexError("%s%s%s index out of range",
                    getTypeName(), space, kind);
        }
    }
}
