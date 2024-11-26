// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.O;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import uk.co.farowl.vsj4.runtime.MethodDescriptor;
import uk.co.farowl.vsj4.runtime.PyFloat;
import uk.co.farowl.vsj4.runtime.PyLong;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A {@code Representation} provides Python behaviour to a Java object
 * by linking its Java class to essential type information. In many
 * cases, the Java class alone determines the behaviour (the Python
 * type). In cases where instances of the the Java class can represent
 * objects with multiple Python types,
 * {@link Representation#pythonType(Object)} refers to the Python object
 * itself for the actual type.
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
    protected final Class<?> javaClass;

    /**
     * Create a {@code Representation} relating a (base) Java class to a
     * type. Creation of a {@code Representation} does not register the
     * association.
     *
     * @param javaClass the base of classes represented
     */
    protected Representation(Class<?> javaClass) {
        this.javaClass = javaClass;
    }

    /**
     * Get the Python type of the object <i>given that</i> this is the
     * representation object for it. The argument {@code x} is only
     * needed when this is not a {@link Shared} representation:
     * {@code null} may be passed in those cases. A shared
     * representation is not associated with a unique type, so in that
     * case {@code x} is consulted for the type, while a {@code null}
     * returns a {@code null} result.
     *
     * @implSpec If this object is also a type object, it will answer
     *     that it itself is that type. (Do not implement this in
     *     {@code PyType} so that the method does not become API.) An
     *     {@link Adopted} representation knows its {@link AdoptiveType}
     *     directly, while a {@link Shared} representation must consult
     *     the object {@code x}.
     *
     * @param x subject of the enquiry
     * @return {@code type(x)}
     */
    public abstract PyType pythonType(Object x);

    /**
     * Get the specific {@code Representation} of the object <i>given
     * that</i> this is the representation object for its class.
     *
     * @implSpec Override this in the {@link Shared} representation to
     *     return the type. The default implementation returns
     *     {@code this}.
     *
     * @param x subject of the enquiry
     * @return {@code type(x)}
     */
    public Representation unshared(Object x) { return this; }

    /**
     * A base Java class representing instances of the related Python
     * {@code type} associated with this {@code Representation}. If
     * there is more than one Java class <i>associated to this
     * representation</i>, they must all be subclasses in Java of the
     * class returned here.
     *
     * @return base class of the implementation
     */
    public Class<?> javaClass() { return javaClass; }

    /**
     * Return the index of this {@code Representation} in the associated
     * type. Adoptive types support multiple Java representation classes
     * for their instances, and each Representation holds the index for
     * the class(es) associated to it in the registry. For others
     * representations, the index is zero.
     * <p>
     * Each descriptor in the dictionary of that type is able to provide
     * an implementation of the method that applies to a {@code self}
     * with Java class equal to (or a subclass of) {@link #javaClass} of
     * the representation.
     *
     * @implSpec Override this in the {@link Adopted} representation.
     *     The default implementation returns zero.
     *
     * @return index in the type (0 if canonical)
     */
    @SuppressWarnings("static-method")
    public int getIndex() { return 0; }

    /**
     * Get a handle that implements the given special method for the
     * given {@code self}, and which is assignment compatible (in Java)
     * with {@link #javaClass}. The returned handle has the signature
     * required by the particular special method, and is not bound to
     * {@code self}.
     *
     * @deprecated This is questionable now: see
     *     {@link SpecialMethod#generic} and the slot functions.
     */
    // Compare CPython SLOT* macros in typeobject.c
    // FIXME Isn't this superseded by SpecialMethod.slot*() etc..
    @Deprecated
    public MethodHandle handle(SpecialMethod sm, Object self) {
        MethodHandle mh;
        if (sm.cache != null) {
            // XXX What if javaClass isn't expected self class?
            // We wouldn't have cached it, but when was that?
            mh = (MethodHandle)sm.cache.get(this);
        } else {
            // XXX Could in-line getting the type if we specialise.
            PyType type = pythonType(self);
            // XXX Abstract rest as find method we use during caching?
            Object attr = type.lookup(sm.methodName);
            if (attr instanceof MethodDescriptor method) {
                // XXX What if javaClass isn't expected self class?
                mh = method.getHandle(getIndex());
            } else {
                // Return a handle on a call
                mh = null; // method.getWrapped(javaClass);
            }
        }
        assert mh.type().equals(sm.signature.type);
        return mh;
    }

    /**
     * Fast check that the target is exactly a Python {@code int}. We
     * can do this without reference to the object itself, or even the
     * type, since it is deducible from the Java class.
     *
     * @implNote The result may be incorrect during type system
     *     bootstrap.
     *
     * @return target is exactly a Python {@code int}
     */
    public boolean isIntExact() { return this == PyLong.TYPE; }

    /**
     * Fast check that the target is exactly a Python {@code float}. We
     * can do this without reference to the object itself, or even the
     * type, since it is deducible from the Java class.
     *
     * @implNote The result may be incorrect during type system
     *     bootstrap.
     *
     * @return target is exactly a Python {@code float}
     */
    public boolean isFloatExact() { return this == PyFloat.TYPE; }

    /**
     * Fast check that the target is a data descriptor.
     *
     * @return target is a data descriptor
     * @deprecated Only valid when asking a type
     */
    // FIXME Only valid when asking a type
    @Deprecated
    public boolean isDataDescr() { return false; }

    /**
     * Fast check that the target is a method descriptor.
     *
     * @return target is a method descriptor
     * @deprecated Only valid when asking a type
     */
    // FIXME Only valid when asking a type
    @Deprecated
    public boolean isMethodDescr() { return false; }

    // ---------------------------------------------------------------

    /**
     * A {@code Representation} that relates an adopted representation
     * to its {@link AdoptiveType}.
     */
    static class Adopted extends Representation {

        /** The type of which this is an adopted representation. */
        final AdoptiveType type;

        /**
         * Index of this implementation in the type (see
         * {@link AdoptiveType#getAdopted(int)}.
         */
        final int index;

        /**
         * Create a {@code Representation} object associating a Python
         * type with the Java type.
         *
         * @param javaClass implementing it
         * @param type of which this is an accepted implementation
         */
        Adopted(int index, Class<?> javaClass, AdoptiveType type) {
            super(javaClass);
            this.type = type;
            this.index = index;
        }

        @Override
        public AdoptiveType pythonType(Object x) { return type; }

        @Override
        public int getIndex() { return index; }

        @Override
        public String toString() {
            String javaName = javaClass().getSimpleName();
            return javaName + " as " + type.toString();
        }
    }

    /**
     * The {@link Representation} for a Python class defined in Python.
     * Many Python classes may be represented by the same Java class,
     * the actual Python type being indicated by the instance.
     */
    static class Shared extends Representation {

        /**
         * {@code MethodHandle} of type {@code (Object)PyType}, to get
         * the actual Python type of an {@link Object} object.
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
                        .findVirtual(WithClass.class, "getType",
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
         *
         * @param javaType extension point Java class
         */
        Shared(Class<?> javaType) { super(javaType); }

        @Override
        public String toString() {
            return String.format("Shared[%s]",
                    javaClass().getSimpleName());
        }

        @Override
        public PyType pythonType(Object x) {
            if (x instanceof WithClass wcx)
                return wcx.getType();
            else if (x == null) {
                return null;
            } else {
                throw notSharedError(x);
            }
        }

        @Override
        public Representation unshared(Object x) {
            if (x instanceof WithClass wcx)
                return wcx.getType();
            else {
                throw notSharedError(x);
            }
        }

        /**
         * Return an exception reporting that the given object was
         * registered as if implementing a {@link ReplaceableType}, but
         * it cannot be inspected for its type. The {@link TypeFactory}
         * has a bug if it created this {@code Representation}. Or the
         * type system has a bug if it allowed anything else to do so.
         *
         * @param x objectionable object
         * @return to throw
         */
        private InterpreterError notSharedError(Object x) {
            String msg = String.format(
                    "unsharable class %.100s registered as %s",
                    x.getClass().getTypeName(), this.toString());
            return new InterpreterError(msg);
        }
    }

    // TODO Consider encapsulating in getters.

    /** Cache of {@link SpecialMethod#op_repr __repr__} */
    public MethodHandle op_repr;
    /** Cache of {@link SpecialMethod#op_hash __hash__} */
    public MethodHandle op_hash;
    /** Cache of {@link SpecialMethod#op_call __call__} */
    public MethodHandle op_call;
    /** Cache of {@link SpecialMethod#op_str __str__} */
    public MethodHandle op_str;

    /** Cache of {@link SpecialMethod#op_add __add__} */
    public MethodHandle op_add;
    /** Cache of {@link SpecialMethod#op_radd __radd__} */
    public MethodHandle op_radd;

    /** Cache of {@link SpecialMethod#op_neg __neg__} */
    public MethodHandle op_neg;
    /** Cache of {@link SpecialMethod#op_abs __abs__} */
    public MethodHandle op_abs;
    /** Cache of {@link SpecialMethod#op_invert __invert__} */
    public MethodHandle op_invert;

    /** Cache of {@link SpecialMethod#op_int __int__} */
    public MethodHandle op_int;
    /** Cache of {@link SpecialMethod#op_index __index__} */
    public MethodHandle op_index;

    /** Cache of {@link SpecialMethod#op_len __len__} */
    public MethodHandle op_len;
    /** Cache of {@link SpecialMethod#op_getitem __getitem__} */
    public MethodHandle op_getitem;
    /** Cache of {@link SpecialMethod#op_setitem __setitem__} */
    public MethodHandle op_setitem;

    static abstract sealed class Accessor permits SpecialMethod.Util {

    }

}
