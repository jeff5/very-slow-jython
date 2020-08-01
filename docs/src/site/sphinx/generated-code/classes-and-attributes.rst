..  generated-code/classes-and-attributes.rst


Classes and Attributes
######################

Access to attributes
********************

Motivating Example
==================

Consider the simple statement ``a = C().x``,
where we construct an object and access a member.
We have not yet explored how to define classes and create instances,
but it is worth looking at the code CPython produces::

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


Slots for Attribute Access
==========================

The type must implement two new slots comparable to those in CPython.
These are ``tp_getattro`` and ``tp_setattro``.
These slots name the attribute by an object (always a ``str``).
We will strongly type the name argument as ``PyUnicode``,
adding the following slots and signatures:

..  code-block:: java

    enum Slot {
        ...
        tp_getattro(Signature.GETATTRO), //
        tp_setattro(Signature.SETATTRO), //
        ...
        enum Signature implements ClassShorthand {
            ...
            GETATTRO(O, S, U), // (getattrofunc) tp_getattro
            SETATTRO(V, S, U, O); // (setattrofunc) tp_setattro

``U`` is a shorthand for ``PyUnicode``, defined in ``ClassShorthand``.
We also add ``tp_getattro`` and ``tp_setattro`` slots to ``PyType``,
but no new apparatus is required in that class.

CPython has slots in addition to the ones we reproduce here,
called ``tp_getattr`` and ``tp_setattr`` (no "o"),
that name the attribute by a ``char *``.
These are the legacy of a now deprecated earlier approach
where attribute access had only that signature.
We only implement the newer form.

As usual, the new slots are wrapped in abstract methods
so that we may call them from Java,
including from the implementation of the opcodes.
In CPython,
the abstract method wrapping ``tp_getattro`` is like this:

..  code-block:: c
    :emphasize-lines: 6, 12-13

    PyObject *
    PyObject_GetAttr(PyObject *v, PyObject *name)
    {
        PyTypeObject *tp = Py_TYPE(v);

        if (!PyUnicode_Check(name)) {
            PyErr_Format(PyExc_TypeError,
                         "attribute name must be string, not '%.200s'",
                         name->ob_type->tp_name);
            return NULL;
        }
        if (tp->tp_getattro != NULL)
            return (*tp->tp_getattro)(v, name);
        if (tp->tp_getattr != NULL) {
            const char *name_str = PyUnicode_AsUTF8(name);
            if (name_str == NULL)
                return NULL;
            return (*tp->tp_getattr)(v, (char *)name_str);
        }
        PyErr_Format(PyExc_AttributeError,
                     "'%.50s' object has no attribute '%U'",
                     tp->tp_name, name);
        return NULL;
    }

Note that CPython falls back on the legacy slot ``tp_getattr``.
We will discuss the ``PyUnicode_Check(name)`` shortly.

There's also a ``PyObject_GetAttrString(PyObject *v, const char *name)``
that accepts the name as a ``char *`` and tries the legacy slot first.
If the legacy slot is not defined,
it creates a temporary ``str`` object and calls ``PyObject_GetAttr``.

Our version (strongly typed to ``PyUnicode``) is:

..  code-block:: java

        /** Python {@code o.name}. */
        static PyObject getAttr(PyObject o, PyUnicode name)
                throws Throwable {
            try {
                MethodHandle getattro = o.getType().tp_getattro;
                return (PyObject) getattro.invokeExact(o, name);
            } catch (EmptyException e) {
                throw noAttributeError(o, name);
            }
        }

In most contexts,
we expect it to be known statically that the name is a ``PyUnicode``,
and so the type check that CPython feels necessary may be avoided.
In particular,
this applies to the implementation of the ``LOAD_ATTR`` opcode:

..  code-block:: java

        PyObject eval() {
            ...
            // Cached references from code
            PyUnicode[] names = code.names;
            ...
                        case Opcode.LOAD_ATTR: // v.name
                            v = valuestack[sp - 1];
                            valuestack[sp - 1] =
                                    Abstract.getAttr(v, names[oparg]);
                            break;

An alternative signature covers cases where the type of the name is not
known statically to be ``PyUnicode``.

..  code-block:: java

        static PyObject getAttr(PyObject o, PyObject name)
                throws Throwable {
            if (name instanceof PyUnicode) {
                return getAttr(o, (PyUnicode) name);
            } else {
                throw new TypeError(ATTR_MUST_BE_STRING_NOT, name);
            }
        }

A ``String`` case would be convenient when writing Java code,
but this is a trap leading to inefficiency.
The ``char *`` form is deprecated in CPython for a good reason:
at each call we would have to create and dispose of a ``PyUnicode`` object.
We use ``Py.str(name)`` instead at such a call site,
and make a static constant of it if the same name will be used repeatedly.

There is a ``setAttr`` to complement ``getAttr``,
but the implementation is obvious.

.. _a-custom-class-constructor:

A Custom Class with Attribute Access
====================================

A class exhibiting these slots,
and giving access to a single attribute ``x``,
is as follows:

..  code-block:: java
    :emphasize-lines: 10, 12, 21

        @SuppressWarnings("unused")
        private static class C implements PyObject {

            static final PyType TYPE =
                    PyType.fromSpec(new PyType.Spec("00C", C.class));

            @Override
            public PyType getType() { return TYPE; }

            PyObject x;         // Attribute for test

            static PyObject tp_getattro(C self, PyUnicode name)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n) && self.x != null)
                    return self.x;
                else
                    throw Abstract.noAttributeError(self, name);
            }

            static void tp_setattro(C self, PyUnicode name, PyObject value)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n))
                    self.x = value;
                else
                    throw Abstract.noAttributeError(self, name);
            }
        }

There is no proper attribute look-up going on.
We test the name, and if it is exactly "x",
then we get or set the attribute.
We call it all like this (in a JUnit test),
exercising the abstract method ``getAttr``
that also supports the ``LOAD_ATTR`` opcode:

..  code-block:: java

        @Test
        void abstract_attr() throws Throwable {
            PyObject c = new C();
            Abstract.setAttr(c, "x", Py.val(42));
            PyObject result = Abstract.getAttr(c, "x");
            assertEquals(Py.val(42), result);
        }

Common built-ins do not provide for attributes added by client code,
that is,
they have no instance dictionary.
In order to experiment with simple attributes,
we must first address class and object creation.
This is is inseparable from sub-classing.
Suddenly, we have a lot on our agenda.


Making ``PyType`` callable
**************************

We must define the ``tp_call`` slot in ``PyType``,
so that anything that is a type can be called to make an instance.


``tp_call`` in ``PyType``
=========================

``PyType.tp_call`` is actually fairly simple,
but it depends on two other new slots.
The body of this method invokes the new slot ``tp_new``,
which returns a new object,
followed optionally by ``tp_init`` on the object itself.
``tp_new`` must be defined or inherited
by all types we expect to instantiate this way.

..  code-block:: java

    class PyType implements PyObject {
        ...
        static PyObject tp_call(PyType type, PyTuple args, PyDict kwargs)
                throws Throwable {
            try {
                // Create the instance with given arguments.
                MethodHandle n = type.tp_new;
                PyObject o = (PyObject) n.invokeExact(type, args, kwargs);
                // Check for special case type enquiry.
                if (isTypeEnquiry(type, args, kwargs)) { return o; }
                // As __new__ may be user-defined, check type as expected.
                PyType oType = o.getType();
                if (oType.isSubTypeOf(type)) {
                    // Initialise the object just returned (in necessary).
                    if (Slot.tp_init.isDefinedFor(oType))
                        oType.tp_init.invokeExact(o, args, kwargs);
                }
                return o;
            } catch (EmptyException e) {
                throw new TypeError("cannot create '%.100s' instances",
                        type.name);
            }
        }
        ...
    }

The code must take into account that ``type`` is itself a type,
but the call ``type(x)`` enquires the type of ``x``,
rather than being a constructor.
(The call ``type(name, bases, dict)`` does construct a ``type`` however.)
This difference is detected from the number of arguments by
``isTypeEnquiry(type, args, kwargs)``.
We follow CPython in placing the test after ``tp_new`` is invoked,
which performs both functions.


Slots ``tp_new`` and ``tp_init``
================================

The slot ``tp_init`` (for ``__init__``) holds no surprises:
it basically looks like ``tp_call``,
but returns ``void``.

The Python special method ``__new__``,
which ``tp_new`` implements,
is (effectively) a static method.
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


``tp_new`` in ``PyLong``
========================

The CPython code behind ``int()`` is quite complicated,
and not very interesting in the present context,
except to say that it tries the ``nb_int`` and ``nb_index`` slots,
in the case of single arguments ``int(x)``,
and a conversion from text for string-like objects.
For the purpose of exploration,
the Very Slow Jython code base implements a subset of the functionality.

The following attempt at ``PyLong.tp_new`` gives an idea,
but it does not deal with Python subclasses of ``int``.
The lines highlighted invoke, directly or indirectly,
a constructor of ``PyLong``.

..  code-block:: java
    :emphasize-lines: 8, 15

    static PyObject tp_new(PyType type, PyTuple args, PyDict kwargs)
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

We cannot yet use this from Python code,
but the type object for ``int`` can be called from Java.
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



``tp_new`` in ``PyType``
========================

When we invoke the ``tp_call`` slot of ``PyType``,
and the target ``PyType`` is ``type`` itself,
the ``tp_new`` slot of ``type`` is invoked,
and we create a new type from the arguments supplied.
This convoluted situation needs careful thought,
based on successively approximating the class build process.


Defining a Class
****************

Defining a Trivial Class
========================

Class definition turns out to begin with function definition::

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

