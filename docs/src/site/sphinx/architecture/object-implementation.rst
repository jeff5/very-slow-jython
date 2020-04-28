..  architecture/object-implementation.rst


Object Implementation 
#####################

This section is for discussion of
the implementation of operations on Python objects
corresponding to the slots (``tp_hash``, ``nb_add``, etc.)
defined for CPython in the ``PyTypeObject``,
and their relationship to dunder methods (``__hash__``, ``__add__``, etc.).

..  note:: At the time of writing
    the ``evo3`` implementation is providing evidence for certain choices,
    but there is still a long way to go:
    inheritance is not yet correct,
    nor support for acceptable implementations.

..  note:: These notes are quite sketchy at present (``evo3``).


Type Slots
**********

The implementation of object type in CPython
depends on a pointer in every ``PyObject`` to a ``PyTypeObject``,
in which a two-level table structure gives operational meaning,
by means of a pointer to function,
to each of the fundamental operations that any object could,
in principle, support.


Apparent Obsession with ``MethodHandle``
========================================

We are using MethodHandle as an equivalent to C pointer-to-function.
Other possibilities exist.

The ``evo3`` implementation provides evidence this choice is workable,
but in the context of a CPython byte code interpreter,
we might have used other mechanisms (lambda functions, say),
and Jython 2 approaches the same need through overriding methods,
which seems the natural choice in Java.
The repeated idiom ``(PyObject) invokeExact(...)``
is fairly ugly and sacrifices Java safety around the method signature.
The type slots all contain method handles,
and we have used them again in the implementation of ``PyJavaFunctio.tp_call``.

A strong motivation for the use of ``MethodHandle``\s is that
they work directly with ``invokedynamic`` call sites.
We expect to generate ``invokedynamic`` instructions
when we compile Python to JVM byte code,
and this is the code in which we seek maximum performance. 


