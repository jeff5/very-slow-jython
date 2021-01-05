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

import uk.co.farowl.vsj2.evo4.Py;
import uk.co.farowl.vsj2.evo4.PyObject;
import uk.co.farowl.vsj2dy.evo4.AbstractProxy;

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

    int iv = 6, iw = 7;
    double v = 1.01 * iv, w = 1.01 * iw;
    PyObject fvo = Py.val(v), fwo = Py.val(w);
    PyObject ivo = Py.val(iv), iwo = Py.val(iv);

    @Benchmark
    public double nothing() { return v; }

    @Benchmark
    public double add_float_float_java() { return v + w; }

    @Benchmark
    public PyObject add_float_float() throws Throwable {
        return AbstractProxy.add(fvo, fwo);
    }

    @Benchmark
    public double add_float_int_java() { return v + iw; }

    @Benchmark
    public PyObject add_float_int() throws Throwable {
        return AbstractProxy.add(fvo, iwo);
    }

    @Benchmark
    public double add_int_float_java() { return iv + w; }

    @Benchmark
    public PyObject add_int_float() throws Throwable {
        return AbstractProxy.add(ivo, fwo);
    }

    @Benchmark
    public double mul_float_float_java() { return v * w; }

    @Benchmark
    public PyObject mul_float_float() throws Throwable {
        return AbstractProxy.multiply(fvo, fwo);
    }

    @Benchmark
// @Fork(4) // Needs a lot of iterations to resolve short times
// @Measurement(iterations = 50)
    public double quartic_java() {
        return v * w * (v + w) * (v - w);
    }

    @Benchmark
    public PyObject quartic() throws Throwable {
        return AbstractProxy.multiply(AbstractProxy.multiply( //
                AbstractProxy.multiply(fvo, fwo),
                AbstractProxy.add(fvo, fwo)),
                AbstractProxy.subtract(fvo, fwo));
    }

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        PyObject v = Py.val(7.01), w = Py.val(6.01);
        System.out.println(AbstractProxy.subtract(v, w));
        PyObject iv = Py.val(7), iw = Py.val(7);
        System.out.println(AbstractProxy.subtract(v, iw));
        System.out.println(AbstractProxy.subtract(iv, w));
        v = Py.val(7.01e3);
        w = Py.val(6.01e3);
        System.out.println(AbstractProxy.subtract(v, w));
        iv = Py.val(7000);
        iw = Py.val(6000);
        System.out.println(AbstractProxy.subtract(v, iw));
        System.out.println(AbstractProxy.subtract(iv, w));

    }
}
