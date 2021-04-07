package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles.Lookup;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * A base class to describe a built-in method as it is declared.
 */
abstract class DescriptorSpecification {

    /** Collects the methods declared. */
    final List<Method> methods = new ArrayList<>(1);

    /** Documentation string for the (eventual) descriptor. */
    String doc = null;

    /**
     * Add a method implementation.
     *
     * @param method to add to {@link #methods}
     */
    void add(Method method) {
        methods.add(method);
    }

    /** @return type of thing exposed (normally matches annotation). */
    abstract String getType();

    /** @return the documentation string */
    String getDoc() {
        return doc;
    }

    /**
     * Set the document string (but only once).
     *
     * @param doc document string
     * @throws InterpreterError if {@link #doc} is already set
     */
    void setDoc(String doc) throws InterpreterError {
        if (this.doc == null) {
            this.doc = doc;
        } else {
            throw new InterpreterError(
                    "Unnecessary documentation string");
        }
    }

    /**
     * Create a {@code Descriptor} from this definition. Note that a
     * definition describes the methods as declared, and that there may
     * be any number. The implementation of this method creates a
     * descriptor that matches them to the accepted implementations of
     * the owning class.
     *
     * @param objclass Python type that owns the descriptor
     * @param lookup authorisation to access methods
     * @return descriptor for access to the methods
     * @throws InterpreterError if the method type is not supported
     */
    abstract Descriptor createDescr(PyType objclass, Lookup lookup)
            throws InterpreterError;
}
