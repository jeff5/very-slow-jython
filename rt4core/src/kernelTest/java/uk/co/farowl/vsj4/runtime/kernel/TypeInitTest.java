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

import uk.co.farowl.vsj4.runtime.PyBaseObject;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Adopted;

/**
 * Test that the Python type system (a singleton) has come into
 * operation in a consistent state. We use subclasses of this test, each
 * to be run in a fresh JVM, to make the same set of tests after the
 * type system is woken in the several materially different ways
 * possible:
 * <ol>
 * <li>A call to {@link Representation#fromClass(Class)}, which be the
 * result of a Python operation (or type enquiry) on any object at
 * all.</li>
 * <li>Initialising the class of a built-in type that entails a call to
 * {@link PyType#fromSpec(uk.co.farowl.vsj4.runtime.TypeSpec)
 * PyType.fromSpec}. Initialisation precedes any call or reference to a
 * to a member (such as the static {@code TYPE}).</li>
 * <li>As an extension of 2, any reference to the type objects for
 * {@code type} or {@code object}, which are created with their own
 * alternative to {@code PyType.from()}.</li>
 * </ol>
 * The design keeps the number of {@code materially} different ways to a
 * minimum, by funnelling all causes into essentially the same bootstrap
 * process, because it is difficult to reason about initialisation.
 * Correct eventual operation depends on the order of events that the
 * JVM ultimately determines.
 * <p>
 * When testing, we arrange to run each subclass of this test in a new
 * JVM. (See the {@code kernelTest} target in the build.)
 */
@DisplayName("Without any preparation")
class TypeInitTest {

    /**
     * A series of tests that critical objects have been created. These
     * tests are inherited by concrete test classes that have initiated
     * the type system in different ways.
     */

    /**
     * The action that triggers the type system. Override this to choose
     * an event that starts the system. It must be annotated in the
     * subclass with {@code @BeforeEach}.
     *
     * @throws Exception when anything bad happens
     */
    @BeforeAll
    static void setUp() throws Exception {};

    /** After setUp() a type exists for {@code object}. */
    @Test
    @DisplayName("PyBaseObject.TYPE exists")
    void object_exists() {
        // If *only* registry exists, this next initialises factory:
        PyType object = PyBaseObject.TYPE;
        assertNotNull(object);
        assertInstanceOf(AdoptiveType.class, object);
        assertEquals("object", object.getName());
    }

    /** After setUp() we can look up {@code Object.class}. */
    @Test
    @Disabled("Representation.of not implemented yet")
    @DisplayName("there is a type for Object.class")
    void creates_object_type() {
        TypeRegistry registry = TypeRegistry.getInstance();
        Representation rep = registry.lookup(Object.class);
        assertNotNull(rep);
        assertInstanceOf(Adopted.class, rep);
        PyType type = rep.pythonType(null);
        assertEquals("object", type.getName());
    }

    /** After setUp() a type exists for {@code type}. */
    @Test
    @DisplayName("PyType.TYPE exists")
    void type_exists() {
        PyType type = PyType.TYPE;
        assertNotNull(type);
        assertInstanceOf(SimpleType.class, type);
        assertEquals("type", type.getName());
    }

    /** After setUp() we can look up {@code PyType.class}. */
    @Test
    @Disabled("Representation.of not implemented yet")
    @DisplayName("there is a type for PyType.class")
    void creates_type_type() {
        TypeRegistry registry = TypeRegistry.getInstance();
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
        // Lookup for the base type
        TypeRegistry registry = TypeRegistry.getInstance();
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
