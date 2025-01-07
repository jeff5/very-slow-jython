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

From Java Class to Python Type
------------------------------
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
The ``ClassValue`` will find or compute a ``Representation`` object,
that either *is* the type object or *leads to* the type object.
(``Representation`` object has a method to return the type,
which may simply return ``this``.)

Consulting the ``ClassValue`` is quick, thread-safe and non-blocking
when the ``Representation`` has once been associated to the ``Class``.
It is a lengthy process when the association has to be created,
and delicate when it comes to thread safety.
(This aspect of the model *is* similar to that in Jython 2.)
When the association has to be created,
at least one ``PyType`` has to be constructed reflectively,
or possibly a cascade of inter-dependent ``PyType``\s.
We must guard against the possibility that
some other thread may already be doing overlapping work.

Classes of Type
---------------

Type objects fall into three categories,
which have distinct Java implementations:

#. **Simple:**
   The ``SimpleType`` object is *one-to-one* with the ``Representation``:
   in fact they are the same object.
   This is frequently the case for Python built-in types,
   where a Java class has been crafted to represent instances of the type.
   Java subclasses of a crafted representation
   are mapped by default to this same Python type.
   The ``SimpleType`` is also used to describe a *found* Java class
   (one not specified programmatically to the runtime system).
   Unlike crafted representations,
   Java subclasses of a found representation
   will generally have a Python type object of their own.
#. **Replaceable:**
   The ``ReplaceableType`` is one of several that
   share a single ``Representation`` object.
   Instances in Python are implemented by the Java class it identifies.
   That Java class will have an instance field that is its Python type.
   This field can usually be assigned,
   to change the Python type of the object,
   within the range of the shared representation.
#. **Adoptive:**
   The ``AdoptiveType`` allows instances of several Java classes,
   unrelated by inheritance,
   to represent a single type in Python.
   There must be a ``Representation`` object for each.
   We refer to these representations as "adopted" representations.
   This is the case for a small number of built-in types:
   it is how we can recognise a native ``java.lang.String``, for example,
   as a Python ``str``.

We implement these three as distinct subclasses in Java of ``PyType``.
All appear to Python as instances of ``type``.


Primary Representation
----------------------
The *primary* representation class is a (least derived) Java class,
instances of which are acceptable as
the ``self`` argument to methods of the type.
(Notice we use the term *representation* in two ways:
for the Java representation class, and
for the ``Representation`` object.)

In the case of a **simple type**,
all representation classes for the type have a common root class,
which is the primary representation class.
For example, all three classes of type object extend ``PyType``,
the primary representation of ``type``.

In an **adoptive type**,
the primary representation is the one for which
the ``AdoptiveType`` object is also the ``Representation``.
If there is a crafted representation class,
that will be the primary.


Canonical Base Class
--------------------
Secondly, we need the idea of a *canonical base* class.
This is the Java class on which the implementation of
a subclass defined in Python will be based.
Specific Java classes are needed
as representations of the Python subclasses,
but all will be subclasses in Java of the canonical base.

Instances of subclasses of the canonical base
must be acceptable as a ``self`` argument to methods.
The reason for this is in the definition of instance methods.
When a Python subclass,
seeking a method along the MRO finds it in a type object,
it will call the implementation matching the primary representation.
Therefore we require the canonical base to be
assignment compatible (in Java) with the ``self`` parameter.
Most often,
the canonical base is exactly the primary representation,
but it could be a subclass,
as in ``type``, to ensure metatypes are ``SimpleType``\s.

The canonical base,
even when it is technically the primary representation,
may exist only to support Python subclasses.
(It could even be ``abstract``.)
For example,
there is no reason to construct an actual base ``PyFloat``,
when a ``java.lang.Double`` will fulfil the same purpose,
but a Python subclass of ``float`` is represented by
a Java subclass of ``PyFloat``.

The canonical representation cannot usually be an adopted one,
as they are mostly ``final``.
As so often, ``object`` is an exception,
where the canonical class is ``java.lang.Object``.

A Java subclass of the canonical base of a type is either
treated as representing the same type
(by being mapped to the same ``Representation``), or
the representation of a Python subclass of the type.
The choice is made when we register the Java class to either
the existing ``Representation`` or
create a new representation for the Python subclass.
When the type in question is a replaceable type,
since equivalent types share the same canonical base,
the Java subclass maps either
to the ``SharedRepresentation`` of the type(s),or
to new ``SharedRepresentation`` defining an equivalence.

..  note::
    The detail of this is gradually being confirmed
    in the implementation of Python exceptions and sublasses.
    The concept is right,
    but does the canonical base have properties that make it
    always a "designer" class?


Accepted Class
--------------
Rarely, we need to accept as a ``self`` argument to methods,
instances of a Java class that is *not* a representation of the type.
This is called an *accepted class*.
We only do this in an **adoptive type**,
the only type that accepts more than one ``self`` class.

Method implementations must exist to cater for the accepted class.
For example, for ``bool`` (represented by ``Boolean``) to subclass ``int``,
we must implement ``__add__(Boolean self, Object other)``
in the Java definition of ``int``.
The descriptor for ``int.__add__`` will accept a ``bool`` as if it were
another representation of ``int``.

..  note::
    There may be an alternative to this where
    a ``self`` is asked to cast to the primary class with ``__tojava__``.
    A Python subclass defined in Java, would be able to do that,
    e.g. ``PyBoolean.__tojava__()`` could succeed
    for ``PyLong`` or ``Integer`` target.
    A general subclass defined in Python would have no need for that,
    as it should extend a representation in Java.



Defining Class
--------------
We shall sometimes refer to the *defining class* of a type,
which is simply the class that supplies the definition of a type,
somewhere in its code.

In many cases,
the defining class is also the primary representation.
(We may refer to the *defining representation*.)
A frequent pattern is to write a defining class in which
a ``static final`` type object is created
from a specification of the type being defined.

In other cases, we have to implement the type in one class,
and define the type with reference to that implementation
in another place that becomes the defining class.

The defining class, when it is not a *defining representation*,
is only of real interest because it usually supplies a lookup object,
which must have adequate rights to access the representation,
to create descriptors.
A **replaceable type** does not have a meaningful defining class,
since it is defined within the type system by derivation from
existing types.


How this has Evolved
--------------------
In ``rt3`` we called the ``Representation`` class ``Operations``,
reflecting its role as the holder of slots for special methods
(operations like ``+`` and ``~``).
We now see it as having a more general use in
encapsulating how instances of the type are represented in Java,
and information that follows directly from that.

The need for different types and representation classes
was present in ``rt3``,
but is now more precise.
It seems more complicated but is perhaps correct now.


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
        javaClass()
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
including the special subclass that we nominate as the *canonical base*,
from which the shared representation (see next section) of
Python subclasses of ``T`` extends in Java.

Other subclasses of ``T``, not marked as *Python* subclasses,
act as alternative implementations of the same Python type as ``T``.
These subclasses will share the ``Representation``
created for ``T``.

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
cites the same ``Representation``.

..  uml::
    :caption: Plain Java Object Pattern: Shared Representation

    class Class<T> {}
    class SharedRepresentation {}
    T .right.> Class
    Class "1..*" -right-> "1" SharedRepresentation

    abstract class Representation {
        pythonType(o)
        javaClass()
    }

    abstract class PyType {
        getDict()
        lookup(attr)
    }

    interface WithClassAssignment {
        getType()
        setType(t)
    }

    T .up.|> WithClassAssignment
    T --> ReplaceableType

    SharedRepresentation -up-|> Representation
    SharedRepresentation "1" <-- "*" ReplaceableType

    ' Representation <|-- PyType
    PyType --|> Representation
    'PyType <|-- ReplaceableType
    ReplaceableType -right-|> PyType


Instances of a class defined in Python
(by a ``class`` statement),
that have no built-in types in their MRO but ``object``,
will have the Java class ``PyObject`` for ``T``.
In general, ``T`` will be a Java *extension point* subclass of
the representation of the most-derived common ancestor.
(The case of mutiple Java bases needs investigation.)

CPython imposes restrictions on the valid combinations of such bases,
and what ``__class__`` assignments are allowed.
We observe that in CPython,
acceptable values for ``__class__``
define an equivalence relation amongst Python classes.
Let :math:`R(A,B)` be the statement that
``A.__class__ = B.__class__`` is allowed.
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

#. The defining representation:
   a crafted representation of the type.
   There is always a defining class to register the new type
   and identify its representations.
   Often, an instance of that class represents an instance of the new type.
   (In the most frequent pattern,
   which is not the adoptive pattern,
   the defining class is the only representation.)
#. Adopted representations:
   classes that we allow to represent the Python type.
   These are generally pre-existing representations
   of an equivalent type in Java (e.g. ``Double`` is adopted as ``float``).
#. Additional accepted classes:
   additional classes that are acceptable as ``self``
   to the methods of a type,
   but are not representations.
   The only example so far is that ``Boolean`` is accepted by ``int``,
   so that ``bool`` may be a Python subclass of ``int``.

Each accepted class must lead to its own ``Representation`` object,
but only the defining and adopted classes lead to the same Python type.
A Java subclass of a class accepted by a built-in type,
not bound already to a different ``Representation``,
will be treated as equivalent to its accepted base.
For example the several concrete subclasses of ``PyType``
are all bound to the one ``Representation``,
leading to Python type ``type``.

..  uml::
    :caption: Plain Java Object Pattern: Adopted Representations

    class Class<T> {}
    class AdoptedRepresentation {}
    T .right.> Class
    Class "1..*" -right-> "1" AdoptedRepresentation

    abstract class Representation {
        pythonType(o)
        javaClass()
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
For example, ``PyFloat`` instances only exist
only so that we may subclass ``float``.

