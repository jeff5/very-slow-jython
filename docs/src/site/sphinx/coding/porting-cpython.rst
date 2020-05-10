..  porting-cpython/porting-cpython.rst


Porting CPython Code to Java
############################

The present implementation,
like Jython 2,
benefits hugely from the code in the reference implementation in C.
Obviously, it cannot be taken verbatim,
but pasting the the C code into a Java source file is often a good start.

There is no mechanical way to convert CPython source to valid Java.

This section is written with contributors to a future Jython 3 in mind,
assuming that to be based on The Very Slow Jython Project.
Even if it contains not a line of the same code,
the process by which it was produced is formative for
the process to produce Jython 3.


Architectural Drivers
*********************

This is just a quick reminder of why Jython is different from CPython,
because Java is different from C.

* Java manages the lifetime of objects:
  we do not do reference any counting or explicit garbage collection
  (well, hardly any).
* There is no GIL: concurrency issues do not just appear between opcodes,
  but during all operations on any object that could be shared. 
* Java supports strong typing at compile time
  and enforces it at run time.
  It costs CPU cycles to check a cast that C will trust at compile time.

These are reasons for the largest differences
between the C and Java implementations,
those affecting the overall program logic.
Other detailed considerations affect
how a particular idea (e.g. field access) is expressed in Java rather than C.


Lessons from Practice
*********************

Quite a lot of code in The Very Slow Jython Project was produced by
pasting the the C code into a Java source file to begin with.

There is no mechanical way to convert CPython source to valid Java.
An editor that supports regular expressions
in its find-and-replace tool is a huge advantage,
and we'll list some favourites in the appropriate sections.

In some cases,
the structure of the CPython version is clearly visible in the Java.
Often the names are retained.
In other cases, once the logic is understood,
an approach more natural to Java is substituted.

The sections below are intended to accumulate observations
as the project develops.


It's much clearer in Java
=========================

The logic of any method in Java tends to be shorter and clearer than in C,
because of the amount of overhead we can remove from the main line
(life-cycle management and response to error conditions).



Comments are needed
===================

The CPython code base is quite thinly commented.
Complex logic is implemented with barely a hint to the reader how it works.

The readers of that code base have to be familiar with
conventions used in the C-API
(which is stable and documented quite well)
and also with a plethora of private API methods and macros
(which are not well documented and change from one version to the next).
Having to translate this to Java,
the reader is more acutely aware than the core developer who conceived it,
of the points at which it is obscure.
This is an opportunity to do better in Jython than in CPython.

We want to avoid a situation where the implementation of Jython
can only be understood by studying the source of CPython at the same vintage.
The Very Slow Jython Project code base was written with those in mind
who must understand and modify (repair) the logic of the implementation,
and are not CPython developers.

..  note:: The present author is subject to the opposite tendency,
    in the code he conceives, tending to explain in detail
    even that which might be obvious to a typical reader.
    This is part of the process of developing the code in the first place,
    and might reasonably be toned down in production.


Documentation comments are needed
=================================

The Javadoc of the code base is a substitute for the C-API documentation.
This is how users embedding Jython will understand the public API.
(There should be less public API than previously,
because we can use the Java module system to control exposure.)

The Javadoc of private or package, classes, methods and fields
will not appear in user documentation.
It will still appear as help available in IDEs that support it.
The Very Slow Jython Project code base was written with those in mind
who read and modify it in that type of IDE.


Names
=====

A correlation to names used in CPython is helpful for comparison.
Names may be shorter in Java than CPython,
because classes form a namespace that makes the prefixes
used in CPython unnecessary.
Similarly, overloading of names
(disambiguated by type signature)
allows us to dipense with complicated suffixes.
So ``PyEval_EvalCode``, ``PyEval_EvalFrame`` and ``PyEval_EvalFrameEx``
could all reasonably be ``eval``, taking type into account,
or ``PyFrame.eval)`` and ``PyCode.eval`` as methods.


Type and cast
=============

Casting is very frequent in the CPython code base.
Signatures in the C-API mostly involve just ``PyObject *`` arguments.
A cast costs nothing in C except the risk of being wrong.
In many cases the nearby ``PyTuple_CheckExact``,
which does cost a few CPU cycles,
is inside an ``assert`` statement that is active only in debug mode.

A cast in Java will always be checked and carry a cost.
Possibly the compiler can eliminate it, if the case is simple enough.
But the language favours those who avoid casting where they can.

The Very Slow Jython project embeds an experiment
in applying strong typing to the implementation,
where the C-API has none.
Quite often the information we need is in an assertion:

..  code-block:: c

    assert(kwnames == NULL || PyTuple_CheckExact(kwnames));

This tells us that ``kwargs`` could be declared explicitly as a ``PyTuple``.
There is an interaction here with the implementation of types and inheritance:
although not fully tested,
the working hypothesis is that all Python sub-types of ``tuple``,
are implemented by a Java sub-class of ``PyTuple``.

A second source of clues is in the fields of built-in types.
If the constructor or comments in C for a field constrain its type,
then it may be strongly typed in the Java implementation.
In a CPython ``PyFunctionObject``:

..  code-block:: c

    typedef struct {
        PyObject_HEAD
        PyObject *func_code;        /* A code object, the __code__ attribute */
        PyObject *func_globals;     /* A dictionary (other mappings won't do) */
        ...
    } PyFunctionObject;


And a typical accesses are:

..  code-block:: c

        PyCodeObject *co = (PyCodeObject *)PyFunction_GET_CODE(func);
        PyObject *globals = PyFunction_GET_GLOBALS(func);

But in Java we may declare:

..  code-block:: java

    class PyFunction implements PyObject {
        ...
        /** __code__, the code object */
        PyCode code;
        /** __globals__, a dict (other mappings won't do) */
        final PyDict globals;

and use them as:

..  code-block:: java

        PyCode co = func.code;
        PyDict globals = func.globals;
        
strengthening the type safety of our implementation,
when these are subsequently referenced in a ``PyFrame``,
and saving us a little CPU time to boot.
Once we start doing this,
the implications of each type deduction spread to other signatures
and variables.

A useful regex is: ``(\w+)_Check\((\w+)\)``
replaced with ``($2 instanceof $1)``.


Object Lifecycle
================

Because Java manages the life-cycle of objects,
occurrences of ``Py_INCREF``, ``Py_XINCREF``, ``Py_DECREF`` and ``Py_XDECREF``
can generally be deleted,
and a number of less obvious calls
such as ``PyMem_Free`` and ``_PyObject_GC_TRACK``.

``Py_CLEAR`` should perhaps be replaced with assignment of ``null``,
rather than being removed totally.

Useful regex: ``Py_X?(IN|DE)CREF\([^)]+\);`` replaced with nothing.



Pointer-to-Function is ``MethodHandle``
=======================================

What was in CPython a pointer to a function is for us ``MethodHandle``.
This is one place where compile-time type safety is not strong,
since every ``MethodHandle`` is the same non-parameterised type.

A typical conversion is from C:

..  code-block:: c

    result = (*(PyCFunctionWithKeywords)(void(*)(void))meth) (
                    self, argtuple, kwdict);

to Java:

..  code-block:: java

    return (PyObject) f.tpCall.invokeExact(args, kwargs);

The relative simplicity hides the significant (but one-time)
investment in constructing the method handle.

    
Error returns
=============

C-API functins return ``NULL`` (sometimes -1) to signal an error.
The information from which a Python exception can be made
is left in the thread state.

Instead of return status, we signal errors by throwing an exception.
There are some drawbacks to this:

* Constructing an exception,
  which normally includes a Java stack trace,
  can be expensive.
  
* It is easy in CPython,
  but less so in Java,
  to replace the message or exception type with another.

Generally however, this is a help because
this kind of thing
(here in the implementation of ``builtins.hash()``)
becomes unnecessary:

..  code-block:: c
    :emphasize-lines: 6-7

    static PyObject *
    builtin_hash(PyObject *module, PyObject *obj)
    {
        Py_hash_t x;
        x = PyObject_Hash(obj);
        if (x == -1)
            return NULL;
        return PyLong_FromSsize_t(x);
    }

We can just let ``PyObject_Hash`` (spelled ``Abstract.hash``) throw,
and need not declare or test the intermediary ``x``,
making the whole thing a one-liner.

Other things are more difficult (from ``eval.c``):

..  code-block:: c
    :emphasize-lines: 5-9

            for (i = oparg; i > 0; i--) {
                PyObject *none_val;
                none_val = _PyList_Extend((PyListObject *)sum, PEEK(i));
                if (none_val == NULL) {
                    if (opcode == BUILD_TUPLE_UNPACK_WITH_CALL &&
                        _PyErr_ExceptionMatches(tstate, PyExc_TypeError))
                    {
                        check_args_iterable(tstate, PEEK(1 + oparg), PEEK(i));
                    }
                    Py_DECREF(sum);
                    goto error;
                }
                Py_DECREF(none_val);
            }

In this, ``check_args_iterable`` is called on error,
only if we are processing a particular type of opcode,
and replaces the message with one specific to that circumstance.
The Java solution is to catch the ``TypeError``
and either call the "check" function (which throws) or re-throw the original,
but this is no more ugly than the original.


Translating Exceptions
======================

A typical idiom in CPython might be:

..  code-block:: c

        if (kwdict == null) {
            _PyErr_Format(tstate, PyExc_TypeError,
                          "%U() got an unexpected keyword argument '%S'",
                          co.name, keyword);
            goto fail;

and the code at ``fail`` will typically clean up (``XDECREF``) objects
and return ``NULL`` from the containing function.

We should turn this into a throw statement,
along the lines:

..  code-block:: java

        if (kwdict == null) {
            throw new TypeError(
                          "%s() got an unexpected keyword argument '%s'",
                          co.name, keyword);
        }



Delete the ``goto``.
The format string will need attention,
since (as here) the formatting codes may not be available,
but ``%s`` calls ``toString()``, which is generally right.

A useful regex for this is: ``_PyErr_Format\(\w+, PyExc_(\w+),`` replaced with
``throw new $1(``.


Translating Container Access
============================

CPython defines a range of macros, for use in the implementation only,
that expand to a direct field access,
so they are efficient but somewhat unsafe.
We'll follow suit, with direct access to fields of the corresponding types.

Useful regexes
--------------

``PyTuple_GET_ITEM\(([^,]+), ([^)]+)\)``
replaced by ``$1.value[$2]``.


``PyTuple_GET_SIZE\(([^)]+)\)``
replaced by ``$1.value.length``.


``PyDict_SetItem\((\w+), ([^,]+), ([^)]+)\)``
replaced by ``$1.put($2, $3)``.



