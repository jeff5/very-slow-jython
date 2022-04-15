package uk.co.farowl.vsj3bm.evo1;

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
import uk.co.farowl.vsj3.evo1.PyNumber;

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
    static Object dummy = Py.None; // Wake the type system explicitly

    BigInteger v = BigInteger.valueOf(6), w = BigInteger.valueOf(7);
    BigInteger bigv = V, bigw = W;

    // Copies of type Object to avoid and static specialisation
    Object vo = v, wo = w, bigvo = bigv, bigwo = bigw;

    @Benchmark
    public BigInteger add_java() { return v.add(w); }

    @Benchmark
    public Object add() throws Throwable {
        return PyNumber.add(vo, wo);
    }

    @Benchmark
    public BigInteger addbig_java() { return bigv.add(bigw); }

    @Benchmark
    public Object addbig() throws Throwable {
        return PyNumber.add(bigvo, bigwo);
    }

    @Benchmark
    public BigInteger mul_java() { return v.multiply(w); }

    @Benchmark
    public Object mul() throws Throwable {
        return PyNumber.multiply(vo, wo);
    }

    @Benchmark
    public BigInteger mulbig_java() { return bigv.multiply(bigw); }

    @Benchmark
    public Object mulbig() throws Throwable {
        return PyNumber.multiply(bigvo, bigwo);
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        Object v = V, w = W;
        System.out.println(PyNumber.multiply(v, w));
    }
}
