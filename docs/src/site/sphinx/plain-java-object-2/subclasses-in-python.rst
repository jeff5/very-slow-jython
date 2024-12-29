..  plain-java-object-2/subclasses-in-python.rst

.. _Subclasses-in-Python:

Subclasses in Python
********************

In a previous section we provided instance models for some "crafted" types,
showing how they can be described by our Python type system.
We sketched an approach to subclasses in Python,
creating a single shared representation for subclasses of a given built-in.
This was the the "derived" class,
that subclasses the crafted built-in type.

The "derived" class pre-allocates references for
a slots array and an instance dictionary ``__dict__``.
In this way, we avoided the synthesis of a new Java class
in response to each class definition in Python.
This much we had already done in ``rt3``.

This looked like it would be sufficient
where the last Java base was a built-in type.
When we attempt to extend the idea to "found" Java classes,
and their subclasses in Python,
we find the idea fails.
There is no limit to the classes and combinations of interfaces
that could be required by the bases of a class defined in Python,
for which there would have to be a pre-allocated derived class.

We therefore cannot avoid synthesising Java classes dynamically,
matching the requirements of arbitrary bases.
Since we have to synthesise a class in those circumstances,
we drop the idea of a representation derived in advance
and turn to solving the synthesis of representations on demand.

We will do this first for subclasses of built-in types,
and address found Java types in a later chapter.
Some of the apparatus we need already exists in VSJ3.


Implementation to Pull from VSJ3
================================

Use of Special Methods
----------------------
In order to create an instance of a subclass defined in Python,
we shall normally call the type object,
that is, invoke its ``__call__`` special method.
This in turn must invoke ``__new__`` for the class being instantiated
and ``__init__`` on the instance.
Somewhere in the implementation of ``__new__``,
we shall have to find the Java constructor of the representation class,
which of course did not exist
when we wrote the ``__new__`` of the base built-in type.

The state of "type slots" and special method exposure in VSJ4,
as we left it at the end of the previous chapter,
was quite weak relative to accomplishments in VSJ3.
Since we need them now,
our first task will be to pull relevant parts of that forward.
There will be change, of course,
as our ideas about special methods and their cached handles have changed.

Descriptors for Special Methods
-------------------------------
When we addressed special methods in VSJ3,
we created handles to cache in the ``Representation``
(there called the ``Operations`` object),
and a "slot wrapper" descriptor to call them from Python.
We ducked the question of
how we would put a handle in the cache that calls a Python object.
(The concepts follow CPython, which has both mechanisms.)

In VSJ4 we see the descriptor as the fundamental object,
whether that descriptor is for a method defined in Java or for one in Python.
When API supporting the interpreter needs a special method,
the definitive version is found by lookup on the type.
We will choose to cache handles to certain special methods,
so we must work out how to construct the handle.

We rename the VSJ3 enum ``Slot`` to ``SpecialMethod``,
consistent with the approach.
We keep the historical names
``PyWrapperDescr`` and ``PyMethodWrapper``,
because they are visible in Python as
``<class 'wrapper_descriptor'>`` and ``<class 'method-wrapper'>``.


Type Exposer
------------
The type exposer (from VSJ3)
scans the Java implementation classes of built-in types
for the methods that give a type behaviour.
We can re-use this almost verbatim.

The exposer drags with it a lot of the VSJ3 core types
(descriptors and methods that we need anyway, and some we don't yet),
and parts of the abstract API.
We try to minimise the number of new types by commenting out
large chunks of the type exposer.
We do not carry this to the detail level,
because of the work to reverse it all.

We run the type exposer from the type factory,
when we are making a Java-ready type Python-ready.


Abstract API
------------
The Abstract API consists mostly of methods (for example ``Abstract.repr``)
that wrap a call to a special method.
As in CPython,
they provide support to the interpreter and to extension modules.
In Jython they will also support call sites.
They are ``static`` methods in a small number of classes,
of which the first is the ``Abstract`` class.

The implementation of ``Abstract.repr`` that we import from VSJ3
looks like this:

..  code-block:: java

    public Abstract { // ...
        public static Object repr(Object o) throws TypeError, Throwable {
            if (o == null) {
                return "<null>";
            } else {
                Operations ops = Operations.of(o);
                try {
                    Object res = ops.op_repr.invoke(o);
                    if (PyUnicode.TYPE.check(res)) {
                        return res;
                    } else {
                        throw returnTypeError("__repr__", "string", res);
                    }
                } catch (Slot.EmptyException e) {
                    return String.format("<%s object>",
                            PyType.of(o).getName());
                }
            }
        }

This design assumes that a method handle on ``type(o).__repr__``
is cached in the ``Operations`` object.
``__repr__`` is one of the special methods
we shall probably cache on our ``Representation`` object,
but what if it were not cached?
What is the VSJ4 idiom for this kind of code?

We'll begin by defining in ``Representation``,
renamed from ``Operations``,
the caches necessary to satisfy references in code we port.
We also crudely convert everything else
by renaming and global replacement.
This makes the code compile but not pass tests,
as we don't know how to fill the caches.


Exceptions
----------
Note that the ``repr`` method also throws ``TypeError``,
constructed by the call to ``Abstract.returnTypeError``.
We suspect that the approach to exceptions in VSJ3,
which we have temporarily followed in VSJ4,
is inconsistent with our architecture for subclasses.
As we are porting in a lot of code that raises Python exceptions,
we should try to get exceptions right
before we add or port much that depends on the legacy approach.

We port ``Abstract`` from VSJ3 partly to support code we need,
but also because it raises questions of idiom and API fit,
to help us design the special method API.


Exceptions
==========

A Serendipitous Detour
----------------------

As we are more or less forced into it,
we start by improving our implementation of some standard Python exceptions.
Initially this looks like a diversion,
but it turns out to be a helpful study.

The inconvenience of exceptions,
for a Java implementation of Python,
is that these class assignments have to work:

..  code-block:: python

    class BE(BaseException): __slots__ = ()
    class TE(TypeError): __slots__ = ()
    class EOF(EOFError): __slots__ = ()

    BE().__class__ = TE
    EOF().__class__ = BE
    BE().__class__ = EOF

Since we cannot change the Java class of an object at runtime,
the representation of ``BE``, ``TE`` and ``EOF`` instances
must be the same Java class.
This extends to quite a few Python classes
in the exception hierarchy.

Note that the Java representation of a Python subclass
must extend a representation of its base (the canonical class).
Our observations imply this is the same for
``BaseException``, ``Exception``, ``TypeError`` and ``EOFError``.
The canonical base does not have to be the only representation,
but for simplicity we'll do that and give them all the same representation.

Observe that the following is supported (but discouraged) in Python:

..  code-block:: python

    class SwissArmyException(TimeoutError, BrokenPipeError, GeneratorExit):
        pass

In Python, we do not find these bases on the same path to ``BaseException``:
there is "diamond inheritance" going on.
The Java representation classes of bases in a Python class definition
must lie along a single inheritance chain (path to ``Object``).

Not *every* exception class has the same representation.
The descendants of ``NameError``, for example, form a clique
distinct from the ``BaseException`` clique:

..  code-block:: python

    >>> class NE(NameError): __slots__ = ()
    >>> class UBE(UnboundLocalError): __slots__ = ()
    >>> UBE().__class__ = NE
    >>> UBE().__class__ = TE
    Traceback (most recent call last):
      File "<pyshell#84>", line 1, in <module>
        UBE().__class__ = TE
    TypeError: __class__ assignment: 'TE' object layout differs from 'UBE'

The differing "layout", in these error messages, for us means Java class.
So the Java representation of ``UBE`` is the same as ``NE``,
but different from ``TE``, ``BE`` and their clique.

The base and subclass representations may be different,
since this sort of thing doesn't work:

..  code-block:: python

    BE().__class__ = BaseException
    Traceback (most recent call last):
      File "<pyshell#74>", line 1, in <module>
        BE().__class__ = BaseException
    TypeError: __class__ assignment only supported for mutable types or ModuleType subclasses

However, note that the reason given here is not "layout".
No "layout" difference is evident in the C ``struct``:
Python just restricts ``__class__`` assignment across the board,
for objects with statically allocated types.
Having a different representation class would enforce this.

The Bad News and the Good
-------------------------
When implementing in Java,
it would have been convenient to catch
distinct Python exceptions in separate ``try-catch`` clauses.
The VSJ3 design aimed for that,
but as ``try-catch`` only inspects the Java class,
we can now definively resign this convenience.
We're back to catching a broad category of exception,
testing its Python type,
and throwing it back if we don't want it (as in Jython 2).

We conclude instead that the entire standard Python exceptions hiererchy
must be represented in Java by a rather small hierarchy of classes.
We can infer this collection of Java classes
by reading CPython ``exceptions.c``
and noting where new fields are added.

As we have seen,
developing our ideas for subclasses and the "replaceable type"
will involve learning to create classes dynamically.
Fortunately, the exceptions provide a rich inheritance hierarchy,
which could almost have been written in Python,
but is hand-coded (in C originally, of course).
That provides a useful test of our ideas,
before we confront class synthesis.


Exceptions and their Representations
------------------------------------
The basic principle is that we must only create a new representation
if the content of the exception type is different from its parent.
In the C implementation,
the extra fields represent an extension of the C ``struct``,
and some entries in the attribute table.
For us, a Java class corresponds to each different "layout".

..  uml::
    :caption:  Java Representation Classes for Python Exceptions

    class PyBaseException implements WithClassAssignment {
        type: PyType
        dict: PyDict
        args : PyTuple
        notes: Object
        traceback: Object
        context: Object
        cause: Object
        suppressContext: boolean
        ~__new__(cls, args, kwds)
        ~__init__(args, kwds)
    }
    class PyStopIteration extends PyBaseException {
        value: Object
        ~__init__(args, kwds)
    }
    class PyNameError extends PyBaseException {
        name: String
        ~__init__(args, kwds)
    }
    class PyAttributeError extends PyBaseException {
        name: String
        obj: Object
        ~__init__(args, kwds)
    }

The base class brings a lot of attributes we can ignore for now,
except note that ``type`` is writable, in principle, and
that a tuple ``args`` essentially fossilises the positional arguments
given the constructor call and passed on to ``__new__`` and ``__init__``.



Coding ``__new__``
------------------
Very few of the exception types define their own ``__new__``.
All of the simple ones,
even those with additional fields,
rely on ``BaseException.__new__``,
with the actual type being communicated in the first argument.
The C version of this (in CPython 3.11) looks like this:

..  code-block:: C
    :emphasize-lines: 6

    static PyObject *
    BaseException_new(PyTypeObject *type, PyObject *args, PyObject *kwds)
    {
        PyBaseExceptionObject *self;

        self = (PyBaseExceptionObject *)type->tp_alloc(type, 0);
        if (!self)
            return NULL;
        /* the dict is created on the fly in PyObject_GenericSetAttr */
        self->dict = NULL;
        self->notes = NULL;
        self->traceback = self->cause = self->context = NULL;
        self->suppress_context = 0;

        if (args) {
            self->args = args;
            Py_INCREF(args);
            return (PyObject *)self;
        }

        self->args = PyTuple_New(0);
        if (!self->args) {
            Py_DECREF(self);
            return NULL;
        }

        return (PyObject *)self;
    }

Note how simple it is for CPython to get the correct type,
just ``type->tp_alloc(type, 0)`` and a cast.

We find this more difficult in Java,
since we have to locate and call a constructor of the right class,
but we can get it down to two lines
thanks to preparations we make when constructing the type object.
Everything else is shorter than in C because
we have the type exposer marshal the arguments
and we do not have to deal with memory management.

..  code-block:: java
    :emphasize-lines: 25-26

    public class PyBaseException extends RuntimeException
            implements WithClassAssignment, WithDict, ClassShorthand {

        public static final PyType TYPE = PyType.fromSpec(...);

        private PyType type;
        protected PyTuple args;
        // ...

        public PyBaseException(PyType type, PyTuple args) {
            // Ensure Python type is valid for Java class.
            this.type = checkClassAssignment(type);
            this.args = args;
        }
        // ...

        // special methods -----------------------------------------------

        @Exposed.PythonNewMethod
        static Object __new__(PyType cls, @PositionalCollector PyTuple args,
                @KeywordCollector PyDict kwargs) {
            assert cls.isSubTypeOf(TYPE);
            try {
                // Look up a constructor with the right parameters
                MethodHandle cons = cls.constructor(T, TUPLE).handle();
                return cons.invokeExact(cls, args);
            } catch (PyBaseException e) {
                // Usually signals no matching constructor
                throw e;
            } catch (Throwable e) {
                // Failed while finding/invoking constructor
                throw constructionError(cls, e);
            }
        }
    }

For this to work,
the type object for ``cls`` has to create handles on
the constructors ``__new__`` needs to call.
The expression ``cls.constructor(T, TUPLE)`` perfroms a lookup
to retrieve information on the constructor matching the arguments.
The shorthands used here designate ``PyType.class`` and ``Tuple.class``.
From the result we get a ``MethodHandle`` of the stated type,
or we raise a ``TypeError``
(or perhaps an ``InterpreterError`` when this circularity fails us).

The same idiom can be used to define the ``__new__`` method
of any built-in type that needs to instantiate its subtypes,
all the way up to ``PyObject.TYPE``.
The preparation is more complex,
considering that not every such type
takes responsibility for managing an assignable ``__class__``.
Those that do,
implement ``WithClassAssignment`` and
accept a ``PyType`` as the first argument to their constructor.
In general,
the the representation class of ``cls`` is synthetic,
but we needn't venture that far yet.


Creating a ``NameError`` Exception
----------------------------------
Imagine that a program in Python executes the line ``NameError('test')``,
or rather the equivalent compiled code.
The arguments ``NameError`` and ``test`` are on the stack and we hit
some kind of ``CALL`` opcode or call site.
What elements do we need to realise the call?

Sequence Diagram for Constructor Call
'''''''''''''''''''''''''''''''''''''

..  uml::
    :caption:  Creating a ``NameError`` Exception

    hide footbox

    boundary "NameError('test')" as prog

    participant "Callables" as api
    participant "NameError\n : PyType" as typeNE
    participant "newFunc\n : PyJavaFunction" as newFunc
    participant "PyBaseException" as PBE
    'participant "cons : MethodHandle\n = PyNameError.<init>" as cons
    participant "PyNameError" as PNE

    prog -> api ++ : call(NameError, "test")
         api -> typeNE ++ : call("test")
             typeNE -> typeNE : newFunc = lookup("~__new__")
             typeNE -> newFunc ++ : call(NameError, "test")
                    newFunc -> PBE ++ : ~__new__(NameError, ["test"], [])
                            PBE -> typeNE ++ : constructor(T, TUPLE).handle()
                                return cons
                            PBE -> PNE ++ : <init>(NameError, ('test',))
                                PNE -> PBE ++ : <init>(NameError, ('test',))
                                    return NameError('test')
                                return NameError('test')
                            return NameError('test')
                    return NameError('test')
             return NameError('test')
             note right
                 Not shown: look up and call ~__init__.
             end note
         return NameError('test')


Narrative for Constructor Call
''''''''''''''''''''''''''''''
The first stop, for interpreted Python at least,
is the abstract API support for calls,
which is in ``Callables.java``.
The callable ``NameError`` is a type object.
Logically, we invoke the ``__call__`` special method,
but ``PyType`` implements the ``FastCall`` interface,
which allows ``Callables``
to avoid forming the conventional array of arguments.

``NameError.call`` will look up ``__new__`` to invoke it
(and later, ``__init__``,
but we're omitting that from the description).

..  topic:: Earlier, in the type factory, ...

    This is the time to introduce some relevant data prepared in the type.

    As each type object is created,
    the type factory has the type exposer
    scan the implementation classes for special methods.
    So it was with ``NameError`` and its ancestors up to ``BaseException``.
    Amongst many other things,
    the type exposer will have found ``PyBaseException.__new__``.

    ``__new__`` is an extra-special method
    because it gets wrapped by the exposer in a ``PyJavaFunction``.
    When called, it will process the arguments,
    and make some checks
    before it calls its target (via a method handle).
    It puts this ``PyJavaFunction`` into the dictionary of ``BaseException``.

    Another action of the type factory was
    to interrogate each of the classes for their Java constructors.
    (It only collects the ``public`` and ``protected`` constructors
    that ``__new__`` should be able to use.)
    It makes an index of them, using their ``MethodType`` as a key,
    and stores this in the type object.

    This lookup will be accessed by the method
    ``PyType.constructor()`` passing the argument types
    the caller intends to use.

We had got as far as ``NameError.call``.
``NameError.call`` looks up ``__new__`` along its MRO.
``PyNameError`` does not define a ``__new__``
and the lookup finds that the closest definition is
in the dictionary of ``BaseException`` and calls it.

This is a ``PyJavaFunction`` wrapping ``BaseException.__new__``.
That in turn invokes its target ``PyBaseException.__new__``.
Code the exposer added gathers the arguments,
just the string ``test`` in this case,
into a ``tuple`` before passing it on.
Now we enter the Java code for ``__new__`` exhibited above.

As we can see there, ``PyBaseException.__new__``
calls back to the relevant type object,
which is ``NameError`` in this case,
to find the constructor with arguments ``(PyType, PyTuple)``.
It exists, so ``PyBaseException.__new__``
invokes the handle on it to make a ``PyNameError`` instance.
The name ``<init>`` appearing in the diagram
is just the name Java uses internally for a constructor.

This is a Java constructor,
so a skeletal ``PyNameError`` is produced,
(equivalent to the CPython ``tp_alloc`` call),
then ``PyNameError(PyType, PyTuple)``
delegates to its superclass ``PyBaseException(PyType, PyTuple)``,
which fills in ``type`` and ``args``.

Now all we need do is return half a dozen times,
until the original program is reached, returning the new ``NameError``.


The ``__init__`` of Exceptions
------------------------------
We have remarked that very few exception types have their own ``__new__``.
Apart from ``BaseException``,
just ``BaseExceptionGroup``, ``OSError`` and ``MemoryError``,
find a reason to do so.
Everything else lets ``BaseException.__new__`` do the work,
which in fact is not very much work:
store the positional arguments as a tuple and ignore the keywords.

This is because their object initialisation is in ``__init__``.
In fact most Python exception classes do not define ``__init__`` either.
Only the exception classes that define a fresh clique,
the ones that first extend the C ``struct`` "layout" with new fields,
need define ``__init__``.

For the Java implementation,
this means that where we define a new Java representation class,
we should implement ``__init__``.
Other exception classes may be defined to inherit from a base,
in exactly the way they should in Python,
and to share the representation class of an ancestor.

In CPython ``exceptions.c``, we have the following definition:

..  code-block:: C
    :emphasize-lines: 4, 8

    static int
    BaseException_init(PyBaseExceptionObject *self, PyObject *args, PyObject *kwds)
    {
        if (!_PyArg_NoKeywords(Py_TYPE(self)->tp_name, kwds))
            return -1;

        Py_INCREF(args);
        Py_XSETREF(self->args, args);

        return 0;
    }

In ``BaseException_init``,
CPython simply refreshes the tuple of positional arguments.
Recall that ``__init__`` may be called repeatedly on the same object
to re-initialise it.
The tuple has a variable interpretation depending on length,
but ``args[0]`` is usually the error message.
The call of ``_PyArg_NoKeywords``
only serves to enforce that there are no keywords
(and raise an error if there are).

The Java version is similar to CPython's:

..  code-block:: java

    public class PyBaseException extends RuntimeException
            implements WithClassAssignment, WithDict, ClassShorthand {
        // ...
        void __init__(Object[] args, String[] kwds) {
            if (kwds == null || kwds.length == 0) {
                this.args = PyTuple.from(args);
            } else {
                throw PyErr.format(PyExc.TypeError,
                        "%s() takes no keyword arguments",
                        getType().getName());
            }
        }
    }

In CPython ``exceptions.c``, we also have the definition:

..  code-block:: C
    :emphasize-lines: 7-8, 16-17, 24

    static int
    NameError_init(PyNameErrorObject *self, PyObject *args, PyObject *kwds)
    {
        static char *kwlist[] = {"name", NULL};
        PyObject *name = NULL;

        if (BaseException_init((PyBaseExceptionObject *)self,
                args, NULL) == -1) {
            return -1;
        }

        PyObject *empty_tuple = PyTuple_New(0);
        if (!empty_tuple) {
            return -1;
        }
        if (!PyArg_ParseTupleAndKeywords(empty_tuple, kwds, "|$O:NameError",
                kwlist, &name)) {
            Py_DECREF(empty_tuple);
            return -1;
        }
        Py_DECREF(empty_tuple);

        Py_XINCREF(name);
        Py_XSETREF(self->name, name);

        return 0;
    }

In ``NameError_init``,
CPython needs to preserve the error message in ``args``,
and supports an optional keyword argument ``name``,
which participates in the "did you mean" protocol.

..  code-block:: python

    >>> raise NameError("No buttons", name="lip")
    Traceback (most recent call last):
      File "<pyshell#92>", line 1, in <module>
        raise NameError("No buttons", name="lip")
    NameError: No buttons. Did you mean: 'zip'?

CPython ``NameError_init`` begins with a call to ``BaseException_init``,
which refreshes the ``args``, as we have seen,
but it must be given ``NULL`` as the keyword arguments.
Then it reprocesses the arguments with ``PyArg_ParseTupleAndKeywords``,
where this time it must supply empty positional arguments,
and finally it can store ``self->name``.

The Java version can be simpler.

..  code-block:: java

    public class PyNameError extends PyBaseException {
        // ...
        private static final ArgParser INIT_PARSER =
                ArgParser.fromSignature("__init__", "*args", "name")
                        .kwdefaults(Py.None);

        @Override
        void __init__(Object[] args, String[] kwds) {
            Object[] frame = INIT_PARSER.parse(args, kwds);
            // frame = [name, *args]
            if (frame[1] instanceof PyTuple argsTuple) {
                this.args = argsTuple;
            }
            // name keyword: can't default directly to null in the parser
            Object name = frame[0];
            this.name = name == Py.None ? null : PyUnicode.asString(name);
        }

We are using a statically created ``ArgParser``,
which places its output in an array
analogous the local variables of a function.
This means we have to cast results as we extract them.
(We do not see a reason to call ``PyBaseException.__init__``.)


Type Objects for Exceptions
---------------------------

The final piece in the exceptions implementation is
to show how we create type objects and publish them.
Exceptions that define a clique of types with a shared representation,
use the ``PyType.fromSpec`` idiom,
in much the same way as any other Python type defined in Java.

..  code-block:: java

    public class PyBaseException extends RuntimeException
            implements WithClassAssignment, WithDict, ClassShorthand {

        /** The type object of Python {@code BaseException} exceptions. */
        public static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("BaseException", MethodHandles.lookup())
                        .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                        .doc("Common base class for all exceptions"));

..  code-block:: java

    public class PyNameError extends PyBaseException {

        /** The type object of Python {@code NameError} exceptions. */
        public static final PyType TYPE = PyType
                .fromSpec(new TypeSpec("NameError", MethodHandles.lookup())
                        .base(PyExc.Exception)
                        .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                        .doc("Name not found globally."));

The novel feature is the use of ``Feature.REPLACEABLE``,
which causes the ``PyType`` and the ``Representation``
to take particular forms (replaceable type and sharable representation).
In spite of this, and the interface ``WithClassAssignment``,
it will not *actually* be possible to assign the type,
because ``Feature.IMMUTABLE`` prevents it,
until we make subclasses that allow it.
Implementing exceptions,
and subclasses in general subsequently
have driven the addition and debugging of these novel features.

For those exceptions that will join an existing clique,
all we need is a type object referencing the shared representation,
and with the appropriate base.

CPython publishes these type objects with the prefix ``PyExc_``,
so we define a class ``PyExc`` with all the exception type objects
published as ``static`` objects.

..  code-block:: java

    public class PyExc {
        // ...
        /**
         * Create a type object for a built-in exception that extends a
         * single base, with the addition of no fields or methods, and
         * therefore has the same Java representation as its base.
         *
         * @param excbase the base (parent) exception
         * @param excname the name of the new exception
         * @param excdoc a documentation string for the new exception type
         * @return the type object for the new exception type
         */
        // Compare CPython SimpleExtendsException in exceptions.c
        private static PyType extendsException(PyType excbase,
                String excname, String excdoc) {
            TypeSpec spec = new TypeSpec(excname, LOOKUP).base(excbase)
                    // Share the same Java representation class as base
                    .primary(excbase.javaClass())
                    // This will be a replaceable type.
                    .add(Feature.REPLACEABLE, Feature.IMMUTABLE)
                    .doc(excdoc);
            return PyType.fromSpec(spec);
        }

        /**
         * {@code BaseException} is the base type in Python of all
         * exceptions and is implemented by {@link PyBaseException}.
         */
        public static PyType BaseException = PyBaseException.TYPE;

        /** Exception extends {@link PyBaseException}. */
        public static PyType Exception =
                extendsException(BaseException, "Exception",
                        "Common base class for all non-exit exceptions.");

        /** {@code TypeError} extends {@link Exception}. */
        public static PyType TypeError = extendsException(Exception,
                "TypeError", "Inappropriate argument type.");

        /**
         * {@code StopIteration} extends {@code Exception} and is
         * implemented by {@link PyStopIteration}.
         */
        public static PyType StopIteration = PyStopIteration.TYPE;

        /**
         * {@code NameError} extends {@code Exception} and is implemented by
         * {@link PyNameError}.
         */
        public static PyType NameError = PyNameError.TYPE;

        /** {@code UnboundLocalError} extends {@link NameError}. */
        public static PyType UnboundLocalError =
                extendsException(NameError, "UnboundLocalError",
                        "Local name referenced but not bound to a value.");

        // ... lots more in the same pattern
    }