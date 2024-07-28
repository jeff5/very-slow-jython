// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after getting {@link PyBaseObject#TYPE} The tests are all in
 * the superclass: this class supplies only the initialising event in
 * {@link #setUpClass()}.
 */
@DisplayName("After getting PyBaseObject.TYPE ...")
class TypeInitTestObject extends TypeInitTest {

    /** Initialised in {@link #setUpClass()}. */
    static PyType object;

    /** Start by touching the type object {@code object}. */
    @BeforeAll
    static void setUpClass() { object = PyBaseObject.TYPE; }
}
