package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Adopted;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Test that the Python type system, {@link TypeFactory} and instances
 * of a few associated classes, may be brought into operation in a
 * consistent state. This is normally a singleton, created during the
 * static initialisation of {@link PyType}, but for the purpose of
 * testing, we make and discard instances repeatedly.
 * <p>
 * The design keeps the number of {@code materially} different
 * initialisation paths to a minimum, by funnelling all causes into
 * essentially the same bootstrap process, because it is difficult to
 * reason about initialisation. Correct eventual operation depends on
 * the order of events that the JVM ultimately determines.
 * <p>
 * When testing, we arrange to run each test involving initialisation in
 * a new JVM. (See the {@code kernelTest} target in the build.)
 */
@DisplayName("In a new TypeFactory")
class TypeFactoryTest {

    /** Logger for the test. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeFactoryTest.class);

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
            PyType dummy = PyType.TYPE;
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
        TypeFactory factory = new TypeFactory();
        @SuppressWarnings("deprecation")
        PyType object = factory.typeForType().getBase();
        assertNotNull(object);
        assertInstanceOf(AdoptiveType.class, object);
        assertEquals("object", object.getName());
    }

    /** We can look up {@code Object.class}. */
    @Test
    @Disabled("Representation.of not implemented yet")
    @DisplayName("there is a type for Object.class")
    void creates_object_type() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory();
        TypeRegistry registry = factory.getRegistry();
        Representation rep = registry.lookup(Object.class);
        assertNotNull(rep);
        assertInstanceOf(Adopted.class, rep);
        PyType type = rep.pythonType(null);
        assertEquals("object", type.getName());
    }

    /** After setUp() a type exists for {@code type}. */
    @Test
    @DisplayName("'type' exists")
    void type_exists() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory();
        @SuppressWarnings("deprecation")
        PyType type = factory.typeForType();
        assertNotNull(type);
        assertInstanceOf(SimpleType.class, type);
        assertEquals("type", type.getName());
    }

    /** After setUp() we can look up {@code PyType.class}. */
    @Test
    @Disabled("Representation.of not implemented yet")
    @DisplayName("there is a type for PyType.class")
    void creates_type_type() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory();
        TypeRegistry registry = factory.getRegistry();
        Representation rep = registry.lookup(PyType.class);
        assertNotNull(rep);
        assertInstanceOf(SimpleType.class, rep);
        PyType type = rep.pythonType(null);
        assertSame(rep, type);
        assertEquals("type", type.getName());
    }

    /** After setUp() all type implementations have the same type. */
    @Test
    @Disabled("Representation.of not implemented yet")
    @DisplayName("Subclasses of PyType share a type object")
    void type_subclasses_share_type() {
        @SuppressWarnings("deprecation")
        TypeFactory factory = new TypeFactory();
        TypeRegistry registry = factory.getRegistry();
        // Lookup for the base type
        Representation repType = registry.lookup(PyType.class);
        assertNotNull(repType);
        PyType typeType = repType.pythonType(null);
        // Lookup each Java subclass has the same type
        for (Class<? extends PyType> c : List.of(SimpleType.class,
                AdoptiveType.class, ReplaceableType.class)) {
            Representation rep = registry.lookup(c);
            assertNotSame(repType, rep);
            PyType type = rep.pythonType(null);
            assertSame(typeType, type);
        }
    }
}
