package uk.co.farowl.vsj2dybm.evo4;

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

import uk.co.farowl.vsj2.evo4.Py;
import uk.co.farowl.vsj2.evo4.PyObject;
import uk.co.farowl.vsj2dy.evo4.AbstractProxy;

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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)

@Fork(2)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)

@State(Scope.Thread)
public class PyLongBinary {

    static BigInteger V = new BigInteger("6000000000000000000000014");
    static BigInteger W = new BigInteger("7000000000000000000000003");

    BigInteger v = BigInteger.valueOf(6), w = BigInteger.valueOf(7);
    PyObject vo = Py.val(v), wo = Py.val(w);

    BigInteger bigv = V, bigw = W;
    PyObject bigvo = Py.val(bigv), bigwo = Py.val(bigw);

    @Benchmark
    public BigInteger add_java() { return v.add(w); }

    @Benchmark
    public PyObject add() throws Throwable {
        return AbstractProxy.add(vo, wo);
    }

    @Benchmark
    public BigInteger addbig_java() { return bigv.add(bigw); }

    @Benchmark
    public PyObject addbig() throws Throwable {
        return AbstractProxy.add(bigvo, bigwo);
    }

    @Benchmark
    public BigInteger mul_java() { return v.multiply(w); }

    @Benchmark
    public PyObject mul() throws Throwable {
        return AbstractProxy.multiply(vo, wo);
    }

    @Benchmark
    public BigInteger mulbig_java() { return bigv.multiply(bigw); }

    @Benchmark
    public PyObject mulbig() throws Throwable {
        return AbstractProxy.multiply(bigvo, bigwo);
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(V), w = Py.val(W);
        System.out.println(AbstractProxy.multiply(v, w));
    }
}
