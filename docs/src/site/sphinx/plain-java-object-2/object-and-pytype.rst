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
===============================

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
They all therefore must share the same representation in Java.
A Java subclass of ``PyList``,
with ``__dict__`` and an explicit ``type``,
may be created in advance,
and used for all such Python subclasses of ``list``.
This prepared class is called ``PyList.Derived``.

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


Sub-classes of  ``list`` using ``__slots__``
============================================

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
    object " : AdoptedRepresentation" as Object.rep
    Object.class -right-> Object.rep : registry

    object "object : AdoptiveType" as objectType
    Object.rep -right- objectType

    object "a : PyObjectBase" as a {
        type = A
        __dict__ = {'x':42}
    }
    object "a2 : PyObjectBase" as a2 {
        type = A2
        __dict__ = {'y':43}
    }

    object "PyObjectBase : Class" as PyObjectBase.class

    object "A : ReplaceableType" as AType
    AType -up-> objectType : base
    object "A2 : ReplaceableType" as A2Type
    A2Type -up-> AType : base

    object " : SharedRepresentation" as PyObjectBase.rep
    a .right.> PyObjectBase.class
    a2 .up.> PyObjectBase.class

    PyObjectBase.class -right-> PyObjectBase.rep : registry
    AType -left-> PyObjectBase.rep
    A2Type --left-> PyObjectBase.rep

    'a --> AType : type
    'a2 --> A2Type : type

Notice that the Java class of ``a`` and ``a2`` is the same ``PyObjectBase``,
that is, the have the same representation,
an instance of ``SharedRepresentation``.
Imagine we pick up either of these and ask its Python type:
the class leads us to the same representation,
from which there is no navigation to ``A`` or ``A2``.
However, ``SharedRepresentation.pythonType(Object o)``
consults the argument for its actual type.

The Java class of ``o`` is simply ``Object``,
which is the (single) adopted representation of ``object``.


Type Objects for ``type``
=========================

In the preceding diagrams,
we depicted objects and the web of connections
we use to navigate to their Python type.
But the type objects we reached are themselves Python objects,
and they have a type object too.

It is well known that the ``type`` of ``type`` is type itself.
We have already come across three variant implementations of ``type``
in the examples.
Suppose we start with one instance of each implementation.
We should be able to navigate from each of them to the same object.

..  uml::
    :caption: Type Objects for ``type``

    object "list : SimpleType" as listType
    object "A : ReplaceableType" as AType
    object "object : AdoptiveType" as objectType

    object "PyType : Class" as PyType.class
    object "SimpleType : Class" as SimpleType.class
    object "ReplaceableType : Class" as ReplaceableType.class
    object "AdoptiveType : Class" as AdoptiveType.class

    listType ..> SimpleType.class
    AType ..> ReplaceableType.class
    objectType ..> AdoptiveType.class

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

    object "x : PyBaseObject" as x
    x --> MyClass : type
    object "y : PyBaseObject" as y
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


    object "z : PyBaseObject" as z
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




.. note:: Still editing from here on.


``PyType`` and ``Representation`` for ``float``
===============================================

The type 'float' is defined by the class ``PyFloat``,
but ``java.lang.Double`` is adopted as its representation
(and we might also allow ``java.lang.Float``).

.. _Operations-builtin-float-neg-2:

A Unary Operation ``float.__neg__``
-----------------------------------

In VSJ 2 we created the means to build a ``PyType``
for classes crafted to represent Python objects.
An example is ``PyFloat``,
representing the type ``float`` and its instances.
There the ``PyType`` provided a ``MethodHandle`` to each method of ``float``,
including the special methods (such as ``__neg__``)
for which it also acted as a cache.

Suppose we want to do the same again,
but now also to allow instances of ``java.lang.Double``
to represent instances of ``float``.
For each method, including the special methods,
we shall have to provide an implementation applicable to ``PyFloat``,
as before,
and also one applicable to ``Double``.

In fact, since the range and precision of ``Double``
are the same as those of ``PyFloat``,
we could manage without ``PyFloat`` entirely,
were it not that we need to define subclasses of ``float`` in Python.
Sub-classes in Python must be represented by subclasses in Java
(with a few exceptions)
and ``Double`` cannot be subclassed.


Descriptive Structures
----------------------

Let's just address unary negative to begin with.
Suppose that in the course of executing a ``UNARY_NEGATIVE`` opcode,
the interpreter picks up an ``Object`` from the stack
and finds it to be a ``Double``.
How does it locate the particular implementation of ``__neg__``?

The structure we propose looks like this,
when realised for two floating-point values:

..  uml::
    :caption: Instance model of ``float`` and its operations

    object "1e42 : PyFloat" as x
    object "PyFloat : Class" as PyFloat.class

    object " : MethodHandle" as xneg {
        target = PyFloatMethods.__neg__(PyFloat)
    }

    object "float : PyType" as floatType

    x --> PyFloat.class
    PyFloat.class --> floatType : ops
    floatType --> floatType
    floatType --> xneg : op_neg

    object "42.0 : Double" as y
    object "Double : Class" as Double.class
    object " : Operations" as yOps

    object " : MethodHandle" as yneg {
        target = PyFloatMethods.__neg__(Double)
    }

    y --> Double.class
    Double.class --> yOps : ops
    yOps -left-> floatType : type
    yOps --> yneg : op_neg

    object " : Map" as dict
    object " : PyWrapperDescr" as neg {
        name = "__neg__"
    }

    floatType --> dict : dict
    dict --> neg
    neg --> xneg
    neg --> yneg


We separate the responsibilities of ``PyType``,
where they have to adapt to the specific Java implementation,
into:

* an ``Operations`` object specialised to one Java implementation class, and
* the ``PyType`` containing the information common to all implementations.

A ``PyType`` is a particular kind of ``Operations`` object,
describing the *canonical implementation* (``PyFloat`` in this case).
The ``Operations`` object for an alternative *adopted implementation*,
is not a ``PyType``.

There can only be one descriptor for ``float.__neg__``,
in the dictionary of the ``type`` for float.
How does it describe the several implementations?
The descriptor must reference all the implementations of its method,
and during execution we must choose one
that matches the class of object appearing as ``self``.

There may not have to be an implementation for each representation:
an implementation method accepting ``Object self``,
or some common base class, would match them all.

As before, we shall have a caching scheme,
in which a slot on each ``Operations`` object,
including the ``PyType``,
holds the handle for its particular Java class.
In the present case, that cache will be the ``op_neg`` slot.


Method Implementations
----------------------

Methods defined in Java are exposed as Python methods
thanks to the class ``TypeExposer`` returned by ``Exposer.exposeType``.
We don't propose to describe how that works here,
only the data structures it finally supplies to the runtime.
The ``TypeExposer`` discovers slot methods automatically
without the annotations necessary to identify other methods.

At the time of writing,
the design provides for multiple styles of definition
of a special method implementation as:

1. an instance method in the canonical class,
#. a static method in the canonical class, or
#. a static method in an auxiliary class.

This last option is the one we use predominantly for types like ``float``,
that have multiple implementing classes and many methods,
since we may generate it with a script.
We are able to choose the style method-by-method, with some constraints.
The operations on ``Double`` have to be ``static`` methods:
we can't very well open up ``java.lang.Double`` and add them there!

When we come to study the implementation of ``int``,
we shall find that the types that can appear as ``self``
are more than just the adopted implementations.
This is because java.lang.Boolean has to be accepted by operations
as if it were a type of ``int``.
We shall use the term *accepted* implementations for the full list.

In the style we apply to ``__neg__`` and many other ``float`` methods,
we create a new class in which ``static`` methods
define the operations for the canonical and all accepted implementations.
We could reasonably think of the canonical implementation as
the *first accepted* implementation (implementation zero).

The defining implementation class will specify, during initialisation,
the Java classes that are the canonical, adopted and
other accepted implementations,
and the name of the extra classes defining the methods.
The defining class now begins something like this:

..  code-block:: java

    public class PyFloat extends AbstractPyObject {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("float", MethodHandles.lookup())
                        .adopt(Double.class)
                        .methods(PyFloatMethods.class));

It suits us still to define some methods by hand in ``PyFloat``,
but the class containing (most of) the methods is ``PyFloatMethods``.
It is generated by a script, as it is somewhat repetitious:

..  code-block:: java

    class PyFloatMethods {
        // ...
        static Object __abs__(PyFloat self)
                { return Math.abs(self.value); }
        static Object __abs__(Double self) { return Math.abs(self); }
        static Object __neg__(PyFloat self) { return -self.value; }
        static Object __neg__(Double self) { return -self; }


