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

import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * A specification for a Python type that acts as a complex argument to
 * {@link PyType#fromSpec(TypeSpec)}. A Java class intended as the
 * definition of a Python object creates one of these data structures
 * during static initialisation, configuring it using the mutators. A
 * fluent interface makes this configuration readable as a single long
 * statement.
 */
public class TypeSpec {

    /** If {@code true}, accept no further specification actions. */
    private boolean frozen = false;

    /**
     * Name of the class being specified. It may begin with the dotted
     * package name.
     */
    final String name;

    /** Delegated authorisation to resolve names. */
    private final Lookup lookup;

    /**
     * Type features, a subset of the type flags, that in turn have
     * similar semantics to similarly-named CPython type flags.
     */
    private EnumSet<Feature> features =
            EnumSet.of(Feature.INSTANTIABLE);

    /**
     * A collection of additional classes in which to look up
     * implementations of methods (not fields) composing the type. See
     * {@link #methods(Class)}.
     */
    private List<Class<?>> methodImpls = new LinkedList<>();

    /**
     * The class that will be used to represent instances of the Python
     * type. See {@link #canonical(Class)}. It may be {@code null} in a
     * type that adopts all its representations.
     */
    private Class<?> canonical;

    /**
     * If {@code true}, there is definitely no canonical class; if
     * {@code false} one has either been specified or should be
     * inferred.
     */
    private boolean noCanonical = false;

    /**
     * The class that will be used to represent instances of Python
     * subclasses. See {@link #extendAt(Class)}.
     */
    private Class<? extends ExtensionPoint> extensionPoint;

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
     * methods or {@code null}. See {@link #binops(Class)}.
     */
    private Class<?> binopClass;

    /**
     * A collection of classes that are allowed to appear as the "other"
     * parameter in binary operations, in addition to
     * {@link #canonical}, {@link #adopted} and {@link #accepted}. The
     * main use for this is to allow efficient mixed {@code float} and
     * {@code int} operations.
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
     * Create (begin) a specification for a Python {@code type}. This is
     * the beginning normally made by built-in classes in their static
     * initialisation.
     * <p>
     * The caller supplies a {@link Lookup} object which must grant
     * sufficient access to implementing class(es), for fields and
     * method definitions to be exposed reflectively. This makes it the
     * <i>defining</i> class. In simple cases, lookup is created in the
     * single Java class that represents the Python type and defines its
     * methods.
     * <p>
     *
     * {@link PyType#fromSpec(TypeSpec)} will interrogate the
     * implementation class reflectively to discover attributes the type
     * should have, and will form type dictionary entries with
     * {@link MethodHandle}s or {@link VarHandle}s on qualifying
     * members. An implementation class may declare methods and fields
     * as {@code private}, and annotate them to be exposed to Python, as
     * long as the lookup object provided to the {@code TypeSpec}
     * confers the right to access them.
     * <p>
     * A {@code TypeSpec} given private or package access to members
     * should not be passed to untrusted code. {@code PyType} does not
     * hold onto the {@code TypeSpec} after completing the type object.
     * <p>
     * Additional classes may be given containing the implementation and
     * the lookup classes (see {code Lookup.lookupClass()}) to be
     * different from the caller. Usually they are the same.
     *
     * @param name of the type
     * @param lookup authorisation to access {@code implClass}
     */
    public TypeSpec(String name, Lookup lookup) {
        this.name = name;
        this.lookup = lookup;
        methodImpls.add(lookup.lookupClass());
    }

    /**
     * Mark the specification as complete. (No further mutation actions
     * are possible and certain internal normalisations will occur.)
     * This is to guard against certain sequencing errors.
     *
     * @return {@code this}
     */
    public TypeSpec freeze() {
        /*
         * If we are not frozen yet, it means we have yet to finalise
         * the canonical representation and extension point.
         */
        if (frozen == false) {
            /*
             * The substitution of default values is messy here so that
             * specification can be simple where we create one. Setting
             * frozen stops further change to the specification.
             */
            frozen = true;

            /*
             * The lookup class may be an extension point, if eligible
             * and a different one was not explicitly specified.
             */
            boolean LUisEP = extensionPointIsLookupClass();

            if (LUisEP) {
                /*
                 * Assumption: the lookup class is never an
                 * ExtensonPoint "accidentally".
                 */
                features.add(Feature.BASETYPE);
            } else {
                /*
                 * The lookup class is not an extension point but it
                 * might be the canonical class.
                 */
                canonicalIsLookupClass();
            }

            /*
             * Form a list of classes ordered most to least specific. We
             * treat duplicates as errors.
             */
            int adoptedIndex = 0, acceptedIndex = 0;
            if (canonical != null) {
                classes.add(canonical);
                adoptedIndex = acceptedIndex = 1;
            }

            for (Class<?> c : adopted) {
                if (orderedAdd(classes, c) == false) {
                    throw repeatError("adopt", c);
                }
                acceptedIndex++;
            }

            for (Class<?> c : accepted) {
                if (orderedAdd(classes, c) == false) {
                    throw repeatError("accept", c);
                }
            }

            // Now replace adopted and accepted
            adopted = classes.subList(adoptedIndex, acceptedIndex);
            accepted = classes.subList(acceptedIndex, classes.size());

            // FIXME process binopOthers to extended array

            // Checks for various specification errors.
            if (canonical == null) {
                if (adopted.isEmpty()) {
                    throw specError(NO_CANONICAL_OR_ADOPTED);
                }
                if (!adopted.get(0).isAssignableFrom(extensionPoint)) {
                    throw specError(EP_NOT_SUBCLASS);
                }
            } else if (extensionPoint != null) {
                if (!canonical.isAssignableFrom(extensionPoint)) {
                    throw specError(EP_NOT_SUBCLASS);
                }
            } else {
                if (features.contains(Feature.BASETYPE)) {
                    throw specError("BASETYPE but no extension point");
                }
            }
        }

        return this;
    }

    /**
     * Assign the lookup class to {@link #extensionPoint} if
     * appropriate.
     *
     * @return whether the assignment was made
     */
    @SuppressWarnings("unchecked")
    private boolean extensionPointIsLookupClass() {
        Class<?> lu = lookup.lookupClass();
        if (!ExtensionPoint.class.isAssignableFrom(lu)) {
            // Not possible (so not intended).
            return false;
        }
        if (extensionPoint != null && extensionPoint != lu) {
            // Intended, but also contradicted by extendFrom()
            throw specError(BOTH_LOOKUP_AND_ANOTHER_EP);
        }
        extensionPoint = (Class<? extends ExtensionPoint>)lu;
        return true;
    }

    /**
     * Assign the lookup class to {@link #canonical} if appropriate.
     *
     * @return whether the assignment was made
     */
    private boolean canonicalIsLookupClass() {
        Class<?> lu = lookup.lookupClass();
        if (noCanonical || canonical != null) {
            // We don't want one or already chose one.
            return false;
        }
        // Lookup class is intended as a representation
        canonical = lu;
        return true;
    }

    /** Check that {@link #freeze()} has not yet been called. */
    private void checkNotFrozen() {
        if (frozen) { specError("specification changed after frozen"); }
    }

    /**
     * Name of the class being specified. It may begin with the dotted
     * package name.
     *
     * @return name specified for the type.
     */
    public String getName() { return name; }

    /**
     * Type features, a subset of the type flags, that in turn have
     * similar semantics to similarly-named CPython type flags.
     *
     * @return the features
     */
    public EnumSet<Feature> getFeatures() { return features; }

    /**
     * The class that will be used to represent instances of the Python
     * type.
     * <p>
     * Passing the special value {@code null} indicates the type has no
     * canonical representation. (It must be given at last one adopted
     * representation via {@link #adopt(Class...)}).
     *
     * @param klass represents instances of the type (or {@code null})
     * @return {@code this}
     */
    public TypeSpec canonical(Class<?> klass) {
        if (klass == null) {
            if (noCanonical) { throw repeatError("canonical(null)"); }
            noCanonical = true;
        } else if (canonical != null) {
            throw repeatError("canonical representation");
        }
        this.canonical = klass;
        return this;
    }

    /**
     * Get the canonical class (or {@code null} if there isn't one).
     *
     * @return a copy of all the operand classes
     */
    public Class<?> getCanonical() { return canonical; }

    /**
     * The class that will be used to represent instances of Python
     * subclasses of the type being specified (or {@code null}). This is
     * known as the <i>extension point</i> class.
     * <p>
     * This class must be acceptable (in Java) as the {@code self}
     * argument of methods of the base type. In most cases, a
     * "canonical" Java class has been created (and identified as a
     * representation), and the extension point class is a sub-class of
     * it. Another possibility is that the defining class is the
     * extension point class, and is not a representation. If no
     * extension point class is specified, no Python subclasses are
     * possible.
     *
     * @param klass represents Python sub-classes
     * @return {@code this}
     */
    public TypeSpec extendAt(Class<? extends ExtensionPoint> klass) {
        if (this.extensionPoint != null) {
            throw repeatError("extension point");
        } else {
            if (klass != null) { features.add(Feature.BASETYPE); }
            this.extensionPoint = klass;
        }
        return this;
    }

    /**
     * The class that will be used to represent instances of Python
     * subclasses. See {@link #extendAt(Class)}.
     *
     * @return the extending class for the type
     */
    public Class<? extends ExtensionPoint> getExtensionPoint() {
        return extensionPoint;
    }

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
            if (orderedAdd(adopted, c) == false) {
                throw repeatError(
                        "duplicate adopt(" + c.getTypeName() + ")");
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
     * canonical and adopted representations. The use for this is with
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
            if (orderedAdd(accepted, c) == false) {
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
            throw repeatError("base " + base.getName());
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
     * Return the accumulated list of bases. If no bases were added, the
     * result is empty (to be interpreted as just {@code [object]}.
     * (We can't make that substitution here because {@code TypeSpec} has to work before {@code object} is published.)
     *
     * @return the bases of this type
     */
    public List<PyType> getBases() {
        return Collections.unmodifiableList(bases);
    }

    /**
     * Add a feature to the type (becomes a type flag).
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
     * Add features to the type (become a type flags).
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
     * Specify that the Python type being specified will be represented
     * by a an instance of this Python sub-class of {@code type}, i.e.
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
     * Provide an additional class in which to look for the
     * implementation of the methods. By default, the lookup class given
     * in the constructor is searched. A separate class is useful when
     * the method definitions are generated by a script, as for types
     * that admit multiple realisations in Java.
     *
     * @param c additional class defining methods
     * @return {@code this}
     */
    public TypeSpec methodImpl(Class<?> c) {
        checkNotFrozen();
        methodImpls.add(c);
        return this;
    }

    /**
     * Provide additional classes in which to look for the
     * implementation of the methods. This is just
     * {@link #methodImpl(Class)} repeated.
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
     * Get the classes additionally defining methods for the type. See
     * {@link #methodImpls(Class...)}.
     *
     * @return classes additionally defining methods for the type
     */
    List<Class<?>> getMethodImpls() { return methodImpls; }

    /**
     * Set the class in which to look up binary class-specific
     * operations, for example {@code __rsub__(MyObject, Integer)}. Such
     * signatures are used in call sites.
     * <p>
     * Types may ignore this technique if the designer is content with a
     * {@code __rsub__(MyObject, Object)} that coerces its right-hand
     * argument on each call. (This method has to exist to satisfy the
     * Python data model.) The method may be defined in the
     * {@link #canonical(Class) canonical} class, or
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
     * Get all the operand classes for the type, in order, the canonical
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
        String fmt = "'%s' %s %s lookup=%s";
        return String.format(fmt, name, bases, features,
                lookup.lookupClass().getSimpleName());
    }

    private static List<Class<?>> EMPTY = Collections.emptyList();
    private static final String BOTH_LOOKUP_AND_ANOTHER_EP =
            "both lookup and another class are extension points";
    private static final String EP_NOT_SUBCLASS =
            "extension point not subclass of representation";
    private static final String NO_CANONICAL_OR_ADOPTED =
            "no canonical or adopted representation";

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
     * @return {@code true} if added, {@code false} if a duplicate.
     */
    private static boolean orderedAdd(List<Class<?>> list, Class<?> c) {
        var i = list.listIterator();
        while (i.hasNext()) {
            Class<?> x = i.next();
            if (x.isAssignableFrom(c)) {
                if (x.equals(c)) {
                    // Duplicate: do not add.
                    return false;
                }
                // x is less specific than c: insert c before x.
                i.previous();
                i.add(c);
                return true;
            }
        }
        // There is no next() so add means append.
        i.add(c);
        return true;
    }

    /**
     * Construct an {@link InterpreterError} along the lines "[err]
     * while defining '[name]'."
     *
     * @param err qualifying the error
     * @return to throw
     */
    private InterpreterError specError(String err) {
        StringBuilder sb = new StringBuilder(100);
        sb.append(err).append(" while defining '").append(name)
                .append("'.");
        return new InterpreterError(sb.toString());
    }

    /**
     * Construct an {@link InterpreterError} along the lines "repeat
     * [err] while defining '[name]'."
     *
     * @param thing qualifying the error
     * @return to throw
     */
    private InterpreterError repeatError(String thing) {
        return specError("repeat " + thing + "specified");
    }

    /**
     * Construct an {@link InterpreterError} along the lines "repeat
     * [method]([c]) while defining '[name]'."
     *
     * @param method naming the method called
     * @param c the class being added
     * @return to throw
     */
    private InterpreterError repeatError(String method, Class<?> c) {
        String msg = String.format("repeat %s(%s) specified", method,
                c.getTypeName());
        return specError(msg);
    }

}
