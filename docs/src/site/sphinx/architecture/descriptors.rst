..  architecture/object-implementation.rst

.. _Descriptors:


Descriptors
###########

Descriptors lie behind many kinds of attribute access in Python.
They are central to the realisation of the `Python Data Model`_.
Behaviour we take for granted in Python depends upon this mechanism --
the behaviour of classes defined in Python and those built-in.

This section covers the several types of built-in descriptor,
based on a study of the CPython 3.8 source,
and depicting in UML a proposed Java implementation.

.. note::

    The description here assumes we shall follow the CPython implementation,
    and reproduce in Java classes the several data structures
    that express the descriptor and slot concept.

    While this is necessary for the elements visible as Python objects,
    supporting structures in the C implementation are complicated and
    depend on C language features to which Java has alternatives.
    Some of this complexity may stem from constraints of
    backwards compatibility in the C API that would not apply here.
    (We will use the Python data model as the acid test.)

    To begin with, we draw our pictures from the CPython implementation,
    but this document is allowed to evolve in search of Java ways.


Introduction
************

The definitive introduction to descriptors in Python is
the `Descriptor HowTo Guide`_ by Raymond Hettinger.
It is fairly precise about the semantics (just what we need),
but only scratches the surface of potential applications
(not our interest here).

Upon a thorough investigation,
one finds there are a lot of built-in descriptor types,
each with their supporting relationships and structures.
The descriptors are visible to code (if you know where to look),
so a Python implementation must faithfully reproduce this family of types.
Each descriptor and each supporting object in CPython
has been crafted individually,
and while some of the supporting data structures are shared,
beyond the descriptor protocol itself,
patterns are difficult to discern.
Names are confusingly similar.

We shall cover each type of built-in descriptor separately,
proposing implementation ideas.
When it comes to implementing them in Java,
we have some discretion about the supporting types
that are not visible to code,
to provide the expected behaviour another way.
On the whole,
we find we always need roughly the same object,
but the Java version could work differently.

As the design here evolves away from its CPython starting point,
we shall be looking for a design that may be efficiently implemented
using ``MethodHandle``\s.
We expect that this will support the use, ultimately,
of generated ``CallSite``\s.

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html

.. _Descriptor HowTo Guide:
    https://docs.python.org/3/howto/descriptor.html

.. _notation-for-sequence-diagrams:

Notation for Sequence Diagrams
==============================

In the UML sequence diagrams that follow,
calls (via the type slots) to special methods on Python objects
are shown in a simplified form that, for brevity,
makes the object itself the target.

A call to ``__neg__`` on a Python ``int`` may be shown simply as:

..  uml::

    hide footbox

    participant prog
    participant "x : PyLong = -5" as x

    prog -> x ++ : ~__neg__()
        return 5

The real sequence of events is more like:

..  uml::

    hide footbox

    participant prog
    participant "x : PyLong\n = -5" as x
    participant "int : PyType" as int
    participant "mh : MethodHandle\n = PyLong.~__neg__" as mh
    participant "r : PyLong\n = 5" as r

    prog -> x ++ : getType()
        return int
    prog -> int ++ : .nb_neg
        return mh
    prog -> mh ++ : invokeExact(x)
        mh -> mh : x.value.negate() = 5
        mh -> r ** : new(5)
        return r

Even this is a simplification
as it is difficult to represent the full detail at all.

A call to ``__call__`` itself deserves a note too.
The signature of ``__call__`` is, of course, ``(O, TUPLE, DICT)O``.
We shall generally elide the argument processing implicit in a Python call,
and show this as ``__call__(x, y)`` directed to the target
when we mean ``type(obj).__call__(obj, *(x, y), {})``.
For example:

..  uml::

    hide footbox

    participant prog
    participant "x : PyLong = 42" as x
    participant "y : PyLong = 10" as x
    participant "foo : PyFunction" as foo

    prog -> foo ++ : ~__call__(42, 10)
        return 32

The real sequence of events in a classic call is more like:

..  uml::

    hide footbox

    participant prog
    participant "args : PyTuple" as args
    participant "function : PyType" as function
    participant "mh : MethodHandle\n = PyFunction.~__call__" as mh
    participant "foo : PyFunction" as foo
    participant "r : PyLong\n = 32" as r

    prog -> args ** : Py.tuple(42, 10)
    prog -> foo ++ : getType()
        return function
    prog -> function ++ : .tp_call
        return mh
    prog -> mh ++ : invokeExact(foo, args, null)
        mh -> foo ++ : ~__call__(args, null)
            foo -> r ** : new(32)
            return r
        return r

This also is a simplification when it comes to certain steps.


Descriptors and ``__getattribute__``
************************************

Descriptors are mostly found and invoked from within
the slot function ``__getattribute__``.
This is called implicitly whenever attribute access (``o.name``) is needed.
It may be invoked through the built-in function ``getattr``,
and in CPython both lead to ``PyObject_GetAttr`` in ``object.c``.

The ``__getattribute__`` special function may be redefined by a type,
but there are just two significant versions in a Python implementation:

* the one defined for ``object`` (``PyBaseObject``),
  that applies when looking up attributes on almost any instance object, and
* the one defined for ``type`` (``PyType``),
  that applies when looking up attributes on a type object itself.

The distinction may be illustrated for the ``str`` type:

..  code-block:: python

    >>> str.replace  # Access through type.__getattribute__
    <method 'replace' of 'str' objects>
    >>> type(str.replace)
    <class 'method_descriptor'>
    >>> s = 'hello'
    >>> s.replace  # Access through inherited object.__getattribute__
    <built-in method replace of str object at 0x0000017B0EA82D30>
    >>> type(s.replace)
    <class 'builtin_function_or_method'>
    >>> str.replace.__get__(s)
    <built-in method replace of str object at 0x0000017B0EA82D30>

Notice that in the last step,
we obtain the same value from ``str.replace.__get__(s)``,
a descriptor access on the type followed by binding to ``s``,
as previously by attribute access ``s.replace`` on the instance.

Classes may redefine ``__getattribute__`` for their own instances,
to take full control of attribute access
(see `Customizing attribute access`_),
but this is not common outside major framework libraries.

A good introduction to this part of attribute access
is given by Brett Cannon in `Unravelling Attribute Access`_.
Brett only discusses attribute access on instances.

.. _Customizing attribute access:
    https://docs.python.org/3/reference/datamodel.html#customizing-attribute-access

.. _Unravelling Attribute Access:
    https://snarky.ca/unravelling-attribute-access-in-python/


.. _PyFunction:

Methods in Python (``PyFunction``)
**********************************

A Python function that is defined in the body of a class definition,
becomes a ``function`` object in the name space
that is passed into type creation.

In our implementation in Java,
this function is an instance of ``PyFunction``.
(In CPython,
the object is ``PyFunctionObject`` defined in ``funcobject.h``.)
During type creation,
this ``PyFunction`` is transferred to the dictionary of the ``PyType``.

A ``PyFunction`` is a descriptor because it defines ``__get__``.
During attribute look-up, ``__getattribute__`` recognises
the dictionary entry *on the type* as a descriptor,
and calls ``__get__``.
The object returned from ``PyFunction.__get__`` is a ``PyMethod``,
an object that binds the original ``PyFunction`` to a ``self`` object.
(In CPython,
the object is a ``PyMethodObject`` and is defined in ``classobject.h``.)

..  uml::
    :caption: ``PyFunction`` as a Descriptor

    class PyFunction {
        name
        qualname
        doc
        dict
        module
        globals
        defaults
        kwdefaults
        closure
        annotations
        {method} __get__()
        {method} __call__()
    }

    class PyMethod {
        {method} __get__()
        {method} __call__()
    }

    interface PyObject {
        getType()
    }

    PyMethod --> PyObject : self
    PyMethod -right-> PyFunction : func
    PyMethod <.. PyFunction : <<creates>>


A ``PyMethod`` is also a descriptor because it defines ``__get__``,
but ``PyMethod.__get__`` ignores its arguments and returns ``this``,
the descriptor itself.

The binding behaviour (``__get__``) of a function
is worth illustrating with sequence diagrams.
Suppose we have defined (pointlessly):

..  code-block:: python

    class C(str):

        def foo(self, x, y):
            print(f"foo called on '{self}' x+y = {(r:=x+y)}")
            return r

    c = C('hello')

..  code-block:: python

    >>> c.foo(2, 3)
    foo called on 'hello' x+y = 5
    5

We shall examine what happens when we call ``foo``.


..  _calling-method-through-object:

Calling a Python Method on an Object
====================================

In the simple call ``c.foo(2, 3)``,
the first step is the attribute access ``c.foo``.
Under the circumstances depicted,
in which the target object is not a type,
this is handled by the ``__getattribute__`` slot in the ``C`` type,
that is a ``MethodHandle`` to ``PyBaseObject.__getattribute__``.

We show this as a direct call from a notional program ``prog``,
when in reality the interpreter and the abstract object API are engaged.
As noted in :ref:`notation-for-sequence-diagrams`,
for simplicity,
we show slot function calls as if directed to the affected object itself.

``PyBaseObject.__getattribute__`` looks in the dictionary
of the type of the target object (the type ``C``).
The attribute access becomes a call to ``PyMethodDescr.__get__``,
that is the equivalent of ``C.__dict__['foo'].__get__(c, C)``.

This has to return something that may be called with the given arguments.
That "something" is here a ``PyMethod``,
in which the ``self`` field is assigned the object ``c``.
(In CPython, it is a ``PyMethodObject``,
in which the  ``__self__`` attribute is set to the target object,
whereas when representing a function,
``__self__`` would be ``None`` or the module.)

..  uml::
    :caption: Method Binding in ``c.foo(2, 3)``

    participant prog
    participant "c = C('hello')" as c
    participant "C : PyType" as C
    participant "f : PyFunction" as f
    participant "m : PyMethod" as m

    prog -> c ++ : ~__getattribute__("foo")
        c -> C ++ : lookup("foo")
            return f
        c -> f ++ : ~__get__(c, C)
            f -> m ** : new(f, c)
            return m
        return m

    prog -> m ++ : ~__call__(2, 3)
        m -> f ++ : ~__call__(c, 2, 3)
            return 5
        return 5

We can see that calling the ``PyMethod``
leads effectively to ``C.foo(c, 2, 3)``.
This, by the way, should also work if used directly in Python.


..  _calling-method-through-type:

Calling a Python Method through a Type
======================================

When a method call is made explicitly through the type,
for example ``C.foo(c, 2, 3)``,
most of the same code is involved as in the previous example.
The exception is that
the ``__getattribute__`` slot in the ``C`` object (a type),
is a ``MethodHandle`` to ``PyType.__getattribute__``.

Its behaviour differs from that of ``PyBaseObject.__getattribute__``.
In these circumstances, ``PyType.__getattribute__``
looks in the dictionary of the target object,
not that of the target's type.
But the target object is the type ``C``.
This means, of course, that the *same* dictionary is consulted
as for a call on the instance object,
so the *same* descriptor is found.

The difference is in the call to ``__get__`` that follows immediately.
``PyType.__getattribute__`` goes on to make a different call to ``__get__``,
to turn the access into ``C.__dict__['foo'].__get__(None, C)``.
Thus ``PyFunction.__get__`` knows something different is required
from the call we saw previously.

..  uml::
    :caption: Method Call in ``C.foo(c, 2, 3)``

    participant prog
    'participant "c = C('hello')" as c
    participant "C : PyType" as C
    participant "f : PyFunction" as f

    prog -> C ++ : ~__getattribute__("foo")
        C -> C : f = lookup("foo")
        C -> f ++ : ~__get__(null, C)
            return f
        return f

    prog -> f ++ : ~__call__(c, 2, 3)
        return 5

Notice that the object returned to ``prog`` from the attribute access
is the descriptor (the function ``f``) itself.
It is a behaviour of ``((PyFunction) f).__get__(obj, type)``
that when ``obj==null`` it simply returns ``f``.
The call that follows is directly on this descriptor,
which is the reason for this behaviour.


.. _classmethod-decorator:

The @classmethod Decorator (``PyClassMethod``)
**********************************************

A ``PyClassMethod`` is a descriptor because it defines ``__get__``.
An instance may be created to wrap any object.
Usually it is applied as a decorator to a function defined in Python.

The wrapped object is referenced by the field ``callable``,
which is exposed as the ``__func__`` member.

..  uml::
    :caption: The @classmethod Decorator

    interface PyObject {
        getType()
    }

    class PyClassMethod {
        dict
        {method} __get__()
    }

    class PyFunction {
        name
        qualname
        doc
        dict
        module
        globals
        defaults
        kwdefaults
        closure
        annotations
        {method} __get__()
        {method} __call__()
    }

    class PyMethod {
        {method} __get__()
        {method} __call__()
    }

    PyClassMethod -right-> PyObject : callable
    PyObject <|.. PyFunction

    PyClassMethod ..> PyMethod : <<creates>>

    PyMethod -up-> PyObject : self
    PyMethod --right-> PyFunction : func


The constructor of a Python ``classmethod``
(the ``__init__`` in fact)
implements the decorator ``@classmethod``.
In that context, it is the constructed ``PyClassMethod``
that is inserted in the dictionary of the type under the function's name.

A ``PyClassMethod`` is not itself callable,
rather ``PyClassMethod.__get__(obj, type)`` returns a ``PyMethod``
that binds ``type`` as the first argument of the callable,
or if ``type`` is not given, then binds the type of ``obj``.
This is likely to be meaningful only if the object is callable.
If the object to which the decorator was applied is not in fact callable,
the error is raised from the invocation of that ``PyMethod``,
and not before.

The descriptor of a class method in a built-in class
does not involve ``PyClassMethod``,
but a special descriptor for built-in types.
(See :ref:`PyClassMethodDescr`.)


.. _staticmethod-decorator:

The @staticmethod Decorator (``PyStaticMethod``)
************************************************

A ``PyStaticMethod`` is a descriptor because it defines ``__get__``.
As with ``PyClassMethod`` (see :ref:`classmethod-decorator`),
an instance may be created to wrap any object.
Often it is applied as a decorator to a function defined in Python,
but it is also applied automatically to methods of built-in classes
(defined in Java)
when they are identified as static.
(See :ref:`PyStaticMethod`.)

The wrapped object appears as the ``__func__`` member.

..  uml::
    :caption: The @staticmethod Decorator

    interface PyObject {
        getType()
    }

    class PyStaticMethod {
        dict
        {method} __get__()
    }

    class PyFunction {
        name
        qualname
        doc
        dict
        module
        globals
        defaults
        kwdefaults
        closure
        annotations
        {method} __get__()
        {method} __call__()
    }

    PyStaticMethod -right-> PyFunction : callable
    PyObject <|.. PyFunction


The constructor of a Python ``staticmethod``
(the ``__init__`` in fact)
is the decorator ``@staticmethod``
seen in Python class definitions.
In that context, the constructed ``PyStaticMethod``
is the object in the dictionary of the type under the function's name,
and the ``callable`` (exposed as ``__func__``) is the function decorated.

A ``PyStaticMethod`` is not itself callable,
rather ``PyStaticMethod.__get__`` returns the associated callable,
ignoring its arguments.
(There is no real binding to do.)
If the object to which ``staticmethod`` was applied is not in fact callable,
the error is raised from the attempted call and not before.


.. _descriptors-builtin:

Descriptors for Built-in Types
******************************

Our first description of attribute access
involved the descriptor behaviour of a familiar object (a ``function``).
Many descriptors are built-in types, specifically created for the purpose.
In CPython, these are mostly defined in ``descrobject.h``
and implemented in ``descrobject.c``.
They share much of their mechanism,
although they are not sub-classes one of another in Python.

This sub-section provides a structural overview
of those descriptors translated to Java,
before we launch into the detail of each.
In proposing a Java design,
we are constrained to match the visible behaviour of
the descriptors in CPython, less so the supporting classes.

We will also follow CPython implementation details,
at least provisionally.
In Java, this gives rise to a fair few related classes.


..  uml::
    :caption: Descriptors and Wrappers

    interface PyObject {
        getType()
    }

    Descriptor .up.|> PyObject
    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- DataDescriptor
    abstract class DataDescriptor {
        {abstract} {method} __set__()
        {abstract} {method} __delete__()
    }

    DataDescriptor <|-- PyMemberDescr
    abstract class PyMemberDescr {
        flags : EnumSet<Flag>
        handle : VarHandle
        doc : String
        {method} __get__()
        {method} __set__()
        {method} __delete__()
        {abstract} get()
        {abstract} set()
        {abstract} delete()
    }

    DataDescriptor <|-- PyGetSetDescr
    class PyGetSetDescr {
        getter : MethodHandle
        setter : MethodHandle
        deleter : MethodHandle
        doc : String
        {method} __get__()
        {method} __set__()
        {method} __delete__()
    }

    Descriptor <|-- PyMethodDescr
    PyMethodDescr --> MethodDef : method

    abstract class MethodDef {
        name
        meth
        flags
        doc
    }

    Descriptor <|-- PyWrapperDescr
    PyWrapperDescr --> SlotDef : base

    class SlotDef {
        name
        offset : Slot
        function : SlotFunction
        wrapper : WrapperFunction
        doc
        flags
    }

    class PyMethodDescr {
        vectorcall
    }

    class PyWrapperDescr {
        wrapped
    }


Our names for these are not quite the same as CPython's,
having elided the suffix ``Object`` from the class names,
prefix ``Py`` where it is not a Python object,
and the prefixes like ``d_`` and ``ml_`` from member names.
Fields that in C are a function pointer
may become ``MethodHandle`` in Java,
although lambda functions and sub-classing may be chosen;
those that are a kind of "offset" could become ``VarHandle``;
those that are ``int`` selectors may become ``enum`` or ``EnumSet``.


.. _PyMemberDescr:

Members (``PyMemberDescr``)
***************************

During type creation from a Java class definition,
a ``PyMemberDescr`` that appears in the dictionary of the ``PyType``
is the result of an ``@Member`` annotation applied to a field.

..  uml::
    :caption: Member Descriptor

    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- DataDescriptor
    abstract class DataDescriptor {
        {abstract} {method} __set__()
        {abstract} {method} __delete__()
    }

    DataDescriptor <|-- PyMemberDescr
    abstract class PyMemberDescr {
        flags : EnumSet<PyMemberDescr.Flag>
        handle : VarHandle
        doc : String
        {method} __get__()
        {method} __set__()
        {method} __delete__()
        {abstract} get()
        {abstract} set()
        delete()
    }

    enum PyMemberDescr.Flag {
        READONLY
        OPTIONAL
        READ_RESTRICTED
        WRITE_RESTRICTED
    }

    class PyMemberDescr._int {
        get()
        set()
    }
    class PyMemberDescr._double {
        get()
        set()
    }

    abstract class PyMemberDescr.Reference {
        optional : boolean
        delete()
    }
    PyMemberDescr <|-- PyMemberDescr.Reference
    note on link #White: and other specialisations to type

    class PyMemberDescr._String {
        get()
        set()
    }

    PyMemberDescr .right.> PyMemberDescr.Flag
    PyMemberDescr <|-- PyMemberDescr._int
    PyMemberDescr <|-- PyMemberDescr._double
    PyMemberDescr.Reference <|-- PyMemberDescr._String

The ``PyMemberDescr`` descriptor is based on a ``VarHandle``
designating the field.
The ``__get__``, ``__set__`` and ``__delete__`` of a ``PyMemberDescr``
must get, set or delete the field in instances of the defining type
through this ``VarHandle``.
Setting will be disallowed if the member is annotated as read-only,
and if setting is allowed, deletion may still be impossible for the type,
e.g. for an ``int``.
When disallowed, both raise an ``AttributeError``.

In CPython,
a ``PyMemberDef`` has to be created for each member to be exposed,
specifying the type and offset of the member in an instance,
and placed in a short, static table referenced from the type object.
In Java, we need no intermediary:
we may make the descriptor directly,
using information available by reflection,
and from the identifying ``@Member`` annotation,
optional properties or further annotations:

..  code-block:: java

    class ObjectWithMembers implements PyObject {
        // ...
        @Member
        @DocString("The i property")
        int i;
        @Member("text")
        String t;
        @Member(readonly = true)
        int i2;
        @Member(readonly = true, value = "text2")
        String t2;
        // ...
    }

We express through Java sub-classes,
the specific implementation of ``get()``, ``set()`` and ``delete()``,
appropriate to the implementation type.
These do not result in distinct Python types.
This is possible because only a fixed repertoire of implementation types
is supported.

(In CPython, the types are defined as constants in ``structmember.h``,
and the set/get functions contain a big case statement.
The API is used exclusively by member descriptors.)

Deletion, ``None`` and ``null``
===============================

Reference values in Java may be ``null``.
When we implement an attribute by a Java reference type,
we may use this possibility to augment its natural range with
either ``None`` or "not defined", but not both.

This possibility is not available to an attribute implemented as a primitive,
since primitive values have no equivalent of ``null``.

The only writeable reference amongst CPython member types is ``object``
(signified by ``T_OBJECT`` in the ``PyMemberDef.type`` field).
Strings (``STRING_T``) are only supported for reading,
although the possibility of ``null`` is still accommodated in the code.
The historic norm is to interpret ``null`` as ``None`` in a ``get``
but for ``set`` to accept and store ``None`` in the attribute.
The asymmetry does not seem to bother anyone.

In CPython it is acceptable to supply a ``null`` value
to ``set`` an attribute,
but in our Java implementation we shall spell that ``delete``.
It is not an error to ``get`` an ``object`` member
that has been deleted but remains visible as ``None``,
or to delete it repeatedly.

An addition to the set of CPython member types (``T_OBJECT_EX``),
defines a variant for the ``object`` member
in which a ``null`` value signifies a deleted (or undefined) attribute.
After deleting this type of attribute,
``get`` and ``delete`` raise ``AttributeError``,
but a ``set`` (non-null value, even ``None``) re-creates it.


..  csv-table::  CPython treatment of ``null`` and ``None`` in ``get`` and ``set``
    :header: "Descriptor type", "Current value", "``get`` returns", "``set None`` effect", "``set null`` effect"
    :widths: 15, 10, 10, 10, 10

    "``Primitive``",   "``x``",    "``x``",           "type error", "type error"
    "``T_OBJECT``",    "``x``",    "``x``",           "``None``",   "``null``"
    "``T_OBJECT``",    "``null``", "``None``",        "``None``",   "``null``"
    "``STRING_T``",    "``x``",    "``str(x)``",      "readonly",   "readonly"
    "``STRING_T``",    "``null``", "``None``",        "readonly",   "readonly"
    "``T_OBJECT_EX``", "``x``",    "``x``",           "``None``",   "``null``"
    "``T_OBJECT_EX``", "``null``", "attribute error", "``None``",   "attribute error"


We will allow this behaviour for any supported reference type,
through the flag ``DataDescriptor.OPTIONAL``
and annotation property ``Member.optional``.
When ``optional == true``, the attribute may be deleted,
otherwise ``delete`` sets it to ``null`` appearing as ``None``.

..  csv-table:: Interpreting ``null`` and ``None`` in ``get``, ``set`` and ``delete``
    :header: "Field type", "Optional", "Current value", "``get`` returns", "``set None`` effect", "``delete`` effect"
    :widths: 10, 10, 10, 10, 10, 10

    "``Primitive``", "``false``", "``x``",    "``x``",           "type error", "attribute error"
    "``PyObject``",  "``false``", "``x``",    "``x``",           "``None``",   "``null``"
    "",              "",          "``null``", "``None``",        "``None``",   "``null``"
    "``String``",    "``false``", "``x``",    "``x``",           "type error", "``null``"
    "",              "",          "``null``", "``None``",        "type error", "``null``"
    "``PyObject``",  "``true``",  "``x``",    "``x``",           "``None``",   "``null``"
    "",              "",          "``null``", "attribute error", "``None``",   "attribute error"
    "``String``",    "``true``",  "``x``",    "``x``",           "type error", "``null``"
    "",              "",          "``null``", "attribute error", "type error", "attribute error"

This is consistent with CPython behaviour,
whilst allowing for more assignable reference types (if we want them).
Th type errors arise because a String cannot be assigned the value ``None``,
even though that's what deleted appears to set.


.. _PyGetSetDescr:

Attributes (``PyGetSetDescr``)
******************************

During type creation,
a ``PyGetSetDescr`` that defines an attribute
in the dictionary of the ``PyType``,
is created from annotations that identify
the get, set and delete methods associated with the attribute by name.

The statically-allocated ``PyGetSetDef`` in CPython,
that ends up as part of the ``PyGetSetDescr``,
has a counterpart ``GetSetDef`` in the Java implementation.
However, that exists only while we find all the annotated methods
(and the documentation string)
for each attribute in the implementation of a type.
The ``PyGetSetDescr`` finally holds all the necessary information directly.
The ``java.lang.reflect.Method`` objects in our ``GetSetDef``
become ``MethodHandle``\s at that point
and the ephemeral ``GetSetDef`` is discarded.

Unlike those of a ``PyMemberDescr``,
the ``get``, ``set`` and ``delete`` operations of a ``PyGetSetDescr``
are provided explicitly by the class containing the attribute.
These methods must at least convert between
the internal representation of the attribute
and one acceptable as a Python object.
However, the scheme offers an unlimited range of possibilities
for computing or transforming the stored value to expose it,
while ``PyMemberDescr`` is limited to actual fields
with a type among those predefined.


..  uml::
    :caption: Get-Set Descriptor

    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- DataDescriptor
    abstract class DataDescriptor {
        {abstract} {method} __set__()
        {abstract} {method} __delete__()
    }

    DataDescriptor <|-- PyGetSetDescr
    class PyGetSetDescr {
        {method} __get__()
        {method} __set__()
        {method} __delete__()
        get : MethodHandle
        set : MethodHandle
        delete : MethodHandle
        doc : String
    }
    PyGetSetDescr <.right. GetSetDef : <<specifies>>

    class GetSetDef {
        name
        get : Method
        set : Method
        delete : Method
        doc : String
    }

The choice to use ``MethodHandle`` here opens the possibility,
in a later implementation,
of making the particular ``get``, ``set`` or ``delete`` method
the target of an attribute-access ``CallSite``,
guarded on the type of the object in which the attribute named is sought.

The ``__get__``, ``__set__`` and ``__delete__`` of a ``PyGetSetDescr``,
get, set and delete the attribute in instances of the defining type,
by invoking the corresponding handle.
The implementation is free
to make any interpretation it needs of those actions.
The attribute may be made read-only by simply not specifying a setter,
and indelible by not specifying a deleter.
When not defined,
any of these handles defaults to a method that throws ``Slot.EmptyException``,
which the surface methods ``__get__``, ``__set__`` and ``__delete__``
catch to raise an ``AttributeError`` with an informative message.

(In the CPython code base,
the response to a disallowed deletion is hand-crafted per attribute,
detecting the value ``NULL`` in the setter method.
Objects mostly produce ``AttributeError``, but some ``TypeError``,
and in ``pyexpat.c`` it is ``RuntimeError``.
This unwelcome variety remains available to us by defining a deleter.)

CPython adds a ``void* closure`` member to its ``PyGetSetDef``,
and provides it as an extra parameter when calling
the ``get`` and ``set`` methods of the ``PyGetSetDef``.
There are no uses of this in the CPython code base,
and we do not replicate it.
The closure must be information available when the ``PyGetSetDef`` is created,
that is, statically and independent of the object instance.
If an operation needs additional information,
preserving context from that point,
it could be bound into the defining method.


.. _PyMethodDescr:

Built-in Methods (``PyMethodDescr``)
************************************

During type creation,
a ``PyMethodDescr`` that appears in the dictionary of the ``PyType``,
is created from a ``MethodDef`` specified by the class.
A ``MethodDef`` (compare ``PyMethodDef`` in CPython),
represents a method defined in Java,
that is to be exposed as the method of a Python ``object``.

In the following model,
we provisionally reproduce the CPython approach,
in which a ``MethodDef`` has several characteristics,
expressed through a set of flags.
It seems likely that an approach using sub-classes of ``PyMethodDescr``
will supersede this before the first implementation.

..  uml::
    :caption: Built-in Method Descriptor

    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- PyMethodDescr
    class PyMethodDescr {
        vectorcall : MethodHandle
        {method} __get__()
        {method} __call__()
    }
    PyMethodDescr -right-> MethodDef : method

    class MethodDef {
        name
        meth : MethodHandle
        flags : EnumSet<MethodDef.Flag>
        doc
    }

    enum MethodDef.Flag {
        VARARGS
        KEYWORDS
        CLASS
        STATIC
        FASTCALL
    }

    MethodDef .up.> MethodDef.Flag : <<uses>>

    class PyJavaFunction {
        module : PyUnicode
        tpCall : MethodHandle
        {method} __call__(args, kwargs)
        {method} __vectorcall__(args, start, nargs, kwnames)
    }
    PyJavaFunction -up-> MethodDef : methodDef

    interface PyObject {
        getType()
    }

    PyJavaFunction <|--left-- PyJavaMethod
    PyJavaMethod --> PyObject : self

    PyMethodDescr ..> PyJavaMethod : <<creates>>


In CPython,
``PyMethodDef``\s occur in short, statically-defined tables,
each entry defining a method (class, static or instance) or a function.
We have already used ``MethodDef``
to represent Java functions exposed from modules
(see :ref:`MethodDef-and-PyJavaFunction`).
There,
we added annotations to the functions to be exposed,
that were processed to create a ``MethodDef[]`` table.
Each ``MethodDef`` led to a ``PyJavaFunction``
in the dictionary of each instance of the module.

This is not quite what we need for the methods of a class.
Here we can use the annotation idea again,
but each ``MethodDef`` should lead to a ``PyMethodDescr``
in the dictionary of the type.

As with every other attribute access mediated by a descriptor,
a reference to the method via an object invokes ``__get__``.
Rather than getting a value or object reference from the target object,
as in a data descriptor,
``PyMethodDescr.__get__`` returns a callable object
binding the method definition and the target object.
Thus in ``'hello'.replace('ell', 'ipp')``,
the call to ``__get__`` returns the object ``'hello'.replace``,
and then that is called with the arguments ``('ell', 'ipp')``.

We represent this binding object by an instance of the ``PyJavaMethod`` class.
(In CPython the object created is another use of ``PyCFunctionObject``.)
Observe that ``PyJavaMethod`` is frequently an ephemeral object,
existing only until the call can be made.

A ``PyMethodDescr`` is itself a callable object,
invoking the method it describes as if it were the defining function.
In ``str.replace('hello', 'ell', 'ipp')``,
``__get__`` returns the object ``str.replace``,
which is the descriptor itself,
and then that is called with the arguments ``('hello', 'ell', 'ipp')``.
No binding ``PyJavaMethod`` is necessary in this case.


Optimising Method Calls
=======================

CPython has dedicated support in the compiler and byte code
(the opcodes ``LOAD_METHOD`` and ``CALL_METHOD``)
that effectively converts ``s.replace('ell', 'ipp')``
into  ``str.replace(s, 'ell', 'ipp')``,
avoiding creation of the bound object.
The possibility can be identified at compile time,
but the translation can only be done at run-time,
when the type of ``s`` is known and ``str.replace`` proves to be a method.

The CPython ``PyMethodDescr`` supports the vector call protocol,
where ``tp_vectorcall_offset`` in the type
references the ``vectorcall`` field in the instance.
The ``PyMethodDescr`` in CPython sets this field to a C function pointer
that refers to one of several fixed wrapper functions,
the choice being made according to the characteristics in the ``PyMethodDef``.
The wrapper function always has the vector call signature,
and internally supplies these arguments
(in the right number and arrangement, with necessary checks made)
to a call to ``meth`` in the attached ``PyMethodDef``.

The vector call protocol is not especially well suited
to a Java implementation.
IFor Java, we need an optimisation similar to ``LOAD_METHOD``-``CALL_METHOD``,
but applied to Java call sites.
In an implementation based on interpreting CPython byte code,
those opcodes must be supported,
but perhaps only in their fall back forms,
or in a form preferentially optimised for Java call sites.


Calling a Built-in Method on an Object
======================================

The binding behaviour (``__get__``) of a method descriptor
is more complex than that of a data descriptor.
It is worth illustrating it with sequence diagrams.
We will ignore the ``LOAD_METHOD``-``CALL_METHOD`` optimisation for now.
As noted in :ref:`notation-for-sequence-diagrams`,
for simplicity,
we show slot function calls as if directed to the affected object itself.

In the simple call ``'hello'.replace('ell', 'ipp')``,
the first step is the attribute access ``'hello'.replace``.
Under the circumstances depicted,
in which the target object is not a type,
this is handled by the ``__getattribute__`` slot in the ``str`` type,
that is a ``MethodHandle`` to ``PyBaseObject.__getattribute__``.

``PyBaseObject.__getattribute__`` looks in the dictionary
of the type of the target object (the type ``str``).
The attribute access becomes a call to ``PyMethodDescr.__get__``,
that is the equivalent of ``str.__dict__['replace'].__get__('hello', str)``.

This has to return something that may be called with the given arguments.
That "something" is here a ``PyJavaMethod``,
in which the ``self`` field is assigned the string ``'hello'``.
(In CPython, it is a ``PyCFunctionObject``.)

..  uml::
    :caption: Method Binding in ``'hello'.replace('ell', 'ipp')``

    participant prog
    participant "s = 'hello'" as s
    participant "str : PyType" as str
    participant "d : PyMethodDescr" as d
    participant "m : PyJavaMethod" as m

    prog -> s ++ : ~__getattribute__("replace")
        s -> str ++ : lookup("replace")
            return d
        s -> d ++ : ~__get__(s, str)
            d -> m ** : new(replace, s)
            return m
        return m

    prog -> m ++ : ~__call__("ell", "ipp")
        m -> s ++ : replace("ell", "ipp")
            return "hippo"
        return "hippo"


We can see that calling the ``PyJavaMethod``
leads effectively to ``str.replace('hello', 'ell', 'ipp')``.
This, by the way, should also work if used directly in Python.
We look at that next,
as it provides insight into
why ``PyMethodDescr`` must be callable.


Calling a Built-in Method through a Type
========================================

When a method call is made explicitly through the type,
for example ``str.replace('hello', 'ell', 'ipp')``,
most of the same code is involved as in the previous example.

The exception is that
the ``__getattribute__`` slot in the ``str`` object (a type),
is a ``MethodHandle`` to ``PyType.__getattribute__``.
The situation is like the one we examined in
:ref:`calling-method-through-type`.

``PyType.__getattribute__``
looks in the dictionary of the target object itself (the type ``str``),
to turn the access into ``str.__dict__['replace'].__get__(None, str)``.
This is a different call to ``PyMethodDescr.__get__`` from previously,
and returns to ``prog`` the descriptor itself.

..  uml::
    :caption: Method Call in ``str.replace('hello', 'ell', 'ipp')``

    participant prog
    participant "str : PyType" as str
    participant "d : PyMethodDescr" as d
    participant "s = 'hello'" as s

    prog -> str ++ : ~__getattribute__("replace")
        str -> str : d = lookup("replace")
        str -> d ++ : ~__get__(null, str)
            return d
        return d

    prog -> d ++ : ~__call__(s, "ell", "ipp")
        d -> s ++ : replace("ell", "ipp")
            return "hippo"
        return "hippo"


It is a behaviour of ``((PyMethodDescr) descr).__get__(obj, type)``
that when ``obj==null`` it simply returns `descr`.
The call that follows is directly on this descriptor,
which is why ``PyMethodDescr`` must be callable.


.. _PyWrapperDescr:

Special Methods (``PyWrapperDescr``)
************************************

The ``PyWrapperDescr`` is one visible part of the mechanism that
allows a slot function defined in Java to be called from Python
using its special method name.
For example,
any type whose implementation class ``T`` defines:

..  code-block:: java

    PyObject __neg__(T self) { ... }

will define the special method ``__neg__``,
precisely because its dictionary contains an entry for ``"__neg__"``.
The entry is an instance of ``PyWrapperDescr``
that knows how to call the Java method from Python.

Special Method Creation in CPython
==================================

In CPython,
a type defined in C, writes its slot function implementations as pointers,
into an instance of ``PyTypeObject`` during its initialisation.
Slots the type does not define,
but may inherit,
are null initially in the type object

During type creation,
CPython examines the slots in the type,
guided by the (single, global) table of ``slotdef`` entries.
The ``slotdefs[]`` table in ``typeobject.c`` is an array of these,
built by a set of clever macros.
The entries in ``slotdefs[]`` are ordered by ascending slot offset
in the (heap) type object.
We note, but do not resolve in the present discussion, the issues that:

* Some offsets are repeated in successive entries,
  representing slots to which more than one special method contributes,
  e.g. ``__add__`` and ``_radd__`` contribute to ``nb_add``.

* Some special method names occur more than once,
  representing special methods that define more than one slot,
  e.g. ``__add__`` contributes to ``nb_add`` and may define ``sq_concat``.

After establishing the type hierarchy,
it will be apparent which slots are eligible to be inherited.
A slot defined by the new type object (a non-null type slot)
results in a new descriptor for that special method.
One that is not defined initially
may be filled from an inherited descriptor.

Slots are filled not only from inherited ``PyWrapperDescr``\s,
but from any inherited descriptor of the right name.

A ``PyWrapperDescr`` provides the slot value directly.

For any other descriptor type,
the ``slotdef`` of matching name provides a pointer
compatible with that slot
(using the C pointer in field ``function``),
that will call the descriptor.
The functions that do so have names matching ``slot_*``
and are found in ``typeobject.c``.
This makes it possible to define a special method in Python
that becomes the meaning of an operator through filling that type slot,
e.g. where ``__neg__`` defined in Python
provides the meaning of unary ``-`` through filling ``nb_neg``.


Special Method Creation in Java
===============================

In the Java implementation,
a ``PyWrapperDescr`` that appears in the dictionary of the ``PyType``,
is created from a single unique table of ``SlotDef`` entries,
and information particular to the implementing class.
We differ from CPython slightly in that
a ``PyWrapperDescr`` is created because a Java method exists
with the right name and signature,
and then the slot in the ``PyType`` is filled as a result.
The ``PyWrapperDescr`` contains a reference to the target Python type,
the method name, and a handle to the implementation compatible with the slot.

The class ``SlotDef``
(variously known as ``wrapperbase`` and ``slotdef`` in CPython)
is somewhat like our ``Slot`` enum class,
but there may be more than one per slot.

..  note::
    If we can resolve the competition for slots and names that CPython has,
    in favour of a one-to-one alignment of slots and special method names,
    then ``Slot`` as seen in the ``evo3`` experiments and
    ``SlotDef`` as we need it can be the same thing.

    We cannot depart from the Python data model to achieve this.
    But we do not have to reproduce the CPython approach internally,
    or depart from the data model where CPython does (slightly).
    Jython 2 seems to do this without violating to the data model,
    naming its slot functions after the special functions.


CPython Structures
------------------

We have an idea to diverge a little from CPython at this point,
so as a reference we look at how calling a slot wrapper works in CPython.

In CPython, each ``slotdef`` (also known as ``struct wrapperbase``)
identifies its slot in the type object by an ``offset`` field,
and the special function by its name (like ``"__sub__"``).
It contains two pointers to wrapper functions
helpfully named ``wrapper`` and ``function``,
and some other fields that need not concern us just now.
The wrapper functions are necessarily independent of the target object type.
``function`` is for synthesising a type slot
from a special method defined in Python,
while ``wrapper`` does the job at hand: calling a slot from Python.

We can think of ``slotdef`` as an association class
between notional classes ``TypeSlot`` and ``SpecialFunction``.

..  uml::
    :caption: SlotDef is an association class (CPython)

    class TypeSlot {
        offset
    }

    class SpecialMethod {
        name
    }

    class slotdef {
        function : void *
        wrapper : wrapperfunc
        doc : const char *
        flags : int
    }

    TypeSlot "1..*" --right- "1..*" SpecialMethod
    slotdef .. (TypeSlot, SpecialMethod)


Neither of the associated classes exists in CPython as a ``struct``:
a ``TypeSlot`` is known only by its offset,
while a ``SpecialFunction`` is simply present as a name.
The association of special functions and slots is many-to-many,
because special functions may compete for slots
and contribute to more than one.
(This is a complication we might be able to avoid.)

..  uml::
    :caption: Special Methods in CPython

    class PyWrapperDescrObject {
        d_name
        d_wrapped : void *
        {method} __get__()
        {method} __call__()
    }
    PyWrapperDescrObject -right-> slotdef : d_base
    PyWrapperDescrObject -left-> PyTypeObject : d_type

    class slotdef {
        name : const char *
        offset : int
        function : void *
        wrapper : wrapperfunc
        doc : const char *
        flags : int
    }

    class wrapperobject {
        {method} __call__()
    }

    wrapperobject --left-> PyObject : self
    wrapperobject -up-> PyWrapperDescrObject : descr


When the ``PyWrapperDescrObject d`` (for ``__sub__``, say) is called,
or a binding ``wrapperobject`` it produced by ``__get__`` is called,
the ``d_wrapped`` field of ``d`` provides the required implementation.

Although the descriptor holds this function pointer,
``d->d_wrapped`` may not be used directly to satisfy the call.
Only the ``slotdef`` has the details needed to call it properly:
the number, type and arrangement of the arguments.

The ``wrapper`` field in the related ``slotdef``
points to a function with a standard signature ``(self, args, wrapped)``.
A ``PyWrapperDescrObject`` is able to call that
without specific knowledge of the slot type.
In the signature, ``wrapped`` is the particular slot implementation
the ``PyWrapperDescrObject`` holds.
(In fact, there are two standard signatures: the other with keywords,
and a flag in the ``slotdef`` decides how to cast ``wrapper``.)

All these wrapper functions are found in ``typeobject.c``,
and have names matching ``"wrap_*"``.
Each such wrapper has a body that knows how to arrange
these arguments in a pattern that suits the wrapped slot.

In the case of ``__sub__``,
the ``slotdef`` represents an unreflected binary operation,
so ``wrapper`` was set by static initialisation to a generic method
called ``wrap_binaryfunc_l``.
This function has a body along the lines ``return (*wrapped)(self, args[0])``.
The companion function ``wrap_binaryfunc_r``,
for wrapping reflected operations like ``__rsub__``,
has a body along the lines ``return (*wrapped)(args[0], self)``,
that is, with the arguments reversed.

All this involves heavy use of pointers and offsets into structures,
and casts.
Surely Java can offer us a less brutal way?


A Java Equivalent
-----------------

The ``SlotDef`` is somewhat the counterpart of ``MethodDef``,
as described in :ref:`PyMethodDescr`.
There,
we proposed that an annotation would identify each function to be exposed.
The annotations were processed to create a table of definitions,
then descriptors from that table.

Here, the set of possible method names and signatures
is fixed in advance by the Python data model,
and so the generation of descriptors for any given class
may be driven by one central table of ``SlotDef``\s, fixed statically.
Every entry in that table
that finds a matching definition in the current Java implementation class,
should create a new descriptor.

The name of the descriptor is the name of
the special function specified by the ``SlotDef``.
The ``SlotDef`` holds all the information needed
to create the appropriate ``PyWrapperDescr``
except for the handle to the implementation.
That will be supplied during type creation
and held by the ``PyWrapperDescr`` as ``MethodHandle wrapped``.

``PyWrapperDescr`` is directly callable (as in ``int.__sub__(42, 10)``)
and also supports a ``__get__`` that returns a ``PyMethodWrapper``
binding an instance (as in ``(42).__sub__(10)``).

..  uml::
    :caption: Special Method Slot Descriptor ``PyWrapperDescr``

    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- PyWrapperDescr
    abstract class PyWrapperDescr {
        {method} __get__()
        {method} __call__()
        {abstract} callWrapped()
        wrapped : MethodHandle
    }
    PyWrapperDescr -right-> SlotDef : base

    class SlotDef {
        name
        doc
        function : SlotFunction
        makeDescriptor() : PyWrapperDescr
    }

    enum Wrapper {
        BINOP_L
        BINOP_R
        CALL
        makeDescriptor() : PyWrapperDescr
    }

    class PyMethodWrapper {
        {method} __call__()
    }

    SlotDef --> Wrapper : wrapper

    PyMethodWrapper --left-> PyObject : self
    PyMethodWrapper --up-> PyWrapperDescr : descr

    Wrapper ..> PyWrapperDescr : <<creates>>

    interface PyObject {
        getType()
    }

The method handle ``PyWrapperDescr.wrapped``
conforms to the signature of the slot
provided in the ``Slot`` of the same name.
However, ``PyWrapperDescr.__call__`` and ``PyMethodWrapper.__call__``
require the classic call arguments ``(args, kwargs)``.
(CPython's ``PyMethodWrapper`` does not support the vector call.
We could, but invocation does not seem likely to be critical to performance.)

In both calls,
positional arguments must be extracted from the tuple
and made Java arguments to ``wrapped.invokeExact``,
in a pattern characteristic of the broad type of the slot
(binary operation, reflected binary operation, attribute access, call, etc.).
This data movement is in ``PyWrapperDescr.callWrapped()`` for both.

We express the specific invocation pattern
that the ``SlotDef`` understands for that type of slot,
by means of a pluggable behaviour, the ``enum Wrapper``.
An enum is appropriate because there is a predetermined variety of patterns.
The ``Wrapper`` is a factory that creates the appropriate
sub-class of ``PyWrapperDescr``,
specialising ``callWrapped()`` for the invocation pattern.
Both a direct call on the descriptor,
and a call to a ``PyMethodWrapper`` depend on ``callWrapped()``,
and so this programs the calling behaviour entirely.

Wherever the descriptor is inherited by a Python sub-class,
the corresponding type slot will be set to ``wrapped``.
Most uses of the operation represented by a special function
already know the signature expected,
and go via the handle cached in the type object.
However,
in order to gain an understanding of the descriptor,
it is worth looking at how it can be be used in calls.


Calling a Special Method through its Descriptor
===============================================

A ``PyWrapperDescr`` is a callable object,
invoking the method it describes as if it were the defining function.

In ``int.__sub__(42, 10)``,
``__get__`` returns the object ``int.__sub__``,
which is the descriptor itself,
and that is then called with the arguments ``(42, 10)``:

..  code-block:: python

    >>> (d := int.__sub__)
    <slot wrapper '__sub__' of 'int' objects>
    >>> type(d)
    <class 'wrapper_descriptor'>
    >>> d(42, 10)
    32

The objects that participate in the interaction are these:

..  uml::
    :caption: Objects in ``int.__sub__(42, 10)``

    object "int : PyType" as int
    object "42 : PyLong" as self
    object "10 : PyLong" as other
    object "d : PyWrapperDescr" as d {
        name = "__sub__"
    }
    object "intsubMH : MethodHandle" as intsubMH {
        target = PyLong.~__sub__
    }
    object "subSlot : SlotDef" as subSlot {
        name = "__sub__"
    }

    d -right-> subSlot : base
    d -left-> int : objclass
    d --> intsubMH : wrapped
    self --> int : type
    other --> int : type

We will show the classic call,
explicitly making the ``target`` descriptor, ``args`` tuple and
``kwargs=null`` visible.
The action during ``int.__sub__(42, 10)`` runs like this:

..  uml::
    :caption: Method Call in ``int.__sub__(42, 10)``

    participant prog
    participant "int : PyType" as int
    participant "d : PyWrapperDescr" as d
    participant "intsubMH\n: MethodHandle" as intsubMH

    prog -> int ++ : ~__getattribute~__("~__sub__")
        int -> int : d = lookup("~__sub__")
        int -> d ++ : ~__get__(null, int)
            return d
        return d

    prog -> d ++ : ~__call__((42, 10), null)
        d -> d++ : callWrapped(42, (10,), null)
            d -> intsubMH ++ : invokeExact(42, 10)
                intsubMH -> PyLong ++ : ~__sub__(42, 10)
                    return 32
                return 32
            return 32
        return 32

Note that although we show ``d`` simply as a ``PyWrapperDescr``,
it is actually an instance of a specialised subclass,
created by the ``SlotDef``.


Calling a Special Method as a Bound Method
==========================================

As with every other attribute access mediated by a descriptor,
a reference to the method via an object invokes ``__get__``.
This returns a callable object
binding the method definition and the target object.
Thus in ``(42).__sub__(10)``,
``__get__`` returns the object ``(42).__sub__``,
and then that is called with the argument ``10``.

We represent this binding object by an instance of the ``PyMethodWrapper``,
that references the target instance ``self``, and the descriptor.

..  uml::
    :caption: Objects in ``int.__sub__(42, 10)``

    object "int : PyType" as int
    object "42 : PyLong" as self
    object "10 : PyLong" as other
    object "d : PyWrapperDescr.Binop" as d {
        name = "__sub__"
    }
    object "intsubMH : MethodHandle" as intsubMH {
        target = PyLong.~__sub__
    }
    object "subSlot : SlotDef" as subSlot
    object "m : PyMethodWrapper" as m

    d -right-> subSlot : base
    d -left-> int : objclass
    d --> intsubMH : wrapped
    self --> int : type
    other --> int : type
    m --> d : descr
    m --> self : self

The action proceeds as follows:

..  uml::
    :caption: Method Binding in ``(42).__sub__(10)``

    participant prog
    participant "42 : PyLong" as left
    participant "int : PyType" as int
    participant "m : PyMethodWrapper" as m
    participant "d : PyWrapperDescr.Binop" as d
    participant "intsubMH\n: MethodHandle" as intsubMH

    prog -> left ++ : ~__getattribute~__("~__sub__")
        left -> int ++ : lookup("~__sub__")
            return d
        left -> d ++ : ~__get__(42)
            d -> m ** : new(d, 42)
            return m
        return m

    prog -> m ++ : ~__call__((10,), null)
        m -> d++ : callWrapped(42, (10,), null)
            d -> intsubMH ++ : invokeExact(42, 10)
                intsubMH -> PyLong ++ : ~__sub__(42, 10)
                    return 32
                return 32
            return 32
        return 32


.. _PyClassMethodDescr:

Built-in Class Methods (``PyClassMethodDescr``)
***********************************************

During type creation,
a ``PyClassMethodDescr`` that appears in the dictionary of the ``PyType``,
is created from a ``MethodDef`` specified by the class.
A ``MethodDef`` signals that a ``PyClassMethodDescr`` should be created,
rather than another type of method descriptor,
by its particular type.
(In CPython it uses the ``METH_CLASS`` flag,
but in the diagram we propose using a sub-class of ``MethodDef``.)

``PyClassMethodDescr`` should not be confused with
the decorator described in :ref:`classmethod-decorator`.

This is another case where we should consider using a ``MethodHandle``
as the basis of a Java implementation,
looking forward to possible use in a ``CallSite``.

..  uml::
    :caption: Class Method Descriptor

    abstract class Descriptor {
        name
        qualname
        {abstract} {method} __get__()
    }
    Descriptor -left-> PyType : objclass

    Descriptor <|-- PyMethodDescr
    class PyMethodDescr {
        {method} __get__()
        {method} __call__()
    }

    PyMethodDescr <|-- PyClassMethodDescr
    class PyClassMethodDescr {
        {method} __get__()
        {method} __call__()
    }

    class MethodDef {
        name
        meth : MethodHandle
        flags : EnumSet<MethodDef.Flag>
        doc
    }

    class MethodDef.Class {
    }

    class PyJavaFunction {
        module : PyUnicode
        tpCall : MethodHandle
        {method} __call__(args, kwargs)
        {method} __vectorcall__(args, start, nargs, kwnames)
    }

    interface PyObject {
        getType()
    }

    MethodDef <|.. MethodDef.Class

    MethodDef.Class ..> PyClassMethodDescr : <<specifies>>

    PyMethodDescr -right-> MethodDef : method
    PyJavaFunction --up--> MethodDef : methodDef

    PyJavaFunction <|-left- PyJavaMethod
    PyClassMethodDescr ..> PyJavaMethod : <<creates>>

    PyJavaMethod --> PyObject : self
    note on link : In this case self\nis a PyType


In the source code,
we can use annotation again to identify a Java method as a class method
in the implementation of an exposed type.
Each ``MethodDef.Class`` thus created should lead to a ``PyClassMethodDescr``
in the dictionary of the type.

Unless ``__getattribute__`` has been customised,
attribute access responds identically through types and their instances.

A reference to the method via an object invokes ``__get__``, as before.
However, the logic differs slightly from ``PyMethodDescr.__get__``,
in that when calling the method in the associated ``MethodDef``,
the ``type`` argument is made the target, not the ``obj`` argument.

This is different from ``PyMethodDescr`` where ``d.__get__(null, type)``
returns ``d``
and only ``d.__get__(obj, type)`` produces a bound method.
In ``PyClassMethodDescr`` both return a ``PyJavaMethod``,
in which the target ``self`` is the type.
(If the ``obj`` argument is given but not the ``type`` argument,
then ``type(obj)`` is used.)

The target is not necessarily the class that defined the descriptor,
but must be a Python sub-class of it.

In ``int.from_bytes(b'abcde', 'little')``,
``__get__`` returns the object ``int.from_bytes``,
which is a method bound to the type ``int``,
and then that is called with the arguments ``(b'abcde', 'little')``:

..  code-block:: python

    >>> (m := int.from_bytes)
    <built-in method from_bytes of type object at 0x00007FFA58368D10>
    >>> m.__self__
    <class 'int'>
    >>> m(b'abcde', 'little')
    435475931745

To reach the raw descriptor,
we must access it directly from the dictionary of the type:

..  code-block:: python

    >>> type(d := int.__dict__['from_bytes'])
    <class 'classmethod_descriptor'>
    >>> d.__get__(42)
    <built-in method from_bytes of type object at 0x00007FFA58368D10>
    >>> d.__get__(None, int)(b'abcde', 'little')
    435475931745
    >>> d(int, b'abcde', 'little')
    435475931745

A direct call to the descriptor requires
that the type be supplied in the call.
No binding ``PyJavaMethod`` is produced in this case.


Calling a Class Method normally in Python
=========================================

We shall examine the binding behaviour (``__get__``)
of a class method descriptor with sequence diagrams.
As already noted, it does not much matter whether the target is the type
or an instance of the type, so we choose the former.

In the simple call ``int.from_bytes(b'abcde', 'little')``,
the first step is the attribute access ``int.from_bytes``.
Under the circumstances depicted, this is handled by
the ``__getattribute__`` slot in the ``int`` object (a type).
That is a ``MethodHandle`` to ``PyType.__getattribute__``.

As we saw with plain ``PyMethodDescr``,
the equivalent in types,
``PyType.__getattribute__``
looks in the dictionary of the target object itself (the type ``int``).
It finds a descriptor (a ``PyClassMethodDescr``),
on which to call ``__get__(None, int)``.
This has to return a ``PyJavaMethod``,
in which the ``self`` field is assigned the type ``int``.

..  uml::
    :caption: Calling a Class Method ``int.from_bytes(b'abcde', 'little')``

    participant prog
    participant "int : PyType" as int
    participant "d : PyClassMethodDescr" as d
    participant "m : PyJavaMethod" as m

    prog -> int ++ : ~__getattribute__("from_bytes")
        int -> int : d = lookup("from_bytes")
        int -> d ++ : ~__get__(null, int)
            d -> m ** : new(from_bytes, int)
            return m
        return m

    prog -> m ++ : ~__call__(b'abcde', 'little')
        m -> int ++ : from_bytes(b'abcde', 'little')
            return 435475931745
        return 435475931745


We can see that calling the ``PyJavaMethod``
leads effectively to:

..  code-block:: python

    int.__dict__['from_bytes'].__get__(None, int)(b'abcde', 'little')

The optimisation in CPython
that avoids creation of an ephemeral ``PyJavaMethod``,
and which we referred to as we discussed ``PyMethodDescr``,
operates also for class method calls.
We'll examine the sequence that produces next.


Calling a Class Method Descriptor in Python
===========================================

Suppose a method call is made explicitly through a ``PyClassMethodDescr``,
for example ``int.__dict__['from_bytes'](b'abcde', 'little')``.
This is effectively what happens when
a class method call is optimised by CPython.
(It is difficult to imagine encountering this in user code.)

..  uml::
    :caption: Calling a Class Method Descriptor ``int.__dict__['from_bytes'](int, b'abcde', 'little')``

    participant prog
    participant "int : PyType" as int
    participant "d : PyClassMethodDescr" as d

    prog -> int ++ : ~__dict__['from_bytes']
            return d
        return d

    prog -> d ++ : ~__call__(int, b'abcde', 'little')
        d -> int ++ : from_bytes(b'abcde', 'little')
            return 435475931745
        return 435475931745

We can see how the direct call on the descriptor
still lands in the implementation object.


.. _PyStaticMethod:


Built-in Static Methods (``PyStaticMethod``)
********************************************

A ``PyStaticMethod`` is a descriptor because it defines ``__get__``.
It does not, however, descend in Java from ``Descriptor``.
(In CPython the C struct ``staticmethod``
does not have the C struct ``PyDescrObject`` as a preamble.)

An instance of ``PyStaticMethod`` is a wrapper
that may be applied to any object,
including a built-in function or a function defined in Python,
although this is likely to be meaningful only if the object is callable.
The wrapped object appears as the ``__func__`` member
(``callable``, internally).

..  uml::
    :caption: Static Method Descriptor

    interface PyObject {
        getType()
    }

    class PyStaticMethod {
        callable
        dict
        {method} __get__()
    }

    class PyJavaFunction {
        module : PyUnicode
        tpCall : MethodHandle
        {method} __call__(args, kwargs)
        {method} __vectorcall__(args, start, nargs, kwnames)
    }

    class MethodDef {
        name
        meth : MethodHandle
        flags : EnumSet<MethodDef.Flag>
        doc
    }

    class MethodDef.Static {
    }

    PyStaticMethod -right-> PyObject : callable
    PyObject <|.. PyJavaFunction

    PyJavaFunction --> MethodDef : methodDef
    MethodDef <|-left- MethodDef.Static
    PyStaticMethod ...> MethodDef.Static : <<uses>>

    'MethodDef.Static ...> PyJavaFunction : <<specifies>>


When a ``PyStaticMethod`` is derived from a ``MethodDef``
during the creation of a type,
a ``PyJavaFunction`` is first created from the ``MethodDef``.
Then the ``PyStaticMethod`` is created to wrap it, and
is entered in the dictionary of the type.
A ``MethodDef`` signals that a ``PyStaticMethod`` should be created,
rather than another type of method descriptor,
by its specific type.
(In CPython it uses the ``METH_STATIC`` flag,
but in the diagram we propose using a sub-class of ``MethodDef``.)

The constructor of a Python ``staticmethod``
(the ``__init__`` in fact)
also implements the decorator ``@staticmethod``
seen in Python class definitions.
In that context, it is once more the constructed ``PyStaticMethod``
that is inserted into the dictionary of the type,
but the callable (``__func__``) is the function decorated.

A ``PyStaticMethod`` is not itself callable,
but ``PyStaticMethod.__get__`` returns the associated callable inside it.
(At least one argument is required to ``__get__``, but it will be ignored:
there is no real binding to do.)


