..  generated-code/refactor-to-evo3.rst

Refactoring the Code Base
#########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo3``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo3``.


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


Re-working ``PyType``
*********************

*   Re-work type object construction to use static factory methods
    for better control over the sequence of ``PyType`` creation.
    Identify and support necessary patterns of immutability,
    re-using ``NumberMethods.EMPTY`` etc. where possible.
    Implement slot inheritance by copying handles from the base(s).

*   Consider flattening the slot function tables
    to a single structure (an ``EnumMap``?).
    Some properties of a type do not belong in this structure,
    even if they have ``tp_*`` names in CPython.
    (See C-API ``PyType_Slot.slot`` fields that cannot be set,
    and note on ``tp_bases``.)
    We don't need an ``EMPTY`` table in this case (I think).

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


Flattening the Slot-function Table
==================================

In the section :ref:`representing-python-class`,
we noted that in the CPython ``PyTypeObject``,
some slots were directly in the type object (e.g. ``tp_repr``),
while most were arranged in sub-tables,
pointed to by fields in the type object that may be ``NULL``.
The motivation is surely to save space on type objects that do not need
the full set of slots.
The cost is some testing and indirection where these slots are used.
A common idiom in the CPython source is something like:

..  code-block:: C

    m = o->ob_type->tp_as_mapping;
    if (m && m->mp_subscript) {
        PyObject *item = m->mp_subscript(o, key);
        return item;
    }

We observe that types defined in Python (``PyHeapTypeObject``)
always create all the tables,
so only types defined in C benefit from this parsimony.
As there are 80 slots in total,
the benefit cannot exceed 640 bytes per type (64-bit pointers),
which is a minor saving even if there are a few hundred such types.

In ``evo3`` we have chosen an implementation in which
all the slots are fields directly in the type object.
This significantly simplifies the code to create them,
and saves an indirection or two with each operation.
With the trick of using ``EmptyException`` in place of a test,
the equivalent Java code is just:

..  code-block:: java

        PyType oType = o.getType();
        try {
            return (PyObject) oType.mp_subscript.invokeExact(o, key);
        } catch (EmptyException e) {}

The supporting fields in ``PyType`` are all ``MethodHandle``\s as before:

..  code-block:: java

    class PyType implements PyObject {
        //...
        // Standard type slots table see CPython PyTypeObject
        MethodHandle tp_hash;
        MethodHandle tp_repr;
        //...

        // Number slots table see CPython PyNumberMethods
        MethodHandle nb_negative;
        MethodHandle nb_add;
        //...

        // Sequence slots table see CPython PySequenceMethods
        MethodHandle sq_length;
        MethodHandle sq_repeat;
        MethodHandle sq_item;
        //...

        // Mapping slots table see CPython PyMappingMethods
        MethodHandle mp_length;
        MethodHandle mp_subscript;
        //...

Previously we gave these short names within their sub-table structures,
but in the larger scope
it seems wise to flag them as slots by using the same names as CPython.
This will simplify translation of code from CPython to Jython, and
it avoids clashes,
e.g. between ``sq_length`` and ``mp_length``,
or between Java ``int`` and ``nb_int``.
We shall not name all the ``PyType`` fields with the ``tp_`` prefix:
fields like ``name``, ``bases`` and ``mro`` are not slots in this sense.

A revised version of ``Slot.java`` (about half the length it was)
now defines an enum with constants for each slot:

..  code-block:: java

    enum Slot {

        tp_hash(Signature.LEN), //
        tp_repr(Signature.UNARY), //
        //...

        nb_negative(Signature.UNARY, "neg"), //
        nb_add(Signature.BINARY, "add"), //
        //...

        sq_length(Signature.LEN, "length"), //
        sq_repeat(Signature.SQ_INDEX), //
        sq_item(Signature.SQ_INDEX), //
        //...

        mp_length(Signature.LEN, "length"), //
        mp_subscript(Signature.BINARY), //
        //...

        final String methodName;
        final MethodType type;
        final MethodHandle empty;
        final VarHandle slotHandle;

        Slot(Signature signature, String methodName) {
            this.methodName = methodName == null ? name() : methodName;
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = Util.slotHandle(this);
        }

        Slot(Signature signature) { this(signature, null); }
        //...
    }

As with the previous ``Slot.TP``, ``Slot.NB``, and so on,
the ``enum`` encapsulates a lot of behaviour,
here elided,
supporting its use.
The design is the same one outlined in :ref:`how-we-fill-slots`,
but we no longer have to repeat the logic for ``Slot.NB``, ``Slot.SQ``, etc..

The name of the slot is the same as that of the ``enum`` constant, and
unless otherwise given,
so is the name of the method in the implementation class.
Consequently,
this change has us changing the names of some of these methods,
to match the distinctive slot name, e.g.:

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

The initialisation of the ``PyType`` now uses a single loop
to initialise all the slots:

..  code-block:: java

        private void setAllSlots() {
            for (Slot s : Slot.values()) {
                final MethodHandle empty = s.getEmpty();
                MethodHandle mh = s.findInClass(implClass);
                for (int i = 0; mh == empty && i < bases.length; i++) {
                    mh = s.getSlot(bases[i]);
                }
                s.setSlot(this, mh);
            }
        }

The version here includes code that deals with simple inheritance.



Immutability and ``PyType.flags``
=================================

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

We noted in :ref:`bool-implementation` that
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
    [`Shipilev 2014`_].
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

