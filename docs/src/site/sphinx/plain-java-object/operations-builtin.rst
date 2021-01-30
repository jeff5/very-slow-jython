..  plain-java-object/operations-builtin.rst


Operations on Built-in Types
############################

The pursuit of the plain Java objects idea requires a extensive change
to the preceding VSJ 2 (project ``rt2``) implementation,
although structurally VSJ 3 (project ``rt3``) will have much in common with it.

    Code fragments in this section are taken from
    ``rt3/src/main/java/.../vsj3/evo1``
    and corresponding test and other directories
    in the project source.

Direction of Travel
===================

We will tackle first the question of how we find the ``MethodHandle``\s
that an interpreter would invoke or a ``CallSite`` bind as its target,
that is, we begin with a typical special method "slot".

In VSJ2 we shifted from seeing these slots as fundamental,
as they first appear in the definition of the built-in objects in CPython.
The true foundation is the data model, with its special methods exposed
through descriptors in the dictionary of the type.
We regard the type slots as merely a cache of that information,
attuned to the needs of a CPython interpreter.

VSJ2 also made a brief and inadequate pass over the question of building
call sites based on these cached values.
(It was inadequate because it equated Java class to Python type
when generating guards, which we must now put right.)

In order to to get started on the new idea,
we should begin with type construction using the exposure apparatus.
This involves copying and adapting a good portion of VSJ2
(taken at the ``evo4`` stage).
For the time being,
we defer actual interpretation of code in favour of unit tests
of the abstract object and other conjectured API.

We'll do this first with a few familiar types,
and attempt to explain how we may define and access
a rich set of operations in the plain objects paradigm.



``PyType`` and ``Operations`` for ``int``
=========================================

The example of ``int.__neg__``
------------------------------

In VSJ 2 we created the means to build a ``PyType``
for classes crafted to represent Python objects.
An example is ``PyLong``,
representing the type ``int`` and its instances.
There the ``PyType`` provides a ``MethodHandle`` to each method of ``int``,
including the special methods (such as ``__neg__``),
for which it also acts as a cache.

Suppose we want to do the same again,
but now also to allow instances of ``java.lang.Integer``
to represent instances of ``int``.
For each method, including the special methods,
we shall have to provide an implementation applicable to ``PyLong``,
as before,
and also one applicable to ``Integer``.

Let's just address unary negative to begin with.
Suppose that in the course of executing code,
the interpreter picks up an ``Object`` from the stack
and finds it to be an ``Integer``.
How does it find the particular implementation of ``__neg__``?

The structure we propose looks like this,
when realised for two integer values:

..  uml::
    :caption: Instance model of ``int`` and its operations

    object "42_000_000_000 : PyLong" as x
    object "PyLong : Class" as PyLong.class

    object " : MethodHandle" as xneg {
        invokeVirtual
        target = PyLong.__neg__()
    }

    object "int : PyType" as intType {
        index = 0
    }

    x --> PyLong.class
    PyLong.class --> intType : ops
    intType --> intType
    intType --> xneg : op_neg

    object "42 : Integer" as y
    object "Integer : Class" as Integer.class
    object " : Operations" as yOps {
        index = 1
    }

    object " : MethodHandle" as yneg {
        invokeStatic
        target = PyLong.~__neg__(Integer)
    }

    y --> Integer.class
    Integer.class --> yOps : ops
    yOps -left-> intType : type
    yOps --> yneg : op_neg

    object " : Map" as dict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    intType --> dict : dict
    dict --> neg
    neg --> xneg
    neg --> yneg


We separate the responsibilities of ``PyType``
where they have to adapt to the specific Java implementation,
into an ``Operations`` object specialised to it.
A ``PyType`` is a particular kind of ``Operations`` object,
describing the *canonical implementation* (``PyLong`` in this case),
additionally containing the information common to all implementations.
The ``Operations`` object for an alternative *accepted implementation*,
is not a ``PyType``.

There may only be one descriptor for ``int.__neg__``.
We may imagine a table of implementations in the descriptor,
indexed by a value the ``Operations`` object holds.
Assuming we also have a caching scheme (``op_neg`` slot),
that cache will be on the ``Operations`` object itself,
and reproduce the handle at the corresponding index from the descriptor.

As before, the canonical implementation class
defines operations on its instances as instance methods.
We assume that this is also a sensible place to implement
the special functions for the accepted implementations.
The operations on ``Integer`` have to be ``static`` methods, since
we can't very well open up ``java.lang.Integer`` and add them there!

The canonical implementation class will specify, during initialisation,
the Java classes that are the accepted implementations.
It looks something like this:

..  code-block:: java

    class PyLong {

        static PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("int", PyLong.class, MethodHandles.lookup())
                        .accept(Integer.class));
        // ...
        protected Object __neg__() {
            return new PyLong(value.negate());
        }

        protected static Object __neg__(Integer self) {
            int v = self.intValue();
            if (v != Integer.MIN_VALUE)
                return -v;
            else
                return new PyLong(-(long) Integer.MIN_VALUE);
        }

Note that if a Java class were to be encountered by the run-time
before its canonical counterpart could register it,
it would be treated as a "found" Java class,
and this would prevent it becoming an accepted implementation as intended.
Types with accepted implementations must initialise before this can happen,
and so we make them bootstrap types.
A list read from configuration is imaginable as an alternative to hard-coding,
but it would have to be acted on early in the life of the type system.



Some Dots on the RADAR
======================

Here is a list of design problems looming already.
Some of these were already apparent for VSJ 2 at ``evo4``,
but since we can see them now,
we may design VSJ 3 with them in mind.

*   The type of attribute names were strongly typed to ``PyUnicode``
    in the VSJ 2 API to ``__getattribute__``, ``__setattr__``, etc.,
    obviating checks in the code.
    If we allow (prefer, even) ``String`` as ``str``,
    we still need ``PyUnicode`` as the canonical type
    (for above BMP strings and Python sub-classes of ``str``).
    So attribute access must now accept ``Object`` (at some level).
    We'd like this to be efficient in call sites
    where a Java ``String`` (UTF-16) may be guaranteed.
    Possibly make this the slot signature.
*   The keys of dictionaries must compare using ``__eq__`` and ``__hash__``
    even when the key is a plain Java object.
    We may not directly use a Java ``Map`` implementation
    directly as the Python implementation,
    except we wrap it in an object defining comparison and hashing.
    (This problem may be latent in VSJ 2.)
    We may avoid this for type dictionaries
    exploiting the string nature of attributes.
*   For types defined in Python,
    the Java class does not define the type,
    so the ``Operations`` slots will indirect
    via a type written on the instance,
    either to another ``Operations`` object specific to the type,
    or directly to the descriptor.
*   Descriptors that appear in the dictionary of the type
    must somehow be applicable to all implementations of that type,
    and therefore contain multiple handles,
    indexed by the implementation (by ``Operations``).
    In ``int.__neg__(42)`` we must invoke index 1.
    Descriptors are inherited by Python sub-classes to which these
    accepted implementations are largely irrelevant,
    since a sub-class Java extends only the canonical implementation.

