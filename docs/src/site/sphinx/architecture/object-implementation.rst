..  architecture/object-implementation.rst


Object Implementation 
#####################

This section is for discussion of
the implementation of operations on Python objects
corresponding to the slots (``tp_hash``, ``nb_add``, etc.)
defined for CPython in the ``PyTypeObject``,
and their relationship to dunder methods (``__hash__``, ``__add__``, etc.).

..  note:: At the time of writing
    the ``evo3`` implementation is providing evidence for certain choices,
    but there is still a long way to go:
    inheritance is not yet correct,
    nor support for acceptable implementations.

..  note:: These notes are quite sketchy in places at present (``evo3``).


Type Slots
**********

The implementation of object type in CPython
depends on a pointer in every ``PyObject`` to a ``PyTypeObject``,
in which a two-level table structure gives operational meaning,
by means of a pointer to function,
to each of the fundamental operations that any object could,
in principle, support.


Apparent Obsession with ``MethodHandle``
========================================

We are using MethodHandle as an equivalent to C pointer-to-function.
Other possibilities exist.

The ``evo3`` implementation provides evidence this choice is workable,
but in the context of a CPython byte code interpreter,
we might have used other mechanisms (lambda functions, say),
and Jython 2 approaches the same need through overriding methods,
which seems the natural choice in Java.
The repeated idiom ``(PyObject) invokeExact(...)``
is fairly ugly and sacrifices Java safety around the method signature.
The type slots all contain method handles,
and we have used them again
in the implementation of ``PyJavaFunction.tp_call``.

A strong motivation for the use of ``MethodHandle``\s is that
they work directly with ``invokedynamic`` call sites.
We expect to generate ``invokedynamic`` instructions
when we compile Python to JVM byte code,
and this is the code in which we seek maximum performance. 



Initialisation of Slots
=======================

From Definitions in Java
------------------------

We have established a pattern in ``rt2`` (``evo2`` onwards)
whereby each ``PyType`` contains named ``MethodHandle`` fields,
pointing to the implementation of the "slot functions" for that type.
At the time of writing (``evo3``),
these are identified by a reserved name like ``nb_add`` or ``tp_call``.
Other approaches are possible and certainly other names.
The design, using a system of Java ``enum``\s denoting the slots,
has worked smoothly in the definition of a wide range of slot types.

The handle in a given slot has to have
a signature characteristic of the slot.

Where a slot defined in a type corresponds to a special function,
in the way for example ``nb_negative`` corresponds to ``__neg__``,
a callable that wraps an invocation of that slot
will be created in the dictionary of the type.
This makes it an attribute of the type.
Instances of it appear to have a "bound" version of the attribute,
that we may tentatively equate to a Curried ``MethodHandle``.

..  code-block:: pycon

    >>> int.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> int.__dict__['__neg__']
    <slot wrapper '__neg__' of 'int' objects>
    >>> (42).__neg__
    <method-wrapper '__neg__' of int object at 0x00007FF8E0CD9BC0>

If the slot is inherited,
it is sufficient that the method be an attribute by inheritance.

..  code-block:: pycon

    >>> bool.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> bool.__dict__['__neg__']
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    KeyError: '__neg__'
    >>>
    >>> class MyInt(int): pass
    ...
    >>> MyInt.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> MyInt.__dict__['__neg__']
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    KeyError: '__neg__'
    >>> m = MyInt(10)
    >>> -m
    -10

In the last operation,
``-m`` invokes the slot ``nb_negative`` in the type of ``m``,
which is a copy of the one in ``int``.
This happens without a dictionary look-up.


From Instance Methods [untested]
--------------------------------

In implementations up to ``evo3``,
the functions are ``static`` members of the implementation class of the type,
or of a Java super-type,
with a signature correct for the slot.
They could, without a significant change to the framework,
be made instance methods of that class.

Wer took a step towards instance methods in ``evo3``,
when it became possible for an argument to the slot function
to adapt to the implementing type.
The method handle in slot ``nb_negative``
has ``MethodType`` ``(O)O`` as it must,
but the implementing function has signature ``(S)O``,
where ``S`` is the implementing type (the type of ``self``).
This is dealt with by a cast in the method handle,
which is neater than doing so in the implementation.

An exception to that pattern occurs with binary operations,
since although at least one of the operands has the target type,
or that implementation would not have been called,
it may be on the left or the right.
As a result,
the implementation (in CPython) must coerce both arguments.

Binary operations could be split into two slots
(``nb_add`` and ``nb_radd``, say),
guaranteeing the type of the target.
The split is necessary if we choose to make the slots instance methods.
In the instance method for ``nb_radd``,
the right-hand argument of ``+`` becomes the target of the call,
therefore the left-hand argument of the signature ``(S,O)O``.
We see this also in the (otherwise quite different)
Jython 2 approach to slot functions.


From Definitions in Python [untested]
-------------------------------------

A function defined in a class becomes a method of that class,
that is, it creates a function that is an attribute of the type.
This is true irrespective of the number or the names of the arguments.
We consider here how functions with the reserved names
``__neg__``, ``__add__``, and so on,
can be made to satisfy the type slots as the Python data model requires.

We saw in the previous section how the definition of a slot
induced the existence of a callable attribute,
a wrapper method on the slot that implements the basic operations,
and that this attribute was inherited by sub-classes:

..  code-block:: pycon

    >>> class MyInt(int): pass
    ...
    >>> MyInt.__neg__
    <slot wrapper '__neg__' of 'int' objects>
    >>> m = MyInt(10)
    >>> -m
    -10

Overriding ``__neg__`` changes this behaviour,
because assignment to a special function in a type
assigns the slot as well.
Note that,
although these methods are usually defined with the class,
they may be assigned after the type has been created,
and the change affects existing objects of that type.

..  code-block:: pycon

    >>> MyInt.__neg__ = lambda v: 42
    >>> -m
    42
    >>> MyInt.__neg__
    <function <lambda> at 0x000001C97118EA60>
    >>> MyInt.__dict__['__neg__']
    <function <lambda> at 0x000001C97118EA60>

The implementation of attribute assignment in ``type``
must be specialised to check for these special names.
It must insert into the slot (``nb_negative`` in the example)
a ``MethodHandle`` that will call the ``PyFunction``
being inserted at ``__neg__``.

CPython ensures that a change of definition is visible
from types down the inheritance hierarchy from the one modified,
so that the behaviour of classes inheriting this method follows the change.

..  code-block:: pycon

    >>> class MyInt2(MyInt): pass
    ...
    >>> m2 = MyInt2(100)
    >>> -m2
    42
    >>> MyInt.__neg__ = lambda v: 53
    >>> -m2
    53

This fluidity limits the gains available from binding a handle to a call site.
A call site capable of binding a method handle
(even one guarded by the Python type of the target)
must still consult the slot because it may have changed by this mechanism.
A call site may bind the actual value found in a slot
only if that is immutable or it becomes an "observer"
of changes coming from the type hierarchy,
potentially to be invalidated by a change (see ``SwitchPoint``).
The cost of invalidation is quite high,
but applications do not often have to redefine a slot.

Some types,
generally built-in types,
do not allow (re)definition of special functions,
even by manipulating the dictionary of the type.

..  code-block:: pycon

    >>> int.__neg__ = lambda v: 42
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: can't set attributes of built-in/extension type 'int'
    >>> int.__dict__['__neg__'] = lambda x: 42
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: 'mappingproxy' object does not support item assignment

A call site that binds the value from a slot in such a type
does not need to become an observer of the type,
since no change will ever be notified.


Bug involving Arithmetic and Sequence Slots in CPython
------------------------------------------------------

Whilst on this subject,
it is worth noting an `operand precedence bug`_ in CPython
with respect to sequence ``+`` and ``*``,
where the same special function defines multiple slots.
The examples are ``__add__``,
which fills both ``nb_add`` and ``sq_concat``,
and ``__mul__``,
which fills both ``nb_multiply`` and ``sq_repeat``.
This also involves the reflected and in-place variants of these operators
(``__iadd__``, ``__imul__``, ``__radd__``, ``__rmul__``).

A `discussion of the operand precedence bug`_ concludes that
the root of the problem is that the abstract implementation
of these binary operation tries to treat both arguments as numeric,
that is, CPython tries the ``nb_add`` slot in the left *and right* operands,
before it tries ``sq_concat`` in the left.
A simple illustration is:

..  code-block:: pycon

    >>> class C(tuple):
    ...     def __radd__(w, v):
    ...         return 42
    ...
    >>> [1,] + C((2,3,4))
    42

In fact, there is a ``list.__add__``,
but it is defined by the ``sq_concat`` slot,
which is not tried until after the ``nb_add`` of ``C``,
with the ``C`` instance on the right leading to a call of ``__radd__``.
(Note that ``C`` is not a sub-class of ``list``.)

Several downstream libraries depend on this bug.
They may give different meanings to the ``nb_add`` and ``sq_concat`` slots,
or the ``nb_multiply`` and ``sq_repeat`` slots,
relying on the (faulty) ordering to get their ``nb_add`` called first.
This is only possible in the C implementation of their objects,
so it should be considered a CPython detail, not a language feature.
(PyPy has reproduced the bug so that it can support these C extensions.)

..  note:: We could do away with the ``sq_concat`` slot,
    and have only ``nb_add``,
    which would then be implemented by ``list``, etc. as concatenation.
    And the same for ``sq_repeat`` in favour of ``nb_multiply``.
    There would then be only one place to look for ``list.__add__`` etc.,
    and it would definitely be tried first.

..  _operand precedence bug:
    https://bugs.python.org/issue11477
..  _discussion of the operand precedence bug:
    https://mail.python.org/pipermail/python-dev/2015-May/140006.html


Inheritance of Slots [untested]
-------------------------------

The following is an understanding of the CPython implementation.
(Certain slots have to be given special treatment,
but for most operations, the account here is accurate.)
The behavioural outcome must be the same for all implementations,
and having decided on a Java implementation that uses slots,
the mechanics would have to be similar.

When a new type is defined,
a slot will be filled, by default, by inheritance along the MRO.
This does not happen directly by copying,
but indirectly through the normal rules of class attribute inheritance,
then the insertion of a handle for the slot function.
These are the same rules under which requested ``x.__add__``, say,
will be sought along the MRO of ``type(x)``.

If the inherited attribute, where found, is a wrapper on a slot,
certain coherence criteria are met,
and there are no additional complexities
the wrapped slot may be copied to the new type directly.
(It is unclear from comments in CPython
``~/Objects/typeobject.c update_one_slot()``
exactly what "complex thinking" the code is doing.
This is the bit of CPython that offers free-as-in-beer ... beer.)

If the inherited attribute is a method defined in Python,
the slot in the new class will be an invoker for the method,
identified by its name.
Each call involves searching for the definition again along the MRO.
(Search along the MRO is backed by a cache, in CPython,
mitigating the obvious slowness.)

When a special function is re-defined in a type,
affected slots in the sub-types of that type are re-computed.
This is why a re-definition is visible in derived types.


``tp_new`` and Java Constructors
********************************

In CPython,
the ``tp_new`` slot of a particular instance of ``PyTypeObject``,
acts as the constructor for the type the ``PyTypeObject`` represents.
This section gives detailed consideration to the problem of
implementing its behaviour in Java.

A "second phase" of construction is performed by ``tp_init``,
but this has much the character of any other instance method.
Although called once automatically, it may be called again expressly,
if the programmer chooses.
``tp_new``, however, is a static method called once per object,
since creates a new instance each time.

Calling a type object
(that is, invoking the ``tp_call`` slot of the ``PyTypeObject`` for ``type``,
and passing it the particular ``PyTypeObject`` for ``C`` as the target)
is what normally leads to invoking the ``tp_new`` slot
on the ``PyTypeObject`` for ``C``,
and ``tp_init`` soon after.
An introduction to the topic,
by Eli Bendersky,
may be found in `Python object creation sequence`_.


Relation of ``tp_new`` to the Java constructor
==============================================

Close, but not close enough
---------------------------

It appears at first as if a satisfactory Java implementation
of the slot function would be the constructor in the defining class.
But a ``tp_new`` slot is inherited by copying,
and many Python types simply get theirs from ``object``.
The definition of ``tp_new`` executed in response to a call ``C()``
could easily be in some ancestor ``A`` on the MRO of ``C``.
The Java constructor for ``A`` would only be satisfactory if
the Java class implementing ``C`` were
the same as that implementing ``A``.
This will not be true in general.

An instance must be created somehow,
so a Java constructor must be invoked,
but from the observation above,
it isn't enough simply to place a ``MethodHandle`` to the constructor
in the ``tp_new`` slot,
even if the signature is made to match.


``__new__`` and a parallel
--------------------------

In cases where ``C`` customises ``tp_new`` in Python
(defines ``__new__``),
it is conventional for ``C.__new__`` to call ``super(C, cls).__new__``
before making its own customisations.
This use of ``super`` means the interpreter should
find ``__new__``, in the MRO of ``cls``, starting after ``C``,
and so the call is to the first ancestor of ``C`` defining it.
Something equivalent must happen in a built-in or extension type.

Since each ``__new__`` (or ``tp_new``) defers immediately to an ancestor,
the first customisation that *completes* is in the ``type`` of ``object``.
This is similar to the way in which Java constructors,
explicitly or implicitly,
first defer to their parent's constructor.
The ancestral line in Java traces itself all the way to ``Object``,
which is therefore the last constructor to start and first to complete.


Allocation before initialisation
--------------------------------

Recall that the first argument in each ``tp_new`` slot invocation
is the type of the target class ``C``.
The ``tp_new`` in the ``PyTypeObject`` for ``object`` in CPython
invokes a slot on the target class we haven't mentioned yet, ``tp_alloc``.
This allocates the right amount of memory for the target type ``C``,
in which the hierarchy of ``tp_new`` slot functions
will incrementally construct an instance of ``C`` from the arguments,
as they complete in reverse method-resolution order.

There is no parallel to the allocation step in Java source:
one cannot allocate an object separate from initialising it,
since an expression with the ``new`` keyword does both.
There *is* a JVM opcode (``new``)
that allocates an uninitialised object of the right size.
The source-level ``new`` generates this, and
an ``invokespecial`` for a target ``<init>()V`` method.
Allocation must happen in Java where object creation is initiated,
not in the ``tp_new`` of ``object`` as it can in CPython.


Examples guiding architectural choices
======================================

Example: extending a built-in
-----------------------------

Consider the following where we derive classes from ``list``
and then manipulate the ``__class__`` attribute of an instance.
What Java classes would make this possible?

..  code-block:: pycon

    >>> class MyList(list): pass
    ...
    >>> class MyList2(MyList): pass
    ...
    >>> m2 = MyList2()
    >>> m2.__class__ = MyList
    >>> m2.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses

The very possibility of giving ``m2`` the Python class ``MyList``
tells us that both must be implemented by the same Java class,
since the Java class of an object cannot be altered.
However,
we were unable to give ``m2`` the type ``list`` (a ``PyList`` in Java).
This allows the implementation of ``MyList`` and ``MyList2`` to be
a distinct Java class from ``PyList``.

It had better be *derived* from ``PyList``
so we can apply its methods to instances of the sub-classes.
One thing we would have to add to this sub-class is a dictionary,
since instances of ``MyList`` have one.
Let's call this class ``PyListDerived`` here, as in Jython 2.
(In practice, an inner class of each built-in seems a tidy solution.)

In the following diagram,
the Python classes in our example are connected to
the Java classes that implement their instances.

..  uml::
    :caption: Extending a Python built-in

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList <<Python>>
    MyList2 <<Python>>

    MyList2 -|> MyList
    MyList -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
    }

    PyListDerived -|> PyList
    PyList -|> Object

    MyList2 .. PyListDerived
    MyList .. PyListDerived
    list .. PyList


Example: extending with ``__slots__``
-------------------------------------

Another possibility for sub-classing is
to specify a ``__slots__`` class attribute.
This suppresses the instance dictionary that was
automatic in the previous example.
Instances are not class re-assignable from other derived types.
Consider:

..  code-block:: pycon

    >>> class MyListXY(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy = MyListXY()
    >>> mxy.__class__ = list
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment only supported for heap types or
        ModuleType subclasses
    >>> mxy.__class__ = MyList
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyList' object layout differs from
        'MyListXY'
    >>> m2.__class__ = MyListXY
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListXY' object layout differs from
        'MyList'

However,
they are class re-assignable from other derived classes,
provided the "layout" matches,
i.e. the slots have exactly the same names in order and number,
and there is (or isn't) an instance dictionary in both.

..  code-block:: pycon

    >>> class MyListXY2(list):
    ...     __slots__ = ('x', 'y')
    ...
    >>> mxy.__class__ = MyListXY2
    >>> class MyListAB(list):
    ...     __slots__ = ('a', 'b')
    ...
    >>> mxy.__class__ = MyListAB
    Traceback (most recent call last):
      File "<stdin>", line 1, in <module>
    TypeError: __class__ assignment: 'MyListAB' object layout differs from
        'MyListXY2'

The possibility of giving ``mxy`` class ``MyListXY2``
tells us that both must be implemented by the same Java class.

In fact it is possible to derive again from a slotted class,
in such a way that it gains an instance dictionary,
or to add ``__slots__`` to a base class that has a dictionary.
(The purpose of ``__slots__`` in Python is
to save the space an instance dictionary occupies,
an advantage lost when the ideas are mixed,
but it must still work as expected.)
Instances of all these types may have their class re-assigned,
provided the constraint on ``__slots__`` is also met.

..  code-block:: pycon

    >>> class MyListMix(MyList2, MyListXY): pass
    ...
    >>> mix = MyListMix()
    >>> mix.a = 1
    >>> mix.__slots__
    ('x', 'y')

To support ``__slots__`` and instance dictionaries in these combinations,
we add a ``slots`` member to ``PyListDerived``.

..  uml::
    :caption: Extending a Python built-in (supporting ``__slots__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    MyList2 <<Python>>
    MyListXY <<Python>>
    MyListMix <<Python>>

    MyListMix -|> MyListXY
    MyListMix -|> MyList2
    MyList2 -|> list
    MyListXY -|> list
    list -|> object

    class PyListDerived {
        dict : PyDictionary
        slots : PyObject[]
    }

    PyListDerived -|> PyList
    PyList -|> ArrayList
    ArrayList -|> Object

    MyListMix .. PyListDerived
    MyListXY .. PyListDerived
    MyList2 .. PyListDerived
    list .. PyList

We have shown the slots implemented as an array,
which is the approach Jython 2 takes.
The dictionary of the type contains entries for "x" and "y",
that index the ``slots`` array in the instance.
Another possibility is to create a new type with fields "x" and "y",
but this requires careful book-keeping to ensure ``MyListXY2``
gets the same implementation class.


Example: extending with custom ``__new__``
------------------------------------------

Consider the case of a long inheritance chain (from ``list`` again),
including one class that customises ``__new__``:

..  code-block:: python

    class L1(list): pass

    class L2(L1):
        def __new__(c, *a, **k):
            obj = super(L2, c).__new__(c, *a, **k)
            obj.args = a
            return obj

    class L3(L2): pass

    x = L3("hello")

After running that script, we may examine what we created

..  code-block:: python

    >>> x
    ['h', 'e', 'l', 'l', 'o']
    >>> x.args
    ('hello',)

The definitions result in an MRO for ``L3`` of
``('L3', 'L2', 'L1', 'list', 'object')``.
The construction of ``x`` calls ``L2.__new__``.
Each class in the MRO gets its turn to customise the object.
We can illustrate how classes in Python are realised by objects in Java
in the following (somewhat abusive UML) diagram,
showing the Java ``PyType`` objects that implement
the Python classes in the discussion:

..  uml::
    :caption: Representing a Python MRO (including ``__new__``)

    skinparam class {
        BackgroundColor<<Python>> LightSkyBlue
        BorderColor<<Python>> Blue
    }

    object <<Python>>
    list <<Python>>
    L1 <<Python>>
    class L2 <<Python>> {
        {method} __new__(c, *a, **k)
    }
    L3 <<Python>>

    list -|> object
    L1 -|> list
    L2 -|> L1
    L3 -|> L2

    object "<u>:PyType</u>" as Tobject {
        name = "object"
    }

    object "<u>:PyType</u>" as Tlist {
        name = "list"
    }

    object "<u>:PyType</u>" as TL1 {
        name = "L1"
    }

    object "<u>:PyType</u>" as TL2 {
        name = "L2"
    }

    object "<u>:PyType</u>" as TL3 {
        name = "L3"
    }

    object "<u>:PyFunction</u>" as L2new {
        {field} __name__ = "__new__"
    }

    object "<u>:PyJavaFunction</u>" as listnew {
        {field} __name__ = "__new__"
    }


    TL3 -> TL2
    TL2 -> TL1
    TL1 -> Tlist
    Tlist -> Tobject

    L3 .. TL3
    L2 .. TL2
    L1 .. TL1
    list .. Tlist
    object .. Tobject

    TL2 -down-> L2new
    Tlist -down-> listnew

The functions in the diagram are (Python) attributes of the type objects,
implemented by descriptors in the dictionary of each type,
in this case under the key ``"__new__"``.
This complexity has been elided from the diagram.

During the building of the structure depicted,
the ``tp_new`` slot of ``L1`` is copied from that of ``list``,
the ``tp_new`` slot of ``L2`` is filled with a wrapper on ``L2.__new__``,
and the ``tp_new`` slot of ``L3`` is copied from that of ``L2``.
The pre-existing ``list.__new__`` is a wrapper invoking ``list.tp_new``.
It sounds as if the chain up to ``list`` is broken between ``L2`` and ``L1``,
and it would be if ``L2.__new__`` were not to call a super ``__new__``.

Now, consider constructing a new object of Python type ``L3``,
by calling ``L3()``.
We know that this invokes the slot ``tp_call`` on ``type``
with ``L3`` as target,
and that in turn invokes the ``tp_new`` slot on ``L3`` with ``L3`` as target.
The ``tp_new`` slot on ``L3`` is a copy of that in ``L2``
and so the code for ``L2.__new__`` is executed (with ``c = L3``).

The expression ``super(L2, c).__new__``
resolves to the ``__new__`` attribute of ``list``, by inheritance,
and this is a wrapper that invokes the method ``PyList.tp_new``.
Recall that the first argument to ``tp_new`` (a ``PyType``) must be
the type actually under construction, in this case ``L3``.

A conclusion about inheritance
------------------------------

We conclude from the examples that the behaviour of ``PyList.tp_new`` must be
to construct a plain ``PyList`` when the type argument is ``list``,
but a ``PyListDerived`` when it is a Python sub-class of ``list``.
``PyListDerived`` is a Java sub-class of ``PyList``
that potentially has ``dict`` and ``slots`` members.
Whether the object actually has ``dict`` or ``slots`` members (or both)
is deducible from the definition,
and must be available from the type object when we construct instances.


.. _Python object creation sequence:
    https://eli.thegreenplace.net/2012/04/16/python-object-creation-sequence


