// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.O;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.Crafted;
import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry.Clash;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A {@code Representation} provides Python behaviour to a Java object
 * by linking its Java class to essential type information. In many
 * cases, the Java class alone determines the behaviour (the Python
 * type). In cases where instances of the the Java class can represent
 * objects with multiple Python types,
 * {@link Representation#pythonType(Object)} refers to the Python object
 * itself for a the actual type.
 * <p>
 * The run-time system will form a mapping from each Java class to an
 * instance of (a specific sub-class of) {@code Representation}. Apart
 * from a small collection of bootstrap classes (all of them built-in
 * types), this mapping will be developed as the classes are encountered
 * through the use of instances of them in Python code.
 */
public abstract class Representation {

    /**
     * The common type (class or interface) of Java classes representing
     * instances of the related Python {@code type}, and registered for
     * this {@code Representation}. If more than one Java class is
     * registered to this representation, then in Java they must all
     * inherit (extend or implement) the type recorded here.
     * <p>
     * Classes representing instances of the related Python {@code type}
     * and <i>not</i> related by inheritance must be described in
     * separate {@code Representation}s.
     */
    protected final Class<?> javaType;

    /**
     * Create a {@code Representation} relating a (base) Java class to a
     * type. Creation of a {@code Representation} does not register the
     * association.
     *
     * @param javaType the base of classes represented
     */
    protected Representation(Class<?> javaType) {
        this.javaType = javaType;
    }

    /**
     * Mapping from Java class to the {@code Representation} that
     * provides instances of the class with Python semantics.
     */
    static final TypeRegistry registry = TypeRegistry.getInstance();

    /**
     * Register the {@link Representation} for a Java class. Subsequent
     * enquiries through {@link #of(Object)} and
     * {@link #fromClass(Class)} will yield this {@code Representation}
     * object. This is a one-time action on the JVM-wide registry,
     * affecting the state of the {@code Class} object: the association
     * cannot be changed, but the {@code Representation} may be mutated
     * (where it allows that). It is an error to attempt to associate
     * different {@code Representation} with a class already bound.
     *
     * @param c class with which associated
     * @param rep the representation object
     * @throws Clash when the class is already mapped
     */
    static void register(Class<?> c, Representation rep) throws Clash {
        Representation.registry.set(c, rep);
    }

    /**
     * Register the {@link Representation}s for multiple Java classes,
     * as with {@link #register(Class, Representation)}. All succeed or
     * fail together.
     *
     * @param c classes with which associated
     * @param reps the representation objects
     * @throws Clash when one of the classes is already mapped
     */
    static void register(Class<?>[] c, Representation reps[])
            throws Clash {
        Representation.registry.set(c, reps);
    }

    /**
     * Map a Java class to the {@code Representation} object that
     * provides Python semantics to instances of the class.
     *
     * @param c class for which representation is required
     * @return {@code Representation} providing Python semantics
     */
    static Representation fromClass(Class<?> c) {
        // Normally, this is completely straightforward
        // TODO deal with re-entrancy and concurrency
        return registry.get(c);
    }

    /**
     * Map an object to the {@code Representation} object that provides
     * it with Python semantics.
     *
     * @param obj for which representation is required
     * @return {@code Representation} providing Python semantics
     */
    public static Representation of(Object obj) {
        return fromClass(obj.getClass());
    }

    /**
     * Get the Python type of the object <i>given that</i> this is the
     * representation object for it.
     *
     * @param x subject of the enquiry
     * @return {@code type(x)}
     */
    public abstract PyType pythonType(Object x);

    /**
     * Identify by index which Java implementation of the associated
     * type this {@code Representation} object is for. (Some types have
     * multiple acceptable implementations.)
     *
     * @return index in the type (0 if canonical)
     */
    int getIndex() { return 0; }

    /**
     * A base Java class representing instances of the related Python
     * {@code type} associated with this {@code Representation}. If
     * there is more than one Java class <i>associated to this
     * representation</i>, they must all be subclasses in Java of the
     * class returned here.
     *
     * @return base class of the implementation
     */
    public Class<?> javaType() { return javaType; }

    /**
     * Fast check that the target is exactly a Python {@code int}. We
     * can do this without reference to the object itself, since it is
     * deducible from the Java class.
     *
     * @return target is exactly a Python {@code int}
     */
    // boolean isIntExact() { return this == PyLong.TYPE; }

    /**
     * Fast check that the target is exactly a Python {@code float}. We
     * can do this without reference to the object itself, since it is
     * deducible from the Java class.
     *
     * @return target is exactly a Python {@code float}
     */
    // boolean isFloatExact() { return this == PyFloat.TYPE; }

    /**
     * Fast check that the target is a data descriptor.
     *
     * @return target is a data descriptor
     */
    boolean isDataDescr() { return false; }

    /**
     * Fast check that the target is a method descriptor.
     *
     * @return target is a method descriptor
     */
    boolean isMethodDescr() { return false; }

    // ---------------------------------------------------------------

    /**
     * Representation for an accepted implementation (non-canonical
     * implementation) are represented by an instance of this class. The
     * representation of a canonical implementation is represented by
     * the {@link PyType} itself.
     */
    static class Adopted extends Representation {

        /** The type of which this is an accepted implementation. */
        final private AdoptiveType type;

        /**
         * Index of this implementation in the type (see
         * {@link PyType#indexAccepted(Class)}.
         */
        final private int index;

        /**
         * Create a {@code Representation} object associating a Python
         * type with the Java type.
         *
         * @param javaType implementing it
         * @param type of which this is an accepted implementation
         */
        Adopted(Class<?> javaType, AdoptiveType type) {
            super(javaType);
            this.type = type;
            this.index = 0; // XXX an argument? Instead of javaType??
            // setAllSlots();
        }

        @Override
        public AdoptiveType pythonType(Object x) { return type; }

        @Override
        public String toString() {
            String javaName = javaType().getSimpleName();
            return javaName + " as " + type.toString();
        }
    }

    /**
     * The {@link Representation} for a Python class defined in Python.
     * Many Python classes may be implemented by the same Java class,
     * the actual type being indicated by the instance.
     */
    static class Shared extends Representation {

        /**
         * {@code MethodHandle} of type {@code (ExtensionPoint)PyType},
         * to get the actual Python type of an {@link ExtensionPoint}
         * object.
         */
        private static final MethodHandle getType;

        /**
         * The type {@code (PyType)MethodHandle} used to cast the method
         * handle getter.
         */
        private static final MethodType MT_MH_FROM_TYPE;

        /** Rights to form method handles. */
        private static final Lookup LOOKUP = MethodHandles.lookup();

        static {
            try {
                // Used as a cast in the formation of getMHfromType
                // (PyType)MethodHandle
                MT_MH_FROM_TYPE =
                        MethodType.methodType(MethodHandle.class, T);
                // Used as a cast in the formation of getType
                // (PyType)MethodHandle
                // getType = λ x : x.getType()
                // .type() = (Object)PyType
                getType = LOOKUP
                        .findVirtual(Crafted.class, "getType",
                                MethodType.methodType(T))
                        .asType(MethodType.methodType(T, O));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InterpreterError(e,
                        "preparing handles in Representation.Shared");
            }
        }

        /**
         * Create a {@code Representation} object that is the
         * implementation of potentially many types defined in Python.
         */
        Shared(Class<? extends ExtensionPoint> javaType) {
            super(javaType);
            // setAllSlots();
        }

        @Override
        public PyType pythonType(Object x) {
            if (x instanceof ExtensionPoint ex)
                return ex.getType();
            else {
                String msg = String.format(
                        "class %.100s registered as ExtensionPoint",
                        x.getClass().getTypeName());
                throw new InterpreterError(msg);
            }
        }
    }

}
