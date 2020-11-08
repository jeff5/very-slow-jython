..  architecture/object-implementation.rst


Object Implementation 
#####################

This section is for discussion of
the implementation of operations on Python objects
corresponding to the slots (``tp_hash``, ``nb_add``, etc.)
defined for CPython in the ``PyTypeObject``,
and their relationship to dunder methods (``__hash__``, ``__add__``, etc.).

..  note:: At the time of writing
    the ``evo4`` implementation is providing evidence for certain choices,
    but there is still a long way to go:
    inheritance is not yet correct,
    nor support for acceptable implementations.


..  _object-methods:

Methods
*******

Special Methods
===============

Special methods define the operations needed

* to interpret byte code,
* to support the abstract object API,
* to implement the built-in functions in Java, and (quite likely)
* to support the same operations compiled to JVM code.

In Java
-------

Special methods defined in the Java implementation of an object
take distinctive names selected from
those defined in the `Python Data Model`_.
Their names in Java will be the same as they are in Python:
``__repr__``, ``__hash__``, ``__add__``, ``__getattribute__``, and so on.
The run-time finds them by reflection and treats them specially.
Here, for example, is a partial ``javap`` dump of ``PyLong``
at a point in its development:

..  code-block:: none

        class PyLong implements PyObject {
          ...
          static PyObject __new__(PyType, PyTuple, PyDict) throws Throwable;
          static PyObject __repr__(PyLong);
          static PyObject __neg__(PyLong);
          static PyObject __abs__(PyLong);
          static PyObject __add__(PyLong, PyObject);
          static PyObject __radd__(PyLong, PyObject);
          static PyObject __sub__(PyLong, PyObject);
          static PyObject __rsub__(PyLong, PyObject);
          static PyObject __mul__(PyLong, PyObject);
          static PyObject __rmul__(PyLong, PyObject);
          ...
          static PyObject __lt__(PyLong, PyObject);
          static PyObject __le__(PyLong, PyObject);
          static PyObject __eq__(PyLong, PyObject);
          static PyObject __ne__(PyLong, PyObject);
          static PyObject __ge__(PyLong, PyObject);
          static PyObject __gt__(PyLong, PyObject);
          static boolean __bool__(PyLong);
          static PyObject __index__(PyLong);
          static PyObject __int__(PyLong);
          static PyObject __float__(PyLong);
          ...
        }

In Python
---------

During the definition of a class in Python,
the body of the class definition is executed
in a way similar to the execution of a function body.
This leaves behind a dictionary containing class members,
including the definition of methods,
and including special methods if any are defined.

Processing that dictionary creates the descriptors
that make the entries attributes accessible in the correct way.
In the case of special methods (defined in Python),
this includes placing a handle in the corresponding type slots,
able to call the function defined.



.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html


..  _type-slots:

Type Slots
**********

We have adopted from CPython the general principle of type slots
as a way to cache ``MethodHandle``\s to the special methods on each type,
that implement its specialisation of
the fundamental operations needed by the byte-code interpreter.
(Our interpreter is actually in the class ``CPythonFrame``,
a specialisation of ``PyFrame``.)


Apparent Obsession with ``MethodHandle``
========================================

We are using ``MethodHandle`` as an equivalent to C pointer-to-function.
Experiments in the Very Slow Jython project
provide evidence this choice is workable.

In the context of a CPython byte code interpreter,
we might have used other mechanisms (lambda functions, say),
and Jython 2 approaches the same need through overriding methods,
which seems the natural choice in Java.
The repeated idiom ``(PyObject) invokeExact(...)``
is fairly ugly and sacrifices compile-time type safety at the call site.
So why on earth are we doing this?

A strong motivation for the use of ``MethodHandle``\s is that
they work directly with ``invokedynamic`` call sites.
Call sites support dynamic specialisation to the exact types
encountered at run-time.
The optimisation built into a JVM understands ``MethodHandle`` trees,
and is able to transform them further to machine code.
When we come to generate code for the JVM,
we expect to output ``invokedynamic`` instructions,
and this is the code in which we seek maximum performance.


Some differences from CPython
=============================

As our thinking has evolved (in The Very Slow Jython Project),
we have become confident that aligning with the data model methods
is preferable to repeating the choice of slots from CPython.
It is effectively what Jython 2 does in defining its ``PyObject``
to have those names as (virtual) methods.
This principle is further enunciated in :ref:`one-to-one-slot-principle`.
For this reason, our differ in their names and number from the CPython slots.


Special Methods and the Slot Table in CPython
---------------------------------------------

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


A Complication in CPython
-------------------------

At least, this is approximately correct.
The relationship between special methods and slots
is one-to-one in some cases, as this simple description suggests,
but in many others it is more complicated.
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


Naming Type Slots
-----------------

The approach to Java implementation of an object differs from CPython,
where the names are only of local significance.
Their global significance is achieved
by being installed in a type object struct at a certain offset,
as a pointer to a function of the right signature.
They are necessarily aligned to the expected behaviour *of the type slots*,
but the type slots bear a complex relationship to the special functions
in the `Python Data Model`_.

In adopting :ref:`one-to-one-slot-principle`,
we have chosen to align our choice of type slots to the special functions
from the Python data model,
rather than the existing slots of CPython.
Furthermore, this structure is flat:
there are no special sub-structures for numeric or sequence types.

We therefore adopt a naming scheme that differs from CPython's
and is noticeably related to the special function names.
After reading a lot of CPython source,
something like ``tp_hash`` or ``nb_add`` "just looks like" a slot name,
so to preserve this character we make them all ``op_xxxx``,
where ``op_`` denotes "operation" and
``xxxx`` is the middle of the "dunder-name" ``__xxxx__``.

.. csv-table:: Example Names for Type Slots
   :header: "Slot", "Special function", "Closest CPython type slot"
   :widths: 10, 10, 20

    "``op_repr``", "``__repr__``", "``tp_repr``"
    "``op_sub``", "``__sub__``", "``nb_subtract``"
    "``op_rsub``", "``__rsub__``", "``nb_subtract``?"
    "``op_getattribute``", "``__getattribute__``", "``tp_getattro``"
    "``op_setattr``", "``__setattr__``", "``tp_setattro``"
    "``op_delattr``", "``__delattr__``", "``tp_setattro`` (null value)"
    "``op_get``", "``__get__``", "``tp_descr_get``"
    "``op_getitem``", "``__getitem__``", "``mp_subscript`` and ``sq_item``"

The full story is in ``Slot.java`` and ``PyType.java``.


Flattening the Slot-function Table
----------------------------------

..  note:: Code examples need updating after the change that this
    text describes.

The implementation of object type in CPython
depends on a pointer in every ``PyObject`` to a ``PyTypeObject``,
in which a two-level table structure gives operational meaning,
by means of a pointer to function,
to each of the fundamental operations that any object could,
in principle, support.

In the CPython ``PyTypeObject``,
some slots are directly in the type object (e.g. ``tp_repr``),
while many are arranged in sub-tables,
pointed to by fields (that may be ``NULL``) in the type object.
The motivation is surely to save space on type objects that do not need
the full set of slots.

We observe that types defined in Python (``PyHeapTypeObject``)
always create all the tables,
so only types defined in C benefit from this parsimony.
As there are 54 optional slots in total,
the benefit cannot exceed 432 bytes per type (64-bit pointers),
which is a minor saving, even if there are a few hundred such types.
(There appear to be 235 type objects in CPython
that spare themselves the weight of the 36-entry ``tp_as_number`` table,
a total saving of 66KB.)

We have therefore chosen an implementation in which
all the slots are fields directly in the type object.
This simplifies the code to create them,
and saves an indirection with each operation.
A common idiom in the CPython source is something like:

..  code-block:: C

    m = o->ob_type->tp_as_mapping;
    if (m && m->mp_subscript) {
        PyObject *item = m->mp_subscript(o, key);
        return item;
    }

With the flattening of the type object,
and the trick of using ``EmptyException`` in place of a test,
the equivalent Java code is just:

..  code-block:: java

        PyType oType = o.getType();
        try {
            return (PyObject) oType.mp_subscript.invokeExact(o, key);
        } catch (EmptyException e) {}

The supporting fields in ``PyType`` are all ``MethodHandle``\s:

..  code-block:: java

    class PyType implements PyObject {
        //...
        // Standard type slots table see CPython PyTypeObject
        MethodHandle tp_hash;
        MethodHandle tp_repr;
        //...

        // Number slots table see CPython PyNumberMethods
        MethodHandle op_neg;
        MethodHandle op_add;
        //...

        // Sequence slots table see CPython PySequenceMethods
        MethodHandle op_getitem;
        MethodHandle op_setitem;
        MethodHandle op_contains;

        //...

We shall not name *all* the fields of a ``PyType`` with the ``op_`` prefix:
fields like ``name``, ``bases`` and ``mro`` are not slots in this sense.

``Slot.java`` defines an enum with a constants for every slot we need:

..  code-block:: java

    enum Slot {

        op_hash(Signature.LEN), //
        op_repr(Signature.UNARY), //
        //...

        op_neg(Signature.UNARY, "-", "neg"), //
        op_add(Signature.BINARY, "+", "add"), //
        //...

        op_getitem(Signature.BINARY), //
        op_getitem(Signature.SETITEM), //

        final MethodType type;
        final String methodName;
        final String opName;
        final MethodHandle empty;
        final VarHandle slotHandle;

        Slot(Signature signature, String opName, String methodName) {
            this.opName = opName == null ? name() : opName;
            this.methodName = methodName == null ? name() : methodName;
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = Util.slotHandle(this);
        }

        Slot(Signature signature) { this(signature, null, null); }

        Slot(Signature signature, String opName) {
            this(signature, opName, null);
        }
        // ...
    }

The ``enum`` encapsulates a lot of behaviour (not shown),
supporting its use.
The name of the slot in the type object
is the same as that of the ``enum`` constant.
There is no relationship as far as Java is concerned,
but by choosing the same name we do not have to specify it in the enum.

The name of the method in the implementation class
is the name in the Python data model,
for example ``op_getitem`` is implemented by ``__getattr__``.
If it cannot be inferred from the pattern of the name,
it has to be an argument to the enum constructor.

..  code-block:: java

    class PyTuple implements PyObject {
        //...
        static int length(PyObject s) {
           //...
        }
        static PyObject sq_item(PyObject s, int i) {
           //...
        }
        static PyObject mp_subscript(PyObject s, PyObject item)
                throws Throwable {
           //...
        }
    }

Note that in the definition of ``enum Slot``,
we defined the implementation method name of ``sq_length`` and ``mp_length``,
to be ``"length"`` in both cases.
This reproduces the behaviour we had before,
but it is not necessarily right.
In all cases in the CPython core where both are defined,
one method serves both slots,
but they are not always both defined.

The initialisation of the ``PyType`` uses a single loop over this enum
to initialise all the slots.


Potentially Problematic Slots in CPython
========================================

The purpose of this section is
to go through all the slots in a CPython type object
that are not one-to-one with special functions.
Such slots might be a problem for us,
either because our simplification leads to a different behaviour,
or because code that uses the CPython slot,
for example in the abstract API,
becomes more difficult to port.
We expect, in fact, that the code becomes clearer in most places.


..  _one-to-one-slot-principle:

The One-to-One Principle [untested]
-----------------------------------

CPython's type slot design
may be appreciated through the ``slotdefs[]`` table in ``typeobject.c``.
Here is a much shortened version:

..  code-block:: java

    static slotdef slotdefs[] = {
        TPSLOT("__getattribute__", tp_getattro, slot_tp_getattr_hook,
               wrap_binaryfunc,
               "__getattribute__($self, name, /)\n--\n\nReturn ... ."),
        TPSLOT("__getattr__", tp_getattro, slot_tp_getattr_hook, NULL, ""),
        TPSLOT("__setattr__", tp_setattro, slot_tp_setattro, wrap_setattr,
               "__setattr__($self, name, value, /)\n--\n\nReturn ... ."),
        TPSLOT("__delattr__", tp_setattro, slot_tp_setattro, wrap_delattr,
               "__delattr__($self, name, /)\n--\n\nReturn ... ."),
        TPSLOT("__lt__", tp_richcompare, slot_tp_richcompare, richcmp_lt,
               "__lt__($self, value, /)\n--\n\nReturn self<value."),
        TPSLOT("__le__", tp_richcompare, slot_tp_richcompare, richcmp_le,
               "__le__($self, value, /)\n--\n\nReturn self<=value."),

        BINSLOT("__sub__", nb_subtract, slot_nb_subtract, "-"),
        RBINSLOT("__rsub__", nb_subtract, slot_nb_subtract, "-"),
        BINSLOT("__mul__", nb_multiply, slot_nb_multiply, "*"),
        RBINSLOT("__rmul__", nb_multiply, slot_nb_multiply, "*"),

        IBSLOT("__imul__", nb_inplace_multiply, slot_nb_inplace_multiply,
               wrap_binaryfunc, "*="),

        MPSLOT("__len__", mp_length, slot_mp_length, wrap_lenfunc,
               "__len__($self, /)\n--\n\nReturn len(self)."),
        MPSLOT("__getitem__", mp_subscript, slot_mp_subscript,
               wrap_binaryfunc,
               "__getitem__($self, key, /)\n--\n\nReturn self[key]."),

        SQSLOT("__len__", sq_length, slot_sq_length, wrap_lenfunc,
               "__len__($self, /)\n--\n\nReturn len(self)."),

        SQSLOT("__mul__", sq_repeat, NULL, wrap_indexargfunc,
               "__mul__($self, value, /)\n--\n\nReturn self*value."),
        SQSLOT("__rmul__", sq_repeat, NULL, wrap_indexargfunc,
               "__rmul__($self, value, /)\n--\n\nReturn value*self."),

        SQSLOT("__getitem__", sq_item, slot_sq_item, wrap_sq_item,
               "__getitem__($self, key, /)\n--\n\nReturn self[key]."),

        SQSLOT("__imul__", sq_inplace_repeat, NULL,
               wrap_indexargfunc,
               "__imul__($self, value, /)\n--\n\nImplement self*=value."),

        {NULL}
    };

We may identify two complicating phenomena,
both known as "competition":

1.  A special function like ``__mul__`` or ``__len__`` is repeated, and
    names more than one slot (second argument to the macro).
    When Python calls ``T.__mul__`` on some type,
    which slot should the wrapper function invoke?
    To which slot does an operation in the interpreter (``*`` say) map?
2.  A single slot like ``tp_getattro`` or ``nb_multiply`` is repeated, and
    is the target of more than one special function.
    If we define both in Python,
    which special function should be called by the ``slot_*`` function
    that CPython places in the slot?

CPython has definite answers to these questions in each case.
For example, the table itself tells us that
no slot function will be synthesised for ``sq_repeat``
in response to ``__mul__``.
Other conflicts are resolved by precedence in the table,
so for example ``mp_length`` (if present) gets to define ``__len__``,
before ``sq_length`` is allowed to,
and both cite the same ``wrap_lenfunc``.

Some competition is fundamental to the semantics of the language,
in particular the giving way in binary operations
to sub-classes through the reflected functions
(for example ``__mul__`` and ``__rmul__``).
In this case, both special methods contribute to API and slot functions.

However, competition contributes to the run time complexity of:

1.  the abstract API implementation
    (functions like ``PyNumber_Multiply`` that must consult ``sq_repeat``);
2.  the functions synthesised to call methods defined in Python
    (functions like ``slot_nb_multiply``);
    and
3.  processing the ``slotdefs[]`` table to create or update a type.

We believe some of the complexity stems from the need to maintain as C API
the layout and meaning of slots in a type object,
where these are relied upon by C extensions.
We do not have this legacy, so there is an opportunity to simplify.
In particular, we shall aim for:

1.  A one-to-one relationship of slots to special methods in the data model
    (in those cases where there is a slot at all).
2.  Irreducible competition is concentrated in the implementation of
    the abstract API methods (``Abstract.add``, etc.),
    keeping the ``MethodHandle`` that occupies the slot simple.

At the same time,
the remaining complexity in the abstract API will have to be replicated
in the structure of the call site, when we come to that stage:
less is better,
but also we hope to pay the price only when linking the site.


Directly-Defined Slots
----------------------

The slots for many unary numerical operations,
and some slots that have relatively complex signatures (like ``__call__``)
are always defined directly by a single special method.

When defined in Python,
the descriptor must provide a wrapper
that invokes the method as a general callable.
It may be possible to create a ``MethodHandle`` that does this.

When defined in Java,
the descriptor may derive a ``MethodHandle``
directly for the defining method.
Note that the slot can safely contain that handle
only if the described function is applicable to the implementation.
If this is not guaranteed by construction,
invoking the handle must lead to a diagnostic.

CPython achieves this by copying the slot itself,
when inspection of the descriptor leads to this possibility.


Binary Operations
-----------------

The slot functions for the binary operations of built-in types
in CPython are not guaranteed the type of either argument,
and must test the type of both.
For each operation the data model defines two special methods
with signature ``__OP__(self, right)`` and ``__rOP__(self, left)``.
For example, descriptors for ``__sub__`` and ``__rsub__``,
defined in Python in some class,
compete for the ``nb_subtract`` slot.

CPython must define a ``slot_nb_subtract`` function to occupy the type slot,
(see the ``SLOT1BIN`` macro in ``typeobject.c``)
that will try ``__sub__`` or ``__rsub__`` or each in turn,
looking them up by name on the respective left and right objects presented.
This is necessary, it seems,
even though ``PyNumber_Subtract`` already contains very similar logic,
because there is only one ``nb_subtract`` slot.

We will follow Jython 2 in making these separate slots.
In the example,
the Java implementation consists of two methods ``__sub__`` and ``__rsub__``,
and there are two slots ``op_sub`` and ``op_rsub``,
ultimately containing either the handle of the Java implementation,
or a handle able to call the correspondingly-named Python method.


Getting, Setting and Deleting
-----------------------------

An important implication of the one-to-one principle is
to go against the widespread convention in CPython that a set operation,
where the value is ``NULL``, is a delete.
This is how the competition for e.g. ``tp_setattr``
is resolved in CPython using if-statements,
in the implementation of ``object.__setattr__``, ``type.__setattr__``
and ``slot_tp_setattro``.

This is not part of the language,
rather we have special methods ``__delattr__``, ``__delitem__``,
and ``__delete__``.
As a result, we shall have distinct slots for these,
named ``op_delattr``, ``op_delitem`` and ``op_delete``.

There is also the problematic ``__del__`` (``op_del`` if we have it),
but this is in a different category.

There are two kinds of getter special function for attributes:
``__getattribute__`` and ``__getattr__``,
that combine in a subtle way in CPython,
but for us more plainly in the abstract API.
Attribute access is amply discussed in :ref:`getattribute-and-getattr`.


``sq_concat`` and ``nb_add``
----------------------------

CPython observations:

* These slots compete to define ``__add__``.
  ``nb_add`` takes precedence.
* Special logic in CPython ``PyNumber_Add`` tries ``sq_concat``
  after the usual dance with ``nb_add`` and its reflection.
* Defining ``__add__`` in Python does not populate ``sq_concat``,
  only ``nb_add``.
  When ``sq_concat`` is empty,
  if both arguments look like sequences,
  ``PySequence_Concat`` tries ``nb_add``.
* For the same reason, there is no ``slot_sq_concat`` dispatcher.
* Filling the ``sq_concat`` slot creates an ``__add__`` descriptor
  (but only if ``nb_add`` did not get there first),
  and it does not create an ``__radd__``.

Possible Java approach:

* ``__add__`` defines ``op_add`` (and ``__radd__`` defines ``op_radd``).
* ``Number.add`` calls only ``op_add`` and ``op_radd``.
* ``Number.add`` and ``Sequence.concat`` are the same thing.


``sq_inplace_concat`` and ``nb_inplace_add``
--------------------------------------------

CPython observations:

* These slots compete to define ``__iadd__``.
  ``nb_inplace_add`` takes precedence.
* Special logic in CPython ``PyNumber_InPlaceAdd``
  tries ``sq_inplace_concat`` and ``sq_concat``
  after both ``nb_inplace_add`` and ``nb_add`` prove not to be implemented.
* Defining ``__iadd__`` in Python does not populate ``sq_inplace_concat``,
  only ``nb_inplace_add``.
  When ``sq_inplace_concat`` and ``sq_concat`` are both empty,
  if both arguments look like sequences,
  ``PySequence_InPlaceConcat`` tries ``nb_inplace_add`` and ``nb_add``.
* For the same reason, there is no ``slot_sq_inplace_concat`` dispatcher.
* Filling the ``sq_inplace_concat`` slot creates an ``__iadd__`` descriptor
  (but only if ``nb_inplace_add`` did not get there first).

Possible Java approach:

* ``__iadd__`` defines ``op_iadd``.
* ``Number.inPlaceAdd`` calls only ``op_iadd``.
* ``Number.inPlaceAdd`` and ``Sequence.inPlaceConcat`` are the same thing.
* The fall-back from ``__iadd__`` to ``__add__`` remains necessary.
  (Not ``__radd__`` as well, notice.)


``sq_repeat``, ``nb_multiply`` and ``nb_rmul``
----------------------------------------------

CPython observations:

* These slots compete to define ``__mul__`` and ``__rmul__``.
  ``nb_multiply`` takes precedence.
* Special logic in CPython ``PyNumber_Multiply`` tries ``sq_repeat``
  after the usual dance with ``nb_multiply`` and its reflection.
* Defining ``__mul__`` in Python does not populate ``sq_repeat``,
  only ``nb_multiply``.
  When ``sq_repeat`` is empty,
  if the first argument looks like a sequence,
  ``PySequence_Repeat`` tries ``nb_multiply``.
* For the same reason, there is no ``slot_sq_repeat`` dispatcher.
* Filling the ``sq_repeat`` slot creates both ``__mul__`` and ``__rmul__``
  descriptors (but only if ``nb_multiply`` did not get there first).
* A complication is that the second argument of ``sq_repeat`` is ``int``.

Possible Java approach:

* ``__mul__`` defines ``op_mul`` (and ``__rmul__`` defines ``op_rmul``).
* ``Number.multiply`` calls only ``op_mul`` and ``op_rmul``.
* ``Number.multiply`` and ``Sequence.repeat`` are nearly the same,
  but the latter wraps its integer argument as an object for ``op_mul``.
  This inefficiency has negligible impact in the core code base.
* Note ``op_mul`` not ``op_multiply``, for brevity and consistency.


``sq_inplace_repeat`` and ``nb_inplace_mul``
--------------------------------------------

CPython observations:

* These slots compete to define ``__imul__``.
  ``nb_inplace_multiply`` takes precedence.
* Special logic in CPython ``PyNumber_InPlaceMultiply``
  tries ``sq_inplace_repeat`` and ``sq_repeat``
  after ``nb_inplace_multiply`` and ``nb_multiply`` are found not implemented.
* Defining ``__imul__`` in Python does not populate ``sq_inplace_repeat``,
  only ``nb_inplace_multiply``.
  When ``sq_inplace_repeat`` and ``sq_repeat`` are both empty,
  if the first argument looks like a sequence,
  ``PySequence_InPlaceRepeat`` tries ``nb_inplace_multiply``
  and ``nb_multiply``.
* For the same reason, there is no ``slot_sq_inplace_repeat`` dispatcher.
* Filling the ``sq_inplace_repeat`` slot creates an ``__imul__`` descriptor
  (but only if ``nb_inplace_multiply`` did not get there first).
* A complication is that the second argument of ``sq_inplace_repeat``
  is ``int``.

Possible Java approach:

* ``__imul__`` defines ``op_imul``.
* ``Number.inPlaceMultiply`` calls only ``op_imul``.
* ``Number.inPlaceMultiply`` and ``Sequence.inPlaceRepeat``
  are nearly the same,
  but the latter wraps its integer argument as an object for ``op_imul``.
  This inefficiency has negligible impact in the core code base.
* The fall-back from ``__imul__`` to ``__mul__`` remains necessary.
  (Not ``__rmul__`` as well, notice)
* Note ``op_imul`` not ``op_inplace_multiply``, for brevity and consistency.


``sq_length`` and ``mp_length``
-------------------------------

CPython observations:

* These slots compete to define ``__len__``.
  ``mp_length`` takes precedence.
* ``PyObject_Size``, ``PySequence_Size`` and ``PyMapping_Size``
  cross-refer in a tangled way.
* ``PySequence_Size`` calls ``sq_length`` (if defined)
  or (if not) produces an error.
  The error message depends on whether ``mp_length`` is defined.
  If ``mp_length`` is defined it is "not a sequence"
  rather than "has no ``len()``"
* ``PyMapping_Size`` calls ``mp_length`` (if defined)
  or (if not) produces an error.
  The error message depends on whether ``sq_length`` is defined.
  If ``sq_length`` is defined, it is "not a mapping"
  rather than "has no ``len()``"
* ``PyObject_Size`` calls ``sq_length`` (if defined)
  or (if not) falls back to ``PyMapping_Size``,
  which, if ``mp_length`` is not defined,
  can then only produce "has no ``len()``".
* ``builtins.len()`` calls ``PyObject_Size``.

Possible Java approach:

* Just one ``op_len`` slot used by ``Abstract.size``.
* The error message is that the type "has no length".
* ``Sequence.size``, ``Mapping.size`` and ``Abstract.size``
  are all the same thing.
* ``builtins.len()`` calls ``Abstract.size``.


..  _sq_item-and-mp_subscript:


``sq_item`` and ``mp_subscript``
--------------------------------

CPython observations:

* These slots compete to define ``__getattr__``.
  ``mp_subscript`` takes precedence.
* ``sq_item`` accepts a non-negative integer index,
  while ``mp_subscript`` accepts an object.
* The opcode ``BINARY_SUBSCR`` is implemented by calling ``PyObject_GetItem``.
* Defining ``__getitem__`` in Python does not populate ``sq_item``,
  only ``mp_subscript``,
  so ``PyObject_GetItem`` tries ``mp_subscript`` (if defined) first,
  or (if not, and ``sq_item`` is) converts the index argument to an ``int``
  and calls ``PySequence_GetItem``.
  The conversion may raise an error about "sequence index" type.
* There is an additional hook in ``PyObject_GetItem`` to make
  type objects support indexing, calling ``__class_getitem__``
* ``PySequence_GetItem`` accepts a signed integer index,
  and is responsible for end-relative indexing when the index is negative.
* The error from ``PySequence_GetItem`` when it fails differs if
  ``mp_subscript`` is defined ("not a sequence")
  or not defined ("does not support indexing").
* ``wrap_sq_item``, which wraps ``sq_item`` as ``__getitem__``,
  accepts negative indices as end-relative.
  (The wrapper for ``mp_subscript`` is just ``wrap_binaryfunc``
  so the objects go through unmolested to the implementation.)
* Slot ``mp_subscript`` accepts an object as key,
  and the implementing object is free to interpret the key
  as an integer if it needs to.
* Sequences accepting slices as indexes do so via
  ``mp_subscript(s, slice)``.
  ``sq_slice`` seen in many type objects is no longer used.
* ``PySequence_GetSlice`` creates a slice object from its integer arguments
  and delegates to ``mp_subscript``, if defined,
  otherwise the "object is unsliceable".
* There is no ``PyMapping_GetItem``,
  but a ``PyMapping_GetItemString`` that wraps its ``char*`` argument
  in a ``str`` and delegates to ``PyObject_SetItem``,
  which as we've seen tries ``mp_subscript`` then ``sq_item``.
* ``collections.deque`` is the only built-in type
  to define ``sq_item`` but not ``mp_subscript``:
  oversight perhaps.


Possible Java approach:

* A single slot ``op_getitem`` is used by abstract API ``getItem``,
  and accepts an ``object`` as the index.

* In general, implementations must check the type of the index object,
  and perform the end-relative indexing and slice interpretation.
  (Utility functions are desirable to support this.)

* A ``getItem`` taking integer argument may be provided (as now),
  that wraps the integer argument as an object for ``op_getitem``,
  but the efficiency that motivated the specialisation to integer is lost.

* Note that use of opcode ``BINARY_SUBSCR`` and its JVM equivalent
  will provide the index as a Python ``object`` from the stack.
  The desire for a specialisation to ``int`` can only come from internal use.

* If this inefficiency has unacceptable impact,
  the implementation could specialise to built-in types actually encountered,
  without a dedicated slot.
  E.g. ``getItem(PyObject, int)`` calls ``PyList.getItem(int)``

* Make type objects support indexing by defining ``PyType.__getitem__``,
  not by a special tweak to ``getItem``.


``sq_ass_item`` and ``mp_ass_subscript``
----------------------------------------

The observations and suggestions of the previous section are the same here,
with adjustments to setting and deleting, in place of getting.

CPython observations (mostly the same as :ref:`sq_item-and-mp_subscript`):

* These slots compete to define ``__setattr__`` and ``__delattr__``.
  ``mp_ass_subscript`` takes precedence, defining both.
* ``sq_ass_item`` accepts a non-negative integer index,
  while ``mp_ass_subscript`` accepts an object.
* The opcode ``STORE_SUBSCR`` is implemented by calling ``PyObject_SetItem``,
  and ``DELETE_SUBSCR`` by calling ``PyObject_DelItem``.
  ``STORE_NAME`` and ``DELETE_NAME`` also,
  used where the local variables are a name space (rather than an array).
* Defining ``__setitem__`` or ``__delitem__`` in Python
  does not populate ``sq_ass_item``, only ``mp_ass_subscript``,
  so ``PyObject_SetItem`` and ``PyObject_DelItem``
  try ``mp_subscript`` (if defined) first,
  or (if not, and ``tp_as_sequence`` is)
  convert the index argument to an ``int``
  and call ``PySequence_SetItem`` or ``PySequence_DelItem`` respectively.
  The conversion may raise an error about "sequence index" type.
* ``PySequence_SetItem`` and ``PySequence_DelItem``
  accept a signed integer index,
  and are responsible for end-relative indexing when the index is negative.
* The error from ``PySequence_SetItem`` or ``PySequence_DelItem``
  when they fail differs if
  ``mp_ass_subscript`` is defined ("not a sequence")
  or not defined ("does not support item assignment" or "... deletion").
* ``wrap_sq_setitem``, which wraps ``sq_ass_item`` as ``__setitem__``,
  and ``wrap_sq_delitem``, which wraps ``sq_ass_item`` as ``__delitem__``,
  accept negative indices as end-relative.
  (The wrappers for ``__setitem__`` and ``__delitem__``,
  when implemented by ``mp_ass_subscript``,
  both pass the objects without conversion to the implementation.)
* Slot ``mp_ass_subscript`` accepts an object as key,
  and the implementing object is free to interpret the key
  as an integer if it needs to.
* Sequences accepting slices as indexes do so via
  ``mp_ass_subscript(s, slice, v)`` (where ``v=NULL`` for deletion).
  ``sq_ass_slice`` seen in many type objects is no longer used.
* ``PySequence_SetSlice`` and ``PySequence_DelSlice``
  create a slice object from their integer arguments
  and delegates to ``mp_subscript``, if defined,
  otherwise the "object doesn't support slice assignment" (or "... deletion").
* There is no ``PyMapping_SetItem`` or ``PyMapping_DelItem``,
  but a ``PyMapping_SetItemString`` that wraps its ``char*`` argument
  in a ``str`` and delegates to ``PyObject_SetItem``,
  which as we've seen tries ``mp_ass_subscript`` then ``sq_ass_item``.
* ``PyObject_SetItem``, but not ``PySequence_SetItem``,
  explicitly rejects a ``NULL`` value as an attempt to delete an item.


Possible Java approach:

* A single ``op_setitem`` slot is used by abstract API ``setItem``,
  and accepts an ``object`` as the index.
* Provide ``op_delitem`` as a distinct slot in the same way.
  ``delItem`` uses this slot.
* In general, implementations must check the type of the index object,
  and perform the end-relative indexing and slice interpretation.
  (Utility functions are desirable to support this.)
* A solution is possible that wraps the integer argument of
  ``setItem(PyObject, int, PyObject)`` or ``delItem(PyObject, int)``,
  as an object for ``op_setitem`` or ``op_delitem``.
  Again, an API function could specialise to built-in types encountered.


``tp_richcompare``
------------------

CPython observations:

* ``tp_richcompare`` defines ``__lt__``, ``__le__``,
  ``__eq__``, ``__ne__``, ``__le__`` and ``__gt__``.

* In a built-in type, a single function implements all six forms.
  An additional parameter communicates which comparison to perform.
  This is attractive because a three-way comparison may be wrapped
  by the appropriate inequality in a ``switch`` statement.

* In the byte code interpreter,
  a single ``COMPARE_OP`` opcode covers these six and also
  ``is``, ``is not``, ``in``, ``not in``,
  and exception matching to support ``try-except``.
  (For the big six, the whole involves several calls and branches.)

* When calling the slot from Python (``x.__le__(y)``, for example),
  a descriptor for ``__le__`` leads to ``richcmp_le``
  which calls ``tp_richcompare`` with the code ``Py_LE``.

* When calling a Python implementation via the ``tp_richcompare`` slot,
  the type slot will contain ``slot_tp_richcompare``,
  which finds the descriptor by the name corresponding to the code.
  If the particular special function is not overridden in Python,
  the descriptor will be an inherited one,
  and the target method will be the ``tp_richcompare`` slot,
  in a base class,
  called via the approriate ``richcompare_*`` to specify the code.

* In ``object``,
  implementations exist for ``__eq__`` and ``__ne__`` *only*.
  (See ``object_richcompare`` in ``typeobject.c``.)

* The abstract API includes ``PyObject_RichCompare`` and
  ``PyObject_RichCompareBool``
  that wrap this slot and take the (big six) operation as a code.

Possible Java approach:

* Just implement the separate functions ``__lt__``, ``__le__``,
  ``__eq__``, ``__ne__``, ``__le__`` and ``__gt__``.
  The inheritance will then be what the user expects,
  without complex logic.

* This is 5 additional slots and an increase in similar-looking code.
  In return, we have a method handle straight to that code.
  If the trade seems good for some type,
  we may easily create each method as a wrapper on a 3-way comparison.

* The fact that ``COMPARE_OP`` invokes ``__contains__``
  alongside the ``tp_richcompare`` operations is simply part of the
  same flattening.



Initialisation of Slots
=======================

..  note:: Code and text need updating after the changes suggested are made.


From Definitions in Java
------------------------

We have established a pattern in ``rt2`` (``evo2`` onwards)
whereby each ``PyType`` contains named ``MethodHandle`` fields,
pointing to the implementation of the "slot functions" for that type.
At the time of writing (``evo3``),
these are identified by a reserved name like ``nb_add`` or ``tp_call``.
Other approaches are possible and certainly other names.
The design, using a system of Java ``enum``\s denoting the slots,
has worked smoothly in the definition of a wide range of slot types.

The handle in a given slot has to have
a signature characteristic of the slot.

Where a slot defined in a type corresponds to a special function,
in the way for example ``nb_negative`` corresponds to ``__neg__``,
a callable that wraps an invocation of that slot
will be created in the dictionary of the type.
This makes it an attribute of the type.
Instances of it appear to have a "bound" version of the attribute,
that we may tentatively equate to a Curried ``MethodHandle``.

..  code-block:: pycon

    >>> int.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> int.__dict__['__neg__']
    <slot wrapper '__neg__' of 'int' objects>
    >>> (42).__neg__
    <method-wrapper '__neg__' of int object at 0x00007FF8E0CD9BC0>

If the slot is inherited,
it is sufficient that the method be an attribute by inheritance.

..  code-block:: pycon

    >>> bool.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> bool.__dict__['__neg__']
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    KeyError: '__neg__'
    >>>
    >>> class MyInt(int): pass
    ...
    >>> MyInt.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> MyInt.__dict__['__neg__']
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    KeyError: '__neg__'
    >>> m = MyInt(10)
    >>> -m
    -10

In the last operation,
``-m`` invokes the slot ``nb_negative`` in the type of ``m``,
which is a copy of the one in ``int``.
This happens without a dictionary look-up.


From Instance Methods [untested]
--------------------------------

In implementations up to ``evo3``,
the functions are ``static`` members of the implementation class of the type,
or of a Java super-type,
with a signature correct for the slot.
They could, without a significant change to the framework,
be made instance methods of that class.

Wer took a step towards instance methods in ``evo3``,
when it became possible for an argument to the slot function
to adapt to the implementing type.
The method handle in slot ``nb_negative``
has ``MethodType`` ``(O)O`` as it must,
but the implementing function has signature ``(S)O``,
where ``S`` is the implementing type (the type of ``self``).
This is dealt with by a cast in the method handle,
which is neater than doing so in the implementation.

An exception to that pattern occurs with binary operations,
since although at least one of the operands has the target type,
or that implementation would not have been called,
it may be on the left or the right.
As a result,
the implementation (in CPython) must coerce both arguments.

Binary operations could be split into two slots
(``nb_add`` and ``nb_radd``, say),
guaranteeing the type of the target.
The split is necessary if we choose to make the slots instance methods.
In the instance method for ``nb_radd``,
the right-hand argument of ``+`` becomes the target of the call,
therefore the left-hand argument of the signature ``(S,O)O``.
We see this also in the (otherwise quite different)
Jython 2 approach to slot functions.


From Definitions in Python [untested]
-------------------------------------

A function defined in a class becomes a method of that class,
that is, it creates a function that is an attribute of the type.
This is true irrespective of the number or the names of the arguments.
We consider here how functions with the reserved names
``__neg__``, ``__add__``, and so on,
can be made to satisfy the type slots as the Python data model requires.

We saw in the previous section how the definition of a slot
induced the existence of a callable attribute,
a wrapper method on the slot that implements the basic operations,
and that this attribute was inherited by sub-classes:

..  code-block:: pycon

    >>> class MyInt(int): pass
    ...
    >>> MyInt.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> m = MyInt(10)
    >>> -m
    -10

Overriding ``__neg__`` changes this behaviour,
because assignment to a special function in a type
assigns the slot as well.
Note that,
although these methods are usually defined with the class,
they may be assigned after the type has been created,
and the change affects existing objects of that type.

..  code-block:: pycon

    >>> MyInt.__neg__ = lambda v: 42
    >>> -m
    42
    >>> MyInt.__neg__
    <function <lambda> at 0x000001C97118EA60>
    >>> MyInt.__dict__['__neg__']
    <function <lambda> at 0x000001C97118EA60>

The implementation of attribute assignment in ``type``
must be specialised to check for these special names.
It must insert into the slot (``nb_negative`` in the example)
a ``MethodHandle`` that will call the ``PyFunction``
being inserted at ``__neg__``.

CPython ensures that a change of definition is visible
from types down the inheritance hierarchy from the one modified,
so that the behaviour of classes inheriting this method follows the change.

..  code-block:: pycon

    >>> class MyInt2(MyInt): pass
    ...
    >>> m2 = MyInt2(100)
    >>> -m2
    42
    >>> MyInt.__neg__ = lambda v: 53
    >>> -m2
    53

This fluidity limits the gains available from binding a handle to a call site.
A call site capable of binding a method handle
(even one guarded by the Python type of the target)
must still consult the slot because it may have changed by this mechanism.
A call site may bind the actual value found in a slot
only if that is immutable or it becomes an "observer"
of changes coming from the type hierarchy,
potentially to be invalidated by a change (see ``SwitchPoint``).
The cost of invalidation is quite high,
but applications do not often have to redefine a slot.

Some types,
generally built-in types,
do not allow (re)definition of special functions,
even by manipulating the dictionary of the type.

..  code-block:: pycon

    >>> int.__neg__ = lambda v: 42
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: can't set attributes of built-in/extension type 'int'
    >>> int.__dict__['__neg__'] = lambda x: 42
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: 'mappingproxy' object does not support item assignment

A call site that binds the value from a slot in such a type
does not need to become an observer of the type,
since no change will ever be notified.


Bug involving Arithmetic and Sequence Slots in CPython
------------------------------------------------------

Whilst on this subject,
it is worth noting an `operand precedence bug`_ in CPython
with respect to sequence ``+`` and ``*``,
where the same special function defines multiple slots.
The examples are ``__add__``,
which fills both ``nb_add`` and ``sq_concat``,
and ``__mul__``,
which fills both ``nb_multiply`` and ``sq_repeat``.
This also involves the reflected and in-place variants of these operators
(``__iadd__``, ``__imul__``, ``__radd__``, ``__rmul__``).

A `discussion of the operand precedence bug`_ concludes that
the root of the problem is that the abstract implementation
of these binary operation tries to treat both arguments as numeric,
that is, CPython tries the ``nb_add`` slot in the left *and right* operands,
before it tries ``sq_concat`` in the left.
A simple illustration is:

..  code-block:: pycon

    >>> class C(tuple):
    ...     def __radd__(w, v):
    ...         return 42
    ...
    >>> [1,] + C((2,3,4))
    42

In fact, there is a ``list.__add__``,
but it is defined by the ``sq_concat`` slot,
which is not tried until after the ``nb_add`` of ``C``,
with the ``C`` instance on the right leading to a call of ``__radd__``.
(Note that ``C`` is not a sub-class of ``list``.)

Several downstream libraries depend on this bug.
They may give different meanings to the ``nb_add`` and ``sq_concat`` slots,
or the ``nb_multiply`` and ``sq_repeat`` slots,
relying on the (faulty) ordering to get their ``nb_add`` called first.
This is only possible in the C implementation of their objects,
so it should be considered a CPython detail, not a language feature.
(PyPy has reproduced the bug so that it can support these C extensions.)

..  note:: We could do away with the ``sq_concat`` slot,
    and have only ``nb_add``,
    which would then be implemented by ``list``, etc. as concatenation.
    And the same for ``sq_repeat`` in favour of ``nb_multiply``.
    There would then be only one place to look for ``list.__add__`` etc.,
    and it would definitely be tried first.

..  _operand precedence bug:
    https://bugs.python.org/issue11477
..  _discussion of the operand precedence bug:
    https://mail.python.org/pipermail/python-dev/2015-May/140006.html


Inheritance of Slots [untested]
-------------------------------

The following is an understanding of the CPython implementation.
(Certain slots have to be given special treatment,
but for most operations, the account here is accurate.)
The behavioural outcome must be the same for all implementations,
and having decided on a Java implementation that uses slots,
the mechanics would have to be similar.

When a new type is defined,
a slot will be filled, by default, by inheritance along the MRO.
This does not happen directly by copying,
but indirectly through the normal rules of class attribute inheritance,
then the insertion of a handle for the slot function.
These are the same rules under which requested ``x.__add__``, say,
will be sought along the MRO of ``type(x)``.

If the inherited attribute, where found, is a wrapper on a slot,
certain coherence criteria are met,
and there are no additional complexities
the wrapped slot may be copied to the new type directly.
(It is unclear from comments in CPython
``~/Objects/typeobject.c update_one_slot()``
exactly what "complex thinking" the code is doing.
This is the bit of CPython that offers free-as-in-beer ... beer.)

If the inherited attribute is a method defined in Python,
the slot in the new class will be an invoker for the method,
identified by its name.
Each call involves searching for the definition again along the MRO.
(Search along the MRO is backed by a cache, in CPython,
mitigating the obvious slowness.)

When a special function is re-defined in a type,
affected slots in the sub-types of that type are re-computed.
This is why a re-definition is visible in derived types.


``tp_new`` and Java Constructors
********************************

In CPython,
the ``tp_new`` slot of a particular instance of ``PyTypeObject``,
acts as the constructor for the type the ``PyTypeObject`` represents.
This section gives detailed consideration to the problem of
implementing its behaviour in Java.

A "second phase" of construction is performed by ``tp_init``,
but this has much the character of any other instance method.
Although called once automatically, it may be called again expressly,
if the programmer chooses.
``tp_new``, however, is a static method called once per object,
since creates a new instance each time.

Calling a type object
(that is, invoking the ``tp_call`` slot of the ``PyTypeObject`` for ``type``,
and passing it the particular ``PyTypeObject`` for ``C`` as the target)
is what normally leads to invoking the ``tp_new`` slot
on the ``PyTypeObject`` for ``C``,
and ``tp_init`` soon after.
An introduction to the topic,
by Eli Bendersky,
may be found in `Python object creation sequence`_.


Relation of ``tp_new`` to the Java constructor
==============================================

Close, but not close enough
---------------------------

It appears at first as if a satisfactory Java implementation
of the slot function would be the constructor in the defining class.
But a ``tp_new`` slot is inherited by copying,
and many Python types simply get theirs from ``object``.
The definition of ``tp_new`` executed in response to a call ``C()``
could easily be in some ancestor ``A`` on the MRO of ``C``.
The Java constructor for ``A`` would only be satisfactory if
the Java class implementing ``C`` were
the same as that implementing ``A``.
This will not be true in general.

An instance must be created somehow,
so a Java constructor must be invoked,
but from the observation above,
it isn't enough simply to place a ``MethodHandle`` to the constructor
in the ``tp_new`` slot,
even if the signature is made to match.


``__new__`` and a parallel
--------------------------

In cases where ``C`` customises ``tp_new`` in Python
(defines ``__new__``),
it is conventional for ``C.__new__`` to call ``super(C, cls).__new__``
before making its own customisations.
This use of ``super`` means the interpreter should
find ``__new__``, in the MRO of ``cls``, starting after ``C``,
and so the call is to the first ancestor of ``C`` defining it.
Something equivalent must happen in a built-in or extension type.

Since each ``__new__`` (or ``tp_new``) defers immediately to an ancestor,
the first customisation that *completes* is in the ``type`` of ``object``.
This is similar to the way in which Java constructors,
explicitly or implicitly,
first defer to their parent's constructor.
The ancestral line in Java traces itself all the way to ``Object``,
which is therefore the last constructor to start and first to complete.


Allocation before initialisation
--------------------------------

Recall that the first argument in each ``tp_new`` slot invocation
is the type of the target class ``C``.
The ``tp_new`` in the ``PyTypeObject`` for ``object`` in CPython
invokes a slot on the target class we haven't mentioned yet, ``tp_alloc``.
This allocates the right amount of memory for the target type ``C``,
in which the hierarchy of ``tp_new`` slot functions
will incrementally construct an instance of ``C`` from the arguments,
as they complete in reverse method-resolution order.

There is no parallel to the allocation step in Java source:
one cannot allocate an object separate from initialising it,
since an expression with the ``new`` keyword does both.
There *is* a JVM opcode (``new``)
that allocates an uninitialised object of the right size.
The source-level ``new`` generates this, and
an ``invokespecial`` for a target ``<init>()V`` method.
Allocation must happen in Java where object creation is initiated,
not in the ``tp_new`` of ``object`` as it can in CPython.


Examples guiding architectural choices
======================================

Example: extending a built-in
-----------------------------

Consider the following where we derive classes from ``list``
and then manipulate the ``__class__`` attribute of an instance.
What Java classes would make this possible?

..  code-block:: pycon

    >>> class MyList(list): pass
    ...
    >>> class MyList2(MyList): pass
    ...
    >>> m2 = MyList2()
    >>> m2.__class__ = MyList
    >>> m2.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses

The very possibility of giving ``m2`` the Python class ``MyList``
tells us that both must be implemented by the same Java class,
since the Java class of an object cannot be altered.
However,
we were unable to give ``m2`` the type ``list`` (a ``PyList`` in Java).
This allows the implementation of ``MyList`` and ``MyList2`` to be
a distinct Java class from ``PyList``.

It had better be *derived* from ``PyList``
so we can apply its methods to instances of the sub-classes.
One thing we would have to add to this sub-class is a dictionary,
since instances of ``MyList`` have one.
Let's call this class ``PyListDerived`` here, as in Jython 2.
(In practice, an inner class of each built-in seems a tidy solution.)

In the following diagram,
the Python classes in our example are connected to
the Java classes that implement their instances.

..  uml::
    :caption: Extending a Python built-in

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList <<Python>>
    MyList2 <<Python>>

    MyList2 -|> MyList
    MyList -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
    }

    PyListDerived -|> PyList
    PyList -|> Object

    MyList2 .. PyListDerived
    MyList .. PyListDerived
    list .. PyList


Example: extending with ``__slots__``
-------------------------------------

Another possibility for sub-classing is
to specify a ``__slots__`` class attribute.
This suppresses the instance dictionary that was
automatic in the previous example.
Instances are not class re-assignable from other derived types.
Consider:

..  code-block:: pycon

    >>> class MyListXY(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy = MyListXY()
    >>> mxy.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses
    >>> mxy.__class__ = MyList
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyList' object layout differs from
        'MyListXY'
    >>> m2.__class__ = MyListXY
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListXY' object layout differs from
        'MyList'

However,
they are class re-assignable from other derived classes,
provided the "layout" matches,
i.e. the slots have exactly the same names in order and number,
and there is (or isn't) an instance dictionary in both.

..  code-block:: pycon

    >>> class MyListXY2(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy.__class__ = MyListXY2
    >>> class MyListAB(list):
    ...     __slots__ = ('a', 'b')
    ...
    >>> mxy.__class__ = MyListAB
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListAB' object layout differs from
        'MyListXY2'

The possibility of giving ``mxy`` class ``MyListXY2``
tells us that both must be implemented by the same Java class.

In fact it is possible to derive again from a slotted class,
in such a way that it gains an instance dictionary,
or to add ``__slots__`` to a base class that has a dictionary.
(The purpose of ``__slots__`` in Python is
to save the space an instance dictionary occupies,
an advantage lost when the ideas are mixed,
but it must still work as expected.)
Instances of all these types may have their class re-assigned,
provided the constraint on ``__slots__`` is also met.

..  code-block:: pycon

    >>> class MyListMix(MyList2, MyListXY): pass
    ...
    >>> mix = MyListMix()
    >>> mix.a = 1
    >>> mix.__slots__
    ('x', 'y')

To support ``__slots__`` and instance dictionaries in these combinations,
we add a ``slots`` member to ``PyListDerived``.

..  uml::
    :caption: Extending a Python built-in (supporting ``__slots__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList2 <<Python>>
    MyListXY <<Python>>
    MyListMix <<Python>>

    MyListMix -|> MyListXY
    MyListMix -|> MyList2
    MyList2 -|> list
    MyListXY -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
        slots : PyObject[]
    }

    PyListDerived -|> PyList
    PyList -|> ArrayList
    ArrayList -|> Object

    MyListMix .. PyListDerived
    MyListXY .. PyListDerived
    MyList2 .. PyListDerived
    list .. PyList

We have shown the slots implemented as an array,
which is the approach Jython 2 takes.
The dictionary of the type contains entries for "x" and "y",
that index the ``slots`` array in the instance.
Another possibility is to create a new type with fields "x" and "y",
but this requires careful book-keeping to ensure ``MyListXY2``
gets the same implementation class.


Example: extending with custom ``__new__``
------------------------------------------

Consider the case of a long inheritance chain (from ``list`` again),
including one class that customises ``__new__``:

..  code-block:: python

    class L1(list): pass

    class L2(L1):
        def __new__(c, *a, **k):
            obj = super(L2, c).__new__(c, *a, **k)
            obj.args = a
            return obj

    class L3(L2): pass

    x = L3("hello")

After running that script, we may examine what we created

..  code-block:: python

    >>> x
    ['h', 'e', 'l', 'l', 'o']
    >>> x.args
    ('hello',)

The definitions result in an MRO for ``L3`` of
``('L3', 'L2', 'L1', 'list', 'object')``.
The construction of ``x`` calls ``L2.__new__``.
Each class in the MRO gets its turn to customise the object.
We can illustrate how classes in Python are realised by objects in Java
in the following (somewhat abusive UML) diagram,
showing the Java ``PyType`` objects that implement
the Python classes in the discussion:

..  uml::
    :caption: Representing a Python MRO (including ``__new__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    L1 <<Python>>
    class L2 <<Python>> {
        {method} __new__(c, *a, **k)
    }
    L3 <<Python>>

    list -|> object
    L1 -|> list
    L2 -|> L1
    L3 -|> L2

    object "<u>:PyType</u>" as Tobject {
        name = "object"
    }

    object "<u>:PyType</u>" as Tlist {
        name = "list"
    }

    object "<u>:PyType</u>" as TL1 {
        name = "L1"
    }

    object "<u>:PyType</u>" as TL2 {
        name = "L2"
    }

    object "<u>:PyType</u>" as TL3 {
        name = "L3"
    }

    object "<u>:PyFunction</u>" as L2new {
        {field} __name__ = "__new__"
    }

    object "<u>:PyJavaFunction</u>" as listnew {
        {field} __name__ = "__new__"
    }


    TL3 -> TL2
    TL2 -> TL1
    TL1 -> Tlist
    Tlist -> Tobject

    L3 .. TL3
    L2 .. TL2
    L1 .. TL1
    list .. Tlist
    object .. Tobject

    TL2 -down-> L2new
    Tlist -down-> listnew

The functions in the diagram are (Python) attributes of the type objects,
implemented by descriptors in the dictionary of each type,
in this case under the key ``"__new__"``.
This complexity has been elided from the diagram.

During the building of the structure depicted,
the ``tp_new`` slot of ``L1`` is copied from that of ``list``,
the ``tp_new`` slot of ``L2`` is filled with a wrapper on ``L2.__new__``,
and the ``tp_new`` slot of ``L3`` is copied from that of ``L2``.
The pre-existing ``list.__new__`` is a wrapper invoking ``list.tp_new``.
It sounds as if the chain up to ``list`` is broken between ``L2`` and ``L1``,
and it would be if ``L2.__new__`` were not to call a super ``__new__``.

Now, consider constructing a new object of Python type ``L3``,
by calling ``L3()``.
We know that this invokes the slot ``tp_call`` on ``type``
with ``L3`` as target,
and that in turn invokes the ``tp_new`` slot on ``L3`` with ``L3`` as target.
The ``tp_new`` slot on ``L3`` is a copy of that in ``L2``
and so the code for ``L2.__new__`` is executed (with ``c = L3``).

The expression ``super(L2, c).__new__``
resolves to the ``__new__`` attribute of ``list``, by inheritance,
and this is a wrapper that invokes the method ``PyList.tp_new``.
Recall that the first argument to ``tp_new`` (a ``PyType``) must be
the type actually under construction, in this case ``L3``.

A conclusion about inheritance
------------------------------

We conclude from the examples that the behaviour of ``PyList.tp_new`` must be
to construct a plain ``PyList`` when the type argument is ``list``,
but a ``PyListDerived`` when it is a Python sub-class of ``list``.
``PyListDerived`` is a Java sub-class of ``PyList``
that potentially has ``dict`` and ``slots`` members.
Whether the object actually has ``dict`` or ``slots`` members (or both)
is deducible from the definition,
and must be available from the type object when we construct instances.


.. _Python object creation sequence:
    https://eli.thegreenplace.net/2012/04/16/python-object-creation-sequence


