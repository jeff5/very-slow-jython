package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;
import java.util.Map;
import java.util.Map.Entry;

/** Compare CPython {@code Objects/call.c}: {@code Py_Object_*}. */
class Callables extends Abstract {

    private Callables() {} // only static methods here

    // XXX Could this be (String[]) null with advantages?
    private static final String[] NO_KEYWORDS = new String[0];

    /**
     * Call an object with the standard {@code __call__} protocol, that
     * is, with an argument array and keyword name array.
     *
     * @param callable target
     * @param args all the arguments (position then keyword)
     * @param names of the keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    // Note that CPython allows only exactly tuple and dict.
    static Object call(Object callable, Object[] args, String[] names)
            throws TypeError, Throwable {

        // Speed up the common idiom:
        // if (names == null || names.length == 0) ...
        if (names != null && names.length == 0) { names = null; }

        if (callable instanceof FastCall) {
            // Take the direct route since __call__ is immutable
            FastCall fast = (FastCall)callable;
            if (args == null || args.length == 0) {
                return fast.call();
            } else if (names == null) {
                return fast.call(args);
            } else {
                return fast.__call__(args, names);
            }
        }

        try {
            // Call via the special method (slot function)
            MethodHandle call = Operations.of(callable).op_call;
            return call.invokeExact(callable, args, names);
        } catch (Slot.EmptyException e) {
            throw typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    /**
     * Call an object with the classic CPython call protocol, that is,
     * with an argument tuple and keyword dictionary.
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
     * Call an object with the CPython call protocol as supported in the
     * interpreter {@code CALL_FUNCTION_EX} opcode, that is, an argument
     * tuple (or iterable) and keyword dictionary (or iterable of
     * key-value pairs), which may be built by code at the opcode site.
     *
     * @param callable target
     * @param args positional arguments
     * @param kwargs keyword arguments
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    /*
     * Note that CPython allows only exactly tuple and dict. (It deals
     * with iterables in line ov the opcode omplementation.)
     */
    static Object callEx(Object callable, Object args, Object kwargs)
            throws TypeError, Throwable {

        // Represent kwargs as a dict (if not already or null)
        PyDict kw;
        if (kwargs == null || kwargs instanceof PyDict)
            kw = (PyDict)kwargs;
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
            ar = (PyTuple)args;
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

        PyTuple.Builder kwnames = new PyTuple.Builder(kwargs.size());
        int j = nargs;
        for (Entry<Object, Object> e : kwargs.entrySet()) {
            kwnames.append(e.getKey());
            stack[j++] = e.getValue();
        }

        return kwnames.take();
    }

    /**
     * Call an object with the vector call protocol. This supports
     * CPython byte code generated according to the conventions in
     * PEP-590. Unlike its use in CPython, this is <b>not</b> faster
     * than the standard {@link #call(Object, Object[], String[]) call}
     * method.
     * <p>
     * The {@code stack} argument (which is often the interpreter stack)
     * contains, at a given offset {@code start}, the {@code npos}
     * arguments given at given by position, followed by those
     * {@code len(kwnames)} given by keyword.
     *
     * @param callable target
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param npos number of <b>positional</b> arguments
     * @param kwnames names of keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws TypeError if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_FastCall in call.c
    static Object vectorcall(Object callable, Object[] stack, int start,
            int npos, PyTuple kwnames) throws Throwable {
        int n = kwnames == null ? 0 : kwnames.size();
        Object[] args =
                Arrays.copyOfRange(stack, start, start + npos + n);
        if (n == 0) {
            // Positional arguments only
            return call(callable, args, null);
        } else {
            // We cannot guarantee that kwnames is a tuple of strings
            String[] kw = new String[n];
            Object[] names = kwnames.value;
            for (int i = 0; i < n; i++) { kw[i] = names[i].toString(); }
            return call(callable, args, kw);
        }
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
     * @param nargs number of <b>positional</b> arguments
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
        return call(callable, args, NO_KEYWORDS);
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
        if (callable instanceof FastCall) {
            // Take the short-cut.
            return ((FastCall)callable).call();
        }
        // Fast call is not supported by the type. Make standard call.
        return call(callable, Py.EMPTY_ARRAY, NO_KEYWORDS);
    }

    /**
     * Resolve a name within an object and then call it with the given
     * positional arguments supplied from Java.
     *
     * @param obj target of the method invocation
     * @param name identifying the method
     * @param args positional arguments
     * @return result of call
     * @throws AttributeError if the named callable cannot be found
     * @throws Throwable from the called method
     */
    // Compare CPython _PyObject_CallMethodIdObjArgs in call.c
    static Object callMethod(Object obj, String name, Object... args)
            throws AttributeError, Throwable {
        Object callable = getAttr(obj, name);
        return callFunction(callable, args);
    }

}
