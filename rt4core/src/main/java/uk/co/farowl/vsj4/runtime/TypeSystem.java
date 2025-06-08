package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.BaseType;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SimpleType;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory.Clash;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * {@code TypeSystem} is the nexus of type object lookup and creation
 * methods in the run-time system, and publishes (to this package) a
 * static reference to the single instance of the Python type factory.
 * The type factory comes into being with the static initialisation of
 * this class.
 * <p>
 * All use of the type system is funnelled through this class in order
 * to ensure this initialisation is complete before types are used. The
 * JVM guarantees that competing threads are made to wait while the
 * first thread to use the type system completes the initialisation.
 * Careful design of the run-time avoids re-entrant use by the
 * initialising thread.
 */
class TypeSystem {
    /*
     * Design intent: the creation of any Python type object or
     * Representation should only take place after (or in selected cases
     * during) the creation of the single TypeFactory instance by the
     * static initialisation of this class. that is static
     * initialisation of this class. The TypeSystem class must also
     * publish that instance to the kernel package, by means that avoid
     * it becoming module API.
     *
     * The class is in this package so that it can have package level
     * access to object implementations and the exposer, granting that
     * access to the TypeFactory through objects it passes when that is
     * created.
     *
     * Could this class safely be made public API? Possibly, but not the
     * factory or registry objects, which are in the kernel for a
     * reason. Even if it were safe to export them, putting that much
     * implementation detail into the API constrains future change.
     */

    private TypeSystem() {} // no instances and static members only

    /** Logger for (the public face of) the type system. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeSystem.class);

    /*
     * The static initialisation of this class brings the type system
     * into existence in the *only* way it should be allowed to happen.
     */

    /**
     * The type factory to which the run-time system goes for all type
     * objects.
     */
    static final TypeFactory factory;

    /**
     * The type registry to which this run-time system goes for all
     * class look-ups.
     */
    static final TypeRegistry registry;

    /** The type object of {@code type} objects. */
    static final PyType TYPE;

    /**
     * High-resolution time (the result of {@link System#nanoTime()}) at
     * which the type system began static initialisation. This is used
     * in tests.
     */
    static final long bootstrapNanoTime;

    /**
     * High-resolution time (the result of {@link System#nanoTime()}) at
     * which the type system completed static initialisation. This is
     * used in tests and to give the "ready" message a time.
     */
    static final long readyNanoTime;

    /**
     * A lookup with package scope. This lookup object is provided to
     * the type factory to grant it package-level access to the run-time
     * system.
     */
    private static final Lookup RUNTIME_LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    /*
     * The next block intends to make all the bootstrap types Java
     * ready, then Python ready, before any type object becomes visible
     * to another thread. For this it relies on the protection a JVM
     * gives to a class during static initialisation, on which the
     * well-known thread-safe lazy singleton pattern is based. This
     * class is the holder class for the entire type system.
     */
    static {
        // This should be the first thing the run-time system does.
        logger.info("Type system is waking up.");
        bootstrapNanoTime = System.nanoTime();

        // Bring the type machinery to life.
        TypeFactory f = Representation.factory;

        @SuppressWarnings("deprecation")
        SimpleType t = f.typeForType();

        try {
            /*
             * At this point, type objects 'type' and 'object' exist
             * "Java ready" forms, but they are not "Python ready". We
             * let them leak out so that the bootstrap process itself
             * may use them. No *other* thread can get to them until
             * this thread leaves the static initialisation of PyType.
             */
            TYPE = t;
            factory = f;
            registry = f.getRegistry();

            /*
             * Get all the bootstrap types Python ready. At this point,
             * the type factory needs access to the implementations of
             * types in this package, and to create exposers for them.
             */
            f.createBootstrapTypes(RUNTIME_LOOKUP,
                    TypeExposerImplementation::new);

        } catch (Clash clash) {
            // Maybe a bootstrap type was used prematurely?
            throw new InterpreterError(clash);
        }

        /*
         * We like to know how long this took. It is also used in
         * BootstrapTest to verify that the initialisation thread
         * finished before any other could access the type system.
         */
        readyNanoTime = System.nanoTime();

        logger.atInfo()
                .setMessage("Type system is ready after {} seconds")
                .addArgument(() -> String.format("%.3f",
                        1e-9 * (readyNanoTime - bootstrapNanoTime)))
                .log();
    }

    /**
     * Weak test that the type system has completed its bootstrap. This
     * does not guarantee that type objects, outside the bootstrap set,
     * are safe to use. A thread that has triggered type system creation
     * can use this as a check that it has finished (and certain
     * operations are valid). Any other thread calling this method will
     * either cause the type system bootstrap or wait for it to
     * complete.
     *
     * @return type {@code true} iff system is ready for use.
     */
    static boolean systemReady() { return readyNanoTime != 0L; }

    /**
     * Determine (or create if necessary) the {@link Representation} for
     * the given object. The representation is found (in the type
     * registry) from the Java class of the argument.
     *
     * @param o for which a {@code Representation} is required
     * @return the {@code Representation}
     */
    static Representation getRepresentation(Object o) {
        return registry.get(o.getClass());
    }

    /**
     * Determine (or create if necessary) the Python type for the given
     * object.
     *
     * @param o for which a type is required
     * @return the type
     */
    static BaseType of(Object o) {
        return registry.get(o.getClass()).pythonType(o);
    }

    /**
     * Create a Python type according to the specification. This is the
     * normal way to create any Python type that is defined in Java: the
     * Python built-ins or user-defined types. The minimal idiom
     * is:<pre>
     * class MyType {
     *     static PyType TYPE = PyType.fromSpec(
     *         new TypeSpec("mypackage.mytype",
     *                      MethodHandles.lookup());
     * }
     * </pre> The type system will add descriptors for the members of
     * the class identified by annotation or reserved names for exposure
     * to Python.
     *
     * @param spec specifying the new type
     * @return the new type
     */
    static BaseType fromSpec(TypeSpec spec) {
        try {
            return factory.fromSpec(spec);
        } catch (Clash clash) {
            logger.atError().log(clash.toString());
            throw new InterpreterError(clash);
        }
    }
}
