// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.invoke.CallSite;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.core.PyRT.UnaryOpCallSite;
import uk.co.farowl.vsj4.kernel.SpecialMethod;
import uk.co.farowl.vsj4.kernel.SpecialMethod.Signature;

/**
 * Test of the mechanism for invoking and updating unary call sites on a
 * variety of types. The particular operations are not the focus: we are
 * testing the mechanisms.
 */
@DisplayName("A unary call site")
class UnaryCallSiteTest extends UnitTestSupport {

    /**
     * Logger for tests, particularly useful when we have to give up
     * early. This logger is named for the actual class of the test.
     */
    static final Logger logger =
            LoggerFactory.getLogger(UnaryCallSiteTest.class);

    static final Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    @DisplayName("for float")
    abstract class UnaryOpTest {
        SpecialMethod op;

        void check(Object expected, Object actual) {}
    }

    static interface ThrowingFunction {
        Object apply(Object v) throws Throwable;
    }

    abstract static class AbstractNumericTest {

        static Stream<Arguments> numberExamples() {
            List<Arguments> examples = new LinkedList<>();

            examples.addAll(//
                    numberExamples("negative", PyNumber::negative, 42,
                            -1e42, true, false));
            examples.addAll(//
                    numberExamples("absolute", PyNumber::absolute, 42,
                            -42, 0, -1e42, Integer.MIN_VALUE));

            return examples.stream();
        }

        private static List<Arguments> numberExamples(String name,
                ThrowingFunction ref, Object... values) {
            // Inflate values to a list of multiple representations
            List<Object> reps = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Integer v) {
                    reps.addAll(inflate(v));
                } else if (value instanceof Double v) {
                    reps.addAll(inflate(v));
                } else if (value instanceof Boolean v) {
                    reps.addAll(inflate(v));
                }
            }

            /*
             * We return a list of several test cases containing the
             * same data, in a different order of arrival at the call
             * site in each replica.
             */
            List<Arguments> examples = new LinkedList<>();

            Random random = new Random(4242);
            for (int i = 0; i < 3; i++) {
                Collections.shuffle(reps, random);
                examples.add(
                        numberExample(name, ref, List.copyOf(reps)));
            }
            return examples;
        }

        /**
         * Create a single example with one call site of the requested
         * type and a list of values to submit to it.
         *
         * @param name of the type of call site
         * @param ref reference function to match
         * @param values to apply to
         * @return arguments used that way in the tests
         */
        private static Arguments numberExample(String name,
                ThrowingFunction ref, List<Object> values) {
            try {
                CallSite cs = PyRT.bootstrap(LOOKUP, name,
                        Signature.UNARY.type);
                return arguments(name, ref, cs, values);
            } catch (NoSuchMethodException e) {
                logger.atError().setMessage(
                        "failed to create test arguments for \"{}\"")
                        .addArgument(name).log();
                return arguments(name, ref, null, values);
            }
        }

        /**
         * The same value in all feasible {@code float} representations.
         */
        private static List<Object> inflate(double v) {
            return List.of(Double.valueOf(v), new PyFloat(v));
        }

        /**
         * The same value in all feasible {@code float} and {@code int}
         * representations.
         */
        private static List<Object> inflate(int v) {
            return List.of(Integer.valueOf(v), BigInteger.valueOf(v),
                    newPyLong(v), Double.valueOf(v), new PyFloat(v));
        }

        /**
         * The same value in all feasible {@code bool} and {@code int}
         * representations.
         */
        private static List<Object> inflate(boolean v) {
            int i = v ? 1 : 0;
            return List.of(Boolean.valueOf(v), Integer.valueOf(i),
                    BigInteger.valueOf(i), newPyLong(i));
        }
    }

    /**
     * Test with float
     */
    @Nested
    @DisplayName("numerical operations")
    class NumericTest extends AbstractNumericTest {
        /**
         * Invoke a special method call site and compare it to the
         * result from the abstract API for the presented values in
         * order.
         *
         * @throws Throwable unexpectedly
         */
        @DisplayName("match abstract API")
        @ParameterizedTest(name = "\"{0}\"")
        @MethodSource("numberExamples")
        void testMatchSpecial(String name, ThrowingFunction ref,
                UnaryOpCallSite cs, List<Object> values)
                throws Throwable {

            // Bootstrap the call site
            MethodHandle invoker = cs.dynamicInvoker();

            // Invoke for each of the values
            for (Object x : values) {
                Object r = invoker.invokeExact(x);
                Object e = ref.apply(x);
                assertPythonEquals(e, r);
            }
        }

        /**
         * Invoke a special method call site for the presented values in
         * order, examining fall-back and new specialisations added as
         * we go along. This is sensitive to the strategy used by the
         * call site, so as that changes, change the test to match the
         * intent.
         *
         * @throws Throwable unexpectedly
         */
        @DisplayName("fallback as expected")
        @ParameterizedTest(name = "\"{0}\"")
        @MethodSource("numberExamples")
        void testFallbackCounts(String name, ThrowingFunction ref,
                UnaryOpCallSite cs, List<Object> values)
                throws Throwable {

            MethodHandle invoker = cs.dynamicInvoker();

            /*
             * Track the classes that (we think) are cached in the call
             * site's handle chain.
             */
            Set<Class<?>> cached = new HashSet<>();
            int lastCount = 0;

            // Invoke for each of the values
            for (Object x : values) {

                @SuppressWarnings("unused")
                Object r = invoker.invokeExact(x);

                if (!cached.contains(x.getClass())) {
                    // Uncached class so should have called fallback.
                    lastCount += 1;
                }
                assertEquals(lastCount, cs.fallbackCount,
                        "fallback calls");

                /*
                 * If the site is not full and the inner SpecialMethod
                 * is a cached type, it should have been added to the
                 * chain.
                 */
                if (cached.size() < UnaryOpCallSite.MAX_CHAIN) {
                    if (cs.op.hasCache()) { cached.add(x.getClass()); }
                }
                assertEquals(cached.size(), cs.chainLength,
                        "chain length");
            }
        }
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code float} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_float() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        Double dx = 42.0;
        PyFloat px = newPyFloat(dx);

        // Update and invoke for PyFloat, Double
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, r);
            assertEquals("42.0", r.toString());
        }

        // Re-invoke (should involve no fall-back)
        dx = -1.25;
        px = newPyFloat(dx);
        for (Object x : List.of(px, dx)) {
            Object r = invoker.invokeExact(x);
            assertEquals("-1.25", r.toString());
        }

        assertEquals(4, cs.fallbackCount, "fallback calls");
        assertEquals(0, cs.chainLength, "chain length");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code int} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_int() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        Integer ix = 42;
        BigInteger bx = BigInteger.valueOf(ix);
        PyLong px = newPyLong(ix);

        // x is PyLong, Integer, BigInteger
        for (Object x : List.of(px, ix, bx)) {
            Object r = invoker.invokeExact(x);
            assertPythonType(PyUnicode.TYPE, r);
            String e = Integer.toString(toInt(x));
            assertEquals(e, r.toString());
        }

        // Re-invoke (should entail no further fall-back)
        ix = Integer.MAX_VALUE;
        bx = BigInteger.valueOf(ix);
        px = newPyLong(ix);
        for (Object x : List.of(px, ix, bx)) {
            Object r = invoker.invokeExact(x);
            String e = Integer.toString(toInt(x));
            assertEquals(e, r.toString());
        }

        assertEquals(6, cs.fallbackCount, "fallback calls");
        assertEquals(0, cs.chainLength, "chain length");
    }

    /**
     * Test invocation of {@code __repr__} call site on accepted
     * {@code bool} classes.
     *
     * @throws Throwable unexpectedly
     */
    @SuppressWarnings("static-method")
    @Test
    void repr_bool() throws Throwable {

        // Bootstrap the call site
        UnaryOpCallSite cs = new UnaryOpCallSite(SpecialMethod.op_repr);
        MethodHandle invoker = cs.dynamicInvoker();

        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object)x);
            assertPythonType(PyUnicode.TYPE, r);
            String e = x ? "True" : "False";
            assertEquals(e.toString(), r.toString());
        }

        // Re-invoke (should entail no further fall-back)
        for (Boolean x : List.of(false, true)) {
            Object r = invoker.invokeExact((Object)x);
            String e = x ? "True" : "False";
            assertEquals(e, r.toString());
        }

        assertEquals(4, cs.fallbackCount, "fallback calls");
        assertEquals(0, cs.chainLength, "chain length");
    }

    /**
     * Test a {@code __invert__} call site throws {@link TypeError} when
     * applied to a {@code float}.
     */
    @SuppressWarnings("static-method")
    @Test
    void invert_float_error() {

        // Bootstrap the call site
        UnaryOpCallSite cs =
                new UnaryOpCallSite(SpecialMethod.op_invert);
        MethodHandle invoker = cs.dynamicInvoker();

        // __invert__ is not defined for any of these
        List<Object> floats =
                List.of(42., newPyFloat(42), -1e-10, newPyFloat(1e30));
        for (Object x : floats) {
            assertRaises(PyExc.TypeError, () -> invoker.invokeExact(x));
        }

        assertEquals(floats.size(), cs.fallbackCount, "fallback calls");
        assertEquals(0, cs.chainLength, "chain length");
    }
}
