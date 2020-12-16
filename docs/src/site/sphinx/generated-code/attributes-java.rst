..  generated-code/attributes-java.rst


Attributes Defined in Java
##########################

Access to Attributes: A Trivial Case
************************************

Motivating Example
==================

First, however,
we turn to the definition of a Python object with attribute operations.

A hand-crafted class will serve the purpose,
like the one we used in :ref:`a-specialised-callable`,
if it returns an object capable of "attribute access".
And we can instantiate it from Java without the associated ``PyType``.


..  note::

    Need section where built-in types constructed and used
    from Java before this.
    Is this where we begin inheritance?
    We have limited examples of inheritance between built-ins.


..  _instance-creation-new-java:

Creating an Instance with ``__new__``
=====================================

At :ref:`custom-class-attribute-access`,
we devised a Java class ``C`` to demonstrate the slots.

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





***** Need to share with "attributes in Python":
here must create instance of type defined in Java.
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


``__new__`` in ``PyLong``
=========================

The CPython code behind ``int()`` is quite complicated,
and not very interesting in the present context,
except to say that it tries the ``nb_int`` and ``nb_index`` slots,
in the case of single arguments ``int(x)``,
and a conversion from text for string-like objects.
For the purpose of exploration,
the Very Slow Jython code base implements a subset of the functionality.

The following attempt at ``PyLong.__new__`` gives an idea,
but it does not deal with Python subclasses of ``int``.
The lines highlighted invoke, directly or indirectly,
a constructor of ``PyLong``.

..  code-block:: java
    :emphasize-lines: 8, 15

    static PyObject __new__(PyType type, PyTuple args, PyDict kwargs)
            throws Throwable {
        PyObject x = null, obase = null;

        // ... argument processing to x, obase

        if (obase == null)
            return Number.asLong(x);
        else {
            int base = Number.asSize(obase, null);
            if ((base != 0 && base < 2) || base > 36)
                throw new ValueError(
                        "int() base must be >= 2 and <= 36, or 0");
            else if (x instanceof PyUnicode)
                return new PyLong(new BigInteger(x.toString(), base));
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

The type object for ``int`` can be called from Java.
The test ``PyByteCode6.intFrom__new__`` does this
for a few of the possible constructor calls.

..  code-block:: java

    class PyByteCode6 {
        // ...
        @Test
        void intFrom__new__() throws Throwable {
            PyType intType = PyLong.TYPE;
            // int()
            PyObject result = Callables.call(intType);
            assertEquals(PyLong.ZERO, result);
            // int(42)
            result = Callables.call(intType, Py.tuple(Py.val(42)), null);
            assertEquals(Py.val(42), result);
            // int("2c", 15)
            PyTuple args = Py.tuple(Py.str("2c"), Py.val(15));
            result = Callables.call(intType, args, null);
            assertEquals(Py.val(42), result);
        }

In order to make the ``int`` constructor accessible from Python,
and the same for ``float`` (not detailed here),
it is only necessary to make them attributes of the ``builtins`` module:

..  code-block:: java

    class BuiltinsModule extends JavaModule implements Exposed {

        BuiltinsModule() {
            super("builtins");
            // ...
            add(ID.intern("float"), PyFloat.TYPE);
            add(ID.intern("int"), PyLong.TYPE);
        }

Now we can execute the following fragment within ``PyByteCode6``:

..  code-block:: python

    # Exercise the constructors for int and float
    i = int(u)
    x = float(i)
    y = float(u)
    j = int(y)

and this is done for a range of arguments ``u`` and ``i``.


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




.. _class-creation-descr-java:

Descriptors in Class Creation (Java)
************************************


..  note:: Section required on this, following Java version.



Integrating the Parts
*********************

Defining a Simple Class
=======================


..  note::

    Section required on this, following existing Python version.




..  code-block:: java


..  code-block:: java

