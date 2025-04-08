// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;

import uk.co.farowl.vsj4.runtime.kernel.KernelTypeFlag;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;

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
    static final PyType TYPE = PyType
            .fromSpec(new TypeSpec("iterator", MethodHandles.lookup()));

    /** Index of the next item to return. -1 if exhausted. */
    private int index;

    /**
     * Method handle (bound to the sequence) that will retrieve an
     * element. Signature is {@code O(O)}.
     */
    private MethodHandle getitem;

    /**
     * Construct an instance of {@code PyIterator}, a Python
     * {@code iterator}, from a given Python sequence (defining
     * {@code __getitem__}).
     *
     * @param seq on which this is an iterator
     */
    public PyIterator(Object seq) {
        super(TYPE);
        this.index = 0;
        Representation rep = PyType.getRepresentation(seq);
        if (rep.hasFeature(seq, KernelTypeFlag.HAS_GETITEM)) {
            this.getitem = rep.op_getitem().bindTo(seq);
        } else {
            throw new IllegalArgumentException(
                    SpecialMethod.op_getitem.methodName);
        }
    }

    @Override
    Object __next__() throws Throwable {
        try {
            if (index >= 0) {
                return getitem.invokeExact((Object)index++);
            }
        } catch (PyBaseException e) {
            e.only(PyExc.IndexError, PyExc.StopIteration);
            // Signal in index that we reached the end
            index = -1;
        }
        throw new PyStopIteration();
    }

    @Override
    Object __iter__() { return this; }
}
