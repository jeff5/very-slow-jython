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

import uk.co.farowl.vsj3.evo1.AbstractProxy;
import uk.co.farowl.vsj3.evo1.Py;

/**
 * This is a JMH benchmark for selected unary numeric operations on
 * {@code int}, small and large (defined as needing 1 and 3 words
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
public class PyLongUnary {

    static BigInteger V = new BigInteger("6000000000000000000000014");
    static Object dummy = Py.val(0); // Wake the type system explicitly

    int v = 6;
    BigInteger bigv = V;

    @Benchmark
    public Object neg() throws Throwable {
        return AbstractProxy.negative(v);
    }

    @Benchmark
    @Fork(10) // Needs a lot of runs to resolve short times
    @Warmup(iterations = 5) // ... but not long warming up
    public int neg_java() { return -v; }

    @Benchmark
    public Object negbig() throws Throwable {
        return AbstractProxy.negative(bigv);
    }

    @Benchmark
    public Object negbig_java() { return bigv.negate(); }
}
