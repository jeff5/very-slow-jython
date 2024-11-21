// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.FastCall;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyJavaFunction;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.runtime.internal._PyUtil;

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

    /** Name of the type (fully-qualified). */
    final String name;

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
     * Find the index in this type corresponding to the class of an
     * object passed as {@code self} to a method of the type.
     *
     * @deprecated This entry point exists to support legacy VSJ3 code
     *     ported to VSJ4. There is a better way than this using the
     *     index available from a {@code Representation}.
     * @param selfClass to seek
     * @return index in {@link #selfClasses()} or -1
     */
    @Deprecated
    public int indexAccepted(Class<?> selfClass) {
        return selfClasses().indexOf(selfClass);
    }

    /**
     * The dictionary of the {@code type} in a read-only view.
     *
     * @return dictionary of the {@code type} in a read-only view.
     */
    // @Getter("__dict__")
    @SuppressWarnings({"unchecked", "rawtypes"})
    public final Map<Object, Object> getDict() {
        // XXX Ought to be a mappingproxy
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

    /** Lookup object with package visibility. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

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
        exposer.populate(_dict, spec.getLookup());
    }

    // Special methods -----------------------------------------------

    protected Object __repr__() throws Throwable {
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
     * @throws PyBaseException(TypeError) when cannot create instances
     * @throws Throwable from implementation slot functions
     */
    protected Object __call__(Object[] args, String[] names)
            throws PyBaseException, Throwable {
        try {
            return call(args, names);
        } catch (ArgumentError ae) {
            throw typeError(ae, args, names);
        }
    }

    // FastCall implementation ---------------------------------------

    /*
     * These methods may be called instead of __call_ to take advantage
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
        // XXX Call __new__ and __init__ via Callables or SpecialMethod
        // XXX Almost certainly cache this so we know the type.
        Object newMethod = lookup("__new__");
        Object obj = _PyUtil.call(newMethod, this, args, names);

        // Call obj.__init__ if it is defined and type(obj) == this
        // maybeInit(obj, args, names);
        return obj;
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
