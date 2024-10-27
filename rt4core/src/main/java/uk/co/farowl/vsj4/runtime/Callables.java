// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.util.Arrays;
import java.util.Map;

import uk.co.farowl.vsj4.support.internal.EmptyException;
import uk.co.farowl.vsj4.support.internal.Util;

/** Support for the {@code __call__} protocol of Python objects. */
// Compare CPython ~/Objects/call.c
class Callables extends Abstract {

    private Callables() {} // only static methods here

    private static final Object[] NO_ARGS = Util.EMPTY_ARRAY;
    // XXX Could this be (String[]) null with advantages?
    private static final String[] NO_KEYWORDS = Util.EMPTY_STRING_ARRAY;

    /**
     * Call an object with the standard {@code __call__} protocol, that
     * is, with an array of all the arguments, those given by position,
     * then those given by keyword, and an array of the keywords in the
     * same order. Therefore {@code np = args.length - names.length}
     * arguments are given by position, and the keyword arguments are
     * {@code args[np:]} named by {@code names[:]}.
     *
     * @param callable target
     * @param args all the arguments (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    // Note that CPython allows only exactly tuple and dict.
    static Object call(Object callable, Object[] args, String[] names)
            throws PyBaseException, Throwable {

        // Speed up the common idiom:
        // if (names == null || names.length == 0) ...
        if (names != null && names.length == 0) { names = null; }

        if (callable instanceof FastCall) {
            // Take the direct route since __call__ is immutable
            FastCall fast = (FastCall)callable;
            try {
                return fast.call(args, names);
            } catch (ArgumentError ae) {
                // Demand a proper TypeError.
                throw fast.typeError(ae, args, names);
            }
        }

        try {
            // Call via the special method
            // XXX Stop gap code until support for special functions
            // Object o = PyType.of(callable).lookup("__call__");
            throw new EmptyException();
        } catch (EmptyException e) {
            throw typeError(OBJECT_NOT_CALLABLE, callable);
        }
    }

    /**
     * Call an object with the standard {@code __call__} protocol, but
     * where the first argument is "loose", and must be prepended to the
     * usual array of arguments. This is potentially useful in calling
     * an instance method.
     *
     * @param callable target
     * @param arg0 the first argument
     * @param args arguments from 1 (position then keyword)
     * @param names of the keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Call_Prepend in call.c
    // Note that CPython allows only exactly tuple and dict.
    static Object call(Object callable, Object arg0, Object[] args,
            String[] names) throws PyBaseException, Throwable {
        // Note names are end-relative, so remain valid after the shift.
        Object[] allargs = new Object[args.length + 1];
        allargs[0] = arg0;
        System.arraycopy(args, 0, allargs, 1, args.length);
        return Callables.call(callable, allargs, names);
    }

    /**
     * Call an object with the classic CPython call protocol, that is,
     * with a tuple of arguments given by position and a dictionary of
     * key-value pairs providing arguments given by keyword.
     *
     * @param callable target
     * @param argTuple positional arguments
     * @param kwDict keyword arguments
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    static Object call(Object callable, PyTuple argTuple, PyDict kwDict)
            throws PyBaseException, Throwable {

        Object[] args;
        String[] kwnames;

        if (kwDict == null || kwDict.isEmpty()) {
            args = argTuple.toArray();
            kwnames = null;

        } else {
            // Convert to arrays of arguments and names
            int n = argTuple.size(), m = kwDict.size(), i = 0;
            args = argTuple.toArray(new Object[n + m]);
            kwnames = new String[m];
            for (Map.Entry<Object, Object> e : kwDict.entrySet()) {
                Object name = e.getKey();
                kwnames[i++] = PyUnicode.asString(name,
                        Callables::keywordTypeError);
                args[n++] = e.getValue();
            }
        }

        return call(callable, args, kwnames);
    }

    /**
     * Call an object with the classic positional-only CPython call
     * protocol, that is, with a tuple of arguments given by position
     * and no dictionary of key-value pairs.
     *
     * @param callable target
     * @param argTuple positional arguments
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    static Object call(Object callable, PyTuple argTuple)
            throws PyBaseException, Throwable {
        return call(callable, argTuple.toArray(), null);
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
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython PyObject_Call in call.c
    /*
     * Note that CPython allows only exactly tuple and dict. (It deals
     * with iterables within the opcode implementation.)
     */
    static Object callEx(Object callable, Object args, Object kwargs)
            throws PyBaseException, Throwable {

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
            ar = PyTuple.EMPTY;
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
    // static PyTuple unpackDict(Object[] args, Map<Object, Object>
    // kwargs,
    // Object[] stack) throws ArrayIndexOutOfBoundsException {
    // int nargs = args.length;
    // assert (kwargs != null);
    // assert (stack.length == nargs + kwargs.size());
    //
    // System.arraycopy(args, 0, stack, 0, nargs);
    //
    // PyTuple.Builder kwnames = new PyTuple.Builder(kwargs.size());
    // int j = nargs;
    // for (Entry<Object, Object> e : kwargs.entrySet()) {
    // kwnames.append(e.getKey());
    // stack[j++] = e.getValue();
    // }
    //
    // return kwnames.take();
    // }

    /**
     * Call an object with the vector call protocol with some arguments
     * given by keyword. This supports CPython byte code generated
     * according to the conventions in PEP-590. Unlike its use in
     * CPython, this is <b>not likely to be faster</b> than the standard
     * {@link #call(Object, Object[], String[]) call} method.
     *
     * @see FastCall#vectorcall(Object[], int, int, String[])
     *
     * @param callable target
     * @param stack positional and keyword arguments
     * @param start position of arguments in the array
     * @param nargs number of positional <b>and keyword</b> arguments
     * @param kwnames names of keyword arguments or {@code null}
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Vectorcall in abstract.h
    // In CPython nargs counts only positional arguments
    static Object vectorcall(Object callable, Object[] stack, int start,
            int nargs, PyTuple kwnames) throws Throwable {
        String[] names = Callables.namesArray(kwnames);
        if (callable instanceof FastCall) {
            // Fast path recognising optimised callable
            FastCall fast = (FastCall)callable;
            try {
                return fast.vectorcall(stack, start, nargs, names);
            } catch (ArgumentError ae) {
                // Demand a proper TypeError.
                throw fast.typeError(ae, stack, start, nargs, names);
            }
        }
        // Slow path by converting stack to ephemeral array
        Object[] args = Arrays.copyOfRange(stack, start, start + nargs);
        return call(callable, args, names);
    }

    /**
     * Call an object with the vector call protocol with no arguments
     * given by keyword. This supports CPython byte code generated
     * according to the conventions in PEP-590. Unlike its use in
     * CPython, this is <b>not likely to be faster</b> than the standard
     * {@link #call(Object, Object[], String[]) call} method.
     *
     * @see FastCall#vectorcall(Object[], int, int)
     *
     * @param callable target
     * @param stack positional and keyword arguments (the stack)
     * @param start position of arguments in the array
     * @param nargs number of positional <b>and keyword</b> arguments
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_Vectorcall in abstract.h
    // In CPython nargs counts only positional arguments
    static Object vectorcall(Object callable, Object[] stack, int start,
            int nargs) throws PyBaseException, Throwable {
        if (callable instanceof FastCall) {
            // Fast path recognising optimised callable
            FastCall fast = (FastCall)callable;
            try {
                return fast.vectorcall(stack, start, nargs);
            } catch (ArgumentError ae) {
                // Demand a proper TypeError.
                throw fast.typeError(ae, stack, start, nargs);
            }
        }
        // Slow path by converting stack to ephemeral array
        Object[] args = Arrays.copyOfRange(stack, start, start + nargs);
        return call(callable, args, NO_KEYWORDS);
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
            int j = start + nargs;
            for (Object name : kwnames)
                kwargs.put(name, stack[j++]);
        }
        return kwargs;
    }

    /**
     * Call an object with positional arguments supplied from Java as
     * {@code Object}s. This is equivalent to
     * {@code call(callable, args, NO_KEYWORDS)}. The name differs from
     * {@link #call(Object, Object[], String[]) call} only to separate
     * the call signatures.
     *
     * @param callable target
     * @param args positional arguments
     * @return the return from the call to the object
     * @throws PyBaseException(TypeError) if target is not callable
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
     * @throws PyBaseException(TypeError) if target is not callable
     * @throws Throwable for errors raised in the function
     */
    // Compare CPython _PyObject_CallNoArg in abstract.h
    // and _PyObject_Vectorcall in abstract.h
    static Object call(Object callable) throws Throwable {
        if (callable instanceof FastCall) {
            // Take the short-cut.
            FastCall fast = (FastCall)callable;
            try {
                return fast.call();
            } catch (ArgumentError ae) {
                // Demand a proper TypeError.
                throw fast.typeError(ae, NO_ARGS);
            }
        }
        // Fast call is not supported by the type. Make standard call.
        return call(callable, NO_ARGS, NO_KEYWORDS);
    }

    /**
     * Resolve a name within an object and then call it with the given
     * positional arguments supplied from Java.
     *
     * @param obj target of the method invocation
     * @param name identifying the method
     * @param args positional arguments
     * @return result of call
     * @throws PyAttributeError if the named callable cannot be found
     * @throws Throwable from the called method
     */
    // Compare CPython _PyObject_CallMethodIdObjArgs in call.c
    static Object callMethod(Object obj, String name, Object... args)
            throws PyAttributeError, Throwable {
        Object callable = getAttr(obj, name);
        return callFunction(callable, args);
    }

    /**
     * Convert a {@code tuple} of names to an array of Java
     * {@code String}. This is useful when converting CPython-style
     * keyword names in a call to the array of (guaranteed)
     * {@code String} which most of the implementation of call expects.
     *
     * @param kwnames (keyword) names to convert
     * @return the names as an array
     * @throws PyBaseException(TypeError) if any keyword is not a string
     */
    static String[] namesArray(PyTuple kwnames) throws PyBaseException {
        int n;
        if (kwnames == null || (n = kwnames.size()) == 0) {
            return NO_KEYWORDS;
        } else {
            String[] names = new String[n];
            for (int i = 0; i < n; i++) {
                Object name = kwnames.get(i);
                names[i] = PyUnicode.asString(name,
                        Callables::keywordTypeError);
            }
            return names;
        }
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "keywords must be strings, not 'X'" giving the type X
     * of {@code name}.
     *
     * @param kwname actual object offered as a keyword
     * @return exception to throw
     */
    public static PyBaseException keywordTypeError(Object kwname) {
        String fmt = "keywords must be strings, not '%.200s'";
        return PyErr.format(PyExc.TypeError, fmt,
                PyType.of(kwname).getName());
    }
}
