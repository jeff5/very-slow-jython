package uk.co.farowl.vsj2bm.evo4;

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

import uk.co.farowl.vsj2.evo4.Number;
import uk.co.farowl.vsj2.evo4.Py;
import uk.co.farowl.vsj2.evo4.PyObject;

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

    int iv = 6, iw = 7;
    double v = 1.01 * iv, w = 1.01 * iw;
    PyObject fvo = Py.val(v), fwo = Py.val(w);
    PyObject ivo = Py.val(iv), iwo = Py.val(iv);

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double nothing() { return v; }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_float_java() { return v + w; }

    @Benchmark
    public PyObject add_float_float() throws Throwable {
        return Number.add(fvo, fwo);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_int_java() { return v + iw; }

    @Benchmark
    public PyObject add_float_int() throws Throwable {
        return Number.add(fvo, iwo);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_int_float_java() { return iv + w; }

    @Benchmark
    public PyObject add_int_float() throws Throwable {
        return Number.add(ivo, fwo);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double mul_float_float_java() { return v * w; }

    @Benchmark
    public PyObject mul_float_float() throws Throwable {
        return Number.multiply(fvo, fwo);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double quartic_java() { return v * w * (v + w) * (v - w); }

    @Benchmark
    public PyObject quartic() throws Throwable {
        return Number.multiply(
                Number.multiply(Number.multiply(fvo, fwo),
                        Number.add(fvo, fwo)),
                Number.subtract(fvo, fwo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(6), w = Py.val(7.01);
        System.out.println(Number.multiply(v, w));
    }
}
