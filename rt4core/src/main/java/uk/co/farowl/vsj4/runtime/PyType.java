// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyBaseObject;
import uk.co.farowl.vsj4.runtime.kernel.AbstractPyType;
import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.ReplaceableType;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SimpleType;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory.Clash;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Each Python {@code type} object is implemented by an <i>instance</i>
 * in Java of {@code PyType}. As {@code PyType} is {@code abstract},
 * that means each is an instance of a subclass of {@code PyType}. Only
 * {@code PyType} is public API. The built-ins {@code object},
 * {@code type}, {@code str}, {@code int}, etc. and any class defined in
 * Python, are instances in Java of a subclass of {@code PyType}.
 * <p>
 * A particular Java subclass of {@code PyType}, implementing
 * {@link ExtensionPoint}, is used to represent those types that are
 * instances of Python subclasses of {@code type} (known as
 * <i>metaclasses</i>). Given the program text: <pre>
 * class Meta(type): pass
 * class MyClass(metaclass=Meta): pass
 * mc = MyClass()
 * </pre>
 * <p>
 * The following will be the implementation types of the objects
 * defined:
 * <ul>
 * <li>{@code Meta} will be an instance in Java of
 * {@link ReplaceableType}, a subclass of {@code PyType} that is not an
 * extension point class.</li>
 * <li>{@code MyClass} will be an instance in Java of
 * {@link AbstractPyType.Derived}, a subclass of {@code PyType}
 * implementing {@link ExtensionPoint}.</li>
 * <li>{@code mc} will be an instance in Java of
 * {@link AbstractPyBaseObject}, a subclass of {@code Object}
 * implementing {@link ExtensionPoint}.</li>
 * </ul>
 * <p>
 * {@code PyType} also offers type object lookup and creation methods,
 * for example {@link PyType#fromSpec(TypeSpec)}. For this purpose it
 * holds the single static instance of the Python type factory, which
 * comes into being upon first use of the {@code PyType} class.
 */
public abstract sealed class PyType extends AbstractPyType
        permits SimpleType, ReplaceableType, AdoptiveType,
        AbstractPyType.Derived {

    /** Logger for (the public face of) the type system. */
    static final Logger logger = LoggerFactory.getLogger(PyType.class);

    /*
     * The static initialisation of this class brings the type system
     * into existence in the *only* way it should be allowed to happen.
     */

    /**
     * The type factory to which the run-time system goes for all type
     * objects. This is used in tests and to give the "ready" message a
     * time.
     */
    static final TypeFactory factory;

    /** The type object of {@code type} objects. */
    // Needed in its proper place before creating bootstrap types
    public static final PyType TYPE;

    /**
     * The type registry to which this run-time system goes for all
     * class look-ups.
     */
    static final TypeRegistry registry;

    /**
     * High-resolution time (the result of {@link System#nanoTime()}) at
     * which the type system began static initialisation. This is used
     * in tests.
     */
    static final long bootstrapNanoTime;
    /**
     * High-resolution time (the result of {@link System#nanoTime()}) at
     * which the type system completed static initialisation. This is
     * used in tests.
     */
    static final long readyNanoTime;

    /*
     * The next block intends to make all the bootstrap types Java
     * ready, then Python ready, before any type object becomes visible
     * to another thread. For this it relies on the protection a JVM
     * gives to a class during static initialisation, on which the
     * well-known thread-safe lazy singleton pattern is based. PyType is
     * the holder class for the entire type system.
     */
    static {
        logger.info("Type system is waking up.");
        bootstrapNanoTime = System.nanoTime();

        try {
            /*
             * Kick the whole type machine into life. Deprecation
             * messages associated with the TypeFactory are for the
             * discouragement of others, not because there's any
             * alternative.
             */
            @SuppressWarnings("deprecation")
            TypeFactory f = new TypeFactory();

            /*
             * At this point, 'type' and 'object' exist in their
             * "Java ready" forms, but they are not "Python ready", and
             * nothing much else exists. We let them leak out but only
             * to this thread for now, as no other can touch PyType yet.
             */
            @SuppressWarnings("deprecation")
            PyType t = f.typeForType();
            TYPE = t;

            /*
             * Get all the bootstrap types ready for Python. Note that
             * the Java classes of bootstrap types are not visible as
             * public API because it would be possible for another
             * thread to touch one during the bootstrap. That would
             * block this thread.
             */
            f.createBootstrapTypes();

            /*
             * After the bootstrap, it is now safe to publish. When this
             * thread leaves the static initialisation of PyType,
             * threads previously blocked on a call become runnable.
             */
            factory = f;
            registry = f.getRegistry();
        } catch (Clash clash) {
            // Maybe a bootstrap type was used prematurely?
            throw new InterpreterError(clash);
        }

        readyNanoTime = System.nanoTime();

        logger.atInfo()
                .setMessage("Type system is ready after {} seconds")
                .addArgument(() -> String.format("%.3f",
                        1e-9 * (readyNanoTime - bootstrapNanoTime)))
                .log();
    }

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaType implementing Python instances of the type
     * @param bases of the new type
     */
    protected PyType(String name, Class<?> javaType, PyType[] bases) {
        super(name, javaType, bases);
    }

    @Override
    public String toString() { return "<class '" + getName() + "'>"; }

    /**
     * Determine (or create if necessary) the Python type for the given
     * object.
     *
     * @param o for which a type is required
     * @return the type
     */
    public static PyType of(Object o) {
        Representation rep = registry.get(o.getClass());
        return rep.pythonType(o);
    }

    /**
     * Determine the Python type for the given Java class. We use this
     * in the publication of certain built-in types.
     *
     * @param klass for which a type is required
     * @return the type
     */
    static PyType forClass(Class<?> klass) {
        Representation rep = registry.get(klass);
        if (rep instanceof PyType t) { return t; }
        throw new InterpreterError(
                "Class<%s> does not represent a fixed Python type.",
                klass.getTypeName());
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
    public static PyType fromSpec(TypeSpec spec) {
        try {
            return factory.fromSpec(spec);
        } catch (Clash clash) {
            throw new InterpreterError(clash);
        }
    }
}
