// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.Feature;
import uk.co.farowl.vsj4.runtime.MethodDescriptor;
import uk.co.farowl.vsj4.runtime.Py;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyList;
import uk.co.farowl.vsj4.runtime.PyLong;
import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PyTuple;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUnicode;
import uk.co.farowl.vsj4.runtime.TypeFlag;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * A base shared by the concrete implementation classes of the Python
 * {@code type} object. This class also allows us to publish methods
 * from the {@code kernel} package, without them becoming Jython API.
 * These will apply to all {@link PyType}s, since all actual type
 * objects are implemented as a subclasses of this one.
 */
public abstract sealed class BaseType extends PyType
        permits SimpleType, ReplaceableType, AdoptiveType {

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
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     */
    protected BaseType(String name, Class<?> javaClass,
            PyType[] bases) {
        this(name, javaClass, bases, new LinkedHashMap<>());
    }

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     * @param _dict dictionary we keep locally and in the base
     */
    private BaseType(String name, Class<?> javaClass, PyType[] bases,
            LinkedHashMap<String, Object> _dict) {
        super(name, javaClass, bases, _dict);
        this._dict = _dict;
    }

    /**
     * The array of bases specified for the type as a reference.
     * Modifications to that array modify the field behind
     * {@link PyType#getBases()}.
     *
     * @return the sequence of bases
     */
    PyType[] bases() { return bases; }

    @Override
    public PyType[] getMRO() {
        return Arrays.copyOf(mro, mro.length, PyType[].class);
    }

    /**
     * Calculate and install the MRO from the bases. Used from type
     * factory
     */
    protected final void setMRO() {
        // FIXME for lookup and call mro() in subclasses
        mro = new PyTuple(mro()).toArray(new BaseType[0]);
    }

    // Compare CPython PyType_IsSubtype in typeobject.c
    // CPython documentation:
    // int PyType_IsSubtype(PyTypeObject *a, PyTypeObject *b)
    // Return true if a is a subtype of b.
    //
    // This function only checks for actual subtypes, which means that
    // __subclasscheck__() is not called on b. Call
    // PyObject_IsSubclass() to do the same check that issubclass()
    // would do.
    @Override
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
     * How durable is the result of the lookup. This supports the
     * possibility of a client behaviour adapting to a result that is
     * valid until a change is notified. See the callback option in
     * {@link lookup(String, Consumer)}.
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
        for (BaseType base : mro) {
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

    // Compare CPython _PyType_Lookup in typeobject.c
    // and find_name_in_mro in typeobject.c
    @Override
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

        for (BaseType base : mro) {
            Object res;
            if ((res = base.dict.get(name)) != null)
                return res;
        }
        return null;
    }

    /**
     * {@inheritDoc}
     * <p>
     * For a {@link SimpleType}, this is a list with exactly one
     * element: the type itself. In a {@link ReplaceableType}, the
     * single element is a {@link SharedRepresentation} shared with
     * those types that may replace this type on an object. Only an
     * {@link AdoptiveType} is able to support multiple representations.
     */
    @Override
    public abstract List<Representation> representations();

    /**
     * Determine (or create if necessary) the {@link Representation} for
     * the given object. The representation is found (in the type
     * registry) from the Java class of the argument.
     * <p>
     * Duplicates {@code PyType.getRepresentation} for the kernel.
     *
     * @param o for which a {@code Representation} is required
     * @return the {@code Representation}
     */
    static Representation getRepresentation(Object o) {
        return registry.get(o.getClass());
    }

    /**
     * Cast the argument to a Jython type object (or throw). It is
     * possible for a client application to supply a {@link PyType} that
     * did not originate in the Jython run-time system, whereas
     * internally we take advantage of private API that requires a
     * {@code BaseType}. Occasionally, we have to check that, usually on
     * assignment to a local variable or a member.
     *
     * @param t presented to our API somewhere
     * @return {@code t} if it is a {@code BaseType}
     * @throws ClassCastException {@code t} is not a {@code BaseType}
     */
    public static BaseType cast(PyType t) throws InterpreterError {
        if (t instanceof BaseType bt) {
            return bt;
        } else {
            throw new ClassCastException(String.format(
                    "non-Jython PyType encountered: %s", t.getClass()));
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
        Representation rep = BaseType.getRepresentation(obj);
        PyType objType = rep.pythonType(obj);
        /*
         * If obj is an instance of this type (or of a sub-type) call
         * any __init__ defined for it.
         */
        if (objType.isSubTypeOf(this)) {
            try {
                // Call obj.__init__ (args, names)
                MethodHandle init = SpecialMethod.op_init.handle(rep);
                init.invoke(obj, args, names);
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

    // Special methods -----------------------------------------------

    /*
     * For technical reasons to do with bootstrapping the type system,
     * the methods and attributes of 'type' that are exposed to Python
     * have to be defined with at least package visibility.
     */

    // Support for Type Initialisation -------------------------------

    /*
     * For technical reasons to do with bootstrapping the type system,
     * the methods and attributes of 'type' that are exposed to Python
     * have to be defined with at least package visibility.
     */

    /**
     * Inherit feature flags (including kernel feature flags) from the
     * base. This should be called early enough in initialising the type
     * that it can be modified by configuration with exposed methods.
     */
    void inheritFeatures() {
        if (base != null) {
            // Inherit the customary features
            for (TypeFlag f : TypeFlag.HERITABLE) {
                if (base.hasFeature(f)) { features.add(f); }
            }
            // Inherit the customary kernel features
            for (KernelTypeFlag f : KernelTypeFlag.HERITABLE) {
                if (base.hasFeature(f)) { kernelFeatures.add(f); }
            }
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
        if (base != null) {
            if (javaClass == k || base.hasFeature(f)) {
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
        if (base != null) {
            if (javaClass == k || base.hasFeature(f)) {
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
     * @param spec supplying the type's lookup object
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
            LookupResult result = lookup(sm.methodName, null);
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
    @Override
    public // throughout the run time not Jython API.
    void updateAfterSetAttr(String name) {

        // FIXME Notify sub-classes and other watchers.
        // Think about synchronisation: must take a lock on lookup.

        /*
         * We look up the current definition of name for this type,
         * which has recently changed. Note that even when removing the
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
                Class<?> c = rep.javaClass();
                int index = where.getSubclassIndex(c);
                assert index < classes.size();
                sm.setCache(rep, descr.getHandle(index));
            }
        }
    }

    /**
     * Collect the information necessary to synthesise and call a
     * constructor for a Java subclass of the {@link #javaClass()} of
     * this type.
     */
    private Map<MethodType, ConstructorAndHandle> constructorLookup;

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
    @Override
    public Map<MethodType, ConstructorAndHandle> constructorLookup() {
        return constructorLookup;
    }

    @Override
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

    // plumbing ------------------------------------------------------

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
            t = t.getBase();
            if (t == null) { return b == PyObject.TYPE; }
        }
        return true;
    }
}
