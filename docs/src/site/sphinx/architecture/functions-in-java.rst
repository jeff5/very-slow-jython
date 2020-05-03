..  architecture/functions-in-java.rst


Functions Defined in Java
#########################

Introduction
************

This section is for discussion of
conventions around the definition and calling of functions defined in Java.
We might include in this:

* Functions in modules defined in Jave.
* Abstract API intended to be public.
* Private methods within the interpreter.
* Methods in "discovered" Java objects.  

What signatures can these have?
What conversion rules apply at the call interface?


..  note:: At the time of writing,
    we have a mixed style, but lean towards a signature in Java
    that reflects constraints documented and enforced in CPython. 


We have to consider the precedent set by CPython,
in which public API is usually in terms only of ``PyObject *``,
even when the documentation says the object must be of a particular type.
Contrary to this,
the implementation functions of type slots
sometimes declare with the actual type expected,
and those in modules defined in C,
where Argument Clinic has inserted a type-checking wrapper.

We must also look ahead to the possibility of "acceptable implementations".
This may mean that a method currently declared
to expect exactly ``PyUnicode``
will have to accept ``String`` as well.


Where to use ``null`` and where ``Py.None``?
============================================

A ``null`` should never escape as the value of a Python variable.
In some cases, assigning ``null`` will mean "delete" or "deleted".
A function returning a ``PyObject`` should not return ``null`` but ``Py.None``.
A function returning a specific sub-type ``T`` of ``PyObject``,
but which may produce no result,
should probably return an ``Optional<T>``,
thus forcing the client to acknowledge the possibility explicitly.

It is convenient to have strongly-typed fields and arguments 
in the implementation of Python types.
Sometimes these fields (as Python attributes) are optional.
admit ``None`` as their effective a value.
For example, ``function.__defaults__`` "must be set to a tuple object",
according to the error message,
but ``None`` is also acceptable,
and internally to ``PyFunction``, CPython converts it to a ``NULL``.

In arguments, we cannot pass ``Py.None`` where a specific type is expected.
We may, however, make the argument ``null``, and that might be what we store.

Generally it appears we should support ``null`` on the way in
and ``Py.None`` on the way out.



