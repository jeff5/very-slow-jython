package uk.co.farowl.vsj4.runtime.kernel;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after the registry is initialised explicitly. The tests are all
 * in the superclass: this class supplies only the initialising event in
 * {@link #setUp()}.
 */
@DisplayName("After TypeRegistry.getInstance() ...")
class TypeInitTestRegistry extends TypeInitTest {

    /** Initialised in {@link #setUp()}. */
    static TypeRegistry registry;

    /** Deliberate creation of the type registry singleton. */
    @BeforeAll
    static void setUp() throws Exception {
        TypeRegistry.getInstance();
    };
}
