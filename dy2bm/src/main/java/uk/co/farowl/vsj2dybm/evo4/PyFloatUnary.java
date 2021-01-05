package uk.co.farowl.vsj2dybm.evo4;

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
import uk.co.farowl.vsj2dy.evo4.AbstractProxy;

/**
 * This is a JMH benchmark for selected unary numeric operations on
 * {@code float}.
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
    PyObject vo = Py.val(v);

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double nothing() { return v; }

    @Benchmark
    public PyObject neg() throws Throwable {
        return AbstractProxy.negative(vo);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double neg_java() { return -v; }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(42.24);
        System.out.println(AbstractProxy.negative(v));
        v = Py.val(1e42);
        System.out.println(AbstractProxy.negative(v));
    }
}
