package uk.co.farowl.jy2bm;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;
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
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class PyFloatBinary {

    int iv = 6, iw = 7;
    double v = 1.01 * iv, w = 1.01 * iw;
    PyObject fvo = Py.newFloat(v), fwo = Py.newFloat(w);
    PyObject ivo = Py.newLong(iv), iwo = Py.newLong(iv);

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
        bh.consume(fvo._add(fwo));
    }

    @Benchmark
    public void add_float_int(Blackhole bh) throws Throwable {
        bh.consume(fvo._add(iwo));
    }

    @Benchmark
    public void add_int_float(Blackhole bh) throws Throwable {
        bh.consume(ivo._add(fwo));
    }

    @Benchmark
    public void mul_float_float_java(Blackhole bh) throws Throwable {
        bh.consume(v * w);
    }

    @Benchmark
    public void mul_float_float(Blackhole bh) throws Throwable {
        bh.consume(fvo._mul(fwo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.newLong(6), w = Py.newFloat(7.01);
        System.out.println(v._mul(w));
    }
}
