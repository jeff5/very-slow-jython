..  performance/unary-operations.rst


Unary Operations
################

Here we examine times for basic unary operations on ``float`` and ``int``.
The integers in these measurements are arbitrary precision integers
(``BigInteger`` or a Python type based on it)
for comparability.


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



Analysis
********

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
(a check that ``vo`` is in fact a ``PyFloat``),
and a call to ``TYPE.op_neg.invokeExact`` on the optimised handle.

In comparison, Jython 2 dispatch consists of a Java virtual method call
to ``PyObject.__neg__``,
overridden by ``PyFloat.__neg__``,
which itself has essentially the same form as in the VSJ 2 implementation.
This dispatch costs only about 8ns,
suggesting that the virtual call is fully in-lined,
after a simple guard on type.

Jython is significantly quicker than plain VSJ 2.
It begins to look as if the called implementation of ``Number.negative``
cannot be in-lined across an ``invokeExact`` call.
Why might this be?

Inlining is not safe here because
Java cannot tell that the handle stored in a ``PyType`` will not change.
We cannot declare it ``final`` in ``PyType`` generally,
since in some types (although not ``int`` or ``float``)
the handle will be re-written
when ``__neg__`` is defined in the called type or an ancestor.
In Python this can happen at any time.

Turning now to VSJ 2 with ``invokedynamic``,
performance recovers to equal that of Jython 2,
suggesting that the JVM is successfully in-lining the method handles
installed by the ``UnaryOpCallSite``.
We apply a class-guard that wraps the ``op_neg`` handle,
but so also must the JVM when it specialises the in-lined call in Jython 2.
The timimgs tell us ours costs no more than in the Jython 2 case.

The call site we implemented in VSJ 2 with ``invokedynamic`` is incorrect.
It assumes no re-definition of ``__neg__`` may occur,
which is true for ``int`` and ``float``,
but not in general.
For types that allow re-assignment (a fact the type object must provide),
and for types that allow object type to be changed,
a different handle should be installed
that always goes via ``op_neg`` in the (current) type object
as in plain VSJ 2.
The difference from plain VSJ 2 is that we get to take advantage of
the short cut for when types are fixed and immutable,
which is the case for many built-in types.


