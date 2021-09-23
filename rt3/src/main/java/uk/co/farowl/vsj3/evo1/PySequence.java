package uk.co.farowl.vsj3.evo1;

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
     * @return {@code o*count}
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PySequence_Repeat in abstract.c
    public static Object repeat(Object o, int count) throws Throwable {
        // There is no equivalent slot to sq_repeat
        return PyNumber.multiply(o, count);
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

    // Convenience functions constructing errors --------------------

    protected static final String HAS_NO_LEN =
            "object of type '%.200s' has no len()";
    private static final String NOT_SUBSCRIPTABLE =
            "'%.200s' object is not subscriptable";
    protected static final String DOES_NOT_SUPPORT_ITEM =
            "'%.200s' object does not support item %s";
}
