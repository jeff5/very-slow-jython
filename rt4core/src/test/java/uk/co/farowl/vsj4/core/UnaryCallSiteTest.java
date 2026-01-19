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
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.StringJoiner;
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
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Test of the mechanism for invoking and updating unary call sites on a
 * variety of types. The particular operations are not the focus: we are
 * testing the mechanisms. The operations should include cached and
 * non-cached {@code SpecialMethod}s.
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

    static interface ThrowingUnaryFunction {
        Object apply(Object v) throws Throwable;
    }

    /**
     * Base class for unary call site tests that exercise some numeric
     * special methods.
     */
    abstract static class AbstractNumericTest {

        /**
         * A Python subclass defined as if in Python.<pre>
         * MyInt = type(name, bases, {})
         * </pre>The type object we get from this should be a shared
         * one.
         */
        static PyType createType(String name, PyType... bases) {
            logger.atTrace().setMessage("Make fresh '{}' type")
                    .addArgument(name).log();
            try {
                return (PyType)PyType.TYPE().call(name,
                        PyTuple.from(bases), Py.dict());
            } catch (Throwable e) {
                throw new InterpreterError(e, "Failed to make %s type",
                        name);
            }
        }

        /**
         * Create an instance from a type.
         *
         * @param type of thing to create
         * @param args to supply the constructor (positionally)
         * @return new instance of {@code type}
         */
        static Object newInstance(PyType type, Object... args) {
            try {
                return type.call(args);
            } catch (Throwable e) {
                throw new InterpreterError(e,
                        "Failed to make %s(%s) instance", type, args);
            }
        }

        /**
         * Build a stream of examples to exercise the parameterised
         * numerical tests.
         *
         * @return stream of
         *     {@link #numberExample(String, String, ThrowingUnaryFunction, List)
         *     numberExample} returns
         */
        static Stream<Arguments> numberExamples() {
            logger.atTrace()
                    .setMessage("Make stream of numberExample()").log();
            List<Arguments> examples = new LinkedList<>();

            examples.addAll(//
                    numberExamples("negative", PyNumber::negative, 42,
                            -1e42, true, false));
            examples.addAll(//
                    numberExamples("absolute", PyNumber::absolute, 42,
                            -42, 0, false, -1e42, Integer.MIN_VALUE));
            examples.addAll(// Not cached
                    numberExamples("positive", PyNumber::positive, 42,
                            -42, 0, false, -1e42, Integer.MIN_VALUE));

            return examples.stream();
        }

        /**
         * Build a stream of examples to exercise the parameterised
         * numerical tests including instances of a custom type.
         *
         * @return stream of
         *     {@link #numberExample(String, String, ThrowingUnaryFunction, List)
         *     numberExample} returns
         */
        static Stream<Arguments> numberExamplesCustom() {

            logger.atTrace().setMessage(
                    "Make stream of numberExample() with custom type")
                    .log();

            // Create a sub-class of int and two instances
            PyType MyInt = createType("MyInt", PyLong.TYPE);
            Object objA = newInstance(MyInt, 7);
            Object objB = newInstance(MyInt, -8);

            List<Arguments> examples = new LinkedList<>();

            examples.addAll(//
                    numberExamples("negative", PyNumber::negative, 42,
                            -1e42, objA, objB));
            examples.addAll(//
                    numberExamples("absolute", PyNumber::absolute, 42,
                            -42, 0, -1e42, Integer.MIN_VALUE, objA,
                            objB));
            examples.addAll(// Not cached
                    numberExamples("positive", PyNumber::positive, 42,
                            -42, 0, false, -1e42, Integer.MIN_VALUE,
                            objA, objB));

            return examples.stream();
        }

        /**
         * Build a stream of examples to exercise the parameterised
         * numerical tests including instances of a custom type.
         *
         * @return stream of
         *     {@link #numberExample(String, String, ThrowingUnaryFunction, List)
         *     numberExample} returns
         */
        static Stream<Arguments> numberExamplesCustom2() {

            logger.atTrace().setMessage(
                    "Make stream of numberExample() with two custom types")
                    .log();

            // Create sub-classes of int and an instances of each
            PyType MyInt = createType("MyInt", PyLong.TYPE);
            Object objA = newInstance(MyInt, 7);

            PyType MyInt2 = createType("MyInt2", MyInt);
            Object objB = newInstance(MyInt2, -8);

            /*
             * Override a method MyInt. MyInt2 should see it by
             * inheritance. The simplest thing for us is to steal a
             * different int method: MyInt.__neg__ = int.__float__ . The
             * exact response to this (but probably not the test) will
             * change if we implement lookup caching in type objects.
             */
            try {
                Object neg = Abstract.getAttr(PyLong.TYPE, "__float__");
                Abstract.setAttr(MyInt, "__neg__", neg);
            } catch (Throwable e) {
                throw new InterpreterError(e,
                        "Failed to update custom type");
            }

            List<Arguments> examples = new LinkedList<>();

            examples.addAll(//
                    numberExamples("negative", PyNumber::negative, 42,
                            objA, objB));
            examples.addAll(//
                    numberExamples("absolute", PyNumber::absolute, 42,
                            -42, 0, true, objA, objB));
            examples.addAll(// Not cached
                    numberExamples("positive", PyNumber::positive, 42,
                            -42, 0, false, -1e42, Integer.MIN_VALUE,
                            objA, objB));

            return examples.stream();
        }

        private static List<Arguments> numberExamples(String name,
                ThrowingUnaryFunction ref, Object... values) {
            // Inflate values to a list of multiple representations
            List<Object> reps = inflateAll(values);

            /*
             * We return a list of several test cases containing the
             * same data, in a different order of arrival at the call
             * site in each replica.
             */
            List<Arguments> examples = new LinkedList<>();

            Random random = new Random(4243);
            for (int i = 0; i < 3; i++) {
                Collections.shuffle(reps, random);
                final StringJoiner sj = new StringJoiner(",", "{", "}");
                typeNames(reps).forEach(s -> sj.add(s));
                examples.add(numberExample(name, sj.toString(), ref,
                        List.copyOf(reps)));
            }
            return examples;
        }

        /**
         * Create a single example with one call site of the requested
         * type and a list of values to submit to it.
         *
         * @param name of the type of call site
         * @param mix of types in the example
         * @param ref reference function to match
         * @param values to apply to
         * @return arguments used that way in the tests
         */
        private static Arguments numberExample(String name, String mix,
                ThrowingUnaryFunction ref, List<Object> values) {
            try {
                CallSite cs = PyRT.bootstrap(LOOKUP, name,
                        Signature.UNARY.type);
                return arguments(name, mix, ref, cs, values);
            } catch (NoSuchMethodException e) {
                logger.atError().setMessage(
                        "failed to create test arguments for \"{}\" {}")
                        .addArgument(name).addArgument(mix).log();
                return arguments(name, mix, ref, null, values);
            }
        }

        /**
         * Represent each value in the arguments in the several forms
         * accepted by Jython for its type.
         *
         * @param values to represent
         * @return a longer list of the same values
         */
        private static List<Object> inflateAll(Object[] values) {
            // Inflate values to a list of multiple representations
            List<Object> reps = new ArrayList<>();
            for (Object value : values) {
                if (value instanceof Integer v) {
                    inflate(reps, v);
                } else if (value instanceof Double v) {
                    inflate(reps, v);
                } else {
                    reps.add(value);
                }
            }
            return reps;
        }

        /**
         * The names of the unique types of the objects in the list in
         * encounter order.
         *
         * @param values to get the types from
         * @return names of the types
         */
        private static Set<String> typeNames(List<Object> values) {
            // Inflate values to a list of multiple representations
            Set<String> names = new LinkedHashSet<>();
            for (Object value : values) {
                names.add(PyType.of(value).getName());
            }
            return names;
        }

        /**
         * The same value in all feasible {@code float} representations.
         */
        private static void inflate(List<Object> reps, double v) {
            reps.add(Double.valueOf(v));
            reps.add(new PyFloat(v));
        }

        /**
         * The same value in all feasible {@code int} representations.
         */
        private static void inflate(List<Object> reps, int v) {
            reps.add(Integer.valueOf(v));
            reps.add(BigInteger.valueOf(v));
            reps.add(newPyLong(v));
        }
    }

    /** Test of numerical operations on float and int types. */
    @Nested
    @DisplayName("encountering built-in types")
    class NumericTest extends AbstractNumericTest {
        /**
         * Invoke a special method call site and compare it to the
         * result from the abstract API for the presented values in
         * order.
         *
         * @throws Throwable unexpectedly
         */
        @DisplayName("matches abstract API")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamples")
        void testMatchSpecial(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {

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
        @DisplayName("falls back as expected")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamples")
        void testFallbackCounts(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {

            MethodHandle invoker = cs.dynamicInvoker();

            /*
             * Track the classes that (we think) are cached in the call
             * site's handle chain.
             */
            Set<Class<?>> chain = new HashSet<>();
            int lastCount = 0;

            // Invoke for each of the values
            for (Object x : values) {

                @SuppressWarnings("unused")
                Object r = invoker.invokeExact(x);

                if (!chain.contains(x.getClass())) {
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
                if (chain.size() < UnaryOpCallSite.MAX_CHAIN) {
                    if (cs.op.hasCache()) { chain.add(x.getClass()); }
                }
                assertEquals(chain.size(), cs.chainLength,
                        "chain length");
            }
        }
    }

    /**
     * Test of numerical operations on float, int and two custom types
     * related by inheritance.
     */
    @Nested
    @DisplayName("encountering built-in and derived types")
    class NumericTestCustom extends NumericTest {
        /**
         * Invoke a special method call site and compare it to the
         * result from the abstract API for the presented values in
         * order.
         *
         * @throws Throwable unexpectedly
         */
        @Override
        @DisplayName("matches abstract API")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamplesCustom")
        void testMatchSpecial(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {
            super.testMatchSpecial(name, mix, ref, cs, values);
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
        @Override
        @DisplayName("falls back as expected")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamplesCustom")
        void testFallbackCounts(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {
            super.testFallbackCounts(name, mix, ref, cs, values);
        }
    }

    /** Test of numerical operations on float, int and custom types. */
    @Nested
    @DisplayName("encountering built-in and two derived types")
    class NumericTestCustom2 extends NumericTest {
        /**
         * Invoke a special method call site and compare it to the
         * result from the abstract API for the presented values in
         * order.
         *
         * @throws Throwable unexpectedly
         */
        @Override
        @DisplayName("matches abstract API")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamplesCustom2")
        void testMatchSpecial(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {
            super.testMatchSpecial(name, mix, ref, cs, values);
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
        @Override
        @DisplayName("falls back as expected")
        @ParameterizedTest(name = "\"{0}\" {1}")
        @MethodSource("numberExamplesCustom2")
        void testFallbackCounts(String name, String mix,
                ThrowingUnaryFunction ref, UnaryOpCallSite cs,
                List<Object> values) throws Throwable {
            super.testFallbackCounts(name, mix, ref, cs, values);
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
