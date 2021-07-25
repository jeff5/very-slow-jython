package uk.co.farowl.vsj3.evo1;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;
import java.util.Arrays;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import uk.co.farowl.vsj3.evo1.PyType.Spec;

/**
 * Test that methods exposed by a Python <b>type</b> defined in Java,
 * identified implicitly by name and signature, result in method
 * descriptors with characteristics expected.
 * <p>
 * The first test in each case is to examine fields in the
 * {@link PyWrapperDescr} to ensure the expected slot has been
 * identified. Then we call the function using the {@code __call__}
 * special method, and using our "Java call" signatures.
 */
@DisplayName("When exposed as a special method")
class TypeExposerSlotWrapperTest {

    /**
     * Certain nested test classes implement these as standard. A base
     * class here is just a way to describe the tests once that reappear
     * in each nested case.
     */
    abstract static class Standard {

        // Working variables for the tests
        /** Unbound descriptor by type access to examine or call. */
        PyWrapperDescr descr;
        /** The object on which to invoke the method. */
        Object obj;
        /** The function to examine or call (bound to {@code obj}). */
        PyMethodWrapper func;

        /**
         * The special method descriptor should have field values that
         * correctly reflect the signature and annotations in the
         * defining class.
         */
        abstract void has_expected_fields();

        /**
         * Call the slot function using the {@code __call__} special
         * method with arguments correct for the slot's specification.
         * The function should obtain the correct result (and not
         * throw).
         *
         * @throws Throwable unexpectedly
         */
        abstract void supports__call__() throws Throwable;

        /**
         * Call the slot function using the Java call interface with
         * arguments correct for the slot's specification. The function
         * should obtain the correct result (and not throw).
         *
         * @throws Throwable unexpectedly
         */
        abstract void supports_java_call() throws Throwable;

        /**
         * Check that the fields of the descriptor match expectations.
         *
         * @param slot to be implemented
         */
        void expect(Slot slot) {
            assertEquals(slot, descr.slot);
            assertEquals(slot.methodName, descr.name);
            assertEquals(PyWrapperDescr.TYPE, descr.getType());
            assertEquals(ExampleObject.TYPE, descr.objclass);
        }

        /**
         * Helper to set up each test.
         *
         * @param name of the method
         * @param o to use as the self argument
         * @throws AttributeError if method not found
         * @throws Throwable other errors
         */
        void setup(String name, Object o)
                throws AttributeError, Throwable {
            descr = (PyWrapperDescr)ExampleObject.TYPE.lookup(name);
            obj = o;
            func = (PyMethodWrapper)Abstract.getAttr(o, name);
        }

        /**
         * Check the result of a call against {@link #exp}. The
         * reference result is the same throughout a given sub-class
         * test.
         *
         * @param result of call
         */
        abstract void check(Object result);

    }

    /**
     * A Python type definition that defines some special methods to be
     * found by the exposer.
     */
    static class ExampleObject {

        static PyType TYPE = PyType.fromSpec( //
                new Spec("Example", MethodHandles.lookup()) //
                        .adopt(ExampleObject2.class));

        private int value;

        ExampleObject(int value) { this.value = value; }

        /** See {@link Test__str__}: a unary operation. */
        String __str__() {
            return "<" + value + ">";
        }

        static String __str__(ExampleObject2 self) {
            return "<" + self.value + ">";
        }

        /**
         * See {@link Test__add__}: a binary operation. We just return
         * the arguments.
         */
        PyTuple __add__(Object other) {
            return Py.tuple(this, other);
        }

        static PyTuple __add__(ExampleObject2 self, Object other) {
            return Py.tuple(self, other);
        }
    }

    /**
     * Class cited as an "adopted implementation" of
     * {@link ExampleObject}
     */
    static class ExampleObject2 {

        private BigInteger value;

        ExampleObject2(long value) {
            this.value = BigInteger.valueOf(value);
        }
    }

    /** {@link ExampleObject#__str__()} accepts no arguments. */
    @Nested
    @DisplayName("__str__")
    class Test__str__ extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            setup("__str__", new ExampleObject(42));
        }

        @Override
        void check(Object result) { assertEquals("<42>", result); }

        @Override
        @Test
        void has_expected_fields() { expect(Slot.op_str); }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).__str__(obj)
            Object[] args = {obj};
            String[] kwnames = {};
            Object r = descr.__call__(args, kwnames);
            check(r);

            // We call obj.__str__()
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            r = func.__call__(args2, kwnames);
            check(r);
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).__str__(obj)
            Object r = descr.call(obj);
            check(r);

            // We call obj.__str__()
            r = func.call();
            check(r);
        }
    }

    /**
     * {@link Test__str__} with {@link ExampleObject2} as the
     * implementation.
     */
    @Nested
    @DisplayName("__str__" + " (type 2 impl)")
    class Test__str__2 extends Test__str__ {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.__str__
            setup("__str__", new ExampleObject2(42));
        }
    }

    /**
     * {@link ExampleObject#__add__(Object)} accepts 1 argument that
     * <b>must</b> be given by position.
     */
    @Nested
    @DisplayName("__add__")
    class Test__add__ extends Standard {

        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.__add__
            setup("__add__", new ExampleObject(555));
        }

        @Override
        void check(Object result) {
            Object[] r = ((PyTuple)result).value;
            assertArrayEquals(new Object[] {obj, 111}, r);
        }

        @Override
        @Test
        void has_expected_fields() { expect(Slot.op_add); }

        @Override
        @Test
        void supports__call__() throws Throwable {
            // We call type(obj).__add__(obj, 111)
            Object[] args = {obj, 111};
            String[] kwnames = {};
            Object r = descr.__call__(args, kwnames);
            check(r);

            // We call obj.__add__(111)
            Object[] args2 = {111};
            r = func.__call__(args2, kwnames);
            check(r);
        }

        /** To set anything by keyword is a {@code TypeError}. */
        @Test
        void raises_TypeError_on_unexpected_keyword() {
            // We call type(obj).__add__(obj, other=3)
            Object[] args = {obj, 3};
            String[] kwargs = {"other"};
            assertThrows(TypeError.class,
                    () -> descr.__call__(args, kwargs));

            // We call obj.__add__(other=3)
            Object[] args2 = Arrays.copyOfRange(args, 1, args.length);
            assertThrows(TypeError.class,
                    () -> func.__call__(args2, kwargs));
        }

        @Override
        @Test
        void supports_java_call() throws Throwable {
            // We call type(obj).__add__(obj, 111)
            Object r = descr.call(obj, 111);
            check(r);

            // We call obj.__add__(111)
            r = func.call(111);
            check(r);
        }
    }

    /**
     * {@link Test__add__} with {@link ExampleObject2} as the
     * implementation.
     */
    @Nested
    @DisplayName("__add__" + " (type 2 impl)")
    class Test__add__2 extends Test__add__ {

        @Override
        @BeforeEach
        void setup() throws AttributeError, Throwable {
            // descr = Example.__add__
            setup("__add__", new ExampleObject2(555));
        }
    }

}
