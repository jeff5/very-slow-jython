package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code abstract.h}: {@code Py_Mapping_*}. */
class Mapping extends Abstract {

    /** Python size of {@code o} */
    static int size(PyObject o) throws Throwable {
        // Note that the slot is called sq_length but this method, size.
        PyType oType = o.getType();

        try {
            MethodHandle mh = oType.mp_length;
            return (int) mh.invokeExact(o);
        } catch (Slot.EmptyException e) {}

        if (Slot.mp_length.isDefinedFor(oType)) // XXX sq_ or mp_?
            // Caller should have tried Abstract.size
            throw typeError(NOT_MAPPING, o);
        throw typeError(HAS_NO_LEN, o);
    }

    private static final String NOT_MAPPING = "%.200s is not a mapping";
}
