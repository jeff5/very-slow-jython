..  architecture/arch-plain-java-object.rst

.. _arch-plain-java-object:


Java ``Object`` as Python ``object``
####################################

This candidate implementation of Jython 3
differs perhaps most fundamentally from Jython 2
in treating any Java object
directly as a Python object.
In Jython 2,
every object the interpreter handles is a ``PyObject``:
those that appear to be actual Java objects
in Python are proxies for the claimed objects.

At the time of writing,
we can say that this makes some things easier
and others more difficult.
As yet there are no show-stopping difficulties.


Motivations
===========

The motivation for the "plain object" pattern
is to engage features of modern Java
intended to support dynamic language implementation,
and maximally to exploit the performance they offer.
Particular thoughts are:

#.  Guidance supporting the launch of ``invokedynamic``
    was to use Java types (``java.lang.Object``)
    rather than create a base type for the language (``PyObject``).
#.  Insisting on a ``PyObject`` base or interface for every ``object``
    necessitates a proxy for any object that isn't a ``PyObject``,
    which adds indirection to calls
    and complexity in handling identity.
#.  We expect that the JVM will optimise code
    that uses the native boxed primitives,
    better than objects we devise ouselves.
    The compiler itself mixes these boxed types freely with primitives.
    The plain object approach allows us to adopt Java boxed types
    as implementations of built-in Python types.
    For example a ``java.lang.Integer`` is adopted as a Python ``int``
    and ``java.lang.Double`` as ``float``.

Early experiments with arithmetic operations
have confirmed that the hoped for optimisation can occur.
The technique produced some small gains over Jython 2.
(Jython 2 itself is well optimised in the same circumstances.)


Essential Idea
==============

We have to explain how the run-time Python interpreter
can give Python semantics to objects on its stack,
that themselves may know nothing about Python.
(In Jython 2,
``PyObject`` is a fat base class
defining all the methods the interpreter might need,
which specific types of Python object redefine.
Every object the Jython 2 interpreter handles
must be "Python aware".)

Solution Architecture
---------------------

Python places object behaviour in the Python ``type`` of the object,
so the problem becomes how to find that from an instance:
to find ``builtins.int``, for example, from a ``java.lang.Integer``.
Our solution is to use a ``ClassValue``
to map each Java ``Class`` to an ``Operations`` object,
which can then determine the Python type (``PyType``) of the object.

..  uml::
    :caption: Plain Java Object Pattern

    class Object {
        getClass()
    }
    Object -right-> Class

    abstract class Explicit
    abstract class Implicit

    Object <|--- Explicit
    Object <|-- Implicit

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

Why do we not simply map the Java class to the type in one step?
For some Java classes,
the ``Class`` is enough to determine the ``type``.
In others,
including all those where the Python type may be changed,
the type must be a property of the object itself,
and all instances of the interchangeable classes
must have the *same* Java class.
For this reason,
``PyType Operations.type(Object)`` takes the object instance as an argument.

The classes ``Explicit`` and ``Implicit`` are not real classes
in the implementation.
Rather ``Explicit`` represents any object implementation that
explicitly designates a type object,
while ``Implicit`` represents a Java object where
the Python type is implicit in the Java class.

In the implicit case,
the ``Operations`` object that we reach may itself be
the ``PyType`` representing a Python type object.
If the Java class is one of several implementations of a Python type,
each must have its own instance of ``Operations``.
One will be the actual ``PyType``,
while the others are (sub-classes of) ``Operations``
from which the actual type may be determined.

In the explicit case,
the Java class may have to implement many distinct Python types,
and in that case the ``Operations`` object is just a trampoline
to get us the actual type.



The ``Operations`` object as a method cache
-------------------------------------------

As we have remarked,
the behaviour of an object is expressed in its Python type.
This behaviour is codified (substantially) in the descriptors
found in the dictionary of the type.
For example,
if the interpreter needs to add an ``int`` and any other value,
``int.__add__``
(a descriptor in the dictionary of the type ``int``)
contains the mechanism.
This is a central part of the language and must be the same in Jython.

In CPython,
the descriptor ``int.__add__`` invokes ``long_add``,
the function in C that actually implements the addition.
If the target is a user-defined class ``MyInt`` rather than ``int``,
and ``MyInt`` defines a method ``__add__(self, other)``,
then the descriptor invokes that.

What we describe is at least the surface appearance.
Beneath the surface of CPython,
a type object is provided with pointers
to the C functions most commonly needed by the interpreter.
For example, there is an ``nb_add`` "slot"
that the interpreter calls when it needs addition.
It goes directly rather than via the descriptor.
The ``nb_add`` slot of an ``int`` is set to ``long_add``
by static initialisation in the source code,
while the descriptor is a wrapper (created later)
that takes its value from there.
Conversely,
the ``nb_add`` slot of the type object ``MyInt``
contains the opposite kind of wrapper:
the descriptor comes first and the slot contains a function to invoke it.

We do not have to reproduce the CPython patterns
beneath the surface of Jython,
but we find in them a useful set of concepts
from which to start.

We have the Java ``MethodHandle`` available
as the equivalent of the C function pointer.
This is the obvious way to define type object "slots",
if we do not approach methods exclusively via their descriptors.
(Jython 2 did not have this possibility when designed.)
However,
we do not have to define slots at all,
or could choose different ones.

``MethodHandle`` will figure prominently when we use ``invokedynamic``
in code compiled to Java byte code.
As we need to interpret Python byte code too,
we will define slots similar to those in CPython,
so that we can follow similar logic in the implementation.

Note however that we must provide each operation
for each implementation of the given type,
so that the ``self`` argument has the correct Java type.
Descriptors must therefore contain
a handle corresponding to each implementation class.
When we embed these handles in the type object,
we actually place them in the ``Operations`` object
corresponding to the Java class of the implementation.


The broad classes of ``object``
-------------------------------

We shall have to support five broad categories of Java class
in relation to this model.
A Java class may be:

#.  the crafted implementation of a Python type.
#.  an adopted implementation of a Python type.
#.  the crafted base for Python sub-classes of a Python type.
#.  a found Java type.
#.  the crafted base of Python sub-classes of a found Java type.

By *crafted* we mean that the class was written with the intention of
implementing a Python type.
Normally there will be one Java class for a given Python type,
known as the "canonical implementation".
It will create a ``PyType`` from a specification
during static initialisation.
(The ``PyType`` is also the ``Operations`` object for the class.)
Instances of the Java class are instances of the Python type,
or of a sub-type,
and reference their specific type as an instance member.
The attributes the type exposes to Python
will be specified by a combination of static data,
annotations on methods and methods with reserved names.

By *adopted* we mean that although we had no opportunity to craft
the class as a Python object,
instances of that class will be accepted in the interpreter as
instances of a particular Python type.
The methods that define the Python behaviour of an adopted implementation
may be be defined in the canonical implementation of the type in question.
That class will declare the adoption when it specifies the ``PyType``.
Each adopted Java class will be mapped to an ``AdoptedOps`` object,
that leads to the particular ``PyType`` it implements.

For example,
``java.lang.Integer`` is adopted as an implementation of ``int``,
as is ``java.math.BigInteger``.
These are given Python behaviour by methods in ``PyInteger``
and related classes.
``PyInteger`` adopts ``java.lang.Integer`` and ``java.math.BigInteger``
when it specifies the type ``int`` during its static initialisation.
``PyInteger`` is the canonical implementation of ``int``,
that is, the Java class from which
implementations of the Python sub-classes of ``int`` are derived.

All other Java classes are *found* types,
to be exposed to Python according to Java conventions.
An ``Operations`` object, that is a ``PyType``,
will be created as each such type is encountered.
There is a potential race hazard here:
during initialisation of the run-time we must ensure that
all adoptions take place before the same class may be found
by another route.

The "crafted base of Python sub-classes of a found Java type"
is a crafted object that results from extending a found type in Python.
This is the result of mentioning an imported Java class
amongst the bases in a Python class definition.
(We expect to do this dynamically at run-time.
This feature may be unavailable in environments that restrict
the definition of classes dynamically.)

In the rest of this section,
we illustrate the main possibilities offered by this object model
through a series of instance diagrams.


Canonical Implementation
========================

In the simplest case, there is only one implementation class,
that has been crafted to represent one Python type,
where the association of an instance to the type cannot be changed,
i.e. the ``__class__`` attribute may not be written.
The built-in type ``bytes`` makes a good example.

Example of ``len(b'abc')``
--------------------------

We'll consider how a call is made on ``bytes.__len__``,
which is implemented in Java by ``PyBytes.__len__``.

..  uml::
    :caption: ``bytes`` has a single implementation class

    object "b'abc' : PyBytes" as x
    object "PyBytes : Class" as PyBytes.class
    object "bytes : PyType" as bytes
    object " : MethodHandle" as mh {
        method = __len__
        type = (Object)int
    }
    bytes --> bytes : type
    bytes --> mh : op_len
    mh --> PyBytes.class : target

    x -up-> PyBytes.class : <<class>>
    PyBytes.class -right-> bytes : ops

In this case,
the ``Operations`` object is itself the ``PyType``.
How this mapping is created,
and how the method handle is formed around ``PyBytes.__len__()``,
is a long story.
For the time being,
the reader should accept that these structures have been set up.

Suppose that,
in the context of this object structure,
some program needs to ask the length (size) of ``x = b'abc'``.
The program calls the ``len()`` built-in function,
which must find and call ``__len__`` as defined for ``bytes``.

Abstract API
''''''''''''

The design for using the special method slots follows that of CPython.
There is an abstract object API
that wraps invocations of the method handles in error-handling
and other logic.
For us, the implementation is through static methods in class ``Abstract``.
The wrapping of ``__len__`` looks like this:

..  code-block:: java

    public class Abstract {
    // ...
        // Compare CPython PyObject_Size in abstract.c
        static int size(Object o) throws Throwable {
            try {
                return (int)Operations.of(o).op_len.invokeExact(o);
            } catch (Slot.EmptyException e) {
                throw typeError(HAS_NO_LEN, o);
            }
        }
    // ...
    }

The implementation only has to look up
the operations object for the argument ``o``,
and invoke the method handle found in the particular slot.
Slots that are "empty",
meaning that the corresponding special method is not defined,
are not ``null``,
but contain a handle to a method that throws the ``EmptyException``.
That way, we need not look before we leap,
and the error-handling logic may be kept out of the main path.

Our slots are named ``op_something``,
where the corresponding method is named ``__something__``.
This is more regular than CPython and we do not have quite the same ones.
They have package-private visibility.
We use ``invokeExact`` so that Java does not waste time on type coercion
with Java semantics.

Slots must be invoked with the correct number and type of arguments,
and with the correct expected return type
(here expressed in the cast to ``int``).
This correctness is a run-time check in ``invokeExact``,
but when we form call sites,
correctness is guaranteed when binding the target method.
The allowable signature for each slot is defined by ``enum Slot``,
which also provides some services for manipulating them.

Sequence of calls
'''''''''''''''''

A call to ``Abstract.size()`` on a Python ``bytes``
proceeds like this:

..  uml::

    hide footbox

    boundary "len()" as prog
    control "Abstract" as api
    participant "Operations" as ops
    participant "bytes : PyType" as bytes
    participant "mh : MethodHandle\n = PyBytes.~__len__" as mh
    participant "x : PyBytes\n = b'abc'" as x

    prog -> api ++ : size(x)
        api -> ops ++ : of(x)
            ops -> x ++ : getClass()
                return PyBytes.class
            ops -> ops ++ : fromClass(PyBytes.class)
                return bytes
            return bytes
        api -> bytes ++ : .op_len
            return mh
        api -> mh ++ : invokeExact(x)
            mh -> x ++ : ~__len__()
                return 3
            return 3
        return 3
    prog -> prog : Integer.valueOf(3)

``Operations`` provides a static ``Operations.of()``, where we consult
the ``ClassValue`` that maps to the ``Operations`` object for ``PyBytes``.
In this case,
the return happens also to be the type object ``bytes`` itself.

The signature of ``Abstract.size``,
specified by ``Signature.LEN``
(to which any ``Operations.op_len`` must conform)
requires it to return a primitive Java ``int``.
``len()`` must return a Python object,
so there is a final step in which
we wrap the result as a ``java.lang.Integer``.
Java will do this implicitly in most circumstances.

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
