..  generated-code/function-definition-and-call.rst


Function Definition and Call
############################

Calling a Function
******************

The "Simple" Call
=================


Our first target is to work out how to call a simple function.
As a motivating example,
suppose we wish to call a built-in function like ``n = len(x)``.
In byte code:

..  code-block:: none

    1           0 LOAD_NAME                0 (len)
                2 LOAD_NAME                1 (x)
                4 CALL_FUNCTION            1
                6 STORE_NAME               2 (n)

Most obviously,
we must implement a new opcode ``CALL_FUNCTION``
and this is probably backed by an abstract API method.

In fact,
there are several opcodes the compiler can generate to make a function call,
depending on the pattern of arguments at the call site.
These are somewhat tuned to C and the CPython byte code.
We'll mostly cut through them and get to our own Java ones in time.
Although ``CALL_FUNCTION`` has the simplest expression in byte code,
it does not have the simplest implementation.


The "Classic" Call
==================

Once upon a time,
all calls compiled to the equivalent of ``f(*args, **kwargs)``,
and this was served by the abstract API ``PyObject_Call``
(see "Python internals: how callables work" `Bendersky 2012-03-23`_).
It's still there to cover this most general case.

We'll return to the optimised forms,
but for now,
we will force the compiler back to a "classic" call with ``n = len(*(x,))``.
In byte code:

..  code-block:: none

      1           0 LOAD_NAME                0 (len)
                  2 LOAD_NAME                1 (x)
                  4 BUILD_TUPLE              1
                  6 CALL_FUNCTION_EX         0
                  8 STORE_NAME               2 (n)

Functions are objects,
so we must define a type to represent functions,
and this type must define a ``tp_call`` slot in its type object,
while an instance of that type must designate an executable function.

In fact there must be several types of function object,
since we need to call (at least)
functions defined in Python
and functions defined in Java.
A function must be a PyObject so we can bind it to a name in a namespace.

More generally,
we can recognise that ``len`` could have been any callable object,
including an instance of a user-defined class with a ``__call__`` method,
and since the compiler cannot know anything about the actual target,
the same opcode has to work for all of them.

The implementation of the ``CALL_FUNCTION_EX``
(within ``CPythonFrame.eval``)
is straightforward:

..  code-block:: java

                    case Opcode.CALL_FUNCTION_EX:
                        kwargs = oparg == 0 ? null : valuestack[--sp];
                        args = valuestack[--sp]; // POP
                        func = valuestack[sp - 1]; // TOP
                        res = callFunction(func, args, kwargs);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

The local helper ``callFunction`` allows for iterables as arguments,
which we don't currently support in our toy implementation.
It lands (with a real ``tuple`` and a real ``dict``)
at the abstract API ``Callables.call``:

..  code-block:: java

    class Callables extends Abstract {
        // ...
        static PyObject call(PyObject callable, PyObject args,
                PyObject kwargs) throws TypeError, Throwable {
            try {
                MethodHandle call = callable.getType().tp_call;
                return (PyObject) call.invokeExact(callable, args, kwargs);
            } catch (Slot.EmptyException e) {
                throw typeError(OBJECT_NOT_CALLABLE, callable);
            }
        }

As we can see,
the implementation just supplies the arguments directly to the slot,
which may be empty if the object is not callable.

..  _Bendersky 2012-03-23: https://eli.thegreenplace.net/2012/03/23/python-internals-how-callables-work


The Simple ("Vector") Call
==========================

The classic call protocol involves copying argument data several times,
when done generally.
The call site builds the ``tuple``
and the receiving function or a wrapper unpacks it to argument variables,
on the Java (or C) call stack (for functions defined in that language),
or into the local variables of the frame.
When the signature at the call site is fixed (something like ``f(a, b)``),
the cost of generality becomes frustrating.

CPython has acquired many optimisations
and special cases designed to short-cut the classic call in simple cases,
especially when the target is a C function.
CPython 3.8 takes an optimisation previously used internally,
improves on it somewhat,
and makes it a public API described in `PEP-590`_.
This is the "vector call protocol",
by which is meant that arguments are transferred as an array,
that is in fact a slice of the interpreter stack.

Jython 2 has a comparable optimisation in which
a polymorphic ``PyObject._call`` has optimised forms
with any fixed number of arguments up to 4.
These may therefore come directly from the JVM stack in compiled code.





..  _PEP-590: https://www.python.org/dev/peps/pep-0590


Defining a Function in Java
***************************

A Specialised Callable
======================

We can make a type that defines a ``tp_call`` slot
specific to ``len()`` like this:

..  code-block:: java

    class PyByteCode5 {

        @SuppressWarnings("unused")
        private static class LenCallable implements PyObject {
            static final PyType TYPE = PyType.fromSpec(
                    new PyType.Spec("0TestCallable", LenCallable.class));
            @Override
            public PyType getType() { return TYPE; }

            static PyObject tp_call(LenCallable self, PyObject args,
                    PyObject kwargs) throws Throwable {
                PyObject v = Sequence.getItem(args, 0);
                return Py.val(Abstract.size(v));
            }
        }

We call it for test purposes like this:

..  code-block:: java

        @Test
        void abstract_call() throws TypeError, Throwable {
            PyObject callable = new LenCallable();
            PyObject args = Py.tuple(Py.str("hello"));
            PyObject kwargs = Py.dict();
            PyObject result = Callables.call(callable, args, kwargs);
            assertEquals(Py.val(5), result);
        }

Overriding ``tp_call`` like this works,
and since an instance is a ``PyObject``,
we could bind one to the name "len" in the dictionary of built-ins
that each frame references.
But we need to make this slicker and more general,
and it ought to check the arguments for us.


A Function in a Module
======================

The ``len()`` function belongs to the ``builtins`` module.
This means that the object that represents it
must be entered in the dictionary of that module as the definition of "len".
We have not needed the Python module type before so we quickly define it:

..  code-block:: java

    /** The Python {@code module} object. */
    class PyModule implements PyObject {

        static final PyType TYPE = new PyType("module", PyModule.class);

        @Override
        public PyType getType() { return TYPE; }

        /** Name of this module. **/
        final String name;

        /** Dictionary (globals) of this module. **/
        final PyDictionary dict = new PyDictionary();

        /** The interpreter that created this module. **/
        final Interpreter interpreter;

        PyModule(Interpreter interpreter, String name) {
            this.interpreter = interpreter;
            this.name = name;
        }

        /** Initialise the module instance {@link #dict}. */
        void init() {}
    }

We intend each actual module to extend this class and define init().
Note that each class defining a kind of module may have multiple instances,
since each Interpreter that imports it will create its own.

..  note::

    The member ``interpreter``, recording the owning ``Interpreter``,
    is an innovation relative to CPython that may be ignored for now.

We would like to define the built-in module like this,
imagining some mechanism ``register``,
currently missing from ``PyModule``,
that will put a Python function object wrapping ``len()``
in the module dictionary:

..  code-block:: java
    :emphasize-lines: 10-12, 17

    package uk.co.farowl.vsj2.evo3;

    /** The {@code builtin} module. */
    class BuiltinModule extends PyModule {

        BuiltinModule(Interpreter interpreter) {
            super(interpreter, "builtins");
        }

        static PyObject len(PyObject v) throws Throwable {
            return Py.val(Abstract.size(v));
        }

        @Override
        void init() {
            // Register each method as an exported object
            register("len");
        }
    }

CPython ``PyMethodDef`` and ``PyCFunctionObject``
=================================================

How can we devise the mechanism we need to wrap ``len()``?
As usual, we'll look at CPython for ideas.
Here is the definition from CPython (from ``~/Python/bltinmodule.c``):

..  code-block:: c
    :emphasize-lines: 10-22, 24-26

    /*[clinic input]
    len as builtin_len

        obj: object
        /

    Return the number of items in a container.
    [clinic start generated code]*/

    static PyObject *
    builtin_len(PyObject *module, PyObject *obj)
    /*[clinic end generated code: output=fa7a270d314dfb6c input=bc55598da9e9c9b5]*/
    {
        Py_ssize_t res;

        res = PyObject_Size(obj);
        if (res < 0) {
            assert(PyErr_Occurred());
            return NULL;
        }
        return PyLong_FromSsize_t(res);
    }
    ...
    static PyMethodDef builtin_methods[] = {
        ...
        BUILTIN_LEN_METHODDEF
        ...
        {NULL,              NULL},
    };

Our code is shorter only because we do not need to check for errors:
our strategy is to throw an exception.

A small difference is that in CPython,
the first argument of a module-level function is the module itself,
as if the module were a class and the function a method of it.
In all functions of almost every module of CPython, this is ignored.
In Java, we could make ``len()`` an instance method for the same effect.
However, let's see if we can do without the extra argument.

A large part of the volume in C
is the header that defines the function to `Argument Clinic`_.
This is the gadget that turns a complex comment into code for processing
the arguments and built-in documentation.
In this case, the results are simple.
(There is no intermediate ``builtin_len_impl``.)
The generated code is in ``~/Python/clinic/bltinmodule.c.h``,
and provides a modified version of the special comment as a doc-string,
and a macro that can form one line of the method definition table.

..  code-block:: c
    :emphasize-lines: 8

    PyDoc_STRVAR(builtin_len__doc__,
    "len($module, obj, /)\n"
    "--\n"
    "\n"
    "Return the number of items in a container.");

    #define BUILTIN_LEN_METHODDEF    \
        {"len", (PyCFunction)builtin_len, METH_O, builtin_len__doc__},

The important part of this for us at present is the use of ``PyMethodDef``
to describe the function,
and particularly ``METH_O``, which is a setting of the ``flags`` field.
The handling of a call by a ``PyCFunctionObject``,
which represents function or method defined in C,
and stored in field ``meth``,
is steered by these flags.

Only a few combinations of flags are valid,
and each corresponds to a supported signature in C.

.. csv-table:: CPython ``PyMethodDef`` signatures
   :header: "Flags", "Type of ``meth``", "Call made"
   :widths: 10, 20, 30

    "``METH_NOARGS``", "``PyCFunction``", "``(*meth) (self, NULL)``"
    "``METH_O``", "``PyCFunction``", "``(*meth) (self, args[0])``"
    "``METH_VARARGS``", "``PyCFunction``", "``(*meth) (self, argtuple)``"
    "``METH_VARARGS | METH_KEYWORDS``", "``PyCFunctionWithKeywords``", "``(*meth) (self, argtuple, kwdict)``"
    "``METH_FASTCALL``", "``_PyCFunctionFast``", "``(*meth) (self, args, nargs)``"
    "``METH_FASTCALL | METH_KEYWORDS``", "``_PyCFunctionFastWithKeywords``", "``(*meth) (self, args, nargs, kwnames)``"

Here ``self`` is the module or target object,
``argtuple`` is a ``tuple`` of positional arguments,
``kwdict`` is a keyword ``dict`` (all these are as in the classic call),
``args`` is an array of positional arguments followed by keyword ones,
``kwnames`` is a tuple of the names of the keyword arguments in that array,
and ``nargs`` is the number of positional arguments.
``args`` may actually be a pointer into the stack,
where we can find the ``nargs + len(kwnames)`` arguments,
placed there by the ``CALL_FUNCTION`` opcode.

Although the table shows the same type ``PyCFunction``
for three of the flag configurations,
this is not ambiguous.
The flags control how the arguments will be presented,
not the actual arguments to the call.
The built-in functions ``locals()`` (takes no arguments),
``len()`` (takes one argument), and
``vars()`` (takes zero arguments or one),
have the same C signatures but are defined as
``METH_NOARGS``, ``METH_O`` and ``METH_VARARGS`` respectively.

The allowable types of ``meth``
are defined in the C header ``methodobject.h``,
and ``meth`` may need to be cast to one of them to make the call correct:

..  code-block:: c

    typedef PyObject *(*PyCFunction)(PyObject *, PyObject *);
    typedef PyObject *(*_PyCFunctionFast)
                (PyObject *, PyObject *const *, Py_ssize_t);
    typedef PyObject *(*PyCFunctionWithKeywords)
                (PyObject *, PyObject *, PyObject *);
    typedef PyObject *(*_PyCFunctionFastWithKeywords)
                (PyObject *, PyObject *const *, Py_ssize_t,  PyObject *);
    typedef PyObject *(*PyNoArgsFunction)(PyObject *);

As we have seen,
`Argument Clinic`_ generates the PyMethodDef for a function,
assigning the flags based on the text signature in its input.
The signature of the implementation function
would not be enough to determine the flags.

.. _Argument Clinic: https://docs.python.org/3/howto/clinic.html


Java ``MethodDef`` and ``JavaFunction``
=======================================

..  We try not to put Py as a prefix unless it's a PyObject
    and Object as a suffix seems unnecessary.

We now look for a way to describe functions
that is satisfactory for a Java implementation of Python.
The ``builtin_function_or_method`` class is a visible feature
(a.k.a. ``PyCFunctionObject``),
so we define a corresponding ``JavaFunction`` class,
which will represent built-in functions and methods.

.. For a while I toyed with distinct JavaFunction and JavaMethod.

We take more liberties in defining ``MethodDef`` behind the scenes.
The allowable flag configurations and signatures,
apart from the equivalent of ``f([self,] *args, **kwargs)``,
are an implementation choice.
Even efficient support for ``CALL_FUNCTION``
(the "stack slice" protocol, or fast call)
is advantageous for CPython byte code but not particularly for a JVM.




Defining a Function in Python
*****************************

