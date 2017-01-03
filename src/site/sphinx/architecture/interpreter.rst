..  architecture/interpreter.rst


Interpreter Structure
#####################
As a prelude to implementing statements
(see :doc:`/treepython/simple_statements`),
we attempted to get a proper structure for the interpreter:
the per-thread state,
the working state of the interpreter to which the threads belong,
and the relationship these things have to
platform-level threads (``Thread`` in Java).
Also, we began to consider execution frames,
modules,
and 'live' objects,
implemented in Python then loosed into the wider application.
This attempt examined the key objects
in the CPython 3.5 and Jython 2.7.1 implementations.

In Jython,
support for multiple interpreters, threads and class loaders,
is critical for certain uses.
In a single application (server),
there will normally be multiple interpreters from different class loaders.
In an application of any kind,
there may be more than one interpreter from the *same* class loader,
and multiple platform threads using them.
Jython has several times corrected its implementation
of the life-cycle of interpreter structures,
in the face of thread re-use (pooling)
and application re-loading.

In CPython,
the use of multiple interpreters seems not to be fully developed.
The possibility of multiple "sub-interpreters" is enunciated,
but there is also a warning against their use with multiple threads.
It seems the architecture of CPython cannot simply be copied
in a version of Python for use in that type application.

This all gets quite complex and subtle,
so the argument has been moved from where it first arose,
to this separate chapter.
At the time of this writing,
the chapter leaves a lot of question unanswered.



The Critical Structures
***********************
CPython defines four structures,
that Java would consider classes,
in the vicinity of the core interpreter:

* :c:type:`PyCodeObject` is an immutable Python object (type ``code``)
  holding compiled code (CPython byte code)
  and information that may be deduced statically from the source.
* :c:type:`PyFrameObject` is a Python object (type ``frame``)
  that provides the execution context,
  for running a ``PyCode``.
* :c:type:`PyThreadState` holds per-thread state,
  most notably the linked list of frames that forms the Python stack.
* :c:type:`PyInterpreterState` holds state shared between threads,
  including the module list and built-in objects.

The choice of data structures in this part of CPython and Jython
is shot through with the idea of multiple threads.
Also, in exploration of the CPython code, one quickly encounters,
`Python's Infamous GIL`_ (Global Interpreter Lock),
a feature we *don't* want to reproduce.

.. _Python's Infamous GIL:
    https://ep2016.europython.eu/conference/talks/pythons-infamous-gil

When it comes to threads,
the CPython C-API is not wholly consistent,
see `Initialization Bugs and Caveats`_.
In places it assumes that a "platform" thread and a ``PyThreadState``
relate one-to-one,
but the implementation does not appear to guarantee this is true.
Nothing could prevent the creation of platform threads unknown to Python,
but it is also curiously easy to create multiple ``PyThreadState``\ s,
and multiple ``PyInterpreterState``\ s,
for a single platform thread.

In many places where a function is called,
CPython does not pass interpreter context as an argument,
but relies on a global pointer to the current ``PyThreadState``,
that changes according to the OS thread that holds the GIL.
The ``PyInterpreterState``, the frame stack, and hence
all other state flow from that.
In a properly concurrent environment,
a platform thread should be able to locate "its" ``PyThreadState``.
We must make this answer well-defined in Jython,
despite the inherent multiplicity of objects.

.. _Initialization Bugs and Caveats:
    https://docs.python.org/3/c-api/init.html#bugs-and-caveats


A Deeper Look
*************


``PyCode``
==========

:c:type:`PyCodeObject` (type ``code``)
holds compiled code (CPython byte code),
such as from a module or function body.
It holds information that may be deduced statically from the source,
such as the names of local variables and function arguments.
For the TreePython version, "compiled code" is the AST itself,
or possibly a transformed version of it,
and the other information can be derived from the AST.
Later we want the compiled code to be a JVM class definition,
and to answer the architectural questions that may raise.

``PyFrame``
===========

:c:type:`PyFrameObject` is a Python object (type ``frame``)
that provides the execution context
for one invocation of a function,
or of a module or class definition while it executes.
It holds the values of local variables named in the associated code object,
the Python VM arithmetic stack,
and all local execution state.
A chain of frames forms the Python execution stack of one thread.

The ``PyFrame`` holds argument values and local variables,
in CPython the arithmetic stack (not necessary for Java),
and any state associated with a particular execution of the code.

A ``PyFrame`` also exists disconnected from the thread state:

* when it is new (e.g. during a function call before the *push*);
* as part of a trace-back (in an exception, say); or
* as the state of a generator or coroutine.

The actions of the interpreter are, essentially,
operations on the current ``frame``,
and a call creates a new frame to act upon,
leaving interpreter state suspended in the calling frame.
It is therefore attractive to identify interpreter actions
as methods on the ``frame`` object,
rather than as static methods following CPython.

The architectural question for Python compiled to JVM byte code will be
how this relates to a JVM frame,
and the variables it holds.


``ThreadState``
===============

:c:type:`PyThreadState` represents a thread of execution.
It holds the linked list of frames (execution context in Python),
information about exceptions raised and their trace-back,
and a dictionary of thread-local values.
Most importantly, it is the double of an operating system thread.

The architectural questions in JVM Python are
how to achieve the same for ``java.lang.Thread``, and
how to make use of Java concurrency and garbage collection.

Whenever a C function is called in CPython,
any local reference code may have to execution context
(like the Python frame stack or the module list)
will go out of scope.
In a single-threaded implementation,
global variables are sufficient to preserve this context.
When threads were introduced into Python,
and the first attempts made to remove the GIL,
the scattered global state was concentrated into ``PyThreadState`` and
``PyInterpreterState`` (see
`It isn't Easy to Remove the GIL`_
and `changes for separate thread state management`_).
A global variable,
a pointer to a ``PyThreadState``,
designates the operative instances of these types.
The pointer is changed whenever another (Python-aware) thread takes the GIL.
A CPython ``PyThreadState`` holds a reference to
the interpreter (a ``PyInterpreterState``) to which it belongs,
so a reference to the current ``PyThreadState`` leads to everything needed.
For a given thread state, in CPython,
it appears that the interpreter never changes.

.. _It isn't Easy to Remove the GIL:
    http://www.artima.com/weblogs/viewpost.jsp?thread=214235
.. _changes for separate thread state management:
    https://hg.python.org/cpython/rev/b7871ca930ad

The context lost during a call,
is restored from the global variable on re-entry to the CPython
evaluation loop in :c:func:`PyEval_EvalFrameEx`,
and again in many supporting functions.
Another approach would be to pass along in each call,
a reference to the current thread state.

Where CPython has ``PyThreadState``,
Jython has a ``ThreadState`` class,
with roughly the same responsibilities.
The thread state and an associated interpreter are designated via
a thread-local variable,
hence, any (Python-aware) thread can locate its ``ThreadState``.
Jython ran into some difficulties with this technique,
in web application servers that re-use threads from a pool,
where this thread-local object has tended to keep alive
classes that should have been unloaded.
This has been fixed,
but seemingly at the cost of significant complexity.
In a puzzling difference from CPython,
Jython makes frequent use of methods that set the interpreter
for the current thread,
creating a new interpreter if necessary,
or locating a global default interpreter.

However, things are genuinely more complicated in Jython than CPython.
In Jython we would like to create objects in Python,
then use them from Java code,
perhaps in threads unknown to the Python interpreter instance that made them.
What if the manufacturing interpreter has exited,
or even been unloaded?
Or should the continued existence of objects also preserve the interpreter?


``PySystemState``
=================

:c:type:`PyInterpreterState` represents an instance of the interpreter,
that owns multiple threads.
(Each thread points back to its owning interpreter,
so that one may navigate from the OS thread,
to the Python thread,
to the interpreter instance.)
The interpreter instance holds references to key universal name spaces,
the global name space,
the ``sys`` module,
the module list itself, and
standard codecs.
In principle, there could be multiple instances concurrently,
but many applications manage fine with one.


In CPython,
``PyInterpreterState`` aggregates state shared between threads:
a list of the ``PyThreadState`` objects themselves,
some configuration information,
and the loaded modules -- particularly the built-ins and ``sys`` module.
``PyThreadState`` objects reference their shared ``PyInterpreterState``

In Jython, each ``ThreadState`` references a ``PySystemState``.
In other parts of Jython, and in textbook examples,
this is used similarly to CPython ``PyInterpreterState``.
However, it compounds that (apologetically) with the ``sys`` module,
so that one can also find here such gems as ``sys.float_info``
and ``sys.copyright``.

When is it useful to have more than one instance of the interpreter
within a single process or a single JVM?

Might such an application need more than one interpreter *per class loader*.
If only one is needed per class loader,
it could be a singleton ...
except singletons are bad (sometimes).
An API avoiding singletons has the following features:

* The client *must* obtain an interpreter before using Python features,
  and hold a reference to it for as long as Python is to be used,
  in order to keep the interpreter state alive.
* (POSSIBLY) Objects resulting from the execution of a particular interpreter,
  that rely on Python for their behaviour,
  contain a reference to that interpreter instance.
* (OR) Objects resulting from the execution of a particular interpreter,
  that rely on Python for their behaviour,
  will use a common/default interpreter instance for execution,
  which may have to be created within their class loader.

In order to cope with multiple interpreters,
I need each Thread
to have one thread state
in each system state that it enters.
However,
this calls into question the parallel with CPython,
in which a simple call to get a (thread local) thread state
also gets me the right interpreter system state.

Use cases
*********
Using Python once
=================
An application runs a Python script
(however complex)
possibly yielding change to the environment (files, etc.)
but the interface with the application uses only Java objects.
The Jython interpreter is an example.
The use may be buried in a library of which the application is not conscious.

Issues:

* Ensure invocation is trivially easy (or invisible).
* We may not want a global, static interpreter instance,
  hanging around indefinitely.


Object Behaviour defined in Python
==================================
An application implements some of its functionality in Python,
creating objects whose implementation is in Python modules,
so that method calls on them invoke code defined in Python.
An example is an application calling many small functions,
defined by executing Python modules.

Issues:

* An interpreter must be found each time a function is called.
  We don't want to create one from scratch each time.
* We don't want a global, static interpreter instance,
  hanging around indefinitely.
* From the application's perspective,
  it may simply create an object and use it,
  without consciously initialising Python.


Using Python and a Python-based library
=======================================
An application runs Python in its own right,
and uses a library that runs Python.

Issues:

* Invocation is should remain easy from the application's perspective.
* The library should have its own instance of the Python interpreter,
  since it may disagree with the application about configuration,
  for example,
  either may manipulate ``sys.modules`` or ``builtins.__import__``.


Application Server
==================
The user application runs in a Java application server
(like Apache Tomcat)
in which user applications are not processes but segregated by class loader,
and threads are re-used.

Issues:

* Thread local data and class values created in one application
  may still present for other applications.
* Class values attached to persistent classes are not disposed of.
* Approaches designed to ensure objects are not retained
  (e.g. use of weak references)
  may result in discarding state when it is still wanted.

