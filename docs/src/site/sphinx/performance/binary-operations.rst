..  performance/binary-operations.rst


Binary Operations
#################

Binary operations are interesting in Python
because of the way left and right operands both get a chance
to reply with the answer.
This complexity is the price we pay for dynamic typing.

As the overhead logic does not vary with the operation concerned,
there would be little point in measuring exhaustively,
all the different arithmetic operations.
Instead, we focus on a single operation (addition)
and explore the effect of varying the types of the operands
(``float`` and ``int``).


VSJ 2 evo 4
***********

We are measuring the execution time of ``Number.add``.
We measure ``Number.multiply`` to confirm its overhead is similar.
Again we compare the API call with the an equivalent calculation in pure Java,
where the type of the operand is known statically.

..  code:: none

    Benchmark                           Mode  Cnt    Score   Error  Units
    PyFloatBinary.add_float_float       avgt   20   37.261 ± 0.207  ns/op
    PyFloatBinary.add_float_float_java  avgt  200    5.817 ± 0.031  ns/op
    PyFloatBinary.add_float_int         avgt   20   47.907 ± 0.229  ns/op
    PyFloatBinary.add_float_int_java    avgt  200    6.412 ± 0.073  ns/op
    PyFloatBinary.add_int_float         avgt   20   71.277 ± 2.288  ns/op
    PyFloatBinary.add_int_float_java    avgt  200    6.625 ± 0.133  ns/op
    PyFloatBinary.mul_float_float       avgt   20   37.338 ± 0.249  ns/op
    PyFloatBinary.mul_float_float_java  avgt  200    5.968 ± 0.049  ns/op
    PyFloatBinary.nothing               avgt  200    5.458 ± 0.037  ns/op
    PyFloatBinary.quartic               avgt   20  171.266 ± 0.452  ns/op
    PyFloatBinary.quartic_java          avgt  200    6.816 ± 0.156  ns/op
    PyLongBinary.add                    avgt   20   58.353 ± 0.474  ns/op
    PyLongBinary.add_java               avgt   20   30.593 ± 0.774  ns/op
    PyLongBinary.addbig                 avgt   20   75.304 ± 2.470  ns/op
    PyLongBinary.addbig_java            avgt   20   37.676 ± 0.265  ns/op
    PyLongBinary.mul                    avgt   20   76.774 ± 0.889  ns/op
    PyLongBinary.mul_java               avgt   20   43.494 ± 0.212  ns/op
    PyLongBinary.mulbig                 avgt   20   85.442 ± 1.011  ns/op
    PyLongBinary.mulbig_java            avgt   20   55.874 ± 0.517  ns/op

The invocation overhead is 28-32ns on this machine for similar types,
added or multiplied (``int`` with ``int``, ``float`` with ``float``).
``float + int`` is slower (at 42ns) because of the need to convert
the ``int`` to a ``float``,
and ``int + float`` even slower (at 64ns) because ``int`` is consulted first,
only to return ``NotImplemented``.

The test fixture that produces this table consists of methods
that typically look like this (without the JMH annotations):

..  code:: java

    public class PyFloatBinary {

        int iv = 6, iw = 7;
        double v = 1.01 * iv, w = 1.01 * iw;
        PyObject fvo = Py.val(v), fwo = Py.val(w);
        PyObject ivo = Py.val(iv), iwo = Py.val(iv);

        public double nothing() { return v; }
        public double add_float_int_java() { return v + iw; }

        public PyObject add_float_int() throws Throwable {
            return Number.add(fvo, iwo);
        }
        // ...

        public double quartic_java() { return v * w * (v + w) * (v - w); }

        public PyObject quartic() throws Throwable {
            return Number.multiply(
                    Number.multiply(Number.multiply(fvo, fwo),
                            Number.add(fvo, fwo)),
                    Number.subtract(fvo, fwo));
        }
        // ...
    }

We throw in a ``quartic`` calculation to examine how the time scales
with complexity but no new data fetches.
This method calls for 5 floating additions and multiplications.


Jython 2.7.2
************

We measure ``PyObject._add`` and ``PyObject._mul``
as these are comparable in purpose to the VSJ2 abstract API,
and are called from compiled Python.
The integers in this measurement are Python 2 ``long`` for comparability
with Python 3 ``int``.

..  code:: none

    Benchmark                           Mode  Cnt   Score   Error  Units
    PyFloatBinary.add_float_float       avgt   20  16.626 ± 0.095  ns/op
    PyFloatBinary.add_float_float_java  avgt  200   6.076 ± 0.035  ns/op
    PyFloatBinary.add_float_int         avgt   20  35.413 ± 0.305  ns/op
    PyFloatBinary.add_float_int_java    avgt  200   6.605 ± 0.137  ns/op
    PyFloatBinary.add_int_float         avgt   20  38.506 ± 0.190  ns/op
    PyFloatBinary.add_int_float_java    avgt  200   6.669 ± 0.136  ns/op
    PyFloatBinary.mul_float_float       avgt   20  16.825 ± 0.083  ns/op
    PyFloatBinary.mul_float_float_java  avgt  200   5.929 ± 0.033  ns/op
    PyFloatBinary.nothing               avgt  200   5.540 ± 0.047  ns/op
    PyFloatBinary.quartic               avgt   20  17.871 ± 0.104  ns/op
    PyFloatBinary.quartic_java          avgt  200   6.195 ± 0.051  ns/op
    PyLongBinary.add                    avgt   20  37.074 ± 0.259  ns/op
    PyLongBinary.add_java               avgt   20  30.670 ± 0.861  ns/op
    PyLongBinary.addbig                 avgt   20  49.040 ± 0.329  ns/op
    PyLongBinary.addbig_java            avgt   20  37.973 ± 0.282  ns/op
    PyLongBinary.mul                    avgt   20  53.386 ± 0.590  ns/op
    PyLongBinary.mul_java               avgt   20  44.158 ± 0.466  ns/op
    PyLongBinary.mulbig                 avgt   20  66.596 ± 0.520  ns/op
    PyLongBinary.mulbig_java            avgt   20  56.113 ± 0.644  ns/op
    PyLongBinary.nothing                avgt   20   5.711 ± 0.219  ns/op


The invocation overhead is 9-11ns on this machine for similar types,
added or multiplied (``int`` with ``int``, ``float`` with ``float``).
``float + int`` is slower (at 29ns) because of the need to convert
the ``int`` to a ``float``,
but ``int + float`` only a little slower (at 32ns)
in spite of the need to consult ``int`` first.


Analysis
********

Again we see that Jython 2 is faster than VSJ 2,
supporting the hypothesis that the virtual method calls
are more successfully in-lined than ``invokeExact``.

In Jython 2 ``float`` tests,
the difference made by having ``int`` on the left,
and returning ``NotImplemented`` each time is not pronounced.
We speculate that having in-lined the body of ``PyLong.__add__``,
the compiler can see that ``NotImplemented`` is the inevitable result,
and goes directly to ``PyFloat.__radd__``.
In VSJ 2, we see quite a big penalty for having ``int`` on the left.

The test ``quartic`` provides a surprise or two.
This method asks for 5 floating additions and multiplications.
(We have seen that the time for each is not greatly different in isolation.)

The pure Java version ``quartic_java`` is noteworthy
for taking barely a nanosecond longer than a single addition.
This is discussed in :ref:`benchmark-vanishing-time`.
The pipelining and concurrency evident in the result
is possible when the floating-point operations are adjacent
(part of the same expression, say).

In VSJ 2, the overhead of the ``quartic`` is basically 5 times
that of ``add_float_float``,
showing that there is no in-lining of the separate calls
that would bring the floating point calculation together in one place.

Jython 2, in contrast, achieves a time
roughly the same as ``add_float_float``,
suggesting the residual overhead (probably two type checks) is paid only once
and the in-lined code optimised as well as for the native case.
