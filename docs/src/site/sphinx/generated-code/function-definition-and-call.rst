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
Our own call sites tuned to Java are a distant gleam in the eye,
so we'll give some attention to CPython's optimisations.
However,
although ``CALL_FUNCTION`` has the simplest expression in byte code,
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
A function must be a ``PyObject`` so we can bind it to a name in a namespace.

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
                        res = Callables.call(func, args, kwargs);
                        valuestack[sp - 1] = res; // SET_TOP
                        break;

We differ from CPython in dispatching promptly to the abstract interface
``Callables.call``,
where CPython deals with the variety of possible arguments.
The signature of ``Callables.call(PyObject, PyObject)``
accepts all-comers,
but uses of the equivalent CPython ``PyObject_Call``
have to be guarded with argument checks and conversions.
That could usefully be inside the API method:

..  code-block:: java

    class Callables extends Abstract {
        // ...
        static PyObject call(PyObject callable, PyObject args,
                PyObject kwargs) throws TypeError, Throwable {

            // Represent kwargs as a dict (if not already or null)
            PyDict kw;
            if (kwargs == null || kwargs instanceof PyDict)
                kw = (PyDict) kwargs;
            else // ...

            // Represent args as a PyTuple (if not already)
            PyTuple ar;
            if (args instanceof PyTuple)
                ar = (PyTuple) args;
            else  // ...

            try {
                MethodHandle call = callable.getType().tp_call;
                return (PyObject) call.invokeExact(callable, ar, kw);
            } catch (Slot.EmptyException e) {
                throw typeError(OBJECT_NOT_CALLABLE, callable);
            }
        }

As we can see,
the implementation just supplies the checked arguments directly to the slot,
which may be empty if the object is not callable.
In the missing ``else`` clauses
we will eventually convert iterables to the necessary types.

Another slight difference from CPython,
is that we make the signature of our ``tp_slot`` strict about type:

..  code-block:: java

    enum Slot {
        // ...
        tp_call(Signature.CALL), //
        // ...

        enum Signature implements ClassShorthand {
            // ...
            CALL(O, S, TUPLE, DICT), // **

This means that receiving implementations
do not have to check and cast their arguments.

The possible variety of arguments at a call site is not always appreciated.
A special opcode supports the concatenation of positional arguments
into a single ``tuple`` for the call:

..  code-block:: python

    >>> def f(*args, **kwargs): print(args, "\nkw =", kwargs)
    ...
    >>> f(0,1,*(2,3),None,*(4,5,6))
    (0, 1, 2, 3, None, 4, 5, 6)
    kw = {}
    >>> dis.dis(compile("f(0, 1, *(2,3), None, *(4,5,6))", "<test>", "eval"))
      1           0 LOAD_NAME                0 (f)
                  2 LOAD_CONST               5 ((0, 1))
                  4 LOAD_CONST               2 ((2, 3))
                  6 LOAD_CONST               6 ((None,))
                  8 LOAD_CONST               4 ((4, 5, 6))
                 10 BUILD_TUPLE_UNPACK_WITH_CALL     4
                 12 CALL_FUNCTION_EX         0
                 14 RETURN_VALUE

And similarly for keyword arguments:

..  code-block:: python

    >>> f(1, 2, *(3,4), a=10, b=20, **{'x':30, 'y':40})
    (1, 2, 3, 4)
    kw = {'a': 10, 'b': 20, 'x': 30, 'y': 40}
    >>> source = "f(1, 2, *(3,4), a=10, b=20, **{'x':30, 'y':40})"
    >>> dis.dis(compile(source, "<test>", "eval"))
      1           0 LOAD_NAME                0 (f)
                  2 LOAD_CONST               9 ((1, 2))
                  4 LOAD_CONST               2 ((3, 4))
                  6 BUILD_TUPLE_UNPACK_WITH_CALL     2
                  8 LOAD_CONST               3 (10)
                 10 LOAD_CONST               4 (20)
                 12 LOAD_CONST               5 (('a', 'b'))
                 14 BUILD_CONST_KEY_MAP      2
                 16 LOAD_CONST               6 (30)
                 18 LOAD_CONST               7 (40)
                 20 LOAD_CONST               8 (('x', 'y'))
                 22 BUILD_CONST_KEY_MAP      2
                 24 BUILD_MAP_UNPACK_WITH_CALL     2
                 26 CALL_FUNCTION_EX         1
                 28 RETURN_VALUE

All sorts of exciting combinations are thereby reduced to the classic call.
The supporting opcodes are easy to implement
although at present we may do so only incompletely.
Again this is because we have not yet implemented iterables.
What we have will work for the examples.

..  _Bendersky 2012-03-23: https://eli.thegreenplace.net/2012/03/23/python-internals-how-callables-work



The Simple ("Vector") Call
==========================

The classic call protocol involves copying argument data
at least twice, generally.
The call site builds the ``tuple`` from items on the stack,
and the receiving function or a wrapper unpacks it to argument variables,
on the Java (or C) call stack,
or into the local variables of the frame.
When the signature at the call site is fixed,
something like ``f(a, b)``,
the cost of generality becomes frustrating.

CPython 3.8 takes an optimisation previously used internally,
improves on it somewhat,
and makes it a public API described in `PEP-590`_.

This is the "vector call protocol",
by which is meant that arguments are found in an array that is,
in fact,
a slice of the interpreter stack.
It requires that the target C function be capable of receiving that way
(the object implementing a compiled Python function is made capable),
and it requires a different call sequence to be generated by the compiler,
which it does whenever the argument list is simple enough.
The machinery between the new call opcodes and the target
is able to tell whether the receiving function object
implements the vectorcall protocol,
and will form a tuple if it does not.

Jython 2 has a comparable optimisation in which
a polymorphic ``PyObject._call`` has optimised forms
with any fixed number of arguments up to 4.
These come directly from the JVM stack in compiled code.
We are interested in the vector call
in order to implement it for the Python byte code interpreter.
Python compiled to the JVM will have something quite different.

..  _PEP-590: https://www.python.org/dev/peps/pep-0590


Defining a Function in Java
***************************

..  _a-specialised-callable:

A Specialised Callable
======================

We can make a type that defines a ``tp_call`` slot
specific to ``len()`` like this:

..  code-block:: java

    class PyByteCode5 {

        @SuppressWarnings("unused")
        private static class LenCallable implements PyObject {
            static final PyType TYPE = PyType.fromSpec(
                    new PyType.Spec("00LenCallable", LenCallable.class));
            @Override
            public PyType getType() { return TYPE; }

            static PyObject tp_call(LenCallable self, PyTuple args,
                    PyDict kwargs) throws Throwable {
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
But we need to make this slicker and more general.


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

        final String name;
        final PyDict dict = new PyDict();

        PyModule(String name) { this.name = name; }

        /** Initialise the module instance. */
        void init() {}

        @Override
        public String toString() {
            return String.format("<module '%s'>", name);
        }
    }

We intend each actual module to extend this class and define ``init()``.
Note that each class defining a kind of module may have multiple instances,
since each ``Interpreter`` that imports it will create its own.

We would like to define the built-in module somewhat like this:

..  code-block:: java
    :emphasize-lines: 5-7, 12

    class BuiltinsModule extends JavaModule implements Exposed {

        BuiltinsModule() { super("builtins"); }

        static PyObject len(PyObject v) throws Throwable {
            return Py.val(Abstract.size(v));
        }

        @Override
        void init() {
            // Register each method as an exported object
            register("len");
        }
    }

We are imagining some mechanism ``register``,
currently missing from ``PyModule``,
that will put a Python function object wrapping ``len()``
in the module dictionary.
It would be nice to have some mechanism do this registration
automagically  behind the scenes.


CPython ``PyMethodDef`` and ``PyCFunctionObject``
=================================================

How can we devise the mechanism we need to wrap ``len()``?
As usual, we'll look at CPython for ideas.
Here is the definition from CPython (from ``~/Python/bltinmodule.c``):

..  code-block:: c
    :emphasize-lines: 2, 10-22, 26

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
        BUILTIN_LOCALS_METHODDEF
        {"max",    (PyCFunction)(void(*)(void))builtin_max,
                METH_VARARGS | METH_KEYWORDS, max_doc},
        {"min",    (PyCFunction)(void(*)(void))builtin_min,
                METH_VARARGS | METH_KEYWORDS, min_doc},
        ...
        BUILTIN_SUM_METHODDEF
        {"vars",   builtin_vars, METH_VARARGS, vars_doc},
        {NULL,              NULL},
    };

The code itself is simple.
Ours is shorter than CPython's because our errors throw an exception.
A small difference is that in CPython,
the first argument of a module-level function is the module itself,
as if the module were a class and the function a method of it.
In all the functions of almost every module of CPython,
this module argument is ignored.
Very occasionally, some per-module storage is accessed.
In Java, we would get the same effect by making ``len()`` an instance method,
and the per-module storage would be the instance variables.
Let's see if we can do without the extra argument.

We can see that in a CPython module,
functions are described in a `method table`_.
When encountered in the CPython standard library modules,
we find that many of the rows are the expansion of a macro.

A large part of the volume in C
is the header that defines the function to `Argument Clinic`_.
This is the gadget that turns a complex comment into code for processing
the arguments and built-in documentation.
In this case, the results are simple.
(There is no intermediate ``builtin_len_impl``.)
The generated code is in ``~/Python/clinic/bltinmodule.c.h``,
and provides a modified version of the special comment as a doc-string,
and the macro that fills one line of the method definition table.

..  code-block:: c
    :emphasize-lines: 7-8

    PyDoc_STRVAR(builtin_len__doc__,
    "len($module, obj, /)\n"
    "--\n"
    "\n"
    "Return the number of items in a container.");

    #define BUILTIN_LEN_METHODDEF    \
        {"len", (PyCFunction)builtin_len, METH_O, builtin_len__doc__},

The important part of this for us at present is the use of ``PyMethodDef``
to describe the function,
and particularly ``METH_O``, which is a setting of the ``ml_flags`` field,
and the pointer to function stored in field ``ml_meth``.
The handling of a call by a ``PyCFunctionObject``,
which represents a function (or method) defined in C,
is steered by this data.

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

Although the table shows the same C type ``PyCFunction``
for three of the flag configurations,
this is not ambiguous.
The flags, not the type, control how the arguments will be presented.
The built-in functions ``locals()`` (takes no arguments),
``len()`` (takes one argument), and
``vars()`` (takes zero arguments or one),
have the same ``PyCFunction`` signature,
but their flag settings are
``METH_NOARGS``, ``METH_O`` and ``METH_VARARGS`` respectively.

The allowable types of ``ml_meth``
are defined in the C header ``methodobject.h``,
and ``ml_meth`` may need to be cast to one of them to make the call correct:

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
`Argument Clinic`_ generates the ``PyMethodDef`` for a function,
assigning the flags based on the text signature in its input.
The signature in C of the implementation function
would not be enough to determine the flags.

.. _method table: https://docs.python.org/3/extending/extending.html#the-module-s-method-table-and-initialization-function
.. _Argument Clinic: https://docs.python.org/3/howto/clinic.html


.. _MethodDef-and-PyJavaFunction:

Java ``MethodDef`` and ``PyJavaFunction``
=========================================

..  We try not to put Py as a prefix unless it's a PyObject
    and Object as a suffix seems unnecessary.

We now look for a way to describe functions
that is satisfactory for a Java implementation of Python.
The CPython version is quite complicated
and it has not been easy to distill the essential idea.

The ``builtin_function_or_method`` class (a.k.a. ``PyCFunctionObject``)
is a visible feature,
so we define a corresponding ``PyJavaFunction`` class,
which will represent built-in functions.
The essence of that class is as follows:

..  code-block:: java
    :emphasize-lines: 7-8, 12, 19

    /** The Python {@code builtin_function_or_method} object. */
    class PyJavaFunction implements PyObject {

        static final PyType TYPE = new PyType("builtin_function_or_method",
                PyJavaFunction.class);
        //...
        final MethodDef methodDef;
        final MethodHandle tpCall;

        PyJavaFunction(MethodDef def) {
            this.methodDef = def;
            this.tpCall = getTpCallHandle(def);
        }
        //...

        static PyObject tp_call(PyJavaFunction f, PyTuple args,
                PyDict kwargs) throws Throwable {
            try {
                return (PyObject) f.tpCall.invokeExact(args, kwargs);
            } catch (BadCallException bce) {
                f.methodDef.check(args, kwargs);
                // never returns ...
            }
        }
    }

Just like in CPython's ``PyCFunction``,
our ``PyJavaFunction`` is linked to a method definition (``MethodDef``)
that supplies the name, characteristics and documentation string.
The implementation of ``tp_call`` is one line,
passing on the (classic) arguments,
plus a catch that turns a simple lightweight ``BadCallException``,
thrown when the number or kind of arguments is incorrect,
into a proper ``TypeError`` diagnosed by the ``MethodDef``.

Our ``MethodDef`` (greatly simplified) looks like this:

..  code-block:: java
    :emphasize-lines: 3

    class MethodDef {
        final String name;
        final MethodHandle meth;
        final EnumSet<Flag> flags;
        final String doc;

        enum Flag {VARARGS, KEYWORDS, FASTCALL}

        MethodDef(String name, MethodHandle mh, EnumSet<Flag> flags,
                String doc) {
            this.name = name;
            this.meth = mh;
            this.doc = doc;
            this.flags = calcFlags(flags);
        }

        //...

        void check(PyTuple args, PyDict kwargs) throws TypeError {
            // Check args, kwargs for the case defined by flags and
            // throw a properly formatted TypeError
            // ...
        }

        int getNargs() {
            MethodType type = meth.type();
            int n = type.parameterCount();
            return flags.contains(Flag.STATIC) ? n : n - 1;
        }
    }

We do not define the flags ``METH_NOARGS`` and ``METH_O``
used by CPython to represent special cases in the number of arguments,
but we have a ``Nargs()`` function valid when ``VARARGS`` is not present.
``calcFlags`` examines the ``MethodHandle mh``
to decide whether it represents a fixed arity or ``VARARGS`` type,
and whether it has ``KEYWORDS``.

Each of the objects ``MethodDef`` and ``PyJavaFunction``
contains a ``MethodHandle``: what is the difference?

``MethodDef.meth`` is the handle of the method as defined in the module.
Its type conforms to small set of allowable signatures.
The allowable flag configurations and module-level signatures
are an implementation choice for a Java Python:
we do not have to mimic CPython.

``PyJavaFunction.tpCall`` wraps ``PyJavaFunction.methodDef.meth``
to conform to the signature ``(PyTuple,PyDict)PyObject``.
This reflects the ``(*args, **kwargs)`` calling pattern that we must support.
This handle is built by ``PyJavaFunction.getTpCallHandle``,
when invoked from the constructor.

Building this is a little complicated,
so we break it down into a helper for each major type of target signature.
Here is the one for a fixed-arity function like ``len()``:

..  code-block:: java

    class PyJavaFunction implements PyObject {
        // ...
        private static class Util {
            // ... Many method handles defined here!
            static MethodHandle wrapFixedArity(MethodDef def) {
                // Number of arguments expected by the def target f
                int n = def.getNargs();
                // f = λ u0, u1, ... u(n-1) : meth(u0, u1, ... u(n-1))
                MethodHandle f = def.meth;
                // fv = λ v k : meth(v[0], v[1], ... v[n-1])
                MethodHandle fv =
                        dropArguments(f.asSpreader(OA, n), 1, DICT);
                // argsOK = λ v k : (k==null || k.empty()) && v.length==n
                MethodHandle argsOK =
                        insertArguments(fixedArityGuard, 2, n);
                // Use the guard to switch between calling and throwing
                // g = λ v k : argsOK(v,k) ? fv(v,k) : throw BadCall
                MethodHandle g = guardWithTest(argsOK, fv, throwBadCallOA);
                // λ a k : g(a.value, k)
                return filterArguments(g, 0, getValue);
            }

            private static boolean fixedArityGuard(PyObject[] a,
                    PyDict d, int n) {
                return (d == null || d.size() == 0) && a.length == n;
            }
        }
    }

At the time of writing,
support for FASTCALL is incomplete.
It may be sufficient simply to form a ``tuple`` from the stack slice.
Efficient support for ``CALL_FUNCTION``
is advantageous for CPython byte code but not at all in JVM byte code,
where we cannot address the JVM stack as a memory array.


Defining a Function in Python
*****************************

Definition site
===============

A function definition in Python is an executable statement.
With the CPython compiler doing the hard part,
of turning the body of a function into a code object,
our interest is only in the execution of the byte code
that creates the functin object at run time.
If we write:

..  code-block:: python

    def f(x, y, a=5, b=6):
        return x * y + a * b

That code typically looks like this:

..  code-block:: none

    1       0 LOAD_CONST              11 ((5, 6))
            2 LOAD_CONST               2 (<code object f at ...>)
            4 LOAD_CONST               3 ('f')
            6 MAKE_FUNCTION            1 (defaults)
            8 STORE_NAME               0 (f)

The body of the function is wrapped up in the constant,
and all that happens at this definition site
is to supply a name and the defaults for positional arguments.
``MAKE_FUNCTION`` has four options:
depending on whether positional or keyword-only defaults are given,
annotations, or a closure.
The implementation of the opcode is a little fiddly
because of the need to unstack all the optional arguments,
but it lands fairly directly in the constructor of this class:

..  code-block:: java
    :emphasize-lines: 16, 20

    class PyFunction implements PyObject {
        static final PyType TYPE = new PyType("function", PyFunction.class);
        ...
        PyCode code;
        final PyDict globals;
        PyObject[] defaults;
        PyDict kwdefaults;
        PyCell[] closure;
        PyObject doc;
        PyUnicode name;
        PyDict dict;
        PyObject module;
        PyDict annotations;
        PyUnicode qualname;

        final Interpreter interpreter;

        PyFunction(Interpreter interpreter, PyCode code, PyDict globals,
                PyUnicode qualname) {
            this.interpreter = interpreter;
            setCode(code);
            this.globals = globals;
            this.name = code.name;
            ...
        }
        ...
        @Override
        public String toString() {
            return String.format("<function %s>", name);
        }
    }

The CPython equivalent is ``PyFunctionObject``
and the construction of it is at ``PyFunction_NewWithQualName``.
Like CPython,
we allow most of the member fields to be ``null`` if they are not used,
representing that as ``None`` externally.
Those that CPython makes tuples,
for example ``closure`` and ``defaults``,
we find it slightly simpler to make correctly-typed arrays internally.

In an important design difference from CPython,
we explicitly store the interpreter that is current at the time of definition.
This is so that body code executes with the same "import context",
wherever it is called from.


Classic call site
=================

We shall take a fairly complicated example that leads to classic call:

..  code-block:: python

    def f(x, y, *args):
        return x * y + args[0] * args[1]
    y = f(u+1, v-1, *args)

..  code-block:: none

    1           0 LOAD_CONST               0 (<code object f at ... >)
                2 LOAD_CONST               1 ('f')
                4 MAKE_FUNCTION            0
                6 STORE_NAME               0 (f)

    3           8 LOAD_NAME                0 (f)
               10 LOAD_NAME                1 (u)
               12 LOAD_CONST               2 (1)
               14 BINARY_ADD
               16 LOAD_NAME                2 (v)
               18 LOAD_CONST               2 (1)
               20 BINARY_SUBTRACT
               22 BUILD_TUPLE              2
               24 LOAD_NAME                3 (args)
               26 BUILD_TUPLE_UNPACK_WITH_CALL     2
               28 CALL_FUNCTION_EX         0
               30 STORE_NAME               4 (y)
               32 LOAD_CONST               3 (None)
               34 RETURN_VALUE

As we have seen, the opcode ``CALL_FUNCTION_EX``
presents the positional arguments as a single tuple to ``Callables.call()``,
and in this case there are no keyword arguments,
so the keyword arguments dictionary is ``null``.
As the callable is a ``PyFunction``,
we end up in the slot function ``PyFunction.tp_call`` with these arguments.

This brings us to the problem of getting these arguments,
and the default values from the function definition,
into the right local variables of the frame.
There is quite some scope for the arguments not to match the definition.
In CPython, around 500 lines of ``ceval.c`` are devoted to this,
and to handling the errors that may arise,
and another 100 in ``call.c`` preparing to do so.
We will place most of this processing in
either the ``PyFunction`` object
or the ``PyFrame`` object itself.


Processing a classic call
=========================

We take a frontal approach to ``PyFunction.tp_call``.
We prepare a frame with all the variables initialised,
then call ``eval()`` on that frame:

..  code-block:: java

    class PyFunction implements PyObject { ...

        static PyObject tp_call(PyFunction func, PyTuple args,
                PyDict kwargs) throws Throwable {
            PyFrame frame = func.createFrame(args, kwargs);
            return frame.eval();
        }

The objective of the processing in ``PyFunction.createFrame``
is to prepare a frame in which arguments, default values, and the closure
have been used to initialise the local variables.

Our model is in CPython ``ceval.c`` at ``_PyEval_EvalCodeWithName``,
and our logic is the same, but the detail has evolved a lot.
We have many fewer arguments than in CPython,
since we make use of the fact we have object context
to reach the information we need.
We have not implemented the function variants
(co-routines, etc.)
but the approach here can extend to that with minor changes.

..  code-block:: java

    class PyFunction implements PyObject { ...

        /** Prepare the frame from "classic" arguments. */
        protected PyFrame createFrame(PyTuple args, PyDict kwargs) {

            PyFrame frame = code.createFrame(interpreter, globals, closure);
            final int nargs = args.value.length;

            // Set parameters from the positional arguments in the call.
            frame.setPositionalArguments(args);

            // Set parameters from the keyword arguments in the call.
            if (kwargs != null && !kwargs.isEmpty())
                frame.setKeywordArguments(kwargs);

            if (nargs > code.argcount) {

                if (code.traits.contains(Trait.VARARGS)) {
                    // Locate the * parameter in the frame
                    int varIndex = code.argcount + code.kwonlyargcount;
                    // Put the excess positional arguments there
                    frame.setLocal(varIndex, new PyTuple(args.value,
                            code.argcount, nargs - code.argcount));
                } else {
                    // Excess positional arguments but no VARARGS for them.
                    throw tooManyPositional(nargs, frame);
                }

            } else { // nargs <= code.argcount

                if (code.traits.contains(Trait.VARARGS)) {
                    // No excess: set the * parameter in the frame to empty
                    int varIndex = code.argcount + code.kwonlyargcount;
                    frame.setLocal(varIndex, PyTuple.EMPTY);
                }

                if (nargs < code.argcount) {
                    // Set remaining positional parameters from default
                    frame.applyDefaults(nargs, defaults);
                }
            }

            if (code.kwonlyargcount > 0)
                // Set keyword parameters from default values
                frame.applyKWDefaults(kwdefaults);

            // Create cells for bound variables
            if (code.cellvars.length > 0)
                frame.makeCells();

            return frame;
        }

It becomes clear that, after creating an empty frame,
the variables are initialised in a number of optional steps.
We steer a path through these steps by a combination of
information fixed in the code object,
and information fixed at the call site.
Each step is given to the frame object to carry out.
The data used in the steps is also partly fixed in those places,
or in the function object itself.
This observation is the basis of some optimisations
we shall come to when we consider the vector call case.

Recall that ``PyFrame`` is an abstract class.
The methods used here are part of the abstract API,
and available therefore to be overridden in each implementation.
We only have one implementation so far, namely ``CPythonFrame``,
but envisage that a function body compiled to JVM byte code
would create a different subclass of ``PyFrame``.

As an example,
consider the implementation of ``PyFrame.setPositionalArguments``.
We supply a base implementation thus:

..  code-block:: java

    abstract class PyFrame implements PyObject { ...

        /** Get the local variable named by {@code code.varnames[i]} */
        abstract PyObject getLocal(int i);

        /** Set the local variable named by {@code code.varnames[i]} */
        abstract void setLocal(int i, PyObject v);

        void setPositionalArguments(PyTuple args) {
            int n = Math.min(args.value.length, code.argcount);
            for (int i = 0; i < n; i++)
                setLocal(i, args.value[i]);
        }

It would be sufficient for ``CPythonFrame``
to implement ``getLocal``, ``setLocal`` and a few other simple methods,
alongside ``eval`` of course,
but the option is available to provide a more efficient version,
using its direct access to ``CPythonFrame.fastlocals``.
``CPythonFrame`` uses this freedom to replace the loop with an array copy:

..  code-block:: java

    class CPythonFrame extends PyFrame { ...
        @Override
        PyObject getLocal(int i) { return fastlocals[i]; }

        @Override
        void setLocal(int i, PyObject v) { fastlocals[i] = v; }

        @Override
        void setPositionalArguments(PyTuple args) {
            int n = Math.min(args.value.length, code.argcount);
            System.arraycopy(args.value, 0, fastlocals, 0, n);
        }

Another sub-class of ``PyFrame``
need not keep its variables in a ``fastlocals`` array at all,
as long as it can correlate them by index with the name in ``PyCode``
(the name tables ``varnames``, ``freevars`` and ``cellvars``).


Processing a vector call
========================

In the vector call,
arguments are on the CPython stack,
including arguments given by keyword.
If we implement a ``tp_vectorcall`` slot just as we have ``tp_call``,
then the abstract gateway ``Callables.vectorcall``,
called in the ``CALL_FUNCTION`` or ``CALL_FUNCTION_KW`` opcode,
lands us at ``PyFunction.tp_vectorcall``.
Apart from the arguments, this should be familiar from the previous section.

..  code-block:: java

    class PyFunction implements PyObject { ...

        static PyObject tp_vectorcall(PyFunction func, PyObject[] stack,
                int start, int nargs, PyTuple kwnames) throws Throwable {
            PyFrame frame = func.createFrame(stack, start, nargs, kwnames);
            return frame.eval();
        }

We deal with the vector call argument style by reproducing
``createFrame`` in that style.
The logic is the same,
and the methods that represent the steps in the logic are
either the same as, or simple counterparts of, those seen before.

..  code-block:: java

    class PyFunction implements PyObject { ...

        /** Prepare the frame from CPython vector call arguments. */
        protected PyFrame createFrame(PyObject[] stack, int start,
                int nargs, PyTuple kwnames) {

            int nkwargs = kwnames == null ? 0 : kwnames.value.length;

            // Optimisation elided ...

            PyFrame frame = code.createFrame(interpreter, globals, closure);

            // Set parameters from the positional arguments in the call.
            frame.setPositionalArguments(stack, start, nargs);

            // Set parameters from the keyword arguments in the call.
            if (nkwargs > 0)
                frame.setKeywordArguments(stack, start + nargs,
                        kwnames.value);

            if (nargs > code.argcount) {

                if (code.traits.contains(Trait.VARARGS)) {
                    // Locate the *args parameter in the frame
                    int varIndex = code.argcount + code.kwonlyargcount;
                    // Put the excess positional arguments there
                    frame.setLocal(varIndex, new PyTuple(stack,
                            start + code.argcount, nargs - code.argcount));
                } else {
                    // Excess positional arguments but no VARARGS for them.
                    throw tooManyPositional(nargs, frame);
                }

            } else { // nargs <= code.argcount

                if (code.traits.contains(Trait.VARARGS)) {
                    // No excess: set the * parameter in the frame to empty
                    int varIndex = code.argcount + code.kwonlyargcount;
                    frame.setLocal(varIndex, PyTuple.EMPTY);
                }

                if (nargs < code.argcount) {
                    // Set remaining positional parameters from default
                    frame.applyDefaults(nargs, defaults);
                }
            }

            if (code.kwonlyargcount > 0)
                // Set keyword parameters from default values
                frame.applyKWDefaults(kwdefaults);

            // Create cells for bound variables
            if (code.cellvars.length > 0)
                frame.makeCells();

            return frame;
        }

In the listing above we have omitted a fast-path optimisation
which we now turn to discuss.


Possibilities for Optimisation
==============================

.. _vector-call-fast-path-optimisation:

Fast path in simple cases
-------------------------

In the logic of ``createFrame`` we see that if ``nargs == code.argcount``,
meaning exactly the right number of arguments is passed,
and no keyword arguments are allowed (or given),
most of the steps are skipped.
If the code object also meets certain criteria,
all steps are skipped apart from setting the positional arguments.

This is the basis of the optimisation elided from the code above,
and now shown here:

..  code-block:: java
    :emphasize-lines: 9-14

    class PyFunction implements PyObject { ...

        /** Prepare the frame from CPython vector call arguments. */
        protected PyFrame createFrame(PyObject[] stack, int start,
                int nargs, PyTuple kwnames) {

            int nkwargs = kwnames == null ? 0 : kwnames.value.length;

            if (fast && nargs == code.argcount && nkwargs==0)
                // Fast path possible
                return code.fastFrame(interpreter, globals, stack, start);
            else if (fast0 && nargs == 0)
                // Fast path possible
                return code.fastFrame(interpreter, globals, defaults, 0);

            // Slow path
            PyFrame frame = code.createFrame(interpreter, globals, closure);
            ...
        }

This follows CPython in taking a fast path in the simple case where
the arguments in the call exactly satisfy the parameters of the function,
or they are all taken from the defaults.

``PyCode.fastFrame`` rolls
``PyCode.createFrame`` and ``PyFrame.setPositionalArguments`` into one,
taking advantage of the knowledge that there are
no cell variables or varargs arguments to consider.
It is roughly CPython ``call.c`` at ``function_code_fastcall()``,
but without the call to ``PyEval_EvalFrameEx()``.

The boolean fields ``fast`` and ``fast0`` are pre-computed
inside the setters for fields ``code`` and ``defaults``:

..  code-block:: java

    class PyFunction implements PyObject { ...

        boolean fast, fast0;
        ...
        void setCode(PyCode code) {
            this.code = code;
            this.fast = code.kwonlyargcount == 0
                    && code.traits.equals(FAST_TRAITS);
            this.fast0 = this.fast && defaults != null
                    && defaults.length == code.argcount;
        }

        void setDefaults(PyTuple defaults) {
            this.defaults = defaults;
            this.fast0 = this.fast && defaults != null
                    && defaults.length == code.argcount;
        }

        private static final EnumSet<Trait> FAST_TRAITS =
                EnumSet.of(Trait.OPTIMIZED, Trait.NEWLOCALS, Trait.NOFREE);

It is not sufficient to do this once in the constructor,
for two reasons:

1.  The ``__code__`` attribute of a ``function`` is (surprisingly)
    writable from Python.
    It's confusing, but you can do it, and it invalidates the optimisation.
2.  While the ``__defaults__`` attribute of a ``function`` is
    read-only from Python,
    the ``MAKE_FUNCTION`` opcode sets it after constructing the function,
    so it must be writable from Java and ``fast0`` recomputed each time.


Possibility of using ``MethodHandle``
-------------------------------------

The CPython vector call presents an interesting case.
It involves a call through a pointer to a function,
which is in the instance function object.
This is not like a call through a slot function,
which is in the type object.

The type object does contain a slot associated with vector call protocol,
but (if not empty) it contains an offset in the instance,
at which the pointer to function may be found.
The offset (or its absence) is characteristic of the type,
but the particular function pointer is specific to the instance.
It is obtained (see ``abstract.h``) like this:

..  code-block:: C

    static inline vectorcallfunc
    _PyVectorcall_Function(PyObject *callable)
    {
        PyTypeObject *tp = Py_TYPE(callable);
        Py_ssize_t offset = tp->tp_vectorcall_offset;
        vectorcallfunc *ptr;
        if (!PyType_HasFeature(tp, _Py_TPFLAGS_HAVE_VECTORCALL)) {
            return NULL;
        }
        ptr = (vectorcallfunc*)(((char *)callable) + offset);
        return *ptr;
    }

It is used essentially as ``res = ptr(callable, args, nargs, kwnames)``.

Another, long-standing example of this use of an offset,
is the way in which the instance dictionary is located
(simplifying a little):

..  code-block:: C

    PyObject **
    _PyObject_GetDictPtr(PyObject *obj)
    {
        Py_ssize_t dictoffset;
        PyTypeObject *tp = Py_TYPE(obj);
        dictoffset = tp->tp_dictoffset;
        ...
        return (PyObject **) ((char *)obj + dictoffset);
    }

In the case of a function,
the pointer in question is always to ``_PyFunction_Vectorcall``,
which is functionally equivalent to our ``createFrame``
(with the conditional :ref:`vector-call-fast-path-optimisation`).
However,
CPython tries harder in its ``PyCFunctionObject``
(Python ``builtin_function_or_method``).
What is the equivalent idea in Java?

A Java ``VarHandle`` is the obvious choice,
if we want a reference through which we may manipulate the pointer.
However, since the objective is to call the target,
we could build the optimisation into the ``MethodHandle`` ``tp_vectorcall``,
that we already defined.
The handle would have to interrogate the function object passed as argument,
and the numbers of positional and keyword arguments,
expressing the fast path logic of ``createFrame`` in handles,
and falling back to the plain version.

Bearing in mind the ultimate implementation of ``CALL_FUNCTION``
should be a Java ``CallSite``
that has full knowledge of the ``nargs`` and ``kwnames``,
we should rather think of a handle that can be inserted there,
specific to the call site and function,
and re-linkable if the function (or its ``__code__`` attribute) should change.

We therefore draw a line here on optimisation of function calls
in the CPython byte code interpreter.
(Ours runs at about 2/3 the speed of CPython, in trivial tests.)
