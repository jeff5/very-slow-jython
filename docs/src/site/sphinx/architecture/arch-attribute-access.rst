..  architecture/arch-attribute-access.rst

.. _Attributes:

Access to Attributes
********************

We use the term "attribute" to cover anything that would be
accessed in Python with the dot notation.
In some compiled languages,
an attribute name can be translated to an offset
from the object's storage location
during compilation and linkage.
In Python it remains a string until encountered at run-time,
and it is resolved via look-up(s) to either:

* a dictionary entry where the attribute belongs, or
* a descriptor, a mechanism for accessing that attribute.

Attribute access and descriptors are interconnected concepts that
have to be developed together,
but are both quite complex.
This section looks at how we implement attribute access in Java.
There is a separate section on :ref:`Descriptors`,
but they are two parts of one mechanism.

.. note::
    At the time of writing,
    what we describe is quite well tested with built-in types
    and modules defined in Java.
    It can be expected to work with types and modules defined in Python.
    The approach to attributes of "found" Java types
    (classes written without the explicit intention they be Python objects)
    may need an expansion of the set of descriptor types.


A Reminder of the CPython Opcodes
=================================

We remind ourselves that there are three opcodes
for attribute setting, getting and deletion:

..  code-block:: pycon

    >>> dis(compile('o.a=42; print(o.a); del o.a', '<>', 'exec'))
      1           0 LOAD_CONST               0 (42)
                  2 LOAD_NAME                0 (o)
                  4 STORE_ATTR               1 (a)
                  6 LOAD_NAME                2 (print)
                  8 LOAD_NAME                0 (o)
                 10 LOAD_ATTR                1 (a)
                 12 CALL_FUNCTION            1
                 14 POP_TOP
                 16 LOAD_NAME                0 (o)
                 18 DELETE_ATTR              1 (a)
                 20 LOAD_CONST               1 (None)
                 22 RETURN_VALUE

The opcodes are ``STORE_ATTR``, ``LOAD_ATTR`` and ``DELETE_ATTR``.
Notice that each references a name ``a``
from the names table of the code object.

Behind the opcodes are special functions to support them:
``__getattribute__``, ``__getattr__``, ``__setattr__`` and ``__delattr__``.
These are the described in the `Python Data Model`_.
In the Java implementation,
a type object must implement slots for them comparable to those in CPython.
In accordance with :ref:`one-to-one-slot-principle`
we give each of the four special function a slot in the type object.

The existence of two "get" functions
allows for a simple fall-back to a computed value.
``__getattribute__`` is consulted first
and ``__getattr__`` only if the first fails with an ``AttributeError``.
By default, ``__getattribute__`` will be inherited from ``object``.
The implementation is difficult to follow in CPython
because both compete for the same ``tp_getattro`` slot
(and both for the ``tp_getattr`` slot too that takes a ``char *`` name)
and there is a trick to combine them in the same slot.
This is revisited in :ref:`getattribute-and-getattr`
where we elaborate a Java alternative.

Each opcode has a corresponding method in
the abstract object API supporting the interpreter.


Slots for Attribute Access
==========================

Special function slots are represented by ``MethodHandles``
in the ``Operations`` class,
a base of ``PyType``:

..  code-block:: java

    abstract class Operations {
        ...
        MethodHandle op_getattribute;
        MethodHandle op_getattr;
        MethodHandle op_setattr;
        MethodHandle op_delattr;

The special methods to which the handles give access
expect the name of the attribute as an object (always a Java ``String``).
We define them to the interpreter as:

..  code-block:: java

    enum Slot {
        ...
        op_getattribute(Signature.GETATTR),
        op_getattr(Signature.GETATTR),
        op_setattr(Signature.SETATTR),
        op_delattr(Signature.DELATTR),
        ...
        enum Signature {
            ...
            GETATTR(O, O, S),
            SETATTR(V, O, S, O),
            DELATTR(V, O, S),

``S`` is a shorthand for ``String.class``,
``O`` for ``Object.class`` and ``V`` for ``Void.class``,
all defined in ``ClassShorthand``.

..  note::
    The convention that these methods accept a ``String`` name
    rather than an ``Object``
    differs from what we find in CPython,
    where the signatures accept a general ``PyObject *``
    and have to check it is a ``PyUnicodeObject``.
    This is a worry point as we should expect there to be a good reason
    to incur the apparent extra work.
    At the time of writing we have not found one.


.. _abstract-api-attr:

Abstract API support for attributes
===================================

As usual, the slots are wrapped in abstract methods
so that we may call them from Java,
including from the implementation of the opcodes.

.. _abstract-getattr:

Abstract API ``getAttr``
------------------------

Our implementation of ``getAttr`` (strongly typed to ``String``) is:

..  code-block:: java

        /** {@code o.name} with Python semantics. */
        public static Object getAttr(Object o, String name)
                throws AttributeError, Throwable {
            Operations ops = Operations.of(o);
            try {
                // Invoke __getattribute__.
                return ops.op_getattribute.invokeExact(o, name);
            } catch (EmptyException | AttributeError e) {
                try {
                    // Not found or not defined: fall back on __getattr__.
                    return ops.op_getattr.invokeExact(o, name);
                } catch (EmptyException ignored) {
                    // __getattr__ not defined, original exception stands.
                    if (e instanceof AttributeError) { throw e; }
                    throw noAttributeError(o, name);
                }
            }
        }

Note that we fall back to ``__getattr__``
if ``__getattribute__`` produces no result.
This will carry no run-time cost where ``__getattribute__`` succeeds,
and only a small one if it raises ``AttributeError``
and ``__getattr__`` is not defined.

In most contexts,
we know statically that the name we have is a ``String``.
In particular,
the ``names`` array in a ``PyCode`` is declared ``String[]``,
so that the implementation of the ``LOAD_ATTR`` opcode can be just:

..  code-block:: java

    class CPython38Frame extends PyFrame<CPython38Code> {
        ...
        Object eval() {
            ...
                        case Opcode.LOAD_ATTR:
                            // v | -> | v.name |
                            // ---^sp ----------^sp
                            name = names[oparg | opword & 0xff];
                            oparg = 0;
                            s[sp - 1] = Abstract.getAttr(s[sp - 1], name);
                            break;

The arithmetic involving ``oparg`` facilitates the ``EXTENDED_ARG`` opcode.

Sometimes it may not be known that the name is a Java ``String``,
for example in the implementation of the built-in function ``getattr()``.
For this case we provide an overloaded version that will convert
``name`` to a String or raise an exception.

..  code-block:: java

        public static Object getAttr(Object o, Object name)
                throws AttributeError, TypeError, Throwable {
            return getAttr(o, PyUnicode.asString(name,
                    Abstract::attributeNameTypeError));
        }

The interested reader could compare these with CPython ``_PyObject_GetAttr``
and also ``PyObject_GetAttrString`` and ``PyObject_GetAttr`` all
in ``object.c``.
The comparable CPython API has to test that ``name`` is indeed a string.
After that the complexity is about the same,
but the two slots ``tp_getattro`` and ``tp_getattr`` there
do not have the same significance as
our ``op_getattribute`` and ``op_getattr``.

There is also a variant ``lookupAttr``,
comparable to ``_PyObject_LookupAttr``,
that returns ``null`` when the attribute is not found,
rather than throwing an exception.


Abstract API ``setAttr`` and ``delAttr``
----------------------------------------

We also need abstract API ``setAttr`` and ``delAttr`` methods,
which are straightforward to implement.
These also have counterparts that take ``Object name`` arguments.
In case of failure,
rather than simply reporting that there is no such attribute,
``attributeAccessError`` tries to provide the whole story.

..  code-block:: java

    public static void setAttr(Object o, String name, Object value)
            throws AttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        try {
            Operations.of(o).op_setattr.invokeExact(o, name, value);
        } catch (EmptyException e) {
            throw attributeAccessError(o, name, Slot.op_setattr);
        }
    }

    public static void delAttr(Object o, String name)
            throws AttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        try {
            Operations.of(o).op_delattr.invokeExact(o, name);
        } catch (EmptyException e) {
            throw attributeAccessError(o, name, Slot.op_delattr);
        }
    }


.. _getattribute-and-getattr:

``__getattribute__`` and ``__getattr__`` in CPython
---------------------------------------------------

Built-in classes in CPython usually fill the ``tp_getattro`` slot
with ``PyObject_GenericGetAttr`` in ``object.c``,
directly or by inheritance.
The slot is exposed as ``__getattribute__``.

``PyObject_GenericGetAttr`` consults the type of a target object
and the instance dictionary of the object,
in the order defined by the Python data model.
In :ref:`object-getattribute`,
we shall show an equivalent implementation in Java.
It matches the CPython ``PyObject_GenericGetAttr`` closely,
but CPython is hiding a trick
concerning *exactly* what it exposes as ``__getattribute__``.

Before Python 2.2,
a type defined in Python would customise attribute access
by defining the special method ``__getattr__``.
That method would be called when a built-in mechanism
failed to resolve the attribute name.
At Python 2.2,
the built-in mechanism became ``__getattribute__`` as a way to give
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
the fall-back is accomplished at almost no cost by setting ``tp_getattro``,
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

The difference in our implementation from CPython
will be visible wherever ``tp_getattro`` is referenced directly.
In ported code, it should probably be converted to ``op_getattribute``,
and it may be appropriate to fall back to ``op_getattr`` in the code.
All the examples of this are in the implementation of attribute access.
In our implementation,
the ``Slot``\s are not API, and so this is an internal matter.


..  _Attribute access in Python 2.2:
    https://docs.python.org/3/whatsnew/2.2.html#attribute-access

.. _Python Data Model:
    https://docs.python.org/3/reference/datamodel.html



.. _descriptors-in-concept:

The Mechanism of Attribute Access
=================================

Descriptors in Concept
----------------------

A descriptor is an object that defines the slot function ``__get__``,
and may also define ``__set__`` and ``__delete__``.
If it also defines ``__set__`` or ``__delete__`` it is a "data descriptor",
otherwise it is a "non-data descriptor".

A descriptor may appear in the dictionary of a type object,
and frequently does.

When looking for an attribute on an object,
the dictionary of the type object is consulted first.
The type may, in the end, supply a simple value for the attribute,
as when a variable or constant defined in the class body
is referenced via the instance.
However,
the search for an attribute via the type will often find a descriptor.
Then Python must call the ``__get__``, ``__set__`` or ``__delete__``,
according to the action requested.

A "data descriptor" will generally get, set or delete
an attribute stored on the instance by whatever process it defines.
A ``__get__`` on a "non-data descriptor"
(it only has a ``__get__`` method)
will generally result in a callable method object.

Most attributes of built-in types are mediated this way,
and it is especially important in the way that methods are bound
before being called.
That descriptors are executed in the course of attribute access,
is critical to a full understanding of the implementations of
``__getattribute__``, ``__setattr__`` and ``__delattr__``
in the coming sections.

There is a long discussion of the different *types* of descriptor
in the architecture section :ref:`Descriptors`,
but this generic description is enough to understand attribute access.


.. _interface-to-dict:

Interface ``DictPyObject``
--------------------------

It will be a frequent need to get the instance dictionary (in Java) from
a Python object, to look up attributes in it.
This includes the case where the object is a ``type`` object.
We define an interface ``DictPyObject`` that advertises the possibility:

..  code-block:: java

    public interface DictPyObject extends CraftedPyObject {
        Map<Object, Object> getDict();
    }

Absence of the interface implies that there is no instance dictionary.
This promise is a demanding one to keep
that has implications for class definition in Python.

This interface does not promise a reference to a fully-functional ``dict``,
although an object could implement the interface like that,
since a ``PyDict`` implements ``Map<Object, Object>``.
Some types of object (and ``type`` is one of them),
insist on controlling access to their members
more tightly than handing out a ``dict`` would allow.


Read-only Dictionary (e.g. ``PyType``)
--------------------------------------

Although every ``type`` object has a ``__dict__`` member,
it is not as permissive as those found in objects of user-defined type.
``PyType`` has a lot to do when the attributes of a class change,
so it needs to take control when that happens.

..  code-block:: pycon

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

We achieve this by wrapping the dictionary implementation in ``getDict()``,
for example, if ``dict`` is the instance dictionary:

..  code-block:: java

    public class PyType extends Operations implements DictPyObject {
        ...
        @Getter("__dict__")
        @Override
        public final Map<Object, Object> getDict() {
            return Collections.unmodifiableMap(dict);
        }

We do this in ``PyType``,
to prevent clients updating the dictionary directly.
(It also prevents ``object.__setattr__`` being applied to a type object,
since ``PyBaseObject.__setattr__`` uses this API.)

In contrast,
``type.__setattr__`` *can* be applied,
since ``PyType.__setattr__`` has access to the private dictionary
and can arrange the proper slot updates.

While built-in types generally do not allow attribute setting,
many user-defined instances of ``PyType`` *do* allow it.
During the following,
the type object ``C`` allows the assignment of
a new definition for special function ``__repr__``.
It must then fill the ``tp_repr`` slot with a function pointer
that will call our new function.

..  code-block:: pycon

    >>> C.__repr__ = lambda self: "I'm a C!"
    >>> C()
    I'm a C!

We can manage this because we give ``PyType`` a custom ``__setttr__``,
that inspects the flag that determines this kind of mutability,
and has private access to the type dictionary.
*All* type objects have to respond to changes to special methods
in their dictionary,
by updating type slots
and notifying sub-classes of (potentially) changed inheritance.
The custom ``__setttr__`` makes sure that happens.

We're finally ready to say how attribute access is implemented in ``object``.


.. _object-getattribute:

Implementing ``object.__getattribute__``
----------------------------------------

The standard implementation of ``__getattribute__`` is in ``PyBaseObject``.
The special function (type slot) it produces
is inherited by almost all built-in and user-defined classes.
It fills the type slot ``op_getattribute``.

The code speaks quite well for itself.
It is adapted from the CPython ``PyObject_GenericGetAttr`` in ``object.c``,
taking account of our different approach to error handling,
and with the removal of some efficiency tricks.
There is some delicacy around which exceptions should be caught
(so we can look somewhere else),
and which should put a definitive end to the attempt.

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...
        static Object __getattribute__(Object obj, String name)
                throws AttributeError, Throwable {

            PyType objType = PyType.of(obj);
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            Object typeAttr = objType.lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor
                Operations typeAttrOps = Operations.of(typeAttr);
                descrGet = typeAttrOps.op_get;
                if (typeAttrOps.isDataDescr()) {
                    // typeAttr is a data descriptor so call its __get__.
                    try {
                        return descrGet.invokeExact(typeAttr, obj, objType);
                    } catch (Slot.EmptyException e) {
                        /*
                         * Only __set__ or __delete__ was defined. We do not
                         * catch AttributeError: it's definitive. Suppress
                         * trying __get__ again.
                         */
                        descrGet = null;
                    }
                }
            }

            /*
             * At this stage: typeAttr is the value from the type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the object instance dictionary a chance.
             */
            if (obj instanceof DictPyObject) {
                Map<Object, Object> d = ((DictPyObject) obj).getDict();
                Object instanceAttr = d.get(name);
                if (instanceAttr != null) {
                    // Found the answer in the instance dictionary.
                    return instanceAttr;
                }
            }

            /*
             * The name wasn't in the instance dictionary (or there wasn't
             * an instance dictionary). typeAttr is the result of look-up on
             * the type: a value , a non-data descriptor, or null if the
             * attribute was not found.
             */
            if (descrGet != null) {
                // typeAttr may be a non-data descriptor: call __get__.
                try {
                    return descrGet.invokeExact(typeAttr, obj, objType);
                } catch (Slot.EmptyException e) {}
            }

            if (typeAttr != null) {
                /*
                 * The attribute obtained from the type, and that turned out
                 * not to be a descriptor, is the return value.
                 */
                return typeAttr;
            }

            // All the look-ups and descriptors came to nothing :(
            throw Abstract.noAttributeError(obj, name);
        }



.. _object-setattr:

Implementing ``object.__setattr__``
-----------------------------------

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
It simplifies the logic (fewer ``if`` statements),
although it means a little more code as we have separate methods.

The standard implementation of ``__setattr__`` is as follows:

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...
        static void __setattr__(Object obj, String name, Object value)
                throws AttributeError, Throwable {

            // Accommodate CPython idiom that set null means delete.
            if (value == null) {
                // Do this to help porting. Really this is an error.
                __delattr__(obj, name);
                return;
            }

            // Look up the name in the type (null if not found).
            Object typeAttr = PyType.of(obj).lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                Operations typeAttrOps = Operations.of(typeAttr);
                if (typeAttrOps.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        typeAttrOps.op_set.invokeExact(typeAttr, obj,
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
            if (obj instanceof DictPyObject) {
                Map<Object, Object> d = ((DictPyObject) obj).getDict();
                try {
                    // There is a dictionary, and this is a put.
                    d.put(name, value);
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            } else {
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
            }
        }


.. _object-delattr:

Implementing ``object.__delattr__``
-----------------------------------

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
but from within the slot function (and with a less helpful message).

..  code-block:: java

    class PyBaseObject extends AbstractPyObject {
        //...
        static void __delattr__(Object obj, String name)
                throws AttributeError, Throwable {

            // Look up the name in the type (null if not found).
            Object typeAttr = PyType.of(obj).lookup(name);
            if (typeAttr != null) {
                // Found in the type, it might be a descriptor.
                Operations typeAttrOps = Operations.of(typeAttr);
                if (typeAttrOps.isDataDescr()) {
                    // Try descriptor __delete__
                    try {
                        typeAttrOps.op_delete.invokeExact(typeAttr, obj);
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
            if (obj instanceof DictPyObject) {
                Map<Object, Object> d = ((DictPyObject) obj).getDict();
                try {
                    // There is a dictionary, and this is a delete.
                    Object previous = d.remove(name);
                    if (previous == null) {
                        // A null return implies it didn't exist
                        throw Abstract.noAttributeError(obj, name);
                    }
                } catch (UnsupportedOperationException e) {
                    // But the dictionary is unmodifiable
                    throw Abstract.cantSetAttributeError(obj);
                }
            } else {
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
            }
        }





.. _type-getattribute:

Implementing ``type.__getattribute__``
--------------------------------------

The type object gets its own definition of ``__getattribute__``,
slightly different from that in ``object``,
and found in ``PyType.__getattribute__``.
We highlight the differences here.

A type has a type, called the meta-type.
This occasions a change of variable names, even where the code is the same:
where in ``PyBaseObject`` we had ``obj``, in ``PyType`` we write ``type``,
and where we had ``typeAttr``, we write ``metaAttr``.

..  code-block:: java
    :emphasize-lines: 37, 39-52

    public class PyType extends Operations implements DictPyObject {
        ...
        protected Object __getattribute__(String name)
                throws AttributeError, Throwable {

            PyType metatype = getType();
            MethodHandle descrGet = null;

            // Look up the name in the type (null if not found).
            Object metaAttr = metatype.lookup(name);
            if (metaAttr != null) {
                // Found in the metatype, it might be a descriptor
                Operations metaAttrOps = Operations.of(metaAttr);
                descrGet = metaAttrOps.op_get;
                if (metaAttrOps.isDataDescr()) {
                    // metaAttr is a data descriptor so call its __get__.
                    try {
                        // Note the cast of 'this', to match op_get
                        return descrGet.invokeExact(metaAttr, (Object) this,
                                metatype);
                    } catch (Slot.EmptyException e) {
                        /*
                         * Only __set__ or __delete__ was defined. We do not
                         * catch AttributeError: it's definitive. Suppress
                         * trying __get__ again.
                         */
                        descrGet = null;
                    }
                }
            }

            /*
             * At this stage: metaAttr is the value from the meta-type, or a
             * non-data descriptor, or null if the attribute was not found.
             * It's time to give the type's instance dictionary a chance.
             */
            Object attr = lookup(name);
            if (attr != null) {
                // Found in this type. Try it as a descriptor.
                try {
                    /*
                     * Note the args are (null, this): we respect
                     * descriptors in this step, but have not forgotten we
                     * are dereferencing a type.
                     */
                    return Operations.of(attr).op_get.invokeExact(attr,
                            (Object) null, this);
                } catch (Slot.EmptyException e) {
                    // We do not catch AttributeError: it's definitive.
                    // Not a descriptor: the attribute itself.
                    return attr;
                }
            }

            /*
             * The name wasn't in the type dictionary. metaAttr is now the
             * result of look-up on the meta-type: a value, a non-data
             * descriptor, or null if the attribute was not found.
             */
            if (descrGet != null) {
                // metaAttr may be a non-data descriptor: call __get__.
                try {
                    return descrGet.invokeExact(metaAttr, (Object) this,
                            metatype);
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
the first step is to access the type (that is the meta-type),
and if we find a data descriptor, act on it.
The second option is again to look in the instance (that is, the ``type``),
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
---------------------------------

The definition of ``type.__setattr__``
is also slightly different from that in ``object``.
First we must deal with the possibility that
the type does not allow its attributes to be changed.
Most built-in types are in that category,
while most classes defined in Python (sub-classes of ``object``)
do allow this.

..  code-block:: java
    :emphasize-lines: 14-15, 18, 30, 46

    public class PyType extends Operations implements DictPyObject {
        ...
        protected void __setattr__(String name, Object value)
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

            // Check to see if this is a special name
            boolean special = isDunderName(name);

            // Look up the name in the meta-type (null if not found).
            Object metaAttr = getType().lookup(name);
            if (metaAttr != null) {
                // Found in the meta-type, it might be a descriptor.
                Operations metaAttrOps = Operations.of(metaAttr);
                if (metaAttrOps.isDataDescr()) {
                    // Try descriptor __set__
                    try {
                        metaAttrOps.op_set.invokeExact(metaAttr,
                                (Object) this, value);
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
---------------------------------

There is nothing to write concerning ``type.__delattr__``
that is not already covered in :ref:`object-delattr`
and :ref:`type-setattr`.

