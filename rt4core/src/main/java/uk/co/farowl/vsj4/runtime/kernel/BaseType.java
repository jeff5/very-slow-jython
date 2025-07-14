// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static uk.co.farowl.vsj4.runtime.internal._PyUtil.cantSetAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.mandatoryAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.noAttributeError;
import static uk.co.farowl.vsj4.runtime.internal._PyUtil.readonlyAttributeError;

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
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.Exposed;
import uk.co.farowl.vsj4.runtime.Feature;
import uk.co.farowl.vsj4.runtime.MethodDescriptor;
import uk.co.farowl.vsj4.runtime.PyAttributeError;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyDict;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyList;
import uk.co.farowl.vsj4.runtime.PyLong;
import uk.co.farowl.vsj4.runtime.PyObject;
import uk.co.farowl.vsj4.runtime.PySequence;
import uk.co.farowl.vsj4.runtime.PyTuple;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.PyUnicode;
import uk.co.farowl.vsj4.runtime.TypeFlag;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithDict;
import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * A base shared by the concrete implementation classes of the Python
 * {@code type} object. The class is used widely in the run time system
 * where a {@link PyType} might have been expected, so that methods may
 * be accessed that are not exposed as Jython API. Where a
 * {@link PyType} is accepted from outside the run-time system it must
 * be cast to {@code BaseType} (see {@link BaseType#cast(PyType)}). This
 * will succeed for all genuine Jython type objects.
 * <p>
 * In the layered architecture of the Python type object, this class
 * takes responsibility for attributes and lookup along the MRO. It
 * contains the apparatus to make the type "Python-ready".
 */
public abstract sealed class BaseType extends KernelType
        permits SimpleType, ReplaceableType, AdoptiveType {

    /**
     * The {@code __mro__} of this type, that is, the method resolution
     * order, as defined for Python and constructed by the {@code mro()}
     * method (which may be overridden), by analysis of the
     * {@code __bases__}.
     */
    protected BaseType[] mro;

    /**
     * The dictionary of the type is always an ordered {@code Map}. The
     * real, writable dictionary is private because the type controls
     * writing strictly. Even in the kernel it is only accessible
     * through a read-only view {@link #dict}.
     */
    private final LinkedHashMap<String, Object> _dict;

    /**
     * The real, writable dictionary is made accessible here through a
     * wrapper that renders it a read-only {@code dict}-like object.
     * Internally names are stored as {@code String} for speed and
     * accessed via {@link #lookup(String)}.
     */
    protected final Map<String, Object> dict;

    /**
     * Constructor used by (permitted) subclasses of {@code PyType}.
     *
     * @param name of the type (fully qualified)
     * @param javaClass implementing Python instances of the type
     * @param bases of the new type
     */
    protected BaseType(String name, Class<?> javaClass,
            BaseType[] bases) {
        super(name, javaClass, bases);
        this._dict = new LinkedHashMap<>();
        // FIXME: define mappingproxy type for this use
        this.dict = Collections.unmodifiableMap(_dict);
    }

    @Override
    public BaseType[] getMRO() {
        return Arrays.copyOf(mro, mro.length, BaseType[].class);
    }

    /**
     * Compute and install the MRO from the bases. Used from type
     * factory and during certain kinds of change to the type hierarchy.
     */
    // Compare CPython mro_internal in typeobject.c
    final void computeInstallMRO() {
        // TODO propagate MRO change to Python subclasses
        try {
            mro = mro_invoke();
        } catch (Throwable e) {
            // FIXME handle errors creating new MRO with roll-back
            throw new InterpreterError(e, "Computing new MRO of '%s'",
                    this.getName());
        }
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
    public static record LookupResult(Object obj, BaseType where,
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
     * Determine (or create if necessary) the Python type for the given
     * object. In the run-time system, we use this in place of
     * {@link PyType#of(Object)}, so that we get the more specific type
     * of result.
     *
     * @param o for which a type is required
     * @return the type
     */
    public static BaseType of(Object o) {
        return registry.get(o.getClass()).pythonType(o);
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
            Representation metaAttrRep = Representation.get(metaAttr);
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
                return Representation.get(attr).op_get()
                        .invokeExact(attr, (Object)null, (PyType)this);
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
            Representation metaAttrRep = Representation.get(metaAttr);
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
            Representation metaAttrRep = Representation.get(metaAttr);
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

    /**
     * Calculate a new MRO for this type by the default algorithm. This
     * method is exposed as the method {@code mro} of type
     * {@code objects} and may be overridden in a Python subclass of
     * {@code type} (a "metatype") to customise the MRO in the types it
     * creates.
     *
     * @return a new MRO for the type
     */
    // Compare CPython type_mro_impl in typeobject.c
    @Exposed.PythonMethod
    protected PyList mro() {
        // Note: this is only the default behaviour (type)
        PyType[] newMRO = MROCalculator.getMRO(this, this.bases);
        return new PyList(newMRO);
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
        if (this == PyType.TYPE()) {
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
        Representation rep = Representation.get(obj);
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
    // if (this == PyType.TYPE()) {
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
    private /* ? */ void updateAfterSetAttr(String name) {

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
            BaseType where, MethodDescriptor descr) {
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
             * representation class of this type should be
             * assignment-compatible with one of the self-classes of the
             * Python type where we found the descriptor.
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

    /**
     * Invoke a custom {@code mro()}, if one has been defined for this
     * type, or do the equivalent of {@link #mro()} if not. The method
     * is safe to use during type system initialisation, when bootstrap
     * types are still being created. A custom {@code mro()} cannot have
     * been defined by then, and so the alternate path is always taken.
     *
     * @return a valid MRO for the type
     * @throws Throwable on Python errors
     */
    // Compare CPython mro_invoke in typeobject.c
    // Unlike CPython, we return an array, avoiding PyTuple.
    private BaseType[] mro_invoke() throws Throwable {

        BaseType[] newMRO = null;
        SimpleType typeType = getTypeForType();
        // FIXME getType() of concrete PyTypes should be concrete
        // This will affect the synthesis of sub-type representations
        BaseType metatype = BaseType.cast(getType());

        if (metatype != typeType) {
            LookupResult lur = metatype.lookup("mro", null);
            if (lur != null && lur.where != typeType) {
                /*
                 * This is a metatype that redefines mro(). We call its
                 * specific mro() as a Python method.
                 */
                Object mro_result = Callables.call(lur.obj, this);
                // It returns some kind of iterable.
                List<Object> result = PySequence.fastList(mro_result,
                        () -> PyErr.format(PyExc.TypeError,
                                "mro() should return an iterable",
                                PyType.of(mro_result)));
                newMRO = checkedMRO(result);
            }
        }

        if (newMRO == null) {
            /*
             * This type is exactly 'type' (or has somehow undefined
             * mro()). We shall do the equivalent of type.mro() but
             * without building a PyList.
             */
            newMRO = MROCalculator.getMRO(this, this.bases);
        }

        return newMRO;
    }

    /**
     * Build an array of bases to represent the MRO of this type, from
     * the list returned by a custom {@code mro()}, after careful type
     * and consistency checks.
     *
     * @param mro result of custom {@code mro()} call as list
     * @return validated array of bases
     */
    // Compare CPython mro_check in typeobject.c
    // Unlike CPython, we build an array here, avoiding PyTuple.
    private BaseType[] checkedMRO(List<Object> mro) {

        int n = mro.size(), index = 0;

        if (n == 0) {
            throw PyErr.format(PyExc.TypeError,
                    "type MRO must not be empty");
        }

        BaseType[] new_mro = new BaseType[n];
        BaseType solid = solid_base();

        for (Object obj : mro) {
            if (obj instanceof BaseType base) {
                if (solid.isSubTypeOf(base.solid_base())) {
                    // Admissible base: all other paths throw.
                    new_mro[index++] = base;
                } else {
                    throw PyErr.format(PyExc.TypeError,
                            "mro() returned base with unsuitable layout ('%.500s')",
                            base.getName());
                }
            } else if (obj instanceof PyType) {
                // Extra help for non-BaseType PyType
                throw PyErr.format(PyExc.TypeError,
                        "mro() returned a type not created by the runtime ('%.500s')",
                        obj.getClass().getTypeName());
            } else {
                // obj isn't even pretending to be a Python type.
                throw PyErr.format(PyExc.TypeError,
                        "mro() returned a non-class ('%.500s')",
                        PyType.of(obj).getName());
            }
        }

        return new_mro;
    }

    /**
     * Find the "solid base" of this type. The "solid base" of a type T
     * is a type S that is the most distant ancestor of T along the
     * chain of bases of T, including T itself, that has the same
     * primary representation as T.
     * <p>
     * Put another way, S is the least-derived Python class that has the
     * same (primary) representation class as T. CPython refers here to
     * the "layout" of the type, meaning the memory layout of instances,
     * but for us it is the Java class (or base of several classes) that
     * matters.
     * <p>
     * Having the same representation means that methods defined in Java
     * for S can be applied to instances (in Python) of T, because the
     * Java class of every instance of T is a subclass in Java of the
     * (primary) class of S. This property plays a role in determining
     * what makes an acceptable set of bases for a Python type.
     *
     * @return the "solid base" of this type
     */
    // Compare CPython solid_base() in typeobject.c
    private BaseType solid_base() {
        // Walk the base chain and return just before the class change.
        BaseType sb;
        if (base != null) {
            sb = base.solid_base();
        } else {
            // This must be the type object of object
            return this;
        }
        return javaClass() == sb.javaClass() ? sb : this;
    }
}
