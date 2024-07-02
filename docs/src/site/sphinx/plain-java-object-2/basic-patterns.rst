..  plain-java-object-2/basic-patterns.rst


``Object`` and ``PyType`` Basic Patterns
****************************************

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
The Python type of an object is not written
on every object the interpreter meets,
as it is in Jython 2.

We find the Python type information we need
via a field we effectively add to ``Class``.
We do this using Java's ``ClassValue`` feature,
backed by a registry of these mappings.
The ``ClassValue`` will find or compute a ``Representation`` object
that either contains or leads to all the type information we need.

Consulting the ``ClassValue`` is quick, thread-safe and non-blocking
when the ``Representation`` has once been associated to the ``Class``.
It is a lengthy process when the association has to be created,
and delicate from the perspective of thread management.
This aspect of the model *is* similar to that in Jython 2.

When the association has to be created,
at least one ``PyType`` has to be constructed reflectively,
or possibly a cascade of inter-dependent ``PyType``\s.
We must guard against the possibility that
some other thread may already be doing overlapping work.

The returned ``Representation`` (created if need be)
will relate to the type in one of three ways:

#. **Simple:**
   The ``Representation`` object is *one-to-one* with the ``PyType``.
   This is frequently the case for built-in types,
   where a Java class has been crafted so that instances of itself,
   and of its Java subclasses,
   are treated as instances in Python of a given built-in type.
   It is also the case for types representing a *found* Java class,
   one that has not been specified programmatically to the runtime system.
#. **Shared:**
   The ``Representation`` object is shared by a group of Python types,
   instances of which have the same representation in Java.
   This is the case for types defined in Python,
   and having the base ``object`` or another built-in type.
#. **Adopted:**
   The ``Representation`` object is one of several for the same ``PyType``.
   This is the case for a small number of built-in types.
   A Java class defining the type causes the type system
   to treat instances of specific unrelated Java classes
   as instances in Python of the type.
   We refer to these representations as "adopted" representations.

In ``rt3`` we called the ``Representation`` class ``Operations``,
reflecting its role as the holder of slots for special methods
(operations like ``+`` and ``~``).
We now see it as having a more general use in
encapsulating how instances of the type are represented in Java,
and information that follows directly from that.


Class Models for the Concepts
=============================

Every object leads unfailingly to a ``Representation``
that can reveal the ``PyType`` of the object we started with.
It begins with the Java class of the object
and afterwards goes one of three ways.


Simple (One Type to One) Representation
---------------------------------------

In the *one-to-one* case ``Representation`` associated to the class
is itself the ``PyType``.
``Representation.pythonType()`` just returns ``this``

..  uml::
    :caption: Plain Java Object Pattern: ``SimpleType``

    class Class<T> {}

    T .right.> Class
    Class "1..*" -right-> "1" Representation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    abstract class PyType {
        getDict()
        lookup(attr)
    }

    PyType -up-|> Representation
    PyType <|-- SimpleType


When the type ``T`` is a *found* Java type,
the ``PyType`` implementation will be created dynamically.
Its bases are found from the Java super-types,
and its dictionary filled with descriptors for the accessible methods,
created reflectively.

When we *craft* a Python type ``T`` explicitly in Java,
we have the luxury of designing in advance the exposed methods
(like ``__add__`` or ``insert``),
and we can arrange to include descriptors for them
in the dictionary of the ``PyType``.
These methods will be applicable to Java subclasses of ``T``,
including the special subclass that we nominate as the *extension point*,
that is the shared representation (see next section) of
Python subclasses of ``T``.

Other subclasses of ``T``, not marked as its *extension point*,
act as alternative implementations of the same Python type as ``T``,
in the usual Java way.
These subclasses will share the ``Representation``
created for their common ancestor.

Usually the Python methods will be implemented in a single class
that defines the type and implements the instances.
They will be instance methods (in Java) of that class.
But if that becomes unweildy, or for other reasons,
other classes may be nominated to provide implementations.
Instance methods in Python will have to be Java ``static``,
and take the representing Java type as their first argument.

This is necessarily the case when representing a found type.
The exposed members are limited to those the interpreter can access.


Shared Representation (Replaceable Types)
-----------------------------------------

Where several types are represented by the same Java class,
a single ``Representation`` will be cited by multiple ``PyType``\s.
Instances must hold their Python type as an attribute
that the runtime consults when it needs the Python type.
(This is why ``pythonType()`` takes an argument.)

Typically ``__class__`` assignment is possible on instances of these types,
as long as the replacement value is another ``ReplaceableType`` that
cites the common ``Representation``.

..  uml::
    :caption: Plain Java Object Pattern: Shared Representation

    class Class<T> {}
    class SharedRepresentation {}
    T .right.> Class
    Class "1..*" -right-> "1" SharedRepresentation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    abstract class PyType {
        getDict()
        lookup(attr)
    }

    interface WithType {
        getType()
    }

    T .up.|> WithType
    T --> ReplaceableType

    SharedRepresentation -up-|> Representation
    SharedRepresentation "1" -- "*" ReplaceableType

    ' Representation <|-- PyType
    PyType --|> Representation
    'PyType <|-- ReplaceableType
    ReplaceableType -right-|> PyType


Instances of a class defined in Python
(by a ``class`` statement)
that have no built-in types in their MRO but ``object``,
will have the Java class ``PyBaseObject`` for ``T``.
In general, ``T`` will be a Java *extension point* subclass of
the representation of the most-derived common ancestor.
(The case of mutiple Java bases needs investigation.)

CPython imposes restrictions on the valid combinations of such bases,
and what ``__class__`` assignments are allowed.
We observe that in CPython,
acceptable values for ``__class__``
define an equivalence relation amongst Python classes.
Let :math:`R(A,B)` be the statement ``A.__class__ = B.__class__`` is allowed.
Then :math:`R(A,A)`,
:math:`R(A,B) ⇒ R(B,A)`,
and :math:`R(A,B) ∧ R(B,C) ⇒ R(A,C)`.

In CPython, the constraint is based on memory layout and other attributes.
In Jython, the essential requirement is that equivalent Python classes
be represented by the same Java class.
Other constraints must be added (e.g. presence of a ``__dict__``).
We think this is no more restrictive than the rules implemented in CPython.

The section :doc:`./object-and-pytype` explores cases of this,
with ``list``, ``object`` and ``type`` as examples.
We check there our intuition that the constraints CPython applies
correspond to the idea of shared representation in Java.


Adoptive Types
--------------

In a few cases we accept several Java types as the same Python type.
For example, while there is a crafted ``PyInteger`` implemention of ``int``,
we will accept ``BigInteger``,
the boxed types (``Integer`` and ``Long`` for a start),
and ``Boolean`` (so that ``bool`` may be a subclass of ``int``).
The Java classes accepted by a type fall into three categories:

#. Canonical representation:
   an optional crafted implementation of the type.
   Often, the defining class is also the canonical representation.
   Alternatively, there is no canonical representation,
   and the defining class is the extension point class.
#. Adopted representations:
   classes that we allow to represent the Python type.
   These are generally pre-existing representations
   of an equivalent type in Java (e.g. ``Double`` for ``float``).
#. Additional accepted classes:
   additional classes that are acceptable as ``self``
   to the methods of a type,
   but are not representations.
   The only example so far is that ``Boolean`` is accepted by ``int``,
   so that ``bool`` may be a Python subclass of ``int``.

Each accepted class must lead to its own ``Representation`` object,
but only the canonical and adopted classes lead to the same Python type.
A Java subclass of a class accepted by a built-in type,
not bound already to a different ``Representation``,
will be treated as equivalent to its accepted base.

..  uml::
    :caption: Plain Java Object Pattern: Adopted Representations

    class Class<T> {}
    class AdoptedRepresentation {}
    T .right.> Class
    Class "1..*" -right-> "1" AdoptedRepresentation

    abstract class Representation {
        pythonType(o)
        javaType()
    }

    abstract class PyType {
        getDict()
        lookup(attr)
    }

    AdoptedRepresentation -right-|> Representation
    AdoptedRepresentation "*" -- "1" AdoptiveType

    ' Representation <|-right- PyType
    AdoptiveType -right-|> PyType
    PyType -up-|> Representation


When we implement a Python type,
we must arrange to include a descriptor for each method
in the dictionary of the ``PyType``.
When we accept adopted representations,
these descriptors have to be a little special.

A single method from the Python perspective
has to contain a definition in Java
applicable to each accepted representation of the type,
the crafted one (if present, ``PyFloat`` say),
and each of the adopted representations (``Float``, ``Double``).
For a given method,
there may be one specific to each accepted representation,
one that works for for all of them (accepting ``Object self``),
or something between (``PyFloat`` and ``Number``, say).

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

Python ``str`` adopts ``java.lang.String`` as a representation.
The adopted form is more frequent than ``PyUnicode``,
which may represent a ``str`` using an array of character values.
Some methods have distinct implementations for ``String`` and ``PyUnicode``,
while others accept ``Object`` in order to share an implementation.

The type ``bool`` adopts ``java.lang.Boolean``
but needs no canonical representation as it cannot be subclassed.
The class ``PyBoolean`` is there only to define the type,
and methods on the only two ``Boolean`` objects that can exist.

Some special treatment is needed to make ``bool`` a subclass of ``int``.
The type ``int`` accepts ``Boolean`` as a representation in methods,
but does not *adopt* it.

Sometimes the canonical representation is only instantiated
to support subclasses.
For example, ``PyBaseObject`` instances only exist
only so that we may subclass ``object``.

