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
* Descriptor protocol.
* The actual Python type is not determined statically by the Java class.
* The ``__new__`` slot and its implementation for several types.
* Attribute names resolved along the MRO.
* Classes defined in Python

``evo3`` as we abandon it contains the beginnings of these features,
but it became impossible to follow through without significant re-work.
Dong that in ``evo3`` would have invalidated the narrative so far.
What we like to do at such a juncture is to *evolve*.

..  note::

    The lists of revisited and added features
    will grow as we work through subsequent sections,
    until the whole becomes untenable and we leap to an ``evo5``.



Slot Functions
**************

Dunder Methods and the Slot Table
=================================

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

In the case of types defined in C,
the built-in and extension types,
the slot is assigned a pointer to the C implementation function,
either statically,
or during ``type`` creation from a specification.
CPython creates a dictionary entry to wrap any slot implemented,
so that however it is implemented, in Python or C,
a type that fills the slot also possesses a dunder method.

At least, this is approximately correct.
The relationship between dunder methods and slots, in some cases,
is not actually one-to-one as this simple description suggests.
Some slots involve multiple dunder methods,
and a few dunder methods affect more than one slot.
However,
the documented data model is expressed in terms of the dunder methods,
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
to implementing Java versions of the dunder methods.
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
but the set of dunder methods will be,
and named identically to the Python-level data model.

That we are starting with the benefit of hindsight,
and free of a legacy API,
opens the possibility of a set of slots
that closely matches the dunder methods.
(If there is a legacy API it is the dunder-ish Jython 2 ``PyObject``,
but our re-think of ``PyObject`` as an interface
precludes reproducing it exactly.)

We will consider what slots this leads to shortly,
including the difficult cases CPython has to deal with.


Descriptors
***********

The entries in the dictionary of a type are (mostly) descriptors.
This type of object is part of the Python data model,
so we must reproduce it accurately.
We will also follow various implementation details,
at least provisionally.

This section contains an analysis of the CPython structures and mechanisms,
translated to Java.
(This translation is partial at the time of writing.)

..  uml::
    :caption: Descriptors and Wrappers (after CPython 3.8)

    PyDescriptor .up.|> PyObject
    PyDescriptor -right-> "owner" PyType

    PyMethodDescr -up-|> PyDescriptor
    PyMethodDescr --> "method" MethodDef

    PyMemberDescr -up-|> PyDescriptor
    PyMemberDescr --> "member" MemberDef

    PyGetSetDescr -up-|> PyDescriptor
    PyGetSetDescr --> "getset" GetSetDef

    PyWrapperDescr -up-|> PyDescriptor
    PyWrapperDescr --> "base" SlotDef

    PyMethodWrapper --left-> "self" PyObject
    PyMethodWrapper ---> "descr" PyWrapperDescr

    class GetSetDef {
        name
        get : getter
        set : setter
        closure
    }

    abstract class PyDescriptor {
        name
        qualname
    }

    class PyMethodDescr {
        vectorcall
    }

    class PyWrapperDescr {
        wrapped
    }

    class MethodDef {
        name
        meth
        flags
        doc
    }

    class MemberDef {
        name
        type : int
        offset
        flags
        doc
    }

    class SlotDef {
        name
        offset : Slot
        function : SlotFunction
        wrapper : WrapperFunction
        doc
        flags
    }

Our names for these are not quite the same as CPython's,
having elided the suffix ``Object`` from the class names,
prefix ``Py`` where it is not a Python object,
and the prefixes like ``d_`` and ``ml_`` from member names.
Attributes that in C are function pointer
may become ``MethodHandle`` or lambdas in Java;
those that are a kind of "offset" may become ``VarHandle``;
those that are ``int`` may become ``enum`` or ``EnumSet``.

We'll examine the descrptors through the lens of the ``*Def`` objects
that specify them.


``MethodDef``
=============

``MethodDef`` (``PyMethodDef`` in CPython)
is a class we have already created
to represent Java functions exposed from modules
(see :ref:`MethodDef-and-PyJavaFunction`).
In CPython,
``PyMethodDef``\s occur in short tables
each entry defining a method or function.

We used them to define the functions in Python modules,
but rather than static tables created by hand
we added annotations to the functions to be exposed,
and these annotations are processed to create a ``MethodDef[]`` table
for each sub-class of ``JavaModule``.
During the initialisation of the module instance,
a ``PyJavaFunction`` is created from each ``MethodDef``
and inserted in the dictionary of the module.

A similar strategy can create a table of methods
for each Python type defined in Java.
A ``PyJavaFunction``
contains a reference to the ``MethodDef`` that specified it,
and so we have all the information we need to create a ``PyMethodDescr``.

The ``PyMethodDescr`` in CPython contains a ``vectorcall`` that is
one of several generic wrappers,
the choice being made according to the characteristics in the ``MethodDef``.
We should have a corresponding entry that may be a ``MethodHandle``,
but perhaps not use the particular categories CPython uses.


``MemberDef``
=============

``MemberDef`` (``PyMemberDef`` in CPython)
represents a field of a Java class
that is exposed to Python as a member of a ``PyObject``.
The member may be marked read-only.
The implementation type of the member must be from a small number of types
supported by the ``PyMemberDesc`` accessor functions.
(In CPython, the types are defined in ``structmember.h``,
but the API is exclusively used by member descriptors.)

In CPython,
``PyMemberDef``\s occur in short tables
each entry specifying the type and offset of the member.
A Java ``MemberDef`` will be based on a ``VarHandle``.



``GetSetDef``
=============

A ``GetSetDef`` (``PyGetSetDef`` in CPython)
represents a field of a Java class
that is exposed to Python as an attribute of a ``PyObject``
through ``get`` and ``set`` accessors.
This offers an unlimited range of possibilities
for producing or setting the exposed value,
where ``MemberDef`` is limited to actual members and predefined types.

In CPython,
``PyGetSetDef``\s occur in short tables
containing get, and (optionally) set, C function pointers.
A Java ``GetSetDef`` will be based on ``MethodHandle``\s.


.. _SlotDef:

``SlotDef``
===========

The class ``SlotDef``
(variously known as ``wrapperbase`` and ``slotdef`` in CPython)
is somewhat like our ``Slot`` enum class,
but there may be more than one per slot.
The ``slotdefs[]`` table in ``typeobject.c`` is an array of these,
built by a set of clever macros.
The entries in ``slotdefs[]`` are ordered by ascending slot offset
in the (heap) type object.
Successive entries may designate the same slot (have equal ``offset``).
In this case we say the dunder methods compete for that slot.
For example, ``__add__`` and ``__radd__`` both compete for ``nb_add``.
Further,
the same dunder method may appear against multiple slots,
for example ``__add__`` appears as the dunder name
for both ``nb_add`` and ``sq_concat``.

A ``SlotDef`` contains the information necessary
to create a ``PyWrapperDescr``
when the corresponding type slot is defined for the owning type.
When that ``PyWrapperDescr`` is inherited
as the defining descriptor for a dunder method
it participates in placing the type slot value from the defining type,
in the corresponding slot of the inheriting type.
This then gives a call to that slot,
say a call from one of the abstract object API methods,
a direct route to the defining function:
a C function pointer in CPython,
or a ``MethodHandle`` in our case,
to the definition in a built-in type.

Since the type slot is being copied,
any update to it must be reprocessed down the same chain of sub-types.
See ``typeobject.c`` at ``update_one_slot()``.

Further to this role in defining slots from Java (or C),
a ``SlotDef`` is a bridge between Java (or C) and Python in two ways:

* Where a dunder method name resolves to a ``PyWrapperDescr``,
  as just described,
  it must be possible to call the slot as a Python method of that name.
  The function in the type slot should then be invoked.
  This is the job of the ``wrapper`` member of the ``SlotDef``.

* Where a dunder method is defined in Python,
  and therefore appears directly or by inheritance
  as an attribute of the type (a ``PyFunction``),
  invoking the corresponding type slot must call the Python function.
  The ``function`` member of the ``SlotDef`` provides
  a value to fill that type slot.


Calling a Type Slot from Python
-------------------------------

Meeting the first need,
to call as a Python method a slot defined in a built-in,
CPython provides a ``wrapper`` that points to a C function
chosen according to the signature of the slot.
These all have a name matching ``wrap_*``,
for example ``wrap_unaryfunc``, ``wrap_lenfunc``.
All have a similar signature themselves,
taking a target object (``self``), an argument tuple,
and the function being wrapped.
A few take an extra argument characteristic of the slot signature
(e.g. a comparison operation).

These are not themselves Python callable objects, of course.
Rather, Python code, by naming the dunder as an attribute on a target object,
causes that object's ``__getattribute__`` implementation
to call the ``__get__()`` of the descriptor (a ``PyWrapperDescr``).
The object returned is a bound method combining the descriptor and the target.
Since it references the descriptor it has access to the ``SlotDef``.

Here is a simple case::

    >>> (m := (42).__add__)
    <method-wrapper '__add__' of int object at 0x00007FFC11AE1BC0>
    >>> m(1)
    43

The bound method defines a ``__call__`` slot,
the implementation of which calls the wrapper function from the ``SlotDef``,
with the particular target object,
the arguments to ``__call__``, and
the slot method to invoke.
This last item is the ``wrapped`` member of the ``PyWrapperDescr``,
which is the contents of the slot it describes in the owning type).

``wrapper`` can point to only a small selection of functions,
defined in advance.
We can implement this by making ``wrapper`` a ``MethodHandle``,
or by creating a sub-class
(of ``SlotDef``, of the method wrapper type, or of ``PyWrapperDescriptor``)
specific to the signature of the slot.


Calling Python from the Type Slot
---------------------------------

The other requirement is that invoking a type slot
should call the appropriate dunder method,
when that is defined in Python.
This is fairly easily met.

In CPython, ``SlotDef`` has a member ``function``
that points to a generically-written C function
with the correct signature for the slot,
and with a body specific to that slot.
In CPython, these all have a name matching ``slot_<slotname>``.

Each of these functions looks up the dunder methods by name on the target
(a single one to use directly in simple cases),
and calls them using Python calling conventions.
This look-up occurs on the type of the target (along the MRO).
The type of the object found in the look-up may be several things.

If the object found has a ``__get__`` (``tp_descr_get``) slot,
that will be called to bind the target object,
and the resulting bound method object (a ``PyMethodObject``)
is treated as the look-up result.
It is this that gets called with the argument list.

In a refinement to this, CPython avoids forming an actual ``PyMethodObject``,
by calling ``__get__``, for built-in types it recognises.
(These are those with the ``Py_TPFLAGS_METHOD_DESCRIPTOR`` flag set.)
Instead, it sets up the same call by working directly
with the with information in the descriptor or function.

Note that defining a dunder method in Python in the class body
places a ``function`` object (``PyFunction``) in the dictionary of the type,
and that a function defines the ``__get__`` slot,
even though it is not actually a type of descriptor.
This process is therefore the operative one in that case,
and the effect is to a call the function
with the target as the first argument.

Here is a simple example::

    >>> class C(int):
        def __neg__(self): return 2*int(self)

    >>> c = C(20)
    >>> -c
    40
    >>> C.__dict__['__neg__']
    <function C.__neg__ at 0x000001C9258BA4C0>
    >>> C.__dict__['__neg__'].__get__(c)
    <bound method C.__neg__ of 20>
    >>> c.__neg__
    <bound method C.__neg__ of 20>
    >>> c.__neg__()
    40

CPython is showing the target of the bound method object as its ``repr()``.
This may not be correct
(see comment in ``classobject.c`` at ``method_repr()``).
Differences of behaviour between different types of attribute
are a consequence of differing definitions of ``__get__``::

    >>> def baz(self): return abs(self)

    >>> C.__neg__ = baz
    <slot wrapper '__add__' of 'int' objects>
    >>> (um := C.__dict__['__neg__'])
    <function baz at 0x000001C9258BAAF0>
    >>> (bm := um.__get__(c))
    <bound method baz of 20>

    >>> C.__neg__ = staticmethod(baz)
    >>> (um := C.__dict__['__neg__'])
    <staticmethod object at 0x000001C9258A6FD0>
    >>> (bm := um.__get__(c))
    <function baz at 0x000001C9258BAAF0>

    >>> C.__neg__ = classmethod(baz)
    >>> (um := C.__dict__['__neg__'])
    <classmethod object at 0x000001C9258A6AF0>
    >>> (bm := um.__get__(c))
    <bound method baz of <class '__main__.C'>>

If the object found does not fill the a ``__get__`` (``tp_descr_get``) slot,
this object is directly the look-up result,
and it gets called with the argument list.

A pointer to one of these ``slot_<slotname>`` functions
is placed in the type slot,
so that invoking the slot on a type object calls the related dunder method.

In complex cases,
multiple dunder methods compete for the same slot,
and the slot function implements logic that may combine
multiple dunder methods.
For example,
the function for binary operation "+" combines ``__add__`` and ``__radd__``,
according to the specified preference order.

In Java, we have a number of implementation possibilities for ``function``.
There is one ``slot_<slotname>`` function per type slot,
so a finite set of possibilities.
Within each signature type (a small number),
they vary only by the dunder method names they embed.

It is appealing to imagine we could avoid the look-up along the MRO
by dunder name that happens on each call.
Instead we could produce a handle once for the call implied by
the current state of the dictionaries along MRO.
CPython already mitigates this look-up cost with a cache.

This would be complex,
because of the number of possible types of descriptor entry,
which includes any type with a ``__get__`` method, not just built-ins.
It would also be made complex,
as indeed CPython's cache is made complex,
by the possibility that code will redefine the dunder method,
or even the hierarchy.
Then, we should re-work the decision all down the inheriting types
(and those disinherited)
in a thread-safe way, of course.


Java Signatures of Slots
************************

We intend to generate a descriptor
for each method or attribute in the class body,
including the dunder methods.
Here we give some thought to the process of
filling the slots from the descriptors.
There will be several sub-types of descriptor,
each able to provide a ``MethodHandle`` for an appropriate slot.

One can do surprising things with descriptors.
Consider the following abuse::

    >>> (d := int.__dict__['__neg__'])
    <slot wrapper '__neg__' of 'int' objects>
    >>> T = type("Thing", (), dict(__invert__=d))
    >>> ~T()
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: descriptor '__neg__' requires a 'int' object but received a 'Thing'

Clearly, some complex validation goes on at the time of the call.
One might think this should be nipped in the bud at class-creation time,
but then this would not work when it should::

    >>> class S(T, int): pass
    ...
    >>> ~S(5)
    -5

The definition of ``__invert__`` we gave to ``T``
is found first on the MRO of ``S`` when the ``nb_invert`` slot is invoked.

We must map a dunder method defined for a type
to a ``MethodHandle`` that can occupy the slot.
As described under :ref:`SlotDef`,
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
are always defined directly by a single dunder method.

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
For each operation there are two dunder methods
with signature ``op(self, right)`` and ``rop(self, left)``.

We will follow Jython 2 in making these separate slots.

What follows is still only notes of the issues
or special features needing consideration
when we come to implement them.


``__add__``
===========

* two dunders, three slots.
* Special logic of ``Number.add`` (``PyNumber_Add``),
  ``nb_add``, ``sq_concat``.


``__mul__``
===========

* two dunders, three slots.
* Special logic of ``Number.multiply`` (``PyNumber_Multiply``),
  ``nb_multiply``, ``sq_repeat``.
* Problem that the second argument of ``sq_repeat`` is ``int.``


``__len__``
===========

* one dunder, two slots.
* Tangled logic of ``Abstract.size`` (``PyObject_Size``),
  ``Sequence.size`` (``PySequence_Size``),
  ``Mapping.size`` (``PyMapping_Size``), ``sq_length``, ``mp_length``.


``__getitem__``
===============

* one dunder, two slots.
* Mapping ``__getitem__`` accepts an object as key.
* ``sq_item`` second argument accepts only ''int''
  while sequence ``__getitem__`` accepts a slice.
* Who is responsible for end-relative indexing (negative ``int``)?
* Sequences accepting slices as indexes do so via
  ``mp_subscript(s, slice)``.
* disambiguation by signature (``sq_item``, ``mp_subscript``).


``__setitem__``
===============

* one dunder, two slots.
* Mapping ``__setitem__`` accepts an object as key.
* ``sq_ass_item`` second argument accepts only ''int''
  while ``__setitem__`` accepts a slice.
* Who is responsible for end-relative indexing (negative ``int``)?
* Sequences accepting slices as indexes do so via
  ``mp_ass_subscript(s, slice, o)``.
* disambiguation by signature (``sq_ass_item``, ``mp_ass_subscript``).


