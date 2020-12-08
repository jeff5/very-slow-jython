..  performance/binary-operations.rst


Binary Operations
#################

Binary operations are interesting in Python
because of the way left and right operands both get a chance
to reply with the answer.
This logic is the price we pay for dynamic typing.

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

..  code:: none

    Benchmark                            Mode  Cnt   Score   Error  Units
    PyFloatNumeric.add_float_float       avgt    5  37.383 ± 0.561  ns/op
    PyFloatNumeric.add_float_float_java  avgt    5   5.860 ± 0.847  ns/op
    PyFloatNumeric.add_float_int         avgt    5  48.502 ± 4.685  ns/op
    PyFloatNumeric.add_float_int_java    avgt    5   6.290 ± 1.029  ns/op
    PyFloatNumeric.add_int_float         avgt    5  68.455 ± 3.413  ns/op
    PyFloatNumeric.add_int_float_java    avgt    5   6.359 ± 1.115  ns/op
    PyFloatNumeric.mul_float_float       avgt    5  37.858 ± 0.532  ns/op
    PyFloatNumeric.mul_float_float_java  avgt    5   5.952 ± 0.239  ns/op
    PyLongNumeric.add                    avgt    5  61.241 ± 2.980  ns/op
    PyLongNumeric.add_java               avgt    5  29.107 ± 5.906  ns/op
    PyLongNumeric.addbig                 avgt    5  73.321 ± 0.893  ns/op
    PyLongNumeric.addbig_java            avgt    5  38.419 ± 0.883  ns/op
    PyLongNumeric.mul                    avgt    5  76.840 ± 0.571  ns/op
    PyLongNumeric.mul_java               avgt    5  43.257 ± 0.734  ns/op
    PyLongNumeric.mulbig                 avgt    5  85.558 ± 0.733  ns/op
    PyLongNumeric.mulbig_java            avgt    5  58.144 ± 0.699  ns/op
    PyLongNumeric.nothing                avgt    5   1.061 ± 0.005  ns/op

The invocation overhead is 28-35ns on this machine for similar types,
added or multiplied (``int`` with ``int``, ``float`` with ``float``).
``float + int`` is slower (at 42ns) because of the need to convert
the ``int`` to a ``float``,
and ``int + float`` even slower (at 62ns) because ``int`` is consulted first,
only to return ``NotImplemented``.

The test fixture that produces this table consists of methods
that look (typically) like this:

..  code:: java

    @OutputTimeUnit(TimeUnit.NANOSECONDS)
    @BenchmarkMode(Mode.AverageTime)
    @State(Scope.Thread)
    public class PyFloatNumeric {

        int iv = 6, iw = 7;
        double v = 1.01 * iv, w = 1.01 * iw;
        PyObject fvo = Py.val(v), fwo = Py.val(w);
        PyObject ivo = Py.val(iv), iwo = Py.val(iv);

        @Benchmark
        public void add_float_int_java(Blackhole bh) throws Throwable {
            bh.consume(v + iw);
        }

        @Benchmark
        public void add_float_int(Blackhole bh) throws Throwable {
            bh.consume(Number.add(fvo, iwo));
        }
        // ...




Jython 2.7.2
************

We measure ``PyObject._add`` and ``PyObject._mul``
as these are comparable in purpose to the VSJ2 abstract API,
and are called from compiled Python.
The integers in this measurement are Python 2 ``long`` for comparability
with Python 3 int.

..  code:: none

    Benchmark                            Mode  Cnt   Score   Error  Units
    PyFloatNumeric.add_float_float       avgt    5  16.966 ± 0.182  ns/op
    PyFloatNumeric.add_float_float_java  avgt    5   5.698 ± 0.113  ns/op
    PyFloatNumeric.add_float_int         avgt    5  35.824 ± 0.198  ns/op
    PyFloatNumeric.add_float_int_java    avgt    5   6.175 ± 0.117  ns/op
    PyFloatNumeric.add_int_float         avgt    5  37.988 ± 0.515  ns/op
    PyFloatNumeric.add_int_float_java    avgt    5   6.172 ± 0.036  ns/op
    PyFloatNumeric.mul_float_float       avgt    5  16.999 ± 0.140  ns/op
    PyFloatNumeric.mul_float_float_java  avgt    5   5.787 ± 0.545  ns/op
    PyLongNumeric.add                    avgt    5  37.825 ± 0.403  ns/op
    PyLongNumeric.add_java               avgt    5  29.691 ± 5.000  ns/op
    PyLongNumeric.addbig                 avgt    5  49.699 ± 0.943  ns/op
    PyLongNumeric.addbig_java            avgt    5  42.488 ± 0.771  ns/op
    PyLongNumeric.mul                    avgt    5  53.947 ± 0.620  ns/op
    PyLongNumeric.mul_java               avgt    5  43.761 ± 0.381  ns/op
    PyLongNumeric.mulbig                 avgt    5  66.721 ± 1.150  ns/op
    PyLongNumeric.mulbig_java            avgt    5  56.407 ± 1.451  ns/op
    PyLongNumeric.nothing                avgt    5   1.057 ± 0.011  ns/op

The invocation overhead is 7-10ns on this machine for similar types,
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


