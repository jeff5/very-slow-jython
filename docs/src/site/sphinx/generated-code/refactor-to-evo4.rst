..  generated-code/refactor-to-evo4.rst

Refactoring for ``evo4``
########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo4``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo4``.

Another Re-work of ``PyType``
*****************************

Motivation
==========

In the coming sections we address attribute access,
including attributes that are functions (methods).
This cannot be done satisfactorily
without much stronger support for inheritance than ``evo3`` provides.
This in turn will lead us to give each type object a dictionary,
to create descriptors that may be entered into that dictionary,
and to implement the MRO along which
the search for any attribute is made.
This means it is already time for an ``evo4``.

Descriptors must represent attributes defined in either Java or Python,
type slots (still ``MethodHandle``\s) will be filled from these descriptors.


Scope
=====

Aspects of the design to be revisited in ``evo4`` are:

* Initialisation of slots (``nb_add``, ``tp_call`` etc.).
* Naming of slot functions (offering simplifications).
* Inheritance of slots not simply by copying.

These features will be added:

* The dictionary of the type.
* Descriptor protocol. (Extensively described in :ref:`Descriptors`.)
* The actual Python type is not determined statically by the Java class.
* The ``__new__`` slot and its implementation for several types.
* Attribute names resolved along the MRO.
* Classes defined in Python

``evo3`` as we abandon it contains the beginnings of these features,
but it became impossible to follow through without significant re-work.
Doing that in ``evo3`` would have invalidated the narrative so far.
What we like to do at such a juncture is to *evolve*.

..  note::

    The lists of revisited and added features
    will grow as we work through subsequent sections,
    until the whole becomes untenable and we leap to an ``evo5``.



Slot Functions
**************

Special Methods and the Slot Table
==================================

We wish to support types defined in Python
and a fairly complete model of inheritance.
Types defined in Python define slots through special (or  "dunder") methods
(``__len__``, ``__add__``, ``__call__``, etc.),
which are entered in the dictionary of the ``type``
when the class is defined.

When execution of the class body is complete,
CPython goes on to wrap each special method in a C function
that it posts to a corresponding slot in the ``type`` object,
the same place they would be if defined in C originally.

In the case of types defined in C,
the built-in and extension types,
the slot is assigned a pointer to the C implementation function,
either statically,
or during ``type`` creation from a specification.
CPython creates a dictionary entry to wrap any slot implemented,
so that however it is implemented, in Python or C,
a type that fills the slot also possesses a special method.

At least, this is approximately correct.
The relationship between special methods and slots, in some cases,
is not actually one-to-one as this simple description suggests.
Some slots involve multiple special methods,
and a few special methods affect more than one slot.
However,
the documented data model is expressed in terms of the special methods,
and we should consider the API towards Python as definitive.
The difference makes both the filling of the slot
from a method defined in Python,
and the synthesis of a Python method from a slot filled by class definition,
difficult in some cases.

This difficulty cannot be resolved by changes to the slot lay-down in CPython,
since the lay-down is public API
and many extensions rely on it.
Recent work to make the internals of ``PyTypeObject`` restricted API,
does not hide the set of slot names.


A Java Approach the Slot Table
==============================

We need to find an equivalent process in Java
that results in method handles we may post in the ``PyType`` slots,
in support of the abstract object API (``Abstract.add()`` for example),
once execution of the class body has produced entries in the dictionary.

For the sake of regularity,
we will also switch from implementing in Java
the methods that match the CPython slots,
to implementing Java versions of the special methods.
That is,
methods in a Java implementation will use the Python names.

We will then produce entries in the dictionary from these definitions,
and after that create method handles uniformly from the dictionary.

This is an important change that moves closer to the Jython 2 design.

We *could* make implementations in the abstract object API
consult the dictionaries along the MRO directly.
However, the use of ``MethodHandle`` should be more efficient
and supports a long-term intention to build ``CallSite`` objects
that embed these handles.

With this perspective,
the slot table in the ``PyType`` looks more like
a convenient private cache of look-ups along the MRO,
for a privileged set of methods used by the interpreter,
than like a fundamental feature of the language implementation.
The slot table will not be public API,
but the set of special methods will be,
and named identically to the Python-level data model.

That we are starting with the benefit of hindsight,
and free of a legacy API,
opens the possibility of a set of slots
that closely matches the special methods.
(If there is a legacy API it is the dunder-ish Jython 2 ``PyObject``,
but our re-think of ``PyObject`` as an interface
precludes reproducing it exactly.)

We will consider what slots this leads to shortly,
including the difficult cases CPython has to deal with.


Java Signatures of Slots
************************

We intend to generate a descriptor
for each method or attribute in the class body,
including the special methods.
Here we give some thought to the process of
filling the slots from the descriptors.
There will be several sub-types of descriptor,
each able to provide a ``MethodHandle`` for an appropriate slot.

One can do surprising things with descriptors.
Consider the following abuse:

..  code-block:: python

    >>> (d := int.__dict__['__neg__'])
    <slot wrapper '__neg__' of 'int' objects>
    >>> T = type("Thing", (), dict(__invert__=d))
    >>> ~T()
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: descriptor '__neg__' requires a 'int' object but received a 'Thing'

Clearly, some complex validation goes on at the time of the call.
One might think this should be nipped in the bud at class-creation time,
but then this would not work when it should:

..  code-block:: python

    >>> class S(T, int): pass
    ...
    >>> ~S(5)
    -5

The definition of ``__invert__`` we gave to ``T``
is found first on the MRO of ``S`` when the ``nb_invert`` slot is invoked.

We must map a special method defined for a type
to a ``MethodHandle`` that can occupy the slot.
As described under :ref:`PyWrapperDescr`,
CPython provides a default implementation that performs a look-up,
and fills the slot with a pointer to it,
but short-circuits this when the descriptor is already a slot wrapper.

In Java, in the same circumstances,
we shall also reduce the work to a slot copy,
but it is desirable too to avoid the look-up if we can.


Directly-Defined Slots
======================

The slots for many unary numerical operations,
and some slots that have seemingly complex signatures (like ``__call__``)
are always defined directly by a single special method.

When defined in Python,
the descriptor must provide a wrapper
that invokes the method as a general callable.
It may be possible to create a ``MethodHandle`` that does this.

When defined in Java,
the descriptor may derive a ``MethodHandle``
directly for the defining method.
Note that the slot can safely contain that handle
only if the described function is applicable to the implementation
as it is for ``S`` and ``int`` in the Python example above.
If this is not guaranteed by construction,
invoking the handle must lead to a diagnostic (as in ``T`` above).

CPython achieves this by copying the slot itself (as in our ``evo3``),
when inspection of the descriptor leads to this possibility.


Binary Operations
=================

The slot functions for the binary operations of built-in types
in CPython (and in ``evo3``)
are not guaranteed the type of either argument,
and must test the type of both.
For each operation the data model defines two special methods
with signature ``op(self, right)`` and ``rop(self, left)``.
For example, descriptors for ``__sub__`` and ``__rsub__``,
defined in Python in some class,
compete for the ``nb_subtract`` slot.
CPython must define a ``slot_nb_subtract`` function to occupy the type slot,
(see the ``SLOT1BIN`` macro in ``typeobject.c``)
that will try ``__sub__`` or ``__rsub__`` or both,
looking them up by name on the respective left and right objects presented.

We will follow Jython 2 in making these separate slots.
In the example,
the Java implementation consists of two methods ``__sub__`` and ``__rsub__``,
and there are two slots ``nb_sub`` and ``nb_rsub``,
ultimately containing either the handle of the Java implementation,
or a handle able to call the correspondingly-named Python method.

What follows is still only notes of the issues
or special features needing consideration
when we come to implement them.


``__add__``
===========

* two special methods, three slots.
* Special logic of ``Number.add`` (``PyNumber_Add``),
  ``nb_add``, ``sq_concat``.
* Defining ``__add__`` in Python does not populate ``sq_concat``,
  only ``nb_add``
  but ``PySequence_Concat`` tries ``nb_add`` after ``sq_concat``.


``__mul__``
===========

* two special methods, three slots.
* Special logic of ``Number.multiply`` (``PyNumber_Multiply``),
  ``nb_multiply``, ``sq_repeat``.
* Problem that the second argument of ``sq_repeat`` is ``int.``


``__len__``
===========

* one special method, two slots.
* Tangled logic of ``Abstract.size`` (``PyObject_Size``),
  ``Sequence.size`` (``PySequence_Size``),
  ``Mapping.size`` (``PyMapping_Size``), ``sq_length``, ``mp_length``.


``__getitem__``
===============

* one special method, two slots.
* Mapping ``__getitem__`` accepts an object as key.
* ``sq_item`` second argument accepts only ''int''
  while sequence ``__getitem__`` accepts a slice.
* Who is responsible for end-relative indexing (negative ``int``)?
* Sequences accepting slices as indexes do so via
  ``mp_subscript(s, slice)``.
* possible disambiguation by signature (``sq_item``, ``mp_subscript``).


``__setitem__``
===============

* one special method, two slots.
* Mapping ``__setitem__`` accepts an object as key.
* ``sq_ass_item`` second argument accepts only ''int''
  while ``__setitem__`` accepts a slice.
* Who is responsible for end-relative indexing (negative ``int``)?
* Sequences accepting slices as indexes do so via
  ``mp_ass_subscript(s, slice, o)``.
* disambiguation by signature (``sq_ass_item``, ``mp_ass_subscript``).


