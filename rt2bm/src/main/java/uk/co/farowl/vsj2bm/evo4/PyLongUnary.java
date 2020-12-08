package uk.co.farowl.vsj2bm.evo4;

import java.math.BigInteger;
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
 * This is a JMH benchmark for selected unary numeric operations on
 * {@code int}, small and large (defined as needing 1 and 3 longs
 * internally to {@code BigInteger}).
 *
 * The target class is the abstract interface (as called by the
 * interpreter). Comparison is with the time for an in-line use in Java,
 * of the operation that Python will eventually choose to do the
 * calculation.
 */
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@BenchmarkMode(Mode.AverageTime)
@State(Scope.Thread)
public class PyLongUnary {

    static BigInteger V = new BigInteger("6000000000000000000000014");

    BigInteger v = BigInteger.valueOf(6);
    PyObject vo = Py.val(v);

    BigInteger bigv = V;
    PyObject bigvo = Py.val(bigv);

    @Benchmark
    public void nothing(Blackhole bh) {}

    @Benchmark
    public void neg_java(Blackhole bh) throws Throwable {
        bh.consume(v.negate());
    }

    @Benchmark
    public void neg(Blackhole bh) throws Throwable {
        bh.consume(Number.negative(vo));
    }

    @Benchmark
    public void negbig_java(Blackhole bh) throws Throwable {
        bh.consume(bigv.negate());
    }

    @Benchmark
    public void negbig(Blackhole bh) throws Throwable {
        bh.consume(Number.negative(vo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(V);
        System.out.println(Number.negative(v));
    }
}
