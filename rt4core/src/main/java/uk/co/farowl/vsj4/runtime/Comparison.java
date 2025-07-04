// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;

import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * Selects a particular "rich comparison" operation from the repertoire
 * supported by opcode {@code COMPARE_OP}, the argument to which is the
 * {@code code} attribute of the name in this {@code enum}.
 *
 * @apiNote The order matches CPython's enumeration of operations used
 *     in the argument to {@code COMPARE_OP}, so that we can rely on it
 *     in the CPython byte code interpreter.
 */
public enum Comparison {
    // Order and number must be reflected in swap[].

    /** The {@code __lt__} operation. */
    LT("<", SpecialMethod.op_lt) {

        @Override
        boolean toBool(int c) { return c < 0; }
    },

    /** The {@code __le__} operation. */
    LE("<=", SpecialMethod.op_le) {

        @Override
        boolean toBool(int c) { return c <= 0; }
    },

    /** The {@code __eq__} operation. */
    EQ("==", SpecialMethod.op_eq) {

        @Override
        boolean toBool(int c) { return c == 0; }
    },

    /** The {@code __ne__} operation. */
    NE("!=", SpecialMethod.op_ne) {

        @Override
        boolean toBool(int c) { return c != 0; }
    },

    /** The {@code __gt__} operation. */
    GT(">", SpecialMethod.op_gt) {

        @Override
        boolean toBool(int c) { return c > 0; }
    },

    /** The {@code __ge__} operation. */
    GE(">=", SpecialMethod.op_ge) {

        @Override
        boolean toBool(int c) { return c >= 0; }
    },

    /**
     * The {@code in} operation (reflected {@code __contains__}). Note
     * that "{@code v in seq}" compiles to<pre>
     *    LOAD_NAME    0 (v)
     *    LOAD_NAME    1 (seq)
     *    CONTAINS_OP  0
     * </pre> which must lead to {@code seq.__contains__(v)}.
     */
    IN("in", SpecialMethod.op_contains) {

        @Override
        boolean toBool(int c) { return c >= 0; }

        @Override
        Object apply(Object v, Object seq) throws Throwable {
            Representation rep = TypeSystem.registry.get(seq.getClass());
            try {
                MethodHandle contains = rep.op_contains();
                return (boolean)contains.invokeExact(seq, v);
            } catch (EmptyException e) {
                throw PyErr.format(PyExc.TypeError, NOT_CONTAINER,
                        rep.pythonType(seq).getName());
            }
        }
    },

    /**
     * The inverted {@code in} operation (reflected
     * {@code __contains__}).
     */
    NOT_IN("not in", SpecialMethod.op_contains) {

        @Override
        boolean toBool(int c) { return c < 0; }

        @Override
        Object apply(Object v, Object seq) throws Throwable {
            Representation rep = TypeSystem.registry.get(seq.getClass());
            try {
                MethodHandle contains = slot.handle(rep);
                return !(boolean)contains.invokeExact(seq, v);
            } catch (EmptyException e) {
                throw PyErr.format(PyExc.TypeError, NOT_CONTAINER,
                        rep.pythonType(seq).getName());
            }
        }
    },

    /** The identity operation. */
    IS("is") {

        @Override
        boolean toBool(int c) { return c == 0; }

        @Override
        Object apply(Object v, Object w) throws Throwable {
            return v == w;
        }

    },

    /** The inverted identity operation. */
    IS_NOT("is not") {

        @Override
        boolean toBool(int c) { return c != 0; }

        @Override
        Object apply(Object v, Object w) throws Throwable {
            return v != w;
        }
    },

    /** The exception matching operation. */
    EXC_MATCH("matches") {

        @Override
        boolean toBool(int c) { return c == 0; }

        @Override
        Object apply(Object v, Object w) throws Throwable {
            return Py.NotImplemented; // XXX implement me!
        }
    },

    /** A dummy operation representing an invalid comparison. */
    BAD("?") {

        @Override
        boolean toBool(int c) { return false; }

        @Override
        Object apply(Object v, Object w) throws Throwable {
            return Py.NotImplemented;
        }
    };

    final String text;
    final SpecialMethod slot;

    Comparison(String text, SpecialMethod slot) {
        this.text = text;
        this.slot = slot;
    }

    Comparison(String text) { this(text, null); }

    /**
     * The text corresponding to the value, e.g. "!=" for {@code NE},
     * "is not" for {@code IS_NOT}. Mostly for error messages.
     *
     * @return text corresponding
     */
    @Override
    public String toString() { return text; }

    /**
     * Translate CPython {@link Opcode311#COMPARE_OP} opcode argument to
     * Comparison constant.
     *
     * @param oparg opcode argument
     * @return equivalent {@code Comparison} object
     */
    static Comparison from(int oparg) {
        return oparg >= 0 && oparg < from.length ? from[oparg] : BAD;
    }

    private static final Comparison[] from = values();

    /**
     * The swapped version of this comparison, e.g. LT with GT.
     *
     * @return swapped version of this comparison
     */
    Comparison swapped() { return swap[this.ordinal()]; }

    private static final Comparison[] swap =
            {GT, GE, EQ, NE, LT, LE, BAD, BAD, IS, IS_NOT, BAD, BAD};

    /**
     * Translate a comparison result into the appropriate boolean, for
     * example {@code GE.toBool(1)} is {@link Py#True}. For the the six
     * operations LT to GE inclusive, this is typically wrapped onto a
     * call to {@code Comparable.compareTo()}). For the others we assume
     * c==0 indicates equality.
     * <p>
     * Avoid the temptation to use the result of a subtraction here
     * unless there is no possibility of overflow in the subtraction.
     *
     * @param c comparison result
     * @return boolean equivalent for this operation
     */
    // Compare CPython object.h::Py_RETURN_RICHCOMPARE
    abstract boolean toBool(int c);

    /**
     * Perform this comparison, raising {@code TypeError} when the
     * requested comparison operator is not supported.
     *
     * @param v left operand
     * @param w right operand
     * @return comparison result
     * @throws Throwable from the implementation of the comparison
     */
    // Compare CPython PyObject_RichCompare, do_richcompare in object.c
    Object apply(Object v, Object w) throws Throwable {
        Representation vRep = TypeSystem.registry.get(v.getClass());
        PyType vType = vRep.pythonType(v);
        Representation wRep = TypeSystem.registry.get(w.getClass());
        PyType wType = wRep.pythonType(w);
        SpecialMethod swappedSlot = null;

        // Try the swapped operation first if w is a sub-type of v

        if (vType != wType && wType.isSubTypeOf(vType)) {
            swappedSlot = swapped().slot;
            try {
                Object r = swappedSlot.handle(wRep).invokeExact(w, v);
                if (r != Py.NotImplemented) { return r; }
            } catch (EmptyException e) {}
        }

        // Try the forward operation
        try {
            Object r = slot.handle(vRep).invokeExact(v, w);
            if (r != Py.NotImplemented) { return r; }
        } catch (EmptyException e) {}

        // Try the swapped operation if we haven't already
        if (swappedSlot == null) {
            swappedSlot = swapped().slot;
            try {
                Object r = swappedSlot.handle(wRep).invokeExact(w, v);
                if (r != Py.NotImplemented) { return r; }
            } catch (EmptyException e) {}
        }

        // Neither object implements this. Base == and != on identity.
        switch (this) {
            case EQ:
                return v == w;
            case NE:
                return v != w;
            default:
                throw comparisonTypeError(v, w);
        }
    }

    /**
     * Create a {@link PyBaseException TypeError} along the lines "OP
     * not supported between instances of V and W"
     *
     * @param v left arg
     * @param w right arg
     * @return the exception
     */
    PyBaseException comparisonTypeError(Object v, Object w) {
        return PyErr.format(PyExc.TypeError, NOT_SUPPORTED, this,
                PyType.of(v).getName(), PyType.of(w).getName());
    }

    private static String NOT_SUPPORTED =
            "'%s' not supported between instances of '%.100s' and '%.100s'";
    private static String NOT_CONTAINER =
            "'%.200s' object is not a container";
}
