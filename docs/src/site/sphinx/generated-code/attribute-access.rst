..  generated-code/attribute-access.rst


Access to Attributes
####################

We use the term "attribute" to cover anything that would be
accessed in Python with the "dot" notation.
It may be imagined that this is mostly a matter of
looking up a name in a dictionary.
A dictionary is involved, but which dictionary,
and what Python does with the thing it finds,
brings us to the complex topic of :ref:`Descriptors`.

This section looks at how a Python implementation in Java would
access attributes,
including those located by a descriptor.

It is difficult to treat this as a journey of discovery.
Python provides a set of interconnected concepts that
have to be implemented together,
which are here progressively revealed.
The next two sections will look at how to implement descriptors
for attributes of Python objects implemented in Java or Python.
The approach to attributes of "found" Java objects
(objects written without the explicit intention they be Python objects)
is for yet another section.

We begin our exploration with the question
of what it means to access an attribute.


Exploration
***********

We start with a built-in type that has an attribute we can get and set.
It is possible to assign the qualified name of a function
after it is defined:

..  code-block:: python

    >>> def f(): pass
    ...
    >>> f.__qualname__
    'f'
    >>> f.__qualname__ = 'Fred'
    >>> f
    <function Fred at 0x000001FE3F821CA0>

Now look at the code CPython produces as we do that:

..  code-block:: python

    >>> dis.dis(compile("f.__qualname__; f.__qualname__='Fred'", '', 'exec'))
      1           0 LOAD_NAME                0 (f)
                  2 LOAD_ATTR                1 (__qualname__)
                  4 POP_TOP
                  6 LOAD_CONST               0 ('Fred')
                  8 LOAD_NAME                0 (f)
                 10 STORE_ATTR               1 (__qualname__)
                 12 LOAD_CONST               1 (None)
                 14 RETURN_VALUE

Evidently the required new opcodes are ``LOAD_ATTR`` and ``STORE_ATTR``.
No doubt we shall have type slots to support them,
and a contribution to the abstract object API supporting the interpreter.

The attribute name comes from the constant pool of the code object,
so it is a ``PyUnicode`` in our terms.
There is some historical baggage in the CPython API that allows
string (that is, ``char *``) names to be supplied by C (extension) code,
but we do not have to reproduce that feature.


Slots for Attribute Access
==========================

The type must implement new slots comparable to those in CPython.
We call them ``op_getattribute``, ``op_getattr`` and ``tp_setattro``.
We add ``op_delattr`` to the set for reasons we explain shortly.
These special methods expect the name of the attribute as an object
(always a ``str``).
We will strongly type the name argument as ``PyUnicode``,
adding the following slots and signatures:

..  code-block:: java

    enum Slot {
        ...
        op_getattribute(Signature.GETATTR),
        op_getattr(Signature.GETATTR),
        op_setattr(Signature.SETATTR),
        op_delattr(Signature.DELATTR),
        ...
        enum Signature implements ClassShorthand {
            ...
            GETATTR(O, S, U),
            SETATTR(V, S, U, O),
            DELATTR(V, S, U),

``U`` is a shorthand for ``PyUnicode``, defined in ``ClassShorthand``.
We also add ``op_getattribute``, ``op_getattr``,
``op_setattr`` and ``op_delattr`` slots to ``PyType``,
but no new apparatus is required in that class to accomplish that.

To understand why Python needs both ``op_getattribute`` and ``op_getattr``,
see :ref:`getattribute-and-getattr`.

CPython does not have a separate slot for deletion operations:
it uses the ``tp_setttro`` slot (same as ``op_setattr``)
with a value to be assigned of ``NULL``.
However,
Python defines ``__del__`` operations separately in the data model.
The tension is the source of some minor complexity in CPython
that we choose to avoid.

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


.. _candidate-getattr:

Candidate ``getAttr``
---------------------

As usual, we take advantage of Java namespaces to choose a shorter name.
A candidate ``getAttr`` (strongly typed to ``PyUnicode``) is:

..  code-block:: java

        /** {@code o.name} with Python semantics. */
        static PyObject getAttr(PyObject o, PyUnicode name)
                throws AttributeError, Throwable {
            PyType t = o.getType();
            try {
                // Invoke __getattribute__.
                return (PyObject) t.op_getattribute.invokeExact(o, name);
            } catch (EmptyException) {
                throw noAttributeError(o, name);
            }
        }

In fact, this is a slight over-simplification
as we shall see in :ref:`getattribute-and-getattr`.

In most contexts,
we expect it to be known statically that the name is a ``PyUnicode``,
and so the type check that CPython feels necessary may be avoided.
In particular,
this benefits the implementation of the ``LOAD_ATTR`` opcode:

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

The ``names`` array is known to be a ``PyUnicode[]``.
An alternative signature covers cases where the type of the name is not
known statically to be ``PyUnicode``.

..  code-block:: java

        static PyObject getAttr(PyObject o, PyObject name)
                throws AttributeError, TypeError, Throwable {
            if (name instanceof PyUnicode) {
                return getAttr(o, (PyUnicode) name);
            } else {
                throw attributeNameTypeError(name);
            }
        }

A ``String`` case would be convenient when writing Java code,
but this is a trap when it comes to efficiency:
it involves making a ``PyUnicode`` every time we call it.
(The equivalent ``char *`` option exists in CPython,
but the CPython source itself avoids using it.)
We use an explicit call to ``Py.str`` for ephemeral values
or constant interned in ``ID`` when built-in names are involved.

There is a ``setAttr`` to complement the candidate ``getAttr``,
with an easily-guessed implementation.



.. _custom-class-attribute-access:

A Custom Class with Attribute Access
====================================

******* here


A class exhibiting these slots,
and giving access to a single attribute ``x``,
is as follows:

..  code-block:: java
    :emphasize-lines: 9, 11, 20

        private static class C implements PyObject {

            static final PyType TYPE =
                    PyType.fromSpec(new PyType.Spec("00C", C.class));

            @Override
            public PyType getType() { return TYPE; }

            PyObject x;         // Attribute for test

            static PyObject __getattribute__(C self, PyUnicode name)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n) && self.x != null)
                    return self.x;
                else
                    throw Abstract.noAttributeError(self, name);
            }

            static void __setattr__(C self, PyUnicode name, PyObject value)
                    throws Throwable {
                String n = name.toString();
                if ("x".equals(n))
                    self.x = value;
                else
                    throw Abstract.noAttributeError(self, name);
            }

            static PyObject __new__(PyType cls, PyTuple args, PyDict kwargs) {
                return new C();
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
            Abstract.setAttr(c, Py.str("x"), Py.val(42));
            PyObject result = Abstract.getAttr(c, Py.str("x"));
            assertEquals(Py.val(42), result);
        }

In general we shall need to give object instance their dictionaries,
and absolutely all types have one,
so we examine that next.


.. _instance-dictionary:

The Instance Dictionary
=======================

Support in ``PyObject``
-----------------------

It will be a frequent need to get the instance dictionary (in Java) from
a Python object, to look up attributes in it.
This includes the case where the object is a ``type`` object.
So we're going to add that facility to the interface ``PyObject``.

..  note::

    An alternative approach is possible in which only ``getType()``
    is provided,
    but the ``PyType`` provides the means to access the instance dictionary
    (if there is one).
    This would resemble more fully the CPython ``tp_dictoffset`` slot,
    and is necessary to the ``Object``\-not-``PyObject`` paradigm.

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
that are ``dict``\-like, but may be a read-only proxy to the dictionary.
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

The slightly clumsy ``create`` argument is intended to allow objects
that create their dictionary lazily
to defer creation until a client intends to write something in it.

An object may implement this additional method
by handing out an actual instance dictionary (a ``dict``),
since ``PyDict`` implements ``Map<PyObject, PyObject>``,
or a proxy that manages access with this interface.

..  code-block:: java

    class PyDict extends LinkedHashMap<PyObject, PyObject>
            implements PyObject {
        // ...



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


The Mechanism of Attribute Access
*********************************

.. _getattribute-and-getattr:

``__getattribute__`` and ``__getattr__``
========================================

Built-in classes in CPython usually fill the ``tp_getattro`` slot
with ``PyObject_GenericGetAttr`` in ``object.c``,
directly or by inheritance.
The slot is exposed as ``__getattribute__``.

``PyObject_GenericGetAttr`` consults the type of target object
and the instance dictionary of the object,
in the order defined by the Python data model.

The situation is similar for Python-defined types.
In the :ref:`candidate-getattr`,
we showed a simplified custom ``getAttr()``
sufficient for the example that preceded it.
It matches the CPython ``PyObject_GenericGetAttr``,
but CPython is hiding a trick.

Before Python 2.2,
a type defined in Python would customise attribute access
by defining the special method ``__getattr__``.
That method would be called when the built-in mechanism
failed to resolve the attribute name.
At Python 2.2,
the language introduced ``__getattribute__`` as a way to give
types defined in Python complete control over attribute access,
but the hook ``__getattr__`` continues to be supported.
For the history of the change, consult `Attribute access in Python 2.2`_,
and earlier versions.

The `Python Data Model`_ states that
"if the class also defines ``__getattr__()``,
the latter will not be called unless ``__getattribute__()`` either
calls it explicitly or raises an ``AttributeError``".
However, there is no sign of this in either ``object.__getattribute__``
(which is the C function ``PyObject_GenericGetAttr``)
or ``PyObject_GetAttr`` (in the abstract API).

In CPython,
this is accomplished at almost no cost by setting ``tp_getattro``,
in classes defined in Python,
to a function ``slot_tp_getattr_hook`` that calls ``__getattribute__``,
and if that raises ``AttributeError`` catches it, and calls ``__getattr__``.
The CPython trick is that this hook method,
upon once finding that ``__getattr__`` is not defined,
replaces itself in the slot with a simplified version ``slot_tp_getattro``
that only looks for ``__getattribute__``.
If ``__getattr__`` is subsequently added to a class,
the re-working of the type slots that follows an attribute change
re-inserts ``slot_tp_getattr_hook``.


..  _Attribute access in Python 2.2:
    https://docs.python.org/3/whatsnew/2.2.html#attribute-access

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html


A Java Approach
---------------

In CPython, the mechanism we are looking for
has been cleverly folded into the slot function.
We could do this in the ``MethodHandle``,
but we choose a greater transparency at the cost of an extra slot.
We shall have two slots ``op_getattribute`` and ``op_getattr``,
and put the mechanism for choosing between them in ``Abstract.getAttr``:

..  code-block:: java

        static PyObject getAttr(PyObject o, PyUnicode name)
                throws AttributeError, Throwable {
            try {
                // Invoke __getattribute__.
                return (PyObject) t.op_getattribute.invokeExact(o, name);
            } catch (EmptyException | AttributeError e) {
                try {
                    // Not found or not defined: fall back on __getattr__.
                    return (PyObject) t.op_getattr.invokeExact(o, name);
                } catch (EmptyException ignored) {
                    // __getattr__ not defined, original exception stands.
                    if (e instanceof AttributeError) { throw e; }
                    throw noAttributeError(o, name);
                }
            }
        }

This will carry no run-time cost where ``__getattribute__`` succeeds,
and only a small one if it raises ``AttributeError``
and ``__getattr__`` is not defined.

The difference in slots from CPython
will be visible wherever ``tp_getattro`` is referenced directly.
In ported code, it should probably be converted to ``op_getattribute``,
and it may be appropriate to fall back to ``op_getattr`` in the code.
All the examples of this are in the implementation of attribute access.
In our implementation,
the ``Slot``\s are not API, and so this is an internal matter.


.. _descriptors-in-concept:

Descriptors in Concept
======================

There is a long discussion of the different types of descriptor
in the architecture section :ref:`Descriptors`.
The short version is that a descriptor is
an object that defines the slot function ``__get__``,
and may also define ``__set__`` and ``__delete__``.
If it also defines ``__set__`` or ``__delete__`` it is a data descriptor.

A descriptor may appear in the dictionary of a type object.

When looking for an attribute on an object,
the dictionary of the type object is consulted first.
The type may, in the end, supply a simple value for the attribute,
as when a variable or constant defined in the class body
is referenced via the instance.
However,
the search for an attribute via the type will often find a descriptor,
and the ``__get__``, ``__set__`` or ``__delete__``,
according to the action requested,
will then take control of the getting, setting or deletion.

Most attributes of built-in types are mediated this way,
and it is especially important in the way that methods are bound
before being called.
That descriptors are executed in the course of attribute access,
is critical to a full understanding of the implementations of
``__getattribute__``, ``__setattr__`` and ``__delattr__``
in the coming sections.


.. _object-getattribute:

Implementing ``object.__getattribute__``
========================================

The standard implementation of ``__getattribute__`` is in ``PyBaseObject``.
The special function (type slot) it produces
is inherited by almost all built-in and user-defined classes.
It fills the type slot ``op_getattribute``.

The code speaks quite well for itself.
It is adapted from the CPython ``PyObject_GenericGetAttr`` in ``object.c``,
taking account of our different approach to error handling,
and with the removal of some efficiency tricks.
There is some delicacy around which exceptions should be caught,
and the next source be consulted,
and which should put a definitive end to the attempt.

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...

        static PyObject __getattribute__(PyObject obj, PyUnicode name)
                throws AttributeError, Throwable {

            PyType objType = obj.getType();
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            PyObject typeAttr = objType.lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor
                PyType typeAttrType = typeAttr.getType();
                descrGet = typeAttrType.op_get;
                if (typeAttrType.isDataDescr()) {
                    // typeAttr is a data descriptor so call its __get__.
                    try {
                        return (PyObject) descrGet.invokeExact(typeAttr,
                                obj, objType);
                    } catch (Slot.EmptyException e) {
                        /*
                         * We do not catch AttributeError: it's definitive.
                         * The slot shouldn't be empty if the type is marked
                         * as a descriptor (of any kind).
                         */
                        throw new InterpreterError(
                                Abstract.DESCR_NOT_DEFINING, "data",
                                "__get__");
                    }
                }
            }

            /*
             * At this stage: typeAttr is the value from the type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the object instance dictionary a chance.
             */
            Map<PyObject, PyObject> dict = obj.getDict(false);
            PyObject instanceAttr;
            if (dict != null && (instanceAttr = dict.get(name)) != null) {
                // Found something
                return instanceAttr;
            }

            /*
             * The name wasn't in the instance dictionary (or there wasn't
             * an instance dictionary). We are now left with the results of
             * look-up on the type.
             */
            if (descrGet != null) {
                // typeAttr may be a non-data descriptor: call __get__.
                try {
                    return (PyObject) descrGet.invokeExact(typeAttr, obj,
                            objType);
                } catch (Slot.EmptyException e) {}
            }

            if (typeAttr != null) {
                /*
                 * The attribute obtained from the meta-type, and that
                 * turned out not to be a descriptor, is the return value.
                 */
                return typeAttr;
            }

            // All the look-ups and descriptors came to nothing :(
            throw Abstract.noAttributeError(obj, name);
        }


.. _object-setattr:

Implementing ``object.__setattr__``
===================================

The approach to ``__delattr__`` and ``__setattr__``
differs from the implementation in CPython.
``__delattr__`` definitely exists separately in the Python data model,
but in CPython both compete for the ``tp_setattro`` slot.
CPython funnels both source-level operations (assignment and deletion)
into ``PyObject_SetAttr`` with deletion indicated by a ``null``
as the value to be assigned.
When definitions of ``__delattr__`` and ``__setattr__`` exist in Python,
CPython's synthetic type-slot function chooses which to call
based on the nullity of the value.

Our approach reflects a design policy of one special function per type slot.
It simplifies the logic (fewer if statements),
although it means a little more code as we have separate methods.

The standard implementation of ``__setattr__`` is as follows:

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...

        static void __setattr__(PyObject obj, PyUnicode name,
                PyObject value) throws AttributeError, Throwable {

            // Accommodate CPython idiom that set null means delete.
            if (value == null) {
                // Do this to help porting. Really this is an error.
                __delattr__(obj, name);
                return;
            }

            // Look up the name in the type (null if not found).
            PyObject typeAttr = obj.getType().lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                PyType typeAttrType = typeAttr.getType();
                if (typeAttrType.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        typeAttrType.op_set.invokeExact(typeAttr, obj,
                                value);
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Descriptor but no __set__: do not fall through.
                        throw Abstract.readonlyAttributeError(obj, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will place the value in
             * the object instance dictionary directly.
             */
            Map<PyObject, PyObject> dict = obj.getDict(true);
            if (dict == null) {
                // Object has no dictionary (and won't support one).
                if (typeAttr == null) {
                    // Neither had the type an entry for the name.
                    throw Abstract.noAttributeError(obj, name);
                } else {
                    /*
                     * The type had either a value for the attribute or a
                     * non-data descriptor. Either way, it's read-only when
                     * accessed via the instance.
                     */
                    throw Abstract.readonlyAttributeError(obj, name);
                }
            } else {
                try {
                    // There is a dictionary, and this is a put.
                    dict.put(name, value);
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            }
        }


.. _object-delattr:

Implementing ``object.__delattr__``
===================================

The standard ``object.__delattr__`` is not much different from
``object.__setattr__``.
If we find a data descriptor in the type,
we call its ``op_delete`` slot
in place of ``op_set`` in ``__setattr__``.
Not only have we a distinct slot for ``__delattr__`` in objects,
we have one for ``__delete__`` in descriptors too.

Note the way ``isDataDescr()`` is used
in both ``__setattr__`` and ``__delattr__``
in deciding whether to call the descriptor:
a descriptor is a data descriptor if it defines
*either* ``__set__`` or ``__delete__``.
It need not define both.

It is therefore possible to find a data descriptor in the type,
and then find the necessary slot empty.
This is raises an ``AttributeError``:
we should not go on to try the instance dictionary.
In these circumstances CPython also raises an attribute error,
but from within the slot function (and with less helpful text).

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...
        static void __delattr__(PyObject obj, PyUnicode name)
                throws AttributeError, Throwable {

            // Look up the name in the type (null if not found).
            PyObject typeAttr = obj.getType().lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                PyType typeAttrType = typeAttr.getType();
                if (typeAttrType.isDataDescr()) {
                    // Try descriptor __delete__
                    try {
                        typeAttrType.op_delete.invokeExact(typeAttr, obj);
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Data descriptor but no __delete__.
                        throw Abstract.mandatoryAttributeError(obj, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will remove the name from
             * the object instance dictionary directly.
             */
            Map<PyObject, PyObject> dict = obj.getDict(true);
            if (dict == null) {
                // Object has no dictionary (and won't support one).
                if (typeAttr == null) {
                    // Neither has the type an entry for the name.
                    throw Abstract.noAttributeError(obj, name);
                } else {
                    /*
                     * The type had either a value for the attribute or a
                     * non-data descriptor. Either way, it's read-only when
                     * accessed via the instance.
                     */
                    throw Abstract.readonlyAttributeError(obj, name);
                }
            } else {
                try {
                    // There is a dictionary, and this is a delete.
                    PyObject previous = dict.remove(name);
                    if (previous == null) {
                        // A null return implies it didn't exist
                        throw Abstract.noAttributeError(obj, name);
                    }
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            }
        }




.. _type-getattribute:

Implementing ``type.__getattribute__``
======================================

The type object gets its own definition of ``__getattribute__``,
slightly different from that in ``object``,
and found in ``PyType.__getattribute__``.
We highlight the differences here.

A type has a type, called the meta-type.
This occasions a change of variable names, even where the code is the same:
where in ``PyBaseObject`` we had ``obj``, in ``PyType`` we write ``type``,
and where we had ``typeAttr``, we write ``metaAttr``.

..  code-block:: java
    :emphasize-lines: 39, 41-54

    class PyType implements PyObject {
        //...
        protected PyObject __getattribute__(PyUnicode name)
                throws AttributeError, Throwable {

            PyType metatype = getType();
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            PyObject metaAttr = metatype.lookup(name);
            if (metaAttr != null) {
                // Found in the metatype, it might be a descriptor
                PyType metaAttrType = metaAttr.getType();
                descrGet = metaAttrType.op_get;
                if (metaAttrType.isDataDescr()) {
                    // metaAttr is a data descriptor so call its __get__.
                    try {
                        // Note the cast of 'this', to match op_get
                        return (PyObject) descrGet.invokeExact(metaAttr,
                                (PyObject) this, metatype);
                    } catch (Slot.EmptyException e) {
                        /*
                         * We do not catch AttributeError: it's definitive.
                         * The slot shouldn't be empty if the type is marked
                         * as a descriptor (of any kind).
                         */
                        throw new InterpreterError(
                                Abstract.DESCR_NOT_DEFINING, "data",
                                "__get__");
                    }
                }
            }

            /*
             * At this stage: metaAttr is the value from the meta-type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the type's instance dictionary a chance.
             */
            PyObject attr = lookup(name);
            if (attr != null) {
                // Found in this type. Try it as a descriptor.
                try {
                    /*
                     * Note the args are (null, this): we respect
                     * descriptors in this step, but have not forgotten we
                     * are dereferencing a type.
                     */
                    return (PyObject) attr.getType().op_get
                            .invokeExact(attr, (PyObject) null, this);
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Not a descriptor: the attribute itself.
                    return attr;
                }
            }

            /*
             * The name wasn't in the type dictionary. We are now left with
             * the results of look-up on the meta-type.
             */
            if (descrGet != null) {
                // metaAttr may be a non-data descriptor: call __get__.
                try {
                    return (PyObject) descrGet.invokeExact(metaAttr,
                            (PyObject) this, metatype);
                } catch (Slot.EmptyException e) {}
            }

            if (metaAttr != null) {
                /*
                 * The attribute obtained from the meta-type, and that
                 * turned out not to be a descriptor, is the return value.
                 */
                return metaAttr;
            }

            // All the look-ups and descriptors came to nothing :(
            throw Abstract.noAttributeError(this, name);
        }

As with regular objects,
the first step is to acceess the type (that is the meta-type),
and if we find a data descriptor, act on it.
The second option is again to look in the instance (that is, the type),
but here we use ``type.lookup(name)``, in place of a dictionary look-up,
and must also be ready to find a descriptor rather than a plain value.

If we find a descriptor, we call it with arguments ``(null, type)``.
A descriptor called so will most often return itself,
making this the same as retrieving the plain value,
but an exception is the descriptor of a class method
(see :ref:`PyClassMethodDescr`),
which returns the method bound to the type.


.. _type-setattr:

Implementing ``type.__setattr__``
=================================

The definition of ``type.__setattr__``
is also slightly different from that in ``object``.
First we must deal with the possibility that
the type does not allow its attributes to be changed.
Most built-in types are in that category,
while most classes defined in Python (sub-classes of ``object``)
do allow this.

..  code-block:: java
    :emphasize-lines: 14-15, 23, 35, 51

    class PyType implements PyObject {
        //...
        protected void __setattr__(PyUnicode name, PyObject value)
                throws AttributeError, Throwable {

            // Accommodate CPython idiom that set null means delete.
            if (value == null) {
                // Do this to help porting. Really this is an error.
                __delattr__(name);
                return;
            }

            // Trap immutable types
            if (!flags.contains(Flag.MUTABLE))
                throw Abstract.cantSetAttributeError(this);

            // Force name to actual str , not just a sub-class
            if (name.getClass() != PyUnicode.class) {
                name = Py.str(name.toString());
            }

            // Check to see if this is a special name
            boolean special = isDunderName(name);

            // Look up the name in the meta-type (null if not found).
            PyObject metaAttr = getType().lookup(name);
            if (metaAttr != null) {
                // Found in the meta-type, it might be a descriptor.
                PyType metaAttrType = metaAttr.getType();
                if (metaAttrType.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        metaAttrType.op_set.invokeExact(metaAttr,
                                (PyObject) this, value);
                        if (special) { updateAfterSetAttr(name); }
                        return;
                    } catch (Slot.EmptyException e) {
                        // We do not catch AttributeError: it's definitive.
                        // Descriptor but no __set__: do not fall through.
                        throw Abstract.readonlyAttributeError(this, name);
                    }
                }
            }

            /*
             * There was no data descriptor, so we will place the value in
             * the object instance dictionary directly.
             */
            // Use the privileged put
            dict.put(name, value);
            if (special) { updateAfterSetAttr(name); }
        }

As in ``object.__setattr__``,
the logic looks for and acts on a data descriptor found in the meta-type,
and then moves to the instance dictionary of the type.
Things are made simpler by the fact that a type always has a dictionary,
and we already know that we are allowed to modify it.

Following the re-definition of any special function,
the type must be given the chance to re-compute internal data structures,
in particular, the affected type slots.


.. _type-delattr:

Implementing ``type.__delattr__``
=================================

There is nothing to write concerning ``type.__delattr__``
that is not already covered in :ref:`object-delattr`
and :ref:`type-setattr`.



A Glance up the Mountain
************************

Common built-ins do not provide for client code
to add attributes to instances,
that is, they have no instance dictionary.
However, they may have attributes, that may be instance data or methods.

In the case of methods,
getting one from an instance will usually create a binding
(a sort of Curried function) that is a new callable object.
Not only that, the slots we rely on extensively (like ``op_sub``)
are also exposed as methods (e.g. ``__sub__``)
that can be called on instances or types.

The code we have exhibited for ``__getattribute__``,
``__setattr__`` and ``__delattr__``,
relies on the existence of :ref:`Descriptors`.
We have yet to develop the mechanism for creating descriptors.

Descriptors are inserted in the dictionary of a type when it is created,
or inherited in the formation of sub-classes.
Quite different mechanisms are needed for filling slots
than we have implemented in ``evo3``.
This in turn is inseparable from sub-classing and inheritance,
which must also differ from their ``evo3`` realisations.

In order to experiment with even the most familiar attributes
of built-in types therefore,
we must greatly strengthen class and object creation and inheritance
in our toy implementation.

Suddenly, we have a significant climb ahead.
