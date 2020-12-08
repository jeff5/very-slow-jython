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
 * This is a JMH benchmark for selected unary numeric operations on
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
public class PyFloatUnary {

    double v = 42.0;
    PyObject vo = Py.newFloat(v);

    @Benchmark
    public void neg_float_java(Blackhole bh) throws Throwable {
        bh.consume(-v);
    }

    @Benchmark
    public void neg_float(Blackhole bh) throws Throwable {
        bh.consume(vo.__neg__());
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.newFloat(42.24);
        System.out.println(v.__neg__());
    }
}
