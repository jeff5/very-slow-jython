..  generated-code/attributes-python.rst


Attributes Defined in Python
############################

Access to Attributes: A Trivial Case
************************************

Motivating Example
==================

Consider the simple statement ``a = C().x``,
where we construct an object and access a member.
We have not yet explored how to define classes and create instances,
but it is worth looking at the code CPython produces:

..  code-block:: python

    >>> dis.dis(compile("a = C().x", '<test>', 'exec'))
      1           0 LOAD_NAME                0 (C)
                  2 CALL_FUNCTION            0
                  4 LOAD_ATTR                1 (x)
                  6 STORE_NAME               2 (a)
                  8 LOAD_CONST               0 (None)
                 10 RETURN_VALUE

Since construction is just a function call,
the only opcode we lack is ``LOAD_ATTR``.
(We'll implement ``STORE_ATTR`` too.)

However, what kind of function is it that we call?
The answer, of course, is that it is the ``type`` object ``C`` itself.
A ``PyType`` must therefore be callable,
returning a new instance of the associated Java class
(or of a sub-class) implementing the Python type.

First, however,
we turn to the definition of a Python object with attribute operations.

A hand-crafted class will serve the purpose,
like the one we used in :ref:`a-specialised-callable`,
if it returns an object capable of "attribute access".
And we can instantiate it from Java without the associated ``PyType``.



..  note::

    This section needs re-reading, having been re-located.
    Compare "attributes in Java",
    then need to revisit with class in Python in mind.
    Is this where we do inheritance?
    No type defined in Python can avoid inheritance.


..  _instance-creation-new-python:


Creating an Instance with ``__new__``
=====================================

Note that the test class ``C`` has a method ``__new__``.
During class creation,
a handle to this is placed into slot ``tp_new``.
We need to arrange that ``C.TYPE`` be callable,
in other words that a ``PyType`` should be callable in general in Python.

To this end, we give ``PyType`` a ``__call__`` method
that will invoke the method in that type object's ``__new__``.
A simplified version of that (enough to make the example work) is:

..  code-block:: java

    class PyType implements PyObject {
        //...

        static PyObject __call__(PyType type, PyTuple args, PyDict kwargs)
                throws Throwable {
            try {
                // Create the instance with given arguments.
                MethodHandle n = type.tp_new;
                PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
                //...
                return o;
            } catch (EmptyException e) {
                throw new TypeError("cannot create '%.100s' instances",
                        type.name);
            }
        }

The elided code in the body of ``__call__``
deals with calling ``__init__`` on the new object,
and also with the possibility that the type being called is ``type``,
in which case,
if there is only one argument,
we are implementing the ``type()`` built-in function.
We may easily test this for ``C``:

..  code-block:: java

    class PyByteCode6 {
        // ...
        @Test
        void call_type_noargs() throws Throwable {
            PyObject c = Callables.call(C.TYPE);
            assertEquals(c.getType(), C.TYPE);
        }



***** Need to share with "attributes in Java":
here must create instance of type defined in Python.
Inevitably it extends a type defined in Java.
Call is to from Python type.__call__
on the type object of the type of object to create,
(which calls __new__ on the implementation of that type
or ancestor).

Class and Instance Improvements
*******************************

In this section we improve (but do not expect to perfect)
class and instance creation from Python.
This is a complex subject,
too complex to surmount in a single leap,
but we need to start somewhere.

Orientation
===========

Currently (from ``evo3``) we have built-in types,
implemented as Java classes,
for which the type objects are created by initialising the Java class.
Somewhere in the static initialisation of the implementation class,
we call ``PyType.fromSpec`` or the equivalent.
(The static initialisation of ``PyType`` itself
creates ``type`` and ``object``.)

We can create instances of these built-in types by:

* calling the constructor from Java (e.g. in a unit test);
* calling runtime support methods like ``Py.str()`` or ``Py.val()``
  when building a code object; or
* executing object-creating opcodes (like ``MAKE_FUNCTION``)
  or doing arithmetic.

For test purposes, we need to be able to create instances from Python,
as well as force them into existence from Java.
A start would be to be able to call ``int()`` or ``str()``,
to create instances of ``int`` and ``str``.
For this, we must define the ``__call__`` slot function in ``PyType``,
so that anything that is a ``type`` can be called to make an instance.

Then we would like to create *classes* in Python,
which is to say we would like to be able to create instances of ``type``.
One does not normally do this by calling ``type()``,
but it is quite possible to do so:

..  code-block:: python

    >>> C = type('C', (str,), {'a':"hello"})
    >>> C.__mro__
    (<class '__main__.C'>, <class 'str'>, <class 'object'>)
    >>> c.a
    'hello'

Normally though, one executes a ``class`` statement.


``__call__`` in ``PyType``
==========================

``PyType.__call__`` is actually fairly simple,
but it depends on two other new slots.
The body of this method invokes the new slot function ``__new__``,
which returns a new object,
followed optionally by ``__init__`` on the object itself.
``__new__`` must be defined or inherited
by all types we expect to instantiate this way.

..  code-block:: java

    class PyType implements PyObject {
        //...
        static PyObject __call__(PyType type, PyTuple args, PyDict kwargs)
                throws TypeError, Throwable {
            try {
                // Create the instance with given arguments.
                MethodHandle n = type.tp_new;
                PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
                // Check for special case type enquiry.
                if (isTypeEnquiry(type, args, kwargs)) { return o; }
                // As __new__ may be user-defined, check type as expected.
                PyType oType = o.getType();
                if (oType.isSubTypeOf(type)) {
                    // Initialise the object just returned (if necessary).
                    if (Slot.tp_init.isDefinedFor(oType))
                        oType.tp_init.invokeExact(o, args, kwargs);
                }
                return o;
            } catch (EmptyException e) {
                throw new TypeError("cannot create '%.100s' instances",
                        type.name);
            }
        }
        //...
    }

The code must take into account that ``type`` is itself a type,
but the call ``type(x)`` enquires the type of ``x``,
rather than being a constructor.
(The call ``type(name, bases, dict)`` does construct a ``type`` however.)
This difference is detected from the number of arguments by
``isTypeEnquiry(type, args, kwargs)``.
We follow CPython in placing the test after ``__new__`` is invoked.
``type.__new__`` performs both functions.


Slots ``tp_new`` and ``tp_init``
================================

The slot ``tp_init`` (for ``__init__``) holds no surprises:
it basically looks like ``tp_call``,
but returns ``void``.

The Python special method ``__new__``,
for which ``tp_new`` is the slot,
leads to an (effectively) static method.
It therefore does not have the "self type" in its signature,
but ``T``, standing for ``Class<? extends PyType>``.

..  code-block:: java

    enum Slot {
        ...
        tp_init(Signature.INIT), //
        tp_new(Signature.NEW), //

        enum Signature implements ClassShorthand {
            ...
            INIT(V, S, TUPLE, DICT), // (initproc) tp_init
            NEW(O, T, TUPLE, DICT); // (newfunc) tp_new
            ...
        }
        ...
    }

These are easily defined,
but the hard work is to add them to every built-in type.
Let's start with ``int``.


``__new__`` in ``PyType`` (Provisional)
=======================================

When we invoke the ``__call__`` special method of ``PyType``,
and the target ``PyType`` is ``type`` itself,
the ``__new__`` special method of ``type`` is invoked,
and we create a new type from the arguments supplied.
This convoluted situation needs careful thought,
based on successively approximating the class build process.

Consider the apparently trivial sequence:

..  code-block:: python

    C = type('C', (), {})
    c = C()

Here we call the constructor of ``type`` objects
to create a class called ``"C"``,
that for sanity's sake we assign to the variable ``C``.
This is to say we call ``type.__call__``,
and this in turn calls ``type.__new__``.
The arguments are the name, a tuple of bases and a name space
that would ordinarily be the result of executing
the body of a class definition.

We have seen ``type.__call__`` already,
but a provisional ``type.__new__`` runs like this:

..  code-block:: java

     static PyObject __new__(PyType metatype, PyTuple args, PyDict kwds)
                throws Throwable {

            // Special case: type(x) should return type(x)
            if (isTypeEnquiry(metatype, args, kwds)) {
                return args.get(0).getType();
            }

            // ... Process arguments to bases, name, namespace ...

            // Specify using provided material
            Spec spec = new Spec(name).namespace(namespace);
            for (PyObject t : bases) {
                if (t instanceof PyType)
                    spec.base((PyType) t);
            }

            return PyType.fromSpec(spec);
     }

After the clause where ``__new__`` checks to see if this is a type enquiry,
it creates a specification for the type,
and a type from that.
In CPython, ``type_new`` is 523 lines long,
so it is likely we have missed a few details,
but we do actually get a type object from this.

In the Python snippet,
we go on to call that type object to get an instance.
That works too, iof we don't look too hard.

One delicate question is how to choose the (Java) implementation class
of the new type.
For a built-in type we construct the ``Spec`` with a knowledge of the
implementation class.
The new type is a Python subclass of each of its bases
(or if that tuple is empty, as it is in the example, just of ``object``).
It must also be a Java sub-class of their implementation types,
so that any methods implemented in Java are applicable to it.
This creates a constraint on the selection of bases
that is the Java parallel to the dreaded "layout conflict".

Assuming ``PyBaseObject`` appears to work for this simple case,
but it doesn't get us far:
``C`` should have an instance dictionary and
``PyBaseObject`` (i.e. ``object``) doesn't.
The correct Java class is one that all the bases may extend,
and which may also have an instance dictionary (or slots, or both).


.. _instance-dictionary-python:

The Instance Dictionary
=======================

Support in ``PyObject``
-----------------------

It will be a frequent need to get the instance dictionary (in Java) from
a Python object, to look up attributes in it.
This includes the case where the object is a type object.
So we're going to add that facility to the interface ``PyObject``.

Now, it would be a mistake here to promise a reference to
a fully-functional ``PyDict``.
Some types of object (and ``type`` is one of them),
insist on controlling access to their members.
(``PyType`` has a lot of re-computing to do when attributes change,
so it needs to know when that happens.)
Although every ``type`` object has a ``__dict__`` member,
it is not as permissive as those found in objects of user-defined type.

..  code-block:: python

    >>> class C: pass

    >>> (c:=C()).__dict__['a'] = 42
    >>> c.a
    42
    >>> type(c.__dict__)
    <class 'dict'>
    >>> type(C.__dict__)
    <class 'mappingproxy'>
    >>> C.__dict__['a'] = 42
    Traceback (most recent call last):
      File "<pyshell#489>", line 1, in <module>
        C.__dict__['a'] = 42
    TypeError: 'mappingproxy' object does not support item assignment

We therefore need to accommodate instance "dictionaries"
that are ``dict``\-like, but may be a read-only proxy to the real,
potentially modifiable dictionary.
We now redefine:

..  code-block:: java

    interface PyObject {

        /** The Python {@code type} of this object. */
        PyType getType();

        /**
         * The dictionary of the instance, (not necessarily a Python
         * {@code dict} or writable.
         */
        default Map<PyObject, PyObject> getDict(boolean create) {
            return null;
        }
    }

An object may implement this additional method
by handing out an actual instance dictionary (a ``dict``),
or a proxy that manages access.

..  code-block:: java

    class PyDict extends LinkedHashMap<PyObject, PyObject>
            implements Map<PyObject, PyObject>, PyObject {
        // ...


The slightly clumsy ``create`` argument is intended to allow objects
that create their dictionary lazily,
to defer creation until a client intends to write something in it.


Read-only Dictionary (``PyType``)
---------------------------------

Where we need to ensure that a mapping handed out by an object
is not modified by the client,
we may use an implementation of ``getDict()`` that wraps it,
for example, if ``dict`` is the instance dictionary:

..  code-block:: java

        @Override
        public Map<PyObject, PyObject> getDict(boolean create) {
            return Collections.unmodifiableMap(dict);
        }

We do this in ``PyType``,
to prevent clients updating the dictionary directly.
The ``PyObject`` interface is public API,
as public as the ``__dict__`` attribute,
and therefore we cannot rely on clients to be well-behaved,
remembering to police their own use of the dictionary,
and triggering re-computation of the ``PyType`` after changes.

(It also prevents ``object.__setattr__`` being applied to a type,
since ``PyBaseObject.__setattr__`` uses this API.)

While built-in types generally do not allow attribute setting,
many user-defined instances of ``PyType`` allow it.
We can manage this because we give ``PyType`` a custom ``__setttr__``,
that inspects the flag that determines this kind of mutability,
and has private access to the type dictionary.
*All* type objects have to respond to changes to special methods
in their dictionary,
by updating type slots
and notifying sub-classes of (potentially) changed inheritance.
The custom ``__setttr__`` also makes sure that happens.

Since we have already strayed a long way into
the discussion of attribute access,
we turn to that next.



.. _class-creation-descr-python:

Descriptors in Class Creation (Python)
**************************************


..  note:: Section required on this, following Java version.




Integrating the Parts
*********************

Defining a Simple Class
=======================

Class definition turns out to begin with function definition:

..  code-block:: python

    >>> dis.dis(compile("class C : pass", '<test>', 'exec'))
      1           0 LOAD_BUILD_CLASS
                  2 LOAD_CONST               0 (<code object C at ... >)
                  4 LOAD_CONST               1 ('C')
                  6 MAKE_FUNCTION            0
                  8 LOAD_CONST               1 ('C')
                 10 CALL_FUNCTION            2
                 12 STORE_NAME               0 (C)
                 14 LOAD_CONST               2 (None)
                 16 RETURN_VALUE

    Disassembly of <code object C at ...>:
      1           0 LOAD_NAME                0 (__name__)
                  2 STORE_NAME               1 (__module__)
                  4 LOAD_CONST               0 ('C')
                  6 STORE_NAME               2 (__qualname__)
                  8 LOAD_CONST               1 (None)
                 10 RETURN_VALUE


We already have everything we need for this trivial example,
except for the new opcode ``LOAD_BUILD_CLASS``.
This opcode simply pushes the function ``__builtins__.__build_class__``,
that by default is in the ``builtins`` module.

The next instructions define a *function* object ``C``,
whose body is the *class* body
(defined by the code object also displayed).

Finally,
the function ``__build_class__`` is called with just two arguments:
the function object just defined, and the name of ``C``.
There is not much to the function body in this trivial case,
but it will get executed (not exactly called as a function),
within ``__build_class__``.
What it leaves behind in its ``locals()``,
essentially populates the dictionary of the type.


A First Approximation to ``__build_class__``
============================================

``__build_class__`` is quite complicated,
and quite likely we cannot implement it fully
with the type system as it stands.



..  code-block:: java


..  code-block:: java

