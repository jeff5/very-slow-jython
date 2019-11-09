..  generated-code/data-model.rst


The Data Model
##############

We shall be taking the approach proposed in :ref:`java_object_approach`,
rather than defining a base type for all Python objects.
We hope thereby to make efficient use of types native to the JVM.


Operations on Objects
*********************

Compiled code may be a Python-specific pseudo-code (word code)
or Java byte code.
In execution, where an operation like ``NEG`` or ``ADD`` is required,
we will encounter either:

* an opcode, with the operand(s) on the interpreter stack, or
* a Java method call, with the operands on the Java stack.

The implementation code we should execute in response
depends on the types of the operands.
In CPython, each Python type is represented by a data structure containing
a table of "slots".
Each fundamental operation is implemented by a function
(``long_neg``, ``long_add``, etc. for type ``int``),
and a pointer to that function is in a slot corresponding to the operation,
in the ``type`` object.



Necessary Kinds of Object
*************************

In CPython, each object we manipulate in the interpreter
contains a pointer to its type object,
so the interpreter can quickly find the specific slot function
implementing the given operation for the operand types presented.
We want to do something similar,
where we obtain a Java ``MethodHandle`` to invoke,
passing the operands as arguments.

We to get the ``MethodHandle`` as a distinct step because it
permits us to cache the result,
accelerating subsequent invocations where the same classes appear.

A ``java.lang.BigInteger`` is to be treated as a Python ``int``,
but there is no provision in the instance for a Python type pointer.
Instead, we must map the Java class to a table of functions
that implement the Python semantics.
We saw in :ref:`dispatch_via_overloading` how to effect this mapping
using a ``java.lang.ClassValue``.

Unlike in CPython,
the table of operations is not the implementation of the type object.
We found in :ref:`dispatch_with_multiple_implementations`,
we could usefully adopt multiple Java types as ``int``.
Similarly we would like to accept both Java ``Float`` and ``Double``
as Python ``float``.
Multiple implementations of ``str`` (``String``, ``Character``)
might be useful too.
Each impementation of the same type needs its own definitions of the operations,
while all link to the same Python type.

However, the Java class of an object does not always tell us its Python type.
Java objects possess a fixed (Java) class for their entire lifetime,
while in some circumstances,
Python objects of one class may change to another (Python) class,
provided the two classes are "compatible".
The requirements for compatibility create equivalence classes of Python types,
such that an object may only mutate between types in the same equivalence class.
As things stand (see note) most built-in types are not mutually compatible,
so that a Python ``int`` is one forever.

Python core developers do not agree what the constraints should be on
class mutation by assigning the ``__class__`` attribute.
The characteristic that historically made the distinction in the code,
was between "heap" types (allowed) and "non-heap" types (disallowed).
This refers to how the ``PyType`` object is allocated,
and ought to be irrelevant.
It will probably remain the case that types with values that are immutable
will forbid assignment to ``__class__``,
so that values interned as shared constants cannot change behaviour.

.. warning::
   At CPython 3.5 an attempt was made to generalise assignment to the
   ``__class__`` attribute to all objects.
   That was curtailed at the last minute (in a release candidate)
   when unfortunate side effects were discovered.
   The motivation was a narrow use case concerning Python modules,
   and a more restrictive rule was eventually restored.
   (See CPython ``typeobject.c:object_set_class`` and comment therein.)
   The current intentions for this language feature are unclear,
   but see `CPython Issue 24991 Define instance mutability explicitly`_.
   The statement about Jython allowing assignment to ``__class__``
   in the `Python-dev discussion on enabling metamodules`_
   is not well researched,
   and permitting assignment to ``__class__`` generally would be a problem.

.. _Python-dev discussion on enabling metamodules:
   https://mail.python.org/pipermail/python-dev/2014-November/137272.html
.. _CPython Issue 24991 Define instance mutability explicitly:
   https://bugs.python.org/issue24991

We propose distinguishing the following kinds of object:

* adopted objects: of Java classes like ``java.math.BigInteger``
  that we wish to accept as implementing a Python type (``int`` in this case).
* crafted objects: of Java classes written to implement specific Python types
  such as ``tuple``.
* found objects: of Java classes that appear in Python as actual Java objects
  (in the way Jython specialises in making possible).
* class-mutable objects: of Java classes designed to allow changing the Python
  class. They may be built-in or the result of a Python ``class`` definition.

Adopted Object
==============

Adopted objects are instances of a Java class
that we accept as implementing a Python type.
For example,
``java.math.BigInteger`` is an acceptable implementation of ``int``.
In general, multiple Java classes may be adopted as the same Python type.
We must specifically prepare the Python behaviour
(slots, Python type and class dictionary),
in a separate object and map the Java type to it,
so that instances behave as instances of the adoptive Python type.
The Java class of the object implies a particular Python type,
so we may only adopt into Python types
whose instances have a fixed type in Python.

Crafted Object
==============

Crafted objects are instances of a Java class
written to implement a specific Python type (such as ``tuple``).
A crafted class may be adopted as one of several acceptable implementations
of a Python type, alongside classes adopted from Java,
or it may be created as the sole implementation.
The crafted class will provide the behaviour
(slots, Python type and class dictionary),
expected of instances of the intended Python type,
which may also provide be its API when the object emerges into Java code.
The Java class implies a particular Python type,
so crafted types implement classes whose instances have a fixed type in Python.

Found Object
============
Found objects are Java objects that the Python interpreter finds itself handling
when they emerge as object references,
and are not designated as types by Python or its libraries.
The behaviour
(slots, Python type and class dictionary),
of a found type must be generated by reflection on the Java class.
The Java class implies a particular Python type,
so instances of found types have a fixed type in Python,
consistent with expectations concerning Java objects.

Class-Mutable Object
====================
Class-mutable objects are instances of a Java class
that permit assignment to the ``__class__`` instance attribute.
They may implement built-in Python types
or be the result of a Python ``class`` definition.
The Java class does not imply a particular Python class
(slots, Python type and class dictionary),
which must be found for each instance from its actual ``__class__``,
including (if need be) its API when addressed by Java code.
We have noted that the compatibility constraints on assignment to ``__class__``,
which involve base class identity and the number and names of ``__slots__``,
create an equivalence relation among Python types,
and instance class mutation may occur only between equivalent types.
In principle, there could be a Java class for each such equivalence class,
or just one may be sufficient.


Code Generated from Expressions
*******************************





Object, Operations and Types
****************************


Reflection
==========
.. copied from VSJ 1 "Type and Operation Dispatch"

To note for future work:

* We have successfully implemented binary and unary operations
  using the dynamic features of Java -- call sites and method handles.
* We have used guarded invocation to create a simple cache in a textbook way.
* Something is not quite right regarding ``MethodHandles.Lookup``:

  * Each concrete sub-class of ``Operations`` yields method handles for its
    ``private static`` implementation methods, using its own look-up.
    (Good.)
  * Each call-site class uses its own look-up to access a ``fallback`` method
    it owns.
    (Good.)
  * The ultimate caller (node visitor here) gives its own look-up object to the
    call-site, as it will under ``invokedynamic``, but we don't use it.
    (Something wrong?)

* We have not yet discovered an actual Python ``type`` object.
  The class tentatively named ``TypeHandler`` (now ``Operations``) is not it,
  since we have several for one type ``int``:
  it holds the *operations* for one Java implementation of the type,
  not the Python-level *type* information.
* Speculation: we will discover a proper type object
  when we need a Python *class* attribute (like the MRO or ``__new__``.
  So far, only *instance* methods have appeared.

