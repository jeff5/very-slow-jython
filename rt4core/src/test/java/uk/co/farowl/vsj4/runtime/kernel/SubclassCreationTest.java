// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClassAssignment;
import uk.co.farowl.vsj4.runtime.WithDict;
import uk.co.farowl.vsj4.runtime.WithDictAssignment;
import uk.co.farowl.vsj4.runtime.WithSlots;

/**
 * This is a test of a process behind class definition in Python. The
 * Java class that will represent instances of the new Python class
 * must, in general, be created dynamically by the run-time system. It
 * will be a subclass of all the bases, and will have {@code __dict__}
 * or {@code __slots__} (or both). However, we also avoid making
 * identical classes.
 * <p>
 * The test mainly checks that correct classes are created, and can be
 * found again when an identical need arises (i.e. a layout-compatible
 * type).
 * <p>
 * When we create a type (such as {@code float}) from a representation
 * (Java) class definition we write ourselves, the class definition
 * exists first, a type specification can be written that refers to it,
 * and the specification given to the type factory to create a
 * {@link Representation} and a type object.
 * <p>
 * When we execute a class definition in Python, Python supplies us with
 * the specification first, from which we can deduce the form of the
 * representation class, and any additional characteristics of the type
 * object.
 */
@DisplayName("A Python subclass ...")
class SubclassCreationTest {

    /**
     * Factory for the tests. Although this couples the tests through a
     * single factory, it contributes to the test to have it used
     * multiple times.
     */
    static final SubclassFactory factory = new SubclassFactory(
            SubclassCreationTest.class.getPackageName()
                    .replace(".kernel", ".subclasses"),
            "TEST$%s$%d");

    /** {@code ClassLoader} for the tests. */
    // XXX Not really sure where this comes from IRL.
    static class TestClassLoader extends ClassLoader {
        public Class<?> defineClass(byte[] b) {
            return defineClass(null, b, 0, b.length);
        }
    }

    /** {@code ClassLoader} for the tests. */
    static final TestClassLoader LOADER = new TestClassLoader();

    /**
     * Base of tests that create and compare Java representations of
     * Python subclasses.
     */
    abstract static class AbstractSubclassTest {
        /**
         * Provide a stream of examples as parameter sets to the tests.
         *
         * @return the examples for representation tests.
         */
        static Stream<Arguments> dictExamples() {
            return Stream.of(//
                    // No bases no slots
                    subclassExample(HCD_O.class, "O1"), //
                    subclassExample(HCD_O.class, "O2"), //
                    subclassExample(HCD_F.class, "F1", PyFloat.TYPE) //
            );
        }

        /**
         * Construct parameters for a test of subclass creation, where
         * the reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @return parameters for the test
         */
        private static Arguments subclassExample(Class<?> refClass,
                String name, PyType... bases) {
            return subclassExample(refClass, name, bases, Map.of());
        }

        /**
         * Construct parameters for a test of subclass creation, where
         * the reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param base (single) of the Python type
         * @param namespace of the Python class
         * @return parameters for the test
         */
        private static Arguments subclassExample(Class<?> refClass,
                String name, PyType base,
                Map<String, Object> namespace) {
            PyType[] bases = {base};
            return subclassExample(refClass, name, bases, namespace);
        }

        /**
         * Construct parameters for a test of subclass creation, where
         * the reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @param namespace of the Python class
         * @return parameters for the test
         */
        private static Arguments subclassExample(Class<?> refClass,
                String name, PyType[] bases,
                Map<String, Object> namespace) {

            // Make a string title "class name(b0, b1, b2):"
            StringBuilder title = new StringBuilder(80);
            title.append("class ").append(name);
            if (bases.length > 0) {
                StringJoiner sj = new StringJoiner(", ", "(", ")");
                for (PyType b : bases) { sj.add(b.getName()); }
                title.append(sj.toString());
            }
            title.append(':');

            /*
             * Find the most-derived representations of all the bases,
             * i.e. b's canonical class is assignable from baseClass for
             * all b.
             */
            // We are not collecting interfaces for now.
            Class<?> baseClass = Object.class;
            for (PyType b : bases) {
                Class<?> canonical = b.javaType();
                if (canonical != baseClass) {
                    if (baseClass.isAssignableFrom(canonical)) {
                        // b.canonical is the more derived: take it
                        baseClass = canonical;
                    } else if (canonical.isAssignableFrom(baseClass)) {
                        // baseClass is the more derived: keep it
                    } else {
                        // Neither more nor less: that's bad
                        fail("cannot resolve bases " + title);
                    }
                }
            }

            // We may have slots
            if (namespace.containsKey("__slots__")) {
                title.append("with __slots__");
            }

            // Create a specification
            RepresentationSpec spec =
                    new RepresentationSpec(name, baseClass);

            return arguments(refClass, title.toString(), spec);
        }

    }

    /** A bridge method, generated by the compiler (JVMS 4.6). */
    static final int ACC_BRIDGE = 0x40;
    /** Declared synthetic (JVMS 4.6). */
    static final int ACC_SYNTHETIC = 0x1000;
    /** Not in our prototype source code if any of these modifiers. */
    static final int SYNTHETIC = ACC_SYNTHETIC | ACC_BRIDGE;

    /** Tests of subclasses that have a {@code __dict__} attribute. */
    @Nested
    @TestMethodOrder(OrderAnnotation.class)
    @DisplayName("a Python subclass with __dict__")
    class TestWithDict extends AbstractSubclassTest {

        @Order(100)
        @DisplayName("can be represented in Java")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dictExamples")
        void createRep(Class<?> refClass, String title,
                RepresentationSpec spec) {
            // Get subclass representation
            byte[] b = factory.findOrCreateSubclass(spec);

            // Write so we can dump it later.
            String fn = spec.getName() + ".class";
            try (OutputStream f = new FileOutputStream(fn)) {
                f.write(b);
            } catch (IOException e) {
                fail("writing class file");
            }

            // Create actual class object (but do not initialise).
            Class<?> c = LOADER.defineClass(b);

            // c should have the same super class as the reference.
            Class<?> refSuper = refClass.getSuperclass();
            Class<?> cSuper = c.getSuperclass();
            assertSame(refSuper, cSuper);

            // c should have the same modifier bits as the reference
            int refModifiers = refClass.getModifiers();
            // except the reference class is static.
            refModifiers &= ~Modifier.STATIC;
            assertModifiersMatch(c.getName(), refModifiers,
                    c.getModifiers());
        }

        @Order(110)
        @DisplayName("declares the expected fields")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dictExamples")
        void expectedFields(Class<?> refClass, String title,
                RepresentationSpec spec) {
            // Get subclass representation
            byte[] b = factory.findOrCreateSubclass(spec);
            Class<?> c = LOADER.defineClass(b);

            // Collect the fields declared by the reference.
            Map<String, Field> fields = new HashMap<>();
            for (Field f : refClass.getDeclaredFields()) {
                fields.put(f.getName(), f);
            }

            // c should have the same fields as the reference.
            List<Field> extras = new LinkedList<>();
            for (Field f : c.getDeclaredFields()) {
                // Compare the same-name reference field
                String name = f.getName();
                Field ref = fields.get(name);
                if (ref != null) {
                    assertSame(ref.getType(), f.getType());
                    assertModifiersMatch(name, ref.getModifiers(),
                            f.getModifiers());
                    fields.remove(name);
                } else {
                    extras.add(f);
                }
            }

            // What is left in fields was missing from c.
            if (!fields.isEmpty()) {
                fail(String.format("missing field(s): %s",
                        fields.keySet()));
            }
            // What landed in extras was unexpected in c.
            if (!extras.isEmpty()) {
                fail(String.format("unexpected field(s): %s",
                        fields.keySet()));
            }
        }

        @Order(120)
        @DisplayName("declares the expected methods")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dictExamples")
        void expectedMethods(Class<?> refClass, String title,
                RepresentationSpec spec) {
            // Get subclass representation
            byte[] b = factory.findOrCreateSubclass(spec);
            Class<?> c = LOADER.defineClass(b);

            // Collect the methods declared by the reference.
            Map<String, Method> methods = new HashMap<>();
            for (Method m : refClass.getDeclaredMethods()) {
                // Assumes we wrote only one method with each name.
                if ((m.getModifiers() & SYNTHETIC) == 0) {
                    // Not a synthetic or bridge method.
                    String name = m.getName();
                    Method previous = methods.put(name, m);
                    assertNull(previous, name);
                }
            }

            // c should have the same methods as the reference.
            List<Method> extras = new LinkedList<>();
            for (Method m : c.getDeclaredMethods()) {
                // Compare the same-name reference methods
                // Assumes there is only one method with each name.
                String name = m.getName();
                Method ref = methods.get(name);
                if (ref != null) {
                    assertSame(ref.getReturnType(), m.getReturnType());
                    assertModifiersMatch(name, ref.getModifiers(),
                            m.getModifiers());
                    // Parameters should match in number and type.
                    Class<?>[] mp = m.getParameterTypes();
                    Class<?>[] rp = ref.getParameterTypes();
                    int n = rp.length;
                    assertEquals(n, mp.length);
                    for (int i = 0; i < n; i++) {
                        assertSame(rp[i], mp[i]);
                    }
                    methods.remove(name);
                } else {
                    extras.add(m);
                }
            }

            // What is left in methods was missing from c.
            if (!methods.isEmpty()) {
                fail(String.format("missing method(s): %s",
                        methods.keySet()));
            }
            // What landed in extras was unexpected in c.
            if (!extras.isEmpty()) {
                fail(String.format("unexpected method(s): %s",
                        methods.keySet()));
            }
        }

        @Order(200)
        @DisplayName("is represented without duplication")
        @Disabled("Currently we always create a new one")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dictExamples")
        void findExistingRep(Class<?> refClass, String title,
                TypeSpec spec) {
            // Look for subclass representation
            // PyType sub = PyType.fromSpec(spec);
            // assertInstanceOf(ReplaceableType.class, sub);

        }
    }

    /**
     * Check expected value of integer as a set of class or member
     * modifier bits.
     *
     * @param name of class or member
     * @param expected modifier bits
     * @param actual modifier bits
     */
    static void assertModifiersMatch(String name, int expected,
            int actual) {
        int missing = expected & ~actual, extras = actual & ~expected;
        if (missing != 0) {
            fail(name + ": missing modifier(s): "
                    + Modifier.toString(missing));
        }
        if (extras != 0) {
            fail(name + ": unexpected modifier(s): "
                    + Modifier.toString(extras));
        }
    }

    /**
     * A hand-crafted model of the Java representation that should be
     * formed when a Python class is defined by:<pre>
     * class O1: pass
     * x = O1()
     * </pre>
     * <p>
     * {@code x} will be an instance of this class (but {@code O1} will
     * be an instance of {@code type}).
     */
    static class HCD_O
            implements WithDictAssignment, WithClassAssignment {

        /** Type of this object. */
        private PyType $type;

        /** Instance dictionary. */
        private PyDict $dict;

        HCD_O(PyType actualType) { this.$type = actualType; }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }

        @Override
        public PyDict getDict() { return $dict; }

        @Override
        public void setDict(Object replacementDict) {
            $dict = checkDictAssignment(replacementDict);
        }
    }

    /**
     * A hand-crafted model of the Java representation that should be
     * formed when a Python class is defined by:<pre>
     * class F1(float): pass
     * x = F1(42)
     * </pre>
     * <p>
     * {@code x} will be an instance of this class (but {@code F1} will
     * be an instance of {@code type}).
     */
    static class HCD_F extends PyFloat
            implements WithDictAssignment, WithClassAssignment {

        /** Type of this object. */
        private PyType $type;

        /** Instance dictionary. */
        private PyDict $dict;

        HCD_F(PyType actualType, double value) {
            super(value);
            this.$type = actualType;
        }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }

        @Override
        public PyDict getDict() { return $dict; }

        @Override
        public void setDict(Object replacementDict) {
            $dict = checkDictAssignment(replacementDict);
        }
    }

    /**
     * A hand-crafted model of the Java representation that should be
     * formed when a Python class is defined by:<pre>
     * class Meta(type): pass
     * class MyClass(metaclass=Meta): pass
     * x = MyClass()
     * </pre>
     * <p>
     * {@code Meta} will be an instance of (exactly) {@code type}, that
     * happens to name {@code type} as its base. {@code MyClass} will be
     * an instance of this class for which {@code getType()} returns
     * {@code Meta}. {@code x} will be an instance of a class extending
     * {@code Object}, for which {@code getType()} returns
     * {@code MyClass}
     * <p>
     * Note that this class inherits {@link WithDict} from
     * {@link SimpleType} but does not implement
     * {@link WithDictAssignment}.
     */
    static class HCD_T extends SimpleType
            implements WithClassAssignment {

        /** Type of this object. */
        PyType $type;

        HCD_T(PyType metaType, String name) {
            super(name, HCD_T.class, new PyType[] {PyType.TYPE});
            this.$type = metaType;
        }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }

    }

    /**
     * A hand-crafted model of the Java representation that formed when
     * a Python class is defined by:<pre>
     * class HCSObject:
     *     __slots__ = ('a', 'b')
     * </pre>
     */
    static class HCS_O implements WithSlots, WithClassAssignment {

        /** Type of this object. */
        PyType $type;
        /* Variables corresponding to {@code __slots__} names. */
        // @Exposed.Member
        Object a;
        // @Exposed.Member
        Object b;

        HCS_O(ReplaceableType actualType) { this.$type = actualType; }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }

        @Override
        public Object getSlot(int i) {
            // TODO Do we do this?
            return null;
        }

        @Override
        public void setSlot(int i, Object value) {
            // TODO Do we do this?
        }
    }
}
