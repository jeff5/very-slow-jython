package uk.co.farowl.vsj4.runtime.kernel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import uk.co.farowl.vsj4.runtime.PyBaseObject;
import uk.co.farowl.vsj4.runtime.PyType;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after getting {@link PyBaseObject#TYPE} The tests are all in the
 * superclass: this class supplies only the initialising event in
 * {@link #setUp()}.
 */
@DisplayName("After getting PyBaseObject.TYPE ...")
class TypeInitTestObject extends TypeInitTest {

    /** Initialised in {@link #setUp()}. */
    static PyType object;

    /** Deliberate creation of the type registry singleton. */
    @BeforeAll
    static void setUp() throws Exception { object = PyBaseObject.TYPE; };
}
