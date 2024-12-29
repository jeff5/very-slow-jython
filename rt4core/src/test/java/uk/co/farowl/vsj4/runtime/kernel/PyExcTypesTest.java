// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.Py;
import uk.co.farowl.vsj4.runtime.PyAttributeError;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyNameError;
import uk.co.farowl.vsj4.runtime.PyStopIteration;
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
 * The Java classes representing structurally different exceptions
 * create their own Python type objects. The exception types that share
 * these representations are created in the class {@link PyExc}. These
 * are the objects under test.
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
                rep(PyStopIteration.class, PyExc.StopIteration,
                        "StopIteration()", NO_ARGS, NO_KWDS), //
                rep(PyStopIteration.class, PyExc.StopIteration,
                        "StopIteration(42)", new Object[] {42},
                        NO_KWDS), //
                rep(PyNameError.class, PyExc.NameError, "NameError()",
                        new Object[] {"x"}, new String[] {"name"}), //
                rep(PyNameError.class, PyExc.UnboundLocalError,
                        "UnboundLocalError()", new Object[] {"u"},
                        new String[] {"name"}), //
                rep(PyAttributeError.class, PyExc.AttributeError,
                        "AttributeError()", new Object[] {"x", 42},
                        new String[] {"name", "obj"}) //
        );
    }

    private static Arguments rep(PyType type) {
        Object[] args = {"Message"};
        String text = String.format("%s('Message')", type.getName());
        return rep(PyBaseException.class, type, text, args, NO_KWDS);
    }

    /**
     * Create a test case for {@link #representationClasses()}.
     *
     * @param cls Java class of the representation
     * @param type Python type of the exception
     * @param reprText text expected from {@code repr()}
     * @param args positional arguments to give to a constructor
     * @param kwds keyword arguments to give to a constructor
     * @return arguments for the test
     */
    private static Arguments rep(Class<? extends PyBaseException> cls,
            PyType type, String reprText, Object[] args,
            String[] kwds) {
        String repName = cls.getSimpleName();
        return arguments(type, repName, reprText, cls, args, kwds);
    }

    @SuppressWarnings("static-method")
    @DisplayName("share the expected Java representations ...")
    @MethodSource("representationClasses")
    @ParameterizedTest(name = "{0} -> {1}")
    <T extends PyBaseException> void sharedRepresentation(PyType type,
            String repName, String reprText, Class<T> cls,
            Object[] args, String[] kwds) throws Throwable {
        // Try to construct an instance
        Object exc = Callables.call(type, args, kwds);
        assertInstanceOf(cls, exc);
    }

    @SuppressWarnings("static-method")
    @DisplayName("have the expected repr() ...")
    @MethodSource("representationClasses")
    @ParameterizedTest(name = "{2}")
    <T extends PyBaseException> void reprExpected(PyType type,
            String repName, String reprText, Class<T> cls,
            Object[] args, String[] kwds) throws Throwable {
        // Try to construct an instance
        Object exc = Callables.call(type, args, kwds);
        assertEquals(reprText, Abstract.repr(exc));
    }

    @SuppressWarnings("static-method")
    @DisplayName("BaseException rejects keywords")
    @Test
    void kwBaseException() throws Throwable {
        PyBaseException exc =
                (PyBaseException)Callables.call(PyExc.BaseException);
        assertSame(exc.getType(), PyExc.BaseException);
        try {
            Callables.call(PyExc.BaseException, new Object[] {"a"},
                    new String[] {"name"});
        } catch (PyBaseException e) {
            assertSame(e.getType(), PyExc.TypeError);
        }
    }

    @SuppressWarnings("static-method")
    @DisplayName("ArithmeticError rejects keywords")
    @Test
    void kwException() throws Throwable {
        PyBaseException exc =
                (PyBaseException)Callables.call(PyExc.ArithmeticError);
        assertSame(exc.getType(), PyExc.ArithmeticError);
        try {
            Callables.call(PyExc.ArithmeticError, new Object[] {"a"},
                    new String[] {"name"});
        } catch (PyBaseException e) {
            assertSame(e.getType(), PyExc.TypeError);
        }
    }

    @SuppressWarnings("static-method")
    @DisplayName("NameError accepts keyword 'name'")
    @Test
    void kwNameError() throws Throwable {
        PyNameError exc = (PyNameError)Callables.call(PyExc.NameError);
        assertEquals(exc.name(), Py.None);
        exc = (PyNameError)Callables.call(PyExc.NameError,
                new Object[] {"a"}, new String[] {"name"});
        assertEquals(exc.name(), "a");
    }

    @SuppressWarnings("static-method")
    @DisplayName("AttributeError accepts keywords 'name', 'obj'")
    @Test
    void kwAttributeError() throws Throwable {
        PyAttributeError exc =
                (PyAttributeError)Callables.call(PyExc.AttributeError);
        assertEquals(exc.name(), Py.None);
        exc = (PyAttributeError)Callables.call(PyExc.AttributeError,
                new Object[] {"a"}, new String[] {"name"});
        assertEquals(exc.name(), "a");
        exc = (PyAttributeError)Callables.call(PyExc.AttributeError,
                new Object[] {42, "b"}, new String[] {"obj", "name"});
        assertEquals(exc.name(), "b");
        assertEquals(exc.obj(), 42);
    }

    private static Object[] NO_ARGS = Util.EMPTY_ARRAY;
    private static String[] NO_KWDS = Util.EMPTY_STRING_ARRAY;
}
