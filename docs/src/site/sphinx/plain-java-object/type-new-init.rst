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
The different cases are distinguished in the specific ``T.__new__``
that the class provides.

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
we might expect ``__new__`` to be treated the same as
other static methods of the type,
but it is not.
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

Some code spelunking explains the different appearance of ``__new__``
from other static methods in a built-in type.
The *implementation* of ``__new__`` necessarily has static character,
since there is no instance to be the ``self`` reference
when it is called.
The object we find in the dictionary of the type, however,
is not the direct exposure of that method as static.

The entry for ``__new__`` most resembles a method of the type ``type``,
bound to the instance that defines this particular ``__new__`` method.
It is this private method (``tp_new_wrapper`` in CPython ``typeobject.c``)
that invokes the static method,
posted by the defining type to the ``tp_new`` slot.
But it invokes it only after a series of validations on the leading argument,
which is the sub-type being requested.
These validations are important to the integrity of the Python runtime.


A Java implementation of ``__new__``
------------------------------------
``__new__`` is not a ``Slot`` name because it is not an instance method.
But it is also not a regular static method as we have seen.

In our implementation,
static methods in general are recognised because
they are annotated with ``PythonStaticMethod``
and enter the dictionary wrapped with ``PyStaticMethod``.
As this is not what we want for ``__new__``,
we create a distinct annotation ``@PythonNewMethod``
that produces a bound ``PyJavaFunction`` object.
In other respects processing is similar to ``@PythonStaticMethod``,
and the resulting object is called like any other ``PyJavaFunction``.

The use of an annotation within the exposer framework,
rather than making ``__new__`` a special method recognised by name,
allows for a wide range of signatures.
We even have the possibility of a fast call in supported cases.
Here is a typical ``__new__``  in Java,
that for the type ``int``:

..  code-block:: java

    public class PyLong // ...

        @PythonNewMethod
        @DocString("...")
        private static Object __new__(PyType cls,
                @Default("None") @PositionalOnly Object x,
                @Default("None") Object base) throws Throwable {
        Object v = intImpl(x, base);
        if (cls == TYPE)
            return v;
        else
            return new PyLong.Derived(cls, PyLong.asBigInteger(v));
        }

The call ``intImpl(x, base)`` returns a Python ``int``,
typically a Java ``Integer``,
while ``PyLong.Derived`` is a sub-class of ``PyLong``
reporting the requested Python type ``cls``.
Note that if ``cls`` is not a Python sub-type of ``int``
we shall be returned an object that *behaves* as a sub-type of ``int``,
but will present to Python as whatever the caller selects in ``cls``.
We will shortly put a stop to this unacceptable licence.


A ``MethodHandle`` for  ``__new__``
-----------------------------------
When the exposer processes ``PyLong.__new__`` above,
it first forms a ``MethodHandle`` for it that has type ``(T,O,O)O``.
(Here ``O`` is a shorthand for ``Object.class``
and ``T`` for ``PyType.class``.)
It also creates an ``ArgParser`` involving the Java names of the parameters.
We are going to embed all this in a ``PyJavaFunction``,
to be invoked by its ``__call__`` method.

The handle in a ``PyJavaFunction`` must have a signature based
entirely on ``O``:
a fixed arity signature like ``(O,O,O)O`` or exactly ``(O[])O``.
It may then be called with values declared in our Java code as ``Object``,
and it may safely return an object of any Java class.
To achieve this in a Python static method,
we add to each argument that needs it a conversion from Java ``Object``
to the type declared in the Java method definition.
These conversions, when they fail, should raise a Python ``TypeError``
rather than throw a Java ``ClassCastException``.
We have handles that convert to supported argument types with
Python semantics and errors.

The first parameter in a ``__new__`` method,
conventionally called ``cls``,
must be declared ``PyType``.
We add conversions where needed to all the subsequent arguments
(those in ``PyLong.__new__`` are already ``O``),
but it is not enough in a handle for ``__new__``
simply to apply a checked cast raising ``TypeError``.
We must also ensure ``cls`` is a Python sub-type of ``int``,
or we shall have objects that break the invariant that
``x instanceof J`` in Java implies ``isinstance(x, P)`` in Python,
where ``J`` is an implementation of ``P``.
For this we take a method handle on
the following instance method in ``PyType``:

..  code-block:: java

    public class PyType extends Operations implements DictPyObject {
        //...
        PyType validatedNewArgument(Object arg0) throws TypeError {
            if (arg0 == this) {
                // Quick success in the frequent case
                return this;
            } else if (!(arg0 instanceof PyType)) {
                // arg0 wasn't even a type
                throw new TypeError(
                        "%s.__new__(X): X must be a type object not %s",
                        this.getName(), PyType.of(arg0).getName());
            } else {
                PyType cls = (PyType)arg0;
                if (!cls.isSubTypeOf(this)) {
                    String name = getName(), clsName = cls.getName();
                    throw new TypeError(
                            "%s.__new__(%s): %s is not a subtype of %s", //
                            name, clsName, clsName, name);
                } else {
                    return cls;
                }
            }
        }

As a ``MethodHandle``, ``validatedNewArgument`` has type ``(T,O)T``,
but when bound to the ``PyType`` of the defining class,
the result has type ``(O)T``.
This is a cast to ``PyType`` that contains the extra logic we need
to ensure the initial consistency of the created object with its Java type.
If the object allows assignment to its ``__class__`` attribute,
it must then defend the same same invariant in its setter,
but that's an issue for elsewhere.





