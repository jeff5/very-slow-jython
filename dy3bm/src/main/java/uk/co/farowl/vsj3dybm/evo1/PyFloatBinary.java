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
    Object vo = v, wo = w, ivo = iv, iwo = iw, bvo = bv, bwo = bw;

    @Benchmark
    public Object nothing() { return dummy; }

    @Benchmark
    public double add_float_float_java() { return v + w; }

    @Benchmark
    public Object add_float_float() throws Throwable {
        return AbstractProxy.add(vo, wo);
    }

    @Benchmark
    public double add_float_int_java() { return v + iw; }

    @Benchmark
    public Object add_float_int() throws Throwable {
        return AbstractProxy.add(vo, iwo);
    }

    @Benchmark
    public double add_int_float_java() { return iv + w; }

    @Benchmark
    public Object add_int_float() throws Throwable {
        return AbstractProxy.add(ivo, wo);
    }

    @Benchmark
    public double addbig_float_int_java() {
        return v + bw.doubleValue();
    }

    @Benchmark
    public Object addbig_float_int() throws Throwable {
        return AbstractProxy.add(vo, bwo);
    }

    @Benchmark
    public double addbig_int_float_java() {
        return bv.doubleValue() + w;
    }

    @Benchmark
    public Object addbig_int_float() throws Throwable {
        return AbstractProxy.add(bvo, wo);
    }

    @Benchmark
    public double mul_float_float_java() { return v * w; }

    @Benchmark
    public Object mul_float_float() throws Throwable {
        return AbstractProxy.multiply(vo, wo);
    }

    @Benchmark
    public double quartic_java() { return v * w * (v + w) * (v - w); }

    @Benchmark
    public Object quartic() throws Throwable {
        return AbstractProxy.multiply(
                AbstractProxy.multiply(AbstractProxy.multiply(vo, wo),
                        AbstractProxy.add(vo, wo)),
                AbstractProxy.subtract(vo, wo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        Object vo = 7.01, wo = 6.01;
        System.out.println(AbstractProxy.subtract(vo, wo));
        Object ivo = 7, iwo = 7;
        System.out.println(AbstractProxy.subtract(vo, iwo));
        System.out.println(AbstractProxy.subtract(ivo, wo));
        vo = 7.01e3;
        wo = 6.01e3;
        System.out.println(AbstractProxy.subtract(vo, wo));
        ivo = 7000;
        iwo = 6000;
        System.out.println(AbstractProxy.subtract(vo, iwo));
        System.out.println(AbstractProxy.subtract(ivo, wo));
    }
}
