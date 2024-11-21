// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.AbstractPyType;
import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.MROCalculator;
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
 * {@code PyType} also offers type object lookup and creation methods,
 * for example {@link PyType#fromSpec(TypeSpec)}. For this purpose it
 * holds the single static instance of the Python type factory, which
 * comes into being upon first use of the {@code PyType} class.
 */
public abstract sealed class PyType extends AbstractPyType
        permits SimpleType, ReplaceableType, AdoptiveType {

    /** Logger for (the public face of) the type system. */
    static final Logger logger = LoggerFactory.getLogger(PyType.class);

    /*
     * The static initialisation of this class brings the type system
     * into existence in the *only* way it should be allowed to happen.
     */

    /**
     * The type factory to which the run-time system goes for all type
     * objects.
     */
    protected static final TypeFactory factory;

    /** The type object of {@code type} objects. */
    // Needed in its proper place before creating bootstrap types
    public static final PyType TYPE;

    /**
     * The type registry to which this run-time system goes for all
     * class look-ups.
     */
    protected static final TypeRegistry registry;

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
    protected static Lookup RUNTIME_LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

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

        /*
         * Kick the whole type machine into life. We go via variables f
         * and t so that we can suppress the deprecation messages, which
         * are for the discouragement of others, not because PyType
         * should use some alternative method.
         */
        @SuppressWarnings("deprecation")
        TypeFactory f = new TypeFactory(RUNTIME_LOOKUP,
                TypeExposerImplementation::new);
        @SuppressWarnings("deprecation")
        PyType t = f.typeForType();

        try {
            /*
             * At this point, 'type' and 'object' exist in their
             * "Java ready" forms, but they are not "Python ready". We
             * let them leak out so that the bootstrap process itself
             * may use them. No *other* thread can get to them until
             * this thread leaves the static initialisation of PyType.
             */
            TYPE = t;
            factory = f;
            registry = f.getRegistry();

            /*
             * Get all the bootstrap types ready for Python. Bootstrap
             * type implementations are not visible as public API
             * because it would be possible for another thread to touch
             * one during the bootstrap and that would block this
             * thread.
             */
            f.createBootstrapTypes();

        } catch (Clash clash) {
            // Maybe a bootstrap type was used prematurely?
            throw new InterpreterError(clash);
        }

        /*
         * We like to know how long this took. Also used in
         * BootstrapTest to verify there is just one bootstrap thread.
         */
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
        // XXX Ought to call "mro" after dictionary filled.
        this.mro = mro();
    }

    /**
     * Calculate a new MRO for this type by the default algorithm. This
     * method is exposed as the method {@code mro} of type
     * {@code objects} and may be overridden in a Python subclass of
     * {@code type} (a "metatype") to customise the MRO in the types it
     * creates.
     *
     * @return a new MRO for the type
     */
    // @Exposed.Method
    final PyType[] mro() {
        return MROCalculator.getMRO(this, this.bases);
    }

    @Override
    public String toString() { return "<class '" + getName() + "'>"; }

    /**
     * Determine (or create if necessary) the {@link Representation} for
     * the given object.
     *
     * @param o for which a {@code Representation} is required
     * @return the {@code Representation}
     */
    // ??? Check we still use this in the long run.
    static Representation representationOf(Object o) {
        Representation rep = registry.get(o.getClass());
        return rep.unshared(o);
    }

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
            logger.atError().log(clash.toString());
            throw new InterpreterError(clash);
        }
    }

    /**
     * {@code true} iff the type of {@code o} is a Python sub-type of
     * {@code this} (including exactly {@code this} type). This is
     * likely to be used in the form:<pre>
     * if(!PyUnicode.TYPE.check(oName)) throw ...
     * </pre>
     *
     * @param o object to test
     * @return {@code true} iff {@code o} is of a sub-type of this type
     */
    boolean check(Object o) {
        PyType t = PyType.of(o);
        return t == this || t.isSubTypeOf(this);
    }

    /**
     * {@code true} iff the Python type of {@code o} is exactly
     * {@code this}, not a Python sub-type of {@code this}, nor just any
     * Java sub-class of {@code PyType}. This is likely to be used in
     * the form:<pre>
     * if(!PyUnicode.TYPE.checkExact(oName)) throw ...
     * </pre>
     *
     * @param o object to test
     * @return {@code true} iff {@code o} is exactly of this type
     */
    public boolean checkExact(Object o) {
        return PyType.of(o) == this;
    }

    /**
     * Determine if this type is a Python sub-type of {@code b} (if
     * {@code b} is on the MRO of this type).
     *
     * @param b to test
     * @return {@code true} if {@code this} is a sub-type of {@code b}
     */
    // Compare CPython PyType_IsSubtype in typeobject.c
    public <T extends AbstractPyType> boolean isSubTypeOf(T b) {
        if (mro != null) {
            /*
             * Deal with multiple inheritance without recursion by
             * walking the MRO tuple
             */
            for (PyType base : mro) {
                if (base == b)
                    return true;
            }
            return false;
        } else
            // a is not completely initialised yet; follow base
            return type_is_subtype_base_chain(b);
    }

    /**
     * Determine if this type is a Python sub-type of {@code b} by
     * chaining through the {@link #base} property. (This is a fall-back
     * when {@link #mro} is not valid.)
     *
     * @param b to test
     * @return {@code true} if {@code this} is a sub-type of {@code b}
     */
    // Compare CPython type_is_subtype_base_chain in typeobject.c
    private boolean type_is_subtype_base_chain(AbstractPyType b) {
        PyType t = this;
        while (t != b) {
            t = t.base;
            if (t == null) { return b == PyObject.TYPE; }
        }
        return true;
    }

    // Special methods -----------------------------------------------

    /*
     * For technical reasons to do with bootstrapping the type system,
     * the methods and attributes of 'type' that are exposed to Python
     * have to be defined in the superclass.
     */

    // plumbing ------------------------------------------------------

    // Compare CPython _PyType_GetDocFromInternalDoc
    // XXX Consider implementing in ArgParser instead
    static Object getDocFromInternalDoc(String name, String doc) {
        // TODO Auto-generated method stub
        return Py.None;
    }

    // Compare CPython: PyType_GetTextSignatureFromInternalDoc
    // XXX Consider implementing in ArgParser instead
    static Object getTextSignatureFromInternalDoc(String name,
            String doc) {
        // TODO Auto-generated method stub
        return Py.None;
    }

}
