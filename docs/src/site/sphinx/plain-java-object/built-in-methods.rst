..  plain-java-object/built-in-methods.rst

.. _Built-in-methods:

Methods Built-in using Java
###########################

We have outlined how the special methods defined by the Python data model
may be implemented for types defined in Java.
There is a finite set of these methods, enumerated in ``Slot``,
and a handful of signatures to which they conform,
enumerated in ``Slot.Signature``.

We now move on to named methods of types or modules defined in Java.
These constitute an essentially unbounded set,
given the possibility of defining extension types,
and we need a general mechanism to support this.

    Code fragments in this section are found in
    ``rt3/src/main/java/.../vsj3/evo1``
    in the project source.

Methods of well-known built-in Python types mostly have complex signatures,
with optional arguments and behaviours we are not ready to explore.
Rather than attempt well-known methods,
we therefore draw examples from ``TypeExposerMethodTest``,
which defines artificial test objects
with a graduated scale of difficulty in the signatures.
This was the test fixture against which development proceeded.


Methods on Built-in Types
*************************

The Visible Classes
===================

In fact we need two main object types to implement methods or functions.

The first is
``PyMethodDescr`` (the Python type ``method_descriptor``),
which appears in the dictionary of a ``PyType`` under the method name.
It is callable and may be applied to an instance as first argument,
although we do not usually use it that way:

>>> type(str.replace)
<class 'method_descriptor'>
>>> str.replace("cacophany", 'c', 'd')
'dadophany'

The second is
``PyJavaMethod`` (the Python type ``builtin_function_or_method``),
which represents the method bound to an instance during attribute access,
effected either by the dot operator,
or by a call to ``__get__`` on the descriptor:

>>> "cacophany".replace('c', 'd')
'dadophany'
>>> type("cacophany".replace)
<class 'builtin_function_or_method'>
>>> "cacophany".replace
<built-in method replace of str object at 0x7fddb41bbd30>
>>> str.replace.__get__("cacophany")
<built-in method replace of str object at 0x7fddb41bbd30>
>>> type(str.replace.__get__("cacophany"))
<class 'builtin_function_or_method'>

Although the call to ``__get__`` on the descriptor
is not common in user code,
it is what makes attribute access work internally.

Note that in a method call,
the "self" of ``__call__`` is the callable object,
while the target to which the Python method call is directed,
e.g. the particular ``str`` instance
in an application of ``str.replace()``,
is the first argument in the array of objects passed.

..  uml::
    :caption: Classes supporting method definition

    abstract class Descriptor {
        name : String
        objclass : PyType
    }

    abstract class MethodDescriptor {
    }
    Descriptor <|-- MethodDescriptor

    class ArgParser {
        name : String
        argNames : String[]
    }

    abstract class PyJavaMethod {
        module : String
        handle : MethodHandle
        ~__call__() : Object
    }
    PyJavaMethod -right-> ArgParser : argParser
    PyJavaMethod -left-> Object : self

    abstract class PyMethodDescr {
        signature : MethodSignature
        method : MethodHandle
        ~__get__() : PyJavaMethod
        ~__call__() : Object
    }
    MethodDescriptor <|-- PyMethodDescr
    PyMethodDescr -right-> ArgParser : argParser
    PyMethodDescr ..> PyJavaMethod : <<creates>>

Behind both of these visible classes is the ``ArgParser``,
as the diagram shows,
which is created by the exposer by reflection
on the defining class of a type or module.
An ``ArgParser`` holds the information necessary to parse call arguments:
the "shape" of the parameter list
(number and names, how many are positional only or keyword),
and the default values where given.
It is capable of expressing the full range of parameter lists
encountered when defining a method or function in Python.


Design Features
===============

The objectives of the design are:

#.  Methods may be defined in Java,
    with parameters of a type natural to their purpose.

#.  Each method is represented by an object callable from Python, that is,
    one that defines an instance method ``__call__(Object[], String[])``,
    that calls the definition (in Java).

#.  Variants exist for functions and methods in modules and classes:
    instance, static or class methods.

#.  There is the prospect of efficient ``invokedynamic`` call sites.

#.  Argument processing code is shared with invocation of
    functions and methods defined in Python.

We'll discuss these objectives in turn.

Natural Parameter Types
-----------------------

A method accepting arguments from Python
could declare every parameter to be ``Object``,
and cast or convert arguments to types natural in their implementation
as part of the program text.
Or it could have a signature like that of ``__call__(Object[], String[])``
itself, in order to support variable argument numbers and keywords.
Every method would begin with code to pick apart these actual arguments
into local variables.
This would make them tedious to write.

CPython solves this problem by generating a wrapper on the "natural" definition,
using a tool in Python called Argument Clinic, defined in :pep:`436`.
It means there are often two C functions:
one with the natural name and stylised ``PyObject`` parameters,
and one the author wrote with natural parameters
but where the name has been modified name (adding `_impl`).

Rather than generate code,
we use annotations to define argument processing
that can transform the arguments to ``__call__(Object[], String[])``
into those in the reflected signature of the target Java method.
An example is provided by:

..  code-block:: java

    class SimpleObject {
        static PyType TYPE = PyType.fromSpec(
                new Spec("Simple", MethodHandles.lookup()));
        // ...

        @PythonMethod
        PyTuple m3p2(int a, @PositionalOnly String b, Object c) { ... }

The ``PythonMethod`` annotation attracts the attention of the exposer,
which creates an ``ArgParser`` to describe the signature.
In help and similar contexts,
this method would be reported as ``m3p2($self, a, b, /, c)``.

Callable Object
---------------

This implementation
(after considering approaches closer to CPython's)
adopts the signature ``__call__(Object[], String[])``
that Jython 2 also chooses.
All the argument values from the call site
are marshalled into the first ``Object[]`` array.
The ``String[]`` array contains the keywords used at the call site,
in order,
and the values that went with them are in the same order
at the end of the ``Object[]`` array.

Both ``PyMethodDescr`` and ``PyJavaMethod`` are callable,
with ``PyMethodDescr`` taking the target object as
the first in the argument array,
while ``PyJavaMethod`` already has the target object
as a member ``self``.

The ``__call__`` methods of ``PyMethodDescr`` and ``PyJavaMethod`` both,
in the general case,
distribute the array of arguments across
the individually declared parameters of the implementation,
using the services and data of the attached ``ArgParser``.
The difference between the two is that
a ``PyJavaMethod`` already binds the object that will be the first
(the ``self``) argument and supplies it to the first parameter.
If there are keyword or collector arguments,
this argument processing may involve some re-ordering in a second array.
In a simple case, specialisations can be written that hard-code the data moves.

Variants for Static and Class Methods
-------------------------------------

At the time of writing this,
we can only say that the exposer and framework of classes
allow for these needs.
We lack both tests and a fully developed implementation.


Prospect of ``invokedynamic``
-----------------------------

The operation ``__call__`` may be bound into a ``CALL`` call site
in the way we have indicated for unary and binary operations.
The types ``PyMethodDescr`` and ``PyJavaMethod``,
and their several implementing Java classes,
would be possible guard classes.
The target handle would designate ``__call__`` on the implementation class.

Note that the number of arguments in the ``Object[]`` array,
whether there are varargs tuples or dictionaries,
and the exact keywords (if any) are fixed by the program text.
One may imagine call sites specialised to certain numbers of arguments,
or to the absence of keywords,
a ``CALL0``, ``CALL1``, ``CALLN``, ``CALLVA``, and so on.

Beyond efficient implementation of the call mechanism in general,
loom further possibilities.
Each callable type represents its target as a ``MethodHandle``
on the implementation Java method.
Processing has been applied (if necessary) to cast or convert arguments,
and to cast the return,
so it is ready to apply to ``Object`` arguments.

Imagine a method call as it commonly appears, in the form ``a.f(x,y)``.
In many cases, it is equivalent to ``getattr(type(a),"f").__call__(a,x,y)``.
If ``a`` has a consistent Python type, in which ``f`` is not redefined,
then ``getattr(type(a),"f")`` is the same object every time the site is reached.
If it has only one Java implementation, then
its ``op_call`` could be bound into the site directly.

In many ``PyMethodDescr`` and ``PyJavaMethod`` specialisations,
``__call__`` simply checks for an acceptable number of arguments,
then dispatches to (invokes) an embedded handle.
Since the number of arguments is statically known for the site,
the result of the checks is known in advance,
and assuming they pass once,
the site could embed the handle from the ``PyMethodDescr`` as target.

The site is now one that,
after argument conversions,
goes directly to the Java implementation.
Note that this relied on quite a few conditions holding.
In common cases they do,
but the guard applied at run-time has to take all of them into account.


Common Code with Python Methods
-------------------------------

In the most general case,
processing supplied arguments to the declared parameter positions,
which is the job of ``ArgParser.parse``,
involves an intermediate array into which arguments are mapped.
This is the same process that we go through to populate
the first part of a stack frame,
when calling a function defined in Python.

We therefore use the same code to process to call a complex built-in,
as we shall for calls to a Python method.
To be precise,
the parsing developed in VSJ2 to fill the interpreter frame,
has been repurposed in VSJ3,
where an interest in the uses of the ``MethodHandle``
has led us to a study of methods defined in Java,
before we try to re-introduce the ``PyFunction``.

Specialisations of ``PyMethodDescr`` and ``PyJavaMethod``,
possible when the signature is simple enough,
do not need to use ``ArgParser.parse`` on the main path
when processing a call.
However, if their validations fail
(e.g. of the number of arguments)
it is still ``ArgParse`` that generates the error message users see.


.. _Built-in-methods-pos:

Positional Parameters
=====================

.. _Built-in-methods-defaults:


..  code-block:: java

    class SimpleObject {
        static PyType TYPE = PyType.fromSpec(
                new Spec("Simple", MethodHandles.lookup()));
        // ...
        @PythonMethod
        static PyTuple m3(SimpleObject self, int a, String b,
                Object c) { ... }

The ``PythonMethod`` annotation attracts the attention of the exposer,
which creates an ``ArgParser`` to describe the signature,
in this case an instance method with positional-only arguments.
In help and similar contexts, it would be reported as:

..  code-block:: text

    m3($self, a, b, c, /)

This is the output of ``ArgParser.toString()``.
Names are retained in the ``ArgParser``
to support error messages and documentation.

The method descriptor holds a ``MethodHandle``
in which the original obtained by reflection:

..  code-block:: text

    MethodHandle(SimpleObject,int,String,Object)PyTuple

has been transformed into:

..  code-block:: text

    MethodHandle(Object[])Object

so that it may be called with the ``Object[]`` argument of ``__call__``.


Default Values
==============

.. _Built-in-methods-kw:

Keyword Parameters
==================

.. _Built-in-methods-kwdefaults:

Default Values with Keywords
============================

.. _Built-in-methods-varargs:

Collector Parameters
====================
