..  plain-java-object-2/object-and-pytype.rst

.. _Instance-models-object-type:

Instance Models of ``object`` and ``type``
******************************************

We have laid out the basic patterns in the previous section,
but only some of this territory was explored in ``rt3`` work.
In ``rt4`` we take the opportunity to adjust even the tested ideas a little.

We shall discover better what we need by drawing instance diagrams
that represent the structures that arise from various uses.

We begin with those we drew for ``rt3``
in :doc:`../plain-java-object/operations-builtin`
(after adjustments)
and go on to more complex cases that support our current aims.
We do not labour the implementations this time:
they are almost the same as in ``rt3``.


.. _Representation-builtin-list:

Representing ``list``
=====================

The type ``list`` is defined by the class ``PyList``
and is represented by instances of that class.
In this case, the ``PyType`` *is* the ``Representation`` object,
so that dereferencing to the associated ``PyType``,
yields the same object (by a safe cast).

Suppose we write:

..  code-block:: python

    x = [1,2,3]

Then the structure we hope for is one that
allows us to navigate from a reference to ``x``
to any named method (``__len__`` for example):

..  uml::
    :caption: Instance model of a ``list`` and its type

    object "x : PyList" as x {
        value = [1,2,3]
    }
    object "PyList : Class" as PyList.class
    object "list : SimpleType" as listType

    x ..> PyList.class
    PyList.class --> listType : registry
    listType --> listType : type

    object " : Map" as dict
    listType --> dict : " ~__dict__"

    object " : PyWrapperDescr" as len {
        name = "__len__"
    }
    object " : MethodHandle" as xlen {
        PyList.__len__(PyList)
    }
    dict --> len
    len --> xlen


    object " : PyWrapperDescr" as getitem {
        name = "__getitem__"
    }
    object " : MethodHandle" as xgetitem {
        PyList.__getitem__(PyList,Object)
    }
    dict --> getitem
    getitem --> xgetitem

    object " : PyWrapperDescr" as add {
        name = "__add__"
    }
    object " : MethodHandle" as xadd {
        PyList.__add__(PyList,Object)
    }
    dict --> add
    add --> xadd


Simple Sub-classes of  ``list``
-------------------------------

How do we represent an instance of a Python subclass of ``list``?
Straightforward subclasses are possible like this:

..  code-block:: python

    class L(list):
        def __init__(self, *p): super().__init__(*p); self.a = 42
        def __repr__(self): return f"{super().__repr__()} {self.__dict__}"
    class L1(list): pass
    class L2(list): pass
    class L3(L): pass
    class L4(L3, L2, list): pass

    x = L()
    x1 = L1(); x1.a = 43
    x2 = L2(); x2.b = 44
    x3 = L3(); x3.a = 45; x3.b = 46
    x4 = L4(); x4.b = 47; x4.c = 48


It is notable that,
with certain restrictions,
instances of distinct Python classes allow assignment to ``__class__``,
in a way that Java objects do not with their class:

>>> x2.__class__ = L
>>> x2
[] {'b': 44}
>>> x3.__class__ = L1
>>> x4
[] {'a': 42, 'b': 47, 'c': 48}
>>> x1.__class__ = list
Traceback (most recent call last):
  File "<pyshell#91>", line 1, in <module>
    x1.__class__ = list
TypeError: __class__ assignment only supported for mutable types or ModuleType subclasses

The error is a clue to the limits of class assignment.
When CPython decides what assignments to allow,
it looks at certain traits of the current and proposed object types.
Sub-classes of common ancestry generally meet these criteria.
It then looks at the memory layout of the object,
as described by the current and proposed types,
and allows the swap if they are sufficiently alike.
All the types ``L``, ``L1``, ``L2``, ``L3``, ``L4``
have the same layout as ``list``,
except for the addition of an instance dictionary ``__dict__``.
The attributes ``a`` and ``b`` are entries in that dictionary,
and do not affect the layout.

The ability to assign a class to instances of another class is
reflexive, symmetric and transitive, so it is an equivalence relation.
The equivalence classes in the example, when we enumerate them by trial, are:
``[('list',), ('L', 'L1', 'L2', 'L3', 'L4')]``.

We don't have this freedom once we have created a Java object:
the Java class is fixed.
Types that allow class assignment must therefore be represented by
a single class in Java.

In the exampes presented, all the subclasses of ``list``
are interchangeable in Python
(even the subclass of a subclass, but not ``list`` itself).
They all therefore must share the same representation in Java,
a Java subclass of ``PyList``,
with ``__dict__`` and an explicit ``type``.

In this simple case of a predictable need,
the class we need may be created in advance,
and used for all such Python subclasses of ``list``.
We shall denote this prepared class by ``PyList.Derived``
on the assumption it can be a nested class of ``PyList``.
Later we shall find this idea does not stretch to cover all our needs,
but we work with it for now.

..  uml::
    :caption: Instance model for simple subclasses of ``list``

    ' object "PyList : Class" as PyList.class
    ' PyList.class --> listType : registry
    ' listType --> listType : type

    object "x : PyList.Derived" as x {
        __dict__ = {'a':42}
    }
    object "x1 : PyList.Derived" as x1 {
        __dict__ = {'a':43}
    }
    object "x2 : PyList.Derived" as x2 {
        __dict__ = {'a':44}
    }
    object "x3 : PyList.Derived" as x3 {
        __dict__ = {'a':45, 'b':46}
    }

    object "PyList.Derived : Class" as PyList.Derived.class

    object "list : SimpleType" as listType
    object "L : ReplaceableType" as LType
    object "L1 : ReplaceableType" as L1Type
    object "L2 : ReplaceableType" as L2Type
    object "L3 : ReplaceableType" as L3Type

    LType --> listType : base
    L1Type --> listType : base
    L2Type --> listType : base
    L3Type -> LType : base

    object " : SharedRepresentation" as PyList.Derived.rep
    x ..> PyList.Derived.class
    x1 ..> PyList.Derived.class
    x2 ..> PyList.Derived.class
    x3 ...> PyList.Derived.class

    PyList.Derived.class --> PyList.Derived.rep : registry

    x --> LType : type
    x1 --> L1Type : type
    x2 --> L2Type : type
    x3 --> L3Type : type

Any of the classes here may appear concurrently
as bases in multiple inheritance,
including ``list``.
The ``PyList.Derived`` design also supports this.
The MRO of ``L4`` is ``(L4, L3, L, L2, list, object)``.

..  uml::
    :caption: Multiple inheritance for simple subclasses of ``list``

    ' object "PyList : Class" as PyList.class
    ' PyList.class --> listType : registry
    ' listType --> listType : type

    object "x4 : PyList.Derived" as x4 {
        __dict__ = {'a': 42, 'b': 47, 'c': 48}
    }

    object "PyList.Derived : Class" as PyList.Derived.class

    object "list : SimpleType" as listType
    object "L : ReplaceableType" as LType
    object "L2 : ReplaceableType" as L2Type
    object "L3 : ReplaceableType" as L3Type
    object "L4 : ReplaceableType" as L4Type

    L3Type -right-> LType : base
    L2Type -right-> listType : base
    L4Type --> L3Type : " ~__mro__[1]"
    L4Type --> LType : " ~__mro__[2]"
    L4Type --> L2Type : " ~__mro__[3]"
    L4Type --> listType : " ~__mro__[4]"
    'LType ----> listType : base

    object " : SharedRepresentation" as PyList.Derived.rep
    x4 .right.> PyList.Derived.class

    PyList.Derived.class -right-> PyList.Derived.rep : registry

    x4 --> L4Type : type

When we need the type of an object,
its Java class leads us to its ``Representation``,
but for derived classes the representation is a ``SharedRepresentation``
that consults the object itself.
The ``SharedRepresentation`` is the same for each object in the example,
but the Python type will be distinct (and in principle assignable),
since it references a ``ReplaceableType``
of the common ``SharedRepresentation``.

We shall see shortly that this does not work in general,
and later that we must be able to create representation classes in Java
as we encounter new class definitions in Python.
We must then somehow retrieve representations we already made,
where their "layout" is the same as CPython would perceive it,
if we are to implement Python's class assignment fully.


.. _Representation-builtin-list-slots:

Sub-classes of  ``list`` using ``__slots__``
--------------------------------------------

There is another way to define subclasses, using ``__slots__``.
When a special tuple of names ``__slots__`` is defined at class level,
Python allocates memory locations in the instances and
there is no instance ``__dict__``.
The motive is often to save space.

We have to set up a fairly complicated example to explore this.

..  code-block:: python

    class LS(list):
        __slots__ = ('a',)
        def __init__(self, *p): super().__init__(*p); self.a = 42
        def __repr__(self): return f"{super().__repr__()} {self.a=}"
    class LS1(list): __slots__ = ('a',)
    class LS2(list): __slots__ = ('b',)
    class LS3(LS):
        __slots__ = ('b',)
        def __init__(self, *p): super().__init__(*p); self.b = 46
        def __repr__(self): return f"{super().__repr__()} {self.b=}"
    class LS4(list): __slots__ = ()
    class LS5(LS):
        __slots__ = ()
        def __init__(self, *p): super().__init__(*p); self.a = 47;
    class LS6(LS):
        def __repr__(self): return f"{super().__repr__()} {self.__dict__}"
    class LS7(LS6, LS3, list):
        __slots__ = ('c',)
        def __init__(self, *p): super().__init__(*p); self.c = 49
        def __repr__(self): return f"{super().__repr__()} {self.c=}"

    xs = LS()
    xs1 = LS1(); xs1.a = 43
    xs2 = LS2(); xs2.b = 44
    xs3 = LS3()
    xs4 = LS4()
    xs5 = LS5()
    xs6 = LS6(); xs6.b = 48
    xs7 = LS7(); xs7.n = 9

The possibilities for assignment to ``__class__``,
and for multiple inheritance,
are significantly narrowed by the use of ``__slots__``.

The equivalence classes, when we compute them, are:
``[('list',), ('LS', 'LS1', 'LS5'), ('LS2',), ('LS3',), ('LS4',), ('LS6',), ('LS7',)]``

..  code-block:: python

    >>> xs1.__class__ = LS
    >>> xs2.__class__ = LS
    Traceback (most recent call last):
      File "<pyshell#94>", line 1, in <module>
        xs2.__class__ = LS
    TypeError: __class__ assignment: 'LS' object layout differs from 'LS2'
    >>> xs4.__class__ = list
    Traceback (most recent call last):
      File "<pyshell#136>", line 1, in <module>
        xs4.__class__ = list
    TypeError: __class__ assignment only supported for mutable types or ModuleType subclasses

``xs1`` is assignable with ``LS``
because ``LS1`` has an identical ``__slots__``,
even though it has quite different methods.
``LS2`` differs in layout from ``LS`` only in the name it chooses
for its member,
but it is still incompatible.
``LS5`` is compatible because it subclasses ``LS``
and adds an empty ``__slots__``,
but the same trick does not make ``LS4`` compatible with ``list``.
``LS6`` does not mention ``__slots__``, so it gets a ``__dict__``,
making it incompatible with parent ``LS``.

A possible approach is to give ``PyList.Derived`` an array member
that holds the values of the slotted variables.
We also need a mapping from slot attribute name to location in the array.
For the purposes of analysis,
we depict this as an array of names ``slotNames`` in the type,
built from the class contributions accumulated among the (reverse) MRO.
Operationally the job can be done by member descriptors in the
dictionary of the type that named the slot,
and found along the MRO.
In the interests of readability, we split the instance diagram into
parts for direct and indirect subclassses of ``list``,
and multiple inheritance:

..  uml::
    :caption: Direct ``__slots__`` subclasses of ``list``

    ' object "PyList : Class" as PyList.class
    ' PyList.class --> listType : registry
    ' listType --> listType : type

    object "xs : PyList.Derived" as xs {
        slots = [42]
        __dict__ = null
    }
    object "xs1 : PyList.Derived" as xs1 {
        slots = [43]
        __dict__ = null
    }
    object "xs2 : PyList.Derived" as xs2 {
        slots = [44]
        __dict__ = null
    }
    object "xs4 : PyList.Derived" as xs4 {
        slots = []
        __dict__ = null
    }

    object "PyList.Derived : Class" as PyList.Derived.class

    object "list : SimpleType" as listType

    object "LS : ReplaceableType" as LSType {
        slotNames = ["a"]
    }
    object "LS1 : ReplaceableType" as LS1Type {
        slotNames = ["a"]
    }
    object "LS2 : ReplaceableType" as LS2Type {
        slotNames = ["b"]
    }
    object "LS4 : ReplaceableType" as LS4Type {
        slotNames = []
    }

    LSType --> listType : base
    LS1Type --> listType : base
    LS2Type --> listType : base
    LS4Type --> listType : base

    object " : SharedRepresentation" as PyList.Derived.rep
    xs ..> PyList.Derived.class
    xs1 ..> PyList.Derived.class
    xs2 ..> PyList.Derived.class
    xs4 ..> PyList.Derived.class

    PyList.Derived.class --> PyList.Derived.rep : registry

    xs --> LSType : type
    xs1 --> LS1Type : type
    xs2 --> LS2Type : type
    xs4 --> LS4Type : type


..  uml::
    :caption: Indirect ``__slots__`` subclasses of ``list``

    ' object "PyList : Class" as PyList.class
    ' PyList.class --> listType : registry
    ' listType --> listType

    object "xs3 : PyList.Derived" as xs3 {
        slots = [42,46]
        __dict__ = null
    }
    object "xs5 : PyList.Derived" as xs5 {
        slots = [47]
        __dict__ = null
    }
    object "xs6 : PyList.Derived" as xs6 {
        slots = [42]
        __dict__ = {"b":48}
    }

    object "PyList.Derived : Class" as PyList.Derived.class

    object "list : SimpleType" as listType {
        slotNames = []
    }
    object "LS : ReplaceableType" as LSType {
        slotNames = ["a"]
    }
    object "LS3 : ReplaceableType" as LS3Type {
        slotNames = ["a","b"]
    }
    object "LS5 : ReplaceableType" as LS5Type {
        slotNames = ["a"]
    }
    object "LS6 : ReplaceableType" as LS6Type {
        slotNames = ["a"]
    }

    LSType --> listType : base
    LS3Type --> LSType : base
    LS5Type --> LSType : base
    LS6Type --> LSType : base

    object " : SharedRepresentation" as PyList.Derived.rep
    xs3 ..> PyList.Derived.class
    xs5 ..> PyList.Derived.class
    xs6 ..> PyList.Derived.class

    PyList.Derived.class --> PyList.Derived.rep : registry

    xs3 --> LS3Type : type
    xs5 --> LS5Type : type
    xs6 --> LS6Type : type

``__slots__`` restricts the classes that may appear concurrently
as bases in multiple inheritance.
The fact of using the ``PyList.Derived`` as a common representation
allows for arbitrary class assignment,
but we must exclude cases that change the ``slotNames``
or the use of ``__dict__``.
We might think we can be less restrictive than CPython,
but a feasible "slot layout" is equivalent (we think)
to the constraint CPython applies.
The MRO of ``LS7`` is ``(LS7, LS6, LS3, LS, list, object)``.

..  uml::
    :caption: Multiple inheritance of ``__slots__`` subclasses of ``list``

    ' object "PyList : Class" as PyList.class
    ' PyList.class --> listType : registry
    ' listType --> listType

    object "xs7 : PyList.Derived" as xs7 {
        slots = [42,46,49]
        __dict__ = {"n":9}
    }

    object "PyList.Derived : Class" as PyList.Derived.class

    object "list :SimpleType" as listType {
        slotNames = []
    }
    object "LS : ReplaceableType" as LSType {
        slotNames = ["a"]
    }
    object "LS3 : ReplaceableType" as LS3Type {
        slotNames = ["a","b"]
    }
    object "LS6 : ReplaceableType" as LS6Type {
        slotNames = ["a"]
    }
    object "LS7 : ReplaceableType" as LS7Type {
        slotNames = ["a","b","c"]
    }

    LSType -right-> listType : base
    LS3Type --right-> LSType : base
    LS6Type --> LSType : base
    LS7Type -left-> LS6Type : " ~__mro__[1]"
    LS7Type --> LS3Type : " ~__mro__[2]"
    LS7Type --> LSType : " ~__mro__[3]"
    LS7Type -right-> listType : " ~__mro__[4]"

    object " : SharedRepresentation" as PyList.Derived.rep
    xs7 .right.> PyList.Derived.class

    PyList.Derived.class --> PyList.Derived.rep : registry

    xs7 --> LS7Type : type


.. _Representation-builtin-object:

``Object``, ``object`` and Python ``class``
===========================================

Suppose we define two classes in Python that have base ``object``,
in the simplest way possible.

..  code-block:: python

    class A: pass
    class A2(A): pass

    a = A(); a.x = 42
    a2 = A2(); a2.y = 43

We can represent these objects and types as follows:

..  uml::
    :caption: ``object`` and subclasses

    object "Object : Class" as Object.class

    object "o : Object" as o
    o .right.> Object.class

    object "object : SimpleType" as objectType
    Object.class -right-> objectType : registry

    object "a : ObjectBase" as a {
        type = A
        __dict__ = {'x':42}
    }
    object "a2 : ObjectBase" as a2 {
        type = A2
        __dict__ = {'y':43}
    }

    object "ObjectBase : Class" as ObjectBase.class

    object "A : ReplaceableType" as AType
    AType -up-> objectType : base
    object "A2 : ReplaceableType" as A2Type
    A2Type -up-> AType : base

    object " : SharedRepresentation" as ObjectBase.rep
    a .right.> ObjectBase.class
    a2 .up.> ObjectBase.class

    ObjectBase.class -right-> ObjectBase.rep : registry
    AType -left-> ObjectBase.rep
    A2Type --left-> ObjectBase.rep

    'a --> AType : type
    'a2 --> A2Type : type

Notice that the Java class of ``a`` and ``a2`` is the same ``ObjectBase``,
that is, they have the same representation and therefore
the same ``Representation`` object,
an instance of ``SharedRepresentation``.
This is another prepared representation like ``PyList.Derived`` above.
There is a PyObjectBase in CPython with similar function.
Nevertheless,
we remind the reader that this approach proves insufficient later.

Imagine we pick up either ``a`` or ``a2`` and ask its Python type:
the class leads us to the same representation,
from which there is no navigation to ``A`` or ``A2``.
However, ``SharedRepresentation.pythonType(Object o)``
consults the argument for its actual type.

The Java class of ``o`` is simply ``Object``,
which is the (single) representation of ``object``.
We might think that object should therefore be an ``AdoptiveType``,
since it is a pre-existing (not crafted) implementation,
and it is the base of all classes in Java (not ``final``)
we are able to nominate it the primary of a ``SimpleType``.


.. _Representation-builtin-type:

Type Objects for ``type``
=========================

In the preceding diagrams,
we depicted objects and the web of connections
we use to navigate to their Python type.
But the type objects we reached are themselves Python objects,
and they have a type object too.

It is well known that the type of ``type`` is ``type`` itself.
We have already come across three variant implementations of ``type``
in the examples.
Suppose we start with one instance of each implementation.
We should be able to navigate from each of them to the same object,
because each of them represents an instance of the ``type`` type.

..  uml::
    :caption: Type Objects for ``type``

    object "list : SimpleType" as listType
    object "A : ReplaceableType" as AType
    object "float : AdoptiveType" as floatType

    object "PyType : Class" as PyType.class
    object "SimpleType : Class" as SimpleType.class
    object "ReplaceableType : Class" as ReplaceableType.class
    object "AdoptiveType : Class" as AdoptiveType.class

    listType ..> SimpleType.class
    AType ..> ReplaceableType.class
    floatType ..> AdoptiveType.class

    object "type : SimpleType" as type {
        name = "type"
    }
    type --> type : type

    PyType.class --> type : registry
    SimpleType.class -down-> type
    ReplaceableType.class -down-> type
    AdoptiveType.class -down-> type

    type .up.> SimpleType.class


We choose to implement ``type`` as a ``SimpleType``.
Although ``type`` has multiple implementations in Java
(``SimpleType``, ``ReplaceableType`` and ``AdoptiveType``),
we need not treat them as adopted (and so use ``AdoptiveType``),
since they all extend ``PyType``.

We have not yet considered metatypes (subtypes of ``type``).
Let's take the example from the Python documentation:

..  code-block:: python

    class Meta(type): pass
    class MyClass(metaclass=Meta): pass
    class MySubclass(MyClass): pass

    x = MyClass()
    y = MySubclass()

We understand that when we create a class, we create an instance of ``type``.
In simple cases, the type of a class is exactly ``type``.

..  code-block:: python

    >>> class C: pass
    ...
    >>> type(C)
    <class 'type'>
    >>> type(C())
    <class '__main__.C'>

Looked at the other way,
``type`` and ``C`` are both instances of ``type``,
but ``C(...)`` produces only new ``C`` objects,
while ``type(...)`` is a constructor of new types.
This is because ``type.__call__`` defers to ``__new__``
in the particular ``type`` object itself,
which is ``type.__new__`` in ``type`` and ``object.__new__`` in ``C``.

It is also worth reflecting that we get exactly the same result
if we de-sugar class creation to a constructor call:

..  code-block:: python

    >>> C = type("C", (), {})
    >>> type(C)
    <class 'type'>
    >>> type(C())
    <class '__main__.C'>

An object that produces new types, and is *not* ``type`` itself,
is disorienting at first.
To help with the orientation,
let us de-sugar class creation involving a metaclass:

..  code-block:: python

    >>> D = Meta("D", (), {})
    >>> type(D)
    <class '__main__.Meta'>
    >>> isinstance(D, type)
    True
    >>> type(D())
    <class '__main__.D'>

Metatypes like ``Meta`` are subclasses of ``type``
in the way that ``L``, ``L1``, ``L2`` are subclasses ``list``
(to borrow from an earlier example).
It follows that an instance of the metatype,
that is, a type defined by calling the metatype,
should be represented in Java by a sub-type of ``PyType``,
just as instances of ``L`` etc.
are represented by a subtype of ``PyList``.

Secondly, each metatype is itself an instance of ``type``,
since it may be called to make objects.
Its class is directly ``type``:

..  code-block:: python

    >>> Meta.__class__
    <class 'type'>

Each metatype itself should therefore be realised by
a Java subclass of ``PyType``, specifically ``ReplaceableType``,
for which the shared representation is always the same.

The behaviour of metatypes with respect to class assignment
is just the same as any other family of subclasses:
all metatypes have the same representation.
Assignment of a replacement metatype is allowed
to the ``__class__`` member of any instance of a metatype
(if simply derived from ``type`` without ``__slots__``).
Any of the (simply derived) classes created by metatypes
may be given a new metatype,
but ``type`` itself cannot be assigned to their ``__class__``.
We can illustrate this by extending the example with another metatype:

..  code-block:: python

    class Other(type): pass
    class MyOtherClass(list, metaclass=Other): pass

    z = MyOtherClass()
    assert type(MyOtherClass) == Other

In the above, ``MyOtherClass.__class__ = Meta`` would be possible.
The assignability of ``__class__`` in instances
of the classes produced by metatypes, depends on their own bases,
not the properties of the metatypes that made them,
so ``z.__class__ = MyClass`` would fail
because of the involvement of ``list``,
not for any difference in metatype.

..  uml::
    :caption: Type Objects for Metatypes (Subclasses of ``type``)

    object "x : PyObject" as x
    x --> MyClass : type
    object "y : PyObject" as y
    y --> MySubclass : type

    'object "PyType : Class" as PyType.class
    object "PyType.Derived : Class" as PyType.Derived.class
    'object "SimpleType : Class" as SimpleType.class
    'object "ReplaceableType : Class" as ReplaceableType.class

    object "metas : SharedRepresentation" as metas.rep
    'object "objects : SharedRepresentation" as objects.rep

    object "type : SimpleType" as type {
        name = "type"
    }
    'type ..> SimpleType.class
    type --> type : type

    object "Meta : ReplaceableType" as Meta {
        name = "Meta"
    }
    Meta --> type : type
    Meta --> type : base
    Meta --> metas.rep

    object "MyClass : PyType.Derived" as MyClass {
        name = "MyClass"
    }
    MyClass ..>  PyType.Derived.class
    MyClass --> Meta : type

    object "MySubclass : PyType.Derived" as MySubclass {
        name = "MySubclass"
    }
    MySubclass ..> PyType.Derived.class
    MySubclass --> Meta : type

    'PyType.class --> type : registry
    'SimpleType.class --> type : registry
    'ReplaceableType.class --> type : registry
    PyType.Derived.class --> metas.rep : registry


    object "z : PyObject" as z
    z --> MyOtherClass : type

    object "Other : ReplaceableType" as Other {
        name = "Other"
    }
    Other --> type
    Other --> type
    Other --> metas.rep

    object "MyOtherClass : PyType.Derived" as MyOtherClass {
        name = "MyOtherClass"
    }
    MyOtherClass ..>  PyType.Derived.class
    MyOtherClass --> Other : type

.. _Representation-builtin-float:

Representing ``float``
======================

The type ``float`` is defined by the class ``PyFloat``,
but ``java.lang.Double`` is adopted as a representation
(and we might also allow ``java.lang.Float``).
We show here how the ``Representation`` helps us navigate to
the correct implementation of a method,
when representations have been adopted.

.. _Operations-builtin-float-neg-2:

A Unary Operation ``float.__neg__``
-----------------------------------

In :ref:`Representation-builtin-list`,
we saw how a ``SimpleType`` object,
which is incidentally also a ``Representation`` object,
allowed us to navigate to a ``MethodHandle`` on
the implementation of that type's special methods.
In the signature of those methods the ``self`` argument had type ``PyList``.
We will draw the comparable diagram for ``PyFloat``,
a type with adopted representations.

Suppose that in the course of executing a ``UNARY_NEGATIVE`` opcode,
the interpreter picks up an ``Object`` from the stack
and finds it to be a ``Double``.
How does it locate the particular implementation of ``__neg__``?

For ``float``, there will be these implementations:

..  code-block:: java

    PyFloatMethods {
        // ...
        static double __neg__(PyFloat self) { return -self.value; }
        static double __neg__(Double self) { return -self; }

Rather than a single handle,
the special method wrapper we enter into the dictionary of the type
will contain an array of handles.
To choose the correct one,
we need to know that ``PyFloat`` is representation 0
and ``Double`` is representation 1.

The structure we propose looks like this,
when realised for two floating-point values:

..  uml::
    :caption: Instance model of ``float`` and its ``__neg__`` method

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class

    object " : MethodHandle" as pyFloatNeg {
        target = PyFloatMethods.__neg__(PyFloat)
    }

    object "float : AdoptiveType" as floatType

    x ..> PyFloat.class
    PyFloat.class --> floatType : registry

    object "42.0 : Double" as y
    object "Double : Class" as Double.class
    object " : AdoptedRepresentation" as doubleRep {
        index = 1
    }
    object " : MethodHandle" as doubleNeg {
        target = PyFloatMethods.__neg__(Double)
    }

    y ..> Double.class
    Double.class --> doubleRep : registry
    doubleRep -left-> floatType : type

    object " : Map" as dict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> dict : dict
    dict --> neg
    neg --> pyFloatNeg : 0
    neg --> doubleNeg : 1

When the interpreter picks up the ``Double`` 42.0,
it traverses the ``Double`` class to the ``AdoptedRepresentation``.
We are effectively looking up the bound attribute ``(42.0).__neg__``,
and we can see that we must implement this so that
it first consults the dictionary of the type,
then uses the index it knows to select and invoke the correct handle,
which is at index 1.

If the orignal object had been a ``PyFloat``,
the representation found would be the type object itself and
the index would have been 0.

Note that the lookup of ``float.__neg__`` will find us the *descriptor*
containing a handle for every representation.
It is the binding operation that selects one
according to the implementation type of the target object.
If we came to this binding cold, as in ``getattr(42.0, "__neg__")``,
we would have to look up the representation of 42.0 to find the index.
Coming as we have from the representation object itself,
we should be able to avoid that repeat lookup.


A Subclass of ``float``
-----------------------

A Python subclass of ``float`` will always be implemented by
a Java subclass of ``PyFloat``, say ``PyFloat.Derived``,
that is mapped in the registry to a shared representation.
The specific type will be designated by a field on each instance.

Suppose that we have defined:

..  code-block:: python

    class MyFloat(float):
        def __repr__(self):
            return super().__repr__() + " inches"

Then the object structure behind an instance ``MyFloat(42)`` is:

..  uml::
    :caption: Instance model of a subclass of ``float``

    object "42.0 : PyFloat.Derived" as x
    object "PyFloat.Derived : Class" as PyFloat.Derived.class

    object " : MethodHandle" as pyFloatNeg {
        target = PyFloatMethods.__neg__(PyFloat)
    }

    object "float : AdoptiveType" as floatType
    object " : SharedRepresentation" as PyFloat.Derived.rep
    object "MyFloat : ReplaceableType" as myFloatType

    x ..> PyFloat.Derived.class
    PyFloat.Derived.class --> PyFloat.Derived.rep : registry

    object " : Map" as floatDict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> floatDict : dict
    floatDict --> neg
    neg --> pyFloatNeg : 0

    object " : Map" as myFloatDict
    object " : PyMethodDescr" as repr {
        objtype = MyFloat
        name = "__repr__"
    }

    x --> myFloatType : type
    myFloatType --> myFloatDict : dict
    myFloatType -up-> floatType : base
    myFloatDict --> repr

Now if ``x = MyFloat(42)``,
then to print out ``x`` we first traverse the Java class of ``x``,
which is ``PyFloat.Derived``,
to a ``SharedRepresentation`` that bounces us back to ``x``
to obtain the real type ``MyFloat``.
We shall then find ``__repr__``
in the dictionary of ``MyFloat`` and call that Python method.
To calculate ``-x``, we shall begin the same way,
then have to search up the MRO,
eventually finding implementation 0 of ``float.__neg__``.

Since the range and precision of ``Double``
are the same as those of ``PyFloat.value``,
we could manage without ``PyFloat`` entirely,
were it not that we need to define subclasses of ``float`` in Python.
Sub-classes in Python must be represented by subclasses in Java
and ``Double`` cannot be subclassed.


.. _Representation-with-cache:

Possibility of Caching on the ``Representation``
================================================

Model
-----

We know that in CPython,
special methods like ``__neg__`` map to pointers in a type object.
Suppose we want to do the same.
The corresponding idea is to give the ``Representation``,
and therefore every ``PyType``,
a ``MethodHandle`` for each special method.

Code for operation ``neg``,
in the Abstract API that supports the interpreter,
accepts and returns arguments of declared type ``Object``.
The direct handle for ``PyFloat.__neg__``, depending on the index,
has type ``(Double)Object`` or ``(PyFloat)Object``.
For a handle to be invoked exactly by the API method,
it must have type ``(Object)Object``,
and therefore we must wrap the direct handle with ``MethodHandle.asType``,
which is effectively a checked cast.

..  uml::
    :caption: Instance model with a short-cut modelled after CPython

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class

    object " : MethodHandle" as pyFloatNeg {
        target = PyFloatMethods.__neg__(PyFloat)
    }
    object " : MethodHandle" as pyFloatNegMH

    object "float : AdoptiveType" as floatType

    x ..> PyFloat.class
    PyFloat.class --> floatType : registry
    floatType --> pyFloatNegMH : op_neg
    pyFloatNegMH --> pyFloatNeg : target

    object "42.0 : Double" as y
    object "Double : Class" as Double.class
    object " : AdoptedRepresentation" as doubleRep {
        index = 1
    }
    object " : MethodHandle" as doubleNeg {
        target = PyFloatMethods.__neg__(Double)
    }
    object " : MethodHandle" as doubleNegMH

    y ..> Double.class
    Double.class --> doubleRep : registry
    doubleRep -left-> floatType : type
    doubleRep --> doubleNegMH : op_neg
    doubleNegMH --> doubleNeg : target

    object " : Map" as floatDict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> floatDict : dict
    floatDict --> neg
    neg --> pyFloatNeg : 0
    neg --> doubleNeg : 1


Notice that when we repeat this with a subclass,
it is the type object (not the shared representation)
that holds the specific method handle.
The ``SharedRepresentation``,
redirects to the type object designated by the specific instance,
before we access the short cut handle.
And this handle is on the ``__call__`` method of the descriptor,
with its ``self`` argument bound to the specific descriptor
from the dictionary of ``MyFloat``.
This ``__call__`` method creates a frame to run the Python method.

..  uml::
    :caption: Subclass instance model with a short-cut modelled after CPython

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class

    object " : MethodHandle" as pyFloatNeg {
        target = PyFloatMethods.__neg__(PyFloat)
    }
    object " : MethodHandle" as pyFloatNegMH

    object "float : AdoptiveType" as floatType

    x ..> PyFloat.class
    PyFloat.class --> floatType : registry
    floatType --> pyFloatNegMH : op_neg
    pyFloatNegMH --> pyFloatNeg : target

    object " : MethodHandle" as doubleNeg {
        target = PyFloatMethods.__neg__(Double)
    }

    object " : Map" as floatDict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> floatDict : dict
    floatDict --> neg
    neg --> pyFloatNeg : 0
    neg --> doubleNeg : 1


    object "42.0 : PyFloat.Derived" as z
    object "PyFloat.Derived : Class" as PyFloat.Derived.class

    object " : SharedRepresentation" as PyFloat.Derived.rep
    object "MyFloat : ReplaceableType" as myFloatType

    z ..> PyFloat.Derived.class
    PyFloat.Derived.class --> PyFloat.Derived.rep : registry


    object " : Map" as myFloatDict
    object " : PyMethodDescr" as myFloatRepr {
        objtype = MyFloat
        name = "__repr__"
    }
    object " : MethodHandle" as myFloatReprMH {
        target = PyMethodDescr.__call__(...)
    }
    myFloatReprMH --> myFloatRepr : self

    z --> myFloatType : type
    myFloatType --> myFloatDict : dict
    myFloatType -up-> floatType : base
    myFloatDict --> myFloatRepr
    myFloatType --> pyFloatNegMH : op_neg
    myFloatType --> myFloatReprMH : op_repr



Motivation for Caching
----------------------

The idea that type objects contain slots is so ingrained that
there is a visibly different descriptor type for these methods,
although there are very few places where
Python is sensitive to the difference between
``WrapperDescriptorType`` and ``MethodDescriptorType``, for example.

..  code-block:: python

    >>> float.__neg__
    <slot wrapper '__neg__' of 'float' objects>

Not every special method gets the special treatment, however.

..  code-block:: python

    >>> float.__reduce__
    <method '__reduce__' of 'object' objects>
    >>> float.__subclasshook__
    <built-in method __subclasshook__ of type object at 0x00007FF9D398BC50>

The motivation for slots in CPython is
to get quickly from the abstract API method,
``PyNumber_Negative`` say,
to the special method implementation specific to the type.
Done conventionally, this would be slow:
an attribute lookup along the MRO,
then argument checks, descriptor binding and finally the call itself.

In the Abstract API, the call is already known to match the signature,
and can be made safely via the pointer cached in the type object.
Only a call from Python, like ``x.__neg__()``,
takes the slow path via the descriptor.
This is of significant benefit when interpreting CPython byte code
and where the methods are mainly from built-in types.

In a subclass of ``float``, say where ``__neg__`` has been redefined,
the dictionary of the subtype contains
a descriptor for the method defined in Python,
which takes precedence over the wrapper descriptor in that of ``float``.
The type slot (ordinarily a copy of that in the parent class)
contains a redirect function.
Thus the interpreter invokes the handle from the type object,
but the function takes the slow path via the descriptor.
Only methods that have actually been overridden get this treatment:
a subclass of ``float`` that does not redefine ``__neg__``
still benefits from the shortcut.

This decision is not final with the construction of the type concerned,
since a method may be redefined dynamically.
Changes to types,
at least where they affect the methods that fill type slots,
must propagate down the inheritance hierarchy.
Therefore each type keeps track of its descendants to notify them of changes.
(The cascade cannot start with a built-in type as they are all immutable.)


Is Caching Beneficial for Jython?
---------------------------------

The short answer is that we are unable to decide just yet.
That is why in ``rt4``
we will avoid shaping the runtime around
the implementation of special methods.

In our highest performing code, we expect that
operations (like ``ast.USub``) will be compiled to mutable call sites.
On first encountering a ``Double`` argument,
the site will specialise itself with a ``MethodHandle`` on
``PyFloat.__neg__(Double)`` guarded by a test for ``Double``.
(If it later encounters an ``Integer`` it will add a clause for that too.)
The handle is found once and never changes
(``float`` is immutable, and ``int``)
so there is no benefit in having a quick way to look it up.

In a subclass  of ``float`` where a definition has been overidden,
we will end up on a slow path anyway,
because we are setting up a Python call frame.
(It is rare to replace a method implemented in Java with another.)
It may be a slow operation for the overridden method only,
since methods inherited from ``float``
still have their Java implementations.
Or it may be a more general slow-down:
once a mutable type is in the MRO,
we can no longer safely bind method handles into the call site,
without taking precautions against the redefinition
that can occur between calls to any method.

Another consideration is that
some code encounters many different Java classes.
A call site in a library compiled from Python
will de-optimise to the slow path when
the tree of guarded handles grows too large.
The Abstract API is another place where
many different classes arrive at a single method.
The interpreter of CPython byte code,
which we need too,
and Python operations in modules,
both rely on calls to the Abstract API.
We should not use call sites to implement the Abstract API,
since they will eventually de-optimise.

The safest course of action with mutable subclasses, and
code that encounters objects of many types,
is to look up the descriptor along the MRO every time.

Suppose we think this is too slow.
There are two steps in the conventional chain of objects that
frustrate simply caching the handle we find first,
whether in the type object or on the call site:

1. The type has to be looked up on each object that arrives there.
   The Java class is not enough:
   the same Java class represents instances from multiple Python types.
#. Each type has its own MRO in which dictionaries are, in general, mutable.
   What we find in the first lookup may be invalidated by subsequent change
   anywhere along the MRO.

The first of these requirements makes the case for a cache of handles
on a ``ReplaceableType`` (only).
A call site embedding the handle itself would have to
follow a guard on the Java class with one on the Python type.
But a handle in the call site that invokes the handle on the type,
need be guarded only by the class of the object.
We still need the apparatus to refresh the handle in the type,
as the appropriate method definition changes (second requirement),
but it is not as onerous as updating every call site.

Another solution is to augment lookup along the MRO with a cache,
so that we get to the descriptor more quickly.
This again requires that each mutable type
keep track of its descendants for cache invalidation.
This is roughly what Jython 2 does.

The cost in space and time of
a set of method handles on each type object,
or of caching lookups in some other structure,
is not negligible,
nor that of propagating change in any scheme.
We'll try to make finding and calling an un-cached descriptor
as slick as possible,
but for the time being,
we do not create method handle slots as we did in ``rt3``.


.. _Representation-builtin-int-bool:

Representing ``int`` and ``bool``
=================================

The type ``int`` is defined by the class ``PyLong``.
The name is chosen to make porting from the CPython API easier.
The pattern there for ``int`` things is ``PyLong_*``,
rather than ``PyInteger_*``,
for historical reasons.

``java.lang.Integer`` and ``java.math.BigInteger``
are adopted as representations of ``int``.
We might also allow ``Long``, ``Short`` and ``Byte``.
So far, this is very much like our account of ``float``,
and the same approach can be taken successfully,
with just one problem.

The type ``bool``, implemented by ``PyBoolean``,
is represented by the Java type ``java.lang.Boolean``.
``PyBoolean`` is *not* a representation of ``bool``:
it is just where we keep some implementation methods.
We only use the constants ``Boolean.TRUE`` and ``Boolean.FALSE``
when we represent Python ``True`` and ``False``.
An application *might* create other instances of ``Boolean``,
although that practice is deprecated.
We should recognise these as valid, but not create them.

The complication is that ``bool`` is a subclass of ``int`` in Python,
while ``Boolean`` is not a subclass of ``PyLong``,
the canonical representation of ``int``.
What we have decided concerning Python subclasses of ``float``,
while it works for subclasses of ``PyLong``,
does not quite work for ``bool``.

Many a method from ``int`` is inherited by ``bool``,
for example:

..  code-block:: python

    >>> bool.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> bool.__add__
    <slot wrapper '__add__' of 'int' objects>
    bool.__radd__
    <slot wrapper '__radd__' of 'int' objects>

This means that when we look up such a method on ``bool``,
or when we try to evaluate ``-x``, ``x+i``, or ``i+x``,
where ``x`` is a ``bool``,
the method name will resolve to an entry in the dictionary of ``int``.
Each method descriptor of ``int`` contains an array of implementations
(as ``MethodHandle``\s).
Following the pattern established for ``float``,
the ``self`` argument of these implementations support ``PyLong``,
the adopted representations of ``int``, and subclasses of those,
but not ``Boolean`` since that is not a representation of ``int``.

The VSJ3 answer to this was to provide a means
for types to accept additional classes as ``self``
that are not representations of the type.
Extra implementations of the methods were provided,
at the end of the array in each method descriptor,
where ``self`` matched the subclass representation class.
Thus the inherited descriptor would have an entry
applicable to the non-representation subclass.

This seems basically sound, but we must revisit it,
as a few things have changed in the implementation for VSJ4.
We hope to avoid additional complexity in the implementation,
given that the only discovered application is ``bool``.


.. _Operations-builtin-int-bool-neg:

The Unary Operation ``__neg__``
-------------------------------

We build on the explanation we gave for ``float``,
where in the course of executing a ``UNARY_NEGATIVE`` opcode,
the interpreter picks up an ``Object`` from the stack.
In the cases where the object happens to be
an ``Integer`` or a ``BigInteger``,
the model in :ref:`Representation-builtin-float`
is a good enough guide already to what takes place.

But suppose it finds it to be Python ``True``.
How does it locate the particular implementation of ``__neg__``?
As a start, we arrange that
the implementations of ``int.__neg__`` include one for ``Boolean``:

..  code-block:: java

    PyLongMethods {
        // ...
        static Object __neg__(PyLong self) { return self.value.negate(); }
        static Object __neg__(Integer self) { return -self; }
        static Object __neg__(BigInteger self) { return self.negate(); }
        static Object __neg__(Boolean self) { return self ? -1 :0; }

The special method descriptor in the dictionary of ``int``
will contain an array of handles to these implementations.
Each ``Representation`` of ``int``
will specify its own location in the array,
but the ``Representation`` of ``bool`` cannot,
since it is already ``Representation`` 0 of ``bool``.
We shall have to find it another way.

The structure we propose looks like this,
when realised for integer ``42`` and boolean ``True``:

..  uml::
    :caption: Instance model of ``int``, ``bool`` and their ``__neg__``

    object "42 : Integer" as x
    object "Integer : Class" as Integer.class
    object " : MethodHandle" as integerNeg {
        target = PyLongMethods.__neg__(Integer)
    }
    object "int : AdoptedRepresentation" as intRepresentation {
        index = 3
    }
    object "int : AdoptiveType" as intType
    x ..> Integer.class
    Integer.class --> intRepresentation : registry
    intRepresentation --> intType : type
    object " : Map" as intDict
    intType --> intDict : dict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }
    intDict --> neg


    object "True : Boolean" as True
    object "Boolean : Class" as Boolean.class
    object " : MethodHandle" as booleanNeg {
        target = PyLongMethods.__neg__(Boolean)
    }
    object "bool : SimpleType" as boolType {
        index = 0
    }
    True ..> Boolean.class
    Boolean.class --> boolType : registry
    object " : Map" as boolDict
    boolType --> boolDict : dict

    neg --> integerNeg : 2
    neg --> booleanNeg : 3
    boolType -right-> intType : base

Note that ``bool`` is a ``SimpleType``,
so it serves as the ``Representation`` itself.

When we come to choose an implementation of ``__neg__``,
we first find the representation and type registered for ``Boolean``.
The dictionary of type ``bool`` does not contain ``__neg__``,
and so we traverse the MRO of ``bool`` and find ``__neg__`` in ``int``.

When we find an attribute amongst the ancestors of a class,
we always look up method implementation zero.
Normally this is exactly the right choice,
because the subclass representation will normally extend
the primary representation of its parent.
``bool`` is the exception to this rule,
where (in the example at least) we need to use index 3,
which isn't even a registered representation for ``int``.
This implies that
we must search for the matching ``MethodHandle``.

Notice that when we find a method descriptor in a type dictionary,
we cannot safely invoke any handle in it,
even when there is only one.
This is because method descriptors may be moved between types,
at least if the receiving type is mutable.

..  code-block:: python

    >>> class F(float): pass
    ...
    >>> F.__neg__ = int.__neg__
    >>> F(42)
    42.0
    >>> -F(42)
    Traceback (most recent call last):
      File "<pyshell#48>", line 1, in <module>
        -F(42)
    TypeError: descriptor '__neg__' requires a 'int' object but received a 'F'
    >>> F.__neg__.__objclass__
    <class 'int'>

We see that Python must check the ``self`` argument against
the (Python) type for which the method descriptor is intended,
stored in ``__objclass__``.
We conclude that Jython must check the Java class of ``self`` against
the Java type of (all) the handles available.
We can do this *instead of* the Python type check
and still produce the same message if no handle fits.

We have to turn the ``if``-test into a loop,
in case we are dealing with an "accepted" representation.
In all cases except ``bool``,
either the first handle we fits, or we shall be raising an error,
and so the cost of the test is not great in non-error cases.
If we implement the caching of handles on ``Representation``\s,
the entire look up, including this test,
need only be performed when the handle is cached
(once or once per change).



Summary Examples
================

We have not explored all the examples we might.
Here they are and some further examples in summary form.


..  list-table:: Representation of exemplar types
    :header-rows: 1
    :widths: auto

    * - Type
      - Primary
      - Adopted
      - Canonical Base

    * - ``object``
      - ``Object``
      -
      - ``Object``

    * - ``type``
      - ``PyType``
      -
      - ``SimpleType``

    * - ``list``
      - ``PyList``
      -
      - ``PyList``

    * - ``str``
      - ``PyUnicode``
      - ``String``, ``Character``
      - ``PyUnicode``

    * - ``int``
      - ``PyLong``
      - ``Integer``, ``BigInteger``, ``Long``, ``Short``, ``Byte``
      - ``PyLong``

    * - ``float``
      - ``PyFloat``
      - ``Double``, ``Float``
      - ``PyFloat``

    * - ``bool``
      - ``Boolean``
      - ``Boolean``
      - (final)

When we define a new class in Python, it has one or more bases,
all of them specified as Python type objects.
If no bases are specified in the class definition,
there is one base, which is ``object``.

A Java class must be created or found to represent the new class,
that is assignment compatible with the ``self`` argument
of all exposed methods of every base.
While Python allows multiple inheritance,
when it involves types implemented in Java (or C),
restrictions equivalent to single inheritance are imposed
by "layout" constraints.

The representation of the new class is then an immediate subclass of
the "most derived" Python type implemented in Java.
The constraints Python imposes,
expressed first as consistent memory layout in C,
ensure that the most-derived type is uniquely identifiable in Java.
This subclass adds only slots or an instance dictionary to its parent,
and so we may define it in advance as the *extension point* class,
which by convention is a nested class ``Derived``.
Since it extends the (canonical) representation of the most derived class,
it is acceptable as ``self`` (really, ``this``) in any method.

The ``Derived`` class is always derived from
the first representation in the table above,
and (if the Python type can be used as a base at all)
we never find ourselves trying to derive from two bases,
unless one of them is ``Object``.

