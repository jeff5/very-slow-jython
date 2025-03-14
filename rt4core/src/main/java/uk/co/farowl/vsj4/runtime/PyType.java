// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import static uk.co.farowl.vsj4.runtime.internal._PyUtil.cantSetAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.mandatoryAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.noAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.readonlyAttributeError;

import java.lang.invoke.MethodHandle;
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
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory.Clash;
import uk.co.farowl.vsj4.runtime.kernel.TypeRegistry;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

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
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     */
    protected PyType(String name, Class<?> javaClass, PyType[] bases) {
        super(name, javaClass, bases);
    }

    // @Exposed.Method
    @Override
    protected PyType[] mro() {
        return MROCalculator.getMRO(this, this.bases);
    }

    @Override
    public String toString() { return "<class '" + getName() + "'>"; }

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
     * Find the index in the self-classes of this type, of a Java class
     * that is assignment-compatible with that of the argument. This
     * method is used by descriptors when they are called with a
     * {@code self} argument that is not of the Python type that defined
     * the descriptor, but is found to be a sub-type of it.
     * <p>
     * In these circumstances, only the primary representation (index 0)
     * and accepted (not adopted) representation classes need be tested.
     * It returns 0 in all cases where there are no such accepted
     * representations, even if that choice is not assignment
     * compatible.
     *
     * @param selfClass to seek
     * @return index in {@link #selfClasses()}
     */
    public int getSubclassIndex(Class<?> selfClass) { return 0; }

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
     * {@code b} is on the MRO of this type). For technical reasons we
     * parameterise with the subclass. (We need it to work with a
     * private superclass or {@code PyType}.)
     *
     * @param <T> actual type of {@code b} normally a {@code PyType}.
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
     * have to be defined with package visibility.
     */

    /**
     * {@link SpecialMethod#op_getattribute} provides attribute read
     * access on this type object and its metatype. This is very like
     * {@code object.__getattribute__}, but the instance is replaced by
     * a type object, and that object's type is a meta-type (which is
     * also a {@code type}).
     * <p>
     * The behavioural difference is that in looking for attributes on a
     * type:
     * <ul>
     * <li>we use {@link #lookup(String)} to search along along the MRO,
     * and</li>
     * <li>if we find a descriptor, we use it.
     * ({@code object.__getattribute__} does not check for descriptors
     * on the instance.)</li>
     * </ul>
     * <p>
     * The following order of precedence applies when looking for the
     * value of an attribute:
     * <ol>
     * <li>a data descriptor from the dictionary of the meta-type</li>
     * <li>a descriptor or value in the dictionary of {@code type}</li>
     * <li>a non-data descriptor or value from dictionary of the meta
     * type</li>
     * </ol>
     *
     * @param name of the attribute
     * @return attribute value
     * @throws PyAttributeError if no such attribute
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_getattro in typeobject.c
    Object __getattribute__(String name)
            throws PyAttributeError, Throwable {

        PyType metatype = getType();
        MethodHandle descrGet = null;

        // Look up the name in the type of the type (null if not found).
        Object metaAttr = metatype.lookup(name);
        if (metaAttr != null) {
            // Found in the metatype, it might be a descriptor
            Representation metaAttrRep =
                    PyType.getRepresentation(metaAttr);
            descrGet = metaAttrRep.op_get();
            if (metaAttrRep.isDataDescr(metaAttr)) {
                // metaAttr is a data descriptor so call its __get__.
                try {
                    // Note the cast of 'this', to match op_get
                    return descrGet.invokeExact(metaAttr, (Object)this,
                            metatype);
                } catch (EmptyException e) {
                    /*
                     * Only __set__ or __delete__ was defined. We do not
                     * catch PyAttributeError: it's definitive. Suppress
                     * trying __get__ again.
                     */
                    descrGet = null;
                }
            }
        }

        /*
         * At this stage: metaAttr is the value from the meta-type, or a
         * non-data descriptor, or null if the attribute was not found.
         * It's time to give the type's instance dictionary a chance.
         */
        Object attr = lookup(name);
        if (attr != null) {
            // Found in this type. Try it as a descriptor.
            try {
                /*
                 * Note the args are (null, this): we respect
                 * descriptors in this step, but have not forgotten we
                 * are dereferencing a type.
                 */
                return PyType.getRepresentation(attr).op_get()
                        .invokeExact(attr, (Object)null, this);
            } catch (EmptyException e) {
                // Do not catch AttributeError: it's definitive.
                // Not a descriptor: the attribute itself.
                return attr;
            }
        }

        /*
         * The name wasn't in the type dictionary. metaAttr is now the
         * result of look-up on the meta-type: a value, a non-data
         * descriptor, or null if the attribute was not found.
         */
        if (descrGet != null) {
            // metaAttr may be a non-data descriptor: call __get__.
            try {
                return descrGet.invokeExact(metaAttr, (Object)this,
                        metatype);
            } catch (EmptyException e) {}
        }

        if (metaAttr != null) {
            /*
             * The attribute obtained from the meta-type, and that
             * turned out not to be a descriptor, is the return value.
             */
            return metaAttr;
        }

        // All the look-ups and descriptors came to nothing :(
        throw noAttributeError(this, name);
    }

    /**
     * {@link SpecialMethod#op_setattr} provides attribute write access
     * on this type object. The behaviour is very like the default
     * {@code object.__setattr__} except that it has write access to the
     * type dictionary that is denied through {@link #getDict()}.
     *
     * @param name of the attribute
     * @param value to give the attribute
     * @throws PyAttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    void __setattr__(String name, Object value)
            throws PyAttributeError, Throwable {

        // Accommodate CPython idiom that set null means delete.
        if (value == null) {
            // Do this to help porting. Really this is an error.
            __delattr__(name);
            return;
        }

        // Trap immutable types
        if (hasFeature(TypeFlag.IMMUTABLE))
            throw cantSetAttributeError(this);

        // Look up the name in the meta-type (null if not found).
        Object metaAttr = getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            Representation metaAttrRep = getRepresentation(metaAttr);
            if (metaAttrRep.isDataDescr(metaAttr)) {
                // Try descriptor __set__
                try {
                    metaAttrRep.op_set().invokeExact(metaAttr,
                            (Object)this, value);
                    updateAfterSetAttr(name);
                    return;
                } catch (EmptyException e) {
                    // Do not catch AttributeError: it's definitive.
                    // Descriptor but no __set__: do not fall through.
                    throw readonlyAttributeError(this, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will place the value in
         * the dictionary of the type directly.
         */
        dictPut(name, value);
    }

    /**
     * {@link SpecialMethod#op_delattr} provides attribute deletion on
     * this type object. The behaviour is very like the default
     * {@code object.__delattr__} except that it has write access to the
     * type dictionary that is denied through {@link #getDict()}.
     *
     * @param name of the attribute
     * @throws PyAttributeError if no such attribute or it is read-only
     * @throws Throwable on other errors, typically from the descriptor
     */
    // Compare CPython type_setattro in typeobject.c
    void __delattr__(String name) throws PyAttributeError, Throwable {

        // Trap immutable types
        if (hasFeature(TypeFlag.IMMUTABLE))
            throw cantSetAttributeError(this);

        // Look up the name in the meta-type (null if not found).
        Object metaAttr = getType().lookup(name);
        if (metaAttr != null) {
            // Found in the meta-type, it might be a descriptor.
            Representation metaAttrRep = getRepresentation(metaAttr);
            if (metaAttrRep.isDataDescr(metaAttr)) {
                // Try descriptor __delete__
                try {
                    metaAttrRep.op_delete().invokeExact(metaAttr,
                            (Object)this);
                    updateAfterSetAttr(name);
                    return;
                } catch (EmptyException e) {
                    // Do not catch AttributeError: it's definitive.
                    // Data descriptor but no __delete__.
                    throw mandatoryAttributeError(this, name);
                }
            }
        }

        /*
         * There was no data descriptor, so we will remove the name from
         * the dictionary of the type directly.
         */
        Object previous = dictRemove(name);
        if (previous == null) {
            // A null return implies it didn't exist
            throw noAttributeError(this, name);
        }
        return;
    }

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
