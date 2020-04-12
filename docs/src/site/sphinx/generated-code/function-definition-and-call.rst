..  generated-code/function-definition-and-call.rst


Function Definition and Call
############################

Calling a Function
******************

The "Simple" Call
=================


Our first target is to work out how to call a simple function.
As a motivating example,
suppose we wish to call a built-in function like ``n = len(x)``.
In byte code:

..  code-block:: none

    1           0 LOAD_NAME                0 (len)
                2 LOAD_NAME                1 (x)
                4 CALL_FUNCTION            1
                6 STORE_NAME               2 (n)

Most obviously,
we must implement a new opcode ``CALL_FUNCTION``
and this is probably backed by an abstract API method.

In fact,
there are several opcodes the compiler can generate to make a function call,
depending on the pattern of arguments at the call site.
These are somewhat tuned to C and the CPython byte code.
We'll mostly cut through them and get to our own Java ones in time.
Although ``CALL_FUNCTION`` has the simplest expression in byte code,
it does not have the simplest implementation.


The "Classic" Call
==================

Once upon a time,
all calls compiled to the equivalent of ``f(*args, **kwargs)``,
and this was served by the abstract API ``PyObject_Call``
(see "Python internals: how callables work" `Bendersky 2012-03-23`_).
It's still there to cover this most general case.

We'll return to the optimised forms,
but for now,
we will force the compiler back to a "classic" call with ``n = len(*(x,))``.
In byte code:

..  code-block:: none

      1           0 LOAD_NAME                0 (len)
                  2 LOAD_NAME                1 (x)
                  4 BUILD_TUPLE              1
                  6 CALL_FUNCTION_EX         0
                  8 STORE_NAME               2 (n)

Functions are objects,
so we must define a type to represent functions,
and this type must define a ``tp_call`` slot in its type object,
while an instance of that type must designate an executable function.

In fact there must be several types of function object,
since we need to call (at least)
functions defined in Python
and functions defined in Java.
A function must be a PyObject so we can bind it to a name in a namespace.

More generally,
we can recognise that ``len`` could have been any callable object,
including an instance of a user-defined class with a ``__call__`` method,
and since the compiler cannot know anything about the actual target,
the same opcode has to work for all of them.

The implementation of the ``CALL_FUNCTION_EX``
(within ``CPythonFrame.eval``)
is straightforward:

..  code-block:: java

                    case Opcode.CALL_FUNCTION_EX:
                        kwargs = oparg == 0 ? null : valuestack[--sp];
                        args = valuestack[--sp]; // POP
                        func = valuestack[sp - 1]; // TOP
                        res = callFunction(func, args, kwargs);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

The local helper ``callFunction`` allows for iterables as arguments,
which we don't currently support in our toy implementation.
It lands (with a real ``tuple`` and a real ``dict``)
at the abstract API ``Callables.call``:

..  code-block:: java

    class Callables extends Abstract {
        // ...
        static PyObject call(PyObject callable, PyObject args,
                PyObject kwargs) throws TypeError, Throwable {
            try {
                MethodHandle call = callable.getType().tp_call;
                return (PyObject) call.invokeExact(callable, args, kwargs);
            } catch (Slot.EmptyException e) {
                throw typeError(OBJECT_NOT_CALLABLE, callable);
            }
        }

As we can see,
the implementation just supplies the arguments directly to the slot,
which may be empty if the object is not callable.

..  _Bendersky 2012-03-23: https://eli.thegreenplace.net/2012/03/23/python-internals-how-callables-work


The Simple ("Vector") Call
==========================

The classic call protocol involves copying argument data several times,
when done generally.
The call site builds the ``tuple``
and the receiving function or a wrapper unpacks it to argument variables,
on the Java (or C) call stack (for functions defined in that language),
or into the local variables of the frame.
When the signature at the call site is fixed (something like ``f(a, b)``),
the cost of generality becomes frustrating.

CPython has acquired many optimisations
and special cases designed to short-cut the classic call in simple cases,
especially when the target is a C function.
CPython 3.8 takes an optimisation previously used internally,
improves on it somewhat,
and makes it a public API described in `PEP-590`_.
This is the "vector call protocol",
by which is meant that arguments are transferred as an array,
that is in fact a slice of the interpreter stack.

Jython 2 has a comparable optimisation in which
a polymorphic ``PyObject._call`` has optimised forms
with any fixed number of arguments up to 4.
These may therefore come directly from the JVM stack in compiled code.





..  _PEP-590: https://www.python.org/dev/peps/pep-0590


Defining a Function in Java
***************************

A Specialised Callable
======================

We can make a type that defines a ``tp_call`` slot
specific to ``len()`` like this:

..  code-block:: java

    class PyByteCode5 {

        @SuppressWarnings("unused")
        private static class LenCallable implements PyObject {
            static final PyType TYPE = PyType.fromSpec(
                    new PyType.Spec("0TestCallable", LenCallable.class));
            @Override
            public PyType getType() { return TYPE; }

            static PyObject tp_call(LenCallable self, PyObject args,
                    PyObject kwargs) throws Throwable {
                PyObject v = Sequence.getItem(args, 0);
                return Py.val(Abstract.size(v));
            }
        }

We call it for test purposes like this:

..  code-block:: java

        @Test
        void abstract_call() throws TypeError, Throwable {
            PyObject callable = new LenCallable();
            PyObject args = Py.tuple(Py.str("hello"));
            PyObject kwargs = Py.dict();
            PyObject result = Callables.call(callable, args, kwargs);
            assertEquals(Py.val(5), result);
        }

Overriding ``tp_call`` like this works,
and since an instance is a ``PyObject``,
we could bind one to the name "len" in the dictionary of built-ins
that each frame references.
But we need to make this slicker and more general,
and it ought to check the arguments for us.


Function and Method Definition
==============================



Defining a Function in Python
*****************************

