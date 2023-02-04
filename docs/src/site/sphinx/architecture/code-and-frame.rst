..  architecture/code-and-frame.rst


Types of ``code`` and ``frame`` Object
######################################

Several Types of Code
*********************

An object that is merely of type ``code`` to Python,
should be capable of representing at least:

* Java byte code (compiled from Python).
  As in Jython 2,
  we expect to produce Java byte code for performance.

* CPython byte code (of more than one vintage?).
  We have found it necessary in Jython 2 to support CPython byte code
  because large modules and functions exceed the maximum size
  for a Java class definition.

An extensible approach is to allow sub-classes of ``PyCode``,
one for each compiled form.

These two cases already make it desirable
to have more than one kind of ``PyFrame``.
The idea briefly, explored more fully below,
is to regard the code object as a factory
for the particular type of frame that it needs.

So far the project has targeted CPython 3.8 byte code,
in the class ``CPython38Frame``,
a specialisation of ``PyFrame``.
Each supported version of the byte code should have its own frame sub-class.

.. "explored more fully below" when I have the time


Old Notes (needs rewrite)
*************************

.. Just rescuing these fragments from p[revious section.

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
and any state associated with a particular execution of the code,
if it is not in the local variables of the interpreter itself.
In CPython it provides space for the arithmetic stack.

A ``PyFrame`` also exists
apart from representing the current state of a thread:

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
the extent to which program state information should be in the JVM frame,
and the variables it holds,
rather than in the ``frame``.
Information in the ``frame``
is accessible to Python in tracebacks and debugging.



