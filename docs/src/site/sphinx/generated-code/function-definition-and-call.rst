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
Function and method calling has been optimised in CPython 3.8,
see `PEP-590`_.
Although ``CALL_FUNCTION`` has the simplest expression in byte code,
it does not have the simplest implementation.

CPython has acquired many optimisations and special cases,
including some that bypass it,
now made public API by PEP-590.
These are somewhat tuned to C and the CPython byte code.
We'll mostly cut through them and get to our own Java ones in time.


The "Classic" Call
==================

Once upon a time,
all calls compiled to the equivalent of ``f(*args, **kwargs)``,
and this was served by the abstract API ``PyObject_Call``
(see "Python internals: how callables work" `Bendersky 2012-03-23`_).

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




..  _PEP-590: https://www.python.org/dev/peps/pep-0590
..  _Bendersky 2012-03-23: https://eli.thegreenplace.net/2012/03/23/python-internals-how-callables-work


