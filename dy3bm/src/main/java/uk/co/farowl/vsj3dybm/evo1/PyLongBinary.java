package uk.co.farowl.vsj3dybm.evo1;

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

import uk.co.farowl.vsj3.evo1.Py;
import uk.co.farowl.vsj3.evo1.AbstractProxy;

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
    static Object dummy = Py.val(0); // Wake the type system explicitly

    int v = 6, w = 7;
    BigInteger bigv = V, bigw = W;

    @Benchmark
    public int add_java() { return v + w; }

    @Benchmark
    public Object add() throws Throwable {
        return AbstractProxy.add(v, w);
    }

    @Benchmark
    public Object addbig_java() { return bigv.add(bigw); }

    @Benchmark
    public Object addbig() throws Throwable {
        return AbstractProxy.add(bigv, bigw);
    }

    @Benchmark
    public int mul_java() { return v * w; }

    @Benchmark
    public Object mul() throws Throwable {
        return AbstractProxy.multiply(v, w);
    }

    @Benchmark
    public Object mulbig_java() { return bigv.multiply(bigw); }

    @Benchmark
    public Object mulbig() throws Throwable {
        return AbstractProxy.multiply(bigv, bigw);
    }

}
