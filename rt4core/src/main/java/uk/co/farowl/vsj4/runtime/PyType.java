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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.kernel.BaseType;
import uk.co.farowl.vsj4.runtime.kernel.KernelType;
import uk.co.farowl.vsj4.runtime.kernel.KernelTypeFlag;
import uk.co.farowl.vsj4.runtime.kernel.MROCalculator;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.runtime.kernel.TypeFactory.Clash;
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
public abstract sealed class PyType extends KernelType
        implements WithClass, FastCall permits BaseType {

    /** Logger for (the public face of) the type system. */
    static final Logger logger = LoggerFactory.getLogger(PyType.class);

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

    /**
     * Return the name of the type.
     *
     * @return the name of the type
     */
    @Override
    public String getName() { return name; }

    /**
     * A copy of the sequence of bases specified for the type,
     * essentially {@code __bases__}.
     *
     * @return the sequence of bases
     */
    @Override
    public PyType[] getBases() { return bases.clone(); }

    /**
     * Return the (best) base type of this type, essentially
     * {@code __base__}. In the case of single inheritance, the choice
     * is obvious. In multiple inheritance, Python makes a somewhat
     * subtle choice. requires a particular care.
     *
     * @return the "best base" type of this type
     */
    @Override
    public PyType getBase() { return base; }

    /**
     * Return a copy of the MRO of this type.
     *
     * @return a copy of the MRO of this type
     */
    @Override
    public abstract PyType[] getMRO();

    @Override
    public PyType getType() { return PyType.TYPE(); }

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
    @Override
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
    @Override
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
    @Override
    public abstract Object lookup(String name);

    /**
     * Determine (or create if necessary) the {@link Representation} for
     * the given object. The representation is found (in the type
     * registry) from the Java class of the argument.
     *
     * @param o for which a {@code Representation} is required
     * @return the {@code Representation}
     */
    static Representation getRepresentation(Object o) {
        return TypeSystem.registry.get(o.getClass());
    }

    /**
     * The Python {@code type} object. The type objects of many built-in
     * types are available as a static final field {@code TYPE}. For
     * technical reasons, we have to use a static method to get the
     * value.
     *
     * @return The {@code type} object.
     */
    public static final PyType TYPE() {
        /*
         * The type object "type" is created deep in the type system
         * with the type factory itself, as might be expected. We
         * carefully avoid calling this method from that constructor,
         * but once TypeSystem.factory has been assigned, then the
         * returned type object is guaranteed to be at least Java ready.
         */
        return TypeSystem.TYPE;
    }

    /**
     * Determine (or create if necessary) the Python type for the given
     * object.
     *
     * @param o for which a type is required
     * @return the type
     */
    public static PyType of(Object o) {
        Representation rep = TypeSystem.registry.get(o.getClass());
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
            return TypeSystem.factory.fromSpec(spec);
        } catch (Clash clash) {
            logger.atError().log(clash.toString());
            throw new InterpreterError(clash);
        }
    }

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
    @Override
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
    @Override
    public boolean checkExact(Object o) { return PyType.of(o) == this; }

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
     * Return true if and only if this is a mutable type. The attributes
     * of a mutable type may be changed, although it will manage that
     * change according to rules of its own. An immutable type object
     * does not allow attribute assignment: the value of an attribute
     * once observed remains valid for the lifetime of the run time
     * system.
     *
     * @return {@code true} iff this is a mutable type
     */
    @Override
    public abstract boolean isMutable();

    /**
     * Fast check that an object of this type is a sequence, defined as
     * not a subclass of {@code dict} and defining {@code __getitem__}.
     *
     * @return target is a sequence
     */
    // Compare CPython PySequence_Check (on instance) in abstract.c
    @Override
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
    @Override
    public boolean isIterable() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_ITER);
    }

    /**
     * Fast check that an object of this type is an iterator (defines
     * {@code __next__}).
     *
     * @return target is a an iterator
     */
    @Override
    public boolean isIterator() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_NEXT);
    }

    /**
     * Fast check that an object of this type is a descriptor (defines
     * {@code __get__}).
     *
     * @return target is a descriptor
     */
    @Override
    public boolean isDescr() {
        return kernelFeatures.contains(KernelTypeFlag.HAS_GET);
    }

    /**
     * Fast check that an object of this type is a data descriptor
     * (defines {@code __set__} or {@code __delete__}).
     *
     * @return target is a data descriptor
     */
    @Override
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
    @Override
    public boolean isMethodDescr() {
        return features.contains(TypeFlag.METHOD_DESCR);
    }

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
    public abstract boolean isSubTypeOf(PyType b);

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
    @Override
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
    @Override
    public abstract ConstructorAndHandle constructor(Class<?>... param);

}
