// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Test the {@link PyWrapperDescr}s for unary special functions on a
 * variety of types. The particular operations are not the focus: we are
 * testing the mechanisms for creating and calling slot wrappers.
 */
@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class UnarySlotWrapperTest extends UnitTestSupport {

    @Nested
    @DisplayName("The slot wrapper '__neg__'")
    class Slot__neg__ extends SlotWrapperTestBase {

        final String NAME = "__neg__";

        @Nested
        @DisplayName("of 'int' objects")
        class OfInt extends UnaryTest<Object, Object> {

            @Override
            Object expected(Object x) {
                // Test material is 32 bit. Maybe BigInteger instead?
                return Integer.valueOf(-toInt(x));
            }

            @Override
            void check(Object exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // x is Integer, BigInteger, PyLong, Boolean
                Integer ix = 42;
                super.setup(PyLong.TYPE, NAME,
                        List.of(ix, BigInteger.valueOf(ix),
                                newPyLong(ix), false, true));
            }

            /**
             * As {@link #supports_call()} but with empty keyword array.
             *
             * @throws Throwable unexpectedly
             */
            @Test
            void supports_call_with_keywords() throws Throwable {
                for (Object x : getCases()) {
                    Object exp = expected(x);
                    checkInt(exp, makeBoundCallKW(x));
                }
            }

            /**
             * As {@link #supports_bound_call()} but with empty keyword
             * array.
             *
             * @throws Throwable unexpectedly
             */
            @Test
            void supports_bound_call_with_keywords() throws Throwable {
                for (Object x : getCases()) {
                    Object exp = expected(x);
                    checkInt(exp, makeBoundCallKW(x));
                }
            }
        }

        @Nested
        @DisplayName("of 'bool' objects")
        class OfBool extends UnaryTest<Object, Boolean> {

            @Override
            Object expected(Boolean x) { return x ? -1 : 0; }

            @Override
            void check(Object exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                super.setup(PyBool.TYPE, NAME, List.of(false, true));
            }
        }

        @Nested
        @DisplayName("of 'float' objects")
        class OfFloat extends UnaryTest<Object, Object> {

            private double exp;

            @Override
            Object expected(Object x) { return exp; }

            @Override
            void check(Object exp, Object r) throws Throwable {
                checkFloat(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // Invoke for Double, PyFloat
                double dx = 42.0;
                exp = -dx;
                super.setup(PyFloat.TYPE, NAME,
                        List.of(dx, newPyFloat(dx)));
            }
        }
    }

    @Nested
    @DisplayName("The slot wrapper '__repr__'")
    class Slot__repr__ extends SlotWrapperTestBase {

        final String NAME = "__repr__";

        @Nested
        @DisplayName("of 'int' objects")
        class OfInt extends UnaryTest<String, Object> {

            @Override
            String expected(Object x) {
                return Integer.toString(toInt(x));
            }

            @Override
            void check(String exp, Object r) throws Throwable {
                checkStr(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // x is Integer, BigInteger, PyLong but not Boolean
                Integer ix = 42;
                super.setup(PyLong.TYPE, NAME, List.of(ix,
                        BigInteger.valueOf(ix), newPyLong(ix)));
            }
        }

        @Nested
        @DisplayName("of 'bool' objects")
        class OfBool extends UnaryTest<String, Boolean> {

            @Override
            String expected(Boolean x) { return x ? "True" : "False"; }

            @Override
            void check(String exp, Object r) throws Throwable {
                checkStr(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                super.setup(PyBool.TYPE, NAME, List.of(false, true));
            }
        }

        @Nested
        @Disabled("Awaits float.__repr__ and stringlib")
        @DisplayName("of 'float' objects")
        class OfFloat extends UnaryTest<String, Object> {

            private String exp;

            @Override
            String expected(Object x) { return exp; }

            @Override
            void check(String exp, Object r) throws Throwable {
                checkStr(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // Invoke for Double, PyFloat
                double dx = 42.0;
                exp = "42.0";
                super.setup(PyFloat.TYPE, NAME,
                        List.of(dx, newPyFloat(dx)));
            }
        }

        @Nested
        @DisplayName("of 'str' objects")
        class OfStr extends UnaryTest<String, Object> {

            private String exp;

            @Override
            String expected(Object x) { return exp; }

            @Override
            void check(String exp, Object r) throws Throwable {
                checkStr(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                String sx = "forty-two";
                exp = "'" + sx + "'";
                super.setup(PyUnicode.TYPE, NAME,
                        List.of(sx, newPyUnicode(sx)));
            }
        }
    }

    @Nested
    @DisplayName("The slot wrapper '__hash__'")
    class Slot__hash__ extends SlotWrapperTestBase {

        final String NAME = "__hash__";

        @Nested
        @DisplayName("of 'int' objects")
        class OfInt extends LenTest<Integer, Object> {

            @Override
            Integer expected(Object x) { return toInt(x); }

            @Override
            void check(Integer exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // x is Integer, BigInteger, PyLong, Boolean
                Integer ix = 42;
                super.setup(PyLong.TYPE, NAME,
                        List.of(ix, BigInteger.valueOf(ix),
                                newPyLong(ix), false, true));
            }
        }

        @Nested
        @DisplayName("of 'bool' objects")
        class OfBool extends LenTest<Integer, Boolean> {

            @Override
            Integer expected(Boolean x) { return x ? 1 : 0; }

            @Override
            void check(Integer exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                super.setup(PyBool.TYPE, NAME, List.of(false, true));
            }
        }

        // XXX Disabled until float.__hash__ implemented
        // @Nested
        @DisplayName("of 'float' objects")
        class OfFloat extends LenTest<Integer, Object> {

            private Integer exp;

            @Override
            Integer expected(Object x) { return exp; }

            @Override
            void check(Integer exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                // Invoke for Double, PyFloat
                double dx = 42.0;
                exp = 42; // since equal in Python
                super.setup(PyFloat.TYPE, NAME,
                        List.of(dx, newPyFloat(dx)));
            }
        }

        @Nested
        @DisplayName("of 'str' objects")
        class OfStr extends LenTest<Integer, Object> {

            private Integer exp;

            @Override
            Integer expected(Object x) { return exp; }

            @Override
            void check(Integer exp, Object r) throws Throwable {
                checkInt(exp, r);
            }

            @BeforeEach
            void setup() throws PyAttributeError, Throwable {
                String sx = "forty-two";
                exp = sx.hashCode();
                super.setup(PyUnicode.TYPE, NAME,
                        List.of(sx, newPyUnicode(sx)));
            }
        }
    }
}
