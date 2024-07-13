package uk.co.farowl.vsj4.runtime;

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

import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.ReplaceableType;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SimpleType;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;

/**
 * Test that the Python type system comes into existence in a consistent
 * state, in response to use. We use subclasses of this test, each to be
 * run in a fresh JVM, to make the same set of tests after the type
 * system is woken in the several materially different ways possible:
 * <ol>
 * <li>A call to {@link TypeRegistry#get(Class)}, which be the result of
 * a Python operation (or type enquiry) on any object at all.</li>
 * <li>Initialising the class of a built-in type that entails a call to
 * {@link PyType#fromSpec(uk.co.farowl.vsj4.runtime.TypeSpec)
 * PyType.fromSpec}. Initialisation precedes any call or reference to a
 * to a member (such as the static {@code TYPE}).</li>
 * <li>As an extension of 2, any reference to the type objects for
 * {@code type} or {@code object}, which are created with their own
 * alternative to {@code PyType.fromSpec()}.</li>
 * </ol>
 * The design keeps the number of <i>materially</i> different ways to a
 * minimum, by funnelling all causes into essentially the same bootstrap
 * process, and placing all static initialisation on the one class
 * {@link PyType}.
 * <p>
 * When testing, we arrange to run each subclass of this test in a new
 * JVM. (See the {@code kernelTest} target in the build.)
 */
@DisplayName("Without any preparation")
class TypeInitTest {

    /**
     * The action that triggers the type system. Override this to choose
     * an event that starts the system. It must be annotated in the
     * subclass with {@code @BeforeEach}.
     */
    @BeforeAll
    static void setUp() {};

    /** After setUp() a type exists for {@code object}. */
    @Test
    @DisplayName("PyBaseObject.TYPE exists")
    void object_exists() {
        PyType object = PyBaseObject.TYPE;
        assertNotNull(object);
        assertInstanceOf(AdoptiveType.class, object);
        assertEquals("object", object.getName());
    }

    /** After setUp() we can look up {@code Object.class}. */
    @Test
    @Disabled("TypeRegistry.get not implemented yet")
    @DisplayName("we can look up a type for Object.class")
    void lookup_object_type() {
        TypeRegistry registry = PyType.registry;
        Representation rep = registry.get(Object.class);
        assertNotNull(rep);
        PyType type = rep.pythonType(new Object());
        assertNotSame(type, rep);
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
    @Disabled("TypeRegistry.get not implemented yet")
    @DisplayName("we can look up a type for PyType.class")
    void lookup_type_type() {
        TypeRegistry registry = PyType.registry;
        Representation rep = registry.get(PyType.class);
        assertNotNull(rep);
        assertInstanceOf(SimpleType.class, rep);
        PyType type = rep.pythonType(null);
        assertSame(rep, type);
        assertEquals("type", type.getName());
    }

    /** After setUp() all type implementations have the same type. */
    @Test
    @Disabled("TypeRegistry.get not implemented yet")
    @DisplayName("Subclasses of PyType share a type object")
    void type_subclasses_share_type() {
        // Lookup for the base type
        TypeRegistry registry = PyType.registry;
        Representation repType = registry.get(PyType.class);
        assertNotNull(repType);
        PyType typeType = repType.pythonType(null);
        // Lookup each Java subclass has the same type
        for (Class<? extends PyType> c : List.of(SimpleType.class,
                AdoptiveType.class, ReplaceableType.class)) {
            Representation rep = registry.get(c);
            assertNotSame(repType, rep);
            PyType type = rep.pythonType(null);
            assertSame(typeType, type);
        }
    }
}
