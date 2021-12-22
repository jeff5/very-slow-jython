package uk.co.farowl.vsj3.evo1;

import java.util.Iterator;

/** Abstract base class for defining Python iterators. */
abstract class AbstractPyIterator extends AbstractPyObject {

    /**
     * Construct an instance of {@code PyIterator}, a Python
     * {@code iterator} or a sub-class, from a given Python sequence
     * (defining {@code __getitem__}).
     *
     * @param type actual type the instance should have
     */
    protected AbstractPyIterator(PyType type) { super(type); }

    /**
     * Get the iterator itself. A Python iterator {@code __iter__} is
     * required to return its {@code self}. This is required to allow
     * both containers and iterators to be used with the {@code for} and
     * {@code in} statements.
     *
     * @return this iterator
     */
    Object __iter__() { return this; }

    /**
     * Get the next item from the iteration. Each concrete sub-class
     * must implement the special function {@code __next__} in its own
     * way.
     *
     * @return the next object or {@code null} signifying stop
     * @throws Throwable from implementation
     */
    abstract Object __next__() throws Throwable;
}
