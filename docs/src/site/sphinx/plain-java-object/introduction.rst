..  plain-java-object/introduction.rst


Introduction
############

Here we will test the idea that we may treat any Java object
as a Python object,
starting afresh on a third model run-time system
in sub-project ``rt3``.

Motivations
===========

Our primary emphasis has been, and remains,
whether a viable Python interpreter may be made in Java,
following the patterns we have chosen to explore.
These patterns are intended to engage the Java features that
support dynamic language implementation.
We have avoided doing much to optimise performance in detail.
However, our interest in exploring particular patterns
and using those features effectively
is driven ultimately by the performance they offer.

The particular concerns in the ``rt3`` sub-project are:

#.  Use of ``java.lang.Object`` in place of ``PyObject``.
    Guidance supporting the launch of ``invokedynamic``
    was to use Java types rather than create a base type for the language.
#.  Smooth integration with Java.
    Insisting on a ``PyObject`` base or interface for every ``object``
    necessitates a proxy for any object that isn't a ``PyObject``,
    which adds indirection to calls and complexity in handling identity.
#.  To allow multiple implementations of a single Python type.
    For example we might want BMP and full Unicode implementations
    of the ``str`` type, that are indistinguishable to Python.
#.  To adopt certain Java types as implementations of built-in Python types.
    For example a ``java.lang.Integer`` may be adopted as a Python ``int``,
    and the JVM then optimise code
    more effectively than when we use our own ``PyInteger``.

Concepts developed in sub-project ``rt2`` will be heavily re-used.
These include:

#.  Sub-classes of ``PyCode`` and ``PyFrame``
    to support different types of code (e.g. CPython byte code).
#.  A CPython byte code interpreter.
#.  Mechanisms for functions defined in Python.
#.  Numerous support and abstract API methods already coded.
#.  Exposure through annotations leading to descriptors
    with method and variable handles.
#.  ``Slot`` and ``Signature`` as the source of tailored descriptors and
    method handles.


Design Concept
==============

To the ideas tested in ``rt2``,
we add a new concept: the ``Operations`` object.
(It has been brewing a while.
It may even be seen as revisiting an idea present in ``rt1``.)

The ``Operations`` object contains the information specific to a Java class,
that allows the run-time system
to treat an instance of the class as a Python object.
This includes identification of a Python type object for the instance.
Some responsibilities that in ``rt2`` belonged to ``PyType``,
now belong to ``Operations``.

..  uml::
    :caption: Plain Java ``Object`` Pattern

    class Object { }
    class Class { }
    Object -> Class

    abstract class Operations { }
    abstract class PyType { }
    Class "1" -- "*" Operations
    Operations "*" -- "*" PyType

The relationship of a ``java.lang.Class`` to its ``Operations`` object
is implemented by a ``ClassValue``.

The relationship of ``PyType`` to ``Operations``,
is technically many-to-many,
but actual multiplicities amongst these objects
depends on the general type of class being described.
We shall explore in the necessary sub-classes
and patterns that arise
in subsequent sections.

We think of the ``Operations`` object primarily as
the means by which we will generate targets and guards
for ``CallSite`` objects bound to ``invokedynamic`` instructions.
We shall still approach the topic initially via CPython byte code,
but code fragments in which a ``CallSite`` appears
will be increasingly significant.


Feasibility
===========

We can see by inspection that much of the ``rt2`` code
will probably work in the new concept:
``rt2`` handles objects generically as ``PyObject`` (an interface),
and the only method of in that interface is ``getType()``.
If we can find a substitute for that single method,
we may switch from ``PyObject`` to ``Object`` in the code,
and the interpreter (or compiled code)
will begin to handle the ``java.lang.Object`` directly.

However, at the time of beginning exploration in ``rt3``,
it is not at all certain that such an approach has the benefits hoped for.

