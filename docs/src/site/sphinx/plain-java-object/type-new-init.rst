..  plain-java-object/type-new-init.rst

.. _type-new-init:

Creation and Initialisation
***************************

When we outlined how special and other methods may be implemented
in types defined in Java,
we omitted ``__new__``.
We could successfully avoid object creation
since it was enough to create objects in Java
using the Java constructor.
The objects in our Python programs so far
have been literals in the byte code,
created by the ``marshal`` module when we load the compiled code,
or the result of functions and operations,
all of which can be created in Java.

This can't go on indefinitely.
Here we return to the topic of object creation from Python,
``__new__`` and ``type.__call__``.

    Code fragments in this section are found in
    ``rt3/src/main/java/.../vsj3/evo1``
    in the project source.


``type.__call__``
=================

Object creation in Python begins with the calling of a type object.
If we call a type we obtain an instance of that type.
(Almost always.
A user may return anything from their own definition of ``__new__``.)

..  code-block:: python

    >>> int(42)
    42
    >>> float()
    0.0
    >>> slice(5)
    slice(None, 5, None)
    >>> bool()
    False
    >>> class A: pass
    >>> A()
    <__main__.A object at 0x000001B0E63A9970>

We can even call the type ``type`` and obtain a new type,
as an alternative to the ``class`` keyword.

..  code-block:: python

    >>> type('B', (A,), {})
    <class '__main__.B'>

The exception to this is when we call ``type`` with one argument.
``type`` is still the type object ``type``,
but ``type.__call__`` checks for this case
and returns the type of its argument.

..  code-block:: python

    >>> type(42)
    <class 'int'>
    >>> type(A())
    <class '__main__.A'>


The implementation of ``type.__call__`` (in CPython 3.11),
somewhat simplified, is this:

..  code-block:: C

    static PyObject *
    type_call(PyTypeObject *type, PyObject *args, PyObject *kwds)
    {
        PyObject *obj;

        /* Special case: type(x) should return Py_TYPE(x) */
        if (type == &PyType_Type) {
            /* ... don't worry about this for now */
        }

        obj = type->tp_new(type, args, kwds);
        if (!PyObject_TypeCheck(obj, type)) return obj;

        objtype = Py_TYPE(obj);
        if (objtype->tp_init != NULL) {
            objtype->tp_init(obj, args, kwds);
        }
        return obj;
    }

Suppose ``T`` is a Python type.
In the Java implementation,
a call of ``T(args)``,
which is the same as ``type.__call__(T, args)``,
lands in ``PyType.__call__``.
A direct Java equivalent of the C code, still simplified, is as follows:

..  code-block:: java

    public class PyType extends Operations implements DictPyObject {
        // ...
        protected Object __call__(Object[] args, String[] names)
                throws TypeError, Throwable {

            if (this == PyType.TYPE) {
                /* ... don't worry about this for now */
            }

            Object _new = lookup("__new__");
            Object obj = Callables.call(_new, args, names);

            PyType objtype = PyType.of(obj);
            if (objtype.isSubTypeOf(this)
                    && Slot.op_init.isDefinedFor(objtype)) {
                objtype.op_init.invokeExact(obj, args, names);
            }
            return obj;
        }

During ``PyType.__call__``,
the target object ``this`` represents the Python type ``T``.
Cases we might want to handle specially include:

1. ``T`` is any type and we are creating a new instance of it.
2. ``T`` is ``type`` exactly and we are creating a new type.
3. ``T`` is a sub-type of ``type`` (a metatype)
   and we are creating a new type customised by it.

In fact these are all the same thing to ``PyType.__call__``.
The different cases are distinguished in the specific ``T.__new__``.

Note that while ``op_init`` is a ``Slot``,
there is no ``op_new``.
Unlike ``__init__``,
``__new__`` is not an instance method but static,
while ``Slot`` is restricted to instance methods.

``__new__`` gets some special treatment in type construction,
but otherwise it is just an entry in the dictionary of the type,
found by ``lookup`` along the MRO.
We show this happening in the method body of ``__call__`` ,
but an optimisation is possible looking up ``__new__`` only when it changes,
and caching the result as a Python callable or Java function.


The definition of ``__new__`` found along the MRO
must be a callable Python object (or we shall receive an exception).
It will normally be:

* a ``staticmethod`` (``PyStaticMethod``)
  leading to a Python ``function`` (``PyFunction``); or
* a ``builtin_function_or_method`` (``PyJavaFunction``)
  bound to the type object ``T`` as ``__self__`` and
  leading to a static method in the Java definition of that type.

``__new__`` will normally return
a Java object representing an instance of ``T``,
and then ``PyType.__call__`` goes on to call ``__init__``
through the ``op_init`` slot of ``this``.


``__new__``
===========

``__new__`` is a static method, even when it is not so annotated.

Comparison with other static methods
------------------------------------
Python gives unique treatment to ``__new__``.
In CPython, ``tp_new`` is a slot in the type object,
but it is only superficially like other slots, ``tp_str`` say.
Consider this class:

..  code-block:: python

    class C:
        def __new__(cls, *args, **kwargs):
            return super().__new__(cls)

        def m(self, *args, **kwargs):
            pass

        @staticmethod
        def sm(x, *args, **kwargs):
            pass

After executing the definition,
we can explore how the methods appear in the dictionary of the type:

..  code-block:: python

    >>> type(C.__dict__['m'])
    <class 'function'>
    >>> type(C.__dict__['sm'])
    <class 'staticmethod'>
    >>> type(C.__dict__['sm'].__func__)
    <class 'function'>
    >>> type(C.__dict__['__new__'])
    <class 'staticmethod'>
    >>> type(C.__dict__['__new__'].__func__)
    <class 'function'>

``__new__`` is reported in exactly the same way as ``sm``.
Its binding behaviour will be the same as that of ``C.sm``
(and not ``C.m``).
``__new__`` is treated specially in type construction,
implicitly decorated with ``@staticmethod`` if it is not explicitly.

When the type is a built-in,
we might expect it to be treated the same as other static methods of the type.
We'll use ``str`` as our example as
it contains a static method ``str.maketrans``.

..  code-block:: python

    >>> type(str.__dict__['maketrans'])
    <class 'staticmethod'>
    >>> type(str.__dict__['maketrans'].__func__)
    <class 'builtin_function_or_method'>
    >>> type(str.__dict__['maketrans'].__func__.__self__)
    <class 'NoneType'>
    >>> type(str.__dict__['__new__'])
    <class 'builtin_function_or_method'>
    >>> str.__dict__['__new__'].__self__
    <class 'str'>

``__new__`` is exposed differently from the regular static method
in two ways:

1. The entry in the dictionary is a bare ``builtin_function_or_method``,
   while that for the regular static method is wrapped in ``staticmethod``.
2. The ``builtin_function_or_method`` of ``__new__`` is *bound*
   to the type that defined it (``__self__`` is assigned),
   while that of the regular static method is *unbound*.

The entry for ``__new__`` most resembles a method of ``type``
bound to an instance that is the type defining ``__new__``.
It is this private method (``tp_new_wrapper`` in the C code)
that invokes the static method (``tp_new`` slot)
after a series of validations on the argument types.

The entry for ``__new__`` is also very similar to one for a method
in a built-in module,
where it is bound to the module:

..  code-block:: python

    >>> import math
    >>> type(math.__dict__['sin'])
    <class 'builtin_function_or_method'>
    >>> math.__dict__['sin'].__self__
    <module 'math' (built-in)>

The reason for the different treatment of ``__new__``
compared with other static methods in a built-in type
is unexplained.

``__new__`` is a static method when defined by the type
but the ``builtin_function_or_method`` differs from the ``staticmethod``
that is the exposed form of other static methods of a built-in.
It is not clear why this is so,
or how essential this difference is to the semantics of Python.
We return ``__new__`` from the exposer as any other static method,
but process ``__new__`` specially in type construction,
where we can reproduce expected behaviour as closely as proves necessary.


Not a ``Slot`` like other special methods
-----------------------------------------
In a type defined in C in CPython,
each slot (including ``tp_new``) is filled by the implementer
with a pointer to the implementing function (e.g. ``str_new``).
The run-time creates a descriptor wrapping the slot,
which in most cases is a ``wrapper_descriptor``.
Special treatment given to ``tp_new``
results in a ``builtin_function_or_method`` decriptor instead.

In Jython 3, the process is the reverse.
Built-in functions with the name of a ``Slot``
are recognised during exposure of the type,
and enter the dictionary as a slot wrapper descriptor.
Afterwards we cache the ``MethodHandle`` in an ``op_*`` slot.

Other static methods in general are recognised because
they are annotated with ``PythonStaticMethod``
and enter the dictionary wrapped with ``PyStaticMethod``.

``__new__`` is not a ``Slot`` name because it is not an instance method.
But it is also not a regular static method as we have seen.
This leads us to need a distinct annotation just for ``__new__``,
that enters a bound ``PyJavaFunction``.

We may decide to cache the look-up result by a mechanism
separate from the ``Operations`` object where instance slots are cached.



