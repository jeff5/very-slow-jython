..  performance/validity.rst


On the Validity of the Benchmarks
#################################

Micro-benchmarking is an appropriate tool to examine
the individual operations that an interpreter or compiler
will string together as the translation of Python code.
We have applied JMH to this purpose in the preceding sections of the chapter.

We have seen that a straightforward approach
identified for us the overhead from each operation,
taking each class of operation in turn.
We conclude we have a tool to determine whether change,
made experimentally,
has been beneficial,
and a measure how we're doing relative to Jython 2 and pure Java.

This section contains some analysis of observations
from the preceding sections as a whole.


Reliability of Timings
**********************

JVM Warm-up
===========

By calling the method repeatedly with the same types,
we induce Java to in-line the called methods, if it can,
and specialise them to the particular types in the test.
This is known as "warm up".


JMH takes care of "warm up",
meaning that all compilation and in-lining will have taken place
by the time measurements are done.
JMH reports the execution times during warm-up,
so it is easy to confirm that enough warm-up iterations are being run
for these have settled to their long-term value.
If we are wrong, additional warm-up occurring during measurement
will appear in the results as high variance (see below).


Validity of Statistics
======================

We use a mean time metric, the sample mean of many "iterations".
Each iteration consists of counting how many times the method under test
may be called within a defined period (usually one second for us)
and converting that to a time per call.

JMH provides error bars to the results in two forms.
One is to report the sample standard deviation.
The second is to convert that to a confidence interval
(expressed as an "error" distance either side of the sample mean),
within which it is 99.9% certain the population mean lies,
assuming a Gaussian distribution.
A Gaussian distribution is a reasonable assumption
where we are averaging many calls,
as we do in each iteration.
Thus if the confidence intervals of two results do not overlap,
we can have high confidence the means differ.

One threat to this scheme is that the results of iteration within a run
should vary so widely that the average would be a poor guide.
This may be because other work interrupts processing
on the CPU that runs the benchmark.
The defence against this is that the variance is also measured:
in a case where such interrupts occur,
the confidence interval would be expanded,
signalling that the result is not useful.

This seems not to occur on the test machine.
In cases where the number of iteration of a test increased from 10 to 1000,
the confidence interval became 10 times smaller
(meaning the variance was 100 times smaller).
This is what would be expected under ideal conditions.

Only relative timings will be significant.
In particular, we are interested how much overhead there is
relative to a similar operation expressed in Java.
The extra time is how much we pay for dynamic typing,
compared to where the type is known statically.


.. _benchmark-vanishing-time:

Vanishing Time in Primitive Operations
**************************************

We have observed some surprising results in the reference ``_java``
methods against which we compare the Python implementations.
Sometimes the calculation takes too little time,
in one case, no time at all.

This can happen in badly constructed benchmarks
when Java detects that the computed result is not used.
The compiler eliminates the code we intended to measure,
and the measurement is zero.
JMH takes a lot of care to avoid this,
yet the results we have seem to show it failing.

In fact, although the current section contains some surprising results,
nothing in our use of JMH suggests the result is not calculated,
as the following examples explain.


.. _benchmark-anomaly-unary:

Anomaly in Unary Operations
===========================

Suppose we set out to measure the cost of a single unary negative operation.
This is a very small operation (a single bit changes):
can we really expect to resolve it?
The code under test (without the JMH annotations) is as follows:

..  code:: java

    public class PyFloatUnary {

        double v = 42.0;
        PyObject vo = Py.val(v);

        public double nothing() { return v; }
        public double neg_float_java() { return -v; }

        public PyObject neg_float() throws Throwable {
            return Number.negative(vo);
        }
    }

Between ``neg_float_java`` and ``nothing``,
we hope to see the cost of a unary negative operation on a Java double.

According to the architecture manual of the processor,
a negation (depending how it is done) takes at least 2 cycles.
We therefore expect a difference of 0.690ns,
but what do we see in practice?

..  code:: none

    Benchmark                    Mode   Cnt   Score   Error  Units
    PyFloatUnary.neg_float       avgt    10  23.693 ± 0.174  ns/op
    PyFloatUnary.neg_float_java  avgt  1000   5.545 ± 0.017  ns/op
    PyFloatUnary.nothing         avgt  1000   5.547 ± 0.016  ns/op

We have chosen a large number of iterations in an attempt to separate
``neg_float_java`` from ``nothing``,
without success: both methods take approximately 5.545ns,
well within the scope for error, a mere 0.017ns.
To put this in perspective, on the processor concerned,
that's quite accurately 16 clock cycles (±1/20 of a cycle).

We are most probably seeing the pipelined operation of the processor.
The floating point negation is going on in parallel with
the return from the method.
The result we compute makes its way to the returned value register
at the same time that the frame pointer is restored and control transferred.

Between ``neg_float`` and ``neg_float_java``,
we expect to see the "dynamic overhead" of steering execution
according to the Python type of the operand.
This is quite clear in the benchmark:
we can be fairly confident that dispatch is costing us 18ns
(or about 53 clocks).

The lesson from the anomaly around ``neg_float_java`` is
not to expect sequences of operations to be exactly additive.


.. _benchmark-anomaly-binary:

Binary Operations
=================

We see something similar in our attempts to measure binary operations.
Consider the fixture (again with the JMH annotations removed):

..  code:: java

    public class PyFloatBinary {

        int iv = 6, iw = 7;
        double v = 1.01 * iv, w = 1.01 * iw;
        PyObject fvo = Py.val(v), fwo = Py.val(w);
        PyObject ivo = Py.val(iv), iwo = Py.val(iv);

        public double nothing() { return v; }
        public double add_float_float_java() { return v + w; }
        public double add_float_int_java() { return v + iw; }
        public double quartic_java() { return v * w * (v + w) * (v - w); }

        public PyObject add_float_float() throws Throwable {
            return Number.add(fvo, fwo);
        }

        public PyObject add_float_int() throws Throwable {
            return Number.add(fvo, iwo);
        }

        public PyObject quartic() throws Throwable {
            return Number.multiply(
                    Number.multiply(Number.multiply(fvo, fwo),
                            Number.add(fvo, fwo)),
                    Number.subtract(fvo, fwo));
        }
    }

Our results from this, re-ordered for comparison, are:

..  code:: none

    Benchmark                           Mode   Cnt    Score   Error  Units
    PyFloatBinary.nothing               avgt  1000    5.507 ± 0.016  ns/op
    PyFloatBinary.add_float_float_java  avgt  1000    5.969 ± 0.038  ns/op
    PyFloatBinary.add_float_int_java    avgt  1000    6.340 ± 0.050  ns/op
    PyFloatBinary.quartic_java          avgt  1000    6.449 ± 0.049  ns/op
    PyFloatBinary.add_float_float       avgt    10   38.806 ± 2.419  ns/op
    PyFloatBinary.add_float_int         avgt    10   49.012 ± 0.662  ns/op
    PyFloatBinary.quartic               avgt    10  169.978 ± 6.691  ns/op

Here there is a difference between a Java double add ``add_float_float_java``
and ``nothing``,
but it is not the 4 cycles we expect (1.37ns).
Even ``quartic_java``,
which comprises 2 additions and 3 multiplications,
seems only to need an extra 0.480ns over the single addition.
We put this down to effective concurrency as well as pipelining.

When it comes to the cost of dynamic type,
``add_float_float`` is 32ns slower than ``add_float_float_java``.
What is more,
that overhead does seem to be additive in ``quartic``,
which pays it 5 times over.
