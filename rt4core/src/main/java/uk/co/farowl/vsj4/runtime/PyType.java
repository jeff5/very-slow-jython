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
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.AdoptiveType;
import uk.co.farowl.vsj4.runtime.kernel.BaseType;
import uk.co.farowl.vsj4.runtime.kernel.KernelTypeFlag;
import uk.co.farowl.vsj4.runtime.kernel.MROCalculator;
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
public abstract sealed class PyType extends Representation
        implements WithClass, FastCall permits BaseType {

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

    /** Name of the type (fully-qualified). */
    final String name;

    /**
     * Feature flags collecting various boolean traits of this type,
     * such as immutability or being a subclass of {@code int}. Some of
     * these come fairly directly from the {@link TypeSpec} (where used
     * to define the type) and others are observed during construction
     * of the type.
     */
    // Compare CPython tp_flags in object.h
    protected final EnumSet<TypeFlag> features =
            EnumSet.noneOf(TypeFlag.class);

    /**
     * Kernel feature flags collecting various traits of this type that
     * are private to the implementation, such as defining a certain
     * special method.
     */
    // Compare CPython tp_flags in object.h
    protected final EnumSet<KernelTypeFlag> kernelFeatures =
            EnumSet.noneOf(KernelTypeFlag.class);

    /**
     * The {@code __base__} of this type. The {@code __base__} is a type
     * from the {@code __bases__}, but its choice is determined by
     * implementation details.
     * <p>
     * It is the type earliest on the MRO after the current type, whose
     * implementation contains all the members necessary to implement
     * the current type.
     */
    protected BaseType base;

    /**
     * The {@code __bases__} of this type, which are the types named in
     * heading of the Python {@code class} definition, or just
     * {@code object} if none are named, or an empty array in the
     * special case of {@code object} itself.
     */
    protected BaseType[] bases;

    /**
     * The {@code __mro__} of this type, that is, the method resolution
     * order, as defined for Python and constructed by the {@code mro()}
     * method (which may be overridden), by analysis of the
     * {@code __bases__}.
     */
    protected BaseType[] mro;

    /**
     * The writable dictionary of the type is private because the type
     * controls writing strictly. Even in the core it is only accessible
     * through a read-only view {@link #dict}.
     */
    private final LinkedHashMap<String, Object> _dict;

    /**
     * The dictionary of the type is always an ordered {@code Map}. It
     * is made accessible here through a wrapper that renders it a
     * read-only {@code dict}-like object. Internally names are stored
     * as {@code String} for speed and accessed via
     * {@link #lookup(String)}.
     */
    protected final Map<String, Object> dict;

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     * @param _dict private dictionary backing {@code __dict__}
     */
    protected PyType(String name, Class<?> javaClass, BaseType[] bases,
            LinkedHashMap<String, Object> _dict) {
        super(javaClass);
        /*
         * These assertions mainly check our assumptions about the needs
         * of sub-types. They are retained only in testing.
         */
        assert name != null;
        assert javaClass != null || this instanceof AdoptiveType;
        assert bases != null;

        this.name = name;
        this.bases = bases;
        this.base = bases.length > 0 ? bases[0] : null;

        // Keep a private copy to support __setattr__ etc..
        this._dict = _dict;
        // FIXME: define mappingproxy type for this use
        this.dict = Collections.unmodifiableMap(_dict);
    }

    /**
     * Return the name of the type.
     *
     * @return the name of the type
     */
    public String getName() { return name; }

    /**
     * A copy of the sequence of bases specified for the type,
     * essentially {@code __bases__}.
     *
     * @return the sequence of bases
     */
    public PyType[] getBases() { return bases.clone(); }

    /**
     * Return the (best) base type of this type, essentially
     * {@code __base__}. In the case of single inheritance, the choice
     * is obvious. In multiple inheritance, Python makes a somewhat
     * subtle choice. requires a particular care.
     *
     * @return the "best base" type of this type
     */
    public PyType getBase() { return base; }

    /**
     * Return a copy of the MRO of this type.
     *
     * @return a copy of the MRO of this type
     */
    public abstract PyType[] getMRO();

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
    protected PyType[] mro() {
        // TODO: detect and call custom __mro__ method
        return MROCalculator.getMRO(this, this.bases);
    }

    @Override
    public String toString() { return "<class '" + getName() + "'>"; }

    @Override
    public PyType getType() { return PyType.TYPE; }

    /**
     * An immutable list of the {@link Representation}s of this type.
     * These are the representations of the primary or adopted classes
     * in the specification of this type, in order.
     *
     * @return the representations of {@code self}
     */
    public abstract List<Representation> representations();

    /**
     * An immutable list of every Java class that was named as primary,
     * adopted or accepted in the specification of this type, in order.
     * These are the base Java classes of objects that can legitimately
     * be presented as {@code self} to methods of the type.
     * <p>
     * These are also the classes of the {@link #representations()}, in
     * order, except that the non-representation classes also accepted
     * as {@code self} (if any) are appended. A method descriptor in an
     * adoptive type uses this list to ensure it has an implementation
     * for each self class.
     *
     * @return the bases of classes allowed as {@code self}
     */
    public abstract List<Class<?>> selfClasses();

    /**
     * A particular subclass (in Java) of the primary representation
     * class that is to be used as the base of representations of
     * subclasses in Python. That is, the canonical class is a subclass
     * of {@link #selfClasses()}{@code [0]}.
     * <p>
     * In many cases, the canonical class is exactly the primary (and
     * only) representation class, but it is not safe to assume so
     * always. For {@code type} itself, the canonical class is called
     * {@code SimpleType}, and for subclasses defined in Python it may
     * be the canonical representation of one of an ancestor class.
     *
     * @return the canonical Java representation class of {@code self}
     */
    public abstract Class<?> canonicalClass();

    /**
     * Look for a name, returning the entry directly from the first
     * dictionary along the MRO containing key {@code name}. This may be
     * a descriptor, but no {@code __get__} takes place on it: the
     * descriptor itself will be returned. This method does not throw an
     * exception if the name is not found, but returns {@code null} like
     * a {@code Map.get}
     *
     * @param name to look up, must be exactly a {@code str}
     * @return dictionary entry or {@code null} if not found
     */
    // Compare CPython _PyType_Lookup in typeobject.c
    // and find_name_in_mro in typeobject.c
    public abstract Object lookup(String name);

    /**
     * Called from {@code type.__setattr__} and
     * {@code type.__delattr__(String)} after an attribute has been set
     * or deleted. This gives the type the opportunity to recompute
     * caches and perform any other actions needed.
     *
     * @param name of the attribute modified
     */
    protected abstract void updateAfterSetAttr(String name);

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
    // FIXME: to be less public or in BaseType
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

    // C-API Equivalents ---------------------------------------------
    /*
     * Java API that is roughly equivalent to the C-API as might be used
     * in the creation of extension types, Python modules in Java, or
     * applications that embed Python requiring more than an
     * encapsulated interpreter.
     */

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
    public boolean check(Object o) {
        PyType t = PyType.of(o);
        return t == this || t.isSubTypeOf(this);
    }

    /**
     * {@code true} iff the Python type of {@code o} is exactly
     * {@code this} type. This is likely to be used in the form:<pre>
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
     * Test for possession of a specified feature.
     *
     * @param feature to check for
     * @return whether present
     */
    public final boolean hasFeature(TypeFlag feature) {
        return features.contains(feature);
    }

    /**
     * Test for possession of a specified kernel feature. Kernel
     * features are not public API.
     *
     * @param feature to check for
     * @return whether present
     */
    final boolean hasFeature(KernelTypeFlag feature) {
        return kernelFeatures.contains(feature);
    }

    @Override
    protected boolean hasFeature(Object x, KernelTypeFlag feature) {
        return kernelFeatures.contains(feature);
    }

    /**
     * Return true if and only if this is a mutable type. The attributes
     * of a mutable type may be changed, although it will manage that
     * change according to rules of its own. An immutable type object
     * does not allow attribute assignment: the value of an attribute
     * once observed remains valid for the lifetime of the run time
     * system.
     *
     * @return {@code true} iff this is a mutable type
     */
    public abstract boolean isMutable();

    /**
     * Fast check that an object of this type is a sequence, defined as
     * not a subclass of {@code dict} and defining {@code __getitem__}.
     *
     * @return target is a sequence
     */
    // Compare CPython PySequence_Check (on instance) in abstract.c
    public boolean isSequence() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_GETITEM)
                && !features.contains(TypeFlag.DICT_SUBCLASS);
    }

    /**
     * Fast check that an object of this type is iterable (defines
     * {@code __iter__}).
     *
     * @return target is a iterable with {@code __iter__}
     */
    public boolean isIterable() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_ITER);
    }

    /**
     * Fast check that an object of this type is an iterator (defines
     * {@code __next__}).
     *
     * @return target is a an iterator
     */
    public boolean isIterator() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_NEXT);
    }

    /**
     * Fast check that an object of this type is a descriptor (defines
     * {@code __get__}).
     *
     * @return target is a descriptor
     */
    public boolean isDescr() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_GET);
    }

    /**
     * Fast check that an object of this type is a data descriptor
     * (defines {@code __set__} or {@code __delete__}).
     *
     * @return target is a data descriptor
     */
    public boolean isDataDescr() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_SET)
                || kernelFeatures.contains(KernelTypeFlag.HAS_DELETE);
    }

    /**
     * Fast check that instances of the type are a method descriptors,
     * meaning that they take a {@code self} argument that may be
     * supplied "loose" when calling them as a method. Types defined in
     * Java may declare this in their specification.
     * <p>
     * This method is equivalent to reading the flag
     * {@code Py_TPFLAGS_METHOD_DESCRIPTOR} described in <a
     * href=https://peps.python.org/pep-0590/#descriptor-behavior>
     * PEP-590</a>. If {@code isMethodDescr()} returns {@code true} for
     * {@code type(func)}, then:
     * <ul>
     * <li>{@code func.__get__(obj, cls)(*args, **kwds)} (with
     * {@code {@code obj}} not None) must be equivalent to
     * {@code func(obj, *args, **kwds)}.</li>
     * <li>{@code func.__get__(None, cls)(*args, **kwds)} must be
     * equivalent to {@code func(*args, **kwds)}.</li>
     * </ul>
     *
     * @return target is a method descriptor
     */
    public boolean isMethodDescr() {
        return features.contains(TypeFlag.METHOD_DESCR);
    }

    // unsigned long PyType_GetFlags(PyTypeObject *type)

    // int PyType_Ready(PyTypeObject *type)
    // Finalize a type object. This should be called on all type objects
    // to finish their initialization. This function is responsible for
    // adding inherited slots from a type’s base class. Return 0 on
    // success, or return -1 and sets an exception on error.

    // PyObject *PyType_GetName(PyTypeObject *type)
    // Return the type’s name. Equivalent to getting the type’s __name__
    // attribute.
    // New in version 3.11.

    // PyObject *PyType_GetQualName(PyTypeObject *type)
    // Return the type’s qualified name. Equivalent to getting the
    // type’s __qualname__ attribute.
    // New in version 3.11.

    // PyObject *PyType_GetModule(PyTypeObject *type)
    // Return the module object associated with the given type when the
    // type was created using PyType_FromModuleAndSpec().

    // void *PyType_GetModuleState(PyTypeObject *type)
    // Return the state of the module object associated with the given
    // type.

    // PyObject *PyType_GetModuleByDef(PyTypeObject *type, struct
    // PyModuleDef *def)
    //
    // Find the first superclass whose module was created from the given
    // PyModuleDef def, and return that module.

    /**
     * Determine if this type is a Python sub-type of {@code b} (if
     * {@code b} is on the MRO of this type). For technical reasons we
     * parameterise with the subclass. (We need it to work with a
     * private superclass or {@code PyType}.)
     *
     * @param b to test
     * @return {@code true} if {@code this} is a sub-type of {@code b}
     */
    // Compare CPython PyType_IsSubtype in typeobject.c
    // CPython documentation:
    // int PyType_IsSubtype(PyTypeObject *a, PyTypeObject *b)
    // Return true if a is a subtype of b.
    //
    // This function only checks for actual subtypes, which means that
    // __subclasscheck__() is not called on b. Call
    // PyObject_IsSubclass() to do the same check that issubclass()
    // would do.
    public boolean isSubTypeOf(PyType b) {
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

    // Support for __new__ -------------------------------------------

    /**
     * The return from {@link #constructor()} holding a reflective
     * constructor definition and a handle by which it may be called.
     * <p>
     * A custom {@code __new__} method in a defining Java class of a
     * type generally has direct access to all the constructors it needs
     * for its own type. When asked for an instance of a different type,
     * it must be able to call the constructor of the Java
     * representation class. The representation of the required type
     * (the {@code cls} argument to {@code __new__}) will be a subclass
     * in Java of the canonical representation of the type from which
     * {@code __new__} was called.
     */
    public static record ConstructorAndHandle(
            Constructor<?> constructor, MethodHandle handle) {}

    /**
     * Return the table holding constructors and their method handles
     * for instances of this type. This enables client code to iterate
     * over available constructors without any copying. The table and
     * its contents are immutable.
     * <p>
     * Note that in the key, the Java class of the return type is
     * {@code Object}.
     *
     * @return the lookup for constructors and handles
     */
    public abstract Map<MethodType, ConstructorAndHandle>
            constructorLookup();

    /**
     * Return a constructor of instances of this type, and its method
     * handle, that accepts arguments matching the given types. The Java
     * class of the return type of the handle is {@code Object}, since
     * we cannot rely on the caller to know the specific class.
     *
     * @param param the intended argument types
     * @return a constructor and a handle on it
     */
    // Compare CPython type slot tp_alloc (but only loosely).
    public abstract ConstructorAndHandle constructor(Class<?>... param);

    // Special methods -----------------------------------------------

    /*
     * For technical reasons to do with bootstrapping the type system,
     * the methods and attributes of 'type' that are exposed to Python
     * have to be defined with at least package visibility.
     */

    /** @return {@code repr()} of this Python object. */
    Object __repr__() {
        return String.format("<class '%s'>", getName());
    }

    /**
     * Handle calls to a type object, which will normally be a request
     * to construct a Python object of the type this object describes.
     * For example the call {@code int()} is a request to create a
     * Python {@code int}, although we often think of it as a built-in
     * function. The exception is when {@code this} is {@code type}
     * itself. There must be one or three arguments. The call
     * {@code type(obj)} enquires the Python type of the object, which
     * is even more like a built-in function. The call
     * {@code type(name, bases, dict)} constructs a new type (instance
     * of {@code type}).
     *
     * @param args argument list (length 1 in a type enquiry).
     * @param names of keyword arguments (empty or {@code null} in a
     *     type enquiry).
     * @return new object (or a type if an enquiry).
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) when
     *     cannot create instances
     * @throws Throwable from implementation slot functions
     */
    Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            throw typeError(ae, args, names);
        }
    }

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
     * {@code object.__setattr__} except that it manages write access to
     * the type dictionary that is denied through
     * {@link WithDict#getDict()}.
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
        _dict.put(name, value);
        updateAfterSetAttr(name);
    }

    /**
     * {@link SpecialMethod#op_delattr} provides attribute deletion on
     * this type object. The behaviour is very like the default
     * {@code object.__delattr__} except that it manages write access to
     * the type dictionary that is denied through
     * {@link WithDict#getDict()}.
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
        Object previous = _dict.remove(name);
        if (previous == null) {
            // A null return implies it didn't exist
            throw noAttributeError(this, name);
        }
        updateAfterSetAttr(name);
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

    /**
     * Determine if this type is a Python sub-type of {@code b} by
     * chaining through the {@link #base} property. (This is a fall-back
     * when {@link #mro} is not valid.)
     *
     * @param b to test
     * @return {@code true} if {@code this} is a sub-type of {@code b}
     */
    // Compare CPython type_is_subtype_base_chain in typeobject.c
    private boolean type_is_subtype_base_chain(PyType b) {
        PyType t = this;
        while (t != b) {
            t = t.base;
            if (t == null) { return b == PyObject.TYPE; }
        }
        return true;
    }
}
