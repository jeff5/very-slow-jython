..  architecture/type-slots.rst

.. _type-slots:

Type Slots
**********

.. note::
    This section was extracted from :ref:`object-implementation`.
    There is an extended comparison with the CPython slots.

Choice of ``MethodHandle``
==========================

The special methods defined by the `Python Data Model`_,
like ``__neg__`` and ``__add__``,
are closely related to the byte codes to which
basic arithmetic and other operations are compiled.
Each Python type may make its own definition of these methods.
In some types, this definition can change at any time,
superseding one in the original definition of the type,
or a method may appear where the slot was previously empty
or vanish from where it previously existed.

The methods will be called quite frequently as code runs,
so it is useful to be able to reach them quickly.
Since there is a finite set of such methods,
it is possible to declare a field for each
in the ``Operations`` object for the implementation class.
Java provides the ``MethodHandle``
as a low-level feature that can achieve this purpose.

..  code-block:: java

    abstract class Operations {
        ...
        MethodHandle op_repr;
        MethodHandle op_hash;
        MethodHandle op_call;
        MethodHandle op_str;
        MethodHandle op_getattribute;
        ...

The signature of each method is known from the name,
although it is not apparent in the Java type of the handle.
Compare this with the descriptor for a method,
where we must store a complex description
in order to marshal arguments into a correct call.
In the context where we use a ``MethodHandle``,
we shall know the number and type of arguments,
and what the handle returns.
The field need only point us to the right implementation.
The slot of a method not defined is filled by a handle of the right type
that throws a special ``EmptyException``.

A ``MethodHandle`` may be invoked in much the same way as
a C function pointer in the implementation of CPython.
Here for example is how we invoke the ``__hash__`` special method
or discover that it is not defined.
We know that ``op_hash`` accepts one argument and returns an ``int``:

..  code-block:: java

    public class Abstract {
        ...
        public static int hash(Object v) throws TypeError, Throwable {
            try {
                return (int)Operations.of(v).op_hash.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw typeError("unhashable type: %s", v);
            }
        }

The Very Slow Jython Project is not primarily concerned with performance,
but it *is* interested how to apply correctly
those mechanisms that the JVM provides for dynamic languages.
Used well, that should lead to a high performance.

We could implement these slots in other ways,
using classes or lambda-expressions.
A strong motivation for the use of ``MethodHandle``\s is that
they work directly with ``invokedynamic`` call sites,
another pert of the JVM's dynamic language support.
Call sites support dynamic specialisation to the exact types
encountered at run-time.

A ``MethodHandle`` may have a tree-like internal structure,
by composition from other handles.
The optimisation built into a JVM understands ``MethodHandle`` trees,
and is able to transform them into efficient machine code.
When we come to generate code for the JVM,
we expect to output ``invokedynamic`` instructions,
and this is the code in which we seek maximum performance.

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html


Learning from CPython
=====================

Special Methods and the ``type`` Object in CPython
--------------------------------------------------

In the case of types defined in C,
the built-in and extension types,
the slot is assigned a pointer to the C implementation function,
either statically
or during ``type`` creation from a specification.
Built-ins use the static model,
so in the CPython source we often encounter a structure like this,
the type object ``int``:

..  code-block:: C

    PyTypeObject PyLong_Type = {
        PyVarObject_HEAD_INIT(&PyType_Type, 0)
        "int",                                      /* tp_name */
        offsetof(PyLongObject, ob_digit),           /* tp_basicsize */
        ...
        long_to_decimal_string,                     /* tp_repr */
        &long_as_number,                            /* tp_as_number */
        0,                                          /* tp_as_sequence */
        0,                                          /* tp_as_mapping */
        (hashfunc)long_hash,                        /* tp_hash */
        0,                                          /* tp_call */
        ...

The run-time invokes the function ``long_hash`` via the ``tp_hash`` slot
whenever it needs to compute the hash,
having first checked it is not ``0`` (null).
The slots ``tp_as_number``, ``tp_as_sequence``, and several others,
lead to optional sub-tables that work the same way.
``int`` points ``tp_as_number`` to a table it creates
which contains slots for the numeric operations.

CPython also creates a descriptor in the dictionary of the type
to wrap any slot implemented.
Those that are not implemented (are null) are absent from the dictionary.

..  code-block:: pycon

    >>> int.__dict__['__hash__']
    <slot wrapper '__hash__' of 'int' objects>
    >>> '__call__' in int.__dict__
    False

When a class is defined in Python,
the body is executed in an isolated namespace
and the functions defined by that execution become methods of the class.
When execution of the class body is complete,
CPython goes on to wrap each special method in a C function
that it posts to the corresponding slot in the ``type`` object,
the same place they would be if defined in C originally.

Thus, however a special method is implemented, in Python or C,
the slot is filled and there is an entry in the type dictionary.

Complications in CPython
------------------------

The account so far makes it appear that each slot
relates to a single special method name.
This is approximately correct and true for some slots.
In many other cases it is more complicated:
some slots involve multiple special methods,
and a few special methods affect more than one slot.
This complicates both the filling of the slot
from a method defined in Python,
and the synthesis of a Python method from a slot filled by C.
This is considered further in :ref:`potentially-problematic-slots`.

This difficulty cannot be resolved in CPython by changes to the slot lay-down
since the lay-down is public API
and many extensions rely on it.
Recent work to make the internals of ``PyTypeObject`` restricted API
does not hide the set of slot names.


Initialising Slots in CPython
-----------------------------

We will not try to explain this in full.
CPython's type slot design
may be appreciated through the ``slotdefs[]`` table in ``typeobject.c``.
It is built with the help of an ingenious set of macros.
In a relatively simple entry like this:

..  code-block:: C

        TPSLOT("__repr__", tp_repr, slot_tp_repr, wrap_unaryfunc,
               "__repr__($self, /)\n--\n\nReturn repr(self)."),

we may identify:

1.  the name of the special method,
2.  the name of the type slot it fills,
3.  a ``slot_*`` function used to dispatch a Python definition of the method,
4.  a ``wrap_*`` function used to present a filled slot in a descriptor,
    and finally
5.  a documentation string.

The ``slotdefs[]`` table is central to the process
of filling and using the ``type``.
Here is a much shortened version:

..  code-block:: C

    static slotdef slotdefs[] = {
        TPSLOT("__getattribute__", tp_getattro, slot_tp_getattr_hook,
               wrap_binaryfunc,
               "__getattribute__($self, name, /)\n--\n\nReturn ... ."),
        TPSLOT("__getattr__", tp_getattro, slot_tp_getattr_hook, NULL, ""),
        TPSLOT("__setattr__", tp_setattro, slot_tp_setattro, wrap_setattr,
               "__setattr__($self, name, value, /)\n--\n\nReturn ... ."),
        TPSLOT("__delattr__", tp_setattro, slot_tp_setattro, wrap_delattr,
               "__delattr__($self, name, /)\n--\n\nReturn ... ."),
        TPSLOT("__repr__", tp_repr, slot_tp_repr, wrap_unaryfunc,
               "__repr__($self, /)\n--\n\nReturn repr(self)."),
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


Two complicating phenomena may be discerned from the table,
both known as "competition":

1.  A special method like ``__mul__`` or ``__len__`` is repeated, and
    names more than one slot (second argument to the macro).
    When Python calls ``T.__mul__`` on some type,
    which slot should the wrapper function invoke?
    To which slot does an operation in the interpreter (``*`` say) map?
2.  A single slot like ``tp_getattro`` or ``nb_multiply`` is repeated, and
    is the target of more than one special method.
    If we define both in Python,
    which special method should be called by the ``slot_*`` dispatcher
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
to sub-classes through the reflected methods
(for example ``__mul__`` and ``__rmul__``).
In this case,
both special methods are necessarily referenced in the abstract API
and in the slot functions,
where this rule must be implemented.

However, competition contributes to the run time complexity of:

1.  the abstract API implementation
    (``PyNumber_Multiply`` must also consult ``sq_repeat``);
2.  the functions synthesised to call methods defined in Python
    (here ``slot_nb_multiply`` must work out
    which of ``__mul__`` or ``__rmul__`` is overridden in Python);
    and
3.  processing the ``slotdefs[]`` table to create or update a type.

The problem does not just afflict multiplication,
but generates quite a few special cases.
We consider the full set in :ref:`potentially-problematic-slots`.


Parting Company
---------------

This is clearly a very complicated part of CPython,
but perhaps worse because it must maintain as C API
the layout and meaning of slots in a type object,
relied upon by C extensions.

We do not have this legacy, so there is an opportunity to simplify.
We must consider as definitive the documented data model
expressed in terms of the special methods,
that is, the Python API,
not the C API or the gymnastics CPython undertakes to satisfy both at once.
In particular, we shall aim for:

1.  A one-to-one relationship of slots to special methods in the data model
    (in those cases where there is a slot at all).
2.  Irreducible competition concentrated in the implementation of
    the abstract API methods (``Abstract.add``, etc.),
    keeping the ``MethodHandle`` that occupies the type slot simple.

At the same time,
the remaining complexity in the abstract API will have to be replicated
in the structure of the ``invokedynamic`` call site,
when we come to that stage:
less is better,
but also we hope to pay the price only when linking the site.

The Java implementation of slots,
while very important internally,
will not be public API.
We therefore have more freedom than CPython to align with the Python API
and tailor the implementation as described next.


..  _one-to-one-slot-principle:

One-to-One Principle
====================

As our thinking has evolved (in The Very Slow Jython Project),
we have formed the view that aligning with the data model methods
is preferable to repeating the choice of slots from CPython.
It is effectively what Jython 2 does in defining its ``PyObject``
to have those names as (virtual) methods.

There is a risk in doing so that something CPython achieves
cannot be reproduced in our preferred structure.
We mitigated this risk by
the consideration given it in :ref:`potentially-problematic-slots`.
At the time of writing,
the approach has succeeded in representing a range of methods
in a range of built in types,
but we have not tested it with types defined in Python.


Naming Special Method Implementations
-------------------------------------

The approach to Java implementation of an object differs from CPython,
where the names are only of local significance.
A built-in type in Java will define methods with the same names
as the special methods in Python.
They are found by this name
and exposed as slots and descriptors during type construction.

Each will usually be an instance method in the
implementation class of the type.
For example, in ``tuple`` (``PyTuple``) we find:

..  code-block:: java

    public class PyTuple extends AbstractList<Object>
            implements CraftedPyObject {
        // ...
        private int __len__() { return size(); }

In types with more than one Java implementation class,
at least one implementation will be a ``static`` method.
For example, in ``str`` (``PyUnicode``),
which accepts ``String`` as an implementation,
we find:

..  code-block:: java

    public class PyUnicode implements CraftedPyObject, PyDict.Key {

        private final int[] value;
        // ...
        private int __len__() { return value.length; }

        private static int __len__(String self) {
            return self.codePointCount(0, self.length());
        }

Note that methods are found irrespective of Java visibility
because the ``Exposer`` is given private access by the defining class.


Naming Type Slots
-----------------

We have chosen to align our type slot names to the special methods
from the Python data model,
rather than the existing slots of CPython.
Furthermore, this structure is flat:
there are no sub-structures for numeric or sequence types.

After reading enough CPython source,
something like ``tp_hash`` or ``nb_add`` "just looks like" a slot name,
so to preserve this visual cue we name ours ``op_xxxx``,
where ``op_`` denotes "operation" and
``xxxx`` is the middle of the "dunder-name" ``__xxxx__``.

.. csv-table:: Example Names for Type Slots
   :header: "Slot", "special method", "Closest CPython type slot"
   :widths: 10, 10, 20

    "``op_repr``", "``__repr__``", "``tp_repr``"
    "``op_sub``", "``__sub__``", "``nb_subtract``"
    "``op_rsub``", "``__rsub__``", "``nb_subtract``?"
    "``op_getattribute``", "``__getattribute__``", "``tp_getattro``"
    "``op_setattr``", "``__setattr__``", "``tp_setattro``"
    "``op_delattr``", "``__delattr__``", "``tp_setattro`` (null value)"
    "``op_get``", "``__get__``", "``tp_descr_get``"
    "``op_getitem``", "``__getitem__``", "``mp_subscript`` and ``sq_item``"

The ``op_*`` slots are all ``MethodHandle`` fields
of the class ``Operations``,
which is a base class of ``PyType``.

Each name is also the name of a structured constant
found in ``enum Slot``.
Each member of the ``enum Slot`` provides services for initialising
and interrogating the corresponding type slot.
A shortened version of the ``Slot`` enum is:

..  code-block:: java

    enum Slot {
        op_repr(Signature.UNARY),
        op_hash(Signature.LEN),
        op_call(Signature.CALL),
        op_str(Signature.UNARY),

        op_getattribute(Signature.GETATTR),
        op_getattr(Signature.GETATTR),
        op_setattr(Signature.SETATTR),
        op_delattr(Signature.DELATTR),

        op_lt(Signature.BINARY, "<"),
        op_le(Signature.BINARY, "<="),
        op_eq(Signature.BINARY, "=="),
        op_ne(Signature.BINARY, "!="),
        op_gt(Signature.BINARY, ">"),
        op_ge(Signature.BINARY, ">="),

        op_iter(Signature.UNARY),
        op_next(Signature.UNARY),

        op_radd(Signature.BINARY, "+"),
        op_rsub(Signature.BINARY, "-"),
        op_rmul(Signature.BINARY, "*"),
        op_rmod(Signature.BINARY, "%"),
        op_rdivmod(Signature.BINARY, "divmod()"),

        op_add(Signature.BINARY, "+", op_radd),
        op_sub(Signature.BINARY, "-", op_rsub),
        op_mul(Signature.BINARY, "*", op_rmul),
        op_mod(Signature.BINARY, "%", op_rmod),
        op_divmod(Signature.BINARY, "divmod()", op_rdivmod),

        op_neg(Signature.UNARY, "unary -"),

        op_contains(Signature.BINARY_PREDICATE);

        // ...

This is in some measure our equivalent of the CPython ``slotdef[]`` table.
The ``enum`` encapsulates a lot of behaviour (not shown).
What the CPython ``slotdef[]`` table achieves with its various macros,
we mostly do through the different constructors and methods on it,
or on the related ``enum Signature``.

The name of the slot in the ``Operations`` object
is the same as that of the ``enum`` constant.
There is no relationship as far as Java is concerned,
but by choosing the same name we do not have to specify it in the ``enum``.

The full story is in ``Slot.java`` and ``Operations.java``.


Flattening the Type Slot Table
------------------------------

We briefly noted above that our type slots are all simply
member fields directly in the class ``Operations``.

In CPython,
type slots common to most objects (e.g. ``tp_repr``)
are fields in the ``PyTypeObject``,
while others (for number, sequence and mapping protocols)
are in a second level table reached via a pointer that may be ``NULL``.
These are the ones with a prefix other than ``tp_``,
like ``nb_add``, ``sq_contains`` and ``mp_subscript``.

The motivation is surely to save space on type objects that do not need
the full set of slots.
It has led (perhaps avoidably) to some complexity in the abstract API,
through having to consult two slots defined by a single data model function,
one in each sub-table.
For example, ``PyObject_Size`` must try both ``sq_length`` and ``mp_length``.

A common idiom in the CPython source is something like:

..  code-block:: C

    m = o->ob_type->tp_as_mapping;
    if (m && m->mp_subscript) {
        PyObject *item = m->mp_subscript(o, key);
        return item;
    }
    return type_error("'%.200s' object is not subscriptable", o);

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
all the slots are fields directly in the ``Operations`` object.
This simplifies the code to create them,
and saves an indirection with each operation.
With the flattening of the type object,
and the trick of using ``EmptyException`` in place of a test,
the equivalent Java code is just:

..  code-block:: java

        try {
            Operations ops = Operations.of(o);
            return ops.op_getitem.invokeExact(o, key);
        } catch (EmptyException e) {
            throw typeError(NOT_SUBSCRIPTABLE, o);
        }


We shall not name *all* the fields of an ``Operations`` or  ``PyType``
with the ``op_`` prefix:
fields like ``name``, ``bases`` and ``mro`` are not slots in this sense,
even though they have a ``tp_`` prefix in CPython's type object.

Competition
-----------

We recognise two kinds of competition in CPython.

1.  One method name, two slots
    (e.g. ``__getitem__`` defining ``mp_subscript`` and ``sq_item``).
2.  One slot, two or more methods
    (e.g. ``nb_add`` defined by ``__add__`` and ``__radd__``.).

The one-to-one scheme eliminates both kinds of conflict.
Note that this doubles the number of slots occupied by
every binary operation,
since we need a distinct slot for the reflected version
(``op_radd`` as well as ``op_add``, etc.).
It means we have 6 slots in place of one ``tp_richcompare``.

The total number of slots in ``Operations`` at the time of writing is 74.
If the size penalty of the flattened scheme proves intolerable,
it would be possible to roll back the flattening idea,
but without re-introducing competition.
A new two-level scheme could accommodate (for example)
the approximately 40 purely numerical slots in their own sub-table.
The effect would be invisible in the public API.

The one-to-one approach is obviously a feasible one
and most of the abstract API was easily re-written to use it.
The question is whether this approach somehow results in
behaviour different from CPython and incorrect.



..  _potentially-problematic-slots:

Potentially Problematic Slots in CPython
========================================

The purpose of this section is
to go through all the slots in a CPython type object
that are not one-to-one with a special method.
Such slots might be a problem for the :ref:`one-to-one-slot-principle`,
either because our simplification leads to a different behaviour,
or because code in CPython that uses the slot,
for example when implementing the abstract API,
becomes more difficult to port.
We expect, in fact, that the code will become clearer in most cases.


Slots not a Problem
-------------------

The slots for many unary numerical operations,
and some slots that have relatively complex signatures (like ``__call__``)
are one-to-one a single special method.
We still have to get a couple of things right.

When the special method is defined in Python,
the descriptor for it is a general callable.
It should be possible to create a ``MethodHandle``
with the right signature for the slot
that calls the descriptor with those arguments.
If the alleged special method is defined incorrectly,
it blames the caller when invoked:

..  code-block:: pycon

    >>> class C(int):
    ...     def __neg__(self, u):
    ...         return u - self
    ...
    >>> -C(43)
    Traceback (most recent call last):
      File "<pyshell#19>", line 1, in <module>
        -C(43)
    TypeError: C.__neg__() missing 1 required positional argument: 'u'

When the special method is defined in Java,
the exposure process derives a ``MethodHandle``
directly for the defining method,
and a descriptor that wraps it for the dictionary of the type.
Note that the slot can safely contain that handle
only if the described method is applicable to the implementation.
If a method has the name of a special method but the wrong signature,
it is not recognised as a definition.


Binary Operations
-----------------

For each binary operation ``OP`` the data model defines two special methods
with signature ``__OP__(self, right)`` and ``__rOP__(self, left)``,
but CPython has them compete for the same slot in a type object.
For example,
when calling either ``__sub__`` or ``__rsub__`` on a ``float``,
CPython delivers arguments to this function in ``floatobject.c``:

..  code-block:: C

    static PyObject *
    float_sub(PyObject *v, PyObject *w)
    {
        double a,b;
        CONVERT_TO_DOUBLE(v, a);
        CONVERT_TO_DOUBLE(w, b);
        a = a - b;
        return PyFloat_FromDouble(a);
    }

This will happen most frequently in an expression like ``5 - 0.7``,
where the abstract API ``PyNumber_Subtract``
tries the equivalent of ``int.__sub__(5, 0.7)`` first,
then the equivalent of ``float.__rsub__(0.7, 5)``,
which becomes the call ``float_sub(5, 0.7)``.

When we call ``self.__rsub__(left)`` in Python
(a less frequent case than evaluating an expression)
the wrapper descriptor swaps the arguments
so that it calls ``float_sub(left, self)``.
See ``wrap_binaryfunc_r`` in ``typeobject.c``.

Because of the competition and the swapping of arguments,
the slot functions for the binary operations of built-in types
in CPython are not guaranteed the type of *either* argument:
they must test and convert the type of both.
In ``floatobject.c`` the conversion macro is ``CONVERT_TO_DOUBLE``.
An early ``return`` is hidden inside the macro
and returns ``Py_NotImplemented`` *from the function* if conversion fails.

When methods are defined in Python,
CPython must fill the type slot with a ``slot_nb_subtract`` function
(see the ``SLOT1BIN`` macro in ``typeobject.c``),
that will try to dispatch ``__sub__`` and ``__rsub__``,
looking them up by name on the respective left and right objects presented.
This is necessary, it seems,
even though ``PyNumber_Subtract`` already contains very similar logic.

We will follow Jython 2 in making ``__OP__`` and ``__rOP__`` separate slots.
Sticking with the example of ``float`` subtraction,
the Java implementation consists of two methods ``__sub__`` and ``__rsub__``,
which for the ``Double`` realisation of ``float`` look like this:

..  code-block:: java

    class PyFloatMethods {
        // ...
        static Object __sub__(Double v, Object w) {
            try {
                return v - toDouble(w);
            } catch (NoConversion e) {
                return Py.NotImplemented;
            }
        }

        static Object __rsub__(Double w, Object v) {
            try {
                return toDouble(v) - w;
            } catch (NoConversion e) {
                return Py.NotImplemented;
            }
        }

In a Java built-in we take advantage of the known type of ``self``
to avoid the conversion.
Here that is ``toDouble``,
which throws ``NoConversion`` if it cannot convert its argument.

There is an ``Operation``\s object for ``Double`` and one for ``PyFloat``,
since these are the two implementations of ``float``,
each containing slots ``op_sub`` and ``op_rsub``.
Each slot is set during type construction to
the handle of the corresponding Java implementation method.

We have implemented this successfully for binary operations in
several built-in types defined in Java
(but not types defined in Python).

..  _tp_richcompare:

``tp_richcompare``
------------------

In CPython:

* ``tp_richcompare`` defines ``__lt__``, ``__le__``,
  ``__eq__``, ``__ne__``, ``__gt__`` and ``__ge__``.
* When defined in C, a single function with signature
  ``PyObject *(*richcmpfunc) (PyObject *, PyObject *, int)``
  implements all six forms and fills the slot ``tp_richcompare``.
  The third parameter communicates which comparison to perform.
* Calling any of these slot wrapper descriptors from Python leads eventually
  to ``tp_richcompare`` with a specific third argument.
  For example, ``x.__le__(y)`` actually wraps ``richcmp_le(x, y)``
  which calls ``tp_richcompare(x, y, Py_LE)``.
* When any of the methods is defined in Python
  the type slot will contain ``slot_tp_richcompare``,
  which finds the descriptor corresponding to the third argument.
  If the particular special method is not overridden in Python,
  this descriptor is for an inherited implementation.
* In the byte code interpreter,
  a single ``COMPARE_OP`` opcode covers the six comparisons,
  and also ``is``, ``is not``, ``in``, ``not in``,
  and exception matching to support ``try-except``.
* The abstract API includes ``PyObject_RichCompare`` and
  ``PyObject_RichCompareBool``
  that wrap this slot and take the (comparison) operation
  as an argument.

The relationship of ``tp_richcompare`` to the implementing methods,
and the extension of ``COMPARE_OP`` beyond that slot's range,
are somewhat unlike other cases but present no absolute difficulty.
The :ref:`one-to-one-slot-principle` leads us to:

* Define for a type implemented in Java each of
  ``__lt__``, ``__le__``, ``__eq__``, ``__ne__``, ``__gt__`` and ``__ge__``.
  If the trade seems good for some type,
  we may easily create these methods as a wrapper on a 3-way comparison.
* Define a type slot for each comparison named
  ``op_lt``, ``op_le``,  ``op_eq``, ``op_ne``, ``op_gt`` and ``op_ge``,
  and fill them by the usual rule.
* Define a type slot for ``__contains__`` named ``op_contains``.
* Implement ``COMPARE_OP`` to invoke one of the comparison type slots,
  ``op_contains`` (for ``in`` and ``not in``),
  object equality (for ``is`` and ``is not``),
  or exeception matching,
  after the pattern in CPython ``ceval.c``.

At the time of this writing we have implemented this successfully,
except for exception matching.


.. _getattribute-and-getattr-2:

``__getattribute__`` and ``__getattr__``
----------------------------------------

Two data model methods combine in the abstract API
to produce the required semantics.

In CPython:

* ``__getattribute__`` and ``__getattr__`` compete to define
  apparently competing slots ``tp_getattro`` and ``tp_getattr``.
* The abstract API ``PyObject_GetAttr`` calls the slot ``tp_getattro`` first
  and ``tp_getattr`` only if that is unsuccessful.
* These methods and slots are handled as a special case in type construction,
  unlike other slots
  where the process is driven by the ``slotdefs[]`` table alone.
* When attribute access is overridden in Python,
  the slot dispatch function ``slot_tp_getattr_hook``
  tries ``__getattribute__`` first and ``__getattr__`` only if that fails,
  similarly to the abstract API.
* ``slot_tp_getattr_hook`` replaces itself with a simplified version
  calling only ``__getattribute__`` if ``__getattr__`` is not defined.

Generally, there is a lot of optimisation in attribute access.

Our approach in Java is to look past these optimisations
and expose the methods uniformly.
``op_getattribute`` is a handle to ``__getattribute__``,
``op_getattr`` is a handle to ``__getattr__``.
This design removes the apparent competition in CPython
to define the type slots.
We assume the entanglement of the two *type slots*
is a side effect of the optimisations,
rather than necessary to semantics we should reproduce.

In the abstract API,
we try the type slots in the order the `Python Data Model`_
specifies for the data model methods.
We can add optimisations suitable to Java later if needed.

It is rare for a built-in to implement ``__getattr__``,
however, the expected behaviour of ``__getattribute__`` is quite complex.
Attribute access is amply discussed in its own chapter :ref:`Attributes`.


..  _recurring-pattern-slot-competition:

Recurring Pattern in Slot Competition
-------------------------------------

In a survey of all type slots we find a pattern often recurs:

* There two type slots ``s`` and ``t`` with similar purpose
  (for example ``nb_add`` and ``sq_concat``)
  but they may belong to different protocols.
  (In this example they are the number protocol and the sequence protocol.)
* There is one method ``m`` in the data model
  that corresponds to both in the ``slotdefs[]`` table.
  (In the example ``__add__`` labels both ``nb_add`` and ``sq_concat``.)
* There is an abstract API function ``S``
  that implements a source construct or opcode in ``ceval.c``.
  (For example ``PyNumber_Add`` implements addition
  by supporting the ``BINARY_OP (+)`` opcode.)
* There may be an alternate abstract API function ``T``, or more than one,
  with slightly different behaviour or argument types,
  for the convenience of those implementing CPython or C extensions.
  The alternates do not generally implement a source construct or opcode.
  (For example ``PySequence_Concat`` is not used in ``ceval.c``.)

The behaviour of CPython is then:

* When either type slot is filled by a function defined in C,
  that type slot defines ``m`` through a slot wrapper descriptor,
  based on a ``wrap_*`` function.
* When both ``s`` and ``t`` are filled, one (``s`` say) takes precedence,
  by appearing first in the ``slotdefs[]`` table.
  (For example,
  ``nb_add`` takes precedence over ``sq_concat`` in defining ``__add__``.)
* When ``m`` is defined in Python, only ``s`` is filled with
  a pointer to a ``slot_*`` function referencing ``m``.
  No data model method fills ``t``: it is empty in types defined in Python.
  (When ``__add__`` is defined for a type in Python,
  it fills ``nb_add`` but ``sq_concat`` is empty.)
* The abstract API function ``S`` calls slot ``s``, if it is filled,
  and then it tries ``t``.
  (For example, ``PyNumber_Add`` tries ``nb_add`` before ``sq_concat``.)
  Either slot may provide the behaviour but the API function tries them
  in the same order of precedence by which they define ``m``.
* If an alternate abstract API function ``T`` exists,
  it may differ in that it calls slot ``t``, if it is filled,
  then tries ``s``.
  (For example, ``sq_concat`` precedes ``nb_add`` in ``PySequence_Concat``.)
  Again, either slot may provide the behaviour.

We could say in brief that a type defined in C
fills one of the slots ``s`` and ``t`` that compete to define ``m``.
The abstract API ``S`` calls whichever one is filled.
A type defined in Python defines ``m`` and
CPython inserts it at ``s`` (only).

Here is a table of the instances that fit the pattern,
to which we expect to apply the generic single-slot solution in Jython.
Type slot names are those in CPython.

.. csv-table:: Competing type slots with a single Data Model method
   :header: "Slot", "Other slot", "Data model", "Abstract API", "Alternate API"
            "``s``", "``t``", "``m``", "``S``", "``T``"
   :widths: 15, 15, 10, 20, 20

    "``nb_add``", "``sq_concat``", "``__add__``", "``PyNumber_Add``", "``PySequence_Concat``"
    "``nb_inplace_add``", "``sq_inplace_concat``", "``__iadd__``", "``PyNumber_InPlaceAdd``", "``PySequence_Concat``"
    "``nb_multiply``", "``sq_repeat``", "``__mul__``", "``PyNumber_InPlaceMultiply``", "``PySequence_InPlaceRepeat``"
    "``nb_inplace_multiply``", "``sq_inplace_repeat``", "``__imul__``", "``PyNumber_Multiply``", "``PySequence_Repeat``"
    "``sq_length``", "``mp_length``", "``__len__``", "``PyObject_Size``", "``PyMapping_Size``"
    "``mp_subscript``", "``sq_item``", "``__getitem__``", "``PyObject_GetItem``", "``PySequence_GetItem``"
    "``mp_ass_subscript``", "``sq_ass_item``", "``__setitem__``", "``PyObject_SetItem``", "``PySequence_SetItem``"
    "``tp_setattro``", "``tp_setattr``", "``__setattr__``", "``PyObject_SetAttr``"

In all of these cases,
a single type slot is enough to capture the critical relationship
between the method in the `Python Data Model`_ and the source construct.
E.g. the data model states that
``__add__`` and ``__radd__`` implement the operation ``+``,
and that
``__getitem__`` defines the result of ``d["spam"]`` and ``"hello"[:4]``.

Effectively in Jython we have only the slots in column ``s``,
where the slot is renamed to match the data model method name.
There is no need for logic in the abstract API to choose between two slots
when we call these methods from the word code interpreter.
The same slot is filled with a handle to the identically-named method
whether defined in Python or Java.

This pattern applies where slots exist that are effectively alternate
expressions of the same data model method.
Other API methods consult multiple type slots,
as in :ref:`getattribute-and-getattr-2`,
because of Python behaviour specified to involve
more than one data model method.

Not everything about these slot pairs is fully described by this pattern.
It is worth visiting these cases for their remaining idiosyncrasies.


..  _set-and-del:

``__set*__`` and ``__del*__``
-----------------------------

In CPython:

* ``__setattr__`` and ``__delattr__`` compete to define
  slot ``tp_setattro``.
* ``tp_setattr`` is deprecated.
* ``__setitem__`` and ``__delitem__`` compete to define
  slot ``mp_ass_subscript`` only.
* ``__set__`` and ``__delete__`` (on a descriptor)
  compete to define ``tp_descr_set``.
* The competition is resolved in CPython
  by the convention that a value of ``NULL`` is a request for deletion.

  * A wrapper function exposed as ``__del*__`` calls
    ``tp_set*`` with a ``NULL`` value to set.
  * A ``slot_tp_set*`` dispatcher
    calls either ``__set*__`` or ``__del*__``
    according to the nullity of its value argument.

Where pairs of data model methods
(e.g. ``__setattr__`` and ``__delattr__``)
compete to define each type slot from Python,
there is no ambiguity  since
the ``NULL``-argument convention identifies the correct method.
Where there is also more than one type slot involved,
the :ref:`recurring-pattern-slot-competition` applies,
extended by this convention.

In Jython,
the :ref:`one-to-one-slot-principle` makes the ``NULL``-argument convention unnecessary,
since we always know whether we are setting or deleting.
We must take care to make the distinction
where code adapted from CPython may be covering both possibilities
without mentioning it in comments.


..  _sq_concat-and-nb_add:

``sq_concat`` and ``nb_add``
----------------------------

These slots match the :ref:`recurring-pattern-slot-competition`,
except that since ``__add__`` is a binary operation,
we should also consider how the reflected operation ``__radd__`` is involved.

Under the :ref:`one-to-one-slot-principle`,
we create ``op_add`` and ``op_radd``,
from ``__add__`` and ``__radd__`` respectively,
whether they are defined in Java or in Python.
When we call Abstract API ``PyNumber.add``,
it makes the choice between ``op_add`` and ``op_radd``
with the semantics defined by Python.

In CPython,
when we call ``PyNumber_Add``,
it tries ``nb_add`` and then ``sq_concat`` as we have described.
In some combinations of types,
this means it tries ``nb_add`` on the left and right operands,
and then ``sq_concat`` on the left and right operands.
For these combinations of types,
this arbitrarily places ``__radd__`` in the right operand (in slot ``nb_add``)
ahead of ``__add__`` in the left (in slot ``sq_concat``),
contrary to the semantics of Python.
It gives rise to the :ref:`bug-arithmetic-sequence-cpython`.

Jython will differ subtly from CPython because of this.
Unfortunately, some code in widespread use
incorrectly relies on this buggy behaviour.


..  _sq_repeat-and-nb_multiply:

``sq_repeat``, ``nb_multiply``
------------------------------

The situation is an exact counterpart
to :ref:`sq_concat-and-nb_add`,
but with multiplication and repetition as subject operations
in place of addition and concatenation.

Here again the :ref:`bug-arithmetic-sequence-cpython` is a problem.


..  _sq_inplace_concat-and-nb_inplace_add:

``sq_inplace_concat`` and ``nb_inplace_add``
--------------------------------------------

The situation is quite like that for :ref:`sq_concat-and-nb_add`.
The slots match the :ref:`recurring-pattern-slot-competition`,
except that in-place operations in the abstract API
fall back to their binary counterparts,
which in turn consult left and right arguments.

In CPython:

* Abstract API ``PyNumber_InPlaceAdd``
  tries ``nb_inplace_add`` on the left argument,
  then falls back to ``nb_add`` on left and right arguments.
  If these return ``NotImplemented``,
  it then tries ``sq_inplace_concat`` and ``sq_concat`` on the left.
* The alternate abstract API ``PySequence_InPlaceConcat`` begins with
  the concatenation part of this logic (in-place and binary on the left)
  and then tries the addition (in-place and binary left and right).

We observe that ``PyNumber_InPlaceAdd``
goes prematurely to the right operand's ``nb_add``,
before the left's ``sq_inplace_concat`` and ``sq_concat``.
It reproduces a version of the :ref:`bug-arithmetic-sequence-cpython`.

The :ref:`one-to-one-slot-principle` leads us to the following in Jython:

* ``__iadd__`` defines ``op_iadd``.
* ``PyNumber.inPlaceAdd`` calls ``op_iadd``, ``op_add`` and ``op_radd``.
  This puts the data model method calls in the correct order.


``sq_inplace_repeat`` and ``nb_inplace_mul``
--------------------------------------------

The situation is an exact counterpart
to :ref:`sq_inplace_concat-and-nb_inplace_add`,
but with multiplication and repetition as subjects operations,
in place of addition and concatenation.

This similarity includes the :ref:`bug-arithmetic-sequence-cpython`.



..  _bug-arithmetic-sequence-cpython:

Bug involving Arithmetic and Sequence Slots in CPython
------------------------------------------------------

We have referenced several times an `operand precedence bug`_ in CPython
where a binary operation (a special method pair) defines multiple slots.
In practice the affected methods are:

* ``__add__`` and ``__radd__``,
  which fill ``nb_add`` and may wrap ``sq_concat``.
* ``__mul__`` and ``__rmul__``.
  which fill ``nb_multiply`` and may wrap ``sq_repeat``.
* in-place variants of these operators
  (``__iadd__``, ``__imul__``, ``__radd__``, ``__rmul__``),
  which have their own slots and fall back to the affected binary operations.

The effect is exhibited when we use the corresponding operators
in program text (``+``, ``*``, ``+=``, ``*=``),
implemented through the abstract object API.
A `discussion of the operand precedence bug`_ identifies
the root of the problem as the way the abstract implementation
of the binary operation tries to treat both arguments as numeric first
and as sequences second.

Taking addition as the example,
when we call ``PyNumber_Add``,
CPython tries ``nb_add`` on the left and right operands
and then ``sq_concat`` on the left and right operands.
When the left operand type fills ``sq_concat``
(it must be a built-in),
but the right operand type fills ``nb_add``
(it could be in Python),
this arbitrarily places ``__radd__`` in the right operand
ahead of ``__add__`` in the left,
contrary to the semantics of Python.

A simple illustration is:

..  code-block:: pycon

    >>> class C(tuple):
    ...     def __radd__(w, v):
    ...         return 42
    ...
    >>> [1,] + C((2,3,4))
    42

In fact there is a ``list.__add__``,
and it raises a ``TypeError`` on any right argument that is not a ``list``.
The C implementation stores it in the ``sq_concat`` slot,
which is not tried until after the ``nb_add`` of ``C``,
leading to a call of ``C.__radd__``.
(Note that ``C`` is not a sub-class of ``list``.)

Several downstream libraries depend on this bug,
relying on the (faulty) ordering
to get their ``nb_add`` or ``nb_multiply`` called first.
This is only possible in the C implementation of their objects.
(PyPy has deliberately reproduced the buggy behaviour
so that it can support these C extensions.)

In Jython,
the the :ref:`one-to-one-slot-principle` leads us to have a single pair of
``op_add`` and ``op_radd`` slots,
where we try ``op_add`` on the left first.
We therefore have
"a `proper 1-1 relationship`_ between syntax operators and methods,
as there is for Python-coded classes" and correct Python semantics.


..  _operand precedence bug:
    https://github.com/python/cpython/issues/55686
..  _discussion of the operand precedence bug:
    https://mail.python.org/pipermail/python-dev/2015-May/140006.html
..  _proper 1-1 relationship:
    https://github.com/python/cpython/issues/55686#issuecomment-1093538591


Initialisation of Slots
=======================

..  note:: This section was in :ref:`object-implementation`.
    It needs refreshing from chapter :ref:`Operations-builtin`
    (or somewhere)
    and corresponding development of slots from Python types
    when the work and its narrative are available.

    We may not need a blow-by-blow account of
    the actions of the ``Exposer``\s.
    What is enough for *architecture*,
    after which the code for the time being can speak for itself?

From Definitions in Java
------------------------

We have established a pattern in ``rt2`` (``evo2`` onwards)
whereby each ``PyType`` contains named ``MethodHandle`` fields,
pointing to the implementation of the "slot methods" for that type.
The design, using a system of Java ``enum``\s denoting the slots,
has worked smoothly in the definition of a wide range of slot types.

The handle in a given slot has to have
a signature characteristic of the slot.

Where a slot defined in a type corresponds to a special method,
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
the methods are ``static`` members of the implementation class of the type,
or of a Java super-type,
with a signature correct for the slot.
They could, without a significant change to the framework,
be made instance methods of that class.

We took a step towards instance methods in ``evo3``,
when it became possible for an argument to the slot method
to adapt to the implementing type.
The method handle in slot ``nb_negative``
has ``MethodType`` ``(O)O`` as it must,
but the implementing method has signature ``(S)O``,
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
Jython 2 approach to slot methods.


From Definitions in Python [untested]
-------------------------------------

A function defined in a class becomes a method of that class,
that is, it creates a method descriptor in the dictionary of the type.
This is true irrespective of the number or the names of the arguments.
We consider here how methods with the reserved names
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
because assignment to a special method in a type
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
do not allow (re)definition of special methods,
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
then the insertion of a handle for the slot method.
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

When a special method is re-defined in a type,
affected slots in the sub-types of that type are re-computed.
This is why a re-definition is visible in derived types.
