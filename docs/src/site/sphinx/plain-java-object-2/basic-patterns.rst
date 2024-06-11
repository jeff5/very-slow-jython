..  plain-java-object-2/basic-patterns.rst


``Object`` and ``PyType`` Revisited
***********************************

In this section we set out the plain Java object approach
to representing Python objects.
We did this already in ``rt3``,
where it was presented as a hypothesis to test.
We now treat it as established.
Furthermore,
experience has taught us in what ways the original idea was incomplete,
so while this section seems to repeat earlier material,
in the guise of a recap,
we have a more precise set of ideas and terms to use.


Concepts
========

Recall that when we encounter a Java object that
we need to treat as a Python object,
we look first to its Java class
to tell us how to handle it.
The Python type of an object is not written on every object we meet,
as it is in Jython 2.

We find the Python type information we need
via a field we effectively add to ``Class``.
We do this using Java's ``ClassValue`` feature,
backed by a registry of these mappings.
The ``ClassValue`` will find or compute a ``Representation`` object
that either contains or leads to all the information we need.

Consulting the ``ClassValue`` is quick, thread-safe and non-blocking
when the ``Representation`` has already been associated to the ``Class``.
It is a lengthy process when the association has to be created,
and delicate from the perspective of thread management.
At least one ``PyType`` has to be created reflectively,
possibly a cascade of inter-dependent ``PyType``\s,
and some other thread may already be doing overlapping work.
This aspect of the model *is* similar to that in Jython 2.

The returned ``Representation`` (created if need be)
will relate to the type in one of three ways:

#. The ``Representation`` object is *one-to-one* with the ``PyType``.
   This is frequently the case for built-in types,
   where a Java class has been crafted to treat instances of itself
   as instances in Python of a given built-in type.
   It is also the case for types representing a "found" Java class
   (one that has not been specified programmatically to the runtime system).
#. The ``Representation`` object is shared by a group of Python types,
   instances of which have the same representation in Java.
   This is the case for Python types related by inheritance,
   such that instances have a mutable ``__class__`` attribute
   that determines the actual type.
   Acceptable values for ``__class__`` define an equivalence relation
   that CPython refers to as having the "same layout".
#. The ``Representation`` object is one of several for the same ``PyType``.
   This is the case for a small number of built-in types,
   where a Java class has been crafted to treat instances of
   specified Java classes as instances in Python of a given built-in type.
   We refer to the additional representations as "adopted".

In ``rt3`` we called the ``Representation`` class ``Operations``,
reflecting its role as the holder of slots for special methods
(operations like ``+`` and ``~``).
We now see it as having a more general use in
describing how instances of the type are represented.


Class Models for the Concepts
=============================

Every object leads unfailingly to a ``Representation``
that can reveal the ``PyType`` of the object we started with.
It begins with the Java class of the object
and afterwards goes one of three ways.


One Type to One Representation
------------------------------

In the *one-to-one* case, ``Representation.pythonType()``
just returns ``this``
because the ``Representation`` associated to the class
is itself the ``PyType``.

..  uml::
    :caption: Plain Java Object Pattern: ``SimpleType``

    class Class<T> {}

    T .right.> Class
    Class "1" -right-> "1" Representation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    interface PyType {
        getDict()
        lookup(attr)
    }

    SimpleType -up-|> Representation
    PyType <|.. SimpleType

When the type ``T`` is a "found" Java type,
the ``PyType`` implementation will be created dynamically,
its bases found from the Java super-types,
and its dictionary filled with descriptors for the accessible methods,
created reflectively.

When we implement a Python type explicitly,
we have the luxury of designing in advance the exposed methods
(like ``__add__`` or ``insert``),
and we can arrange to include descriptors for them
in the dictionary of the ``PyType``.
We describe this as a "crafted" type.
Usually the methods will be implemented in a single class
that defines the type and implements the instances.
They will mostly be instance methods (in Java) of that class.

In principle, another class may provide implementations,
if it has sufficient access to the internals of the Java class
used to represent instances.
The methods will have to be Java ``static``,
and take the representing Java type as their first argument
if they are instance methods in Python.


Shared Representation (Mutable Types)
-------------------------------------

Where several types are represented by the same Java class,
a single ``Representation`` will be cited by multiple ``PyType``\s.
Instances must hold their Python type as an attribute
that the ``Representation`` consults when asked for the Python type.
(This is why ``Representation.pythonType()`` takes an argument.)

Typically ``__class__`` assignment is possible on instances of these types,
as long as the replacement value is another ``MutableType`` that
cites the common ``Representation``.

..  uml::
    :caption: Plain Java Object Pattern: Shared Representation

    class Class<T> {}
    class SharedRepresentation<T> {}
    T .right.> Class
    Class "1" -right-> "1" SharedRepresentation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    interface PyType {
        getDict()
        lookup(attr)
    }

    interface WithType {
        getType()
    }

    T .up.|> WithType

    SharedRepresentation -up-|> Representation
    SharedRepresentation "1" -- "*" MutableType

    PyType <|.. MutableType


Instances of a class defined in Python
(by a ``class`` statement)
that have no built-in types in their MRO but ``object``,
will have the Java class ``PyBaseObject`` for ``T``.
This is obviously a common case.

In general, ``T`` will be a subclass of the "most-derived" common ancestor.
Python imposes restrictions on the valid combinations of such bases,
based on memory layout and other attributes.
We shall explore examples with ``list`` as the base,
in the next section.


Adoptive Types
--------------

In a few cases we accept several Java types as the same Python type.
Each must lead to its own ``Representation`` object,
but all lead to the same Python type.
For example, several kinds of boxed integer all represent Python ``int``.

..  uml::
    :caption: Plain Java Object Pattern: Adopted Representations

    class Class<T> {}
    class AdoptiveRepresentation<T> {}
    T .right.> Class
    Class "1" -right-> "1" AdoptiveRepresentation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    interface PyType {
        getDict()
        lookup(attr)
    }

    AdoptiveRepresentation -up-|> Representation
    AdoptiveRepresentation "*" -- "1" AdoptiveType

    PyType <|.. AdoptiveType

When we implement a Python type this way,
as before, we arrange to include a descriptor for each method
in the dictionary of the ``PyType``.
These descriptors may have to be a little special,
because a single method from the Python perspective
has to know a definition in Java
applicable to each accepted representation of the type.
There may be one for each, one for all, or a number between these extremes.

We could implement all the methods of an adoptive type in one class,
but this can be large and repetitive so we often supply definitions as
Java ``static`` with parameters matching the accepted representations.

As we saw in the previous subsection,
Python subclasses of a given built-in type
are represented by a common Java class.
When the type is adoptive,
and admits subclasses,
we must identify a particular (non-``final``) representation
as "canonical",
and that or a crafted subclass will be the representation
of every Python subclass.
When we call methods defined for the type on instances of the subclass,
the Java method called will always be that defined for
the canonical representation.

For a simple type, the single representation is canonical,
and a specific Java subclass of it is the extension point.
Amongst the adoptive built-in types
we find a diversity of patterns to be necessary.

Python ``str`` is an adopts ``java.lang.String`` as a representation.
The adopted form is more frequent than ``PyUnicode``,
which may represent a ``str`` using an array of character values,
Some methods have distinct implementations for ``String`` and ``PyUnicode``,
while others accept ``Object`` in order to share an implementation.

The type ``bool`` adopts ``java.lang.Boolean``
but needs no canonical representation as it cannot be subclassed
The class ``PyBoolean`` is there only to define the type,
and methods on the only two ``Boolean`` objects that can exist.

Some special treatment is needed to make ``bool`` a subclass of ``int``.
The type ``int`` accepts ``Boolean`` as a representation in methods,
but does not *adopt* it.

Sometimes the canonical representation is only instantiated
to support subclasses.
For example, ``PyInteger`` instances only exist to subclass ``int``,
and ``PyBaseObject`` instances only so that we may subclass ``object``.

