// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after creating a type with {@link PyType#fromSpec(TypeSpec)}.
 * The tests are all in the superclass: this class supplies only the
 * initialising event in {@link #setUpClass()}.
 */
@DisplayName("After creating a type with PyType.fromSpec() ...")
class TypeInitTestFromSpec extends TypeInitTest {

    /** Initialised in {@link #setUpClass()}. */
    static MyType myType;

    /** Example user-defined type. */
    static class MyType {
        /** Construct trivially. */
        MyType() {}

        /** The Python type. */
        PyType TYPE = PyType.fromSpec(
                new TypeSpec("MyType", MethodHandles.lookup()));
    }

    /** Deliberate creation of the type registry singleton. */
    @BeforeAll
    static void setUpClass() { myType = new MyType(); }
}
