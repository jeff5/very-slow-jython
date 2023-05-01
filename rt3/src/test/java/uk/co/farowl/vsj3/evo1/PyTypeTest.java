// Copyright (c)2023 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj3.evo1.PyType.Spec;

/**
 * Tests of some basic mechanisms in {@link PyType} when exercised from
 * Java. We make a unit test of this because at the stage where these
 * don't work, we can't run tests in Python. It was created originally
 * to exercise the use of {@code type.__call__} {@code type.__new__}
 * {@code type.__init__} in a range of circumstances.
 */
@DisplayName("PyType implements")
class PyTypeTest extends UnitTestSupport {

    /**
     * A Python type definition we may use to test various operations on
     * type objects.
     * <p>
     * An identically-named Python type {@code Simple} is defined by
     * {@link TypeExposerMethodTest.SimpleObject}. Here we deliberately
     * invite confusion in order to verify that tests are sufficiently
     * isolated that we never need to worry about that again.
     */
    static class SimpleObject {
        static PyType TYPE = PyType
                .fromSpec(new Spec("Simple", MethodHandles.lookup()));
    }

    /** Base of tests that examine types. */
    abstract static class AbstractTypeTest {
        // Subterfuge to initialise the type system :(
        @SuppressWarnings("unused")
        private static Object DUMMY = UnitTestSupport.OBJECT;

        /**
         * Provide a stream of types and selected characteristics as
         * parameter sets to the tests of types. Each example is
         * essentially a tuple of a type object, a value that should
         * have that type, and text forms of those two.
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> enquiryExamples() {
            return Stream.of(
                    // A type and a value that should have that type
                    enquiryExample(PyBaseObject.TYPE,
                            new PyBaseObject()), //
                    enquiryExample(PyLong.TYPE, 1), //
                    enquiryExample(PyBool.TYPE, true), //
                    enquiryExample(PyType.TYPE, PyLong.TYPE)  //
            );
        }

        /**
         * Construct a type enquiry problem and reference result.
         *
         * @param type concerned
         * @param obj an instance of that type
         * @return example data for a test
         */
        private static Arguments enquiryExample(PyType type,
                Object obj) {
            String ostr = obj.toString();
            String tname = type.getName();
            return arguments(type, obj, ostr, tname);
        }

        /**
         * Provide a stream of types and selected characteristics as
         * parameter sets to the tests of types. Each example is
         * essentially a tuple of a type object, a value ofe that type,
         * and arguments to for the type object that would yield an
         * equal value when used as a constructor.
         *
         * @return the examples for search tests.
         */
        static Stream<Arguments> instanceExamples() {
            final BigInteger BIG = BigInteger.valueOf(10_000_000_000L);
            return Stream.of(
                    // A type, value and args to construct the value
                    instanceExample(PyLong.TYPE, 1, 1), //
                    instanceExample(PyLong.TYPE, 10, BigInteger.TEN), //
                    instanceExample(PyLong.TYPE, BIG, BIG), //
                    instanceExample(PyBool.TYPE, true, 1) //
            );
        }

        /**
         * Construct an instance construction problem and reference
         * result.
         *
         * @param type concerned
         * @param value an instance of that type
         * @param args to {@code type.__call__} to produce the value
         * @return example data for a test
         */
        private static Arguments instanceExample(PyType type,
                Object value, Object... args) {
            String ostr = value.toString();
            String tname = type.getName();
            StringJoiner sj = new StringJoiner(", ");
            for (Object v : args) { sj.add(v.toString()); }
            return arguments(type, value, ostr, tname,
                    args, sj.toString());
        }
    }

    @Nested
    @DisplayName("type.__call__")
    class CallTest extends AbstractTypeTest {
        /**
         * Test type enquiry.
         *
         * @param type expected as a result
         * @param obj on which to call {@code type()}
         * @param ostr string form of obj for display only
         * @param tname string form of type for display only
         * @throws Throwable
         */
        @DisplayName("enquiry type(obj)")
        @ParameterizedTest(name = "type({2}) is {3}")
        @MethodSource("enquiryExamples")
        void enquiry(PyType type, Object obj, String ostr, String tname)
                throws Throwable {
            Object t = Callables.callFunction(PyType.TYPE, obj);
            assertEquals(type, t);
            assertEquals(type, PyType.of(obj));
        }

        /**
         * Test instance construction.
         *
         * @param type to use as constructor
         * @param value expected as result
         * @param ostr string form of obj for display only
         * @param tname string form of type for display only
         * @param args to produce the value
         * @throws Throwable
         */
        @DisplayName("construction <type>(args)")
        @ParameterizedTest(name = "{3}({5}) == {2}")
        @MethodSource("instanceExamples")
        void instance(PyType type, Object value, String ostr,
                String tname, Object[] args, String argstr)
                throws Throwable {
            Object r = Callables.call(type, args, null);
            assertPythonEquals(value, r);
        }
    }

    // construction of a new plain type

    // creation of a metatype

    // construction of with a metatype

}
