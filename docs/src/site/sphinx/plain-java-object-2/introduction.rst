..  plain-java-object-2/introduction.rst


Introduction
************

We expand on the new things we hope to expore
in the fourth model run-time system ``rt4``.


Java Classes as Python Types
============================

``rt3`` proves its type and exposure model only for crafted types.
It did not try to expose members of a discovered type,
or to construct a type with natural Java classes amongst its bases.
We have a theory how to do those things,
but it needs refining through tests.


Special Methods
===============

We contend that special methods are methods first and special later.

Type objects, with their tables of slots,
are highly recognisable in the CPython code base.
A large part of ``typeobject.c`` is devoted to
filling, inheriting and invoking type slots, with all their quirks.
At first these look fundamental to any implementation,
and it appears need method handles for them to embed in our call sites.
We started there in ``rt3`` with the ``Operations`` object,
and eventually had slot-wrapper objects to call them as methods,
as in CPython.

We then created the means to expose and call other methods,
and found that these could contain method handles.
In retrospect, we recognise that type slots are an optimisation
applied to selected methods that occur repeatedly in objects.

This time we shall start with general method exposure.
Methods that are special (as in the Python data model)
will be less special than before.
A special method has a well-known name and an expected signature.
This permits certain optimisations when interpreting Python byte code
or supporting call sites in Python compiled to JVM byte code,
but much of the apparatus around special methods
is the same as for methods in general .
We can choose where to apply this optimisation,
independent of the repertoire of special methods in the data model.


A Jython API using Java modules
===============================

We must consider the API Jython presents to programmers,
including extension writers,
whose work is in a different Java package from Jython.
Java modularity (project Jigsaw) gives us effective control of visibility,
provided we choose packages wisely.
The re-start is an opportunity to work out how that applies.
``rt4core`` will be a modular project.
We shall have a distinct ``rt4client`` project
to represent the module space of an application or extension,
with "black box" unit tests in that project.
As a matter of detail, we drop the ``evo`` level from package names.
(It was a useful device until ``rt1``
but we never outgrew ``evo1`` in ``rt3``.)

