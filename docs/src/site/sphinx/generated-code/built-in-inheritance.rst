..  generated-code/built-in-inheritance.rst

Built-in Inheritance
####################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo2``
    and ``rt2/src/test/java/.../vsj2/evo2/PyByteCode3.java``
    in the project source.


The Example of ``bool``
***********************

A next obvious place to go in our development is to branching and loops.
The new elements,
comparison and conditional branching,
both involve the type ``bool``.
Although it emerges here from our interest in conditional branching,
``bool`` presents us with interesting new challenges in its own right.

``bool`` is a sub-class of ``int``.
This inheritance involves only built-in types,
which gives us the chance to address Python inheritance fairly simply.

..  code-block:: python

    >>> isinstance(True, int)
    True
    >>> isinstance(True, bool)
    True

The type has only two instances, ``True`` and ``False``,
but since they have arithmetic value,
we will have to explore, for the first time,
that path through the implementation of binary operations
that involves delegation to the derived type first.

This leads us on a short digression on the subject of inheritance.

Inheritance will not be Free
****************************

When two types are related by inheritance in Python,
it does not necessarily follow that
the Java classes that implement them should be related by Java inheritance.
Java inheritance is available to help us write the interpreter;
Python inheritance is something we have to represent
through relationships we build amongst ``PyType`` objects.
When complete,
these relationships include an MRO (method resolution order)
computed according to Python rules.

Java inheritance will not do the job for us automatically,
since Java does not support multiple inheritance of classes,
while any implementation of Python must.

Our study of the slot functions gives us one set of criteria
by which to judge implementation ideas:
we must be able to produce the required sub-class behaviour
where it involves invoking slots.
However, there are other relevant language features we have not yet reached,
so it is possible that what we choose here will be overturned later.


Python gives us a Break
=======================

We know that CPython implements objects as a sort of ``struct``,
in which the value data is laid out after a standard object header,
that deals with type and reference counting.
(There is an extended standard header for variable-size objects.)

Instances of a class have data laid out in a way characteristic of the class.
Here are ``float`` and ``complex`` (not actually in the same file)
from the source of CPython:

..  code-block:: C

    typedef struct {
        PyObject_HEAD
        double ob_fval;
    } PyFloatObject;

    typedef struct {
        double real;
        double imag;
    } Py_complex;

    typedef struct {
        PyObject_HEAD
        Py_complex cval;
    } PyComplexObject;

For an instance to belong to a class that inherits more than one base,
the layout in memory has to be compatible with each base type
that contributes a method or slot function.

In Python, this is fine:

..  code-block:: python

    class A: pass
    class B: pass
    class C(A, B): pass

But this (in CPython):

..  code-block:: python

    class D(float, complex): pass

leads to a ``TypeError`` "multiple bases have instance lay-out conflict".

Both ``A`` and ``B`` consist in memory of just the header
and (a pointer to) a dictionary for the instance attributes.
The same layout will do for instances of ``C``,
since it needs one common dictionary.
The methods (or other attributes) of their classes,
will happily co-exist in the dictionary of ``C`` (the type).

Classes with just one built-in base class in their hierarchy,
which may be ``object``, as with ``A`` and ``B``,
will generally combine without an issue.
(``class E(float, C): pass`` would also be fine.)

Python classes that define ``__slots__``,
effectively extend the ``struct`` of their instances.
These will run into the same ``TypeError``
if they do not name and order their members compatibly.

All of this may sound as if the C implementation "tail"
is wagging the language design "dog".
Let us simply say that the design of the language
makes concessions to what may be efficiently implemented.

..  note::
    It is not clear whether the incompatibility of particular types
    is a language rule or a limitation of the CPython implementation.
    However,
    users will expect a Java implementation to be no more restrictive
    than CPython.


Choices in the Implementation of Inheritance
============================================

Suppose we have a Python type ``C``,
with immediate super-classes of ``A`` and ``B``,
and suppose these are implemented by Java classes
``PyC``, ``PyA`` and ``PyB``.
Implementation options we might consider include:

#.  ``PyC`` Java-extends ``PyA`` and ``PyB``.
    This approach is seemingly ruled out because it cannot fully implement
    multiple inheritance.
#.  ``PyB`` includes a ``PyA`` field and delegates to it.
    This places no constraints on inheritance hierarchy.
    Slots could be filled by wrapping the inherited ``MethodHandle``
    with a function that delegates to the field.
    This is complex and gains one indirection per inheritance level.
#.  ``PyB`` has an implementation independent of ``PyA``.
    This makes it necessary to reproduce inherited behaviour
    by writing the slot functions again from scratch.
    It will not normally be possible to do this automatically,
    so we consider this a dead-end.

This is somewhat discouraging.
But perhaps we ruled out Java inheritance too quickly:
bear in mind the limitations imposed by "layout" compatibility.
The layout is only inherited from one ancestor (base),
chosen so that all ancestral lines can agree on this layout.
(If that is not possible, class creation is forbidden.)
Other attributes may co-exist in the instance dictionary,
and methods that manipulate those may come from any ancestor,
all lines converging in ``object`` or the same built-in type,
which ``PyC`` can inherit.

The upshot of this is (we may hope)
that Python inheritance extending the footprint in memory,
or overriding slot functions,
can be modelled successfully by Java inheritance.
Inherited slot functions will find a compatible (Java) type.
Python inheritance not extending the footprint,
does not require a new Java class:
all Python types with the same layout can be (must be)
represented by the same Java class.
Instances would have to contain a field telling us their Python class,
since the Java class would not be enough to identify that.

Here we will take the first (Java-inheritance) approach,
and hope that nothing emerges that it cannot handle.
The delegation model is in reserve,
either to become the general solution,
or to address specific hard cases.


A Simplified MRO
****************

We do not need (and are not ready for)
the full richness of the Python type system,
but let's see what it tells us about ``int`` and ``bool``.

..  code-block:: python

    >>> bool.__base__
    <class 'int'>
    >>> bool.__bases__
    (<class 'int'>,)
    >>> bool.mro()
    [<class 'bool'>, <class 'int'>, <class 'object'>]
    >>>
    >>> int.__base__
    <class 'object'>
    >>> int.__bases__
    (<class 'object'>,)
    >>> int.mro()
    [<class 'int'>, <class 'object'>]

We can see that ``bool`` has ``int`` as its (only) base
and ``int`` has ``object``.
The MRO is formed by walking up the inheritance hierarchy,
using a particular strategy to deal with multiple inheritance,
and constitutes the order in which we look for the definition of a method.
It is also, effectively, the order in which we resolve a slot as non-empty,
but it doesn't work that way when executing code: we can do it in advance.

The ``__base__`` and ``__bases__`` attributes of a type,
and the result of the ``mro()`` method,
are all held as attributes of the type (``tp_*`` slots),
and kept aligned by carefully avoiding direct client access
(through the advertised C-API).

All the MROs end with the type ``object``.
This is slightly special,
in that it is implicitly the super-type of any type not declaring otherwise.
``object`` itself has no base.

..  code-block:: python

    >>> object.__base__
    >>> object.__bases__
    ()
    >>> object.mro()
    [<class 'object'>]


Hints from the C Implementation
*******************************

Each of these types has a (statically initialised) ``PyTypeObject``
to describe it.
As CPython creates the type, it modifies this information,
to create the content of ``tp_mro`` for example.
The definition ``PyBool_Type`` is noticeably sparse,
because much of the content will be filled by the type system.
In particular,
the numeric slots will mostly be copied from ``PyLong_Type``,
which it names as its base.

We should be able to obtain the same semantics
by instantiating the ``PyType`` for ``bool``
with correspondingly few slot functions defined in ``PyBool``.
The slots ``PyBool`` fails to define
may then be filled by copying from the ``PyType`` of ``int``.

It is worth noting the difference between ``PyObject``
and the implementation of ``object``.
All objects in the CPython interpreter are (can be successfully cast to)
``PyObject``, because they are ``struct``\s that "start in the right way".
The type object of ``object`` in C Python is called ``PyBaseObject_Type``.
There is no ``PyObject_Type``.
(Actually, there is, but it is the abstract C-API ``type()`` function.)

.. _bool-implementation:

A ``bool`` Implementation
*************************

We may implement ``bool`` according to this scheme as follows:

..  code-block:: java

    /** The Python {@code bool} object. */
    class PyBool extends PyLong {

        static final PyType TYPE =
                new PyType("bool", PyLong.TYPE, PyBool.class);

        @Override
        public PyType getType() { return TYPE; }

        /** Python {@code False} object. */
        static final PyBool False = new PyBool(false);

        /** Python {@code True} object. */
        static final PyBool True = new PyBool(true);

        // Private so we can guarantee the doubleton. :)
        private PyBool(boolean value) {
            super(value ? BigInteger.ONE : BigInteger.ZERO);
        }

        @Override
        public String toString() {
            return asSize() == 0 ? "Float" : "True";
        }
    }

We can try this with simple examples
that require the inheritance of the numeric slot functions.
It works just fine, for example with:

..  code-block:: python

    u = 42
    a = u + True
    b = u * True
    c = u * False

However, under the covers,
the path through ``Number.binary_op1`` is not quite what we want:
the slow path (with a test for sub-type) is taken every time.
Recall that in a binary operation (see :ref:`binary_operation`),
we test method handles for equality,
and if they are equal we try just that handle.
Using our current mechanism for filling the slots,
the slot function ``NB.add``, for example,
is found by ``NB.findInClass(PyBool.class)``.
The search succeeds, although ``PyBool`` does not define ``add``,
because ``PyBool`` inherits it from ``PyLong``.
As a result,
``bool`` and ``int`` are given distinct handles to the same method.

We would prefer that ``findInClass``,
which is relying on ``MethodHandles.Lookup.findStatic``,
only look in the particular class it is given,
so that on returning empty,
we are prompted to copy the slot from the base (Python) type.

Remedies to be explored include:

#.  Augment the logic of ``EnumUtil.findInClass(c)``,
    so that after the lookup,
    we crack the handle to see where its method is defined.
    If the defining class is not ``c``, treat it as not found.
#.  Make the "conventional name" of the slot function embed the name
    of the target class,
    just as CPython would call the ``neg`` method ``long_neg``.
#.  Add ``c`` as a type to the signature of the method sought,
    either instead of an existing argument,
    or as a dummy.
    This is attractive anyway as we could then declare, for example,
    ``PyObject PyLong.neg(PyLong)``,
    and avoid the ugly ``try-catch`` and cast within the implementation.
    We may not, however, want to do this everywhere,
    and the signature of binary operations has to be ``PyObject, PyObject``.
#.  Put the slot functions in a separate class, perhaps a nested class,
    with private access to the implementation.
    For example ``PyLong.neg`` could move to ``PyLong.Operations.neg``.
    The implementation ``PyBool extends PyLong`` would not imply
    ``PyBool.Operations extends PyLong.Operations``.

These remedies require an evolution of the existing code base,
and so we'll leave that for a couple of sections later.
``bool`` works correctly,
and this is enough for us to explore conditional branching next.
