..  generated-code/refactor-to-evo4.rst

Refactoring for ``evo4``
########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo4``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo4``.

Another Re-work of Core Types
*****************************

Motivation
==========

In the coming sections we address attribute access,
including attributes that are functions (methods).
This cannot be done satisfactorily
without much stronger support for inheritance than ``evo3`` provides.
This means it is already time for an ``evo4``.

The need for attributes will lead us to give each type object a dictionary,
to create descriptors that may be entered into that dictionary,
and to implement the MRO along which
the search for any attribute is made.
Thus, quite a lot of core apparatus will be revisited.

Descriptors must be able to represent
attributes defined in either Java or Python:
type slots (still ``MethodHandle``\s) will be filled from these descriptors.
Descriptors also cause us to consider how we will use ``VarHandle``\s.

There are a lot of different descriptor types,
each a new type of Python object.
This puts a strain on our ability in ``evo3``
efficiently to code Java implementations of Python types.
We will revisit how slots are filled,
in particular we shall switch to using instance methods
to define special functions.


Scope
=====

Aspects of the design to be revisited in ``evo4`` (not just of ``PyType``)
are:

* Alignment of slots to special function (see :ref:`type-slots`).
* Names of slots: renaming to the pattern ``op_add``, ``op_setattr``, etc..
* Process for initialisation of slots (``op_add``, ``op_call`` etc.).
* Inheritance of slots not simply by copying.

These features will be added:

* The dictionary of the type.
* Descriptor protocol. (Extensively described in :ref:`Descriptors`.)
* The actual Python type is not determined statically by the Java class.
* The ``op_new`` slot and its implementation for several types.
* The ``op_getattribute``, ``op_getattr``, ``op_setattr`` and  ``op_delattr``
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

We wish to support types defined in Python
and a fairly complete model of inheritance.
Types defined in Python define slots through special (or  "dunder") methods
(``__len__``, ``__add__``, ``__call__``, etc.),
which are entered in the dictionary of the ``type``
when the class is defined.

The ultimate approach will be described in
the architecture section :ref:`type-slots`.
Here we want to mention notable changes of approach
occurring between ``evo3`` and ``evo4``.


A Java Approach the Slot Table
==============================

The function pointers in a CPython built-in type
are assigned either statically,
or during ``type`` creation from a specification.

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


Defining Comparison Operations
==============================

We are abandoning ``tp_richcompare`` in favour of ``op_lt``, ``op_le``, etc..
A drawback is that that where types were only asked to implement one method,
they now have to implement six quite similar methods.
This is why CPython's rich comparison approach is attractive.
In particular, there is often a handy ``Comparable.compareTo`` method
returning ``-1``, ``0`` or ``+1`` meaning less, equal or more.

We can provide some support, and a useful pattern.
We can attach code that might have been a support method,
directly to the operation (the ``enum Comparison``),
since Java ``enum`` members are full-blown singleton objects
or even sub-classes (where necessary).
In our case,
each member is initialised knowing which ``Slot`` it corresponds to,
and how to convert a 3-way comparison result:

..  code-block:: java

    enum Comparison {

        /** The {@code __lt__} operation. */
        LT("<", Slot.op_lt) {

            @Override
            PyBool toBool(int c) { return c < 0 ? Py.True : Py.False; }
        },

We define a method ``Comparison.apply(PyObject, PyObject)``,
so that the implementation of ``COMPARE_OP`` is no more than:

..  code-block:: java

    class CPythonFrame extends PyFrame {
        // ...
        PyObject eval() {
                        // ...
                        case Opcode.COMPARE_OP:
                            w = valuestack[--sp]; // POP
                            v = valuestack[sp - 1]; // TOP
                            valuestack[sp - 1] = // SET_TOP
                                    Comparison.from(oparg).apply(v, w);
                            break;

The complexity entailed by the "big six" binary operations,
trying left and right objects and deferring to sub-classes,
does not disappear:
it is all inside ``Comparison.apply``,
which uses the slot we gave the constructor,
and built-in knowledge of the "swapped" operation.
Outside the big six operations,
other implementations override ``apply`` appropriately for themselves.

We hope to gain as we replace CPython's nested switch statements
by a single virtual method call.
As a side benefit, ``Abstract.richCompare`` works for all operation types.

The use of ``Comparison.toBool(int)``, glimpsed earlier,
is as a wrapper on the result provided by Java ``Comparable.compareTo``.
We may minimise code duplication by this pattern (here in ``PyUnicode``):

..  code-block:: java

    class PyUnicode implements PySequence, Comparable<PyUnicode> {
        // ...
        @Override
        public int compareTo(PyUnicode o) {
            return value.compareTo(o.value);
        }
        // ...
        static PyObject __lt__(PyUnicode v, PyObject w) {
            return v.cmp(w, Comparison.LT);
        }
        static PyObject __le__(PyUnicode v, PyObject w) {
            return v.cmp(w, Comparison.LE);
        }
        // and so on ...
        private PyObject cmp(PyObject w, Comparison op) {
            if (w instanceof PyUnicode) {
                return op.toBool(compareTo((PyUnicode) w));
            } else {
                return Py.NotImplemented;
            }
        }
    }



Defining ``__getitem__`` and similar
====================================

In ``evo3`` we have an abstract API method ``getItem(PyObject, PyObject)``.
It corresponds to CPython ``PyObject_GetItem``,
and is the implementation of ``Opcode.BINARY_SUBSCR``.
Following CPython, it tries the ``mp_subscript`` slot first.
If that is not defined, the object appears to be a sequence,
and the it can be converted to a native ``int``,
the method delegates to ``getItem(PyObject, int)``.

``getItem(PyObject, int)`` deals with end-relative addressing
(a negative index) and calls the ``sq_item`` slot.
In code where an integer index is available, this is a good option.
Meanwhile, the implementation of ``mp_subscript`` in a sequence type,
must itself validate the integer nature of the index
and deal with end-relative addressing where it is negative.

..  code-block:: java

    class Abstract {
        // ...
        static PyObject getItem(PyObject o, PyObject key) throws Throwable {
            PyType oType = o.getType();

            try {
                return (PyObject) oType.mp_subscript.invokeExact(o, key);
            } catch (EmptyException e) {}

            if (Slot.sq_item.isDefinedFor(oType)) {
                // For a sequence (only), key must have index-like type
                if (Slot.nb_index.isDefinedFor(key.getType())) {
                    int k = Number.asSize(key, IndexError::new);
                    return Sequence.getItem(o, k);
                } else
                    throw typeError(MUST_BE_INT_NOT, key);
            } else
                throw typeError(NOT_SUBSCRIPTABLE, o);
        }


    class Sequence extends Abstract {
        // ...
        static PyObject getItem(PyObject s, int i) throws Throwable {
            PyType sType = s.getType();

            if (i < 0) {
                // Index from the end of the sequence (if it has one)
                try {
                    i += (int) sType.sq_length.invokeExact(s);
                } catch (EmptyException e) {}
            }

            try {
                return (PyObject) sType.sq_item.invokeExact(s, i);
            } catch (EmptyException e) {}

            if (Slot.mp_subscript.isDefinedFor(sType))
                // Caller should have tried Abstract.getItem
                throw typeError(NOT_SEQUENCE, s);
            throw typeError(NOT_INDEXING, s);
        }

What we have to say here about ``getItem()`` calling (``__getitem__``)
applies to ``setItem()`` and ``delItem()``
(calling ``__setitem__`` and ``__delitem__`` respectively).

Almost no built-in types (in CPython) omit a definition for ``mp_subscript``
(or ``mp_ass_subscript``).
Therefore, the ``sq_item`` path is hardly ever taken
when executing the ``BINARY_SUBSCR``, ``STORE_SUBSCR`` or ``DELETE_SUBSCR``
opcode.
``sq_item`` is mostly only called from built-in functions,
such as ``min()``, when iterating an sequence-like type.

In ``evo4``, we effectively re-name ``mp_subscript`` to ``op_getitem``,
and discard ``sq_item``.
This makes everything simpler:

..  code-block:: java

    class Abstract {
        // ...
        static PyObject getItem(PyObject o, PyObject key) throws Throwable {
            // Decisions are based on types of o and key
            try {
                PyType oType = o.getType();
                return (PyObject) oType.op_getitem.invokeExact(o, key);
            } catch (EmptyException e) {
                throw typeError(NOT_SUBSCRIPTABLE, o);
            }
        }

    class Sequence extends Abstract {
        // ...
        static PyObject getItem(PyObject s, int i) throws Throwable {
            try {
                PyObject k = Py.val(i);
                return (PyObject) s.getType().op_getitem.invokeExact(s, k);
            } catch (EmptyException e) {
                throw typeError(NOT_INDEXING, s);
            }
        }

But simpler is not quicker where we have a native ``int`` index to hand.
We see that ``getItem(PyObject, int)``
has to wrap its argument as a Python object.
Then the receiving object will validate and unwrap it.
In order to avoid this nugatory work we would have to recognise
types for which a short-cut is available,
in ``getItem(PyObject, int)`` or where it might be called.

A check for the Java ``List<PyObject>`` interface
would allow us to call ``List.get(int)`` directly,
although it leaves the problem of end-relative indexing,
and raising the correct error, in that caller's hands.
We must be careful that in subclasses defined in Python,
the implementation of ``List.get(int)`` actually calls ``__getitem__``.

..  note::
    The problems with implementing ``List`` are that it requires a lot
    of methods to be implemented referencing the actual value,
    that sub-classes may make arbitrary definitions of critical methods,
    and that the problem of end-relative indexing,
    and raising the correct error, remain in the caller's hands..
    We have bumped into this with ``mappingproxy`` and ``Map``,
    where it is even worse.
    It's not impossible but it's a lot of work and
    the attraction of efficiency soon evaporates.

    A specific interface or wrapper like ``PySequence`` may be better.
    But there are interesting possibilities in return for the work.
    Down this route lies a series of interfaces that ``PyObject``\s
    may optionally have for efficient use at the Java level.
    It may these can be "discovered" when Python objects are passed
    in Python to Java methods that expect those interfaces.
    They cannot be used to make all dict-like Python objects Java ``Map``\s,
    since the characteristic methods may be added or removed dynamically.


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


