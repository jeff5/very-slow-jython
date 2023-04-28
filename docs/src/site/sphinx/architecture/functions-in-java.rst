..  architecture/functions-in-java.rst


Functions Defined in Java
*************************

Introduction
============

This section is for discussion of
conventions around the definition and calling of functions defined in Java.
We might include in this:

* Functions in modules defined in Java.
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


Patterns of Method Definition
=============================

In the course of exploring types and modules defined in C in CPython,
we encounter several patterns in Python,
visible to the user of the language.
We must reproduce these when implementing types and modules in Java.
We do not necessarily commit to implementing
the *same* types and modules in Java
that are implemented in C in CPython.

We study the CPython implementation for guidance
how to achieve this visible behaviour,
and find some useful structures and concepts,
but we do not have to follow the pattern if there is a better way in Java.
Here are the visible type of methods
as they appear in the dictionary of built-in types and modules,
and some notes on their implementation in C.


.. csv-table:: Python built-in method ``m`` of type ``T`` or module ``M``
   :header: "Kind", "Dictionary entry ``T.__dict__[m.__name__]``", "Notes"
   :widths: 5, 60, 5

    "special", "``wrapper_descriptor(T, m)``"
    "instance", "``method_descriptor(T, m)``"
    "class", "``classmethod_descriptor(T, m)``"
    "static", "``staticmethod(builtin_function_or_method(m, None))``", "\(1)(2)(3)"
    "``__new__``", "``builtin_function_or_method(T.__new__, T)``", "\(4)(5)"
    "module", "``builtin_function_or_method(m, M)``", "\(6)"

Notes:

1. In CPython, the ``builtin_function_or_method`` constructor
   is in fact given ``T`` to bind and stores it in the field ``m_self``,
   but the surface behaviour is that ``__self__`` is ``None``.
   (Accessors return ``NULL`` internally
   when the flag ``METH_STATIC`` is set in the attached ``PyMethodDef``.)
2. The C signature of a function exposed as static
   begins with an ignored argument which receives ``NULL`` when called.
3. The entry in ``T.__dict__['m']``
   is wrapped in the ``staticmethod`` decorator.
4. In CPython, the ``builtin_function_or_method`` exposing ``__new__`` also
   stores ``T`` in the field ``m_self`` which accesors return because
   the special ``PyMethodDef`` for ``__new__`` does not set ``METH_STATIC``.
5. The entry in ``T.__dict__['__new__']``
   is a ``builtin_function_or_method`` that actually references
   a private function ``tp_new_wrapper`` in C via a private ``PyMethodDef``.
   It most resembles a method of ``type`` bound to instance ``T``.
   It is this private method that invokes the static method posted by ``T``
   to its ``tp_new`` slot.
6. The C signature of a method in a module begins with a module instance,
   which in a call is supplied by the bound value (``M``).

The first three method kinds in the table,
are all represented in the dictionary by *descriptors*:
``wrapper_descriptor``, ``method_descriptor`` and ``classmethod_descriptor``.
They have binding behaviour through a ``__get__`` method
that implements the ``.`` operator.
When any is called directly,
one has to supply a ``self`` object (or class) as the first argument.
When bound to a (non-type) object using ``.`` the result is a bound object:

..  code-block:: python

    >>> type(int.__dict__['__add__'])
    <class 'wrapper_descriptor'>
    >>> type(b:=(7).__add__)
    <class 'method-wrapper'>
    >>> b.__self__
    7
    >>>
    >>> type(int.__dict__['bit_length'])
    <class 'method_descriptor'>
    >>> type(b:=(7).bit_length)
    <class 'builtin_function_or_method'>
    >>> b.__self__
    7
    >>>
    >>> type(int.__dict__['from_bytes'])
    <class 'classmethod_descriptor'>
    >>> type(b:=(7).from_bytes)
    <class 'builtin_function_or_method'>
    >>> b.__self__
    <class 'int'>

A static method is also represented by a descriptor ``staticmethod``,
but ``__get__`` simply returns the inner ``builtin_function_or_method``.

..  code-block:: python

    >>> type(b:=str.__dict__['maketrans'])
    <class 'staticmethod'>
    >>> type(str.maketrans)
    <class 'builtin_function_or_method'>
    >>> assert str.maketrans is b.__func__
    >>> assert "x".maketrans is b.__func__
    >>> type(str.maketrans.__self__)
    <class 'NoneType'>

Decorator ``staticmethod`` is applied to methods in classes defined in Python
to disable binding that would otherwise take place.
Much the same effect could be obtained by
storing the ``builtin_function_or_method`` directly in the dictionary,
since that has no binding behaviour,
although to dispense with it would be
a visible divergence from Python behaviour.

In summary of the first 4 method kinds,
the result of any binding is a ``builtin_function_or_method``,
except when it is a ``method-wrapper``, which is similar.

The last two method kinds in the table do not have binding behaviour.
Python enters an already bound ``builtin_function_or_method``
representing the callable.
The use of ``.`` to access them via a an object
simply returns the value in the dictionary of the type.

..  code-block:: python

    >>> type(int.__dict__['__new__'])
    <class 'builtin_function_or_method'>
    >>> assert int.__new__ is int.__dict__['__new__']
    >>> int.__new__.__self__
    <class 'int'>
    >>>
    >>> type(math.__dict__['sqrt'])
    <class 'builtin_function_or_method'>
    >>> assert math.sqrt is math.__dict__['sqrt']
    >>> math.sqrt.__self__
    <module 'math' (built-in)>

``__new__`` is a static method when defined by the type
but the ``builtin_function_or_method`` differs from the ``staticmethod``
that is the exposed form of other static methods of a built-in.
It is not clear why this is so,
or how essential this difference is to the semantics of Python.
We return ``__new__`` from the exposer as any other static method,
but process ``__new__`` specially in type construction,
where we can reproduce expected behaviour as closely as proves necessary.



Argument and Return Value Coercion
==================================

Where to use ``null`` and where ``Py.None``?
--------------------------------------------

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



