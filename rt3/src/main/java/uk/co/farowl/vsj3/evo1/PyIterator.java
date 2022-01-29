// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

/**
 * The Python iterator type provides iteration over any Python
 * <em>sequence</em>, relying only on {@code __getitem__} accepting an
 * integer index.
 * <p>
 * Note that (in CPython) well-known built-in sequence types define
 * specialised iterator types, leaving this type mostly as an iterator
 * on user-defined sequence types (with {@code __getitem__} but not
 * {@code __iter__}).
 */
public class PyIterator extends AbstractPyIterator {
    /** The type {@code iterator}. */
    static final PyType TYPE = PyType.fromSpec(
            new PyType.Spec("iterator", MethodHandles.lookup()));

    /** Index of the next item to return. -1 if exhausted. */
    private int index;

    /**
     * Method handle (bound to the sequence) that will retrieve an
     * element. Signature is {@code O(O)}.
     */
    private MethodHandle getitem;

    /**
     * Construct an instance of {@code PyIterator}, a Python
     * {@code iterator} or a sub-class, from a given Python sequence
     * (defining {@code __getitem__}).
     *
     * @param type actual type the instance should have
     * @param seq on which this is an iterator
     */
    public PyIterator(PyType type, Object seq) {
        super(type);
        this.index = 0;
        Operations ops = Operations.of(seq);
        if (Slot.op_getitem.isDefinedFor(ops)) {
            this.getitem = ops.op_getitem.bindTo(seq);
        } else {
            throw new IllegalArgumentException(
                    Slot.op_getitem.methodName);
        }
    }

    /**
     * Construct an instance of {@code PyIterator}, a Python
     * {@code iterator}, from a given Python sequence (defining
     * {@code __getitem__}).
     *
     * @param seq on which this is an iterator
     */
    public PyIterator(Object seq) { this(TYPE, seq); }

    @Override
    Object __next__() throws Throwable {
        try {
            if (index >= 0) {
                return getitem.invokeExact((Object)index++);
            }
        } catch (IndexError | StopIteration e) {
            // Signal in index that we reached the end
            index = -1;
        }
        throw PyObjectUtil.STOP_ITERATION;
    }
}
