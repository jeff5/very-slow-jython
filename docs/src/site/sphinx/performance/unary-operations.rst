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

In this test we are measuring the execution time of ``Number.negative``.
We also measure a ``nothing`` (empty body) method
to confirm measurement overheads are negligible
even for such small operations.

..  code:: none

    Benchmark                    Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg_float       avgt    5  23.465 ± 0.447  ns/op
    PyFloatUnary.neg_float_java  avgt    5   5.302 ± 0.406  ns/op
    PyLongUnary.neg              avgt    5  34.249 ± 0.484  ns/op
    PyLongUnary.neg_java         avgt    5  15.809 ± 0.301  ns/op
    PyLongUnary.negbig           avgt    5  33.984 ± 0.562  ns/op
    PyLongUnary.negbig_java      avgt    5  16.168 ± 0.150  ns/op
    PyLongUnary.nothing          avgt    5   1.052 ± 0.010  ns/op


The invocation overhead is consistently about 18ns on this machine.
Since "nothing" takes 1ns, we should perhaps deduct that,
but we won't.

The test fixture code looks (typically) like this:

..  code:: java

    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @State(Scope.Thread)
    public class PyFloatUnary {

        double v = 42.0;
        PyObject vo = Py.val(v);

        @Benchmark
        public void neg_float_java(Blackhole bh) throws Throwable {
            bh.consume(-v);
        }

        @Benchmark
        public void neg_float(Blackhole bh) throws Throwable {
            bh.consume(Number.negative(vo));
        }
        // ...

There is a nearest equivalent Java method (suffix ``_java``),
and the test method on the API call.
The convention is chosen to make them adjacent in the results table.

A ``Blackhole`` is the way JMH tricks Java into believing
the result will be used elsewhere,
and that it cannot be optimised away to nothing.


Jython 2.7.2
************

We measure ``PyObject.__neg__``
as this is comparable in purpose to the VSJ 2 abstract API,
and is called from compiled Python.
The integers in this measurement are Python 2 ``long`` for comparability
with Python 3 ``int``.

..  code:: none

    Benchmark                    Mode  Cnt   Score   Error  Units
    PyFloatUnary.neg_float       avgt    5  14.470 ± 0.218  ns/op
    PyFloatUnary.neg_float_java  avgt    5   5.423 ± 0.153  ns/op
    PyLongUnary.neg              avgt    5  24.749 ± 0.517  ns/op
    PyLongUnary.neg_java         avgt    5  16.271 ± 1.075  ns/op
    PyLongUnary.negbig           avgt    5  24.533 ± 0.363  ns/op
    PyLongUnary.negbig_java      avgt    5  16.062 ± 0.320  ns/op
    PyLongUnary.nothing          avgt    5   1.060 ± 0.031  ns/op

The invocation overhead is consistently about 9ns on this machine.


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
on the test machine. This is not very much.

We should expect ``Number.negative`` to have been in-lined at the call site,
and specialised for ``PyFloat``.
At the same time, the ``PyFloat`` constructor call will have been
in-lined in ``__neg__``.
The cost probably consists of a guard
(a check that ``vo`` is in fact a ``PyFloat``),
and a call to ``TYPE.op_neg.invokeExact`` on the optimised handle.

In comparison, Jython 2 dispatch consists of a Java virtual method call
to ``PyObject.__neg__``,
overridden by ``PyFloat.__neg__``,
which has essentially the same form as in the VSJ 2 implementation.
This dispatch costs only about 9ns,
suggesting that the virtual call is fully in-lined
after a simple guard on type.

