package uk.co.farowl.vsj2bm.evo4;

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
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)

@Fork(2)
@Warmup(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)

@State(Scope.Thread)
public class PyLongUnary {

    static BigInteger V = new BigInteger("6000000000000000000000014");

    BigInteger v = BigInteger.valueOf(6);
    PyObject vo = Py.val(v);

    BigInteger bigv = V;
    PyObject bigvo = Py.val(bigv);

    @Benchmark
    @Fork(10) // Needs a lot of runs to resolve short times
    @Warmup(iterations = 5) // ... but not long warming up
    public BigInteger neg_java() {
        return v.negate();
    }

    @Benchmark
    public PyObject neg() throws Throwable {
        return Number.negative(vo);
    }

    @Benchmark
    public BigInteger negbig_java() { return bigv.negate(); }

    @Benchmark
    public PyObject negbig() throws Throwable {
        return Number.negative(vo);
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
