// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Constructor;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyNameError;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.internal.Util;

/**
 * This is a test of the construction of shared representations and
 * replaceable types in the type system, by examination of the the
 * built-in exception hierarchy. The exceptions are unusual amongst the
 * built-in classes in providing a deep inheritance hierarchy where the
 * addition of attributes in subclasses is sparse and representations
 * can be shared. We are not much interested here to test the behaviour
 * of the exceptions themselves.
 * <p>The Java classes representing structurally different
 *  exceptions create their own Python type objects.
 * The exception types that share these representations
 * are created in the class {@link PyExc}.
 * These are the objects under test.
 */
@DisplayName("Selected Python exception types ...")
class PyExcTypesTest {

    /**
     * Provide a stream of examples as parameter sets to the tests.
     *
     * @return the examples for representation tests.
     */
    static Stream<Arguments> representationClasses() {
        return Stream.of(//
                rep(PyExc.BaseException), //
                rep(PyExc.Exception), //
                rep(PyExc.TypeError) // , //
                // FIXME: Missing feature: Subclass without __new__
                //rep(PyNameError.class, PyExc.NameError,
                //        new Object[] {"x"}, new String[] {"name"}) //
        );
    }

    private static Arguments rep(PyType type) {
        return rep(PyBaseException.class, type, NO_ARGS, NO_KWDS);
    }

    private static Arguments rep(Class<? extends PyBaseException> cls,
            PyType type, Object... args) {
        return rep(cls, type, args, NO_KWDS);
    }

    /**
     * Create a test case for {@link #representationClasses()}.
     * @param cls Java class of the representation
     * @param type Python type of the exception
     * @param args positional arguments to give to a constructor
     * @param kwds keyword arguments to give to a constructor
     * @return arguments for the test
     */
    private static Arguments rep(Class<? extends PyBaseException> cls,
            PyType type, Object[] args, String[] kwds) {
        Object[] a = new Object[args.length + 1];
        System.arraycopy(args, 0, a, 1, args.length);
        a[0] = "Test message";
        String repName = cls.getSimpleName();
        return arguments(type, repName, cls, a, kwds);
    }

    @SuppressWarnings("static-method")
    @DisplayName("share the expected Java representations ...")
    @MethodSource("representationClasses")
    @ParameterizedTest(name = "{0} -> {1}")
    <T extends PyBaseException> void sharedRepresentation(PyType type,
            String repName, Class<T> cls, Object[] args, String[] kwds)
            throws Throwable {
        // Try to construct an instance
        Object exc = Callables.call(type, args, kwds);
        assertInstanceOf(cls, exc);
    }

    private static Object[] NO_ARGS = Util.EMPTY_ARRAY;
    private static String[] NO_KWDS = Util.EMPTY_STRING_ARRAY;
}
