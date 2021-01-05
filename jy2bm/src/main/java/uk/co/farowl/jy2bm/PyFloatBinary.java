package uk.co.farowl.jy2bm;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.python.core.Py;
import org.python.core.PyObject;

/**
 * This is a JMH benchmark for selected binary numeric operations on
 * {@code float}, and float mixed with {@code int}.
 *
 * The target class is the abstract interface (as called by the
 * interpreter). Comparison is with the time for an in-line use in Java,
 * of the operation that Python will eventually choose to do the
 * calculation.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)

@Fork(2)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)

@State(Scope.Thread)
public class PyFloatBinary {

    BigInteger iv = BigInteger.valueOf(6), iw = BigInteger.valueOf(7);
    double v = 1.01 * iv.doubleValue(), w = 1.01 * iw.doubleValue();
    PyObject fvo = Py.newFloat(v), fwo = Py.newFloat(w);
    PyObject ivo = Py.newLong(iv), iwo = Py.newLong(iv);

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double nothing() { return v; }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_float_java() { return v + w; }

    @Benchmark
    public PyObject add_float_float() { return fvo._add(fwo); }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_int_java() { return v + iw.doubleValue(); }

    @Benchmark
    public PyObject add_float_int() { return fvo._add(iwo); }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_int_float_java() { return iv.doubleValue() + w; }

    @Benchmark
    public PyObject add_int_float() { return ivo._add(fwo); }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double mul_float_float_java() { return v * w; }

    @Benchmark
    public PyObject mul_float_float() { return fvo._mul(fwo); }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double quartic_java() { return v * w * (v + w) * (v - w); }

    @Benchmark
    public PyObject quartic() {
        return fvo.__mul__(fwo)._mul(fvo._add(fwo))._mul(fvo._sub(fwo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) {
        PyObject v = Py.newLong(6), w = Py.newFloat(7.01);
        System.out.println(v._mul(w));
    }
}
