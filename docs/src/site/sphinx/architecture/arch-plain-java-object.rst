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
    class Class { }
    Object -right-> Class

    interface PyObject {
        getType()
    }

    abstract class Crafted { }
    abstract class Other { }

    PyObject <|.right. Crafted
    Object <|--- Crafted
    Object <|-- Other

    abstract class Operations {
        {abstract} type(o)
    }
    Class -right-> Operations

    class PyType { }
    Operations <|-- PyType
    Crafted -right-> PyType : type

    class AdoptedOps { }
    Operations <|-- AdoptedOps
    AdoptedOps "*" -- "1" PyType


The classes ``Crafted`` and ``Other`` are not real classes
in the implementation.
Rather ``Crafted`` represents any object implementation that
explicitly designates a type object,
while ``Other`` represents any other object type.


Discussion
----------

Every ``Object`` has a ``Class``, of course,
which we do not normally show in class diagrams,
but it has an important explicit purpose here.
The relationship of a ``java.lang.Class`` to its ``Operations`` object
is implemented by a ``ClassValue``,
and so the path from an arbitrary object to its ``PyType``
is via ``Object.getClass()``.

In some cases the ``Operations`` object that we reach
is itself the ``PyType`` representing a Python type object.
In others, the Java class is one of several implementations of a type,
each needing their own instance of (a concrete sub-type of) ``Operations``,
only one of which can be the ``PyType``.
Or the Java class may implement many distinct Python types,
and in that case the ``Operations`` has to go via the instance,
which must be a ``PyObject``, to get the actual type.

Some responsibilities that seem naturally to belong to ``PyType``,
belong in fact to ``Operations``,
but ``PyType`` inherits them since it extends ``Operations``.



The broad classes of ``object``
-------------------------------

We shall have to support five broad categories of Java class
in relation to this model.
A Java class may be:

#.  the crafted canonical implementation of a Python type.
#.  an adopted implementation of some Python type.
#.  the crafted base of Python sub-classes of a Python type.
#.  a found Java type.
#.  the crafted base of Python sub-classes of a found Java type.

By *crafted* we mean that the class was written with the intention of
defining a Python type,
It will implement ``PyObject`` and designate its type explicitly.
It will have static data, annotations and use reserved method names,
that will be exposed as attributes.

By *adopted* we mean that although we had no opportunity to craft
the class as a ``PyObject``,
we adopt it as an implementation of a Python type
(as ``java.lang.Integer`` is adopted as an implementation of ``int``).
The class will be bound to an ``AdoptedOps`` object,
which is not itself a ``PyType``.
From that we may reach the particular ``PyType`` it implements.

All other Java classes are *found* types,
to be exposed to Python according to Java conventions.
An ``Operations`` object, that is a ``PyType``,
will be created as each such type is encountered.

The "crafted base of Python sub-classes of a found Java type"
is a ``PyObject`` that results from extending a found type in Python.
This is the result of mentioning an imported Java class
amongst the bases in a Python class definition.
A Java class would have to be created dynamically,
if it is also to be a sub-class in Java.
This feature may be unavailable on some platforms.



Canonical Implementation
========================

In the simplest case,
there is only one implementation class for the Python type.

..  uml::
    :caption: ``bytes`` has a single implementation class

    object "b'\x01\x02\x03' : PyBytes" as x
    object "PyBytes : Class" as PyBytes.class
    object " : Operations" as bytesOps
    object "bytes : PyType" as bytesType

    x -up-> PyBytes.class
    PyBytes.class -right-> bytesOps : ops
    bytesOps -right-> bytesType : type




Mutable Type
------------

Derived and Variable Type
-------------------------


Adopted Implementation
======================

Specification
-------------

Implications for ``PySlotWrapper``
----------------------------------




Found Java Type
===============


