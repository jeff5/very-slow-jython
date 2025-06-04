// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after getting {@link PyType#TYPE()} The tests are all in the
 * superclass: this class supplies only the initialising event in
 * {@link #setUpClass()}.
 */
@DisplayName("After calling PyType.TYPE() ...")
class TypeInitTestType extends TypeInitTest {

    /** Initialised in {@link #setUpClass()}. */
    static PyType type;

    /** Start by touching the type object {@code type}. */
    @BeforeAll
    static void setUpClass() { type = PyType.TYPE(); }
}
