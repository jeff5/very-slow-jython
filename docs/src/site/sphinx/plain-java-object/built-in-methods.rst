..  plain-java-object/built-in-methods.rst

.. _Built-in-methods:

Methods on Built-in Types
#########################

We have outlined how the special methods defined by the Python data model
may be implemented for types defined in Java.
There is a finite set of these methods, enumerated in ``Slot``,
and a handful of signatures to which they conform,
enumerated in ``Slot.Signature``.

We now move on to named methods of types defined in Java.
These constitute an essentially unbounded set,
given the possibility of defining extension types,
and we need a general mechanism to support this.

    Code fragments in this section are found in
    ``rt3/src/main/java/.../vsj3/evo1``
    in the project source.

Methods of well-known built-in Python types mostly have complex signatures,
with optional arguments and behaviours
it takes the full call apparatus to explore.
Rather than attempt well-known methods,
we therefore draw detailed examples from ``TypeExposerMethodTest``,
which defines artificial test objects
with a graduated scale of difficulty in the signatures.
This was the test fixture against which solution development proceeded.

The Visible Classes
*******************

In fact we need two main object types to implement methods or functions.

The first is
``PyMethodDescr`` (the Python type ``method_descriptor``),
which appears in the dictionary of a ``PyType`` under the method name.
It is callable and may be applied to an instance as first argument,
although we do not usually use it that way:

>>> type(str.replace)
<class 'method_descriptor'>
>>> str.replace("cacophony", 'c', 'd')
'dadophony'

The second is
``PyJavaMethod`` (the Python type ``builtin_function_or_method``),
which in CPython is implemented by ``PyCFunctionObject``.
It represents the method bound to an instance during attribute access,
either by use of the dot operator,
or by a call to ``__get__`` on the descriptor:

>>> type("cacophony".replace)
<class 'builtin_function_or_method'>
>>> "cacophony".replace
<built-in method replace of str object at 0x7fddb41bbd30>
>>> str.replace.__get__("cacophony")
<built-in method replace of str object at 0x7fddb41bbd30>
>>> type(str.replace.__get__("cacophony"))
<class 'builtin_function_or_method'>

Although the call to ``__get__`` on the descriptor
is not common in user code,
it is what makes attribute access work internally.

Note that in a method call,
the "self" target of ``__call__`` is the callable object,
while the target to which the Python method call is directed,
e.g. the particular ``str`` instance
in an application of ``str.replace()``,
is held by the callable ``builtin_function_or_method``,
then given to ``str.replace`` as first argument when it is called:

>>> r = str.replace.__get__("cacophony")
>>> r.__self__
'cacophony'
>>> r.__call__('c', 'd')
'dadophony'


Relationships amongst the Classes
*********************************

``PyMethodDescr`` belongs to a broad hierarchy of descriptors.
As we have seen,
binding it to a target object gives rise to a ``PyJavaMethod``.
This is a Python behaviour and so far is identical in CPython.

..  uml::
    :caption: Classes supporting method definition

    abstract class Descriptor {
        name : String
        objclass : PyType
    }

    abstract class MethodDescriptor {
    }
    Descriptor <|-- MethodDescriptor

    class ArgParser {
        name : String
        argNames : String[]
    }

    abstract class PyJavaMethod {
        module : String
        handle : MethodHandle
        ~__call__() : Object
    }
    PyJavaMethod -right-> ArgParser : argParser
    PyJavaMethod -left-> Object : self

    abstract class PyMethodDescr {
        signature : MethodSignature
        method : MethodHandle
        ~__get__() : PyJavaMethod
        ~__call__() : Object
    }
    MethodDescriptor <|-- PyMethodDescr
    PyMethodDescr -right-> ArgParser : argParser
    PyMethodDescr ..> PyJavaMethod : <<creates>>

Behind both of these visible classes is the ``ArgParser``,
as the diagram shows,
which is created for each method by the exposer by reflection
on the defining class of a type or module.
An ``ArgParser`` holds the information necessary to parse call arguments:
the "shape" of the parameter list
(number and names, how many are positional only or keyword),
and the default values where given.
It is capable of expressing the full range of parameter lists
encountered when defining a method or function in Python.

``PyMethodDescr`` and ``PyJavaMethod`` are both abstract classes.
As we shall see,
concrete classes derived from each
provide efficient argument processing during calls,
falling back on ``ArgParser`` only in complex cases.


Design Features
***************

The objectives of the design are:

#.  Methods may be defined in Java,
    with parameters of a type natural to their purpose.

#.  A static method (function) or bound method
    is represented by an object callable from Python
    that in turn calls the definition in Java.

#.  A method (with "self")
    is represented by an object callable from Python
    that in turn calls the definition in Java.

#.  Variants exist for instance, static or class methods.

#.  We can make efficient calls (as in CPython)
    in common cases arising in the byte code interpreter.

#.  There is the prospect of efficient ``invokedynamic`` call sites
    in common cases arising in generated Java byte code.

#.  Argument processing code is shared with invocation of
    functions and methods defined in Java or Python.

An "object callable from Python" is one that defines
an instance method ``__call__``.
This implementation
(after considering approaches closer to CPython's)
follows Jython 2 in adopting
the signature ``__call__(Object[], String[])``
as the standard entrypoint.
The presence of that signature populates the corresponding ``Slot``.

We'll discuss the design objectives in turn.


Natural Parameter Types
=======================

A method accepting arguments from Python
could declare every parameter to be ``Object``,
and cast or convert arguments to types natural to the work
as part of the program text.
Or it could have a signature ``(Object[], String[])``
itself, in order to support variable argument numbers and keywords.
Every method would begin with code to pick apart these actual arguments
into strongly-typed local variables.
This would make method bodies tedious to write.

CPython solves this problem using a tool in Python
called Argument Clinic, defined in :pep:`436`.
Argument Clinic processes a signature string and
the C definition with its natural C arguments,
into a wrapper that unpacks arguments from a standard signature,
and calls the "natural" definition renamed.
It means there are often two C functions in the code base:
one with the original name and stylised ``PyObject`` parameters,
and one with the body the author wrote
but where the name has been modified (adding ``_impl``).

Rather than generate code,
we use annotations to define argument processing
that can transform the arguments to ``__call__(Object[], String[])``
into those in the reflected signature of the target Java method.
An example is provided by:

..  code-block:: java

    class SimpleObject {
        static PyType TYPE = PyType.fromSpec(
                new Spec("Simple", MethodHandles.lookup()));
        // ...

        @PythonMethod
        PyTuple m3(int a, String b, Object c) { ... }

The ``PythonMethod`` annotation attracts the attention of the ``TypeExposer``,
which creates an ``ArgParser`` to describe the signature.
In help and similar contexts,
this method would be reported as ``m3($self, a, b, c/)``.
The ``ArgParser`` that results from processing the annotations on the method
is attached to the ``PyMethodDescr`` that represents the method to Python.
The ``ArgParser`` turns arguments after the first ``self``,
as they arrive from executing code (e.g. the byte code interpreter),
into an array of ``Object``\s.

The parameters in the example method are strongly typed
(except by chance ``c`` is ``Object``).
To the Python interpreter, every argument it supplies is just ``Object``.
How can we reconcile the two?

In a minimal implementation,
internally to the ``PyMethodDescr``,
the method is represented by a Java ``MethodHandle``
with signature ``(O,O[])O``:
it expects a "self" ``Object`` and an array of ``Object`` arguments
and returns ``Object``.
The array will be produced on each call by the ``ArgParser``.

The handle has been created from the raw handle for ``m3``,
by a series of casts or conversions,
added using ``MethodHandles.filterArguments``
and ``MethodHandles.filterReturnValue``.
We use ``MethodHandle.asSpreader`` to make it expect an array.

At the time of writing,
only a few casts and conversions are supported in handle formation.
A consistent and sufficiently expressive framework for argument conversion
is still to be elaborated.
The Jython 2 ``__tojava__`` special method is almost what we want,
but does not yield a ``MethodHandle``.
We should explore instead a method resembling:

..  code-block:: java

    class MyType {
        // ...
        MethodHandle __adapt_to__(Class<?> c) {
            // ...
            assert ah.type() == methodType(c, Object.class)
            return ah;
        }

The bound counterpart ``PyJavaMethod`` works similarly,
but the "self" of a method call is already stored as ``__self__``.


Callable ``PyJavaMethod``
=========================

When the interpreter calls ``__call__(Object[], String[])``,
all the argument values from the call site
are marshalled into the first ``Object[]`` array.
The ``String[]`` array contains the keywords used at the call site,
in the same order as their values,
which are the last in the ``Object[]`` array.

The ``__call__`` method of ``PyJavaMethod``
distributes the array of arguments across
the individually declared parameters of the implementation,
using the services and data of the attached ``ArgParser``.
A sufficient implementation of ``__call__`` is:

..  code-block:: java
    :emphasize-lines: 12-16

    public class PyJavaMethod implements CraftedPyObject {

        /** The type of Python object this class implements. */
        static final PyType TYPE = PyType.fromSpec( //
                new PyType.Spec("builtin_function_or_method",
                        MethodHandles.lookup()));
        //...
        final Object self;
        final MethodHandle handle;
        final ArgParser argParser;
        //...
        public Object __call__(Object[] args, String[] names)
                throws TypeError, Throwable {
            Object[] frame = argParser.parse(args, names);
            return handle.invokeExact(frame);
        }
        //...
    }

This code is very simple because the hard work is done by ``argParser.parse``.
The method handle adapts the called Java method to the array argument
prepared by the ``ArgParser``.

An instance of ``PyJavaMethod`` may be the result of a
method declaration like:

..  code-block:: java

        @PythonStaticMethod
        static PyTuple f3(int a, String b, Object c) { ... }

The constructor ensures ``handle.type()`` is ``(O[])O``.
The handle is constructed with the necessary casts and conversions
to match the elements of the array to the parameters ``a``, ``b`` and ``c``,
and the return from ``PyTuple`` to ``Object``.
When representing a static function, member ``self`` is ``null``.

A ``PyJavaMethod`` may also be constructed by binding a ``PyMethodDescr``
declared as:

..  code-block:: java

        @PythonMethod
        PyTuple m3(int a, String b, Object c) { ... }

The source expression ``o.m3`` leads to
an eventual call to ``PyMethodDescr.__get__``,
and a ``PyJavaMethod`` in which member ``self`` is ``o``.
(This is exposed to Python as ``__self__``.)

In order to avoid complicating call processing with a test ``self==null``,
the ``MethodHandle`` in a bound ``PyJavaMethod``
still has the signature ``(O[])O`` appropriate to a function.
When a ``PyJavaMethod`` is formed by binding a ``PyMethodDescr``,
we simply take the handle in the  ``PyMethodDescr``
and bind ``self`` into the first argument,
to get the handle stored in the ``PyJavaMethod``.

The body of ``__call__`` shown above is illustrative.
(The reader will be able to find it, but not in ``__call__`` directly.)
In practice,
substantial optimisations are present to handle common cases,
in which we move arguments directly to the Java method being called.


Callable ``PyMethodDescr``
==========================

Turning now to ``PyMethodDescr``,
a direct invocation of ``__call__``
(meaning something like ``str.replace.__call__('hello', 'ell', 'ipp')``)
has to treat the first argument as ``self``.

``self`` must be an instance of the defining class (or of a sub-class).
We must also deal with the potential complexity of multiple
acceptable implementations.
This causes the handle to vary with the Java type of ``self``.
Trivially, we must also check ``self`` is not missing.

A sufficient, illustrative implementation of ``__call__`` is:

..  code-block:: java
    :emphasize-lines: 8-23

    class PyMethodDescr extends MethodDescriptor {

        static final PyType TYPE = PyType.fromSpec(
                new PyType.Spec("method_descriptor", MethodHandles.lookup())
                        .flagNot(Flag.BASETYPE)
                        .flag(Flag.IS_METHOD_DESCR, Flag.IS_DESCR));
        // ...
        public Object __call__(Object[] args, String[] names)
                throws TypeError, Throwable {
            int m = args.length - 1, nk = names == null ? 0 : names.length;
            if (m < nk) {
                // Not even one argument (self) given by position
                throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                        objclass.name);
            } else {
                // Call this with self and rest of args separately.
                Object self = args[0];
                MethodHandle mh = getHandle(self);
                // Parse args without the leading element self
                Object[] frame = argParser.parse(args, 1, m, names);
                return mh.invokeExact(self, frame);
            }
        }
        // ...
    }

Again, the hard work is done by ``argParser.parse``.
By construction ``mh.type()`` is ``(O,O[])O``.

In practice, the code is not quite like this.
Substantial optimisations are present to provide a fast path in common cases.


Variants for Static and Class Methods
=====================================

We have demonstrated in passing already how ``PyJavaMethod`` represents
a Java ``static`` method,
as well as arising from a method binding.
When encountered in the context of a built-in type,
this becomes an entry in the dictionary of that type,
and so we have a Python static method.
Examples from the interpreter are:

    >>> bytes.__dict__['fromhex'] # METH_CLASS
    <method 'fromhex' of 'bytes' objects>
    >>> bytes.__dict__['maketrans'] # METH_STATIC
    <staticmethod object at 0x0000024B67890250>

At the time of writing class methods are not implemented.


Efficient calls from CPython byte code
======================================

The account we have given so far of the construction of
``PyMethodDescr`` and ``PyJavaMethod``,
and how we implement ``__call__`` in them,
is a simplified one.
It works that way only when the call is sufficiently complicated
that we must give up on optimisations.

CPython contains several optimisations in
the objects that implement method descriptors and functions,
and its compiler generates byte code to take advantage of them.
As we wish to execute this CPython byte code,
either we must implement corresponding optimised mechanisms,
or interpret these sequences into a standard ``__call__``.
We choose the former,
since the mechanism to support CPython optimised calls
is also a big step towards optimisation of Java call sites.

The standard call signature in CPython,
if we were to adopt it in Java,
would be ``Object __call__(PyTuple args, PyDict kwargs)``.
This corresponds directly to the ``CALL_FUNCTION_EX`` opcode
which CPython now only generates in complicated cases:

..  code-block:: text

    # f(*(x, y), **{j:42, k:z})
      1           0 LOAD_NAME                0 (f)
                  2 LOAD_NAME                1 (x)
                  4 LOAD_NAME                2 (y)
                  6 BUILD_TUPLE              2
                  8 LOAD_NAME                3 (j)
                 10 LOAD_CONST               0 (42)
                 12 LOAD_NAME                4 (k)
                 14 LOAD_NAME                5 (z)
                 16 BUILD_MAP                2
                 18 CALL_FUNCTION_EX         1
                 20 RETURN_VALUE

In order to support this,
the CPython byte code support (in ``Callables.java``) includes
``call(Object callable, PyTuple argTuple, PyDict kwDict)``.


Calling a ``PyJavaMethod`` as a function
----------------------------------------

When the call is simpler,
CPython generates simpler byte code.
Suppose we consider calling the function ``f3`` defined in outline above.
CPython pushes the arguments onto the interpreter stack
and executes ``CALL_FUNCTION``,
telling it there are 3 arguments.

..  code-block:: text

    # f3(42, 'hello', 3.21)
      1           0 LOAD_NAME                0 (f3)
                  2 LOAD_CONST               0 (42)
                  4 LOAD_CONST               1 ('hello')
                  6 LOAD_CONST               2 (3.21)
                  8 CALL_FUNCTION            3
                 10 RETURN_VALUE

We implement ``CALL_FUNCTION`` like this (with many details left out):

..  code-block:: java

    class CPython38Frame extends PyFrame<CPython38Code> {
        // ...
        @Override
        Object eval() {
            // Evaluation stack and index
            final Object[] s = valuestack;
            int sp = this.stacktop;
            // ...
                        case Opcode.CALL_FUNCTION:
                            // Call with positional args only. Stack:
                            // f | arg[n] | -> res |
                            // ------------^sp -----^sp
                            oparg |= opword & 0xff; // = n # of args
                            sp -= oparg + 1;
                            s[sp] = Callables.vectorcall(s[sp++], s, sp,
                                    oparg);
                            oparg = 0;
                            break;

Following the motion of ``sp`` in this snippet
may tax the reader's understanding of expression evaluation in Java.
The result is placed in the stack location where ``f`` was
and the ``sp`` passed in indexes ``arg[0]``.
Thus, ``Callables.vectorcall`` is pointed at a slice of the stack
containing the arguments.

In the case of a built-in,
``f`` will be a ``PyJavaMethod``,
which is a class that implements the interface ``FastCall``,
and so we take the fast path in the following method:

..  code-block:: java

    class Callables extends Abstract {
        // ...
        static Object vectorcall(Object callable, Object[] stack, int start,
                int nargs) throws TypeError, Throwable {
            if (callable instanceof FastCall) {
                // Fast path recognising optimised callable
                FastCall fast = (FastCall)callable;
                try {
                    return fast.vectorcall(stack, start, nargs);
                } catch (ArgumentError ae) {
                    // Demand a proper TypeError.
                    throw fast.typeError(ae, stack, start, nargs);
                }
            }
            // Slow path by converting stack to ephemeral array
            Object[] args = Arrays.copyOfRange(stack, start, start + nargs);
            return call(callable, args, NO_KEYWORDS);
        }

The interface ``FastCall``
provides a default implementation for ``vectorcall`` like this:

..  code-block:: java

    interface FastCall {
        // ...
        default Object vectorcall(Object[] s, int p, int n)
                throws ArgumentError, Throwable {
            switch (n) {
                case 0:
                    return call();
                case 1:
                    return call(s[p]);
                case 2:
                    return call(s[p++], s[p]);
                case 3:
                    return call(s[p++], s[p++], s[p]);
                case 4:
                    return call(s[p++], s[p++], s[p++], s[p]);
                default:
                    return call(Arrays.copyOfRange(s, p, p + n));
            }
        }

This unpacks the 3 arguments in our example onto the Java stack,
for a specialised 3-argument ``call`` method.

``PyMethodDescr`` and ``PyJavaMethod``
are both abstract classes.
Concrete classes derived from each
provide efficient argument processing during calls,
falling back on ``ArgParser`` only in complex cases.
When we constructed the ``PyJavaMethod`` representation of ``f3``,
we actually created an instance of a sub-class ``PyJavaMethod.O3``,
guided by the description in the ``ArgParser`` for ``f3``,
which tells us it takes 3 arguments given by position only.

The method handle expected in ``PyJavaMethod.O3``
has signature ``(O,O,O)O`` not ``(O[])O``, that is,
it performs the argument conversion but does not expect an array.
We do not therefore need ``ArgParser.parse``,
the utility that marshals arguments into an array.

Finally, ``PyJavaMethod.O3``
overrides ``FastCall.call(Object, Object, Object)`` like this:

..  code-block:: java

        private static class O3 extends AbstractPositional {
            // ...
            @Override
            public Object call(Object a0, Object a1, Object a2)
                    throws Throwable {
                return handle.invokeExact(a0, a1, a2);
            }

As we can see,
arguments from the CPython stack are moved to an invocation of
the embedded method handle with almost the minimum of data movement.
By this means we achieve what CPython does by keeping
``PyMethodDef`` as part of its structure
and interrogating its ``ml_flags`` field
(see ``_PyMethodDef_RawFastCallDict`` in ``call.c``).


Calling a ``PyMethodDescr`` as a method
---------------------------------------

CPython has another trick up its sleeve when it compiles a method call.
We'll treat this only briefly.

..  code-block:: text

    # o.m3(100, 'hello', 3.21)
      1           0 LOAD_NAME                0 (o)
                  2 LOAD_METHOD              1 (m3)
                  4 LOAD_CONST               0 (100)
                  6 LOAD_CONST               1 ('hello')
                  8 LOAD_CONST               2 (3.21)
                 10 CALL_METHOD              3
                 12 RETURN_VALUE

Here the CPython byte code attempts to avoid
creation of a bound method to represent ``o.m3``.
Where we might have had ``2 LOAD_ATTR 1 (m3)``,
we find instead ``2 LOAD_METHOD  1 (m3)``.

``LOAD_ATTR`` would have called ``PyMethodDescr.__get__``
and returned a bound ``PyJavaMethod``,
later called with ``CALL_FUNCTION``.

The special ``LOAD_METHOD`` looks up ``m3`` in ``type(o)``
and if it supports this method-calling protocol,
which a descriptor does,
``LOAD_METHOD`` leaves both ``o`` and ``type(o).m3`` on the stack.
If the lookup yields any other kind of object,
``LOAD_METHOD`` leaves a ``null`` and the result of ``LOAD_ATTR``
on the stack.

The other special opcode is ``CALL_METHOD``,
found where we might have expected ``CALL_FUNCTION``.

``CALL_METHOD`` examines the two entries
that ``LOAD_METHOD`` left on the stack.
If the ``null`` is present,
it behaves (almost) like ``CALL_FUNCTION 3``,
where the callable is the bound (or other non-descriptor) value.
Otherwise, it is dealing with an unbound descriptor
and ``o`` as the "self" argument to a call on ``PyMethodDescr``,
so it behaves (almost) like ``CALL_FUNCTION 4``.

We implement these opcodes in our CPython byte code interpreter.
Similar optimisations are available in ``PyMethodDescr``
to those described for ``PyJavaMethod``,
using sub-classes again to specialise based on the defining signature.


Prospect of efficient ``invokedynamic`` call sites
==================================================

When the interpreter calls ``__call__(Object[], String[])``,
all the argument values from the call site
are marshalled into the first ``Object[]`` array.
The ``String[]`` array contains the keywords used at the call site,
in the same order as their values,
which are placed  at the end of the ``Object[]`` array.
In principle,
a dynamic call site could accept this and
bind ``__call__`` on the incoming types.

It is worth noticing that the number of arguments
and the order of keywords (or their absence)
is determined entirely by the source code at the call site.
During compilation,
no knowledge is available about the object being called.

The several opcodes that CPython uses to make calls
may be mapped approximately to types of call site,
in the way we have indicated for unary and binary operations.
The types ``PyMethodDescr`` and ``PyJavaMethod``,
and their several implementing Java classes,
would be possible guard classes.

With compile-time knowledge of the number of arguments at the call site,
and whatever use is being made of keywords, tuple or dictionary,
the site may be specialised to certain numbers of arguments,
or to the absence of keywords,
a ``CALL0``, ``CALL1``, ``CALLN``, ``CALLVA``, and so on.
In complicated cases,
the site will be a general ``__call__``.

At run-time,
one may begin to specialise handles for the particular callables encountered.
Under a guard that has matched the sub-type of
``PyMethodDescr`` or ``PyJavaMethod`` presented,
it should be possible to bind the exact implementation of ``call``
that would have been selected.
If the guard can be on the instance of callable,
the exact method handle it holds could be bound to the site,
and even the default values of parameters not matched by arguments.

The site is now one that,
after argument conversions,
goes directly to the Java implementation.
Note that this relied on quite a few conditions holding.
In common cases they do,
but the guard applied at run-time has to take all of them into account.


Common Code with Python Methods
===============================

In the most general case,
processing supplied arguments to the declared parameter positions,
which is the job of ``ArgParser.parse``,
involves an intermediate array into which arguments are mapped.
This is the same process that we go through to populate
the first part of a stack frame,
when calling a function defined in Python.

We therefore use the same code to process a complex call to a built-in,
as we shall for calls to a Python method.
To be precise,
the parsing developed in VSJ2 to fill the interpreter frame,
has been repurposed in VSJ3,
where an interest in the uses of the ``MethodHandle``
has led us to a study of methods defined in Java,
before we try to re-introduce the ``PyFunction``.

Specialisations of ``PyMethodDescr`` and ``PyJavaMethod``,
possible when the signature is simple enough,
do not need to use ``ArgParser.parse`` on the main path
when processing a call.
However, if their validations fail
(e.g. of the number of arguments)
it is still ``ArgParse`` that generates the error message users see.

