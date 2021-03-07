..  architecture/arch-plain-java-object.rst

.. _arch-plain-java-object:


Java ``Object`` as Python ``object``
####################################

Essential Idea
==============

The ``Operations`` object contains the information specific to a Java class,
that allows the run-time system
to treat an instance of the class as a Python object.
This includes identification of a Python type object for the instance.

..  uml::
    :caption: Plain Java ``Object`` Pattern

    class Object {
        getClass()
    }
    Object -right-> Class

    abstract class Explicit
    abstract class Other

    Object <|--- Explicit
    Object <|-- Other

    abstract class Operations {
        {abstract} type(o)
    }
    Class -right-> Operations

    class PyType
    Operations <|-- PyType
    Explicit -right-> PyType : type

    class AdoptedOps
    Operations <|-- AdoptedOps
    AdoptedOps "*" -- "1" PyType

    class DerivedOps
    Operations <|-- DerivedOps
    DerivedOps "1" ..> "*" PyType


The classes ``Explicit`` and ``Other`` are not real classes
in the implementation.
Rather ``Explicit`` represents any object implementation that
explicitly designates a type object,
while ``Other`` represents a Java object of any other class.


Discussion
----------

Every ``Object`` has a ``Class``, of course,
which we do not normally show in class diagrams,
but ``java.lang.Class`` mediates a critically important relationship
in the language implementation.
The relationship of a Python object to its ``Operations`` object
is implemented by a ``ClassValue``,
and so the path from an arbitrary object to its ``PyType``
is via ``Object.getClass()``.

In some cases the ``Operations`` object that we reach
is itself the ``PyType`` representing a Python type object.
In others, the Java class is one of several implementations of a type,
each needing their own instance of ``Operations``.
One will be the actual ``PyType``,
while the others are (concrete sub-classes of) ``Operations``
from which the actual type may be determined.
Or the Java class may implement many distinct Python types,
and in that case the ``Operations`` has to go via the instance,
to get the actual type.

Some responsibilities that seem naturally to belong to ``PyType``,
and would do so in CPython,
belong in fact to ``Operations``,
but ``PyType`` inherits them since it extends ``Operations``.

One prominent example of this is that
``MethodHandle``\s for the special methods of a type
are fields of the ``Operations`` object.
These are analogous to the table of pointers in a CPython type object,
and are the handles consulted directly by the interpreter.
In cases where more than one Java class is adopted for a Python type,
we want to go directly to the method for that class.
The descriptors for the special methods
(and all other attributes)
are in the dictionary of the type,
as required by the Python data model.


The broad classes of ``object``
-------------------------------

We shall have to support five broad categories of Java class
in relation to this model.
A Java class may be:

#.  the crafted implementation of a Python type.
#.  an adopted implementation of some Python type.
#.  the crafted base for Python sub-classes of a Python type.
#.  a found Java type.
#.  the crafted base of Python sub-classes of a found Java type.

By *crafted* we mean that the class was written with the intention of
defining a Python type.
It will designate a type,
either statically for the class or
per-instance (so it may be the base of many Python types).

A crafted implementation class specifies the type directly
through static initialisation.
The attributes the type exposes to Python
will be specified by a combination of static data,
annotations on methods and methods with reserved names.

By *adopted* we mean that although we had no opportunity to craft
the class as a Python object,
we adopt it as an implementation of a Python type.
For example,
``java.lang.Integer`` is adopted as an implementation of ``int``.
The specification in the canonical implementation class
will enumerate the classes its type adopts.
Each adopted Java class will be mapped to an ``AdoptedOps`` object,
which is not itself a ``PyType``.
From that we may reach the particular ``PyType`` it implements.

All other Java classes are *found* types,
to be exposed to Python according to Java conventions.
An ``Operations`` object, that is a ``PyType``,
will be created as each such type is encountered.

The "crafted base of Python sub-classes of a found Java type"
is a crafted object that results from extending a found type in Python.
This is the result of mentioning an imported Java class
amongst the bases in a Python class definition.
(We expect to do this dynamically at run-time.
This feature may be unavailable in environments that restrict
the definition of classes dynamically.)

We will now illustrate the main possibilities offered by this pattern
through a series of instance diagrams.


Canonical Implementation
========================

In the simplest case, there is only one implementation class,
that has been crafted to represent one Python type,
where the association of an instance to the type cannot be changed,
i.e. the ``__class__`` attribute may not be written.
In this case,
the ``Operations`` object is itself the ``PyType``.

..  uml::
    :caption: ``bytes`` has a single implementation class

    object "b'abc' : PyBytes" as x
    object "PyBytes : Class" as PyBytes.class
    object "bytes : PyType" as bytes
    bytes --> bytes : type

    x -up-> PyBytes.class : <<class>>
    PyBytes.class -right-> bytes : ops

A type enquiry ``type(b'abc')`` would request the ``Operations`` object
via the Java class,
and be returned the ``PyType``.
``PyType`` extends ``Operations``,
so it can implement ``PyType Operations.type(Object)``
to return itself (``this``), irrespective of the argument.


Mutable Type
------------

The attributes of an object,
that are defined on the type,
are provided in the type's dictionary.
The structure allows for modifying attribute entries
just as in CPython
and for preventing modification, according to rules the type imposes.

A ``PyType`` controls the modifications to its dictionary,
and may prevent certain changes or
recognise the need for follow-up actions.
A type that allows redefinition of special methods,
is thereby able to update the slots in the type
that are caches (``MethodHandle``\s, in fact) for those definitions.


Inheritance in Python from a Built-in
-------------------------------------

Suppose that ``C`` is implemented by a Java class ``K``,
and ``B``, in the MRO of ``C``, is implemented by a Java class ``J``.
We wish to allow instance methods of ``B`` to be defined in Java
and to be declared as instance methods of ``J``, as ``Object m(...)``.
Or we may opt for a type-safe ``static Object m(J self, ...)``.

Methods of ``B`` must be applicable to instances of ``C``,
because it is a sub-class.
The methods of ``J`` must therefore be applicable to instances of ``K``.
As ``J`` and ``K`` are classes (not interfaces),
it follows that ``K`` must be a Java sub-class of ``J``,
either a proper sub-class or identical with ``J`` itself.

This establishes a constraint on acceptable MROs.
We claim this is no more restrictive than the CPython "layout constraint",
and will allows us all the cases available in CPython.

We will extend this logic when we consider multiple implementations.
For now, consider that ``J`` is a unique, crafted, canonical implementation.

When we derive a new Python type ``C`` from a built-in type ``T``,
with canonical implementation class ``J``,
the instances of ``C`` are implemented by a Java class ``J.Derived``,
that is a sub-class in Java of ``J``.
All Python sub-classes of ``C`` will also be implemented by ``J.Derived``.
When ``C`` has multiple bases in Python,
they must all be implemented by ``J.Derived`` or its ancestors.


Example Sub-classing ``bytes``
------------------------------

Having considered a general case,
let's see Java supporting inheritance from a canonical base.
Imagine making some sub-classes of ``bytes``:

..  code-block:: python

    >>> class B(bytes) : pass
    >>> class C(B) : pass
    >>> C.__mro__
    (<class '__main__.C'>, <class '__main__.B'>, <class 'bytes'>,
        <class 'object'>)

The Python ``bytes`` object (a ``PyBytes`` instance)
establishes its type in the way we have already seen,
but here we also show its ancestry in ``object``,
and provide it with descendants.

The MRO may be seen running up the right-hand side of the following diagram.
The hierarchy of Java classes is shorter than the MRO,
beginning in ``PyBytes.Derived``,
at which point instances of ``B`` and ``C`` have to differentiate their types
by means of a field each instance will hold.

..  uml::
    :caption: ``B`` is a Python sub-class of ``bytes``, and ``C`` of ``B``

    ' The most base class ;) ----------------------------
    object "Object : Class" as jlo.class
    object "object : PyType" as obj
    obj --> obj : type

    jlo.class -right-> obj : ops

    ' The built in --------------------------------------
    object "b'abc' : PyBytes" as x
    object "PyBytes : Class" as PyBytes.class
    PyBytes.class -up-> jlo.class : <<super>>
    object "bytes : PyType" as bytes
    bytes --> bytes : type
    bytes -up-> obj : base

    x -right-> PyBytes.class : <<class>>
    PyBytes.class -right-> bytes : ops

    ' Derived Java class--------------------------------
    object "PyBytes.Derived : Class" as BD.class
    BD.class -up-> PyBytes.class : <<super>>
    ' object " : DerivedOps" as BD.ops
    ' BD.class --> BD.ops : ops

    ' Sub-class B --------------------------------------
    object "b'abcdef' : PyBytes.Derived" as b
    object "B : PyType" as B
    B -up-> bytes : base
    b -right-> BD.class : <<class>>
    b -right-> B : type

    ' Sub-class C --------------------------------------
    object "b'xyz' : PyBytes.Derived" as c
    object "C : PyType" as C
    C -up-> B : base
    c -up-> BD.class : <<class>>
    c -right-> C : type

The ``PyBytes.Derived`` class in the picture
is distinct from the ``PyBytes`` class,
and has its own ``Operations`` object (not shown for layout reasons).
This ``Operations`` object, however, does not uniquely identify a ``PyType``.
Rather, the object itself does so, and
the implementation of ``Operations.type(Object)`` for derived classes
will interrogate the object, which is passed as the argument.
This leads to the actual type, and therefore
the definitions of the methods stored on that type.

Method handles cached on such an object
will embed the same dereference step.


Variable Type
-------------

In general,
it is possible to re-assign the ``__class__`` attribute
in an instance of a Python sub-class.
A Java implementation must therefore provide for it,
even though particular ``PyType``\s may disallow it.
It will be evident from the preceding section that
making the object type a field of the ``JT.Derived``
makes this possible in the case of types with a built-in ancestor
of this pattern.

At present in CPython,
built-in types do not allow assignment to ``__class__``
(except accidentally as a bug).
This is an artificial correspondence that could change in future.
If we needed to allow assignment in a built-in type,
it would only be necessary to implement the built-in
in the same pattern we just illustrated.


Adopted Implementation
======================

A simple example of the adopted implementation is available in ``float``,
which adopts ``Double`` as an implementation type alongside ``PyFloat``.

..  uml::
    :caption: ``float`` adopts ``Double`` as an implementation class

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class
    object "float : PyType" as floatType

    x -up-> PyFloat.class : <<class>>
    PyFloat.class -right-> floatType : ops
    floatType --> floatType : type

    object "42.0 : Double" as y
    object "Double : Class" as Double.class
    object " : AdoptedOps" as yOps

    y -up-> Double.class : <<class>>
    Double.class -right-> yOps : ops
    yOps -right-> floatType : type

The canonical implementation class ``PyFloat``
has the ``PyType`` ``float`` as its ``Operations`` object,
while ``Double`` has an ``AdoptedOps``
where the actual Python type is indicated by a field.




Example Sub-classing ``float``
------------------------------

All the ``float`` objects we encounter in practice
will be ``Double`` not ``PyFloat``.
There is really no need to create a ``PyFloat``
(it could be abstract).
We need ``PyFloat`` only so that we can have Python sub-classes of ``float``.




Found Java Type
===============

A Found Type
------------







Example Sub-classing a Found Type
---------------------------------
