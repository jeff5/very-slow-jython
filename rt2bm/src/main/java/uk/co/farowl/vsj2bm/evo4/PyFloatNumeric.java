package uk.co.farowl.vsj2bm.evo4;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import uk.co.farowl.vsj2.evo4.Number;
import uk.co.farowl.vsj2.evo4.Py;
import uk.co.farowl.vsj2.evo4.PyObject;

/**
 * This is a JMH benchmark for selected numeric operations on
 * {@code float}, and float mixed with {@code int}.
 *
 * The target class is the abstract interface (as called by the
 * interpreter). Comparison is with the time for an in-line use in Java,
 * of the operation that Python will eventually choose to do the
 * calculation.
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class PyFloatNumeric {

    int iv = 6, iw = 7;
    double v = 1.01 * iv, w = 1.01 * iw;
    PyObject fvo = Py.val(v), fwo = Py.val(w);
    PyObject ivo = Py.val(iv), iwo = Py.val(iv);

    @Benchmark
    public void add_float_float_java(Blackhole bh) throws Throwable {
        bh.consume(v + w);
    }

    @Benchmark
    public void add_float_int_java(Blackhole bh) throws Throwable {
        bh.consume(v + iw);
    }

    @Benchmark
    public void add_int_float_java(Blackhole bh) throws Throwable {
        bh.consume(iv + w);
    }

    @Benchmark
    public void add_float_float(Blackhole bh) throws Throwable {
        bh.consume(Number.add(fvo, fwo));
    }

    @Benchmark
    public void add_float_int(Blackhole bh) throws Throwable {
        bh.consume(Number.add(fvo, iwo));
    }

    @Benchmark
    public void add_int_float(Blackhole bh) throws Throwable {
        bh.consume(Number.add(ivo, fwo));
    }

    @Benchmark
    public void mul_float_float_java(Blackhole bh) throws Throwable {
        bh.consume(v * w);
    }

    @Benchmark
    public void mul_float_float(Blackhole bh) throws Throwable {
        bh.consume(Number.multiply(fvo, fwo));
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
