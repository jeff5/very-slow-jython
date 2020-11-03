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

Descriptors must be able to represent
attributes defined in either Java or Python:
type slots (still ``MethodHandle``\s) will be filled from these descriptors.


Scope
=====

Aspects of the design to be revisited in ``evo4`` (not just of ``PyType``)
are:

* Initialisation of slots (``nb_add``, ``tp_call`` etc.).
* Naming of slot functions (offering simplifications).
* Inheritance of slots not simply by copying.

These features will be added:

* The dictionary of the type.
* Descriptor protocol. (Extensively described in :ref:`Descriptors`.)
* The actual Python type is not determined statically by the Java class.
* The ``tp_new`` slot and its implementation for several types.
* The ``tp_getattribute``, ``tp_getattr``, ``tp_setattr`` and  ``tp_delattr``
  slots and their implementations in ``object`` and ``type``.
* Attribute names resolved along the MRO.
* Classes defined in Python.

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
CPython creates a descriptor in the dictionary to wrap any slot implemented.

Thus, however it is implemented, in Python or C,
the slot is filled and there is an entry in the type dictionary.


A Complication
==============

At least, this is approximately correct.
The relationship between special methods and slots
is one-to-one in some cases, as this simple description suggests,
and in many others is more complicated.
Some slots involve multiple special methods,
and a few special methods affect more than one slot.
This complication makes difficult both the filling of the slot
from a method defined in Python,
and the synthesis of a Python method from a slot filled by C.

This difficulty cannot be resolved by changes to the slot lay-down in CPython,
since the lay-down is public API
and many extensions rely on it.
Recent work to make the internals of ``PyTypeObject`` restricted API,
does not hide the set of slot names.

The documented data model is expressed in terms of the special methods,
and we should consider the API towards Python as definitive,
not the gymnastics CPython undertakes to satisfy at once
both the data model and the C API.


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
    TypeError: descriptor '__neg__' requires a 'int' object but received a
    'Thing'

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


