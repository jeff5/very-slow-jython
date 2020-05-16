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
             * In CPython, there are specific cases here that look for
             * support for vector call and PyCFunction (would be
             * PyJavaFunction) leading to PyVectorcall_Call or
             * cfunction_call_varargs respectively on the args, kwargs
             * arguments.
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

    /**
     * Call an object with argument with vector call protocol. This
     * supports CPython byte code generated according to the conventions
     * in PEP-590. It differs in detail since one cannot designate, in
     * Java, a slice of the stack by an address and size.
     *
     * @param callable target
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments
     * @param kwnames names of keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    static PyObject vectorcall(PyObject callable, PyObject[] stack,
            int start, int nargs, PyTuple kwnames) throws Throwable {

        // Try the vector call slot. Not an offset like CPython's slot.
        try {
            MethodHandle call = callable.getType().tp_vectorcall;
            return (PyObject) call.invokeExact(callable, stack, start,
                    nargs, kwnames);
        } catch (Slot.EmptyException e) {}

        // Vector call is not supported by the type. Make classic call.
        PyTuple args = new PyTuple(stack, start, nargs);
        PyDict kwargs = null;
        if (kwnames != null) {
            // We do not check for PyUnicode because receiver will.
            kwargs = Py.dict();
            PyObject[] names = kwnames.value;
            for (int i = 0, j = start + nargs; i < names.length; i++)
                kwargs.put(names[j++], stack[i]);
        }
        return call(callable, args, kwargs);
    }

}
