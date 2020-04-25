..  architecture/interpreter-structure.rst


Interpreter Structure
#####################

Introduction
************

Importance for Jython
=====================

In Jython,
support for multiple interpreters and threads
is critical for certain uses.

An application may create more than one interpreter
and multiple platform threads may use them.
Jython has to provide clear semantics for multiple interpreters:
initialisation, the scope of options, and
the extent to which state is shared.

In a Java application server,
applications are separated by class loaders.
There may be multiple instantiations of
objects we normally think of as unique:
the ``Class`` objects themselves and fields declared ``static`` in them.
(A single class definition found by different class loaders
creates distinct ``Class`` objects.)
And yet, there is not complete isolation: loaders are hierarchical
and definitions found by the loaders towards the root
(basically, those from the Java system libraries)
create shared definitions.
Jython would not normally be one of these, but it could be.

Threads, platform threads owned by the application server itself,
may visit the objects of multiple applications.

Jython has several times corrected its implementation
of the life-cycle of interpreter structures,
in the face of difficulties with threads and interpreters
encountered in more complex cases.
(These are not well supported by tests.)
It is a source of bugs and confusion for users and maintainers alike.


Difficulty of following CPython
===============================

In CPython,
support for multiple interpreters (sub-interpreters)
has been present in the C API for a long time.
Support for multiple threads is also well established,
at least where the platform thread is created from CPython itself.
The notion of a call-back,
a platform thread entering the runtime from outside (a thread pool, say),
is also supported,
but here the C API is hedged with warnings that suggest
this will not mix properly with the use of multiple interpreters.
In spite of much helpful refactoring and re-writing between
CPython 3.5 (when the present very slow project began)
and CPython 3.8 (current at the time of this writing),
that warning is still necessary.

It seems there may be weaknesses here
in just the place Jython needs to find a strong foundation.
In that case, the architecture of CPython cannot simply be followed
uncritically in a Java implementation.
Nor, given experience,
can we take it for granted that Jython 2.7 has it perfectly right.

As a prelude to implementing statements in sub-project ``rt1``
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
This continues to evolve in ``rt2``.

This complex subject deserves an architectural section of its own,
and here it is.



Runtime, Thread and Interpreter (CPython)
*****************************************

Critical Structures
===================

CPython defines four structures,
that Java would consider classes,
in the vicinity of the core interpreter:

* :c:type:`PyInterpreterState` holds state shared between threads,
  including the module list and built-in objects.
* :c:type:`PyThreadState` holds per-thread state,
  most notably the linked list of frames that forms the Python stack.
* :c:type:`PyFrameObject` is a Python object (type ``frame``)
  that provides the execution context for running a ``PyCode``,
  the local variables and value stack.
* :c:type:`PyCodeObject` is an immutable Python object (type ``code``)
  holding compiled code (CPython byte code)
  and information that may be deduced statically from the source.

Additionally, ``_PyRuntimeState`` is a statically allocated singleton
that consolidates global data from across the CPython executable,
and holds the interpreter (of which one is the "main" interpreter).  

An examination of the code allows us to draw the class diagram below.
Not all the relatonships are shown,
but enough to support the discussion.

..  uml::
    :caption: The Principal Runtime Structures in CPython 3.8

    class _PyRuntimeState << singleton >> {
        main : PyInterpreterState
        PyThreadState_Get()
        _PyInterpreterState_Get()
    }

    '_gilstate_runtime_state
    class GIL {
        tstate_current : PyThreadState
    }

    _PyRuntimeState --> "1.." PyInterpreterState
    _PyRuntimeState *-> GIL
    
    PyInterpreterState "1" *-- "*" PyThreadState
    PyInterpreterState -> "*" PyModule : modules

    PyModule o-> ModuleDict : md_dict

    PyThreadState -left-> Thread : thread_id

    PyThreadState *--> "0..1" PyFrameObject : frame

    PyFrameObject -right-> PyFrameObject : f_back
    PyFrameObject -left-> PyCodeObject : f_code

    PyFrameObject --> PyDictObject : f_builtins
    PyFrameObject --> PyDictObject : f_globals

    class PyFrameObject {
        / locals : Mapping
    }


The choice of data structures in this part of CPython (and Jython)
is shot through with the idea of multiple threads,
and on exploring the CPython code, one quickly encounters,
`Python's Infamous GIL`_ (Global Interpreter Lock),
a feature we *don't* want to reproduce in Java.

.. _Python's Infamous GIL:
    https://ep2016.europython.eu/conference/talks/pythons-infamous-gil

In many places where a function is called,
CPython does not pass interpreter context as an argument.
The CPython runtime provides a method ``_PyRuntimeState_GetThreadState()``,
that accesses the global ``_PyRuntime.gilstate.tstate_current``,
which is a pointer to the current ``PyThreadState``.
A thread takes the GIL by a call to ``_PyThreadState_Swap()``
that installs its own thread state.

The loop in ``ceval.c`` simulates concurrency
by creating an occasion for that swap between succesive byte codes.
This makes the operation of most byte codes atomic.
Very little hardware concurrency is possible.

The frame stack,
and all other state that should be used at a particular moment,
flow from identifying the correct thread state.
Each ``PyThreadState`` also points to the ``PyInterpreterState`` that owns it,
and so we have the correct module state for the code executing on the stack.
``_PyInterpreterState_Get()`` produces its answer
by first finding the current thread from the GIL. 


Sub-interpreters
================

When it comes to threads,
the CPython C-API is not wholly consistent,
see `Initialization Bugs and Caveats`_.
Recent work to expose sub-interpreters at the Python level in :pep:`554`
has clarified the definition and use of these structures.
But it remains necessary to caution users against
mixing sub-interpreters with the sort of manipulation of the GIL
necessary to deal with `Non-Python created threads`_.

The direction of development in this part of CPython is towards
one GIL per interpreter (``PyInterpreterState``),
so that interpreters are able to execute concurrently.
Interpreters do not share objects: each has their own memory pool
from which that interpreter's objects are allocated.
As a result, threads in different interpreters
may safely increment and decrement reference counts
protected by that interpreter's GIL from errors of concurrent modification.

In fact, interpreters do not share objects *by design*,
but it is not possible to prevent an application or extension from doing so.
In the simplest case,
an application may create two interpreters, *i1* and *i2*,
get a result *r* (a Python object) from *i1*,
and call a function in *i2* with that as argument.
Two problems now arise:

1.  When code in *i2* calls a method on *r*,
    it will execute code written in the expectation that
    the import context in which *i1*'s methods were defined,
    will be will be present.
2.  When *i2* preforms operations on *r* that could lead to its destruction,
    or the destruction of members of it
    (suppose *r* contained a list and *i2* were to delete an item),
    the wrong memory allocator might be called,
    or reference counts updated in a race with a thread in *i1*.
    The current thread only holds the GIL in *i2*.

Notice that the first of these
is a question on the meaning of the Python language,
while the second is an issue for the implementation of CPython.


Sub-interpreters for Concurrency (Two Conjectures)
==================================================

:pep:`554` exposes the current C API (with its single GIL) for use from Python.
It does not introduce a concurrency mechanism *per se*:
that requires changes to the runtime.
In the perception of many, however,
the value of the PEP is in exposing for use an API that subsequently
*will* support concurrency through sub-interpreters.

The proposal is to have one GIL per interpreter.
It would serialise threads competing in a single interprter,
except in the special cases where the GIL is explicitly released
(e.g. during slow I/O),
just as now.
How might the runtime structures change to accommodate concurrent interpreters?
It is possible to speculate as follows.

..  uml::
    :caption: Conjectured Runtime Structures in CPython GIL-per-interpreter

    class _PyRuntimeState << singleton >> {
        main : PyInterpreterState
        _PyInterpreterState_Get()
    }

    class PyInterpreterState {
        modules
        PyThreadState_Get()
    }

    'CPython calls this _gilstate_runtime_state
    class GIL {
        tstate_current : PyThreadState
    }

    _PyRuntimeState --> "1.." PyInterpreterState
    PyInterpreterState *-> GIL
    
    PyInterpreterState "1" *-- "*" PyThreadState

    PyThreadState "*" -left-> Thread : thread_id


A platform thread must have a thread state
in each interpreter where it handles objects (we think).
Unless a platform thread is confined to one interpreter,
there is a problem here:
a platform thread in need of a reference to its current thread state,
must find it in the GIL of the current interpreter.
Previously the interpreter was found through the thread state,
using the universal GIL.
How does a platform thread first establish the current interpreter?

Another possibility is to map a given platform thread to the same thread state,
whichever interpreter it appear in.
One may then quickly find the correct thread state
(as a thread-local variable perhaps).
This changes how stacks and tracebacks work,
but because the relationship to interpreter is many-to-many,
it does not alter the fundamental problem of finding the right one.
This is a question about Python, unrelated to the GIL.

..  uml::
    :caption: Conjectured Runtime Structures in CPython: State per Thread

    class _PyRuntimeState << singleton >> {
        main : PyInterpreterState
        _PyInterpreterState_Get()
    }

    class PyInterpreterState {
        modules
        PyThreadState_Get()
    }

    'CPython calls this _gilstate_runtime_state
    class GIL {
        tstate_current : PyThreadState
    }

    _PyRuntimeState --> "1.." PyInterpreterState
    PyInterpreterState *-> GIL
    
    PyInterpreterState "*" -- "*" PyThreadState

    PyThreadState "1" -left-> Thread : thread_id


A number of difficult cases may be devised,
involving threads and interpreters,
where it is not clear from current documentation or code
how CPython would deal with the circumstances.
We must make this answer well-defined in Jython,
despite the inherent multiplicity of objects.

.. _Initialization Bugs and Caveats:
    https://docs.python.org/3/c-api/init.html#bugs-and-caveats

.. _Non-Python created threads:
   https://docs.python.org/3/c-api/init.html#non-python-created-threads



Use cases
*********

Using Python once
=================
An application runs a Python script
(however complex)
possibly yielding change to the environment (files, etc.)
but the interface with the application uses only Java objects.
The Jython interpreter is a particular case.
The use may be buried in a library of which the application is not conscious.

Issues:

* Ensure invocation is trivially easy (or invisible).
* Try to ensure well-known examples (Jython Book) still work.
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

..  uml::
    :caption: Object Behaviour defined in Python

    ": Application" -> ": Py" : interp = get Interpreter
    ": Application" -> ": Py" : globals = dict()
    note right
        Does this dict need
        module context?
    end note

    ": Application" -> interp : run(module, globals)
    ": Application" -> globals : foo = get("foo")
    ": Application" -> foo : bar()
    note right
        How does foo.bar get
        module context?
    end note



An Extension Module with a Private Interpreter
==============================================

An application runs Python in its own right,
and uses a an extension module,
that (directly or indirectly) runs Python.
This case is challenging because a single platform thread
is current in two distinct interpreters.

Issues:

* Invocation should remain easy from the application's perspective.
* The library should have its own instance of the Python interpreter,
  since it may disagree with the application about configuration,
  for example,
  either may manipulate ``sys.modules`` or ``builtins.__import__``.

..  uml::
    :caption: An Extension Modle with a Private Interpreter

    ": Application" -> ": Py" : get Interpreter





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

..  uml::
    :caption: Application Server

    ": Application" -> ": Py" : get Interpreter




  
Proposed Model for Jython [untested]
************************************

In this model,
we propose a different arrangement of the critical data structures.
In particular,
we abandon the idea that a thread belongs to an interpreter.
This will be controversial.
It may solve problems latent in CPython,
that make it unable to address some of the use cases.

We have implemented this model in the ``rt2`` iteration ``evo3``,
but not tested it with multiple threads and interpreters.

..  uml::

    class Py << singleton >> {
    }
    
    Py -- "*" Interpreter

    Interpreter - "*" PyModule : modules

    PyModule o--> ModuleDict : dict

    ThreadState -left- Thread : thread_id

    ThreadState *--> "0..1" PyFrame : frame

    PyFrame -right-> Interpreter : interpreter
    
    PyFrame --> PyFrame : back
    PyFrame -left-> PyCode : code
    PyFrame --> PyDictionary : builtins
    PyFrame --> PyDictionary : globals

    class PyFrame {
        / locals : Mapping
    }




An ``Interpreter`` does not own objects [untested]
==================================================

We'll call this "untested" for now,
but it is more of an observation than a hypothesis.
Java manages the life-cycle of our objects:
we do not have to count references or manage memory.

We therefore have no need for a rule that
objects may not be shared between interpreters,
to work around CPython's approach to life-cycle.
The Python-level API for interpreters (:pep:`554`)
may provide no means to do it,
and a Java extension that shares objects may never be a valid C extension.
However, we may benefit by sharing
immutable type objects and constant values of them.

It follows that the interpreter is responsible only for "import context".


The current interpreter is known to every ``PyFrame`` [untested]
================================================================

Any code that might need to import a module,
must belong to a particular interpreter,
in order that it should access the correct import mechanism
and list of already imported modules.
Code that already holds a reference resulting from import
(typically a global variable of the same name as the module)
only needs that reference.

When we write "code" in the paragraph above,
we definitely include code compiled from CPython,
since an ``import`` statement (or call to ``__import__``) may lurk there.
But we include any Java code that is Python-aware,
and might therefore consult ``sys.modules``.
Java code written without knowing it woulkd be used in Jython is exempt.
Perhaps surprisingly,
Jython specific code (like the implementation of ``PyFloat.tp_add``)
is also exempt,
but not the same slot in another type where ``__add__`` is defined in Python.

It may be accurate to summarise that
the current interpreter must be known to any code that has a ``PyFrame``.


The function interpreter defined the current function [untested]
================================================================







A Thread may visit multiple interpreters [untested]
===================================================

We propose that ``ThreadState``
not be the property of on particular ``Interpreter``.

If we allow the mixing of objects from different interpreters,
by which we mean the mixing of functions defined by different interpreters,
it follows that a thread will pass freely from one interpreter to the next.

This makes it sound like a frequent occurrence.
It may not be, but it needs to be possible freely.
If we share immutable objects,
these will frequently be constants created by the first interpreter to run,
and wgere sub-interpreters are in use,
these objects will be frequently visited
(although the visit may not often need module context (create a ``PyFrame``).



