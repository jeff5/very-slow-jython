..  plain-java-object/modules-in-java.rst

.. _Modules-in-java:

Python Modules in Java
######################

Objectives
**********

We seek a general approach to defining Python modules in Java,
comparable to CPython's described in `Extending and Embedding`_.
We will use this to create the modules
that users expect to find in the standard library,
where we choose to implement them in Java.
(These are not necessarily the same modules that CPython
chooses to implement in C.)

We have seen already in :ref:`Built-in-methods`
how we can construct types in Java
by building the necessary data structures during class initialisation.
It is convenient to re-use for modules the annotations
and the exposure mechanism used for types
with necessary adjustments.

We will distinguish from the start the definition of a module
(static initialisation of the module Java class)
from the creation of instances of the ``module`` object.
Each interpreter will hold its own instance of a given module
that it may modify independently.

We shall have a ``builtins`` module and a ``sys`` module
quite early in our work.
These may have special access to the runtime,
while built-in and extension modules generally will not.
The mechanism itself should not require extension modules in general
to have privileged access to the Jython runtime.

We aim to offer the same mechanism we use ourselves to extension writers
whose modules will be in unrelated Java packages
with no privileged access to the runtime.
The package and module structure will protect the runtime under Java rules.

.. _Extending and Embedding:
    https://docs.python.org/3/extending/index.html
.. _Method Table and Initialization:
    https://docs.python.org/3/extending/extending.html#the-module-s-method-table-and-initialization-function


Module Data Structures
**********************

Definition and Instance Structures
==================================

When we expose a *type* from a defining class
we create one ``PyType`` object,
which we attach to the Java class,
containing a dictionary of descriptors and constants.
All objects of that Python type reference the type
but each object has independent state.

When an interpreter imports a given module,
it must represent a state for the module independently of other interpreters
so each may modify their copy.
This state is the Python ``module`` object.
(When a module is defined in Python,
all the state is the module's ``__dict__``,
but one defined in Java may have state in Java fields.)

The module's dictionary contains the functions and other values
that we normally access as attributes.

..  code-block:: python

    >>> import math
    >>> type(math)
    <class 'module'>
    >>> math.__dict__.keys()
    dict_keys(['__name__', '__doc__', '__package__', '__loader__', '__spec__',
        'acos', 'acosh', 'asin', 'asinh', 'atan', 'atan2', 'atanh', ...
    >>> type(math.__dict__['tan'])  # math.tan : PyCFunctionObject
    <class 'builtin_function_or_method'>
    >>> type(math.__dict__['pi'])   # math.pi : PyFloatObject
    <class 'float'>

Each ``import`` statement referencing that module within the same interpreter
makes the same ``module`` object available to a particular *namespace*.
Only the first import statement imports it to the *interpreter*,
creates the ``module`` object, and executes its body.
This applies whether the module is defined in Python or compiled.

Notice that a method in the module is defined with a ``self``
and bound to the module instance:

..  code-block:: python

    >>> tan = math.__dict__['tan']
    >>> type(tan)
    <class 'builtin_function_or_method'>
    >>> tan.__text_signature__
    '($module, x, /)'
    >>> tan
    <built-in function tan>
    >>> tan.__self__
    <module 'math' (built-in)>

Because it is bound to a module, ``tan`` reports as a function
and takes a single argument.

There is a hint in the ``__spec__`` attribute
that behind the module instance lies a potentially static description,
but we have to dive into the C tutorial `Method Table and Initialization`_
to find the facts.


CPython's approach
------------------

The initialisation of the ``math`` module (``mathmodule.c``)
looks like this in CPython 3.11, when abridged:

..  code-block:: c

    static int
    math_exec(PyObject *module)
    {
        if (PyModule_AddObject(module, "pi",
                PyFloat_FromDouble(Py_MATH_PI)) < 0) { return -1; }
        if (PyModule_AddObject(module, "e",
                PyFloat_FromDouble(Py_MATH_E)) < 0) { return -1; }
        // ...
        return 0;
    }

    static PyMethodDef math_methods[] = {
        {"acos",            math_acos,      METH_O,         math_acos_doc},
        {"acosh",           math_acosh,     METH_O,         math_acosh_doc},
        // ...
        {"tan",             math_tan,       METH_O,         math_tan_doc},
        {"tanh",            math_tanh,      METH_O,         math_tanh_doc},
        MATH_TRUNC_METHODDEF
        MATH_PROD_METHODDEF
        // ...
        {NULL,              NULL}           /* sentinel */
    };

    static PyModuleDef_Slot math_slots[] = {
        {Py_mod_exec, math_exec},
        {0, NULL}
    };

    PyDoc_STRVAR(module_doc,
    "This module provides access to the mathematical functions\n"
    "defined by the C standard.");

    static struct PyModuleDef mathmodule = {
        PyModuleDef_HEAD_INIT,
        .m_name = "math",
        .m_doc = module_doc,
        .m_size = 0,
        .m_methods = math_methods,
        .m_slots = math_slots,
    };

    PyMODINIT_FUNC
    PyInit_math(void)
    {
        return PyModuleDef_Init(&mathmodule);
    }

We show the CPython 3.11 version
since we intend to support multiphase initialisation (:pep:`489`)
and multiple interpreters.

The method table and the ``struct`` type ``PyMethodDef``
are also used when exposing methods from types in CPython.
Notice that the definition of ``tan`` has signature ``METH_O``,
signifying a method taking a ``self`` and one further argument,
as we observed in the REPL.
Entries like ``MATH_TRUNC_METHODDEF``
have been generated by Argument Clinic as locally-used macros.

The table of ``PyModuleDef_Slot`` objects orchestrates
the phases of initialisation.
The creation of a ``module`` object (``Py_mod_create`` slot) is implicit here,
as it is almost everywhere in the code base.
The module "body" function has to be stated explicitly
in ``{Py_mod_exec, math_exec}``.

Note that the create and execute phases are both run *once per interpreter*.
The distinction we seek between static and per-interpreter module state
is expressed between *static* data in the module definition file
and the ``module`` object (both creation and execution of the "body").

An interpreter holds the import context, that is,
the search strategy and a list of module *instances*.
Module instances ought to hold all the state of the module,
but many C extension modules were developed
with module state in static variables.
This is not safe when those modules are
imported into concurrent sub-interpreters.
CPython API has to accommodate these extensions
through a hoped for transitional period.
Guidance and the API to make `Isolating Extension Modules`_
the norm is relatively recent.

..  note:: CPython assumes that a module that does not support
    multiphase initialisation does not support multiple interpreters either.
    (It is not clear if this connection is causal.)
    We don't need to support single-phase initialisation in Java
    nor the API patterns that support legacy C extensions.

It is good that this is all private to CPython
as it leaves us free to implement whatever way we want in Java,
aiming only to make the surface behaviour identical.

..  _`Isolating Extension Modules`:
    https://docs.python.org/3.11/howto/isolating-extensions.html


A Java approach
---------------

When we first read the Java class that defines a module,
we will create a module description based on the annotated class.
For this we need a variation on the ``TypeExposer``,
referenced several times in
:ref:`Operations-builtin` and :ref:`Built-in-methods`.

This variant is obviously called the ``ModuleExposer``.
An instance is obtained from method ``Exposer.exposeModule``.
The exposer builds a complex ephemeral description,
but this is less important to discuss than the structures it leaves behind,
which we now present.

..  uml::
    :caption: Classes describing a Python module in Java

    class PyModule {
        name : String
    }

    abstract class JavaModule {
    }
    PyModule <|-- JavaModule
    JavaModule -right-> ModuleDef : definition

    class ModuleDef {
        name : String
        definingClass : Class
    }
    ModuleDef *-right-> "*" MethodDef : methods

    class MethodDef {
        handle : MethodHandle
    }
    MethodDef --> ArgParser : argParser
    MethodDef ...> PyJavaFunction : <<creates>>

    class ArgParser {
        name : String
        argNames : String[]
    }

    abstract class PyJavaFunction {
        module : String
        handle : MethodHandle
        ~__call__() : Object
    }
    PyJavaFunction -right-> ArgParser : argParser
    PyJavaFunction -up-> JavaModule : self
    PyJavaFunction "*" <--- JavaModule : dict

    PyJavaFunction <|-- NoArgs
    PyJavaFunction <|-- O1
    PyJavaFunction <|-- Positional
    PyJavaFunction <|-- General

``PyModule`` is the Python ``module`` object.
We define an abstract subclass ``JavaModule``,
which every Python module defined in Java extends.

During static initialisation of the class defining a module,
we create a ``ModuleDef`` and scan the defining class for annotated methods.
This scan is actually the work of a ``ModuleExposer``.
From each method annotated for exposure,
it creates a ``MethodDef`` with an attached ``ArgParser``
describing the method signature and its attributes.
The ``ModuleDef`` holds all the ``MethodDef``\s resulting from exposure.
The ``ModuleDef`` is stored as a static field in the defining class.
This completes the once-per-class processing.

Each time an instance of the module must be created,
the superclass constructor (``JavaModule(ModuleDef)``)
reads the definition built earlier.
It inserts a bound ``PyJavaFunction`` into the module's dictionary
for each ``MethodDef`` in the ``ModuleDef``.
In each case,
it chooses the appropriate concrete subclass of ``PyJavaFunction``
for the signature in the ``ArgParser``,
so that efficient calls will be possible in simple cases.

The ``MethodDef`` is superficially similar to the CPython ``PyMethodDef``,
but it never becomes part of the function object,
except for the ``ArgParser`` that contains all we need to call it
and create explicit error messages.


An Attempt at ``builtins``
**************************

Let's try to define a ``builtins`` module
containing just a few familiar objects.

..  code-block:: java

    package uk.co.farowl.vsj3.evo1;
    // ...
    class BuiltinsModule extends JavaModule {

        private static final ModuleDef DEFINITION =
                new ModuleDef("builtins", MethodHandles.lookup());

        BuiltinsModule() {
            super(DEFINITION);
            add("None", Py.None);
            add("int", PyLong.TYPE);
        }
    // ... methods
    }

We have implemented two different annotations on methods
``PythonMethod`` and ``PythonStaticMethod``
for use in slightly different circumstances.


Using ``PythonStaticMethod``
============================

The function ``abs`` is very simple.
It just calls the abstract API method that invokes the slot on the argument.
We declare ``abs`` to be ``static``
and use the matching annotation ``PythonStaticMethod`` to expose it.

..  code-block:: java

    class BuiltinsModule extends JavaModule {

        // ... as previously

        @PythonStaticMethod
        @DocString("Return the absolute value of the argument.")
        static Object abs(Object x) throws Throwable {
            return PyNumber.absolute(x);
        }
    }

The exposer creates a direct ``MethodHandle`` on the ``abs`` method,
which has type ``(Object)Object``,
and stores it in the ``MethodDef``.

The ``JavaModule`` constructor (per instance) writes it into
the ``handle`` field of the ``PyJavaFunction`` representing ``abs``.
It sets the ``self`` field, exposed as ``__self__``,
to the module instance as expected by Python,
but we do not need it when we call ``abs`` through the handle.
This is the simple way to create a module-level function.

The CPython equivalent of this is very similar:

..  code-block:: c

    static PyObject *
    builtin_abs(PyObject *module, PyObject *x)
    {
        return PyNumber_Absolute(x);
    }

The significant difference is the presence of the ``module`` parameter,
which will be satisfied by the stored ``self``
(the module instance)
in the call the CPython ``PyCFunction`` makes.
It isn't used.


Using ``PythonMethod``
======================

We could emulate CPython more closely.
We could declare ``abs`` to be an *instance* method of the module,
and use the annotation ``PythonMethod``, like this:

..  code-block:: java

        @PythonMethod
        @DocString("Return the absolute value of the argument.")
        Object abs(Object x) throws Throwable {
            return PyNumber.absolute(x);
        }

An instance method has access to the module state implicitly of course
(or explicitly as ``this``),
should we need it in our code.

For a function defined by an instance method,
``handle`` is slightly more complicated than in the static case.
The exposer creates a direct ``MethodHandle`` as before in the ``MethodDef``,
but this time of type ``(BuiltinsModule,Object)Object``.

The ``JavaModule`` constructor (per instance)
binds the handle to the instance it is creating (``this``),
to produce a handle of type ``(Object)Object`` as previously.
It writes the *bound* handle in the ``handle`` field
of the ``PyJavaFunction`` representing ``abs``.
Again, the ``self`` field (``__self__``)
will be the module instance ``this`` as expected by Python.

We favour the simplicity of the static approach
in any function where the module state is not needed.
There are few examples of CPython modules with state in the standard library.
The dialect registry of the ``csv`` module provides a case:
methods ``reader``, ``writer`` and methods acting on the registry
(provided from compiled module ``_csv``)
will need the ``PythonMethod`` annotation when we get to them.

Incidentally, we could have declared ``abs`` static
with an explicit ``module`` argument and the ``PythonMethod`` annotation,
which we sometimes do for adopted implementations
in the defining class of a *type*
but there's no reason to do that in a module.
