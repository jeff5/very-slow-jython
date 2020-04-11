package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code Objects/call.c}: {@code Py_Mapping_*}. */
class Callables extends Abstract {

    /**
     * Call a callable object with argument tuple and keyword
     * dictionary
     *
     * @param callable target
     * @param args positional arguments ({@code tuple}
     * @param kwargs keyword arguments {@code dict}
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _/Objects/call.c PyObject_Call
    static PyObject call(PyObject callable, PyObject args,
            PyObject kwargs) throws TypeError, Throwable {
        try {
            MethodHandle call = callable.getType().tp_call;
            return (PyObject) call.invokeExact(callable, args, kwargs);
        } catch (Slot.EmptyException e) {
            throw typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    static final String OBJECT_NOT_CALLABLE =
            "'%.200s' object is not callable";
    static final String ATTR_NOT_CALLABLE =
            "attribute of type '%.200s' is not callable";
}
