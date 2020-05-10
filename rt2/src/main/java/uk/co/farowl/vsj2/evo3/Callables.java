package uk.co.farowl.vsj2.evo3;

import java.lang.invoke.MethodHandle;

/** Compare CPython {@code Objects/call.c}: {@code Py_Object_*}. */
class Callables extends Abstract {

    /**
     * Call an object with argument tuple (or iterable) and keyword
     * dictionary (or iterable of key-value pairs).
     *
     * @param callable target
     * @param args positional arguments
     * @param kwargs keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _/Objects/call.c PyObject_Call
    // Note that CPython allows only exactly tuple and dict. (Why?)
    static PyObject call(PyObject callable, PyObject args,
            PyObject kwargs) throws TypeError, Throwable {

        // Represent kwargs as a dict (if not already or null)
        PyDict kw;
        if (kwargs == null || kwargs instanceof PyDict)
            kw = (PyDict) kwargs;
        else {
            // TODO: Treat kwargs as an iterable of (key,value) pairs
            // Throw TypeError if not convertible
            kw = Py.dict();
            // Check kwargs iterable, and correctly typed
            // kwDict.update(Mapping.items(kwargs));
        }

        // Represent args as a PyTuple (if not already)
        PyTuple ar;
        if (args instanceof PyTuple)
            ar = (PyTuple) args;
        else {
            // TODO: Treat args as an iterable of objects
            // Throw TypeError if not convertible
            ar = Py.tuple();
            // Construct PyTuple with whatever checks on values
            // argTuple = Sequence.tuple(args);
        }

        try {
            /*
             * In CPython, there are specific cases here that look for support for
             * vector call and PyCFunction (would be PyJavaFunction) leading to
             * PyVectorcall_Call or cfunction_call_varargs respectively
             * on the args, kwargs arguments.
             */
            MethodHandle call = callable.getType().tp_call;
            return (PyObject) call.invokeExact(callable, ar, kw);
        } catch (Slot.EmptyException e) {
            throw typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    static final String OBJECT_NOT_CALLABLE =
            "'%.200s' object is not callable";
    static final String OBJECT_NOT_VECTORCALLABLE =
            "'%.200s' object does not support vectorcall";
    static final String ATTR_NOT_CALLABLE =
            "attribute of type '%.200s' is not callable";

    static PyObject vectorcall(PyObject callable, PyObject[] args,
            int start, int nargsf, PyTuple kwnames) {
        // TODO: Stub to satisfy eval()
        return Py.None;
    }

}
