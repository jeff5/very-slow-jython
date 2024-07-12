package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;

/**
 * Test that the Python type system comes into operation in a consistent
 * state after creating a type with {@link PyType#fromSpec(TypeSpec)}. The tests are all
 * in the superclass: this class supplies only the initialising event in
 * {@link #setUp()}.
 */
@DisplayName("After creating a type with PyType.fromSpec() ...")
class TypeInitTestFromSpec extends TypeInitTest {

    /** Initialised in {@link #setUp()}. */
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
    static void setUp() throws Exception { myType = new MyType(); };

}
