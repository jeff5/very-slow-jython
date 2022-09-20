package uk.co.farowl.vsj3.evo1;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

/**
 * This is a JMH benchmark to test whether the idiom in which we pass an
 * exception supplier to a method is an efficient one.
 * <p>
 * We're not very interested in the case where an exception is thrown.
 * We want to know that when it isn't, we are not paying very much for
 * the construction of a closure for the {@code Supplier}.
 * <p>
 * Our baseline is a local method in which the exception is hard-coded
 * into the method body, and the caller cannot customise it. In fact, we
 * never throw the exception in the benchmarks of most interest. We
 * compare this with other ways of specifying the exception implemented
 * locally, and with {@code PyUnicode.asString}.
 * <p>
 * When run with no in-lining of the {@code asString} methods, formation
 * of the closure in {@link #asString(Object, Supplier)} costs 8ns
 * relative to the baseline {@link #asString(Object)}. This shows that
 * forming a {@code Supplier} closure including the variable argument
 * has a small cost.
 * <p>
 * However, also with no in-lining, {@link #asString(Object, Function)}
 * is as fast as the baseline. This shows that the the closure itself is
 * not the problem, but the variable argument that causes it to be
 * re-formed on every call.
 * <p>
 * When run with in-lining allowed, there is no significant difference
 * in timing between the implementations, except when we mix types in
 * the {@code asString_mix_*} benchmarks. The supplier is optimised away
 * when it is evident from the consistent type that no exception is
 * possible. This is not representative of real use.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)

@Fork(2)
@Warmup(iterations = 10, time = 1, timeUnit = TimeUnit.SECONDS)
@Measurement(iterations = 20, time = 1, timeUnit = TimeUnit.SECONDS)

@State(Scope.Thread)
public class BMExceptionSupplier {
    /** Number of objects to process in the asString_mix_* benchmarks */
    static final int N_MIX = 100;
    /** Proportion of those to be {@code PyUnicode}. */
    static final double PROP_UNICODE = 0.1;

    static String VS = "hello";
    Object vs = VS, vu = PyUnicode.fromJavaString(VS), vi = 42;
    final Object[] vmix = new Object[N_MIX];

    @Setup
    public void setup() {
        double tumbler = 0.0;
        for (int i = 0; i < N_MIX; i++) {
            String s = "name" + i;
            tumbler += PROP_UNICODE;
            if (tumbler > 1.0) {
                tumbler -= 1.0;
                vmix[i] = PyUnicode.fromJavaString(s);
            } else {
                vmix[i] = s;
            }
        }
    }

    @Benchmark
    @Fork(4) // Needs a lot of iterations to resolve short times
    @Measurement(iterations = 50)
    public String nothing() { return VS; }

    // Benchmark the local asString that does not use a Supplier ------

    @Benchmark
    @Fork(4)
    public String asString_string() { return asString(vs); }

    @Benchmark
    @Fork(4)
    public String asString_unicode() { return asString(vu); }

    @Benchmark
    @Fork(4)
    @Measurement(iterations = 10) // Need fewer since each long
    public void asString_mix(Blackhole bh) {
        for (Object v : vmix) { String s = asString(v); bh.consume(s); }
    }

    // @Benchmark
    @Fork(4)
    @Measurement(iterations = 10)
    public String asString_int() {
        try {
            return asString(vi);
        } catch (Exception e) {
            return "";
        }
    }

    // Benchmark local asString that uses a Function -------------

    @Benchmark
    @Fork(4)
    public String asString_string_func() {
        return asString(vs, BMExceptionSupplier::strRequired);
    }

    @Benchmark
    @Fork(4)
    public String asString_unicode_func() {
        return asString(vu, BMExceptionSupplier::strRequired);
    }

    @Benchmark
    @Fork(4)
    @Measurement(iterations = 10) // Need fewer since each long
    public void asString_mix_func(Blackhole bh) {
        for (Object v : vmix) {
            String s = asString(v, BMExceptionSupplier::strRequired);
            bh.consume(s);
        }
    }

    // @Benchmark
    @Fork(4)
    @Measurement(iterations = 10)
    public String asString_int_func() {
        try {
            return asString(vi, BMExceptionSupplier::strRequired);
        } catch (Exception e) {
            return "";
        }
    }

    // Benchmark local asString that uses a Supplier -----------------

    @Benchmark
    @Fork(4)
    public String asString_string_supp() {
        return asString(vs,
                () -> Abstract.requiredTypeError("a str", vs));
    }

    @Benchmark
    @Fork(4)
    public String asString_unicode_supp() {
        return asString(vu,
                () -> Abstract.requiredTypeError("a str", vu));
    }

    @Benchmark
    @Fork(4)
    @Measurement(iterations = 10) // Need fewer since each long
    public void asString_mix_supp(Blackhole bh) {
        for (Object v : vmix) {
            String s = asString(v,
                    () -> Abstract.requiredTypeError("a str", v));
            bh.consume(s);
        }
    }

    // @Benchmark
    @Fork(4)
    @Measurement(iterations = 10)
    public String asString_int_supp() {
        try {
            return asString(vi,
                    () -> Abstract.requiredTypeError("a str", vi));
        } catch (Exception e) {
            return "";
        }
    }

    // Benchmark PyUnicode.asString that uses a Supplier -------------

    @Benchmark
    @Fork(4)
    public String asString_string_py() {
        return PyUnicode.asString(vs, BMExceptionSupplier::strRequired);
    }

    @Benchmark
    @Fork(4)
    public String asString_unicode_py() {
        return PyUnicode.asString(vu, BMExceptionSupplier::strRequired);
    }

    @Benchmark
    @Fork(4)
    @Measurement(iterations = 10) // Need fewer since each long
    public void asString_mix_py(Blackhole bh) {
        for (Object v : vmix) {
            String s = PyUnicode.asString(v,
                    BMExceptionSupplier::strRequired);
            bh.consume(s);
        }
    }

    @Benchmark
    @Fork(4)
    @Measurement(iterations = 10)
    public String asString_int_py() {
        try {
            return PyUnicode.asString(vi,
                    BMExceptionSupplier::strRequired);
        } catch (Exception e) {
            return "";
        }
    }

    // Supporting code ------------------------------------------------

    /*
     * main() is useful for following the code path in the debugger, but
     * is not material to the benchmark.
     */
    public static void main(String[] args) throws Throwable {
        BMExceptionSupplier test = new BMExceptionSupplier();
        test.vs = VS;
        test.vu = PyUnicode.fromJavaString(VS.toUpperCase());
        test.vi = 1;
        System.out.println(test.checkAll());
    }

    /*
     * We call this from MainLoop to investigate optimisation under
     * repeated calls.
     */
    Set<String> checkAll() {
        Set<String> s = new HashSet<>();
        for (Object o : List.of(vs, vu, vi)) {
            try {
                s.add(asString(o));
            } catch (Exception e) {}
        }
        return s;
    }

    /**
     * Create a {@link TypeError} that a {@code str} is required.
     * 
     * @param o objectionable object
     * @return {@code TypeError} to throw
     */
    static TypeError strRequired(Object o) {
        return Abstract.requiredTypeError("a str", o);
    }

    /**
     * Present a qualifying object as a Java {@code String} value or
     * throw {@code TypeError}. This mimics
     * {@link PyUnicode#asString(Object, Supplier)} except without the
     * flexibility offered by using a {@code Supplier}.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     */
    static String asString(Object v) throws PyException {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).asString();
        throw Abstract.requiredTypeError("a str", v);
    }

    /**
     * Present a qualifying object as a Java {@code String} value or
     * throw {@code E}. This mimics
     * {@link PyUnicode#asString(Object, Supplier)} exactly.
     *
     * @param <E> type of exception to throw
     * @param v claimed {@code str}
     * @param exc supplier for the exception to throw
     * @return {@code String} value
     * @throws E if {@code v} is not a Python {@code str}
     */
    static <E extends PyException> String asString(Object v,
            Supplier<E> exc) throws PyException {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).asString();
        throw exc.get();
    }

    /**
     * Present a qualifying object as a Java {@code String} value or
     * throw {@code E}. This mimics
     * {@link PyUnicode#asString(Object, Supplier)} except except we use
     * a {@code Function} to avoid continually binding a variable
     * {@code v} at the call site.
     *
     * @param <E> type of exception to throw
     * @param v claimed {@code str}
     * @param exc to supply the exception to throw wrapping {@code v}
     * @return {@code String} value
     * @throws E if {@code v} is not a Python {@code str}
     */
    static <E extends PyException> String asString(Object v,
            Function<Object, E> exc) throws PyException {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).asString();
        throw exc.apply(v);
    }
}
