// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.VarHandle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedList;
import java.util.List;

import uk.co.farowl.vsj4.runtime.kernel.SimpleType;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.MissingFeature;

/**
 * A specification for a Python type that acts as a complex argument to
 * {@link PyType#fromSpec(TypeSpec)}. A Java class intended as the
 * definition of a Python object creates one of these data structures
 * during static initialisation, configuring it using the mutators. A
 * fluent interface makes this configuration readable as a single long
 * statement. The normal idiom is:<pre>
 * public static PyType TYPE = PyType.fromSpec(
 *    new TypeSpec("Example", MethodHandles.lookup()));
 * </pre> The Python name of the class being specified may begin with
 * the dotted (Python) package name.
 * <p>
 * Recall that {@code MethodHandles.lookup} is context sensitive: it
 * captures the identity of the class from which it was called, in the
 * {@code Lookup} it returns. In the example, this class becomes the
 * (primary) Java representation class for instances of the type, and
 * the source of fields and method definitions. It is possible to adjust
 * this behaviour though choice of constructor and mutators on the
 * specification before {@code PyType.fromSpec} reads it.
 */
public class TypeSpec extends NamedSpec {

    /** Delegated authorisation to resolve names. */
    private final Lookup lookup;

    /**
     * If {@code true} the class creating the {@code Lookup} object will
     * be treated as the primary representation and examined as a method
     * implementation class. (This is frequently the case.) If
     * {@code false}, the mutators {@link #primary} and optionally
     * {@link #methodImpl(Class)} must be used to specify those
     * explicitly. See {@link #TypeSpec(String, Lookup, boolean)}.
     */
    private final boolean defaultToLookup;

    /**
     * Type features, a subset of the type flags, that in turn have
     * similar semantics to similarly-named CPython type flags.
     */
    private EnumSet<Feature> features =
            EnumSet.of(Feature.INSTANTIABLE);

    /** Documentation string for the type. */
    private String doc;

    /**
     * A collection of additional classes in which to look up
     * implementations of methods (not fields) composing the type. See
     * {@link #methodImpl(Class)}.
     */
    private List<Class<?>> methodImpls = new LinkedList<>();

    /**
     * The class that will be the base (in Java) of classes that
     * represent the instances subclasses (in Python) of the type being
     * defined. Methods defined by the type must be able to receive
     * instances of the specified class as their the {@code self}
     * argument.
     *
     * See {@link #canonicalBase(Class)}.
     *
     * It may be {@code null} in a type that adopts all its
     * representations.
     */
    private Class<?> canonicalBase;

    /**
     * The primary class that will represent the instances of the type
     * being defined. Methods defined by the type must be able to
     * receive instances of the specified class as their the
     * {@code self} argument.
     *
     * See {@link #primary(Class)}.
     *
     * It may be {@code null} in a type that adopts all its
     * representations.
     */
    private Class<?> primary;

    /**
     * A collection of classes that are adopted representations for the
     * type being defined.
     */
    private List<Class<?>> adopted = EMPTY;

    /**
     * A collection of classes that are accepted representations for the
     * type being defined.
     */
    private List<Class<?>> accepted = EMPTY;

    /**
     * Additional class in which to look up binary class-specific
     * methods or {@code null}. See {@link #binopImpl(Class)}.
     */
    private Class<?> binopClass;

    /**
     * A collection of classes that are allowed to appear as the "other"
     * parameter in binary operations, in addition to
     * {@link #canonicalBase}, {@link #adopted} and {@link #accepted}.
     * The main use for this is to allow efficient mixed {@code float}
     * and {@code int} operations.
     */
    private List<Class<?>> binopOthers = EMPTY;

    /**
     * The Python type being specified may be represented by a Python
     * sub-class of {@code type}, i.e. something other than
     * {@link PyType#TYPE}. This will be represented by a sub-class of
     * {@link PyType}.
     */
    private PyType metaclass;

    /**
     * Python types that are bases of the type being specified. For
     * 'object', uniquely, this is empty.
     */
    private final List<PyType> bases = new LinkedList<>();

    /**
     * Classes that must be accepted as the {@code self} argument to
     * methods.
     */
    private final List<Class<?>> classes = new ArrayList<>();

    /**
     * Names of slots (fields) in the representation of the type being
     * specified. There is a difference between leaving this at null
     * (instances will get a dictionary) and an empty list (instances
     * will not get a dictionary, but define no slots).
     */
    private List<String> slots = null;

    /**
     * Create (begin) a specification for a Python {@code type}. This is
     * the beginning normally made by types defined in Java, during the
     * static initialisation of their defining class. The Python name of
     * the class being specified may begin with the dotted (Python)
     * package name.
     * <p>
     * The caller supplies a {@code Lookup} object which must grant
     * sufficient access to implementing class(es) for fields and method
     * definitions to be exposed reflectively. Recall that
     * {@code MethodHandles.lookup} is context sensitive: it captures
     * the identity of the class from which it was called, in the
     * {@code Lookup} it returns. By default, this class becomes the
     * (primary) Java representation class for instances of the type,
     * and the source of fields and method definitions.
     * <p>
     * It is possible to specify a different primary representation
     * class and other sources using methods of this class to mutate the
     * specification. It is possible to prevent the lookup class being a
     * source of methods and field accessors using the alternative
     * constructor {@link #TypeSpec(String, Lookup, boolean)
     * TypeSpec(String, Lookup, boolean)}.
     * <p>
     * {@link PyType#fromSpec(TypeSpec)} will interrogate the
     * implementation classes reflectively to discover attributes the
     * type should have, and will form type dictionary entries with
     * {@link MethodHandle}s or {@link VarHandle}s on qualifying
     * members.
     * <p>
     * A {@code TypeSpec} given private or package access to members
     * should not be passed to untrusted code. {@code PyType} does not
     * hold onto the {@code TypeSpec} after completing the type object.
     *
     * @param name of the type being specified (may be dotted name)
     * @param lookup authorisation to access {@code implClass}
     */
    public TypeSpec(String name, Lookup lookup) {
        this(name, lookup, true);
    }

    /**
     * Create (begin) a specification for a Python {@code type}. This is
     * identical to {@link #TypeSpec(String, Lookup)}, except that the
     * class creating the {@code Lookup} object will not be treated as
     * the primary representation or examined as a method implementation
     * class. The mutators {@link #primary} and optionally
     * {@link #methodImpl(Class)} must be used to specify those
     * explicitly.
     *
     * @param name of the type being specified (may be dotted name)
     * @param lookup authorisation to access {@code implClass}
     * @param defaultToLookup if false, do not look for methods in the
     *     lookup class itself
     */
    public TypeSpec(String name, Lookup lookup,
            boolean defaultToLookup) {
        super(name);
        this.lookup = lookup;
        this.defaultToLookup = defaultToLookup;
        if (defaultToLookup) { methodImpls.add(lookup.lookupClass()); }
    }

    /**
     * Mark the specification as complete. (No further mutations are
     * possible.) Certain internal derivations are made and consistency
     * is checked so that the specification easier to use.
     *
     * @return {@code this}
     * @throws InterpreterError if inconsistent in some way
     */
    @Override
    public TypeSpec freeze() throws InterpreterError {
        /*
         * If we are not frozen yet, it means we have yet to finalise
         * the canonical base representation and extension point.
         */
        if (frozen == false) {
            /*
             * The substitution of default values is messy here so that
             * specification can be simple where we create one. Setting
             * frozen stops further change to the specification.
             */
            frozen = true;

            // We need to decide:
            // Primary: javaClass in the type.
            // Canonical: the Java base of Python subclasses.
            // MethodImpls: where we will look up methods.

            // The primary class defaults to the lookup class
            if (primary == null) {
                if (defaultToLookup) {
                    primary = lookup.lookupClass();
                } else {
                    throw specError(PRIMARY_NOT_GIVEN);
                }
            }

            // The canonical base defaults to the primary class
            if (canonicalBase == null) {
                canonicalBase = primary;
            } else if (!primary.isAssignableFrom(canonicalBase)) {
                // It must be acceptable as a self-argument
                throw specError(CANONICAL_INCONSISTENT,
                        canonicalBase.getSimpleName(),
                        primary.getSimpleName());
            }

            /*
             * Form a list of classes ordered most to least specific. We
             * treat duplicates as errors. The partition of the list is
             * [primary|adopted|accepted].
             */
            int adoptedIndex = 1, acceptedIndex = 1;
            classes.add(primary);

            for (Class<?> c : adopted) {
                // Add adopted class c
                int index = orderedAdd(classes, c);
                // Check for rejection or ordering before primary
                if (index < 0) {
                    throw repeatError("adopt", c);
                } else if (index < adoptedIndex) {
                    throw specError(SUBCLASS_PRIMARY, "adopted", c,
                            primary);
                }
                acceptedIndex++;
            }

            for (Class<?> c : accepted) {
                // Add accepted class c
                int index = orderedAdd(classes, c);
                // Check for rejection or ordering before adopted
                if (index < 0) {
                    throw repeatError("accept", c);
                } else if (index < acceptedIndex) {
                    throw specError(SUBCLASS_PRIMARY, "accepted", c,
                            primary);
                }
            }

            // Now update adopted and accepted lists
            adopted = classes.subList(adoptedIndex, acceptedIndex);
            accepted = classes.subList(acceptedIndex, classes.size());

            // TODO process binopOthers to extended array
        }

        return this;
    }

    private static final String PRIMARY_NOT_GIVEN =
            "no primary representation was specified";
    private static final String CANONICAL_INCONSISTENT =
            "Canonical base %s inconsistent with primary %s";
    private static final String SUBCLASS_PRIMARY =
            "%s %s is subclass of primary %s";

    /**
     * Name of the class being specified. It may begin with the dotted
     * package name.
     *
     * @return name specified for the type.
     */
    @Override
    public String getName() { return super.getName(); }

    /**
     * The {@code Lookup} object provided in the constructor, which
     * represents a delegated authority to access methods and fields of
     * the defining classes.
     *
     * @return lookup specified in construction.
     */
    public Lookup getLookup() { return lookup; }

    /**
     * Get the type features specified with {@link #add(Feature)}.
     * {@code Feature} flags have similar semantics to similarly-named
     * CPython type flags.
     *
     * @return the features
     */
    public EnumSet<Feature> getFeatures() { return features; }

    /**
     * Return the documentation string for the type.
     *
     * @return documentation string (or {@code null})
     */
    public String getDoc() { return doc; }

    /**
     * The class that will represent the instances of the type being
     * defined. Methods defined by the type must be able to receive
     * instances of the specified class as their {@code self} argument.
     * <p>
     * The default value is the defining class (obtained from the lookup
     * object in {@link #TypeSpec(String, Lookup)}), so this method need
     * not be called in simple cases. It is useful where a class
     * different from these defaults is the presentation.
     *
     * @param klass is the primary representation class of instances
     * @return {@code this}
     */
    public TypeSpec primary(Class<?> klass) {
        if (primary != null) { throw repeatError("primary class"); }
        this.primary = klass;
        return this;
    }

    /**
     * The class that will be the base (in Java) of classes that
     * represent the instances subclasses (in Python) of the type being
     * defined. Methods defined by the type must be able to receive
     * instances of the specified class as their {@code self} argument.
     * <p>
     * The default value is the same class that is used to represent
     * instances of the Python type, which in turn defaults to the
     * defining class, so this method need not be called in simple
     * cases. It is useful where a class different from these defaults
     * is the base for subclasses. For example, the canonical base of
     * {@code type} (primary class {@link PyType} is {@link SimpleType},
     * to ensure metatypes have that implementation.
     *
     * @param klass is a base for instances of every subclass
     * @return {@code this}
     */
    public TypeSpec canonicalBase(Class<?> klass) {
        if (canonicalBase != null) {
            throw repeatError("canonical base class");
        }
        this.canonicalBase = klass;
        return this;
    }

    /**
     * Get the primary representation class This may be {@code null}
     * before {@link #freeze()} is called.
     *
     * @return primary representation class for instances
     */
    public Class<?> getPrimary() { return primary; }

    /**
     * Get the canonical base class to be the base of representations of
     * subclasses of this type. This may be {@code null} before
     * {@link #freeze()} is called.
     *
     * @return canonical base for instances of every subclass
     */
    public Class<?> getCanonicalBase() { return canonicalBase; }

    /**
     * Specify Java classes that must be adopted by the Python type as
     * representations. The adopted classes will be identified by the
     * run-time as having the Python type specified by this
     * {@code TypeSpec}. Successive calls are cumulative.
     * <p>
     * Each Python instance method must have an implementation in Java
     * applicable for each class that must be accepted as the
     * {@code self} argument. *
     *
     * @apiNote For every instance method {@code m} (including special
     *     methods) on a Python object, and for for every accepted Java
     *     class {@code C}, there must be an implementation
     *     {@code m(D self, ...)} where the {@code self} type {@code D}
     *     is assignable from {@code C}.
     *     <p>
     *     Note that this criterion could be satisfied by defining just
     *     one {@code m(Object self, ...} or by a series of specialised
     *     implementations, or any combination. When it selects an
     *     implementation, the run-time chooses the most specialised
     *     match.
     * @param classes classes to treat as adopted implementations
     * @return {@code this}
     */
    public TypeSpec adopt(Class<?>... classes) {
        checkNotFrozen();
        if (adopted == EMPTY) { adopted = new LinkedList<>(); }
        for (Class<?> c : classes) {
            if (orderedAdd(adopted, c) < 0) {
                throw repeatError("adopt", c);
            }
        }
        return this;
    }

    /**
     * Get the adopted {@code self} classes for the type.
     *
     * @return an unmodifiable view of the adopted {@code self} classes
     */
    public List<Class<?>> getAdopted() {
        return Collections.unmodifiableList(adopted);
    }

    /**
     * Specify additional Java classes that must be accepted as
     * {@code self} arguments in methods of the type, in addition to the
     * primary and adopted representations. The use for this is with
     * Python subclasses defined by unrelated Java classes. The only
     * known example is where {@code int} must accepts a Java
     * {@code Boolean} (Python {@code bool}).
     * <p>
     * Successive calls are cumulative. Classes assignable to existing
     * accepted classes are ignored.
     * <p>
     * Each Python instance method must have an implementation in Java
     * applicable for each class that must be accepted as the
     * {@code self} argument. The note in {@link #adopt(Class...)}
     * formalising this statement applies here too.
     *
     * @param classes to append to the list
     * @return {@code this}
     */
    public TypeSpec accept(Class<?>... classes) {
        checkNotFrozen();
        if (accepted == EMPTY) { accepted = new LinkedList<>(); }
        for (Class<?> c : classes) {
            if (orderedAdd(accepted, c) < 0) {
                throw specError(
                        "duplicate accept(" + c.getTypeName() + ")");
            }
        }
        return this;
    }

    /**
     * Get the accepted {@code self} classes for the type.
     *
     * @return an unmodifiable view of the accepted {@code self} classes
     */
    public List<Class<?>> getAccepted() {
        return Collections.unmodifiableList(accepted);
    }

    /**
     * Specify a base for the type. Successive bases given are
     * cumulative and ordered.
     *
     * @param base to append to the bases
     * @return {@code this}
     */
    public TypeSpec base(PyType base) {
        checkNotFrozen();
        if (base == null) {
            throw specError("base type is null (not yet created?)");
        } else if (bases.indexOf(base) >= 0) {
            throw repeatError("base", base.getName());
        }
        bases.add(base);
        return this;
    }

    /**
     * Specify some bases for the type. Successive bases given are
     * cumulative and ordered.
     *
     * @param bases to append to the bases
     * @return {@code this}
     */
    public TypeSpec bases(PyType... bases) {
        // checkNotFrozen(); // Covered in base()
        for (PyType b : bases) { base(b); }
        return this;
    }

    /**
     * Specify some variable slots for the type, as if with
     * {@code __slots__} in a class definition. Successive slots given
     * are cumulative and ordered.
     *
     * @param slotNames to append to the __slots__
     * @return {@code this}
     */
    public TypeSpec slots(String... slotNames) {
        checkNotFrozen();
        if (slots == null) { slots = new LinkedList<>(); }
        for (String name : slotNames) {
            if (slots.contains(name)) {
                throw repeatError("slot", name);
            }
            slots.add(name);
        }
        return this;
    }

    /**
     * Return the accumulated list of bases. If no bases were added, the
     * result is empty (to be interpreted as just {@code [object]}. (We
     * can't make that substitution here because {@code TypeSpec} has to
     * work before {@code object} is published.)
     *
     * @return the bases of this type
     */
    public List<PyType> getBases() {
        return Collections.unmodifiableList(bases);
    }

    /**
     * Add a feature to the type. {@code Feature} flags have similar
     * semantics to similarly-named CPython type flags.
     *
     * @param f to add to the current features
     * @return {@code this}
     */
    /*
     * XXX Better encapsulation to have methods for things we want to
     * set/unset. Most PyType.flags members should not be manipulated
     * through the TypeSpec and are derived in construction, or as a
     * side effect of setting something else.
     */
    public TypeSpec add(Feature f) {
        checkNotFrozen();
        features.add(f);
        return this;
    }

    /**
     * Add features to the type. {@code Feature} flags have similar
     * semantics to similarly-named CPython type flags.
     *
     * @param f to add to the current features
     * @return {@code this}
     */
    public TypeSpec add(Feature... f) {
        checkNotFrozen();
        for (Feature x : f) { features.add(x); }
        return this;
    }

    /**
     * Remove a feature from the type (usually one present by default).
     *
     * @param f to remove from the current flags
     * @return {@code this}
     */
    public TypeSpec remove(Feature f) {
        checkNotFrozen();
        features.remove(f);
        return this;
    }

    /**
     * Specify the documentation string for the type.
     *
     * @param doc documentation string
     * @return {@code this}
     */
    public TypeSpec doc(String doc) {
        checkNotFrozen();
        this.doc = doc;
        return this;
    }

    /**
     * Specify that the Python type being specified will be represented
     * by an instance of this Python sub-class of {@code type}, that is,
     * something other than {@code PyType.TYPE}.
     *
     * @param metaclass to specify (or null for {@code type}
     * @return {@code this}
     */
    public TypeSpec metaclass(PyType metaclass) {
        checkNotFrozen();
        this.metaclass = metaclass;
        return this;
    }

    /**
     * Provide additional classes in which to look for the
     * implementation of the methods. By default, the lookup class given
     * in the constructor is searched. A separate class is useful when
     * there are many method definitions (e.g. generated by a script),
     * as for types that admit multiple representations in Java.
     *
     * @param cls additional classes defining methods
     * @return {@code this}
     */
    public TypeSpec methodImpls(Class<?>... cls) {
        checkNotFrozen();
        for (Class<?> c : cls) { methodImpls.add(c); }
        return this;
    }

    /**
     * Get the classes defining methods for the type, starting with the
     * lookup class. See {@link #methodImpls(Class...)}.
     *
     * @return classes defining methods for the type
     */
    public List<Class<?>> getMethodImpls() { return methodImpls; }

    /**
     * Set the class in which to look up binary class-specific
     * operations, for example {@code __rsub__(MyObject, Integer)}. Such
     * signatures are used in call sites.
     * <p>
     * Types may ignore this technique if the designer is content with a
     * {@code __rsub__(MyObject, Object)} that coerces its right-hand
     * argument on each call. (This method has to exist to satisfy the
     * Python data model.) The method may be defined in the
     * {@link #canonicalBase(Class) canonical base} class, or
     * {@link #methodImpls(Class...) methodImpls}.
     * <p>
     * A separate class is necessary since the method definition for
     * {@code __rsub__(MyObject, Object)} must sometimes return
     * {@code Py.NotImplemented}, and we should like to avoid checking
     * for that in the call site. Rather, the absence of a definition
     * should indicate that the operation is not defined for a given
     * pair of types Certain built-ins use the technique to speed up
     * call sites in JVM byte code compiled from Python. (The class may
     * be generated by a script.)
     *
     * @param binopClass class with binary class-specific methods
     * @return {@code this}
     */
    TypeSpec binopImpl(Class<?> binopClass) {
        checkNotFrozen();
        this.binopClass = binopClass;
        return this;
    }

    /**
     * Get the class defining binary class-specific operations for the
     * type. See {@link #binopImpl(Class)}. {@code null} if there isn't
     * one.
     *
     * @return class defining binary class-specific operations (or
     *     {@code null})
     */
    Class<?> getBinopClass() { return binopClass; }

    /**
     * Get all the operand classes for the type, in order, the primary
     * at index 0 (if there is one), followed by adopted and accepted
     * classes.
     *
     * @return an unmodifiable view of the {@code self} classes
     */
    List<Class<?>> getSelfClasses() {
        return Collections.unmodifiableList(classes);
    }

    /**
     * Return the meta-class of the type being created, or {@code null}
     * if it is {@code type}.
     *
     * @return the proper meta-class
     */
    public PyType getMetaclass() {
        // XXX is there an always-safe way to return type as an object?
        // XXX Test TypeRegistry readiness?
        return metaclass;
        // return metaclass != null ? metaclass : PyType.TYPE;
    }

    // Something helpful in debugging (__repr__ is different)
    @Override
    public String toString() {
        String fmt = "'%s' %s %s (lookup:%s)";
        return String.format(fmt, name, bases, features,
                lookup.lookupClass().getSimpleName());
    }

    /**
     * In several places, we have to order a list of classes so that we
     * try a more specific match before a less specific one. This method
     * adds classes to a list, preserving the constraint that a more
     * specific class comes before its assignment-compatible rival.
     * Mostly, it just appends {@code c} to the {@code list}. Exact
     * duplicates are not added at all.
     *
     * @param list to add to
     * @param c to add
     * @return place where added or {@code -1} if a duplicate.
     */
    private static int orderedAdd(List<Class<?>> list, Class<?> c) {
        var i = list.listIterator();
        int index = 0;
        while (i.hasNext()) {
            Class<?> x = i.next();  // = list[index]
            if (x.isAssignableFrom(c)) {
                if (x.equals(c)) {
                    // Duplicate: do not add.
                    return -1;
                }
                // x is less specific than c: insert c before x.
                i.previous();
                i.add(c);
                return index;
            }
            index++;
        }
        // There is no next() so add means append.
        i.add(c);
        return index;
    }
}
