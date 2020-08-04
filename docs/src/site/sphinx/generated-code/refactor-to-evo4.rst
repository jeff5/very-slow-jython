..  generated-code/refactor-to-evo4.rst

Refactoring for ``evo4``
########################

    Code fragments in this section are taken from
    ``rt2/src/main/java/.../vsj2/evo4``
    in the project source,
    and are exercised by unit tests in ``rt2/src/test/java/.../vsj2/evo4``.


Code in the last section resided in package ``evo3``.
That evolution introduced some modest tidying of the type system
that eased the implementation of functions.
Looking back, the changes in ``evo3`` appear largely cosmetic.

In the coming sections we address attribute access,
including attributes that are functions (methods).
This cannot be done without considering sub-classing in Python,
for which we shall need much stronger support for inheritance.
An attempt to found this on the ``evo3`` implementation fails.

For one thing,
the simple "slot inheritance" we used is insufficient.
Slots may be filled by recognition of a slot function defined in Java
(the only mechanism we used in ``evo3``)
or by a "dunder" method defined in Python (or otherwise).
And for another,
as soon as we reach the implementation of the ``__new__`` slot,
our approach to inheritance in ``evo3`` is shown (again)
to have been wholly inadequate.

We could not do this until we had learned
how to implement and call functions based on ``evo3``.
But already we need to advance the implementation to an ``evo4``.
This time,
it might involve a bit of a leap.

Another Re-work of ``PyType``
*****************************

Slot Functions
==============

Java Name
---------

Java Signature
--------------

Dictionary of the ``type``
==========================


Structure of ``PyType``
=======================
