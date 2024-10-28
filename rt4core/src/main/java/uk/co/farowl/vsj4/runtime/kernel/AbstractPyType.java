// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithClass;

/**
 * {@code AbstractPyType} is the Java base of Python {@code type}
 * objects. Only {@code PyType} is in the public API and actual
 * implementations further subclass that. This class provides members
 * common to all the implementations, and accessible internally in the
 * run-time system, without being exposed as API from {@code PyType}
 * itself.
 */
public abstract sealed class AbstractPyType extends Representation
        implements WithClass permits PyType {

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
}
