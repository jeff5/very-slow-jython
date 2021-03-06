..  performance/unary-operations.rst


Unary Operations
################

Here we examine times for basic unary operations on ``float`` and ``int``.
The integers in these measurements are arbitrary precision integers
(``BigInteger`` or a Python type based on it)
for comparability.


.. _benchmark-unary-vsj2:

VSJ 2 evo 4
***********

The measurements are of the abstract API
that supports the Python byte code interpreter.
If there were a compiler for VSJ 2,
it would probably emit calls to these methods.

In this test we are measuring the execution time of ``Number.negative``,
and comparing it with the an equivalent calculation in pure Java,
where the type of the operand is known statically.
This will tell us how much we are paying for the dynamic typing in Python,
in a particular implementation.

..  code:: none

    Benchmark                Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg         avgt   20  23.966 ± 0.306  ns/op
    PyFloatUnary.neg_java    avgt  200   5.616 ± 0.041  ns/op
    PyFloatUnary.nothing     avgt  200   5.650 ± 0.053  ns/op
    PyLongUnary.neg          avgt   20  34.552 ± 0.264  ns/op
    PyLongUnary.neg_java     avgt  100  16.676 ± 0.098  ns/op
    PyLongUnary.negbig       avgt   20  35.507 ± 0.636  ns/op
    PyLongUnary.negbig_java  avgt   20  16.537 ± 0.088  ns/op

The invocation overhead is consistently around 18ns on this machine,
over the basic cost of the pure Java operation.
The test fixture code looks (typically) like this:

..  code:: java

    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.NANOSECONDS)

    @Fork(2)
    @Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
    @Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)

    @State(Scope.Thread)
    public class PyFloatUnary {

        double v = 42.0;
        PyObject vo = Py.val(v);

        @Benchmark
        @Fork(4)  // Needs a lot of iterations to resolve short times
        @Measurement(iterations = 50)
        public double nothing() { return v; }

        @Benchmark
        @Fork(4)  // Needs a lot of iterations to resolve short times
        @Measurement(iterations = 50)
        public double neg_java() { return -v; }

        @Benchmark
        public PyObject neg() throws Throwable {
            return Number.negative(vo);
        }
        // ...

We have a test method on the API call,
and a nearest equivalent Java method (suffix ``_java``).
The name is chosen to make them adjacent in the results table.

A result must always be returned that depends on all the code under test,
so that JMH can convince Java that the result will be used elsewhere,
and that it cannot be optimised away to nothing.

We also measured a ``nothing`` (empty body) method
as a check on the overheads of simply calling a method under test.
Notice that the costs of ``PyFloatUnary.neg_java`` and of ``nothing``
are not significantly different.
If there is a difference, it is less than one clock cycle of the CPU.
We give an explanation for this in the section
:ref:`benchmark-vanishing-time`.


.. _benchmark-unary-jython2:

Jython 2.7.2
************

We measure ``PyObject.__neg__``
as this is comparable in purpose to the VSJ 2 abstract API,
and is called from compiled Python.
The integer arguments in this measurement are Python 2 ``long``
for comparability with the Python 3 ``int``.
They are implemented as ``BigInteger``.

..  code:: none

    Benchmark                Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg         avgt   20  15.095 ± 0.090  ns/op
    PyFloatUnary.neg_java    avgt  200   5.400 ± 0.039  ns/op
    PyFloatUnary.nothing     avgt  200   5.653 ± 0.058  ns/op
    PyLongUnary.neg          avgt   20  24.859 ± 0.214  ns/op
    PyLongUnary.neg_java     avgt   20  16.601 ± 0.144  ns/op
    PyLongUnary.negbig       avgt   20  24.725 ± 0.142  ns/op
    PyLongUnary.negbig_java  avgt   20  16.640 ± 0.086  ns/op

We can see that the invocation overhead of the Jython 2 approach
is 8-9ns on this machine.


.. _benchmark-unary-vsj2-indy:

VSJ 2 evo 4 with ``invokedynamic``
**********************************

We measure a specially-generated equivalent to ``Number.negative``,
that contains just an ``invokedynamic`` instruction,
and will become linked to a mutable call site at run time.
We do not yet have a compiler for Python that would generate that code,
but this allows us to benchmark the fragment we expect one to emit.

The call site becomes specialised to invoke ``Slot.op_neg``
from the type (or types) encountered,
and therefore we call the same VSJ 2 implementation of ``__neg__``
that was engaged in the plain VSJ 2 benchmark.
Only the linkage and call mechanisms are different.
In particular, Python ``int`` is still implemented using ``BigInteger``.

..  code:: none

    Benchmark                Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg         avgt   20  14.273 ± 0.161  ns/op
    PyFloatUnary.neg_java    avgt  200   5.492 ± 0.031  ns/op
    PyFloatUnary.nothing     avgt  200   5.562 ± 0.064  ns/op
    PyLongUnary.neg          avgt   20  25.405 ± 0.555  ns/op
    PyLongUnary.neg_java     avgt  100  16.691 ± 0.108  ns/op
    PyLongUnary.negbig       avgt   20  24.763 ± 0.489  ns/op
    PyLongUnary.negbig_java  avgt   20  16.482 ± 0.093  ns/op

We can see that the invocation overhead of the dynamic implementations
relative to pure Java is about 9ns on this machine.

The calls benchmarked are to this method, generated using ASM,
intended to mimic what we would expect a compiler to output:

..  code:: none

    public class uk.co.farowl.vsj2dy.evo4.AbstractProxy {
      public static PyObject negative(PyObject);
        Code:
           0: aload_0
           1: invokedynamic #15,  0
                        // InvokeDynamic #0:negative:(LPyObject;)LPyObject;
           6: areturn
    ...
    }

In the disassembly the package name prefixes ``uk/co/farowl/vsj2/evo4/``
and ``uk.co.farowl.vsj2.evo4.``
have been elided and lines broken for the sake of readability.
The generated class AbstractProxy is used in the benchmarks
in place of the abstract numeric API ``Number``:

..  code:: java

    public class PyFloatUnary {

        double v = 42.0;
        PyObject vo = Py.val(v);

        @Benchmark
        @Fork(4)  // Needs a lot of iterations to resolve short times
        @Measurement(iterations = 50)
        public double nothing() { return v; }

        @Benchmark
        public PyObject neg() throws Throwable {
            return AbstractProxy.negative(vo);
        }

        @Benchmark
        @Fork(4)  // Needs a lot of iterations to resolve short times
        @Measurement(iterations = 50)
        public double neg_java() { return -v; }
        // ...


The ``MutableCallSite`` specialisation on the receiving end
is straight out of the textbook in the unary case (some set-up removed):

..  code:: java

    static class UnaryOpCallSite extends MutableCallSite {
        //...

        private final Slot op;

        public UnaryOpCallSite(Slot op)
                throws NoSuchMethodException, IllegalAccessException {
            super(UOP);
            this.op = op;
            setTarget(fallbackMH.bindTo(this));
        }

        private PyObject fallback(PyObject v) throws Throwable {
            PyType vType = v.getType();
            MethodHandle resultMH, targetMH;

            if (op.isDefinedFor(vType)) {
                resultMH = op.getSlot(vType);
            } else {
                resultMH = OPERAND_ERROR.bindTo(op);
            }

            // MH for guarded invocation (becomes new target)
            MethodHandle guardMH = CLASS_GUARD.bindTo(v.getClass());
            targetMH = guardWithTest(guardMH, resultMH, getTarget());
            setTarget(targetMH);

            // Compute the result for this case
            return (PyObject) resultMH.invokeExact(v);
        }
        //...


.. _benchmark-unary-vsj3:

VSJ 3 evo 1
***********

VSJ 3 is the "plain Java object" implementation.
There is no ``PyObject`` that all Python objects extend or implement.
We associate Python types with classes through a ``ClassValue``,
that permits a ``BigInteger`` to be recognised directly as an ``int``,
for example,
and a ``Double`` as a ``float``.

As in VSJ 2,
each operation of which an object is capable,
is accessed through a ``MethodHandle``
stored in a data structure that describes the Python type.
Since the Python type is no longer written on the object, in VSJ 3,
finding the handle is less direct than in VSJ 2,
and we should expect the extra work
(a call to ``ClassValue.get()``)
to show in the time taken to invoke the operation.

..  code:: none

    Benchmark                Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg         avgt   20  31.125 ± 0.377  ns/op
    PyFloatUnary.neg_java    avgt  200   5.646 ± 0.050  ns/op
    PyFloatUnary.nothing     avgt  200   5.773 ± 0.070  ns/op
    PyLongUnary.neg          avgt   20  26.226 ± 0.809  ns/op
    PyLongUnary.neg_java     avgt  100   5.441 ± 0.052  ns/op
    PyLongUnary.negbig       avgt   20  32.605 ± 0.663  ns/op
    PyLongUnary.negbig_java  avgt   20  16.509 ± 0.109  ns/op

Compared with VSJ 2 evo4,
the overhead for ``float`` has indeed increased to 25ns (up from around 18ns),
but in fact we are doing slightly better than VSJ 2 with ``int``.
This will count when we are interpreting CPython byte code.
We have no measurements (at the time of writing)
to tell us whether this is important
relative to the overhead of the interpreter loop.

The comparison with VSJ 2 is not quite direct,
since in VSJ 3 we represent ``int`` by ``Integer``,
if the value is not too big.
This saves work in ``PyLongUnary.neg``.
Its comparator ``PyLongUnary.neg_java``
is written using a primitive Java ``int``.


.. _benchmark-unary-vsj3-indy:

VSJ 3 evo 1 with ``invokedynamic``
**********************************

VSJ 3 also supports binding the ``MethodHandle``\s
into ``invokedynamic`` call sites.
The mechanism for doing so is more complex
than the one we layered onto VSJ 2,
but in return we create the possibility of binding versions
specialised to the argument(s).
For example, the call site in ``PyLongUnary.neg``
will be bound to a method with signature ``Object __neg__(Integer)``.
Binding is a one-time cost (per call site and type).

..  code:: none

    Benchmark                Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg         avgt   20  12.590 ± 0.108  ns/op
    PyFloatUnary.neg_java    avgt  200   5.511 ± 0.025  ns/op
    PyFloatUnary.nothing     avgt  200   5.612 ± 0.053  ns/op
    PyLongUnary.neg          avgt   20  12.913 ± 0.051  ns/op
    PyLongUnary.neg_java     avgt  100   5.408 ± 0.053  ns/op
    PyLongUnary.negbig       avgt   20  16.752 ± 0.341  ns/op
    PyLongUnary.negbig_java  avgt   20  16.544 ± 0.117  ns/op

For ``float`` and small ``int`` the overhead is just 7ns,
while for ``int`` big enough to need a ``BigInteger``,
we there seems to be no overhead at all.


Analysis
********

Basic Slot Dispatch
===================

The plain VSJ 2 implementation dispatches through a ``MethodHandle``
in the following way:

..  code:: java

    public class Number extends Abstract { // ...
        public static PyObject negative(PyObject v) throws Throwable {
            try {
                return (PyObject) v.getType().op_neg.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw operandError("unary -", v);
            }
        }
        // ...

..  code:: java

    class PyFloat extends AbstractPyObject { // ...

        private PyObject __neg__() { return new PyFloat(-value); }

The 18ns that this dispatch costs in VSJ 2 on the test machine
is not very much time:
not enough to create frames for the apparent depth of call the stack.
We explain this in terms of in-lining carried out by Java HotSpot.

For example, in the floating-point benchmark,
we should expect ``Number.negative`` to have been in-lined at the call site,
and specialised for ``PyFloat``.
At the same time, the ``PyFloat`` constructor call will have been
in-lined in ``__neg__``.
The residual time probably consists of a guard
(a check that ``v`` is in fact a ``PyFloat``),
and a call to ``TYPE.op_neg.invokeExact`` on the optimised handle.

In comparison, Jython 2 dispatch consists of a Java virtual method call
to ``PyObject.__neg__``,
overridden by ``PyFloat.__neg__``,
which itself has essentially the same form as in the VSJ 2 implementation.
This dispatch costs only about 8ns,
suggesting that the virtual call is fully in-lined,
and specialised to ``PyFloat``,
after a simple guard on type.


.. _benchmark-invoke-barrier:

The ``invokeExact`` Barrier
===========================

Jython is significantly quicker than plain VSJ 2.
It begins to look as if the called implementation of ``Number.negative``
cannot be in-lined across an ``invokeExact`` call.
The equivalent path in VSJ 3 displays the same problem.
Why might this be?

Inlining is not safe here because
Java cannot tell that the handle stored in a ``PyType`` will not change.
We cannot declare it ``final`` in ``PyType`` generally,
since in some types (although not ``int`` or ``float``)
the handle will be re-written
when ``__neg__`` is defined in the called type or an ancestor.
In Python this can happen at any time.

This sets a limit to what can be expected of interpreted CPython byte code.


Recovery with ``invokedynamic``
===============================

Turning now to VSJ 2 with ``invokedynamic``,
performance recovers to equal that of Jython 2,
suggesting that the JVM is successfully in-lining the method handles
installed by the ``UnaryOpCallSite``.
We apply a class-guard that wraps the ``op_neg`` handle,
and falls back to a method that will look for the correct handle.
When the JVM specialises the in-lined call in Jython 2,
it too must check the specialisation applies to each new argument.
The timings tell us the checks in VSJ 2 cost no more than those in Jython 2.

By installing the handle on ``__neg__`` as the target of the call site,
the run-time system implicitly guarantees to the JVM that in-lining is safe.
We have to use a mutable call site,
one where the handle may change,
because a new class may come along at any time and fail the class guard.
Then we will re-write the target,
and the JVM will respond by adapting the in-line code.

The call site we implemented in VSJ 2 with ``invokedynamic``
is incorrect in this respect.
It assumes no re-definition of ``__neg__`` will occur,
which is correct for the types in the test but will not do long-term.
For types that allow re-assignment of special functions
(something the type object must indicate is a possibility),
and for types that allow object type to be changed,
a different handle should be installed
that always goes via ``op_neg`` in the type the object (currently) has.

The VSJ 3 call site is designed to allow for mutable types.
For immutable types like ``int`` and ``float``
it will install the fast path.

.. _benchmark-unary-class-specific-dispatch:

Dispatch Specific to Java Class
===============================

The other big difference in VSJ 3 from VSJ 2 is
the adoption of multiple types as implementations of
a built-in Python object type.
Operations are defined separately for each implementing Java class,
and it is these definitions that the abstract API will invoke.

..  code:: java

    public class Number extends Abstract { // ...
        public static PyObject negative(PyObject v) throws Throwable {
            try {
                return Operations.of(v).op_neg.invokeExact(v);
            } catch (Slot.EmptyException e) {
                throw operandError(Slot.op_neg, v);
            }
        }
        // ...

..  code:: java

    class PyFloatMethods { // ...

        static Object __neg__(PyFloat self) { return -self.value; }
        static Object __neg__(Double self) { return -self.doubleValue(); }

The ``PyFloat`` implementation exists so that
we may sub-class ``float`` in Python.
A method is defined for all implementing classes (or for a super-class),
or it is not defined for the type,
and the slot will then either be inherited or be empty.

When binding the target for a newly-encountered call into a call site,
the site will find the definition for that class
and (for immutable types) bind that directly.
If the method is not defined,
it will bind one that raises a Python ``TypeError``.

It is clear in the timings that specialisation and
the simpler ``MethodHandle``\s
do reduce the overhead as hoped,
beating Jython 2 by a small margin.
