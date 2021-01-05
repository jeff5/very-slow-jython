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

The integers in these measurements are arbitrary precision integers
(``BigInteger`` or a Python type based on it)
for comparability.


VSJ 2 evo 4
***********

We are measuring the execution time of ``Number.add``.
We measure ``Number.multiply`` to confirm its overhead is similar.
Again we compare the API call with an equivalent calculation in pure Java,
where the type of the operand is known statically.

..  code:: none

    Benchmark                           Mode  Cnt    Score   Error  Units
    PyFloatBinary.add_float_float       avgt   20   37.583 ± 0.436  ns/op
    PyFloatBinary.add_float_float_java  avgt  200    5.884 ± 0.034  ns/op
    PyFloatBinary.add_float_int         avgt   20   49.790 ± 0.240  ns/op
    PyFloatBinary.add_float_int_java    avgt  200   12.914 ± 0.102  ns/op
    PyFloatBinary.add_int_float         avgt   20   75.128 ± 0.429  ns/op
    PyFloatBinary.add_int_float_java    avgt  200   13.183 ± 0.049  ns/op
    PyFloatBinary.mul_float_float       avgt   20   37.640 ± 0.193  ns/op
    PyFloatBinary.mul_float_float_java  avgt  200    5.835 ± 0.029  ns/op
    PyFloatBinary.nothing               avgt  200    5.518 ± 0.061  ns/op
    PyFloatBinary.quartic               avgt   20  169.479 ± 0.608  ns/op
    PyFloatBinary.quartic_java          avgt  200    6.417 ± 0.103  ns/op
    PyLongBinary.add                    avgt   20   59.843 ± 1.894  ns/op
    PyLongBinary.add_java               avgt   20   31.587 ± 1.567  ns/op
    PyLongBinary.addbig                 avgt   20   71.830 ± 0.624  ns/op
    PyLongBinary.addbig_java            avgt   20   39.237 ± 0.899  ns/op
    PyLongBinary.mul                    avgt   20   75.785 ± 0.948  ns/op
    PyLongBinary.mul_java               avgt   20   43.508 ± 0.213  ns/op
    PyLongBinary.mulbig                 avgt   20   93.269 ± 6.621  ns/op
    PyLongBinary.mulbig_java            avgt   20   56.321 ± 0.336  ns/op

The invocation overhead is 28-32ns on this machine for similar types,
added or multiplied (``int`` with ``int``, ``float`` with ``float``).
``float + int`` is slower (at 37ns) perhaps related to the need to convert
the ``int`` to a ``float``,
and ``int + float`` even slower (at 62ns) because ``int`` is consulted first,
only to return ``NotImplemented``.

The test fixture that produces this table consists of methods
that typically look like this (without the JMH annotations):

..  code:: java

    public class PyFloatBinary {

        BigInteger iv = BigInteger.valueOf(6), iw = BigInteger.valueOf(7);
        double v = 1.01 * iv.doubleValue(), w = 1.01 * iw.doubleValue();
        PyObject fvo = Py.val(v), fwo = Py.val(w);
        PyObject ivo = Py.val(iv), iwo = Py.val(iv);

        public double nothing() { return v; }
        public double add_float_int_java() { return v + iw.doubleValue(); }

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
This method calls for 5 floating operations.


Jython 2.7.2
************

We measure ``PyObject._add`` and ``PyObject._mul``
as these are comparable in purpose to the VSJ 2 abstract API,
and are called from compiled Python.
The integers in this measurement are Python 2 ``long`` for comparability
with Python 3 ``int``.

..  code:: none

    Benchmark                           Mode  Cnt   Score   Error  Units
    PyFloatBinary.add_float_float       avgt   20  17.086 ± 0.396  ns/op
    PyFloatBinary.add_float_float_java  avgt  200   5.719 ± 0.035  ns/op
    PyFloatBinary.add_float_int         avgt   20  36.094 ± 0.227  ns/op
    PyFloatBinary.add_float_int_java    avgt  200  12.554 ± 0.087  ns/op
    PyFloatBinary.add_int_float         avgt   20  34.508 ± 0.449  ns/op
    PyFloatBinary.add_int_float_java    avgt  200  13.114 ± 0.056  ns/op
    PyFloatBinary.mul_float_float       avgt   20  16.731 ± 0.069  ns/op
    PyFloatBinary.mul_float_float_java  avgt  200   5.930 ± 0.050  ns/op
    PyFloatBinary.nothing               avgt  200   5.530 ± 0.050  ns/op
    PyFloatBinary.quartic               avgt   20  18.168 ± 0.116  ns/op
    PyFloatBinary.quartic_java          avgt  200   6.550 ± 0.065  ns/op
    PyLongBinary.add                    avgt   20  37.721 ± 0.496  ns/op
    PyLongBinary.add_java               avgt   20  29.790 ± 1.104  ns/op
    PyLongBinary.addbig                 avgt   20  49.441 ± 0.457  ns/op
    PyLongBinary.addbig_java            avgt   20  38.964 ± 0.198  ns/op
    PyLongBinary.mul                    avgt   20  53.263 ± 0.202  ns/op
    PyLongBinary.mul_java               avgt   20  43.607 ± 0.170  ns/op
    PyLongBinary.mulbig                 avgt   20  68.540 ± 1.227  ns/op
    PyLongBinary.mulbig_java            avgt   20  55.820 ± 0.165  ns/op
    PyLongBinary.nothing                avgt   20   5.522 ± 0.119  ns/op


The invocation overhead is 10-13ns on this machine for similar types,
added or multiplied (``int`` with ``int``, ``float`` with ``float``).
For ``float + int`` and ``int + float``
the overhead is roughly double (at 24ns)
perhaps related to the need to convert the ``int`` to a ``float``,
The need to consult ``int`` first in ``int + float``
does not seem to have added any overhead.


VSJ 2 evo 4 with ``invokedynamic``
**********************************

Our benchmarks depend on specially-generated equivalents to ``Number.add``,
``Number.multiply`` and (in one place) ``Number.subtract``
that contain just an ``invokedynamic`` instruction.

..  code:: none

    Benchmark                           Mode  Cnt   Score   Error  Units
    PyFloatBinary.add_float_float       avgt   20  17.682 ± 0.371  ns/op
    PyFloatBinary.add_float_float_java  avgt   20   5.686 ± 0.057  ns/op
    PyFloatBinary.add_float_int         avgt   20  22.065 ± 0.182  ns/op
    PyFloatBinary.add_float_int_java    avgt   20  12.675 ± 0.081  ns/op
    PyFloatBinary.add_int_float         avgt   20  24.399 ± 0.743  ns/op
    PyFloatBinary.add_int_float_java    avgt   20  13.061 ± 0.126  ns/op
    PyFloatBinary.mul_float_float       avgt   20  16.066 ± 0.509  ns/op
    PyFloatBinary.mul_float_float_java  avgt   20   5.688 ± 0.073  ns/op
    PyFloatBinary.nothing               avgt   20   5.597 ± 0.126  ns/op
    PyFloatBinary.quartic               avgt   20  49.196 ± 0.211  ns/op
    PyFloatBinary.quartic_java          avgt   20   6.619 ± 0.609  ns/op
    PyLongBinary.add                    avgt   20  45.005 ± 1.198  ns/op
    PyLongBinary.add_java               avgt   20  29.224 ± 1.099  ns/op
    PyLongBinary.addbig                 avgt   20  54.124 ± 0.392  ns/op
    PyLongBinary.addbig_java            avgt   20  38.736 ± 0.238  ns/op
    PyLongBinary.mul                    avgt   20  56.878 ± 0.325  ns/op
    PyLongBinary.mul_java               avgt   20  44.448 ± 0.807  ns/op
    PyLongBinary.mulbig                 avgt   20  65.876 ± 0.267  ns/op
    PyLongBinary.mulbig_java            avgt   20  54.934 ± 0.191  ns/op

The invocation overhead is 10-15ns on this machine.
As before we see some additional cost to convert types during
``float + int`` and ``int + float``.

As in the unary case,
the call sites become specialised to invoke ``op_add``,
``op_mul`` or ``op_sub`` from the type (or types) encountered.
The call site is quite complicated compared to the unary case
because the types of both arguments must be taken into account
in the specialisation.


Analysis
********

Again we see that Jython 2 is faster than VSJ 2,
supporting the hypothesis that the virtual method calls
are more successfully in-lined than ``invokeExact``.
This deficit is mostly made up in VSJ 2 with ``invokedynamic``.

In Jython 2 ``float`` tests,
the difference made by having ``int`` on the left,
and returning ``NotImplemented`` each time is not pronounced.
We speculate that having in-lined the body of ``PyLong.__add__``,
the compiler can see that ``NotImplemented`` is the inevitable result,
and goes directly to ``PyFloat.__radd__``.
In VSJ 2, we see quite a big penalty for having ``int`` on the left.

The shortcoming of the call site we implemented in VSJ 2 with ``invokedynamic``
is also (doubly) present in the binary operation,
in that it too assumes no re-definition of the operations may occur.
Here also, this is true for ``int`` and ``float``,
and does not affect the handles we would generate for them,
or the benchmark results.
Again,
the solution will be to embed a handle that goes via the type object,
when an operand has mutable character.


Thoughts on the quartic test
============================

The test ``quartic`` provides a surprise or two.
This method asks for 5 floating operations
(3 multiplications, an addition and a subtraction).
We have seen that the time for each is not greatly different in isolation.

The pure Java version ``quartic_java`` is noteworthy
for taking barely a nanosecond longer than a single addition.
This is discussed in :ref:`benchmark-vanishing-time`.
The pipelining and concurrency evident in the result
is possible when the floating-point operations are adjacent
(part of the same expression, say).

Jython 2 also achieves a time for ``quartic``
roughly the same as ``add_float_float``,
suggesting the residual overhead (probably two type checks) is paid only once
and the in-lined code optimised as well as for the native case.

In VSJ 2, the overhead of the ``quartic`` is basically 5 times
that of ``add_float_float``,
showing that there is no in-lining of the separate calls
that would bring the floating point calculation together in one place.

This is also approximately true of VSJ 2 with ``invokedynamic``:
the overhead relative to pure Java is 43ns,
around 3.5 times the 12ns on ``add_float_float`` .
We speculate that the method handles are partly in-lined
but are too deeply nested to merge the floating point operations.
Evidently we do not get the remarkable concurrency seen in ``quartic``
for Jython 2 and pure Java.


Further Opportunities
=====================

The complexity in the method handles of VSJ 2 with ``invokedynamic``
is a function of the Python semantics for binary operations:
the conditional delegation to the left and right operand
and the possibility of either returning ``NotImplemented``.
This is part of the language,
and we cannot simply dispense with it.

For built in immutable types, these checks are of no value at run time:
it is a foregone conclusion that ``float+float`` is always implemented,
and that ``int+float`` will in the end be handled by ``PyFloat.__radd__``.
Since we know this, we could go directly to the implementation in one bound.
There could even be a specific ``PyFloat.__radd__(PyLong)`` method
to receive the call.

Such a strategy would require explicit support in the type system,
so that a call site could enquire whether and how
that type likes handle a specified operation on a given other class.
An implementation of a built-in type
could take advantage of the mechanism by defining
additional implementations of the special methods (with distinct signatures)
optimised for each supported argument type.
