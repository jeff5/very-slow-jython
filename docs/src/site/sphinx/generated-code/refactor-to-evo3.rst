..  generated-code/refactor-to-evo3.rst

Refactoring the Code Base
#########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo3``
    and ``rt2/src/test/java/.../vsj2/evo3/PyByteCode4.java``
    in the project source.


Code in the last few sections has all resided in package ``evo2``.
Reflecting on how this has developed,
several choices made along the way could be improved upon.
We don't want to invalidate the commentary that has been written so far,
so that readers can no longer find the code referred to.
The only solution is to make a copy of everything in a new package ``evo3``,
and refactor the code there.


Static Factory Methods in ``Py``
********************************

We have made extensive use of constructors to create Python objects in Java.
There are some potential advantages to using static functions:
brevity in the client code,
the potential to share commonly used values,
and the possibility of returning specialised sub-classes.
This will be done mostly for immutable value types.

It seems sensible (as in Jython 2.7) to implement these in class ``Py``.
We name the factory methods after the Python type they produce.
In place of ``new PyUnicode("a")``,
we should write ``Py.str("a")``,
in place of ``new PyTuple(...)``, ``Py.tuple(...)``,
and so on.
``Py.int(42)`` and ``Py.float(1.0)`` aren't valid Java,
so we take advantage of overloading and write
``Py.val(42)`` and ``Py.val(1.0)``.

``srcgen.py`` supports this with a pluggable code generator,
generating new tests in the new pattern,
from the same examples used previously.


Static Factories for ``PyType``
*******************************

*   Re-work type object construction to use static factory methods
    for better control over the sequence of ``PyType`` creation.
    Identify and support necessary patterns of immutability,
    re-using ``NumberMethods.EMPTY`` etc. where possible.
    Implement slot inheritance by copying handles from the base(s).

Untangling the Type Initialisation
==================================

In ``evo2``,
the implementation class of each Python object
created an instance of concrete class ``PyType`` by calling a constructor
from the static initialisation of the class.
This has been ok so far,
but results in a bit of a recursive scramble
with the risk that we call methods on fundamental types
before they are ready.
(The Jython 2.7 type registry is also delicate on this way.)

We should like the option to create variants of ``PyType``,
according (for example) to different mutability patterns,
and whether the type has numerical or sequence nature.
This might extend to sub-classing ``PyType`` if necessary.
Constraints on the coding of constructors
(when ``super`` and ``this`` or instance methods may be called)
limit the possibilities for expression.
A series of static factory methods and helpers is more flexible.
We might even use (private) sub-classes of ``PyType``
to express different behaviours and information content,
which is not possible if the client must call a constructor.


Immutability and ``*Methods.EMPTY``
===================================

CPython type objects contain a ``tp_flags`` (a bit set)
that records some functionally important information,
and also caches frequently-used, derivable characteristics,
such as whether the type is a subclass of ``int``.
One that we need now is whether slots may be re-written.
In CPython,
this is conflated with whether the type is a "heap type".
This is really about where the type object is allocated.
Built-in types like ``int`` are not "heap-types",
in the sense of mutable,
while user-defined classes are.
When reading CPython source,
one must be alert to which sense of ``Py_TPFLAGS_HEAPTYPE`` is being used.



Inheritance of Slot Functions
=============================

We noted in bool-implementation_ that
``bool`` inherited the slot functions of ``int``,
because the look-up of (say) ``add`` on ``PyBool`` found ``PyLong.add``.
This made the test pass,
but resulted in taking the slow path in ``Number.binary_op1``.

In the refactoring,
we overhaul the way inheritance is handled.
Ambitiously, we aim to:

*   Allow multiple bases.

*   Compute an MRO by Python rules (or approximation).

*   Choose the unique ``__base__`` by Python rules.



Lightweight ``EmptyException``
******************************

*   ``EmptyException`` to be lightweight and static,
    following "The Exceptional Performance of Lil' Exception"
    (`Shipilev 2014`_).
    Add discussion suggesting correct balance.

..  _Shipilev 2014: https://shipilev.net/blog/2014/exceptional-performance/

Utility Methods
***************

*   Reconsider the placement of utility methods,
    such as those for exception construction.

Type Cast in the Method Handle
******************************

*   Try to wrap the cast into the slot function handle graph,
    so that "self" parameters to slot functions may be declared
    with their natural type.

Standardised Type-checking
**************************

*   Work out an approach for type-checking ``PyObject`` arguments,
    where still necessary.

