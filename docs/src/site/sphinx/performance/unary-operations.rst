..  performance/unary-operations.rst


Unary Operations
################

Here we examine times for basic unary operations on ``float`` and ``int``.


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
    PyFloatUnary.neg         avgt   20  23.618 ± 0.174  ns/op
    PyFloatUnary.neg_java    avgt  200   5.751 ± 0.380  ns/op
    PyFloatUnary.nothing     avgt  200   5.537 ± 0.023  ns/op
    PyLongUnary.neg          avgt   20  34.504 ± 0.420  ns/op
    PyLongUnary.neg_java     avgt  100  16.509 ± 0.049  ns/op
    PyLongUnary.negbig       avgt   20  34.553 ± 0.419  ns/op
    PyLongUnary.negbig_java  avgt   20  16.501 ± 0.118  ns/op

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
    PyFloatUnary.neg         avgt   20  14.686 ± 0.089  ns/op
    PyFloatUnary.neg_java    avgt  200   5.629 ± 0.056  ns/op
    PyFloatUnary.nothing     avgt  200   5.541 ± 0.028  ns/op
    PyLongUnary.neg          avgt   20  24.562 ± 0.122  ns/op
    PyLongUnary.neg_java     avgt   20  16.775 ± 0.257  ns/op
    PyLongUnary.negbig       avgt   20  24.907 ± 0.221  ns/op
    PyLongUnary.negbig_java  avgt   20  16.544 ± 0.051  ns/op

The invocation overhead is about 8ns on this machine.


Analysis
********

The VSJ 2 implementation dispatches through a ``MethodHandle``
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

We can see in the VSJ 2 table that this dispatch costs about 18ns
on the test machine.
This is not very much time:
not enough for the apparent depth of call stack.
We explain this in terms of in-lining carried out by Java HotSpot.

We should expect ``Number.negative`` to have been in-lined at the call site,
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
suggesting that the virtual call is fully in-lined
after a simple guard on type.

Jython is significantly quicker than VSJ 2.
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
