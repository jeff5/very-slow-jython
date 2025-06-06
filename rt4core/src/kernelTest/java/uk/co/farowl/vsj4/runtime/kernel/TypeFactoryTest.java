// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.List;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Test that the Python type system, {@link TypeFactory} and instances
 * of a few associated classes, may be brought into operation in a
 * consistent state. The {@code TypeFactory} is normally a singleton,
 * created during the static initialisation of {@link PyType}, but for
 * the purpose of testing, we make and discard instances repeatedly.
 * <p>
 * The design keeps the number of <i>materially</i> different
 * initialisation paths to a minimum. In practice, we funnel all actions
 * that cause initialisation into essentially the same bootstrap
 * process, in the static initialisation of {@link PyType}. It is too
 * difficult to reason about otherwise. In this test we subvert that to
 * test copies of the parts separately.
 * <p>
 * When testing, we arrange to run each test involving initialisation in
 * a new JVM. (See the {@code kernelTest} target in the build.)
 */
@Disabled("We prevent multiple factories")
@DisplayName("In a new TypeFactory")
class TypeFactoryTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeFactoryTest.class);

    /** A lookup with package scope as in {@link PyType}. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    /**
     * Satisfy the reference in the {@code TypeFactory} constructor,
     * with an implementation of {@link TypeExposer} that does nothing.
     */
    Function<PyType, TypeExposer> nullFactory =
            (PyType t) -> new TypeExposer() {
                @Override
                public void exposeMethods(Class<?> definingClass) {}

                @Override
                public void exposeMembers(Class<?> memberClass) {}

                @Override
                public Iterable<Entry> entries(Lookup lookup) {
                    return List.of();
                }
            };

    /**
     * Statically initialise the type system. Any reference to a
     * {@code PyType} member or subclass creates the static singleton
     * instance of {@code TypeFactory}, but not the ones we use in the
     * tests. Since we can't avoid doing that, let's get it over with.
     */
    @BeforeAll
    static void initPyType() {
        try {
            @SuppressWarnings("unused")
            PyType dummy = PyType.TYPE();
        } catch (ExceptionInInitializerError e) {
            if (e.getCause() instanceof InterpreterError ie) {
                /*
                 * Ignore this in order to run the tests that will give
                 * us a better diagnosis.
                 */
                logger.warn("Ignoring problem in static init");
            }
        }
    }

    /** A type exists for {@code object}. */
    @Test
    @DisplayName("'object' exists")
    void object_exists() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory(LOOKUP, nullFactory);
        @SuppressWarnings("deprecation")
        PyType object = factory.typeForType().getBase();
        assertNotNull(object);
        assertInstanceOf(SimpleType.class, object);
        assertEquals("object", object.getName());
    }

    /** A type exists for {@code type}. */
    @Test
    @DisplayName("'type' exists")
    void type_exists() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory(LOOKUP, nullFactory);
        @SuppressWarnings("deprecation")
        PyType type = factory.typeForType();
        assertNotNull(type);
        assertInstanceOf(SimpleType.class, type);
        assertEquals("type", type.getName());
    }

    /** No type object is public for {@code PyType.class}. */
    @Test
    @DisplayName("PyType.class is not registered")
    void type_type_unpublished() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory(LOOKUP, nullFactory);
        TypeRegistry registry = factory.getRegistry();
        Representation rep = registry.lookup(PyType.class);
        assertNull(rep);
    }

    /** No type object is public for {@code Object.class}. */
    @Test
    @DisplayName("Object.class is not registered")
    void object_type_unpublished() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory(LOOKUP, nullFactory);
        TypeRegistry registry = factory.getRegistry();
        Representation rep = registry.lookup(Object.class);
        assertNull(rep);
    }
}
