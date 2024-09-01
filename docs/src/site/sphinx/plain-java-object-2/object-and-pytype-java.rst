..  plain-java-object-2/object-and-pytype-java.rst

.. _Java-instance-models-object-type:

Instance Models for Java Objects
********************************

In a previous section we provided instance models for some "crafted" types,
showing how they can be described by our Python type system,
and how we might approach the management of inheritance.
This much we had already done in ``rt3``,
but never satisfactorily explained how to do the same for "found" Java types.
We now attempt that extra step.


The Challenge of Subclasses
===========================

We ended the last section with a table of
sample Python types and their representations.
In Jython we also need type objects to represent "found" Java types,
that is, where the Java class is not one for which
we could have prepared a type object or extension point class in advance.
The model in the previous section is not adequate for this.

Need for a Synthetic Class
--------------------------

Where there is a type object,
there is an invitation to base a Python class definition on it.
In general, we should expect to define new classes in Python
that have a Java class as a base.

We will also need to represent interfaces and abstract Java classes.
We cannot encounter them directly as the class of an instance,
but we shall surely need to refer to them in the MRO of Java types,
and as bases of classes defined in Python.
The MRO of a found Java type will be rich in interfaces.
For example, the MRO of ``java.util.ArrayList`` in Jython 2.7.4 is:

..  code-block:: python

    (<type 'java.util.ArrayList'>, <type 'java.util.AbstractList'>,
    <type 'java.util.AbstractCollection'>, <type 'java.util.List'>,
    <type 'java.util.Collection'>, <type 'java.lang.Iterable'>,
    <type 'java.util.RandomAccess'>, <type 'java.lang.Cloneable'>,
    <type 'java.io.Serializable'>, <type 'java.lang.Object'>,
    <type 'object'>)

It surely cannot differ much from this in Jython 3.

It seems obvious that each Java class we encounter
should have a type object we could import by that name.
It could be a ``SimpleType``, or an ``AdoptiveType`` with one adopted class.
A class defined in Python, with found Java bases,
must be a ``SharedType``,
in order to allow class assignment where feasible.

..  note::  Design question: a ``SimpleType``
    or an ``AdoptiveType`` with one adopted class?
    Is there much difference?

When we define a new class in Python, it has one or more bases,
all of them specified as Python type objects.
A Java class must be created or found, to represent the new class,
that is assignment compatible with the ``self`` argument
of all exposed methods of every base.

For this it must extend a Java class,
which may also be the implementation of a Python type,
and implement every Java interface class found amongst the bases.
If the specified bases are all Java interfaces,
``object`` is implicitly a base,
and the representation need only extend ``Object``.

The unlimited variety of combinations of bases leads us to believe that
the representation class must be created dynamically at run time.

Overriding Methods
------------------

Finally,
we should be able to override methods that belong to base classes
with methods defined in Python
that are called by Java clients addressing an instance of the new class.

..  code-block:: python

    >>> class C(Runnable):
    ...     def run(self):
    ...         print "hello"
    ...
    >>> c = C()
    >>> c.run()
    hello
    >>> t = Thread(c)
    >>> t.run()
    hello
    >>> t.start()
    hello

Jython 2 does so,
although it ``__new__`` does not to work as well as we'd like
with combinations of bases.

..  code-block:: python

    >>> class R(Runnable, int):
    ...     def run(self):
    ...         print int.__str__(self)
    ...
    >>> r = R(42)
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: org.python.proxies.__main__$R$7(): expected 0 args; got 1

Note the synthetic class visible in the error message.

Creating a Representation
-------------------------

When we create a type (such as ``float``)
from a representation (Java) class that we write ourselves,
the class definition exists first,
a type specification can be written that refers to it,
and the specification given to the type factory to
create a ``Representation`` object and a type object.
We generally find it convenient to request creation of the type
during static initialisation of the representation class.

When we execute a class definition in Python,
execution supplies us with the specification first,
in the form of the Python bases, a name, a metatype,
and a few other characteristics (like ``__slots__``).
From these we can deduce the form of the representation class,
create or find the class and its ``Representation`` object,
and create a new type object to reference it.
We write "create or find the class" because
when a representation class is needed of the same form as previously,
we wish to re-use the previous class and ``Representation``,
not just for efficiency,
but because the types ought to be mutually replaceable (layout compatible),
which we only achieve when instances belong to the same Java class.

We hypothesise that ...

The specification of the representation class is deducible from
the list of Python bases of the type requested,
by replacing each with its representation (Java) class.
We then eliminate superclasses and superinterfaces as duplicates,
leaving an essential core (single) base class and minimal set of interfaces.
We can use the bse and interfaces,
the sequence of slot names, and
the presence of an instace dictionary,
as a key to look up a prior representation with the same description.