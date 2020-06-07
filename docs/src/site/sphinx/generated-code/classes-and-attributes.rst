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

They supersede earlier slots ``tp_getattr`` and ``tp_setattr`` (no "o"),
now deprecated but still present in CPython for backwards compatibility,
that name the attribute by a ``char *``.
Although it is tempting to us to use a ``String`` as the argument,
we'll follow CPython down the ``PyObject`` route,
since the name will usually be the key in a subsequent dictionary look-up.
We will, however,
strongly type the name argument as ``PyUnicode``,
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

As usual,
these slots are wrapped in abstract methods
so that we may call them from Java,
including from the implementation of the opcodes.
In CPython,
the abstract method wrapping ``tp_getattro`` is:

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

Note that CPython must support the legacy slot ``tp_getattr`` as well.
Note also the ``PyUnicode_Check(name)``: we'll refer to this again shortly.
There's also a ``PyObject_GetAttrString(PyObject *v, const char *name)``
that accepts the name as a ``char *`` and tries the legacy slot first.
If the legacy slot is not defined,
it creates a temporary ``str`` object and calls ``PyObject_GetAttr``.

Our version is:

..  code-block:: java

        /** Python {@code o.name}. */
        static PyObject getAttr(PyObject o, PyUnicode name)
                throws Throwable {
            try {
                return (PyObject) o.getType().tp_getattro.invokeExact(o,
                        name);
            } catch (EmptyException e) {
                throw noAttributeError(o, name);
            }
        }

In most contexts,
we expect it to be known statically that the name is a ``PyUnicode``,
and so the type check that CPython feels necessary may be avoided.
In particular, this applies to the implementation of the opcode:

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

Two alternative signatures cover cases where the type of the name is not
known statically to be ``PyUnicode`` or is a ``String``.

..  code-block:: java

        static PyObject getAttr(PyObject o, String name) throws Throwable {
            return getAttr(o, Py.str(name));
        }

        static PyObject getAttr(PyObject o, PyObject name)
                throws Throwable {
            if (name instanceof PyUnicode) {
                return getAttr(o, (PyUnicode) name);
            } else {
                throw new TypeError(ATTR_MUST_BE_STRING_NOT, name);
            }
        }

The ``String`` case is convenient in Java code
because we may supply a string literal as in ``Abstract.getAttr(c, "x")``,
but it raises an interesting question of optimisation.
At each call, we create and dispose of a ``PyUnicode`` object.
If the name is (an interned) ``String`` at the call site,
might we not be better off with a ``static`` or interned ``PyUnicode``?

..  note:: Can interning be automatic in the code,
    or must it be up to the caller to remember?
    In the latter case,
    the client calls ``getAttr(PyObject o, PyUnicode name)`` anyway,
    and ``getAttr(PyObject o, String name)``
    is more of a trap than a helper.


.. _a-custom-class-constructor:

A Custom Class and its Constructor
==================================

A class exhibiting these slots is as follows:

..  code-block:: java

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
We call it all like this (in a JUnit test):

..  code-block:: java

        @Test
        void abstract_attr() throws Throwable {
            PyObject c = new C();
            Abstract.setAttr(c, "x", Py.val(42));
            PyObject result = Abstract.getAttr(c, "x");
            assertEquals(Py.val(42), result);
        }


Making ``PyType`` callable
**************************

We must define the ``tp_call`` slot in ``PyType``,
and also a new slot ``tp_new``
defined in all types we expect to instantiate this way.



..  code-block:: java


..  code-block:: java



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

