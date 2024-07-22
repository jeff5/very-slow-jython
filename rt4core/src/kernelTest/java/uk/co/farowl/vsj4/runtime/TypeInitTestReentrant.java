// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * When a Python class defined in Java names another as a base, and
 * neither type has yet been created for Python, reentrant calls are
 * made to {@link PyType#fromSpec(TypeSpec)}. This test exercises that
 * process (in the {@code @BeforeAll} set up), and in its cases verifies
 * invariants we claim for the {@code TypeFactory}.
 */
@DisplayName("On Java-defining a Python class referring to another")
class TypeInitTestReentrant extends TypeInitTest {

    /** Result of trigger action (if any). */
    protected static MyOther object;

    /**
     * The action that triggers the type system. Override this to choose
     * an event that starts the system. It must be annotated in the
     * subclass with {@code @BeforeAll}.
     */
    @BeforeAll
    static void setUpClass() { object = new MyOther(); };

    /** A simple type defined in Java. */
    static class MyClass {
        /** Python type of MyClass. */
        static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("MyClass", MethodHandles.lookup()));
    }

    // XXX Add some specific tests.

    /**
     * A type defined in Java that references another type.
     * <p>
     * This doesn't currently cause any reentrant behaviour. What would?
     * How about an exposed method taking or returning a
     * {@code MyClass}?
     */
    static class MyOther {
        /** Static init of this class will require MyClass to init. */
        static MyClass friend = new MyClass();
        /** Python type of MyOther. */
        static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("MyOther", MethodHandles.lookup()));
    }
}
