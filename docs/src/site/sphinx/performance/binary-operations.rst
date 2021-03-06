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


.. _benchmark-binary-vsj2:

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


.. _benchmark-binary-jython2:

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


.. _benchmark-binary-vsj2-indy:

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


.. _benchmark-binary-vsj3:

VSJ 3 evo 1
***********

VSJ 3 is the "plain Java object" implementation, where
finding the handle of an operation involves work
(a call to ``ClassValue.get()``)
not necessary with the type-labelled ``PyObject``\s of VSJ 2.

..  code:: none

    27/02 18:30
    Benchmark                            Mode  Cnt    Score   Error  Units
    PyFloatBinary.add_float_float        avgt   20   54.345 ± 0.835  ns/op
    PyFloatBinary.add_float_float_java   avgt  200    5.993 ± 0.050  ns/op
    PyFloatBinary.add_float_int          avgt   20   68.574 ± 1.226  ns/op
    PyFloatBinary.add_float_int_java     avgt  200    6.851 ± 0.217  ns/op
    PyFloatBinary.add_int_float          avgt   20   94.763 ± 2.103  ns/op
    PyFloatBinary.add_int_float_java     avgt  200    6.299 ± 0.062  ns/op
    PyFloatBinary.addbig_float_int       avgt   20   76.177 ± 0.308  ns/op
    PyFloatBinary.addbig_float_int_java  avgt   20   17.232 ± 0.136  ns/op
    PyFloatBinary.addbig_int_float       avgt   20   98.819 ± 0.304  ns/op
    PyFloatBinary.addbig_int_float_java  avgt   20   20.261 ± 0.094  ns/op
    PyFloatBinary.mul_float_float        avgt   20   53.369 ± 0.275  ns/op
    PyFloatBinary.mul_float_float_java   avgt  200    5.995 ± 0.048  ns/op
    PyFloatBinary.nothing                avgt  200    5.618 ± 0.037  ns/op
    PyFloatBinary.quartic                avgt   20  257.507 ± 1.100  ns/op
    PyFloatBinary.quartic_java           avgt  200    6.934 ± 0.132  ns/op
    PyLongBinary.add                     avgt   20   60.712 ± 0.622  ns/op
    PyLongBinary.add_java                avgt   20   30.235 ± 0.968  ns/op
    PyLongBinary.addbig                  avgt   20   85.987 ± 0.794  ns/op
    PyLongBinary.addbig_java             avgt   20   38.598 ± 0.914  ns/op
    PyLongBinary.mul                     avgt   20   78.763 ± 1.996  ns/op
    PyLongBinary.mul_java                avgt   20   43.609 ± 0.212  ns/op
    PyLongBinary.mulbig                  avgt   20   96.414 ± 0.915  ns/op
    PyLongBinary.mulbig_java             avgt   20   55.932 ± 0.576  ns/op


Compared with VSJ 2 evo4,
for ``float`` the overhead has indeed increased to 50-90ns
(up from around 30ns),
but in fact we are doing about the same as VSJ 2 with ``int``.

Again the comparison with VSJ 2 is not quite direct,
since in VSJ 3 we represent ``int`` by ``Integer``,
if the value is not too big.
The ``int``\s are ``Integer`` in ``add_float_int`` and ``add_int_float``,
and primitive Java int in their Java counterparts.
The tests with ``addbig`` or ``mulbig`` in the name use ``BigInteger``.


.. _benchmark-binary-vsj3-indy:

VSJ 3 evo 1 with ``invokedynamic``
**********************************

When VSJ 3 binds the ``MethodHandle``\s
into binary ``invokedynamic`` call sites,
it uses definitions specialised to the types of both operands.

For example, the call site in ``PyFloatBinary.add_int_float``
will be bound to a method with signature ``Object __add__(Integer, Double)``,
provided by the implementation of ``float``.
The fact that ``int`` provides no method with this signature,
and this can be decided at the time the call site is being bound,
makes it unnecessary to consult ``int`` during the call itself.
(This only applies to types where the definition cannot change.)

..  code:: none

    Benchmark                            Mode  Cnt   Score   Error  Units
    PyFloatBinary.add_float_float        avgt   20  12.522 ± 0.112  ns/op
    PyFloatBinary.add_float_float_java   avgt   20   6.026 ± 0.148  ns/op
    PyFloatBinary.add_float_int          avgt   20  16.439 ± 0.144  ns/op
    PyFloatBinary.add_float_int_java     avgt   20   6.404 ± 0.280  ns/op
    PyFloatBinary.add_int_float          avgt   20  15.417 ± 0.047  ns/op
    PyFloatBinary.add_int_float_java     avgt   20   6.563 ± 0.285  ns/op
    PyFloatBinary.addbig_float_int       avgt   20  24.067 ± 0.245  ns/op
    PyFloatBinary.addbig_float_int_java  avgt   20  16.972 ± 0.072  ns/op
    PyFloatBinary.addbig_int_float       avgt   20  23.798 ± 0.128  ns/op
    PyFloatBinary.addbig_int_float_java  avgt   20  20.342 ± 0.108  ns/op
    PyFloatBinary.mul_float_float        avgt   20  12.604 ± 0.062  ns/op
    PyFloatBinary.mul_float_float_java   avgt   20   6.106 ± 0.268  ns/op
    PyFloatBinary.nothing                avgt   20   5.741 ± 0.080  ns/op
    PyFloatBinary.quartic                avgt   20  12.925 ± 0.123  ns/op
    PyFloatBinary.quartic_java           avgt   20   6.746 ± 0.585  ns/op
    PyLongBinary.add                     avgt   20  15.204 ± 0.172  ns/op
    PyLongBinary.add_java                avgt   20   5.181 ± 0.173  ns/op
    PyLongBinary.addbig                  avgt   20  43.007 ± 0.388  ns/op
    PyLongBinary.addbig_java             avgt   20  38.462 ± 0.245  ns/op
    PyLongBinary.mul                     avgt   20  15.770 ± 0.099  ns/op
    PyLongBinary.mul_java                avgt   20   6.080 ± 0.190  ns/op
    PyLongBinary.mulbig                  avgt   20  61.291 ± 0.453  ns/op
    PyLongBinary.mulbig_java             avgt   20  56.014 ± 0.636  ns/op



Analysis
********

Binary Slot Dispatch
====================

The semantics of binary operations in Python are complex,
in particular the way in which the types of both arguments
must be consulted,
and types give way to sub-types.
This is the reason why we explore the combinations
``float+float``, ``float+int`` and ``int+float`` separately.
The last of these has the largest overhead,
since the ``int.__add__`` must be consulted and return ``NotImplemented``,
before ``float.__add__`` comes up with the answer.

Even in the case ``float+int`` where ``float.__add__`` will succeed,
code that is ignorant of the particular types
must check that ``int`` is not a sub-type of it,
then apply a test for ``NotImplemented``.
This happens for every addition,
compared with Java in which the types are statically known,
and only a handful of instructions need be executed.


Greater Strain on In-lining
===========================

In the binary performance tests,
we again see that Jython 2 is faster than VSJ 2
and VSJ 3 using ``invokeExact``,
supporting the hypothesis that the virtual method calls
are more successfully in-lined.
(See :ref:`benchmark-invoke-barrier`.)

In Jython 2 ``float`` tests,
the difference made by having ``int`` on the left,
and returning ``NotImplemented`` each time is not pronounced.
The assembly code generated by the JVM is long and complex,
but it appears that having in-lined the body of ``PyLong.__add__``,
the compiler can see that ``NotImplemented`` is the inevitable result,
and goes directly to ``PyFloat.__radd__``.

In VSJ 2, we see quite a big penalty for having ``int`` on the left.
This deficit is only partly made up in VSJ 2 with ``invokedynamic``.
We speculate that the method handles are only partially in-lined
because they are too deeply nested for the JVM to do so fully.
The wrappers that test for ``NotImplemented`` contribute to that depth,
in addition to the class guards.

The shortcoming we noted in VSJ 2 unary ``invokedynamic`` call sites,
that they assume no re-definition of the operations may occur,
is (doubly) present in the binary case,
but we correct that in VSJ 3.


Dispatch Specific to Java Class
===============================

We saw a small advantage in VSJ 3 from
:ref:`benchmark-unary-class-specific-dispatch` in the unary case.
In the binary case,
we gain enormously from specialisation in a mixed case like ``int+float``.
After a guard on class,
the course of events may be plotted completely by the call site:
it is not necessary (in the case cited) to consult ``int``,
or to test for ``NotImplemented`` in the handle.

To pull this off,
we have to supply quite a number of ``static`` implementation methods.
Each implementation class of ``float`` must be supported
as the first argument of ``__add__`` and ``__radd__``,
then combined with each legitimate other operand class for the second.

..  code:: java

    class PyFloatBinops { // ...
        static Object __add__(PyFloat v, PyFloat w) {
            return v.value + w.value;
        }
        static Object __add__(PyFloat v, Double w) {
            return v.value + w.doubleValue();
        }
        static Object __add__(PyFloat v, Integer w) {
            return v.value + w.doubleValue();
        }
        static Object __add__(PyFloat v, BigInteger w) {
            return v.value + PyLong.convertToDouble(w);
        }
        // ...
        static Object __add__(Double v, PyFloat w) {
            return v.doubleValue() + w.value;
        }
        // ...
        static Object __radd__(PyFloat w, PyFloat v) {
            return v.value + w.value;
        }
        // ...

There are many combinations and we use a script to generate them.
The call site binds in the method for each operand class pair,
under a double class guard.


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
roughly the same as its own ``add_float_float``,
suggesting the residual overhead (probably two type checks) is paid only once
and the in-lined code optimised as well as for the native case.

In VSJ 2, the overhead of the ``quartic`` is basically 5 times
that of ``add_float_float``,
showing that there is no in-lining of the separate calls
that would bring the floating point calculation together in one place.

This is also approximately true of VSJ 2 with ``invokedynamic``:
the overhead relative to pure Java is 43ns,
around 3.5 times the 12ns on ``add_float_float`` .
Evidently we do not get the remarkable concurrency seen in ``quartic``
for Jython 2 and pure Java.

Finally in VSJ 3,
the specialisation to class allows the handles to in-line fully,
and we are down to 7ns total overhead
relative to the pure Java expression.

