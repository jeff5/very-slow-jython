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
public class PyFloatBinary {

    static BigInteger V = new BigInteger("1000000000000000000006");
    static BigInteger W = new BigInteger("1000000000000000000007");
    static Object dummy = Py.None; // Wake the type system explicitly

    int iv = 6, iw = 7;
    BigInteger bv = V, bw = W;
    double v = 1.01 * iv, w = 1.01 * iw;

    // Copies of type Object to avoid and static specialisation
    Object vo = v, wo = w, ivo = iv, iwo = iw;

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public Object nothing() { return dummy; }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_float_java() { return v + w; }

    @Benchmark
    public Object add_float_float() throws Throwable {
        return PyNumber.add(v, w);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_float_int_java() { return v + iw; }

    @Benchmark
    public Object add_float_int() throws Throwable {
        return PyNumber.add(v, iw);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double add_int_float_java() { return iv + w; }

    @Benchmark
    public Object add_int_float() throws Throwable {
        return PyNumber.add(iv, w);
    }

    @Benchmark
    public double addbig_float_int_java() {
        return v + bw.doubleValue();
    }

    @Benchmark
    public Object addbig_float_int() throws Throwable {
        return PyNumber.add(v, bw);
    }

    @Benchmark
    public double addbig_int_float_java() {
        return bv.doubleValue() + w;
    }

    @Benchmark
    public Object addbig_int_float() throws Throwable {
        return PyNumber.add(bv, w);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double mul_float_float_java() { return v * w; }

    @Benchmark
    public Object mul_float_float() throws Throwable {
        return PyNumber.multiply(v, w);
    }

    @Benchmark
    @Fork(4)  // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public double quartic_java() { return v * w * (v + w) * (v - w); }

    @Benchmark
    public Object quartic() throws Throwable {
        return PyNumber.multiply(
                PyNumber.multiply(PyNumber.multiply(vo, wo),
                        PyNumber.add(vo, wo)),
                PyNumber.subtract(vo, wo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        Object v = 6, w = 7.01;
        System.out.println(PyNumber.multiply(v, w));
    }
}
