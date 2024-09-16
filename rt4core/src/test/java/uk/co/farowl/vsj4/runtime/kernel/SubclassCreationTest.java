// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithClassAssignment;
import uk.co.farowl.vsj4.runtime.WithDict;
import uk.co.farowl.vsj4.runtime.WithDictAssignment;

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
@DisplayName("A Java representation of a Python class ...")
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

    /**
     * Provide a stream of examples as parameter sets to the tests.
     *
     * @return the examples for representation tests.
     */
    static Stream<Arguments> specExamples() {
        return Stream.of(//
                // No bases no slots
                subclassExample(HCD_O.class, "O"), //
                // Simple base, no slots
                subclassExample(HCD_F.class, "F", PyFloat.TYPE), //
                // No bases, slots
                subclassExample(HCS_Oa.class, "Oa", List.of(),
                        List.of("a")), //
                // Simple base, slots
                subclassExample(HCS_Fabc.class, "Fabc", PyFloat.TYPE,
                        List.of("c", "a", "b")) //
        );
    }

    /**
     * Construct parameters for a test of subclass creation, with the
     * implied Python base {@code object}, no {@code __slots__} and an
     * instance dictionary. The reference result is expressed through a
     * hand-crafted class.
     *
     * @param refClass the reference result
     * @param name of the Python type to create
     * @return parameters for the test
     */
    private static Arguments subclassExample(Class<?> refClass,
            String name) {
        return subclassExample(refClass, name, List.of());
    }

    /**
     * Construct parameters for a test of subclass creation, with a
     * single Python base, {@code __slots__} and no instance dictionary.
     * The reference result is expressed through a hand-crafted class.
     *
     * @param refClass the reference result
     * @param name of the Python type to create
     * @param base (single) of the Python type or {@code null}
     * @param slots {@code __slots__} or {@code null}
     * @return parameters for the test
     */
    private static Arguments subclassExample(Class<?> refClass,
            String name, PyType base, List<String> slots) {
        if (base == null) { base = PyObject.TYPE; }
        return subclassExample(refClass, name, List.of(base), slots);
    }

    /**
     * Construct parameters for a test of subclass creation, with a
     * single Python base, no {@code __slots__} and an instance
     * dictionary. The reference result is expressed through a
     * hand-crafted class.
     *
     * @param refClass the reference result
     * @param name of the Python type to create
     * @param base (single) of the Python type or {@code null}
     * @return parameters for the test
     */
    private static Arguments subclassExample(Class<?> refClass,
            String name, PyType base) {
        if (base == null) { base = PyObject.TYPE; }
        return subclassExample(refClass, name, List.of(base), null);
    }

    /**
     * Construct parameters for a test of subclass creation, with Python
     * bases, no {@code __slots__} and an instance dictionary. The
     * reference result is expressed through a hand-crafted class.
     *
     * @param refClass the reference result
     * @param name of the Python type to create
     * @param bases of the Python type (empty means {@code object})
     * @return parameters for the test
     */
    private static Arguments subclassExample(Class<?> refClass,
            String name, List<PyType> bases) {
        return subclassExample(refClass, name, bases, null);
    }

    /**
     * Construct parameters for a test of subclass creation, with Python
     * bases, and either {@code __slots__} or an instance dictionary.
     * The reference result is expressed through a hand-crafted class.
     *
     * @param refClass the reference result
     * @param name of the Python type to create
     * @param bases of the Python type (empty means {@code object})
     * @param slots {@code __slots__} or {@code null}
     * @return parameters for the test
     */
    private static Arguments subclassExample(Class<?> refClass,
            String name, List<PyType> bases, List<String> slots) {

        // Make a string title "class name(b0, b1, b2):"
        StringBuilder title = new StringBuilder(80);
        title.append("class ").append(name);
        if (bases.size() > 0) {
            StringJoiner sj = new StringJoiner(", ", "(", ")");
            for (PyType b : bases) { sj.add(b.getName()); }
            title.append(sj.toString());
        }
        title.append(':');

        /*
         * Find the most-derived representations of all the bases, i.e.
         * b's canonical class is assignable from baseClass for all b.
         * We're not as thorough here as we must be in PyType, just
         * aiming to catch mistakes building a test.
         */

        Class<?> baseClass = Object.class;
        Set<Class<?>> interfaces = new HashSet<>();

        for (PyType b : bases) {
            Class<?> canonical = b.javaType();
            if (canonical != baseClass) {
                if (baseLike(canonical)) {
                    if (baseClass.isAssignableFrom(canonical)) {
                        // b.canonical is the more derived: take it
                        baseClass = canonical;
                    } else if (canonical.isAssignableFrom(baseClass)) {
                        // baseClass is the more derived: keep it
                    } else {
                        // Neither more nor less: that's bad
                        fail("cannot resolve bases " + title);
                    }
                } else if (interfaceLike(canonical)) {
                    // Collect interfaces
                    interfaces.add(canonical);
                } else {
                    fail(String.format(
                            "neither base nor interface %s in %s",
                            b.getName(), title));
                }
            }
        }

        // Create a specification
        RepresentationSpec spec =
                new RepresentationSpec(name, baseClass);
        spec.addInterfaces(interfaces);

        // We may have slots
        if (slots != null) {
            spec.addSlots(slots);
            StringJoiner sj =
                    new StringJoiner("', '", " __slots__ = ('",
                            slots.size() == 1 ? "',)" : "')");
            for (String s : slots) { sj.add(s); }
            title.append(sj.toString());
        }

        return arguments(refClass, title.toString(), spec);
    }

    private static boolean baseLike(Class<?> c) {
        int m = c.getModifiers();
        return !(Modifier.isInterface(m) || Modifier.isFinal(m)
                || Modifier.isPrivate(m));
    }

    private static boolean interfaceLike(Class<?> c) {
        int m = c.getModifiers();
        return Modifier.isInterface(m);
    }
    // }

    /** A bridge method, generated by the compiler (JVMS 4.6). */
    static final int ACC_BRIDGE = 0x40;
    /** Declared synthetic (JVMS 4.6). */
    static final int ACC_SYNTHETIC = 0x1000;
    /** Not in our prototype source code if any of these modifiers. */
    static final int SYNTHETIC = ACC_SYNTHETIC | ACC_BRIDGE;

    /**
     * We verify that when two specifications are equal, we do not
     * create a second the subclass representation. This is essential
     * for the operation of class assignment to be possible between
     * sufficiently similar types.
     *
     * @param refClass a local class like the one we need
     * @param title roughly equivalent Python class definition
     * @param spec we are trying to satisfy.
     */
    @DisplayName("is represented without duplication")
    @ParameterizedTest(name = "when defined by {1}")
    @MethodSource("specExamples")
    void withoutDuplication(Class<?> refClass, String title,
            RepresentationSpec spec) {
        /*
         * When run as a single test in the IDE, this first call to
         * findOrCreateSubclass will create the class, and the second
         * call should return that class from the cache. When run as a
         * batch, this may not be the first test, so one of the other
         * tests may already have created the class and both calls will
         * return the class from the cache. This does not invalidate the
         * test.
         */
        // Get a Java subclass representation for refspec
        RepresentationSpec refspec = spec.clone();
        assertEquals(spec, refspec, "clones not equal");
        Class<?> refc = factory.findOrCreateSubclass(refspec);

        // Get a Java subclass representation for spec
        Class<?> c = factory.findOrCreateSubclass(spec);
        // It should be the same as for refspec
        assertSame(refc, c);
    }

    @DisplayName("has the expected superclass and modifiers")
    @ParameterizedTest(name = "when defined by {1}")
    @MethodSource("specExamples")
    void createRep(Class<?> refClass, String title,
            RepresentationSpec spec) {
        // Get subclass representation
        Class<?> c = factory.findOrCreateSubclass(spec);

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

    @DisplayName("declares the expected fields")
    @ParameterizedTest(name = "when defined by {1}")
    @MethodSource("specExamples")
    void expectedFields(Class<?> refClass, String title,
            RepresentationSpec spec) {
        // Get subclass representation
        Class<?> c = factory.findOrCreateSubclass(spec);

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

    @DisplayName("declares the expected methods")
    @ParameterizedTest(name = "when defined by {1}")
    @MethodSource("specExamples")
    void expectedMethods(Class<?> refClass, String title,
            RepresentationSpec spec) {
        // Get subclass representation
        Class<?> c = factory.findOrCreateSubclass(spec);

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
        private PyType $type;

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
     * A hand-crafted model of the Java representation that should be
     * formed when a Python class is defined by:<pre>
     * class Oa:
     *     __slots__ = ('a',)
     * </pre>
     */
    static class HCS_Oa implements WithClassAssignment {

        /** Type of this object. */
        private PyType $type;

        /* Variables corresponding to {@code __slots__} names. */
        // @Exposed.Member
        Object a;

        HCS_Oa(ReplaceableType actualType) { this.$type = actualType; }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }
    }

    /**
     * A hand-crafted model of the Java representation that should be
     * formed when a Python class is defined by:<pre>
     * class Fabc(float):
     *     __slots__ = ('a', 'b', 'c')
     * </pre>
     */
    static class HCS_Fabc extends PyFloat
            implements WithClassAssignment {

        /** Type of this object. */
        private PyType $type;

        /* Variables corresponding to {@code __slots__} names. */
        // @Exposed.Member
        Object a;
        // @Exposed.Member
        Object b;
        // @Exposed.Member
        Object c;

        HCS_Fabc(ReplaceableType actualType, double value) {
            super(value);
            this.$type = actualType;
        }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }
    }
}
