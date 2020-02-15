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

The type has only two instances, True and False,
but since they have arithmetic value,
we will have to explore, for the first time,
that path through the implementation of binary operations
that involves delegation to the derived type first.

Choices in the Implementation of Inheritance
********************************************

When two types are related by inheritance in Python,
it is not automatically required that
the Java classes that implement them should be related by Java inheritance.
Java inheritance will not be able to represent Python inheritance fully,
since Python allows multiple inheritance of types,
and Java does not support multiple inheritance of classes.
We must manage Python inheritance through relationships amongst
``PyType`` objects.

When complete,
these relationships include an MRO (method resolution order)
computed according to Python rules.
Our study of the slot functions gives us one set of criteria,
by which to judge implementation ideas:
we must be able to produce the required sub-class behaviour
where it involves invoking slots.
However, there are other relevant language features we have not yet reached,
so it is possible that what we choose here will be overturned later.

Where a Python type ``A`` is an immediate super-class of ``B``,
implementation options we might consider include:

#.  ``PyB`` Java-extends ``PyA``.
    This approach is seemingly ruled out because it cannot fully implement
    multiple inheritance.
    In the implementation of CPython ``PyB`` would be a ``struct`` that
    can be successfully cast to a ``struct PyA``.
    (Other bases of ``B`` have to be compatible with that,
    or one receives a "layout error".)
    The need to allow this implementation gives rise to a language-level
    limitation on inheritance involving common types.
    As long as every cast Python needs has a Java "image",
    the slot functions will be compatible with the derived type.
#.  ``PyB`` includes a ``PyA`` field and delegates to it.
    This places no constraints on inheritance hierarchy.
    Slots could be filled by wrapping the inherited ``MethodHandle``
    with a function that delegates to the field.
#.  ``PyB`` has an implementation independent of ``PyA``.
    This makes it necessary to reproduce inherited behaviour
    by writing the slot functions again from scratch.
    It will not normally be possible to do this automatically,
    so we consider this a dead-end.

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
(Actually, there is, but it is the abstact C-API ``type()`` function.)


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
