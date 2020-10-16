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


CPython source to Java in general
=================================

There is no mechanical way to convert CPython source to valid Java.
In the sections that follow,
we point out some replacements that work at the time of writing,
to move CPython source towards Jython source,
in particular contexts.
It is always safest to watch these operate,
rather than trust to global replacement.
Some replacements make a good beginning.

.. csv-table:: Editor regexes
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``NULL``", "``null``"
    "``static``", "``private``"
    "``(Py_s)?size_t``", "``int``"
    "``Py_UNUSED\((\w+)\)``", "``$1``"
    "``const\s+``", nothing
    "``""\n\s+""``", nothing
    "``(Py\w+)\s*\*\s*(\w+),((\s*\*\s*(\w+),?)+);``", "``$1 $2; $1 $3;``"
    "``(Py\w+)\s*\*\s*(\w+)``", "``$1 $2``"
    "``, \*(\w+)``", "``, $1``"
    "``Py_RETURN_TRUE``", "``return Py.True``"
    "``Py_RETURN_FALSE``", "``return Py.False``"
    "``Py_RETURN_NONE``", "``return Py.None``"
    "``Py_RETURN_NOTIMPLEMENTED``", "``return Py.NotImplemented``"

In general, it is difficult to decide whether a C pointer
is a reference to an object,
an array base, or serves as a moving pointer into an array.
Pointers that appear in arithmetic expressions
cannot simply be turned into array bases or object references.
On the plus side, we often find length arguments we do not need.

In something of a different league,
this can be useful in correlating the Java port to the CPython original,
but needs adaptation to the local file name (at least):

.. csv-table:: Method labelling
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``^((    \s*)(static\s+)?)(PyObject|int|void|boolean)\s+(\w+)\(``", "``$2// Compare CPython $5 in NAME.c\n$1 $4 $5\(``"



Names
=====

A correlation to names used in CPython is helpful for comparison.
Names may be shorter in Java than CPython,
because classes form a namespace that makes the prefixes
used in CPython unnecessary.
Similarly, overloading of names
(disambiguated by type signature)
allows us to dispense with complicated suffixes.
So ``PyEval_EvalCode``, ``PyEval_EvalFrame`` and ``PyEval_EvalFrameEx``
could all reasonably be called ``eval``,
or taking target type into account,
``PyFrame.eval`` and ``PyCode.eval``.

In view of the name-spacing available from packages and classes,
it seems superfluous to add ``Object`` to the end of every type,
apart from ``PyObject``.
So, ``PyTypeObject`` becomes ``PyType``, and so on.

CPython has a convention for interning strings
that are commonly used as identifiers.
This looks a little like a declaration statement,
that is then referenced later,
for example:

..  code-block:: c

    _Py_IDENTIFIER(__builtins__);
    ...
        builtins = _PyDict_GetItemIdWithError(globals, &PyId___builtins__);

It is in fact a macro that initialises a statically allocated ``struct``.
Special versions of many look-up methods take a ``_Py_Identifier``
as an argument where a string might otherwise be expected.
We have a similar facility
(less cunning but more transparent)
by defining names as static members of a class ``ID``.
We do not need a special version of any look-up methods to accept them.

..  code-block:: java

    class ID {
        static final PyUnicode __builtins__ = Py.str("__builtins__");
        static final PyUnicode __name__ = Py.str("__name__");
        ...
    }

These regular expressions are useful for the subjects covered here:

.. csv-table:: Editor regexes related to names
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``Py(\w+)Object``", "``Py$1``"
    "``&PyId_(\w+)``", "``ID.$1``"
    "``_Py_IDENTIFIER\(\w+\);``", "nothing"

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

.. csv-table:: Editor regexes dealing with type
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``Py_TYPE\((\w+)\)``", "``$1.getType()``"
    "``(\w+)->ob_type``", "``$1.getType()``"
    "``PyType_IsSubtype\(([^,]+), ([^)]+)\)``", "``($1).isSubTypeOf($2)``"
    "``PyObject_TypeCheck\(([^,]+),\s*([^)]+)\)``", "``Abstract.typeCheck($1, $2)``"
    "``(\w+)_Check\((\w+)\)``", "``($2.getType().isSubTypeOf($1.TYPE))``"
    "``(\w+)_CheckExact\(([^)]+)\)``", "``($2.getType()==$1.TYPE)``"
    "``(\w+)_CheckExact\(([^)]+)\)``", "``($2 instanceof $1)`` if one-to-one"
    "``PyDescr_TYPE\((\w+)\)``", "``$1.objclass``"
    "``PyDescr_NAME\((\w+)\)``", "``$1.name``"

Note that these assume a type model as in ``vsj2`` and ``evo3``.
This will be superseded in due course.


Object Lifecycle
================

Because Java manages the life-cycle of objects,
occurrences of ``Py_INCREF``, ``Py_XINCREF``, ``Py_DECREF`` and ``Py_XDECREF``
can generally be deleted,
and a number of less obvious calls
such as ``PyMem_Free`` and ``_PyObject_GC_TRACK``.

``Py_CLEAR`` should perhaps be replaced with assignment of ``null``,
rather than being removed totally.

.. csv-table:: Editor regexes dealing with type
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``Py_X?(IN|DE)CREF\([^)]+\);``", nothing
    "``Py_X?SETREF\(([^,]+),\s*([^)]+)\);``", "``$1 = $2;``"
    "``Py_CLEAR\(([^)]+)\);``", "``$1 = null;``"


Some Abstract Interface Methods
===============================

CPython defines a large API with structured names.
The methods are not always to be found in the file the name suggests.
We have less freedom in Java,
and consolidate their equivalents in classes suggested by the CPython name,
except that ``Object`` and ``PyObject`` don't seem like good choices,
so we settle for ``Abstract``.

.. csv-table:: Editor regexes for the abstract object API
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``PyObject_Repr``", "``Abstract.repr``"
    "``PyObject_Str``", "``Abstract.str``"
    "``PyObject_IsTrue``", "``Abstract.isTrue``"
    "``PyObject_Rich(Compare(Bool)?)``", "``Abstract.rich$1``"
    "``PyObject_Size``", "``Abstract.size``"
    "``PyObject_Get(Item|Attr)(Id)?``", "``Abstract.get$1``"
    "``PyObject_Set(Item|Attr)(Id)?``", "``Abstract.set$1``"
    "``_PyObject_LookupAttr(Id)?``", "``Abstract.lookupAttr``"
    "``PyObject_Is(Instance|Subclass)``", "``Abstract.is$1``"
    "``_PyObject_RealIs(Instance|Subclass)``", "``Abstract.recursiveIs$1``"


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

.. csv-table:: Editor regexes dealing with exceptions
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``_?PyErr_(SetString|Format)\(\s*PyExc_(\w+),``", "``throw new $2(``"


Translating Attribute Access
============================

CPython has optimisations and short-cuts based on interned identifiers,
but we have slightly different ones.
Java overloading means that we do not have to give them different names.

.. csv-table:: Editor regexes dealing with attribute access
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``_?PyObject_GetAttr(Id)?``", "``Abstract.getAttr``"
    "``_?PyObject_SetAttr(Id)?``", "``Abstract.setAttr``"
    "``_?PyObject_LookupAttr(Id)?\(([^,]+),\s*([^,]+),\s*&([^)]+)\)``", "``($4 = Abstract.lookupAttr($2, $3))==null?0:1``"
    "``_PyType_Lookup(Id)?\(([^,]+),\s*([^)]+)\)``", "``$2.lookup($3)``"


Translating Container Access
============================

CPython defines a range of macros, for use in the implementation only,
that expand to a direct field access,
so they are efficient but somewhat unsafe.
The substitutions below show both the (intended) public API,
and the direct counterparts possible for code in the core.

.. csv-table:: Editor regexes dealing with containers
   :header: "Match", "Replacement"
   :widths: 30, 20

    "``PyTuple_GET_SIZE\(([^)]+)\)``", "``$1.size()``"
    "``PyTuple_GET_SIZE\(([^)]+)\)``", "``$1.value.length``"
    "``PyTuple_GET_ITEM\(([^,]+), ([^)]+)\)``",  "``$1.get($2)``"
    "``PyTuple_GET_ITEM\(([^,]+), ([^)]+)\)``",  "``$1.value[$2]``"
    "``PyDict_SetItem\((\w+), ([^,]+), ([^)]+)\)``", "``$1.put($2, $3)``"



