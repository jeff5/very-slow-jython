package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.util.Map;
import java.util.Map.Entry;

/** Compare CPython {@code Objects/call.c}: {@code Py_Object_*}. */
class Callables extends Abstract {

    private Callables() {} // only static methods here

    /**
     * Call an object with the classic call protocol, that is, with an
     * argument tuple and keyword dictionary.
     *
     * @param callable target
     * @param args positional arguments
     * @param kwargs keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    // Note that CPython allows only exactly tuple and dict.
    static Object call(Object callable, PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {

        // Speed up the idiom common in called objects:
        // if (kwargs == null || kwargs.isEmpty()) ...
        if (kwargs != null && kwargs.isEmpty()) { kwargs = null; }

        try {
            /*
             * In CPython, there are specific cases here that look for
             * support for vector call and PyCFunction (would be
             * PyJavaFunction) leading to PyVectorcall_Call or
             * cfunction_call_varargs respectively on the args, kwargs
             * arguments.
             */
            MethodHandle call = Operations.of(callable).op_call;
            return call.invokeExact(callable, args, kwargs);
        } catch (Slot.EmptyException e) {
            throw typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    /**
     * Call an object with the classic call protocol as supported in the
     * interpreter {@code CALL_FUNCTION_EX} opcode, that is, an argument
     * tuple (or iterable) and keyword dictionary (or iterable of
     * key-value pairs).
     *
     * @param callable target
     * @param args positional arguments
     * @param kwargs keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    // Note that CPython allows only exactly tuple and dict. (Why?)
    static Object callEx(Object callable, Object args, Object kwargs)
            throws TypeError, Throwable {

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

        return call(callable, ar, kw);
    }

    static final String OBJECT_NOT_CALLABLE =
            "'%.200s' object is not callable";
    static final String OBJECT_NOT_VECTORCALLABLE =
            "'%.200s' object does not support vectorcall";
    static final String ATTR_NOT_CALLABLE =
            "attribute of type '%.200s' is not callable";

    /**
     * Convert classic call arguments to an array and names of keywords
     * to use in the CPython-style vector call.
     *
     * @param args positional arguments
     * @param kwargs keyword arguments (normally {@code PyDict})
     * @param stack to receive positional and keyword arguments, must be
     *     sized {@code args.length + kwargs.size()}.
     * @return names of keyword arguments
     */
    // Compare CPython _PyStack_UnpackDict in call.c
    static PyTuple unpackDict(Object[] args, Map<Object, Object> kwargs,
            Object[] stack) throws ArrayIndexOutOfBoundsException {
        int nargs = args.length;
        assert (kwargs != null);
        assert (stack.length == nargs + kwargs.size());

        System.arraycopy(args, 0, stack, 0, nargs);

        Object[] kwnames = new Object[kwargs.size()];
        int i = 0, j = nargs;
        for (Entry<Object, Object> e : kwargs.entrySet()) {
            kwnames[i++] = e.getKey();
            stack[j++] = e.getValue();
        }

        return PyTuple.wrap(kwnames);
    }

    /**
     * Call an object with the vector call protocol. This supports
     * CPython byte code generated according to the conventions in
     * PEP-590. It differs in detail since one cannot designate, in
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
    // Compare CPython _PyObject_FastCall in call.c
    static Object vectorcall(Object callable, Object[] stack, int start,
            int nargs, PyTuple kwnames) throws Throwable {

        // Try the vector call slot. Not an offset like CPython's slot.
        try {
            MethodHandle call = Operations.of(callable).op_vectorcall;
            return call.invokeExact(callable, stack, start, nargs,
                    kwnames);
        } catch (Slot.EmptyException e) {}

        // Vector call is not supported by the type. Make classic call.
        PyTuple args = new PyTuple(stack, start, nargs);
        PyDict kwargs = stackAsDict(stack, start, nargs, kwnames);
        return call(callable, args, kwargs);
    }

    /**
     * Return a dictionary containing the last {@code len(kwnames)}
     * elements of the slice {@code stack[start:start+nargs]}. This is a
     * helper method to convert CPython vector calls (calls from a slice
     * of an array, usually the stack) and involving keywords.
     * {@code kwnames} normally contains only {@code str} objects, but
     * that is not enforced here.
     *
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional arguments
     * @param kwnames tuple of names (may be {@code null} if empty)
     * @return dictionary or {@code null} if {@code kwnames==null}
     */
    // Compare CPython _PyStack_AsDict in call.c
    static PyDict stackAsDict(Object[] stack, int start, int nargs,
            PyTuple kwnames) {
        PyDict kwargs = null;
        if (kwnames != null) {
            kwargs = Py.dict();
            Object[] names = kwnames.value;
            for (int i = 0, j = start + nargs; i < names.length; i++)
                kwargs.put(names[i], stack[j++]);
        }
        return kwargs;
    }

    /**
     * Call an object with positional arguments supplied from Java as
     * {@code Object}s.
     *
     * @param callable target
     * @param args positional arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_CallFunctionObjArgs in call.c
    static Object callFunction(Object callable, Object... args)
            throws Throwable {
        try {
            // A vector call with no keywords (if supported).
            MethodHandle call = Operations.of(callable).op_vectorcall;
            return call.invokeExact(callable, args, 0, args.length,
                    PyTuple.EMPTY);
        } catch (Slot.EmptyException e) {}

        // Vector call is not supported by the type. Make classic call.
        return call(callable, new PyTuple(args), null);
    }

    /**
     * Call an object with no arguments.
     *
     * @param callable target
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_CallNoArg in abstract.h
    // and _PyObject_Vectorcall in abstract.h
    static Object call(Object callable) throws Throwable {
        // Try the vector call slot. Not an offset like CPython's slot.
        try {
            MethodHandle call = Operations.of(callable).op_vectorcall;
            return call.invokeExact(callable, Py.EMPTY_ARRAY, 0, 0,
                    PyTuple.EMPTY);
        } catch (Slot.EmptyException e) {}

        // Vector call is not supported by the type. Make classic call.
        return call(callable, PyTuple.EMPTY, null);
    }

    /**
     * Resolve a name within an object and then call it with the given
     * positional arguments supplied from Java.
     *
     * @param obj target of the method invocation
     * @param name identifying the method
     * @param args positional arguments
     * @return result of call
     * @throws Throwable
     * @throws AttributeError
     */
    // Compare CPython _PyObject_CallMethodIdObjArgs in call.c
    static Object callMethod(Object obj, String name, Object... args)
            throws AttributeError, Throwable {
        /*
         * CPython has an optimisation here that, in the case of an
         * instance method, avoids creating a bound method by returning
         * the unbound method and a flag that indicates that obj should
         * be treated as the first argument of the call. The following
         * code returns a bound method which is correct though it may be
         * slower.
         */
        Object callable = getAttr(obj, name);
        return callFunction(callable, args);
    }

}
