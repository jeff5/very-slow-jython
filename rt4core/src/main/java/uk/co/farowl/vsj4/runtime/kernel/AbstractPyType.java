// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.FastCall;
import uk.co.farowl.vsj4.runtime.Feature;
import uk.co.farowl.vsj4.runtime.MethodDescriptor;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyJavaFunction;
import uk.co.farowl.vsj4.runtime.PyList;
import uk.co.farowl.vsj4.runtime.PyLong;
import uk.co.farowl.vsj4.runtime.PyTuple;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUnicode;
import uk.co.farowl.vsj4.runtime.TypeFlag;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * {@code AbstractPyType} is the Java base of Python {@code type}
 * objects. Only {@code PyType} is in the public API and actual
 * implementations further subclass that. This class provides members
 * common to all the implementations, and accessible internally in the
 * run-time system, without being exposed as API from {@code PyType}
 * itself.
 */
public abstract sealed class AbstractPyType extends Representation
        implements WithClass, FastCall permits PyType {

    /**
     * Lookup object with package visibility. This is used by
     * {@link TypeFactory} to create and expose the type.
     */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

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
    final EnumSet<TypeFlag> features = EnumSet.noneOf(TypeFlag.class);

    /**
     * Kernel feature flags collecting various traits of this type that
     * are private to the implementation, such as defining a certain
     * special method. Some of these are mostly observed during
     * construction of the type.
     */
    // Compare CPython tp_flags in object.h
    final EnumSet<KernelTypeFlag> kernelFeatures =
            EnumSet.noneOf(KernelTypeFlag.class);

    /**
     * The {@code __bases__} of this type, which are the types named in
     * heading of the Python {@code class} definition, or just
     * {@code object} if none are named, or an empty array in the
     * special case of {@code object} itself.
     */
    protected PyType[] bases;
    /**
     * The {@code __base__} of this type. The {@code __base__} is a type
     * from the {@code __bases__}, but its choice is determined by
     * implementation details.
     * <p>
     * It is the type earliest on the MRO after the current type, whose
     * implementation contains all the members necessary to implement
     * the current type.
     */
    protected PyType base;
    /**
     * The {@code __mro__} of this type, that is, the method resolution
     * order, as defined for Python and constructed by the {@code mro()}
     * method (which may be overridden), by analysis of the
     * {@code __bases__}.
     */
    protected PyType[] mro;

    /**
     * Collect the information necessary to synthesise and call a
     * constructor for a Java subclass.
     */
    private Map<MethodType, ConstructorAndHandle> constructorLookup;

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
     * The writable dictionary of the type is private because the type
     * controls writing strictly. Even in the core it is only accessible
     * through a read-only view {@link #dict}.
     */
    private final Map<String, Object> _dict;
    /**
     * The dictionary of the type is always an ordered {@code Map}. It
     * is made accessible here through a wrapper that renders it a
     * read-only {@code dict}-like object. Internally names are stored
     * as {@code String} for speed and accessed via
     * {@link #lookup(String)}.
     */
    protected final Map<String, Object> dict;

    /**
     * Base constructor of type objects. We establish values for members
     * common to the several {@link PyType} implementations.
     *
     * @param name of the type (final). May include package name.
     * @param javaClass of instances or {@code null}.
     * @param bases array of the bases (as in a class definition).
     */
    protected AbstractPyType(String name, Class<?> javaClass,
            PyType[] bases) {
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

        this._dict = new LinkedHashMap<>();
        this.dict = Collections.unmodifiableMap(this._dict);
    }

    @Override
    public PyType getType() { return PyType.TYPE; }

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
    protected PyType[] getMRO() { return mro.clone(); }

    /**
     * Calculate and install the MRO from the bases. Used from type
     * factory
     */
    final void setMRO() {
        // FIXME lookup and call mro() in subclasses
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
    protected abstract PyType[] mro();

    /**
     * An immutable list of the {@link Representation}s of this type.
     * These are the representations of the primary or adopted classes
     * in the specification of this type, in order.
     * <p>
     * For a {@link SimpleType}, this is a list with exactly one
     * element: the type itself. In other cases, the single element is a
     * {@link Shared representation shared} with those types that may
     * replace this type on an object. Only an {@link AdoptiveType} is
     * able to support multiple representations.
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

    @Override
    public boolean isDataDescr(Object x) {
        return kernelFeatures.contains(KernelTypeFlag.HAS_SET)
                || kernelFeatures.contains(KernelTypeFlag.HAS_DELETE);
    }

    /**
     * Test for possession of a specified feature.
     *
     * @param feature to check for
     * @return whether present
     */
    public boolean hasFeature(TypeFlag feature) {
        return features.contains(feature);
    }

    @Override
    public boolean hasFeature(Object x, TypeFlag feature) {
        return features.contains(feature);
    }

    /**
     * Test for possession of a specified kernel feature. Kernel
     * features are not public API.
     *
     * @param feature to check for
     * @return whether present
     */
    public boolean hasFeature(KernelTypeFlag feature) {
        return kernelFeatures.contains(feature);
    }

    @Override
    public boolean hasFeature(Object x, KernelTypeFlag feature) {
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
     * Fast check that the target is a sequence, defined as not a
     * subclass of {@code dict} and defining {@code __getitem__}.
     *
     * @return target is a sequence
     */
    // Compare CPython PySequence_Check (on instance) in abstract.c
    public boolean isSequence() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_GETITEM)
                && !features.contains(TypeFlag.DICT_SUBCLASS);
    }

    /**
     * Fast check that the target is iterable with {@code __iter__}.
     *
     * @return target is a iterable with {@code __iter__}
     */
    public boolean isIterable() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_ITER);
    }

    /**
     * Fast check that the target is an iterator (defines
     * {@code __next__}).
     *
     * @return target is a an iterator
     */
    public boolean isIterator() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_NEXT);
    }

    /**
     * Fast check that the target is a descriptor (defines
     * {@code __get__}).
     *
     * @return target is a descriptor
     */
    public boolean isDescr() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_GET);
    }

    /**
     * Fast check that the target is a data descriptor (defines
     * {@code __set__} or {@code __delete__}).
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

    /**
     * The dictionary of the {@code type} in a read-only view.
     *
     * @return dictionary of the {@code type} in a read-only view.
     */
    // @Getter("__dict__")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final Map<Object, Object> getDict() {
        // TODO Ought to be a mappingproxy
        // For now just erase type: safe (I think) since unmodifiable.
        return (Map)dict;
    }

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
    public Object lookup(String name) {

        /*
         * CPython wraps this in a cache keyed by (type, name) and
         * sensitive to the "version" of this type. (Version changes
         * when any change occurs, even in a super-class, that would
         * alter the result of a look-up.) We do not reproduce that at
         * present.
         */

        // CPython checks here to see in this type is "ready".
        // Could we be "not ready" in some loop of types? Think not.

        for (PyType base : mro) {
            Object res;
            if ((res = base.dict.get(name)) != null)
                return res;
        }
        return null;
    }

    /**
     * How durable is the result of the lookup. This supports the
     * possibility of a client behaviour adapting to a result that is
     * valid until a change is notified. See the callback option in
     * {@link AbstractPyType#lookup(String, Consumer)}.
     */
    public enum LookupStatus {
        /** The result is valid for just this enquiry. */
        ONCE,
        /** The result is valid until notified. */
        CURRENT,
        /** The result is final: valid forever. */
        FINAL;
    }

    /**
     * Extended information returned from a lookup on a type object when
     * we need it.
     *
     * @param obj the object found
     * @param where type in which it was found
     * @param status the extent to which this result is stable
     */
    public static record LookupResult(Object obj, PyType where,
            LookupStatus status) {}

    /**
     * Lookup for the definition of the name along the MRO, returning an
     * extended result that includes where it was found and whether it
     * might change. The caller provides a callback method that will be
     * called every time the definition of this name changes.
     *
     * @param name to look up
     * @param callback to deliver updates (or {@code null})
     * @return extended result or {@code null} if not found
     */
    // FIXME Support the lookup callback properly
    // Deal with *some* uses not needing a callback when found on this
    public LookupResult lookup(String name,
            Consumer<LookupResult> callback) {
        /*
         * CPython wraps this in a cache keyed by (type, name) and
         * sensitive to the "version" of this type. (Version changes
         * when any change occurs, even in a super-class, that would
         * alter the result of a look-up.) We do not reproduce that at
         * present.
         */

        // CPython checks here to see in this type is "ready".
        // Could we be "not ready" in some loop of types? Think not.

        LookupStatus status = LookupStatus.FINAL;

        /* Search along the MRO. */
        for (PyType base : mro) {
            Object obj;
            /*
             * The result is not FINAL if *any* type on the way is
             * mutable since that type could later introduce a
             * definition in front of the one we now find.
             */
            if (status == LookupStatus.FINAL && base.isMutable()) {
                status = callback == null ? LookupStatus.ONCE
                        : LookupStatus.CURRENT;
            }
            if ((obj = base.dict.get(name)) != null) {
                // We found a definition in type object base.
                return new LookupResult(obj, base, status);
            }
        }
        return null;
    }

    /**
     * Put a value in the dictionary of the type directly (for runtime
     * use only). This gives privileged access to the dictionary of the
     * type. It will trigger internal updates to the state of the type
     * object that follow from attribute values.
     *
     * @param name in the dictionary of the type
     * @param value to put there
     * @return the previous value (or null)
     */
    public // throughout the run time not Jython API.
    Object dictPut(String name, Object value) {
        Object previous = _dict.put(name, value);
        updateAfterSetAttr(name);
        return previous;
    }

    /**
     * Remove an entry from the dictionary of the type directly (for
     * runtime use only). This gives privileged access to the dictionary
     * of the type. It will trigger internal updates to the state of the
     * type object that follow from attribute values.
     *
     * @param name in the dictionary of the type
     * @return the previous value (or null)
     */
    public // throughout the run time not Jython API.
    Object dictRemove(String name) {
        Object previous = _dict.remove(name);
        updateAfterSetAttr(name);
        return previous;
    }

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
    public Map<MethodType, ConstructorAndHandle> constructorLookup() {
        return constructorLookup;
    }

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
    public ConstructorAndHandle constructor(Class<?>... param) {
        // Neutralise the actual return type
        MethodType mt = MethodType.methodType(Object.class, param);
        ConstructorAndHandle ch = constructorLookup.get(mt);
        if (ch == null) {
            /*
             * This method is really here for __new__ implementations,
             * and has to be public because they could be in any
             * package. So, stuff can go wrong. But how to explain to
             * the hapless caller when something is really a Java API
             * error?
             */
            // TODO Need a Java API Error not InterpreterError
            throw PyErr.format(PyExc.TypeError,
                    "Incorrect arguments to constructor of '%s'",
                    getName());
        }
        return ch;
    }

    /**
     * Discover the Java constructors of representations of this type
     * and create method handles on them. This is what allows
     * {@code __new__} in a Python superclass of this type to create an
     * instance, even though it has no knowledge of the type at compile
     * time.
     *
     * @param spec specification of this type
     */
    void fillConstructorLookup(TypeSpec spec) {
        // Collect constructors in this table
        Map<MethodType, ConstructorAndHandle> table = new HashMap<>();
        // We allow public construction only of the canonical base.
        // (This is right for use by __new__ ... and generally?)
        Class<?> baseClass = spec.getCanonicalBase();
        Lookup lookup = spec.getLookup();
        final int accept = Modifier.PUBLIC | Modifier.PROTECTED;
        for (Constructor<?> cons : baseClass
                .getDeclaredConstructors()) {
            int modifiers = cons.getModifiers();
            try {
                if ((modifiers & accept) != 0) {
                    MethodHandle mh = lookup.unreflectConstructor(cons);
                    // Neutralise the actual return type to Object
                    MethodType mt = mh.type();
                    mt = mt.changeReturnType(Object.class);
                    mh = mh.asType(mt);
                    table.put(mt, new ConstructorAndHandle(cons, mh));
                }
            } catch (IllegalAccessException e) {
                throw new InterpreterError(e,
                        "Failed to get handle for constructor %s",
                        cons);
            }
        }
        // Freeze the table as the constructor lookup
        constructorLookup = Collections.unmodifiableMap(table);
    }

    /**
     * Inherit feature flags (including kernel feature flags) from the
     * base. This should be called early enough in initialising the type
     * that it can be modified by configuration with exposed methods.
     */
    void inheritFeatures() {
        AbstractPyType base = this.base;
        if (base != null) {
            // Inherit the customary features
            EnumSet<TypeFlag> inheritedTF =
                    EnumSet.copyOf(base.features);
            inheritedTF.retainAll(TypeFlag.HERITABLE);
            features.addAll(inheritedTF);
            // Inherit the customary kernel features
            EnumSet<KernelTypeFlag> inheritedKTF =
                    EnumSet.copyOf(base.kernelFeatures);
            inheritedKTF.retainAll(KernelTypeFlag.HERITABLE);
            kernelFeatures.addAll(inheritedKTF);
        }
    }

    /**
     * Add feature flags from the specification. This should be called
     * early enough in initialising the type that it can influence
     * configuration with exposed methods.
     *
     * @param spec specification of this type
     */
    void addFeatures(TypeSpec spec) {
        for (Feature f : spec.getFeatures()) { features.add(f.flag); }
    }

    /**
     * Add feature flags derived from observations of the type itself
     * (and the specification if necessary). This should be called at
     * the end of initialising the type so that it is influenced by the
     * set of exposed methods.
     *
     * @param spec specification of this type
     */
    // Compare CPython inherit_special in typeobject.c
    void deriveFeatures(TypeSpec spec) {
        /*
         * Set at most one of the fast sub-type test flags. (Use short
         * circuit evaluation to stop at the first flag set.) We cannot
         * rely on this==PyWhatever.TYPE when constructing a type for
         * 'whatever', as the TYPE won't be assigned yet.
         */
        //
        @SuppressWarnings("unused")
        boolean unused = setFeature(PyLong.class, TypeFlag.INT_SUBCLASS)
                || setFeature(PyTuple.class, TypeFlag.TUPLE_SUBCLASS)
                || setFeature(PyUnicode.class, TypeFlag.STR_SUBCLASS)
                || setFeature(PyDict.class, TypeFlag.DICT_SUBCLASS)
                || setFeature(PyBaseException.class,
                        TypeFlag.EXCEPTION_SUBCLASS);

        /*
         * Certain built-ins have this feature, which relates to pattern
         * matching. It would be nice to accomplish this in the
         * TypeSpec, but it is not public API.
         */
        unused = setFeature(PyLong.class, KernelTypeFlag.MATCH_SELF)
                || setFeature(PyFloat.class, KernelTypeFlag.MATCH_SELF)
                // || setFeature(PyBytes.class,
                // KernelTypeFlag.MATCH_SELF)
                || setFeature(PyUnicode.class,
                        KernelTypeFlag.MATCH_SELF)
                || setFeature(PyList.class, KernelTypeFlag.MATCH_SELF)
                || setFeature(PyDict.class, KernelTypeFlag.MATCH_SELF);
    }

    /**
     * Maybe set the given feature flag. The flag is set if and only if
     * the implementation class of this type matches that given, or the
     * same flag is set in the base. This way, there is a first
     * qualifying type {@code k}, then every type that descends from it
     * inherits the flag.
     *
     * @param k identifying the first qualifying type
     * @param f flag marking a sub-type
     * @return {@code true} iff the flag was set in this call
     */
    private final boolean setFeature(Class<?> k, TypeFlag f) {
        AbstractPyType base = this.base;
        if (base != null) {
            if (javaClass == k || base.features.contains(f)) {
                features.add(f);
                return true;
            }
        }
        return false;
    }

    /**
     * Maybe set the given kernel feature flag. The flag is set if and
     * only if the implementation class of this type matches that given,
     * or the same flag is set in the base. This way, there is a first
     * qualifying type {@code k}, then every type that descends from it
     * inherits the flag.
     *
     * @param k identifying the first qualifying type
     * @param f flag marking a sub-type
     * @return {@code true} iff the flag was set in this call
     */
    private final boolean setFeature(Class<?> k, KernelTypeFlag f) {
        AbstractPyType base = this.base;
        if (base != null) {
            if (javaClass == k || base.kernelFeatures.contains(f)) {
                kernelFeatures.add(f);
                return true;
            }
        }
        return false;
    }

    /**
     * Load the dictionary of this type with attributes discovered by an
     * exposer. This is a package-visible hook with direct access to the
     * dictionary, for {@link TypeFactory} to use during type
     * construction.
     *
     * @param exposer from which to populate the dictionary
     * @param supplying the lookup object
     */
    void populateDict(TypeExposer exposer, TypeSpec spec) {

        // Add contents of the exposer to the dictionary
        Lookup lookup = spec.getLookup();
        for (TypeExposer.Entry e : exposer.entries(lookup)) {
            _dict.put(e.name(), e.value());
            updateAfterSetAttr(e.name());
        }

        // Fill the cache for each special method (not just defined).
        for (SpecialMethod sm : SpecialMethod.values()) {
            LookupResult result = lookup(name, null);
            updateSpecialMethodCache(sm, result);
        }
    }

    /**
     * Called from {@code type.__setattr__} and
     * {@code type.__delattr__(String)} after an attribute has been set
     * or deleted. This gives the type the opportunity to recompute
     * caches and perform any other actions needed.
     *
     * @param name of the attribute modified
     */
    public // throughout the run time not Jython API.
    void updateAfterSetAttr(String name) {

        // FIXME Notify sub-classes and other watchers.
        // Think about synchronisation: must take a lock on lookup.

        /*
         * We look up the current definition of name for this type,
         * which has recently changed. Note that even when renmoving the
         * name from the dictionary of this type, the effect may be to
         * uncover new definition somewhere along the MRO, and so it
         * becomes a change.
         */
        LookupResult result = lookup(name, null);
        SpecialMethod sm = SpecialMethod.forMethodName(name);

        if (sm != null) {
            // Update affects a special method.
            updateSpecialMethodCache(sm, result);
            // Some special methods need:
            KernelTypeFlag feature = switch (sm) {
                case op_getitem -> KernelTypeFlag.HAS_GETITEM;
                case op_iter -> KernelTypeFlag.HAS_ITER;
                case op_next -> KernelTypeFlag.HAS_NEXT;
                case op_index -> KernelTypeFlag.HAS_INDEX;
                case op_get -> KernelTypeFlag.HAS_GET;
                case op_set -> KernelTypeFlag.HAS_SET;
                case op_delete -> KernelTypeFlag.HAS_DELETE;
                default -> null;
            };
            // If sm corresponds to a feature flag
            if (feature != null) {
                if (result != null) {
                    // We are defining or changing sm
                    kernelFeatures.add(feature);
                } else {
                    // We are deleting sm
                    kernelFeatures.remove(feature);
                }
            }

        } else if ("__new__".equals(name)) {
            // Update affects __new__.
            // updateNewCache();
        }
    }

    /**
     * Update the cache for each representation of this type, and
     * certain feature values, by looking up the definition along the
     * MRO.
     *
     * @param sm the special method
     * @param result of looking up the name, may be ({@code null}
     */
    private void updateSpecialMethodCache(SpecialMethod sm,
            LookupResult result) {

        if (sm.cache == null) {
            // There is no cache for this special method. Ignore.
            return;

        } else if (result == null) {
            /*
             * The special method is not defined for this type. Install
             * a handle that throws EmptyException. (Unlike in CPython,
             * null won't do.)
             */
            for (Representation rep : representations()) {
                sm.setEmpty(rep);
            }
        } else if (result.status == LookupStatus.ONCE) {
            /*
             * We can't cache the result. Use a generic slot wrapper so
             * we look it up on the type object every time.
             */
            for (Representation rep : representations()) {
                sm.setGeneric(rep);
            }
        } else if (result.obj instanceof MethodDescriptor descr) {
            /*
             * A method descriptor can give us a direct handle to the
             * implementation for a given self class.
             */
            updateSpecialMethodCache(sm, result.where, descr);
        } else {
            /*
             * It is a method defined in Python or some other object or
             * descriptor. Use a generic slot wrapper to look it up (and
             * bind it) each time.
             */
            for (Representation rep : representations()) {
                sm.setGeneric(rep);
            }
        }
    }

    /**
     * Update the cache for each representation of this type, from a
     * method descriptor.
     *
     * @param sm the special method
     * @param where the descriptor was found along the MRO
     * @param descr the descriptor defining the special method
     */
    private void updateSpecialMethodCache(SpecialMethod sm,
            PyType where, MethodDescriptor descr) {
        if (where == this) {
            /*
             * We found the definition locally. Method descriptors
             * created for this type explicitly support all its
             * representations.
             */
            for (Representation rep : representations()) {
                int index = rep.getIndex();
                sm.setCache(rep, descr.getHandle(index));
            }
        } else {
            /*
             * The method descriptor is in a super class. Every
             * representation class of this type must be
             * assignment-compatible with a self-class where we found
             * the descriptor (index 0 or an accepted class).
             */
            List<Class<?>> classes = where.selfClasses();
            for (Representation rep : representations()) {
                Class<?> c = rep.javaClass;
                int index = where.getSubclassIndex(c);
                assert index < classes.size();
                sm.setCache(rep, descr.getHandle(index));
            }
        }
    }

    // Special methods -----------------------------------------------

    /** @return {@code repr()} of this Python object. */
    public Object __repr__() {
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
     * @throws PyBaseException (TypeError) when cannot create instances
     * @throws Throwable from implementation slot functions
     */
    public Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            throw typeError(ae, args, names);
        }
    }

    // FastCall implementation ---------------------------------------

    /*
     * These methods may be called instead of __call__ to take advantage
     * of potentially efficient handling of arguments in a type's
     * __new__ method. There is a short-cut in Callables.call that
     * detects the FastCall interface. In a CallSite, the MethodHandle
     * could be formed with this in mind.
     */

    @Override
    public Object call(Object[] args, String[] names)
            throws ArgumentError, Throwable {
        /*
         * Special case: type(x) should return the Python type of x, but
         * only if this is exactly the type 'type'.
         */
        if (this == PyType.TYPE) {
            // Deal with two special cases
            assert (args != null);
            int nk = names == null ? 0 : names.length;
            int np = args.length - nk;

            if (np == 1 && nk == 0) {
                // Call is exactly type(x) so this is a type enquiry
                return PyType.of(args[0]);

            } else if (np != 3) {
                // Call ought to be type(x, bases, dict [, **kwds])
                // __new__ will check too but we prefer this message.
                throw PyErr.format(PyExc.TypeError,
                        "type() takes 1 or 3 arguments");
            }
        }

        // Call __new__ of the type described by this type object
        // XXX Almost certainly cache this so we know the type.
        Object new_ = lookup("__new__");
        Object obj = _PyUtil.callPrepend(new_, this, args, names);

        // Call obj.__init__ if it is defined and type(obj) == this
        maybeInit(obj, args, names);
        return obj;
    }

    /**
     * Call {@code obj.__init__(args, names)} after {@code __new__} if
     * it is defined and if obj is an instance of this type (or of a
     * sub-type). It is not an error for {@code __new__} not to return
     * an instance of this type.
     *
     * @param obj returned from {@code __new__}
     * @param args passed to __new__
     * @param names passed to __new__
     * @throws Throwable Python errors from {@code __init__}
     */
    private void maybeInit(Object obj, Object[] args, String[] names)
            throws Throwable {
        assert obj != null;
        Representation rep = AnyType.getRepresentation(obj);
        PyType objType = rep.pythonType(obj);
        /*
         * If obj is an instance of this type (or of a sub-type) call
         * any __init__ defined for it.
         */
        if (objType.isSubTypeOf(this)) {
            try {
                // Call obj.__init__ (args, names)
                rep.op_init().invoke(obj, args, names);
            } catch (EmptyException ee) {
                // Not an error for __init__ not to be defined
            }
        }
    }

// @Override
// public Object call(Object a0) throws Throwable {
// if (this == PyType.TYPE) {
// // Call is exactly type(x) so this is a type enquiry
// return PyType.of(a0);
// }
// Object obj = newMethod.call(this, a0);
// maybeInit(obj, a0);
// return obj;
// }
//
// @Override
// public Object call(Object a0, Object a1) throws Throwable {
// // Note that this cannot be a type enquiry
// Object obj = newMethod.call(this, a0, a1);
// maybeInit(obj, a0, a1);
// return obj;
// }

    @Override
    public PyBaseException typeError(ArgumentError ae, Object[] args,
            String[] names) {
        // Almost certainly not called, but let __new__ explain
        // FIXME: reinstate newMethod.typeError
        // return newMethod.typeError(ae, args, names);
        return PyErr.format(PyExc.TypeError, "some type error");
    }

    /**
     * Validate the argument presented first in a call to
     * {@code __new__} against {@code this} as the defining type. When
     * {@code type.__call__} is not simply a type enquiry, it is a
     * request to construct an instance of the type that is the
     * {@code self} argument (or {@code this}).
     * <p>
     * The receiving type should then search for a definition of
     * {@code __new__} along the MRO, and pass itself as the first
     * argument, called {@code cls} in the Python documentation for
     * {@code __new__}. This definition is necessarily provided by a
     * superclass. Certainly {@link PyType#__call__(Object[], String[])
     * PyType.__call__} will do this, and the {@code __new__} of all
     * classes, whether defined in Python or Java, should include a
     * comparable action.
     * <p>
     * This method asks the defining class (as {@code this}) to validate
     * that {@code cls} is a Python sub-type of the defining class. We
     * apply this validation to {@code __new__} calls in every Python
     * type defined in Java. It is implemented as a wrapper on the
     * handle in the {@link PyJavaFunction} that exposes {@code __new__}
     * for that type. Invoking that handle, will call a Java method
     * that, in simple cases, is defined by:<pre>
     * T __new__(PyType cls, ...) {
     *     if (cls == T.TYPE)
     *         return new T(...);
     *     else
     *         return new S(cls, ...);
     * }
     * </pre> where {@code S} is a Java subclass of the canonical base
     * of {@code cls}. The instance of S created will subsequently claim
     * a Python type {@code cls} in its {@code __class__} attribute. The
     * validation enforces the constraint that instances of {@code S}
     * may only be instances of a Python sub-type of the type T
     * represents.
     * <p>
     * The {@code __new__} of a class defined in Python is a harmless
     * {@code staticmethod}. It doesn't matter how defective it is
     * until, during {@code super().__new__}, we reach a built-in type
     * and then this validation will be applied.
     *
     * @param arg0 first argument to the {@code __new__} call
     * @return arg0 if the checks succeed
     * @throws PyBaseException (TypeError) if the checks fail
     */
    // Compare CPython tp_new_wrapper in typeobject.c
    public PyType validatedNewArgument(Object arg0)
            throws PyBaseException {
        if (arg0 instanceof PyType cls) {
            if (cls.isSubTypeOf(this)) {
                return cls;
            } else {
                String name = getName(), clsName = cls.getName();
                throw PyErr.format(PyExc.TypeError,
                        "%s.__new__(%s): %s is not a subtype of %s", //
                        name, clsName, clsName, name);
            }
        } else {
            // arg0 wasn't even a type
            throw PyErr.format(PyExc.TypeError,
                    "%s.__new__(X): X must be a type object not %s",
                    this.getName(), PyType.of(arg0).getName());
        }
    }

}
