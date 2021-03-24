..  plain-java-object/hash-dictionary.rst

.. _Hash-dictionary:

The Hash and a Dictionary
#########################

Both Python and Java use collections that depend on hashes.
In both languages,
it is important for the use of collection types (sets and maps),
that two objects considered equal should also have the same hash.

    Code fragments in this section continue in
    ``rt3/src/main/java/.../vsj3/evo1``
    and corresponding test and other directories
    in the project source.



The Type ``str``
****************

We implement Python ``str`` by the canonical class ``PyUnicode``,
and we adopt ``java.lang.String`` as an implementation.
This follows the pattern already explained in :ref:`Operations-builtin`.
We can generate many of the special methods,
including the comparison functions,
with the script ``PyUnicode.py``.

``PyUnicode`` holds an array of Java ``int`` representing the code points.
A representation may eventually be possible that
provides more variety of storage internally (like CPython),
but not just yet.
In practice,
most instances of ``str`` will be represented by ``java.lang.String``,
and so compact representations would benefit only sub-classes of ``str``.

We obtain an accurate interpretation of ``String``
in mixed operations with ``PyUnicode``
by wrapping the ``String`` temporarily
in a Java iterable yielding ``Integer`` elements.
(``PyUnicode`` is such an iterable already.)

There is potentially a problem in giving a proper interpretation
to a ``String`` involving UTF-16 surrogate pairs,
where what might be preferred for consistency is slow at run-time.



.. _Hash-dictionary-plain-object:

Plain Object Keys and ``dict``
******************************

It is important for the use of collection types,
that two objects considered equal should have the same hash.
A Java collection will determine equality using ``Object.equals(Object)``
and hash using ``Object.hashCode()``.
A Python collection must determine equality using ``__eq__(Object, Object)``
and hash using ``__hash__(Object)``.

In VSJ2 we were pleased withthe simplicity of implementing ``dict`` as:

..  code-block:: java

    class PyDict extends LinkedHashMap<PyObject, PyObject> // ...

We could operate on ``dict`` instances
through the API of ``LinkedHashMap`` directly.

We shall explain why this is no longer an option.
Although the problems we discuss afflict
all types that have multiple accepted implementation classes,
we'll discuss it in the context of ``str`` and ``int`` keys.


Problems Posed by ``Object.hashCode()``
=======================================

The way in which ``String``\s are hashed in Java
is part of the language specification.
Fortunately it is not specified in Python,
so we can reasonably define ``str.__hash__``
in terms of ``String.hashCode()``.

We may then define both of these for ``PyUnicode`` by the same formula,
and be guaranteed that equal strings will hash to the same value.
(We have to take a little care to handle supplementary plane characters
as if they were actually represented by a surrogate pair.)

Java defines ``Integer.hashCode()``
and ``BigInteger.hashCode()`` consistently.
But we come unstuck with ``Boolean.TRUE`` and ``Boolean.FALSE``,
which Java hashes to 1231 and 1237,
but Python to 1 and 0 respectively.


Problems Posed by ``Object.equals()``
=====================================

We can define ``PyUnicode.equals(Object)``
to accept ``String`` or ``PyUnicode`` and compare using
the Python ``Comparison.EQ``.
This will deal correctly with ``u.equals(s)`` encountered in Java,
where ``u`` and ``s`` are ``PyUnicode`` and  ``String`` respectively.

Do not mistake ``s`` here for a ``bytes`` object
(``PyString`` as it was in Jython 2).
Both ``u`` and ``s`` are instances of ``str``.
Let's suppose these ``u`` and ``s`` are equal
according to the rules of Python.
In the midst of a call ``map.get(u)``,
in the depths of ``LinkedHashMap``,
this test will identify correctly
an entry previously made with ``map.put(s)``.

However,
``map.get(s)`` will not correctly retrieve
an entry previously made with ``map.put(u)``,
because ``s.equals(u)`` is ``false``.
``String.equals(Object)`` is ``false`` if the argument is not a ``String``.

Likewise,
``BigInteger.equals(Object)`` will return ``true`` only if
the other object is a ``BigInteger``.
The several implementations of ``int``
(and ``bool``)
will therefore not report as equal when used as keys in a Java container
although Python requires it.


Changes to ``dict``
===================

These considerations mean that
we cannot use Python objects as keys in Java containers,
and obtain Python semantics in the index operations.
(Python ``1`` and ``True`` will index different entries, for example.)
In particular we cannot implement ``PyDict``
simply by extending the Java container ``LinkedHashMap``,
although otherwise it seems the perfect choice.

Instead, we shall use a Java ``LinkedHashMap`` internally,
but wrap our keys so as to compare them using Python semantics.
``PyDict`` will implement ``java.util.Map``,
but we have to do more work than before to implement the API.

The idea is illustrated here:

..  uml::
    :caption: Giving Python semantics to keys

    interface Key {
        equals() : boolean
        hashCode() : int
        get() : Object
    }

    interface KeyHolder {
        equals() : boolean
        hashCode() : int
        get() : Object
    }
    Key <|.. KeyHolder
    KeyHolder --> Object

    abstract class PySomething {
        equals() : boolean
        hashCode() : int
        get() : Object
    }
    Key <|.. PySomething

    class PyDict {
        get(Object) : Object
        put(Object, Object) : Object
    }
    PyDict -right-> LinkedHashMap : map

    class LinkedHashMap {
        get(Key) : Object
        put(Key, Object) : Object
    }
    LinkedHashMap --> "*" Entry

    class Entry {
        value : Object
    }
    Entry -right-> Key : key


Each key in the inner ``map`` implements the ``PyDict.Key`` interface.
A ``KeyHolder`` is an object we create to wrap
the actual key received by ``PyDict.put``,
so it may participate in a ``Map.Entry``.

We must also wrap the argument to ``PyDict.get``,
so that we may search ``map`` with it.
The code for these two methods is simply:

..  code-block:: java
    :emphasize-lines: 11-19

    class PyDict extends AbstractMap<Object, Object>
            implements CraftedType {

        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("dict", MethodHandles.lookup()));

        /** The dictionary as a hash map preserving insertion order. */
        private final LinkedHashMap<Key, Object> map =
                new LinkedHashMap<Key, Object>();

        @Override
        public Object get(Object key) {
            return map.get(toKey(key));
        }

        @Override
        public Object put(Object key, Object value) {
            return map.put(toKey(key), value);
        }
        // ...

In order to extend ``AbstractMap``,
``PyDict`` must also implement a custom method
``Set<Entry<Object, Object>> entrySet()``,
the set that backs it,
and an iterator on the entry set.
This is all fairly standard: the library gives us the apparatus we need.

Now, wrapping every key is an overhead.
While it is a necessary one,
to support the plain object paradigm with adopted implementations,
we may avoid it much of the time.

Where we can redefine ``equals()`` and ``hashCode()``,
we'll allow the objects themselves to be the keys.
For this reason the class diagram shows an example built-in ``PySomething``
implementing ``PyDict.Key``.
In general crafted implementations may implement ``PyDict.Key``,
while adopted ones cannot.

It remains an open question whether discovered Java types
should be treated as keys directly or wrapped.
There seems no need to give them Python semantics in this respect,
so whatever ``hashCode()`` and ``equals()`` they define
could probably stand.
This would force ``map`` to become a ``Map<Object, Object>``.

There may be a case for having the ``Operations`` object
provide a ``PyDict.Key``,
since it differentiates by Java class within a common type.



