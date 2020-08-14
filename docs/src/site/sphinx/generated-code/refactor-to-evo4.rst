..  generated-code/refactor-to-evo4.rst

Refactoring for ``evo4``
########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo4``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo4``.

In the coming sections we address attribute access,
including attributes that are functions (methods).
This cannot be done without considering sub-classing in Python,
for which we shall need much stronger support for inheritance
than ``evo3`` provides.
This means it is already time for an ``evo4``.

The primary innovation is to make entries in the dictionary of each type
representing attributes defined in either Java or Python,
and to fill type slots (still ``MethodHandle``\s) from these entries.

Aspects of the design to be revisited in ``evo4`` are:

* Initialisation of slots (``nb_add``, ``tp_call`` etc.).
* Naming of slot functions (offering simplifications).
* Inheritance of slots.

These features will be added:

* The ``__new__`` slot and its implementation for several types.
* The dictionary of the type.
* Attribute names resolved along the MRO.
* Descriptor protocol.
* Classes defined in Python

``evo3`` as we abandon it contains the beginnings of these features,
but it became impossible to follow through without this re-work,
which would have invalidated the narrative so far.
What we like to do at such a juncture is to *evolve*.

..  note::

    The lists of revisited and added features
    will grow as we work through subsequent sections,
    until the whole becomes untenable and we leap to an ``evo5``.


Another Re-work of ``PyType``
*****************************

Slot Functions
==============

Dunder Methods and the Slot Table
---------------------------------

We wish to support types defined in Python
and a fairly complete model of inheritance.
Types defined in Python define slots through "dunder" methods
(``__len__``, ``__add__``, ``__call__``, etc.),
which are entered in the dictionary of the ``type``
when the class is defined.

When execution of the class body is complete,
CPython goes on to wrap each dunder method in a C function
that it posts to a corresponding slot in the ``type`` object,
the same place they would be if defined in C originally.

In the case of (built-in and extension) types defined in C,
the slot is statically assigned a pointer to the C implementation function,
or is so assigned during initialisation.
CPython creates a dictionary entry to wrap any slot not left empty,
so that however it is implemented, in Python or C,
a type that fills the slot also possesses a dunder method.

At least, this is approximately correct.
The relationship between dunder methods and slots
is not one-to-one in all cases.
The dunder methods express more mature data model than the slots,
and a simpler API towards Python.
This makes both the filling of the slot from a method defined in Python,
and the synthesis of a Python method from a slot filled by C,
difficult in some cases.

This difficulty cannot be resolved by changes to the slot lay-down,
since the lay-down is public API
and many extensions rely on it.
Recent work to make the internals of ``PyTypeObject`` restricted API,
do not hide the set of slot names.


A Java Approach the Slot Table
------------------------------

We need to find an equivalent process in Java
that results in method handles we may post in the ``PyType`` slots,
in support of the abstract object API (``Abstract.add()`` for example),
once execution of the class body has produced entries in the dictionary.

For the sake of regularity,
we will also switch from implementing in Java
the methods that match the CPython slots,
to implementing Java versions of the dunder methods.
We will then produce entries in the dictionary from these definitions,
and after that create method handles uniformly from the dictionary.

This is an important change that moves closer to the Jython 2 design.

We *could* make implementations in the abstract object API
consult the dictionaries along the MRO directly.
However, the use of ``MethodHandle`` should be more efficient
and supports a long-term intention to build ``CallSite`` objects.

With this perspective,
the slot table in the ``PyType`` looks more like
a convenient private cache of look-ups along the MRO,
than like a fundamental feature of the interpreter.
The slot table will not be public API,
but the set of dunder methods probably will be,
and named identically to the Python-level data model.

That we are starting with the benefit of hindsight,
and free of a legacy API,
opens the possibility of a set of slots that matches our needs.
(If there is a legacy API it is the dunder-ish Jython 2 ``PyObject``,
but our re-think of ``PyObject`` as an interface
precludes reproducing it exactly.)

We will consider what slots this leads to shortly,
including the difficult cases CPython has to deal with.

Java Signature of Slots
-----------------------

Although identically named,
the Java dunder methods will have specific signatures,
where a dunder method in Python is a general callable.
(If the signature is wrong, a problem only arises when it is called.)


Scope (notes)
=============

Argument for changes to ``PyType``:

* The centrality of the MRO and its sequence of dictionaries.
* Equivalence of Java and Python definitions (hence use Python names).
* Name collisions at ``__add__``, ``__mul__``.
* Special logic of ``Number.add`` (``PyNumber_Add``),
  ``nb_add``, ``sq_concat``.
* Special logic of ``Number.multiply`` (``PyNumber_Multiply``),
  ``nb_multiply``, ``sq_repeat``.
* Name collisions at ``__len__``.
* Tangled logic of ``Abstract.size`` (``PyObject_Size``),
  ``Sequence.size`` (``PySequence_Size``),
  ``Mapping.size`` (``PyMapping_Size``), ``sq_length``, ``mp_length``.
* Name collisions at ``__getitem__``, ``__setitem__``, objects as indexes,
  and disambiguation by signature.


Dictionary of the ``type``
==========================


Structure of ``PyType``
=======================
