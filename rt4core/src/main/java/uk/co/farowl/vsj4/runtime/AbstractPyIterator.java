package uk.co.farowl.vsj4.runtime;

/** Abstract base class for defining Python iterators. */
abstract class AbstractPyIterator extends TypedPyObject {

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
     * {@code in} statements. It has to be defined in the Python
     * implementation class to get exposed to Python.
     *
     * @return this iterator
     */
    abstract Object __iter__();

    /**
     * Get the next item from the iteration. Each concrete sub-class
     * must implement the special function {@code __next__} in its own
     * way.
     *
     * @implNote Exhaustion of the iterator is signalled by an exception
     *     as is standard for Python objects. This exception does not
     *     need to carry any context, since it will be caught by the
     *     surrounding loop, in an idiom like:<pre>
     * try {
     *     for (;;) { list.add(next.invokeExact()); }
     * } catch (PyStopIteration si) {}
     * </pre>
     * @return the next object
     * @throws PyBaseException (StopIteration) signifying no more items
     * @throws Throwable from implementation
     */
    abstract Object __next__() throws PyBaseException, Throwable;
}
