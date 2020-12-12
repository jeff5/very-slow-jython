package uk.co.farowl.jy2bm;

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
 * This is a JMH benchmark for selected unary numeric operations on
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
public class PyFloatUnary {

    double v = 42.0;
    PyObject vo = Py.newFloat(v);

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double nothing() { return v; }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double neg_java() { return -v; }

    @Benchmark
    public PyObject neg() { return vo.__neg__(); }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.newFloat(42.24);
        System.out.println(v.__neg__());
    }
}
