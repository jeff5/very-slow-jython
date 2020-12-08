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
 * This is a JMH benchmark for selected binary numeric operations on
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
public class PyLongBinary {

    static BigInteger V = new BigInteger("6000000000000000000000014");
    static BigInteger W = new BigInteger("7000000000000000000000003");

    BigInteger v = BigInteger.valueOf(6), w = BigInteger.valueOf(7);
    PyObject vo = Py.val(v), wo = Py.val(w);

    BigInteger bigv = V, bigw = W;
    PyObject bigvo = Py.val(bigv), bigwo = Py.val(bigw);

    @Benchmark
    public void nothing(Blackhole bh) {}

    @Benchmark
    public void add_java(Blackhole bh) throws Throwable {
        bh.consume(v.add(w));
    }

    @Benchmark
    public void add(Blackhole bh) throws Throwable {
        bh.consume(Number.add(vo, wo));
    }

    @Benchmark
    public void addbig_java(Blackhole bh) throws Throwable {
        bh.consume(bigv.add(bigw));
    }

    @Benchmark
    public void addbig(Blackhole bh) throws Throwable {
        bh.consume(Number.add(bigvo, bigwo));
    }

    @Benchmark
    public void mul_java(Blackhole bh) throws Throwable {
        bh.consume(v.multiply(w));
    }

    @Benchmark
    public void mul(Blackhole bh) throws Throwable {
        bh.consume(Number.multiply(vo, wo));
    }

    @Benchmark
    public void mulbig_java(Blackhole bh) throws Throwable {
        bh.consume(bigv.multiply(bigw));
    }

    @Benchmark
    public void mulbig(Blackhole bh) throws Throwable {
        bh.consume(Number.multiply(bigvo, bigwo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(V), w = Py.val(W);
        System.out.println(Number.multiply(v, w));
    }
}
