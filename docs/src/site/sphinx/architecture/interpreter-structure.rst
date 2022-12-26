..  architecture/interpreter-structure.rst


Interpreter Structure
#####################

.. note:: This section,
    although repeatedly re-written,
    still contains much thinking aloud.
    We learn from the moving target of CPython and its PEPs,
    while trying to theorise an alternative better suited to the JVM.
    There will be less rambling argument
    when we have made it work in practice.


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
CPython 3.5 (when the present very slow project began),
through CPython 3.8 (target for now),
up to CPython 3.11 (current at the time of this writing),
that warning remains necessary
(see :ref:`CPython-sub-interpreters`).

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
This continued to evolve in ``rt2`` and now ``rt3``.

This complex subject deserves an architectural section of its own,
and here it is.



Runtime, Thread and Interpreter (CPython)
*****************************************

.. _CPython-critical-structures:

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
and holds the interpreters (of which one is the "main" interpreter).

An examination of the code allows us to draw the class diagram below.
Not all the relationships are shown,
but enough to support the discussion.
Active development of CPython in support of multiple interpreters
leads us to study CPython 3.11 in this section.

..  uml::
    :caption: The Principal Runtime Structures in CPython 3.11

    class _PyRuntimeState << singleton >> {
        main : PyInterpreterState
        PyThreadState_Get()
        _PyInterpreterState_Get()
    }

    class PyInterpreterState {
        sysdict
        builtins
        importlib
    }

    class PyModuleObject{
        md_dict : Mapping
    }

    'CPython calls this _gilstate_runtime_state
    class GIL {
        tstate_current : PyThreadState
        autoTSSkey : Py_tss_t
        autoInterpreterState : PyInterpreterState
    }

    _PyRuntimeState --> "1.." PyInterpreterState
    _PyRuntimeState *-left-> GIL
    
    PyInterpreterState "1" *-- "*" PyThreadState
    PyInterpreterState -right-> "*" PyModuleObject : modules

    Thread -> "0..1" PyThreadState : autoTSSkey
    'GIL .. (Thread, PyThreadState) : autoTSSkey

    PyThreadState *--> "0..1" PyFrameObject : frame

    PyFrameObject -right-> PyFrameObject : f_back
    PyFrameObject -left-> PyCodeObject : f_code

    PyFrameObject --> PyDictObject : f_builtins
    PyFrameObject --> PyDictObject : f_globals

    class PyFrameObject {
        locals : Mapping
    }


The choice of data structures in this part of CPython (and Jython)
is shot through with the idea of multiple threads,
and on exploring the CPython code, one quickly encounters
`Python's Infamous GIL`_ (Global Interpreter Lock).
This is a feature we *don't* want to reproduce in Java.

.. _Python's Infamous GIL:
    https://ep2016.europython.eu/conference/talks/pythons-infamous-gil

Python objects in CPython cannot safely be manipulated
by two threads concurrently,
and have no intrinsic protection against this being attempted.
Authors often mention that reference counts need this protection,
but they are not the only data at risk.

Instead of fine-grain locks on individual objects there is a global lock,
on the runtime as a whole (the GIL).
A thread takes the GIL by a call to :c:func:`PyGILState_Ensure`,
which installs its own thread state.
A thread relinquishes the GIL with :c:func:`PyGILState_Release`,
which allows another thread to handle Python objects.

A thread must take the GIL in order to handle Python objects safely.
The loop in ``ceval.c`` simulates concurrency
by creating an occasion to swap ownership of the GIL
between successive opcodes.
This makes the operation of most opcodes atomic,
all built-in functions implemented in C,
and the methods of built-in types implemented in C.

Thus, for all the apparatus there is in support of threads in CPython,
the actual effect is to ensure only one of them can run at once.
Very little hardware concurrency is possible in CPython.

A Python programmer may reliably assume that
even quite complex operations on built-in types,
for example set insertion or a list sort,
operates atomically without further safeguards.
The programmer may assume this when in fact
a definition in Python of ``__lt__`` in the class of some list items
invalidates that assumption.
But a programmer using only CPython
will mostly get away with the assumption.

Jython 2 adapts some of the CPython apparatus in its own run time system
to permit actual concurrency,
but not without some bugs arising.
We intend to support true Java concurrency in Jython 3 too,
learning what we can from recent work in CPython 3.


.. _CPython-finding-pythreadstate:

Finding the right ``PyThreadState``
===================================

The frame stack,
and all other state that should be used by a thread at a particular moment,
flow from identifying the correct thread state.
Each ``PyThreadState`` also points to the ``PyInterpreterState`` that owns it,
and so we have the correct module state for the code executing on the stack.

In places where the GIL is held,
CPython may use ``PyThreadState_Get()`` to find the current thread state.
This accesses the global ``_PyRuntime.gilstate.tstate_current``
pointer to the current ``PyThreadState``.
Similarly, ``_PyInterpreterState_Get()`` produces its answer
by first finding the current thread from the GIL.

When a platform thread does not hold the GIL these methods will not work,
and it cannot safely use Python objects.
A thread that does not hold the GIL
must call :c:func:`PyGILState_Ensure` to gain it.
CPython will first try to find a ``PyThreadState``
from thread-local storage provided by the platform for this thread.
It uses a key chosen once and held in ``gilstate.autoTSSkey``.

If there is no such storage on the current platform thread,
CPython creates a new ``PyThreadState``
on the default ``PyInterpreterState gilstate.autoInterpreterState``,
and associates this with the current platform thread.
This may happen, for example,
when a C library calls back into a CPython extension.
An artificial example is at ``temporary_c_thread()``
in ``~/Modules/_testcapimodule.c``,
and several real ones are in ``~/Modules/_sqlite/connection.c``
in the CPython code base.
A ``PyThreadState`` created this way is distinguished such that
:c:func:`PyGILState_Release` will dispose of it,
rather than leave it associated with the platform thread.


.. _CPython-sub-interpreters:

Sub-interpreters
================

When it comes to threads,
the CPython C-API is aware of its shortcomings,
see `Initialization Bugs and Caveats`_.
Recent work to expose sub-interpreters at the Python level in :pep:`554`
has clarified the definition and use of these structures behind the scenes,
as well as exposing an abstraction of them as part of the language.
But it remains necessary to caution users against
mixing sub-interpreters with the sort of manipulation of the GIL
necessary to deal with `Non-Python created threads`_.

The direction of development in this part of CPython is towards
one GIL per interpreter (in ``PyInterpreterState``),
so that interpreters will truly be able to execute concurrently.
Some of our discussion of CPython anticipates this development,
in order to contrast it with a proposed Java approach.

Interpreters in CPython do not share objects unless
those objects are immutable and effectively immortal (:pep:`683`):
each interpreter manages the lifecycle of objects it allocates.
(Draft :pep:`684` leaves open whether interpreters have separate memory pools
or use a common thread-safe allocator.)
As a result, it is safe for threads in different interpreters
to handle objects they encounter without further checks.

In fact, interpreters do not share objects *by design*,
but CPython cannot prevent an application or extension
from handing an object created in one interpreter to another interpreter.
In the simplest case,
a C application may create two interpreters, *i1* and *i2*,
get a result *r* (a Python object) from *i1*,
and call a function in *i2* with that as argument.

CPython relies on the careful construction of C extensions and applications
to avoid this.
A case is noted at the end of the section
:ref:`CPython-finding-pythreadstate`
where no amount of care seems enough.
When a thread enters extension code from the platform,
to be handled by an object belonging to *i1*
(for example in a call-back posted by *i1*),
that thread may not be associated in thread-local storage with *i1*.
It may belong to another interpreter *i2*
through an existing ``PyThreadState``,
or be given a new ``PyThreadState`` in the default interpreter.

Whatever the origin, several problems now arise:

1.  When *i2* performs operations on *r*,
    the reference count of *r* or of a member might be
    updated in a race with a thread in *i1*.
    (The current thread only holds the lock in *i2*.)
2.  Similarly, *i2* may perform operations on *r*
    that lead to its destruction (reference count zero),
    or the destruction of a member
    (e.g. if *i2* were to delete an item from a list in *r*).
    The memory would either be returned to the *i2* allocator,
    which does not own it,
    or possibly to the *i1* allocator unsafely,
    without holding the *i1* GIL.
3.  When code in *i2* calls a method on *r*,
    it will execute code written in the expectation that
    the import context in which *i1*'s methods were defined,
    will apply when it runs.

Notice that the last of these
is a question on the meaning of the Python language.
The others seem to be issues specific to CPython memory management,
whereas in Java, the JVM will take care of memory management.


Sub-interpreters for Concurrency
================================

:pep:`554` exposes the current C API (with its single GIL)
for use from Python.
It does not introduce a concurrency mechanism *per se*:
that requires changes to the runtime.
In the perception of many, however,
the value of the PEP is in exposing for use an API that subsequently
*will* support concurrency through sub-interpreters.
This has now been formally proposed in draft :pep:`684`.

The proposal is to have one GIL (not global any more)
per interpreter.
It would serialise threads competing in a single interpreter,
except in the special cases where the GIL is explicitly released.
(as now e.g. during slow I/O).

Objects are confined to a single interpreter,
at least if they are mutable (including the reference count).
They are allocated from and returned to the memory pool of that interpreter.
Threads are also confined to a single interpreter,
and restricted to access only that interpreter's objects.
Since only one platform thread can run at a time in a given interpreter,
thanks to the GIL,
only one thread may be running in a given object.

This clever scheme achieves thread-safe concurrency
without the cost of per-object locks
or much extra record keeping at all.
The down side is the constraint on the free sharing of objects:
while many threads may visit an object,
they must all be threads of the interpreter that owns that object.

How might the runtime structures change
to accommodate concurrent interpreters?
It is possible to speculate as follows:

..  uml::
    :caption: Conjecture: Structures in CPython with per-interpreter GIL

    class _PyRuntimeState << singleton >> {
        _main_interpreter : PyInterpreterState
        PyThreadState_Get()
        _PyInterpreterState_Get()
    }

    class PyInterpreterState {
        sysdict
        builtins
        importlib
    }

    class PyModuleObject{
        md_dict : Mapping
    }

    'CPython calls this _gilstate_runtime_state
    class GIL {
        tstate_current : PyThreadState
        autoTSSkey : Py_tss_t
    }

    _PyRuntimeState --> "1.." PyInterpreterState
    PyInterpreterState *-left-> GIL

    PyInterpreterState "1" *-- "*" PyThreadState
    PyInterpreterState -right-> "*" PyModuleObject : modules

    Thread -> "0..1" PyThreadState
    GIL .. (Thread, PyThreadState) : autoTSSkey

    PyThreadState *--> "0..1" PyFrameObject : frame


A platform thread could have a thread state
in each interpreter where it handles objects (we think),
since each GIL may hold a different ``autoTSSkey``.
However, the problem remains that
a platform thread in need of a reference to its current thread state,
must find it in the GIL of the right interpreter.

Previously the interpreter was found through the thread state,
using the universal GIL (see :ref:`CPython-finding-pythreadstate`).
How does a platform thread first establish the current interpreter?
It seems it would have to be the default interpreter as before.

A different approach in Jython?
===============================

A number of difficult cases may be devised
involving threads and interpreters,
where it is not clear from current documentation or code
how CPython would deal with the circumstances.
We must make this answer well-defined in Jython,
despite the inherent multiplicity of objects.

In Jython the JVM manages the lifecycle of objects.
It becomes unnecessary to tie objects to an owning interpreter
since there is only one memory pool.
We only need to know the current interpreter for its inherent state
and certain context,
such as the registry of imported modules and codecs.
In return,
we must be careful to make objects thread safe
where CPython can already assume only one thread can be present.

We will propose a different approach for Jython from CPython,
made possible because the JVM manages the lifecycle of objects.
All the same behaviour should be expected of interpreters exposed to Python
as in the C implementation.

.. _Initialization Bugs and Caveats:
    https://docs.python.org/3/c-api/init.html#bugs-and-caveats

.. _Non-Python created threads:
   https://docs.python.org/3/c-api/init.html#non-python-created-threads



Use cases
*********

We will catalogue several patterns
in which interpreters and threads might be used.
The idea is to test our architectural ideas in theory first,
in a series of use cases.
We may then prove the implementation by constructing test cases around them.
The first are somewhat trivial, for completeness only.

.. _uc-using-python-directly:

Using Python Directly
=====================
An application obtains an interpreter and gives it work to do.
It may be called to run a script (Python file)
or fed commands like a REPL.
Objects the application obtains as return values,
or from the namespace against which commands execute,
will generally be Python objects,
with behaviours defined in Python code.

The Jython 2 main program is a particular case,
and we'll need that or something similar in an implementation of Python 3.

..  uml::
    :caption: Using Python Directly

    myApp -> interp ** : new PythonInterpreter()

    myApp -> interp ++ : exec("def f(x) : return 6*x")
        interp -> f ** : new
        return

    myApp -> interp : f = get("f")

    myApp -> f ++ : call(7)
        return 42

For simplicity we show ``get()`` acting on the interpreter.
Does a separate namespace dictionary better represent the source?


Considerations
--------------

* Ensure invocation by a user application is trivially easy.
* Contrary to this, is explicit initialisation of the runtime preferable?
* Try to ensure well-known examples (Jython Book) still work.
* The client may drop ``interp`` once it has its result.

  * Quite likely the Jython runtime core is static (global),
    and not disposed of when an interpreter might be.
  * The interpreter must exist as long as needed by the objects it created.
  * But sometimes a returned object is just a Java object (``42``).

* What is the context of an ``import`` in the body of ``f``?


.. _uc-using-python-jsr-223:

Using Python under JSR-223
==========================
As previously,
an application obtains an interpreter and gives it work to do.
Possibilities are mostly as in :ref:`uc-using-python-directly`,
except that the usage is defined by JSR-223.
The `Java Scripting Programmer's Guide`_ gives examples of use,
from which we take "Invoking a Script Function" as our example
(but in Python, of course).

..  uml::
    :caption: Invoking a script function under JSR-223

    main -> manager ** : new ScriptEngineManager()
    main -> manager ++ : getEngineByName("python")
        manager -> engine ** : new
        return engine

    main -> engine ++ : eval("def f(x) : return 6*x")
        engine -> f ** : new
        return

    main -> engine ++ : f = invokeFunction("f", 7)
        engine -> f ++ : ~__call__(7)
            return 42
        return 42

In this simple case,
we take a "hands off" approach to using the object ``f``
in the name space of the interpreter,
referring to it only by its name.

Considerations
--------------

We only note those additional to :ref:`uc-using-python-directly`.

* Invocation and API are as defined by JSR-223 and the JDK.
* The ``engine`` may hold the interpreter as long as it exists.
* The Jython 2 interpretation is a little weird:
  let's think again, especially that thing about thread-local globals.


.. _uc-implementing-interface-jsr-223:

Implementing a Java interface under JSR-223
===========================================

The JSR-223 API is far richer than the previous example gives space for
(see `Java Package javax.script`_).
In another example adapted from the Programmer's Guide,
we handle an object directly that implements a specified interface.
Suppose that in a script ``r.py`` we define a class ``R`` like so:

..  code-block:: python

    from java.lang import Runnable

    class R(Runnable):
        def run(self):
            print("run() called")

When we execute the script,
it defines a type ``R`` to Python with a descriptor for ``run()``
and to Java a class that extends ``java.lang.Runnable``.
Next we create an instance ``r`` in Python of that class
that is also an instance of ``R`` in Java.
Both ``R`` and ``r`` are stored in a name space not shown,
the globals of ``engine``'s interpreter.

..  uml::
    :caption: Implementing ``Runnable`` under JSR-223

    participant main
    participant th

    main -> manager ** : new ScriptEngineManager()
    main -> manager ++ : getEngineByName("python")
        manager -> engine ** : new
        return engine

    main -> engine ++ : eval(new FileReader("r.py"))
        engine -> R ** : new
        return

    main -> engine ++ : eval("r = R()")
        engine -> R ++ : ~__call__()
            R -> r ** : new
            return
        return

    main -> engine : obj = get("r")
    main -> engine : r = getInterface(obj, Runnable.class)

    main -> th ** : new Thread(r)
    main ->> th ++ : start()
        th -> r ++ : run()
            r -> R ++ : run(r)
    main -> th : join()
                return
            return
        return

Considerations
--------------

We only note those additional to :ref:`uc-using-python-jsr-223`.

* Use in a ``Thread`` demands that ``R`` extend ``Runnable`` and therefore
  dynamic Java class creation is part of Python class creation (with ASM).

  * Some platforms may not allow that.
  * Perhaps only when a Java class is named as a base.

* ``r`` is the same object in both Java and Python,
  but its Python type ``R`` is not the same object as
  ``r``'s class in Java (nominally also ``R``).
* The Java method ``r.run()`` must invoke Python ``R.run(r)``
  found as a descriptor by look-up on Python ``R``.
* ``main`` could simply have cast ``obj`` to ``Runnable``.
  The idiom ``r = engine.getInterface(obj, Runnable.class)``
  allows the ``engine`` to proxy the actual object,
  which is not necessary with the "plain objects" design.
* ``main`` could have created multiple threads passing ``r``,
  resulting in concurrent look-ups and invocations of ``run()``.


.. _uc-python-twice-directly:

Using Python Twice Directly
===========================

An application obtains two interpreters
using the mechanisms in :ref:`uc-using-python-directly`,
or by JSR-223.
It takes an object defined in one interpreter
and calls a method on it in the second.
For variety,
suppose the application shares the objects from the first interpreter
by sharing a dictionary as the namespace of both.

..  uml::
    :caption: Using Python Twice Directly

    myApp -> Py : globals = dict
    note right
        Does this dict need
        import context?
    end note

    myApp -> i1 ** : new PythonInterpreter(globals)
    myApp -> i2 ** : new PythonInterpreter(globals)

    myApp -> i1 ++ : exec("class C :\n    def foo(self, ...")
        i1 -> C **
        return
    myApp -> i1 ++ : exec("c = C()")
        i1 -> C ++ : call()
            C -> c ** : new
            return            
        return

    myApp -> i2 ++ : exec("c.foo()")
        i2 -> c ++ : foo()
            note right
                It is essential that i1,
                having defined foo, supply
                the import context.
            end note
            c -> C ++ : foo(c)
                C -> i1 ++ : import bar
                    note left
                        The Python stack at
                        this point is in
                        both interpreters.
                    end note
                    return
                return
            return
        return

Considerations
--------------

We only note those additional to :ref:`uc-using-python-directly`.

* A single thread is valid in two interpreters simultaneously.
* When ``foo`` is executing, called by ``i2``,
  the current interpreter must be ``i1``.
* A dictionary object is created before any interpreter.
  If a built-in is guaranteed not to need import context,
  does it have an interpreter at all?
  What if it needs a codec or ``sys.stderr``?
* If the (platform) thread has a thread state in each interpreter,
  there will be two (disconnected) stacks.
  One stack (i.e. ``ThreadState``) per thread seems logical,
  and so threads do not belong to interpreters.


.. _uc-python-behind-library:

Python behind the Library
=========================
.. Possibly lurking in the bike shed?

A Java application uses a Java library.
The implementor of that library chose to use Python.
This is not visible in the API:
objects are handled through their Java API
but get their behaviour in a Python interpreter ``i1``.

A second interpreter ``i2`` is also in use by the application directly.
The application asks it to manipulate objects from the library,
whose implementation uses the first interpreter.
The Python implementation of the objects from the library
will not be apparent to the second Python interpreter.


..  uml::
    :caption: Python behind the Library

    participant main
    participant i2
    participant f

    lib -> i1 ** : new PythonInterpreter()

    lib -> i1 ++ : execFile("lib.py")
        i1 -> Thing **
        return

    main -> lib ++ : makeThing(7)
        lib -> Thing ++ : ~__call__(7)
            Thing -> thing ** : new Thing(7)
            return thing
        return thing

    main -> i2 ** : new PythonInterpreter()
    main -> i2 ++ : eval("def f(x) : return x.foo(6)")
        i2 -> f ** : new
        return
    main -> i2 : f = get("f")

    main -> f ++ : ~__call__(thing)
        f -> thing ++ : foo(6)
            thing -> Thing ++ : foo(thing, 6)
                Thing -> thing2 ** : new Thing(42)
                return thing2
            return thing2
        return thing2

In the last section of this sequence,
``main`` calls ``f`` passing the ``thing`` that the library created.
``f`` is written in Python
so in order to call ``foo`` it is going to look up the name in
the Python type corresponding to the class of ``thing``.
This is because we have assumed a single Python run time system,
where the single type registry exists,
because there is one static ``ClassValue`` for Python type.

We could allow the creation of multiple run-time systems,
but the Python types of exchanged objects
and Java types (like ``String``) that both run-times handle.
give rise to duplicate types.


Considerations
--------------

* A single thread is valid in two interpreters simultaneously.
* The single run-time system is necessary to share Python objects.
* The library does not hide the Python nature of its objects.
* On resolving the Java class of ``thing`` to ``Thing`` in ``i2``,
  we would be led to the Python type ``Thing`` created by the library
  under ``i1``.
* A library could hide its Python objects by only handing out
  pure Java classes that use them,
  but two interpreters using one of those would share the type object.


Application Server
==================
The user application runs in a Java application server
(like Apache Tomcat)
in which user applications are not processes but segregated by class loader,
and threads are re-used.

..  uml::
    :caption: Application Server

    myApp -> ": Py" : get Interpreter


..  note:: Not yet elaborated.


Considerations
--------------

* Thread local data and class values created in one application
  may still present for other applications.
* Class values attached to persistent classes are not disposed of.
* Approaches designed to ensure objects are not retained
  (e.g. use of weak references)
  may result in discarding state when it is still wanted.


.. _Java Scripting Programmer's Guide:
   https://docs.oracle.com/en/java/javase/11/scripting/java-scripting-programmers-guide.pdf

.. _Java Package javax.script:
    https://docs.oracle.com/en/java/javase/11/docs/api/java.scripting/javax/script/package-summary.html


  
Proposed Model for Jython [untested]
************************************

In the Jython model,
we propose a different arrangement of the critical data structures
from CPython.
In particular,
we abandon the idea that a thread or an object
belongs to a particular interpreter.
Although possibly controversial,
we expect to be able to address more challenging use cases
than with the CPython model.


Critical Structures Revisited
=============================

We have implemented this model in the ``rt3`` iteration ``evo1``.
At the time of this writing,
we have not tested it with multiple threads and interpreters.

..  uml::

    class Interpreter {
        sysdict
        builtins
        importlib
    }

    class PyModule {
        dict : Mapping
    }

    'Runtime --> "1.." Interpreter

    Interpreter "1" -right- "*" PyModule

    Thread -> "0..1" ThreadState : current

    ThreadState *--> "0..1" PyFrame : frame

    PyFrame -right-> PyFrame : back
    PyFrame --> PyFunction : func

    class PyFrame {
        locals : Mapping
    }

    class PyFunction {
        globals : PyDict
        defaults : PyTuple
        kwdefaults : Mapping
    }
    PyFunction -left-> PyCode : code
    PyFunction -right-> Interpreter : interpreter


The notable differences from the CPython model are:

#. ``ThreadState`` is not associated with a unique "owning" ``Interpreter``.
   A ``ThreadState`` is associated with multiple ``Interpreter``\s
   but only through the frames in its stack (if the stack is not empty).
#. Each ``PyFrame`` references a ``PyFunction``.
   (In fact, CPython *does* do this privately from version 3.11.)
#. Each ``PyFunction`` references an ``Interpreter``.
#. Each ``PyModule`` references an ``Interpreter``.

We now identify some assumptions (hypotheses) that led to this model
or are implications of it.
These are ideas we might test as a way of testing the overall model.


Threads are freely concurrent
=============================

The hypothesis is effectively that we don't need a GIL
— a lock that must always be held in order to execute Python code.
Instead, locks are taken in order to access individual objects safely.

In this model,
platform threads that handle Python objects
(and are associated with a ``ThreadState`` as necessary)
are freely concurrent with minimal constraints.
They are scheduled by the platform,
meaning the JVM or the operating system itself.
Threads may be physically concurrent if the hardware allows it.
Even where the hardware does not allow that,
the stream of instructions will be interleaved by the JVM or OS
which acts without regard to the state of Python.

This does not mean that threads will run entirely unconstrained.
We will need (and the platform offers) locks of various kinds
to constrain threads where we have to.

.. _thread_safe_objects:

Python objects must be thread-safe individually
-----------------------------------------------
Python objects that may be visited concurrently by threads
must be thread-safe.
(They must continue to meet their specification
in the face of potentially concurrent access.)

It is difficult to say for certain that any Python object
could never be used concurrently,
still less any built-in type
as the type of an object is constantly consulted when running Python.
Even a local variable in a function is in principle
reachable concurrently via a closure or by examination of a frame.
We conclude that every Python object must be made thread-safe.

Objects that are immutable are inherently thread-safe.
Others must be made thread-safe
by means of appropriate synchronisation.

Application code in Python has to pay attention to concurrency
in its own right
to protect the integrity of its composite structures.
This is additional to the safety that our implementation supplies.
A class will not be thread-safe simply because
its members are thread-safe individually.

.. _thread_safe_runtime:

Objects in the run-time must be synchronised
--------------------------------------------
Objects in the run-time system that may be visited concurrently by threads
must also be thread-safe.
We write this separately from the similar statement about Python objects
as the strategies can be quite different.

Construction of an object is naturally thread-safe,
since the object is not accessible until constructed.
The same can be true of Java class initialisation if it is not too complex.
(Reentrancy in a *single* thread is a hazard here too.)
We make maximum use of immutability,
so that objects once constructed are inherently safe.

Much of the time, data will be in Java local variables.
Unlike in Python,
these can be in the scope of only a single thread.
For everything else, there are locks.

We aim to protect shared data by means of appropriate synchronisation.
Particular cases may be recognised:

* The type system.
* The import system.

These are both places where Jython 2 makes careful use of locking.


Comparison with CPython's GIL
-----------------------------
There is a cost to synchronisation.
The locking strategy of CPython is a conscious choice
that allows the implementation to be written
with little attention to concurrency,
except at specific points "between byte codes".
There is only one lock (per interpreter)
and the CPython core developers judge this to be more efficient
than many fine-grain locks.

CPython's approach to object lifecycle management
is a factor in that calculation.
It would be necessary to take and release locks constantly
in order to protect reference counting object by object,
or at least to use atomic variables for that,
which also has a cost.

Java manages object lifecycle for us and so
we do not need the same locks or atomic variables for that.


The hypotheses entailed by our approach
---------------------------------------
It is a hypothesis that
what we gain by not having to manage lifecycle explicitly,
exceeds the costs we incur in the fine-grain locks we *do* need.
Java locking is understood to be cheap when not contended,
e.g. when there is only one thread.

We also effectively advance the hypothesis that,
where multiple threads *are* involved,
we gain more from increased concurrency by letting them run,
than we lose in managing multiple fine-grain locks.


.. _affinity_to_interpreter:

Some objects have affinity with a single ``Interpreter``
========================================================

CPython gives the interpreter responsibility for a pool of memory,
from which the objects are allocated that are handled in that interpreter.
It allows one thread to run per interpreter,
so that memory management is single-threaded in each pool.

This creates a strong affinity between every object
and the interpreter that allocated it,
and is the only interpreter that should handle it.
The Python-level API for interpreters (:pep:`554`)
provides no means to share most objects.
The only exceptions are those objects that will never be de-allocated,
and the channels that communicate between interpreters
(for which special considerations apply).


No "lifecycle" affinity
-----------------------
We do not need the memory pool in our ``Interpreter``,
where the JVM allocates and recycles all objects
(in its own dedicated thread).
Therefore we do not need this kind of affinity to an interpreter.

The hypothesis is that we can correctly
handle an object in multiple ``Interpreter``\s.

.. _affinity_to_import_context:

Affinity to import context
--------------------------
The other main responsibility of the interpreter is for "import context":
the imported module list, import library, module path,
certain short-cuts to built-ins (all to do with modules),
and the codec registry (a similar idea).

When does import context matter to an object?
In our use cases (for example :ref:`uc-python-twice-directly`),
we found that we needed sometimes to be able to navigate from
an executing method in a Python object
to the import context for which it was written.
This seems to be the only time running Java Python
needs to know the interpreter.
This is quite different from CPython
where the memory pool of the interpreter is needed for:

* the creation of any object, or
* any action that drops a reference to an object.

A method definition in Python is a statement
executed most often in a module or class body,
but possibly in a nested scope.
Execution of a module body occurs when it is imported into an interpreter,
and it occurs once within each interpreter.

The programmer who manipulates ``sys.modules`` and ``sys.path``,
or customises the import mechanism,
in the current interpreter before or after a definition,
surely intends to affect those definitions as they are executed
and for this interpreter to be current again while the method is executed.
This affinity is not a property of the thread that visits the method.
We do not find a reason for a thread (``ThreadState``)
to belong to an interpreter.

We hypothesise that the following object types
*do* have an import affinity to an interpreter,
immutably established when they are created:

* A ``frame``
  from the method or function whose execution creates it,
  or the interpreter that created it to execute the code
  (a module body or an immediate string).
* A method or function (or any callable?),
  from the interpreter that executed its definition.
* Possibly a ``module``
  from the interpreter that imported it.

Does a module need interpreter context after the body has executed,
and outside a call to any function in it?

When a module is implemented in Python it seems enough that the
functions defined by executing the body,
and the methods of classes in it.
should hold a reference to the interpreter,
and for this to be added to the frame.

When a module is implemented in Java,
the simplest way for every method to have such a reference is to
add it as a field to the module instance.

Some methods may need the interpreter
e.g. ``builtins.exec`` needs it to create a frame
to execute its argument.


.. _frame_refs_interpreter:

A ``frame`` references a particular interpreter [untested]
==========================================================

Any code that imports a module,
must import it to a particular interpreter,
so that it uses the correct import mechanism, paths
and list of already imported modules,
to which the module will be added.

Whenever we execute code compiled from Python,
we do so in a ``PyFrame`` (Python ``frame`` object).
We therefore hypothesise that a ``PyFrame``
should keep a reference to the appropriate ``Interpreter``,
so that we can always find it when it is needed as import context.

A ``PyFrame`` is ephemeral, so where does the information come from?
A frame may be the result of:

#. REPL, JSR-223 or explicit interpreter use.
#. Module import, when executing the module body.
#. The ``exec`` or ``eval`` function.
#. Class definition, when executing the module body.
#. Function or method execution.
#. Lambda expression evaluation.
#. Generator expression evaluation.

The first four are actions in an interpreter that logically should provide
the import context for the ``frame`` that is formed.
The others seem to require that callable objects
should designate the interpreter that creates the instance
in the ``frame`` that results from a call.
We shall find that it is enough for the Python ``function`` object
to behave that way,
but that ``function`` objects arise in more circumstances
than might at first be expected.
This is further argued in :ref:`function_refs_interpreter`.

A ``frame`` could find the correct interpreter indirectly.
We have chosen to follow a pattern suggested by
examination of CPython source in version 3.11.

Frames in that version have gained
a private reference to a ``PyFunctionObject``.
It appears that this is never ``NULL``,
being synthetic in the cases where no actual function is called
e.g when the frame is created to ``exec`` a code object or
load a module body.


.. _function_refs_interpreter:

A ``function`` designates its defining interpreter [untested]
=============================================================

Our hypothesis is that
the interpreter current at the point of definition of a callable object
is the one that should provide context for running its code.
It must therefore be remembered as a property.

Where a function or method is defined in Python,
we have argued this is necessary in order to preserve
the import context prepared by an application programmer,
in case we encounter an import operation.
A reference to a module is not reason enough,
since it will be resolved in the local or global variables.

The category of "callable" is wide.
We now consider the possible kinds of callable in turn.

``function`` defined in Python
------------------------------
The most straightforward way to produce a ``function``
is the execution of a ``def`` statement.
In CPython 3.8 a definition produces this code:

..  code-block::

    >>> dis(compile("def fun(): pass", '<function-def>', 'exec'))
      1           0 LOAD_CONST               0 (<code object fun at ...
                  2 LOAD_CONST               1 ('fun')
                  4 MAKE_FUNCTION            0
                  6 STORE_NAME               0 (fun)
                  8 LOAD_CONST               2 (None)
                 10 RETURN_VALUE

This creates an object into which is bound,
amongst other things,
a reference to the globals of the defining module,
and if it is a nested definition,
a closure referencing non-local variables.

In Java, the object is a ``PyFunction``,
which when called and will produce a ``PyFrame``
against which the compiled body (``PyCode`` object) will execute.
This frame needs a reference to the defining interpreter
to give it the import context the programmer intended.
The ``PyFunction`` must therefore keep the reference.

``lambda`` expression
---------------------
A ``lambda`` expression results in the definition of a function object.

..  code-block::

    >>> dis(compile("fun = lambda i: i*i", '<lambda-def>', 'exec'))
      1           0 LOAD_CONST               0 (<code object <lambda> at ...
                  2 LOAD_CONST               1 ('<lambda>')
                  4 MAKE_FUNCTION            0
                  6 STORE_NAME               0 (fun)
                  8 LOAD_CONST               2 (None)
                 10 RETURN_VALUE

There is no difference from regular function definition.

Generator expression
--------------------
A generator expression results in the definition of a function object,
which is then called once to initialise it.

..  code-block::

    >>> dis(compile("gen = (i*i for i in range(10))", '', 'exec'))
      1           0 LOAD_CONST               0 (<code object <genexpr> at ...
                  2 LOAD_CONST               1 ('<genexpr>')
                  4 MAKE_FUNCTION            0
                  6 LOAD_NAME                0 (range)
                  8 LOAD_CONST               2 (10)
                 10 CALL_FUNCTION            1
                 12 GET_ITER
                 14 CALL_FUNCTION            1
                 16 STORE_NAME               1 (gen)
                 18 LOAD_CONST               3 (None)
                 20 RETURN_VALUE

The code object has generator nature,
but at run-time when we encounter ``MAKE_FUNCTION``,
we shall hardly be able to tell it from a regular function definition.

Method of a class
-----------------
Suppose we define a class in Python with one method.
The ``class`` definition is an executable statement,
where the class body is also executed immediately.
The method definition is then a function definition within that body:

..  code-block::

    >>> classdef = \
    """
    class C:
        def fun(self, a):
            return a+1
    """
    >>> dis(compile(classdef, '<class-def>', 'exec'))
      2           0 LOAD_BUILD_CLASS
                  2 LOAD_CONST               0 (<code object C at ...
                  4 LOAD_CONST               1 ('C')
                  6 MAKE_FUNCTION            0
                  8 LOAD_CONST               1 ('C')
                 10 CALL_FUNCTION            2
                 12 STORE_NAME               0 (C)
                 14 LOAD_CONST               2 (None)
                 16 RETURN_VALUE

    Disassembly of <code object C at ...
      2           0 LOAD_NAME                0 (__name__)
                  2 STORE_NAME               1 (__module__)
                  4 LOAD_CONST               0 ('C')
                  6 STORE_NAME               2 (__qualname__)

      3           8 LOAD_CONST               1 (<code object fun at ...
                 10 LOAD_CONST               2 ('C.fun')
                 12 MAKE_FUNCTION            0
                 14 STORE_NAME               3 (fun)
                 16 LOAD_CONST               3 (None)
                 18 RETURN_VALUE

Here CPython 3.8 has created an ephemeral function ``C()`` and
passed it to a special ``__build_class__`` built-in function.
(A reference to that is what ``LOAD_BUILD_CLASS`` leaves on the stack.)
The code object for the ephemeral function is
the body of the class definition.
``__build_class__`` executes that body function
against a dictionary that captures the definitions made.

Both functions should follow the rule that
the interpreter that executes the definition should be
the interpreter that executes the body.
Then wherever this type is used
(in whatever interpreter)
the defining interpreter provides context during ``C.fun``.

This is also true of implementations that could possibly be given for
``__init__`` or ``__new__``,
even though a statement that creates an instance of ``C``
is executed in the context of a different interpreter.

If a sub-class of ``C`` were to be defined with further methods,
in a second interpreter,
the added methods would have affinity with the second interpreter,
while the ones inherited unchanged would keep their original affinity.


Instance with ``__call__``
--------------------------
Suppose we define a class with ``__call__``,
that is, we define a *custom callable*.

..  code-block:: python

    >>> class C:
        def __init__(self, n):
            self.n = n
        def __call__(self, x):
            return x * self.n

    >>> triple = C(3)
    >>> triple('A')
    'AAA'

It should be apparent by now that the defining interpreter of the class
is the current interpreter during ``__new__``, ``__init__`` and ``__call__``,
and for the good reason that their text was authored in that context.
This is why we make the statement we do *only* about ``function``.

It does not matter in what interpreter the instance of ``C`` is created.
If in a second interpreter we create a new ``C`` and call it,
the call runs entirely in the first interpreter
where ``C.__call__`` was defined.

..  code-block:: python

    >>> double = C(2)
    >>> double(10)
    20

In fact,
the context of the second interpreter can easily have its effect
if ``C.__call__`` can be made to execute a function
defined by the second interpreter.
It could be that ``C`` allows customisation of the meaning of ``__call__``
through a parameter to ``__init__``,
or it could be that the call itself introduces the local context like this:

..  code-block:: python

    >>> class X(int):
        def __mul__(self, n):
            return (self+1)*n

    >>> double = C(2)
    >>> double(X(10))
    22

We conclude that it is the ``def`` and ``class`` keywords,
and equivalent constructs recognised by the compiler
(all resulting in a ``MAKE_FUNCTION`` opcode in the cases exhibited),
that impress the current interpreter onto a callable,
which is always a ``function``.

A function defined in Java
--------------------------
A function defined in Python has affinity with
the interpreter that defined it:
what is the affinity of a function or method defined in Java?

Most methods (in Python or Java)
will not in practice be sensitive to the import context.
Calling a Java method (in a built-in type or extension module)
does not itself create a Python ``frame``.
However, the method *could* invoke the import mechanism,
create a ``PyFrame`` directly,
or call a function implemented in Python,
and then we would have to know which interpreter was involved.

The API for creating a ``PyFrame`` or for importing a module
could reasonably demand the interpreter be explicit
in the call signatures.

If the interpreter is not explicit,
it could be found from the top frame of the Python stack,
which in turn is found from the current thread.
When the stack is empty,
perhaps what is being attempted is meaningless anyway,
and an error should be signalled
at the point the interpreter context is needed.


A ``PyModule`` designates its defining interpreter [dubious]
============================================================

The body of a module defined in Python
is sensitive to the import context like any other code.
This context is defined by the interpreter into which it is being imported,
and stamps itself on the functions and classes the module defines.

It is not clear that the module needs to retain this affinity itself,
except through the callables it leaves behind.


A ``Thread`` always has a ``ThreadState`` [dubious]
====================================================

From within any platform thread (``java.lang.Thread``),
according to our model,
we may navigate to the corresponding ``ThreadState``.
We expect to implement this as a thread-local variable in the runtime.
(We'd say "global" here,
were it not for the possibility of creating instances of the "global" runtime,
under different class loaders.)

If the stack of a ``ThreadState`` is not empty,
the ``Interpreter`` designated by the top frame
(through its function)
may be thought of as the current interpreter.
If the stack is empty, arguably no interpreter is current.

Contrary to the hypothesis,
there is no guarantee at all that an arbitrary platform thread
has been assigned a ``ThreadState`` by the Python run-time system.
However,
at the point we need it,
the run-time system will find or make a ``ThreadState``.
This is a standard pattern with a ``ThreadLocal``.

Consider the case where a Java thread pool
is responsible for call-backs into objects defined in Python.
Threads are chosen arbitrarily by the pool:
the history of that thread's interaction with Python
should not affect subsequent behaviour.
Any ``ThreadLocal`` value retained from last use can only be misleading.

This suggests that the incoming pool thread
should begin with default state in all respects,
including an empty stack.
If the ``ThreadState`` is not actually destroyed on thread exit,
it should at least be zeroed.

The called object defines the interpreter, of course,
not the ``ThreadState``.


Threads are not confined to a single ``Interpreter``
====================================================

CPython confines each thread to one interpreter
as part of its scheme for ensuring that no two threads
execute a critical section of C concurrently on the same data.
Threads take turns to hold the GIL for their interpreter
and only access the objects it controls exclusively.
Global data are scrupulously avoided or guarded specifically.

Whatever the implementation language of the interpreter,
threads may enter critical sections defined in Python concurrently.
The objects they potentially contend for must be protected by
locks requested by the programmer
and found in the ``threading`` module.
In CPython the objects will belong to the same interpreter as the threads,
but this does not protect them from disorderly (pseudo-concurrent) access
from Python in the general case.

In our model,
threads may cross easily between objects where
different interpreters set the execution context.
There is no exclusive ownership of objects by interpreters,
and no global mechanism is constantly present at "opcode" level (GIL).

As objects are not confined to an interpreter,
we find no reason for a thread (``ThreadState``)
to belong to a fixed single interpreter,
as noted in :ref:`affinity_to_import_context`.

Objects instead must be associated with locks that de-conflict access,
specific to the circumstances.
This is the case
whether the critical section of code is Python written by a user,
in Java in an extension module.
or in a single operation of the interpreter itself.
These locks will operate without reference to a current interpreter.


Certain built-in types must be thread-safe [open]
=================================================

We have discussed at length that we choose not to reproduce the CPython GIL,
and that threads by default may reach any code or data concurrently.
The absence of a global lock operative during every opcode means that
built-in and extension objects in the Java implementation
may have to protect themselves from concurrent access
where their CPython counterparts make no explicit provision.

The different model presents the risk that users may write Python objects
that do not use the resources of the ``threading`` module
but rely on the atomicity of operations on built-in types
that is a consequence of the GIL in CPython and does not exist for us.

It is not clear what the policy should be in a Java implementation of Python,
when threads are mis-handled by the programmer:

* As much like CPython as possible.
* Incorrect result possible, but with a Python exception (if detected).
* May produce Java stack dump, but interpreter remains stable.
* No guarantee even that the interpreter remains viable.

Nor can we always blame the programmer.
In :ref:`uc-implementing-interface-jsr-223`
we detected a need for method lookup (in a Python ``dict`` probably)
to be thread-safe in the face of multiple platform threads.

This chapter has been about the interpreter structure,
which is heavily influenced by our needs when supporting concurrency.
However, the subject is large enough to deserve separate treatment.

