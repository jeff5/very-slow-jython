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
        SimpleType t = Representation.factory.typeForType();

        try {
            /*
             * At this point, type objects 'type' and 'object' exist
             * "Java ready" forms, but they are not "Python ready". We
             * let them leak out so that the bootstrap process itself
             * may use them. No *other* thread can get to them until
             * this thread leaves the static initialisation of
             * TypeSystem.
             */
            TYPE = t;
            factory = f;
            registry = f.getRegistry();

            /*
             * Create partial types for all the bootstrap types. These
             * are not Python ready. All the calls to f.fromSpec() count
             * as "re-entrant", so the type objects they create are not
             * published, and will not be visible to any other thread
             * yet. The process of making them Python ready is deferred,
             * and they sit in the workshop until we have defined the
             * full set.
             */

            /*
             * Many types can use the idiom 'static TYPE =
             * PyType.fromSpec(...)' in the static initialisation of the
             * defining class. This is not safe for types needed in the
             * construction of type objects themselves. The reason is
             * that a competing thread could seize control of the static
             * initialisation of such a class, and would block with the
             * necessary class half-initilised. Instead, each defining
             * class has a nested class, not visible in the API, that
             * provides the specification, even if some other thread has
             * locked the defining class of that type.
             */

            /*
             * The first types needing this consideration are the
             * descriptors.
             */
            f.fromSpec(PyGetSetDescr.Spec.get());
            f.fromSpec(PyJavaFunction.Spec.get());
            f.fromSpec(PyMemberDescr.Spec.get());
            f.fromSpec(PyMethodDescr.Spec.get());
            f.fromSpec(PyMethodWrapper.Spec.get());
            f.fromSpec(PyWrapperDescr.Spec.get());

            /*
             * The second group of types is those with adopted
             * representation Java classes. A client that, for example,
             * calls PyType.of(1) will otherwise accidentally create a
             * "discovered" type for Integer.
             */

            f.fromSpec(PyUnicode.Spec.get());
            f.fromSpec(PyFloat.Spec.get());

            /*
             * We create 'int' before 'bool' and keep the type object,
             * so that we may hand it to the definition of 'bool'.
             * This thread cannot reliably reference PyLong.TYPE.
             */
            final PyType INT = f.fromSpec(PyLong.Spec.get());
            f.fromSpec(PyBool.Spec.get(INT));

            /*
             * At this point, the type factory needs access to the
             * implementations of types in this package, and to create
             * exposers for them.
             */
            f.publishBootstrapTypes(RUNTIME_LOOKUP,
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
     * A specification of a bootstrap a type object. We do this as a
     * shorthand for use in the specification of the bootstrap types
     * (e.g. {@link PyFloat.Spec#get()}, as we find the same mutators
     * have to be added.
     */
    static class BootstrapSpec extends TypeSpec {
        /**
         * Create a specification using name and primary class. The
         * lookup object used to expose methods will be a generic one
         * supplied by the run-time package, that does not have access
         * to {@code private} members of the {@code primary} class. For
         * this reason, the special and other exposed methods of a
         * bootstrap type must be visible at package scope in the
         * run-time package.
         *
         * @param name of type
         * @param lookup to access the implementation classes
         * @param primary used to represent and define methods
         */
        BootstrapSpec(String name, Lookup lookup, Class<?> primary) {
            super(name, lookup, false);
            this.primary(primary).add(Feature.IMMUTABLE);
        }
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
    static BaseType typeOf(Object o) {
        return registry.get(o.getClass()).pythonType(o);
    }

    /**
     * Determine the Python type for the given class, or fail if it the
     * class is a representation of more than one type.
     *
     * @param klass for which a type is required
     * @return the corresponding unique type
     * @throws InterpreterError when not unique
     */
    static BaseType typeForClass(Class<?> klass)
            throws InterpreterError {
        Representation rep = registry.get(klass);
        if (rep instanceof BaseType bt) {
            return bt;
        } else {
            throw new InterpreterError(
                    "Python type of %s was expected to be unique",
                    klass.getSimpleName());
        }
    }

    /**
     * Create a Python type according to the specification. This exists
     * to back {@link PyType#fromSpec(TypeSpec)}. Within the run-time
     * system package, we might call:<pre>
     * class MyType {
     *     BaseType bt = TypeSystem.typeFromSpec(
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
    static BaseType typeFromSpec(TypeSpec spec) {
        try {
            return factory.fromSpec(spec);
        } catch (Clash clash) {
            logger.atError().log(clash.toString());
            throw new InterpreterError(clash);
        }
    }
}
