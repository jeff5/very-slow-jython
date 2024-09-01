..  plain-java-object-2/type-system-init.rst


Type System Initialisation
**************************

The models shown so far illustrate the behaviour of
our Python type system once the objects exist.
All the instance models refer to
state as we need to find it when looking up a type or method.
We have indicated the use of ``ClassValue`` to find
a representation via a mysterious "registry" of types.
How they were created and registered has not been explained.

At the time of this writing,
that hasn't been entirely worked out,
but we write down what we think we know anyway.
By the time the text settles down, perhaps we'll know for sure.

The integrity of the type system must be jealously guarded.
It is central to the interpreter,
and must be maintained in a correct state.
This is our first use for the Java module system:
to provide a limited API to the type system,
not exposing the inner workings to clients.
It is a major concern of the design to maintain correctness
when multiple client threads access the type system,
including during initialisation.


A Thread-Safe Type System
=========================

Type Registry and Factory
-------------------------

The API exposes the type registry through ``PyType.of``.
This is how the interpreter will locate
the Python properties and behaviour of
any object it encounters.

The type factory is exposed through ``PyType.fromSpec``,
for the creation of Python types explicitly defined in Java.
It is also present behind ``PyType.of`` so that
types may be created on demand for
Java classes that are not explicitly crafted as Python types
(the "discovered" types).

We might need more than these two ways into the type system,
but we won't create many.
With this constrained set of entrypoints,
we are able to reason safely about threads using the type system.


Strategy
--------

The strategy for thread safety is based on the well-known
thread-safe lazy singleton pattern.

We do not try to create the type system at application start-up.
In a multi-threaded application using Jython,
the thread that first tries to use the type system is
the one that brings it into existence.
This thread has no special status once it has done so.

Every action on the type system is channelled through
static methods on ``PyType``.
The factory and registry are created and initialised during
the static initialisation of ``PyType``,
by the first thread to call any of the API.
During this time,
the JVM guarantees that no other thread may enter ``PyType``.
Every competing thread is forced to wait
at the point it references a ``PyType`` method or field.

After initialisation,
static variables in ``PyType`` hold the singleton factory and registry,
which uses them to service the API.
This, of course, makes ``PyType`` a bottleneck, of a sort,
but only where type creation is involved.
The most common action,
that of finding a ``Representation`` or a ``PyType`` for
a given class or object,
once that relationship has been published,
is non-blocking because of our use of ``java.lang.ClassValue``.

Actions that create a new type are linearised:
only one thread may use the factory at a time.
A thread that asks to resolve a class to a representation and "misses",
will go on to create the missing representation in the factory.
But if the factory is busy with some other thread,
it may be busy creating the requested object.
The new requesting thread must wait to claim the factory,
and then check the registry again,
before creating the type if that is still necessary.
Note the careful ordering of locks implied in this description.


Specifying a Python Type
========================

Common Idiom
------------

We like to use the following idiom in the creation of types,
for example ``list``:

..  code-block:: java

    public class PyList {
        /** The type object {@code list}. */
        public static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("list", MethodHandles.lookup()));
        //...

A type can be complex, so
we use a specification object to collect the details,
before asking the type system to create it through ``PyType.fromSpec()``.
By convention, we make the type a ``public final static``
member called ``TYPE``.

It may be surprising that ``PyList.class`` is not
an argument in the call to ``PyType.fromSpec()``.
``MethodHandles.lookup()`` is a context-sensitive method that
captures the calling class and its access rights in a ``Lookup`` object.
Inside the type system,
this object identifies the defining class and
confers the rights necessary to expose the methods defined in it.

However, the important feature of the idiom is that
type creation takes place atomically in
the static initialisation of the representation class.
No client code will construct an instance or call a method
until static initialisation completes and therefore ``TYPE`` exists.
We just have to design ``PyType.fromSpec()`` so it finishes the type
before handing back control.

Further,
the JVM guarantees that no other thread can enter ``PyList`` until
the the initialisation completes.
A racing thread will not see a partial type object
(if the type system is well-behaved).

..  uml::
    :caption: Two Threads Compete to Use ``PyList``

    participant Thread1
    participant Thread2
    participant PyList
    participant PyType
    participant "list : SimpleType" as list

    Thread1 -> PyList ** : clinit()
        activate PyList

    Thread2 ->x PyList: f = foo()
        note right
            Thread2 is blocked because
            Thread1 owns PyList.
        end note

        PyList -> PyType ++ : TYPE = fromSpec()
            PyType -> list **
            return TYPE = list
        PyList --> Thread1
        deactivate PyList

    Thread2 x-> PyList : f = foo()
        activate PyList
        note right
            Thread2 can resume.
        end note
        PyList --> Thread2 : f
        deactivate PyList


``TYPE`` will be fully-formed by the time
this ``fromSpec`` invocation returns.

*In principle* the current thread can see ``TYPE`` incomplete or ``null``,
outside the type factory,
but only from re-entrant definitions invoked during type creation.
Types must be designed not to cross-refer during initialisation
in ways that undermine the type system guarantees.
We haven't found a reason to do this yet.


General Thread-Safety Issue
---------------------------

A problem arises when we apply the above idiom to particular built-in types.
The type system itself needs to create these types
before any other code is allowed to use it.
This means that certain types
(the *bootstrap types*)
must be created during the static initialisation of ``PyType``.
One of them is ``float``.

..  code-block:: java

    public class PyFloat {
        /** The type object {@code float}. */
        public static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("float", MethodHandles.lookup())
                    .adopt(Double.class));
        //...

In order to get ``PyType.fromSpec()`` to run,
and to present the specification that only ``PyFloat`` knows,
the type factory will request
the static initialisation of ``PyFloat`` by the JVM.
The problem is that
if another thread got there first,
the JVM will wait for it to complete.

..  uml::
    :caption: How not to Define ``float``

    participant Thread1
    participant Thread2
    participant PyFloat
    participant PyType
    participant factory

    Thread1 -> PyType ** : clinit()
        activate PyType

    Thread2 -> PyFloat **  : clinit()
        activate PyFloat

        PyFloat -> spec ** : new TypeSpec("float")
        PyFloat ->x PyType  : TYPE = fromSpec(spec)
            note left
                Thread2 is blocked because
                Thread1 owns PyType.
            end note

        PyType -> factory ** : new TypeFactory()
        PyType -> factory ++ : createBootstrapTypes()
            factory ->x PyFloat : clinit()
                note right
                    Thread1 is blocked because
                    Thread2 owns PyFloat.
                end note


The deadly embrace continues indefinitely.


Special Idiom for Bootstrap Types
---------------------------------

Our solution for this is relatively simple.
It just requires the type system and the bootstrap object implementations
to co-ordinate.

The type system itself holds the specification for each bootstrap type,
not the defining class itself.
The type factory is able to create and publish each type (here ``float``)
during the static initialisation of ``PyType``,
and without requiring the static initialisation of ``PyFloat``.
When ``PyFloat.TYPE`` is needed,
it is obtained by asking the type system for it.

..  code-block:: java

    public class PyFloat {
        /** The type object {@code float}. */
        public static final PyType TYPE = PyType.of(0.0);
        //...

Any other thread that tries to access ``PyFloat``
during the time that the type system is being created,
will begin the initialisation of ``PyFloat``
and immediately block on the call to ``PyType.of()``.

..  uml::
    :caption: How to Define ``float`` safely

    participant Thread1
    participant Thread2
    participant PyFloat
    participant PyType
    participant factory
    participant spec
    participant registry

    Thread1 -> PyType ** : clinit()
        activate PyType

    Thread2 -> PyFloat ** : clinit()
        activate PyFloat
        PyFloat ->x PyType  : TYPE = of(0.0)
            note right
                Thread2 is blocked because
                Thread1 owns PyType.
            end note

        PyType -> factory ** : new TypeFactory()
            activate factory
            factory -> registry ** : new TypeRegistry()
            factory --> PyType
            deactivate factory

        PyType -> factory ++ : createBootstrapTypes()
            factory -> spec ** : new TypeSpec("float")
            factory -> factory : float = fromSpec(spec)
            factory -> registry : publish(float)
            return
        PyType --> Thread1
        note right
            Thread2 can resume.
        end note
        deactivate PyType

        PyFloat x-> PyType : TYPE = of(0.0)
            activate PyType
            PyType -> registry : t = lookup(Double.class)
            PyType --> PyFloat: TYPE = t
            deactivate PyType
        PyFloat --> Thread2
        deactivate PyFloat

The blocked state of the second thread is transient.
It does not hold any resource the initialisation of the type system needs.
It will resume when the type system is ready to answer the type enquiry.


Bootstrap Types
---------------

The bootstrap types are:

 * The primordial types ``object`` and ``type``
 * Descriptors (without which no ``type`` object can be complete).
 * Adoptive types.

The first group are so fundamental (and entangled) that they have to be
created with the type factory, in its constructor.
The others are created during the static initialisation of ``PyType``.
We include the built-in adoptive types to avoid automatic creation of
type objects for the adopted Java classes.
(E.g. a call to ``PyType.of(42)`` before ``int`` exists would create
a type representing just ``java.lang.Integer``.)





