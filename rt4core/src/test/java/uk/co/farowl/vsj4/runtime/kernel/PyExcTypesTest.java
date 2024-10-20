// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

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
 * <p>
 * In CPython, the object implementation of many Python exception types
 * is shared with multiple others. This allows multiple inheritance and
 * class assignment amongst user-defined exceptions, with diverse
 * built-in bases, that may be surprising. The following is valid in
 * CPython: <pre>
 * class TE(TypeError): __slots__=()
 * class FPE(FloatingPointError): __slots__=()
 * TE().__class__ = FPE
 * class E(ZeroDivisionError, TypeError): __slots__=()
 * E().__class__ = FPE
 * </pre>In order to meet user expectations from CPython, the Java
 * representation of many Python exception types is shared. For example
 * {@code TypeError}, {@code FloatingPointError} and
 * {@code ZeroDivisionError} must share a representation (that of
 * {@code BaseException}, in fact).
 * <p>
 * CPython prohibits class-assignment involving built-in types directly.
 * For example {@code FloatingPointError().__class__ = E} and its
 * converse are not allowed. There seems to be no structural reason to
 * prohibit it, but we should do so for compatibility.
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
                rep(PyExc.TypeError), //
                rep(PyNameError.class, PyExc.NameError,
                        new Object[] {"x"}, new String[] {"name"}) //
        );
    }

    private static Arguments rep(PyType type) {
        return rep(PyBaseException.class, type, NO_ARGS, NO_KWDS);
    }

    private static Arguments rep(Class<? extends PyBaseException> cls,
            PyType type, Object... args) {
        return rep(cls, type, args, NO_KWDS);
    }

    private static Arguments rep(Class<? extends PyBaseException> cls,
            PyType type, Object[] args, String[] kwds) {
        Object[] a = new Object[args.length + 1];
        System.arraycopy(args, 0, a, 1, args.length);
        a[0] = "Test message";
        String repName = cls.getSimpleName();
        return arguments(type, repName, cls, a, kwds);
    }

    @DisplayName("share the expected Java representations ...")
    @MethodSource("representationClasses")
    @ParameterizedTest(name = "{0} -> {1}")
    <T extends PyBaseException> void sharedRepresentation(PyType type,
            String repName, Class<T> cls, Object[] args, String[] kwds)
            throws NoSuchMethodException, SecurityException,
            InstantiationException, IllegalAccessException,
            IllegalArgumentException, InvocationTargetException {
        // Try to construct an instance
        Constructor<T> cons = cls.getConstructor(PyType.class,
                Object[].class, String[].class);
        T exc = cons.newInstance(type, args, kwds);
        assertInstanceOf(cls, exc);
    }

    private static Object[] NO_ARGS = Util.EMPTY_ARRAY;
    private static String[] NO_KWDS = Util.EMPTY_STRING_ARRAY;
}
