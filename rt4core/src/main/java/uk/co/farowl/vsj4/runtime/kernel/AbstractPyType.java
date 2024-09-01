// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.Crafted;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.MissingFeature;

/**
 * {@code AbstractPyType} is the Java base of Python {@code type}
 * objects. Only {@code PyType} is in the public API and actual
 * implementations further subclass that. This class provides members
 * common to all the implementations, and accessible internally in the
 * run-time system, without being exposed as API from {@code PyType}
 * itself.
 */
public abstract class AbstractPyType extends Representation
        implements Crafted {

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
     * The real dictionary of the type is always an ordered {@code Map}.
     * Internally names are stored as {@code String} for speed and
     * accessed via {@link #lookup(String)}.
     */
    private final Map<String, Object> _dict;

    /**
     * Read-only view of the dictionary of the type, always an ordered
     * {@code Map}. It is exposed to Python through a
     * {@code mappingproxy} that renders it a read-only
     * {@code dict}-like object.
     */
    protected final Map<Object, Object> dict;

    /**
     * Base constructor of type objects. We establish values for members
     * common to the several {@link PyType} implementations.
     *
     * @param name of the type (final). May include package name.
     * @param javaType of instances or {@code null}.
     * @param bases array of the bases (as in a class definition).
     */
    protected AbstractPyType(String name, Class<?> javaType,
            PyType[] bases) {
        super(javaType);
        /*
         * These assertions mainly check our assumptions about the needs
         * of sub-types. They are retained only in testing.
         */
        assert name != null;
        assert javaType != null || this instanceof AdoptiveType;
        assert bases != null;

        this.name = name;
        this.bases = bases;
        this.base = bases.length > 0 ? bases[0] : null;

        this._dict = new LinkedHashMap<>();
        // XXX Ought to be a mappingproxy?
        this.dict = Collections.unmodifiableMap(this._dict);
    }

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
     * The dictionary of the {@code type} in a read-only view.
     *
     * @return dictionary of the {@code type} in a read-only view.
     */
    // @Getter("__dict__")
    public final Map<Object, Object> getDict() { return dict; }

    /**
     * Look for a name, returning the entry directly from the first
     * dictionary along the MRO containing key {@code name}. This may be
     * a descriptor, but no {@code __get__} takes place on it: the
     * descriptor itself will be returned. This method does not throw an
     * exception if the name is not found, but returns {@code null} like
     * a {@code Map.get}
     *
     * @param name to look up, must be exactly a {@code str}
     * @return dictionary entry or null
     */
    // Compare CPython _PyType_Lookup in typeobject.c
    // and find_name_in_mro in typeobject.c
    Object lookup(String name) {

        /*
         * CPython wraps this in a cache keyed by (type, name) and
         * sensitive to the "version" of this type. (Version changes
         * when any change occurs, even in a super-class, that would
         * alter the result of a look-up.) We do not reproduce that at
         * present.
         */

        // CPython checks here to see in this type is "ready".
        // Could we be "not ready" in some loop of types?

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
     * Extension point class for {@link PyType}. <i>Instances</i> (in
     * Java) of this class represent {@code instances} (in Python) of a
     * metaclass. Given the program text: <pre>
     * class Meta(type): pass
     * class MyClass(metaclass=Meta): pass
     * </pre>
     * <p>
     * {@code MyClass} will be an instance of this class (but
     * {@code Meta} will not).
     */
    public final class Derived extends PyType {

        // TODO expose as __class__
        private PyType type;

        /**
         * Construct an instance of a proper subclass of {@code type}.
         * This is the constructor that creates a type object
         * representing a class defined with the {@code metaclass}
         * keyword argument, which becomes the actual type of this type.
         *
         * @param metaclass actual Python type of this {@code type}
         *     object (a proper subclass of {@code type})
         * @param name of the type (fully qualified)
         * @param javaType implementing Python instances of the type
         * @param bases of the new type
         */
        protected Derived(PyType metaclass, String name,
                Class<?> javaType, PyType[] bases) {
            super(name, javaType, bases);
        }

        @Override
        public PyType getType() { return type; }

        public Object getSlot(int i) {
            throw new MissingFeature("metaclass __slots__");
        }

        public void setSlot(int i, Object value) {
            throw new MissingFeature("metaclass __slots__");
        }

        @Override
        public PyType pythonType(Object x) {
            throw new MissingFeature("metaclass pythonType()");
        }
    }
}
