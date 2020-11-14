package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code abstract.h}: {@code Py_Mapping_*}. */
class Mapping extends Abstract {

    /**
     * Python size of {@code o}, a mapping.
     *
     * @param o to operate on
     * @return derived size
     * @throws Throwable from invoked method implementations
     */
    static PyObject size(PyObject o) throws Throwable {
        // Note that the slot is called length but this method, size.
        PyType oType = o.getType();

        try {
            MethodHandle mh = oType.mapping.length;
            return (PyObject) mh.invokeExact(o);
        } catch (Slot.EmptyException e) {}

        if (Slot.MP.length.isDefinedFor(oType))
            // Caller should have tried Abstract.size
            throw typeError(NOT_MAPPING, o);
        throw typeError(HAS_NO_LEN, o);
    }

    private static final String NOT_MAPPING = "%.200s is not a mapping";
}
