// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import java.lang.invoke.MethodHandles.Lookup;
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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.Feature;
import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClassAssignment;
import uk.co.farowl.vsj4.runtime.WithDict;
import uk.co.farowl.vsj4.runtime.WithDictAssignment;
import uk.co.farowl.vsj4.runtime.subclass.SubclassFactory;
import uk.co.farowl.vsj4.runtime.subclass.SubclassSpec;
import uk.co.farowl.vsj4.support.internal.Util;

/**
 * This is a test of a process behind class definition in Python. We
 * create a specification, as we might when interpreting a class
 * definition, and generate a Java class in byte code that will
 * represent instances of the new Python class. We then compare
 * properties of the generated class with a hand-crafted prototype.
 * <p>
 * The new Python class will be a subclass (in Python) of all the bases,
 * and will have {@code __dict__} or {@code __slots__} (or both).
 * However, we also avoid making identical classes. The new Java
 * representation class may only be a subclass (in Java) of one base. It
 * follows that the bases are only suitable if their representations lie
 * on a single inheritance hierarchy.
 * <p>
 * The test mainly checks that correct classes are created, and can be
 * found again when an identical need arises (i.e. a layout-compatible
 * Python class is defined).
 */
class SubclassCreationTest {

    /**
     * We create Java representations of classes directly, using only
     * the code generation aspects of defining a subclass. See
     * {@link DynamicBase} for a parallel test going via capabilities in
     * the type object.
     */
    @Nested
    @DisplayName("A synthetic Java representation  ...")
    class BareJavaRepresentation {
        /**
         * Factory for the tests. Although this couples the tests
         * through a single factory, it contributes to the test to have
         * it used multiple times.
         */
        static final SubclassFactory factory =
                new SubclassFactory("BARE$%s$%d");

        /**
         * Provide a stream of examples as parameter sets to the tests.
         *
         * @return the examples for representation tests.
         */
        static Stream<Arguments> bareExamples() {
            return Stream.of(//
                    // No bases no slots
                    bareExample(HCD_O.class, "O"), //
                    // Simple base, no slots
                    bareExample(HCD_F.class, "F", PyFloat.TYPE), //
                    // No bases, slots
                    bareExample(HCS_Oa.class, "Oa", List.of(),
                            List.of("a")), //
                    // Simple base, slots
                    bareExample(HCS_Fabc.class, "Fabc", PyFloat.TYPE,
                            List.of("c", "a", "b")) //
            );
        }

        /**
         * Construct parameters for a test of subclass creation, with
         * the implied Python base {@code object}, no {@code __slots__}
         * and an instance dictionary. The reference result is expressed
         * through a hand-crafted class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @return parameters for the test
         */
        private static Arguments bareExample(Class<?> refClass,
                String name) {
            return bareExample(refClass, name, List.of());
        }

        /**
         * Construct parameters for a test of subclass creation, with a
         * single Python base, {@code __slots__} and no instance
         * dictionary. The reference result is expressed through a
         * hand-crafted class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param base (single) of the Python type or {@code null}
         * @param slots {@code __slots__} or {@code null}
         * @return parameters for the test
         */
        private static Arguments bareExample(Class<?> refClass,
                String name, PyType base, List<String> slots) {
            if (base == null) { base = PyObject.TYPE; }
            return bareExample(refClass, name, List.of(base), slots);
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
        private static Arguments bareExample(Class<?> refClass,
                String name, PyType base) {
            if (base == null) { base = PyObject.TYPE; }
            return bareExample(refClass, name, List.of(base), null);
        }

        /**
         * Construct parameters for a test of subclass creation, with
         * Python bases, no {@code __slots__} and an instance
         * dictionary. The reference result is expressed through a
         * hand-crafted class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @return parameters for the test
         */
        private static Arguments bareExample(Class<?> refClass,
                String name, List<PyType> bases) {
            return bareExample(refClass, name, bases, null);
        }

        /**
         * Construct parameters for a test of subclass creation, with
         * Python bases, and either {@code __slots__} or an instance
         * dictionary. The reference result is expressed through a
         * hand-crafted class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @param slots {@code __slots__} or {@code null}
         * @return parameters for the test
         */
        private static Arguments bareExample(Class<?> refClass,
                String name, List<PyType> bases, List<String> slots) {

            // Make a string title "class name(b0, b1, b2):"
            String title = generateClassDeclaration(name, bases, slots);

            /*
             * Find the most-derived representations of all the bases,
             * i.e. b's canonical class is assignable from baseClass for
             * all b, and the interfaces any of them asks. We're not as
             * thorough here as we must be in PyType, just aiming to
             * catch mistakes building a test.
             */
            Set<Class<?>> interfaces = new HashSet<>();
            PyType baseType = findBaseClass(interfaces, bases, title);

            // Create a specification
            SubclassSpec spec =
                    new SubclassSpec(name, baseType.javaClass());
            spec.addInterfaces(interfaces);
            if (slots != null) { spec.addSlots(slots); }

            return arguments(refClass, title.toString(), spec, name);
        }

        /**
         * We verify that when two specifications are equal, we do not
         * create a second the subclass representation. This is
         * essential for the operation of class assignment to be
         * possible between sufficiently similar types.
         *
         * @param refClass a local class like the one we need
         * @param title roughly equivalent Python class definition
         * @param spec we are trying to satisfy.
         */
        @DisplayName("is created without duplication")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("bareExamples")
        void withoutDuplication(Class<?> refClass, String title,
                SubclassSpec spec) {
            /*
             * When run as a single test in the IDE, this first call to
             * findOrCreateSubclass will create the class, and the
             * second call should return that class from the cache. When
             * run as a batch, this may not be the first test, so one of
             * the other tests may already have created the class and
             * both calls will return the class from the cache. This
             * does not invalidate the test.
             */
            // Get a Java subclass representation for refspec
            SubclassSpec refspec = spec.clone();
            assertEquals(spec, refspec, "clones not equal");
            Lookup reflu = factory.findOrCreateSubclass(refspec);

            // Get a Java subclass representation for spec
            Lookup lu = factory.findOrCreateSubclass(spec);
            // It should be the same as for refspec
            assertSame(reflu, lu);
        }

        @DisplayName("has the expected superclass and modifiers")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("bareExamples")
        void createRep(Class<?> refClass, String title,
                SubclassSpec spec) {
            // Get subclass representation
            Lookup lu = factory.findOrCreateSubclass(spec);
            Class<?> c = lu.lookupClass();

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
        @MethodSource("bareExamples")
        void expectedFields(Class<?> refClass, String title,
                SubclassSpec spec) {
            // Get subclass representation
            Lookup lu = factory.findOrCreateSubclass(spec);
            Class<?> c = lu.lookupClass();

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

        @DisplayName("bears the expected interfaces")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("bareExamples")
        void expectedInterfaces(Class<?> refClass, String title,
                SubclassSpec spec) {
            // Get subclass representation
            Lookup lu = factory.findOrCreateSubclass(spec);
            Class<?> c = lu.lookupClass();

            // Collect the interfaces declared by the reference.
            Map<String, Class<?>> interfaces = new HashMap<>();
            for (Class<?> i : refClass.getInterfaces()) {
                interfaces.put(i.getName(), i);
            }

            // c should have the same interfaces as the reference.
            List<Class<?>> extras = new LinkedList<>();
            for (Class<?> i : c.getInterfaces()) {
                // Compare the same-name reference interface
                String name = i.getName();
                Class<?> ref = interfaces.get(name);
                if (ref != null) {
                    assertSame(ref, i);
                    interfaces.remove(name);
                } else {
                    extras.add(i);
                }
            }

            // What is left in interfaces was missing from c.
            if (!interfaces.isEmpty()) {
                fail(String.format("missing interface(s): %s",
                        interfaces.keySet()));
            }
            // What landed in extras was unexpected in c.
            if (!extras.isEmpty()) {
                fail(String.format("unexpected interface(s): %s",
                        interfaces.keySet()));
            }
        }

        @DisplayName("declares the expected methods")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("bareExamples")
        void expectedMethods(Class<?> refClass, String title,
                SubclassSpec spec) {
            // Get subclass representation
            Lookup lu = factory.findOrCreateSubclass(spec);
            Class<?> c = lu.lookupClass();

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
    }

    /**
     * We create new types and instances of their Java representations
     * going via capabilities in the type object, to supply test cases
     * with a specific base.
     */
    static class DynamicBase {
        /**
         * {@link SubclassFactory} to use in the tests. Although this
         * couples the tests through a single factory, using multiple
         * times implicitly tests the caching and re-use of
         * representations with the same specification.
         */
        static final SubclassFactory factory =
                new SubclassFactory("DYNAMIC$%s$%d");

        /**
         * Construct parameters for a test of subclass creation and
         * instantiation,
         *
         * with the implied Python base {@code object},
         *
         * no {@code __slots__},
         *
         * an instance dictionary,
         *
         * and no arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name) {
            return dynamicExample(refClass, name, List.of());
        }

        /**
         * Construct parameters for a test of subclass creation,
         *
         * with a single Python base,
         *
         * {@code __slots__},
         *
         * no instance dictionary,
         *
         * and no arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param base (single) of the Python type or {@code null}
         * @param slots {@code __slots__} or {@code null}
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name, PyType base, List<String> slots) {
            if (base == null) { base = PyObject.TYPE; }
            return dynamicExample(refClass, name, List.of(base), slots);
        }

        /**
         * Construct parameters for a test of subclass creation,
         *
         * with a single Python base,
         *
         * no {@code __slots__},
         *
         * an instance dictionary,
         *
         * and no arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param base (single) of the Python type or {@code null}
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name, PyType base) {
            if (base == null) { base = PyObject.TYPE; }
            return dynamicExample(refClass, name, List.of(base), null);
        }

        /**
         * Construct parameters for a test of subclass creation,
         *
         * with multiple Python bases,
         *
         * no {@code __slots__},
         *
         * an instance dictionary,
         *
         * and no arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name, List<PyType> bases) {
            return dynamicExample(refClass, name, bases, null,
                    Util.EMPTY_ARRAY, Util.EMPTY_STRING_ARRAY);
        }

        /**
         * Construct parameters for a test of subclass creation,
         *
         * with Python bases,
         *
         * {@code __slots__},
         *
         * no instance dictionary,
         *
         * and no arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name, List<PyType> bases, List<String> slots) {
            return dynamicExample(refClass, name, bases, slots,
                    Util.EMPTY_ARRAY, Util.EMPTY_STRING_ARRAY);
        }

        /**
         * Construct parameters for a test of subclass creation,
         *
         * with Python bases,
         *
         * either {@code __slots__}
         *
         * or an instance dictionary,
         *
         * and classic arguments to the constructor.
         *
         * The reference result is expressed through a hand-crafted
         * class.
         *
         * @param refClass the reference result
         * @param name of the Python type to create
         * @param bases of the Python type (empty means {@code object})
         * @param slots {@code __slots__} or {@code null}
         * @return parameters for the test
         */
        protected static Arguments dynamicExample(Class<?> refClass,
                String name, List<PyType> bases, List<String> slots,
                Object[] args, String[] kwds) {

            // Make a string title "class name(b0, b1, b2):"
            String title = generateClassDeclaration(name, bases, slots);

            // XXX Shouldn't this be automatic?
            Set<Class<?>> interfaces = new HashSet<>();
            // interfaces.add(WithClassAssignment.class);

            /*
             * Find the most-derived representations of all the bases,
             * i.e. b's canonical class is assignable from baseClass for
             * all b, and the interfaces any of them asks. We're not as
             * thorough here as we must be in __build_class__, just
             * aiming to catch mistakes building a test.
             */
            /// XXX This should be in PyType or __build_class__
            PyType baseType = findBaseClass(interfaces, bases, title);

            // Create a specification from baseType Java information
            SubclassSpec spec =
                    new SubclassSpec(name, baseType.javaClass());
            // spec.addInterfaces(interfaces);
            if (slots != null) { spec.addSlots(slots); }
            spec.addConstructors(baseType);

            // Get subclass representation
            Lookup lu = factory.findOrCreateSubclass(spec);
            PyType type = PyType.fromSpec(new TypeSpec(name, lu)
                    .add(Feature.REPLACEABLE)
                    .bases(bases.toArray(new PyType[bases.size()])));

            return arguments(refClass, title.toString(), type, args,
                    kwds);
        }
    }

    /**
     * We create new types and instances of their Java representations
     * going via capabilities in the type object.
     */
    @Nested
    @DisplayName("A Python subclass of object ...")
    class DynamicObjectSubclass extends DynamicBase {
        /**
         * Provide a stream of examples as parameter sets to the tests.
         *
         * @return the examples for dynamic instance tests.
         */
        static Stream<Arguments> dynamicExamples() {
            return Stream.of(//
                    // No bases no slots
                    dynamicExample(HCD_O.class, "O"), //
                    // No bases, slots
                    dynamicExample(HCS_Oa.class, "Oa", List.of(),
                            List.of("a")) //
            );
        }

        @DisplayName("is instantiated by calling its type")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dynamicExamples")
        void newInstance(Class<?> refClass, String title, PyType type,
                Object[] args, String[] kwds) throws Throwable {
            // Instantiate by calling the type
            Object o = Callables.call(type, args, null);
            PyType t = PyType.of(o);
            PyType[] m = t.getMRO();
            assertSame(type, m[0]);
            assertSame(PyObject.TYPE, m[1]);
        }
    }

    /**
     * We create new types and instances of their Java representations
     * going via capabilities in the type object.
     */
    @Nested
    @DisplayName("A Python subclass of float ...")
    class DynamicFloatSubclass extends DynamicBase {
        /**
         * Provide a stream of examples as parameter sets to the tests.
         *
         * @return the examples for dynamic instance tests.
         */
        static Stream<Arguments> dynamicExamples() {
            return Stream.of(//
                    // Simple base, no slots
                    dynamicExample(HCD_F.class, "F",
                            List.of(PyFloat.TYPE), null,
                            new Object[] {42.0}, null), //
                    // Simple base, slots
                    dynamicExample(HCS_Fabc.class, "Fabc",
                            List.of(PyFloat.TYPE),
                            List.of("c", "a", "b"), new Object[] {42.0},
                            null) //
            );
        }

        @DisplayName("is instantiated by calling its type")
        @ParameterizedTest(name = "when defined by {1}")
        @MethodSource("dynamicExamples")
        void newInstance(Class<?> refClass, String title, PyType type,
                Object[] args, String[] kwds) throws Throwable {
            // Instantiate by calling the type
            Object o = Callables.call(type, args, null);
            PyType t = PyType.of(o);
            PyType[] m = t.getMRO();
            assertSame(type, m[0]);
            assertSame(PyFloat.TYPE, m[1]);
        }
    }

    // TODO: Test complex web of subclasses (of list?) as in narrative

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
     * Find the most-derived representation class of all the bases,
     * which the generated class must extend, and the interfaces that
     * class must implement. That is, {@code b}'s canonical class is
     * assignable from the returned class for all {@code b} in
     * {@code bases}.
     *
     * @param interfaces aggregated from the representation classes
     * @param bases to scan for canonical representations
     * @param title for error messages only
     * @return the most-derived representation class
     */
    private static PyType findBaseClass(Set<Class<?>> interfaces,
            List<PyType> bases, CharSequence title) {

        PyType baseType = PyObject.TYPE;
        Class<?> baseClass = baseType.javaClass();
        assert baseClass == Object.class;

        for (PyType b : bases) {
            Class<?> canonical = b.javaClass();
            if (canonical != baseClass) {
                if (isBaseLike(canonical)) {
                    if (baseClass.isAssignableFrom(canonical)) {
                        // b.canonical is the more derived: take it
                        baseType = b;
                        baseClass = canonical;
                    } else if (canonical.isAssignableFrom(baseClass)) {
                        // baseClass is the more derived: keep it
                    } else {
                        // Neither more nor less: that's bad
                        fail("cannot resolve bases " + title);
                    }
                } else if (isInterfaceLike(canonical)) {
                    // Collect interfaces
                    interfaces.add(canonical);
                } else {
                    fail(String.format(
                            "neither base nor interface %s in %s",
                            b.getName(), title));
                }
            }
        }
        return baseType;
    }

    /**
     * Test that a class is suitable as a base (not final, private or an
     * interface).
     */
    private static boolean isBaseLike(Class<?> c) {
        int m = c.getModifiers();
        return !(Modifier.isInterface(m) || Modifier.isFinal(m)
                || Modifier.isPrivate(m));
    }

    /** Test that a class is an interface. */
    private static boolean isInterfaceLike(Class<?> c) {
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

    // Hand-crafted models of the representation classes -------------

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

        HCD_O(PyType actualType) {
            this.$type = checkClassAssignment(actualType);
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
            this.$type = checkClassAssignment(actualType);
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
            this.$type = checkClassAssignment(metaType);
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

        HCS_Oa(ReplaceableType actualType) {
            this.$type = checkClassAssignment(actualType);
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
            this.$type = checkClassAssignment(actualType);
        }

        @Override
        public PyType getType() { return $type; }

        @Override
        public void setType(Object replacementType) {
            $type = checkClassAssignment(replacementType);
        }
    }

    /**
     * Make a string title for the test of the form
     * {@code class name(b0, b1, b2):} and a {@code __slots__}
     * declaration.
     *
     * @param name for the class
     * @param bases of the class
     * @param slots names of the slots
     * @return title for test
     */
    private static String generateClassDeclaration(String name,
            List<PyType> bases, List<String> slots) {

        StringBuilder sb = new StringBuilder(80);
        sb.append("class ").append(name);
        if (bases.size() > 0) {
            StringJoiner sj = new StringJoiner(", ", "(", ")");
            for (PyType b : bases) { sj.add(b.getName()); }
            sb.append(sj.toString());
        }
        sb.append(':');

        // We may have slots
        if (slots != null) {
            StringJoiner sj =
                    new StringJoiner("', '", " __slots__ = ('",
                            slots.size() == 1 ? "',)" : "')");
            for (String s : slots) { sj.add(s); }
            sb.append(sj.toString());
        }

        return sb.toString();
    }
}
