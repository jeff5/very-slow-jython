// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.Crafted;
import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.bootstrap.PyFloatImpl;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Adopted;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Shared;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * The {@code TypeFactory} is the home of Python type creation and
 * management. In normal operation, only one instance of
 * {@code TypeFactory} will be created. It will be held statically by
 * the {@link PyType} class. Exceptionally, we create and destroy
 * instances for limited test purposes.
 * <p>
 * The {@code TypeFactory} is kept thread-safe, in a complex reentrant
 * process of type creation, by careful synchronisation that counts the
 * reentrant calls. The design ensures that:
 * <ul>
 * <li>All partially completed work in the factory belongs to the single
 * thread that has taken the lock.</li>
 * <li>All work for the thread is completed by the time the thread
 * finally leaves the factory (and the lock is released).</li>
 * <li>All {@link Representation}s and types created for a thread are
 * published (or discarded in the event of an error) by the time the
 * thread finally leaves the factory.</li>
 * </ul>
 */
public class TypeFactory {

    /** Logger for the type factory. */
    final Logger logger = LoggerFactory.getLogger(TypeFactory.class);

    /**
     * A TypeRegistry in which results, the association of a class with
     * a {@link Representation}, behind which there is always a
     * {@link PyType} will be published.
     */
    private final Registry registry;
    /** The workshop for the factory. */
    private final Workshop workshop;
    /** The (initially partial) type object for 'type'. */
    final SimpleType typeType;
    /** The (initially partial) type object for 'object'. */
    final AdoptiveType objectType;
    /** An empty array of type objects */
    final PyType[] EMPTY_ARRAY;
    /** An empty array of type objects */
    private final PyType[] OBJECT_ONLY;

    /** We count reentrant calls here. */
    private int reentrancyCount;
    /** Used to indent log messages by the reentrancy level. */
    private final Supplier<String> indent = () -> INDENT_RULER
            .substring(0, Math.min(reentrancyCount, 24) * 2);
    private final static String INDENT_RULER = ". ".repeat(24);

    /**
     * Construct a {@code TypeFactory}. Normally this constructor is
     * used exactly once from {@link PyType}. Exceptionally, we create
     * instances for test purposes.
     *
     * @deprecated Do not create a {@code TypeFactory} other than the
     *     one {@code PyType} holds statically.
     */
    @Deprecated
    public TypeFactory() {
        // This is the first thing the run-time system does.
        logger.info("Type factory being created.");

        this.registry = new Registry();
        this.workshop = new Workshop();

        // The corresponding decrement is in createBootstrapTypes().
        this.reentrancyCount = 1;

        /*
         * Create type objects for type and object. We need special
         * constructors because nothing is more bootstrappy than these
         * types.
         */
        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument("object").log();
        this.objectType = new AdoptiveType();

        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument("type").log();
        this.typeType = new SimpleType(objectType);

        /*
         * We need a specification for each type as well, which we
         * create from their type objects, not the other way around, as
         * PyType.fromSpec is not safe until.
         */
        TypeSpec specOfObject = new PrimordialTypeSpec(objectType,
                AbstractPyBaseObject.LOOKUP);
        TypeSpec specOfType =
                new PrimordialTypeSpec(typeType, AbstractPyType.LOOKUP)
                        .extendAt(AbstractPyType.Derived.class);

        /*
         * Add the specifications to the workshop so it can complete the
         * types later with their methods.
         */
        try {
            workshop.add(specOfObject, objectType);
            workshop.add(specOfType, typeType);
        } catch (Clash clash) {
            // If we can't get this far ...
            throw new InterpreterError(clash);
        }

        // This cute re-use also proves 'type' and 'object' exist.
        this.OBJECT_ONLY = typeType.bases;
        assert OBJECT_ONLY.length == 1;
        this.EMPTY_ARRAY = typeType.base.bases;
        assert EMPTY_ARRAY.length == 0;
    }

    /**
     * Return the registry where this factory posts its type and
     * representation information.
     *
     * @return the registry
     */
    public TypeRegistry getRegistry() { return registry; }

    private class Registry extends TypeRegistry {

        Registry() {}

        /**
         * Register the given pairings of Java class and
         * {@link Representation}s for multiple Java classes. Subsequent
         * enquiry through {@link #get(Class)} will yield the given
         * {@code Representation}. This is a one-time action on the
         * registry, affecting the state of the {@code Class} object:
         * the association cannot be changed, but the {@code type}
         * behind it may be mutated (where it allows that). It is an
         * error to attempt to associate a different
         * {@code Representation} with a class already bound in the
         * registry. All registrations in the batch succeed or fail
         * together.
         * <p>
         * <b>Concurrency:1</b> The representations are immediately
         * available to other threads, so they and the type(s) behind
         * them must be fully formed.
         *
         * @param unpublished associations to add to the published map
         * @throws Clash when a representing class is already bound
         */
        synchronized void
                registerAll(Map<Class<?>, Representation> unpublished)
                        throws Clash {
            /*
             * Checks we made in workshop.add should guarantee that no
             * class in the unpublished map is already in the published
             * map. We're going to check it anyway. If this ever fails,
             * something is wrong with the synchronisation that ensures
             * only the thread that built the new entries can add new
             * entries.
             */
            for (Class<?> c : unpublished.keySet()) {
                if (map.containsKey(c)) {
                    Representation old = map.get(c);
                    throw alreadyBoundError(c, old);
                }
                logger.atDebug().setMessage(PUBLISHING_CLASS_REP)
                        .addArgument(indent)
                        .addArgument(() -> c.getTypeName())
                        .addArgument(() -> unpublished.get(c)).log();
            }
            // Safe to do en masse.
            map.putAll(unpublished);
        }

        @Override
        Representation find(Class<?> c) {
            synchronized (TypeFactory.this) {
                /*
                 * This thread has control of the factory, so nothing
                 * can change the published map while we work.
                 */
                Representation r = lookup(c);
                if (r == null) {
                    /*
                     * If the unpublished map is not empty, this had
                     * better be a reentrant call, or there is a bug.
                     */
                    assert workshop.isEmpty() || reentrancyCount > 0;
                    // The caller is a thread re-entering the factory.
                    r = workshop.unpublished.get(c);
                }
                return r;
            }
        }

        @Override
        Representation findOrCreate(Class<?> c) {
            synchronized (TypeFactory.this) {
                assert reentrancyCount == 0;
                assert workshop.isEmpty();
                Representation rep;
                try {
                    /*
                     * The thread has just locked the factory. The
                     * published map may have changed while it was
                     * waiting, so look c up again.
                     */
                    if ((rep = lookup(c)) == null) {
                        rep = _findOrCreate(c);
                    }
                    assert reentrancyCount == 0;

                    workshop.publishAll();
                    return rep;
                } catch (Clash clash) {
                    throw new InterpreterError(clash);
                }
            }
        }

        /**
         * This method must only be called with the factory lock held.
         *
         * @param c class to resolve
         * @return representation object for {@code c} or {@code null}.
         * @throws Clash when a representing class is already bound
         */
        private Representation _findOrCreate(Class<?> c) throws Clash {
            Representation rep = null;
            reentrancyCount += 1;
            logger.atDebug().setMessage(FINDING_REP).addArgument(indent)
                    .addArgument(() -> c.getTypeName()).log();
            /*
             * Ensure c is statically initialised, all its superclasses
             * and interfaces. This may result in reentrant calls to the
             * factory that add c or an ancestor to the workshop, but do
             * not publish anything while reentrancyCount > 0.
             */
            ensureInit(c);

            // See if we can already resolve c to work in progress.
            rep = resolve(c);

            if (rep == null) {
                // Make a type to represent the class.
                // XXX What lookup should be used here?
                TypeSpec spec = new TypeSpec(c.getTypeName(), LOOKUP)
                        .canonical(c);
                rep = fromSpec(spec);
            }

            if (rep == null) {
                // Maybe just log as error/warning
                String fmt = "No representation found for class %s";
                throw new InterpreterError(
                        String.format(fmt, c.getTypeName()));
            }

            // XXX Should we only do this at the top level?
            workshop.exposeWaitingTypes();

            reentrancyCount -= 1;
            return rep;
        }

        /**
         * Find an existing {@link Representation} that may be used for
         * {@code c}, or return {@code null}.
         *
         * The argument {@code c} is a class that has been statically
         * initialised.
         * <p>
         * If {@code c} implements {@code Crafted}, and some superclass
         * of {@code c} is already registered (in the published or
         * unpublished map), the same {@link Representation} will be
         * registered for {@code c} also in the unpublished map.
         * <p>
         * This is the normal case where {@code c} is a subclass of a
         * Java representation {@code d} of a Python type. That
         * representation has already been registered thanks to a call
         * to {@link PyType#fromSpec(TypeSpec)}. {@code c} should then
         * also be a representation of that type. That is, looking up
         * {@code c}, or any superclass up to {@code d}, returns the
         * same {@code Representation} as for {@code d}.
         * <p>
         * In anomalous cases, {@code c} implements {@code Crafted}
         * without extending a registered representation. This is not
         * necessarily an error. The method will return {@code null},
         * and no registration will occur.
         *
         * @param c the given class to resolve
         * @return the representation to which c is registered
         */
        Representation resolve(Class<?> c) {
            Representation r;
            if ((r = find(c)) == null) {
                if (Crafted.class.isAssignableFrom(c)) {
                    // c may be a crafted type that inherits r
                    if ((r = resolve(c.getSuperclass())) != null) {
                        workshop.unpublished.put(c, r);
                    }
                }
            }
            return r;
        }
    }

    /** Lookup object with package visibility. */
    static final Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    /**
     * Construct a type from the given specification. The type object
     * does not retain a reference to the specification, once
     * constructed, so that subsequent alterations have no effect on the
     * {@code PyType}.
     *
     * @param spec specification
     * @return the constructed {@code PyType}
     * @throws Clash when a representing class is already bound
     */
    public synchronized PyType fromSpec(TypeSpec spec) throws Clash {
        /*
         * We are able to make (the right kind of) type object but
         * cannot always guarantee to fill its dictionary. In that case,
         * it would ideally not escape yet, but how?
         */
        reentrancyCount += 1;
        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument(spec.getName()).log();
        // Crate a type and add it to the work in progress.
        PyType type = workshop.addPartialFromSpec(spec);
        reentrancyCount -= 1;
        if (reentrancyCount == 0) {
            /*
             * It is ok to return a partial type to a re-entrant call,
             * and at the same time have work in hand. It stops being ok
             * the moment the we are back at the top-level request. On
             * this return we release the lock: everything we have
             * created becomes accessible to another thread.
             */
            workshop.exposeWaitingTypes();
            workshop.publishAll();
            // Let's hope that emptied the shop ...
            int workshopCount = workshop.tasks.size();
            if (workshopCount != 0) {
                logger.atWarn().setMessage(
                        "Type '{}' finished but {} tasks in factory.")
                        .addArgument(type.getName())
                        .addArgument(workshopCount).log();
            }
        }
        return type;
    }

    /**
     * A special factory method for the type object of {@code type},
     * which has to be hand-constructed, so the usual pattern can't be
     * applied.
     *
     * @return the type
     * @deprecated Use {@link PyType#TYPE} instead. This method is
     *     public only so that {@code PyType} may use it to initialise
     *     that member.
     */
    @Deprecated
    public synchronized PyType typeForType() {
        // Becomes PyType.TYPE.
        return typeType;
    }

    /**
     * Create and complete the bootstrap types. These are the adoptive
     * types and those types required to create descriptors.
     * {@link PyType#TYPE} should have been initialised before this
     * method is called so that types being defined now may refer to it.
     * <p>
     * We include the adoptive types to ensure that each adopted Java
     * class becomes bound to its {@link Representation} before it gets
     * used from Python.
     * <p>
     * This method finishes what the constructor began. The design of
     * {@link PyType} ensures all the steps are called before a type
     * becomes visible outside the thread.
     *
     * @throws Clash when a representing class is already bound
     */
    public synchronized void createBootstrapTypes() throws Clash {
        // Definition classes should be able to assume:
        assert PyType.TYPE == typeType;


        TypeRegistry.ensureInit(PyFloatImpl.class);

        // Give all waiting types their Python nature
        workshop.exposeWaitingTypes();

        /*
         * This reverses reentrancyCount = 1 in the constructor.
         * reentrancyCount doesn't lock anything, but it defers
         * publication, and various checks we can only satisfy when at
         * top level.
         */
        reentrancyCount -= 1;
        assert reentrancyCount == 0;

        // Publish the Python-ready types.
        workshop.publishAll();

        logger.info("Bootstrap types ready.");
    }

    /**
     * Create an error to throw when the an attempt is made to define a
     * mapping from a class to a Representation and the class is already
     * bound.
     *
     * @param c to be bound
     * @param existing binding (from a find)
     * @return to throw
     */
    private Clash alreadyBoundError(Class<?> c,
            Representation existing) {
        // Get the first spec (the top level one).
        Iterator<TypeSpec> i = workshop.tasks.keySet().iterator();
        TypeSpec spec = i.hasNext() ? i.next() : null;
        return new Clash(spec, c, existing);
    }

    /**
     * The type system has to prepare {@code PyType} objects in two
     * stages, sometimes deferring the filling of the dictionary of the
     * type until all the classes in this set have completed their
     * static initialisation in Java and built a {@code PyType}. We have
     * these reasons (at least) for putting a type on the list:
     * <ol>
     * <li>The type is encountered in static initialisation of the type
     * system (e.g. an instance is referenced when initialising
     * {@code Py}).</li>
     * <li>The type has accepted non-canonical implementations we could
     * encounter before the canonical class is loaded.</li>
     * <li>The type must exist for us to create entries in the
     * dictionary of any other type.</li>
     * </ol>
     */
    private class Workshop {
        /**
         * A map from a type specification to the work in progress on
         * that type.
         */
        final Map<TypeSpec, Task> tasks = new LinkedHashMap<>();

        /**
         * Mapping from Java class (the representation or extension
         * point of a type) to the {@link Representation} object, while
         * the type remains work in progress. These will all be added to
         * the mappings published in the {@link Registry} when the
         * top-level type-creation request is complete. Until then, it
         * can be treated as an extension to the public map, but
         * <b>only</b> within the scope of that request and its private
         * transaction.
         */
        final Map<Class<?>, Representation> unpublished =
                new HashMap<>();

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(100);
            for (Entry<TypeSpec, Task> task : tasks.entrySet()) {
                buf.append(task.getKey());
                buf.append('\n');
            }

            for (Entry<Class<?>, Representation> rep : unpublished
                    .entrySet()) {
                buf.append(rep.getKey().getTypeName()).append(" -> ")
                        .append(rep.getValue());
                buf.append('\n');
            }
            return buf.toString();
        }

        /**
         * Determine whether there are unfinished tasks and unpublished
         * representations in the workshop.
         *
         * @return true if there are no unfinished tasks or unpublished
         *     representations.
         */
        boolean isEmpty() {
            return tasks.isEmpty() && unpublished.isEmpty();
        }

        /**
         * Create a partial Python type constructed according to the
         * specification and add it to the work in progress. The type so
         * produced is "Java ready" but not "Python ready": its type
         * dictionary is empty of the descriptors needed to make it
         * work. This is the first phase in the process that ultimately
         * lies behind {@link PyType#fromSpec(TypeSpec)}.
         *
         * @param spec specifying the new type
         * @return the new partial type
         * @throws Clash when a representing class is already bound
         */
        PyType addPartialFromSpec(TypeSpec spec) throws Clash {
            // No further change once we start
            String name = spec.freeze().getName();

            // It has these (potentially > 1) representations:
            Class<?> canonical = spec.getCanonical();
            List<Class<?>> adopted = spec.getAdopted();

            PyType[] bases;
            List<PyType> baseList = spec.getBases();
            if (baseList.isEmpty()) {
                bases = OBJECT_ONLY;
            } else {
                bases = baseList.toArray(new PyType[baseList.size()]);
            }

            PyType newType;

            if (adopted.isEmpty()) {
                /*
                 * The type is not adoptive: the representation is the
                 * canonical class.
                 */
                assert canonical != null;
                SimpleType st = new SimpleType(name, canonical, bases);
                tasks.put(spec, new Task(newType = st, spec));
                addRepresentation(canonical, st);
            } else {
                /*
                 * The type adopts one or more classes: the
                 * representations may include the canonical class.
                 */
                int na = adopted.size();
                AdoptiveType at =
                        new AdoptiveType(name, canonical, na, bases);
                tasks.put(spec, new Task(newType = at, spec));
                if (canonical != null) {
                    addRepresentation(canonical, at);
                }
                for (Class<?> c : adopted) {
                    Adopted r = new Adopted(c, at);
                    at.adopt(r);
                    addRepresentation(c, r);
                }
            }

            // If there is an extension point, add a representation.
            addExtension(spec);
            return newType;
        }

        /**
         * Add an existing partial Python type, assumed consistent with
         * the specification, to the work in progress. The type is "Java
         * ready" but not "Python ready". This method is a variant of
         * {@link #addPartialFromSpec(TypeSpec)} where the type object
         * already exists (quite possibly, only used for {@code type}).
         *
         * @param spec specifying the new type
         * @param st the new type
         * @throws Clash when a representing class is already bound
         */
        void add(TypeSpec spec, SimpleType st) throws Clash {
            // No further change once we start
            Class<?> canonical = spec.freeze().getCanonical();
            /*
             * The type is not adoptive: the representation is the
             * canonical class.
             */
            assert spec.getAdopted().isEmpty();
            assert canonical != null;
            tasks.put(spec, new Task(st, spec));
            addRepresentation(canonical, st);

            // If there is an extension point, add a representation.
            addExtension(spec);
        }

        /**
         * Add an existing partial Python type, assumed consistent with
         * the specification, to the work in progress. The type is "Java
         * ready" but not "Python ready". This method is a variant of
         * {@link #addPartialFromSpec(TypeSpec)} where the type object
         * already exists (quite possibly, only used for
         * {@code object}).
         *
         * @param spec specifying the new type
         * @param at the new type
         * @throws Clash when {@code at} is already bound
         */
        void add(TypeSpec spec, AdoptiveType at) throws Clash {
            // No further change once we start
            Class<?> canonical = spec.freeze().getCanonical();
            tasks.put(spec, new Task(at, spec));
            // Add the canonical representation if there is one.
            if (canonical != null) { addRepresentation(canonical, at); }
            // Add each adopted representation.
            for (int i = 0; i < at.getAdoptedCount(); i++) {
                Adopted r = at.getAdopted(i);
                addRepresentation(r.javaType, r);
            }
            // If there is an extension point, add a representation.
            addExtension(spec);
        }

        /**
         * Add a representation for the extension point class (if there
         * is one) that represents Python subclasses on the new type.
         * Note that there is no type object for the shared
         * representation until a class is defined in Python that uses
         * it as a base, and then a new one each time.
         *
         * @param spec specification for the type
         * @throws Clash when the extension point is already bound
         */
        private void addExtension(TypeSpec spec) throws Clash {
            Class<? extends ExtensionPoint> ep =
                    spec.getExtensionPoint();
            if (ep != null) {
                Shared shared = new Shared(ep);
                addRepresentation(ep, shared);
            }
        }

        /**
         * Add a class representation to the workshop's
         * {@link #unpublished} map during construction of a partial
         * type. The class must not already be mapped in either the
         * published registry or the local unpublished representations.
         * <p>
         * Add the specification to {@link #tasks} before invoking this
         * method or the context will be missing from the error message
         * in the event of a clash.
         *
         * @param c class to enter in unpublished map
         * @param r linking {@code c} to the type
         * @throws Clash when {@code c} is already bound
         */
        private void addRepresentation(Class<?> c, Representation r)
                throws Clash {
            // This is in the course of processing *some* spec
            assert !tasks.isEmpty();
            // Look up c in both published and unpublished maps.
            Representation existing = registry.find(c);
            if (existing != null) {
                throw alreadyBoundError(c, existing);
            }
            unpublished.put(c, r);
        }

        void exposeWaitingTypes() {
            // TODO Add an Exposer to each Task and run it.

            // Each type in tasks is Java ready.
            // Run the exposure process on each.
            // Running exposure involves creating descriptors.
            // Descriptors call PyType.fromSpec from their static init.
            // This happens as a reentrant call.
            // Each new descriptor type will add itself to tasks.
            // Some new descriptor classes will be added to unpublished.
            // Maybe not just descriptors are added (args types?).
            // Iterate tasks carefully because it keeps growing.
            // Continue until every type in tasks has been exposed.
        }

        void publishAll() throws Clash {
            // TODO Publish all representations and empty tasks.
            // Each type in tasks is Python ready.
            // Each unpublished representations relates to a ready type.
            // We can publish the representations.
            // We can clear the tasks.
            registry.registerAll(unpublished);
            unpublished.clear();
            tasks.clear();
        }
    }

    /**
     * A TypeFactory.Task is a partially-built type and its
     * specification. It is created to hold work in progress in the
     * {@link Workshop}, between creation of a "Java ready" type object
     * and completion of it as "Python ready". This is particularly the
     * case with "bootstrap" types, those with adopted representations
     * and the types necessary to expose any type, during initialisation
     * of the type system itself.
     * <p>
     * In principle (we think) any type has somewhere in its interface a
     * type reference or a Java class that gets exposed, which results
     * in a the reentrant request to the factory for that type.
     * (Reentrant here means that while one request is being answered, a
     * new request is made synchronously. An asynchronous request from
     * another thread)
     */
    private static class Task {
        /** The type being built. */
        final PyType type;

        // XXX Do I need the spec if I look it up that way?
        /** Specification for the type being built. */
        final TypeSpec spec;

        /**
         * Classes for which the tasks must all be ready for publication
         * before or with this one.
         */
        final Set<Class<?>> required;

        Task(PyType type, TypeSpec spec) {
            super();
            this.type = type;
            this.spec = spec;
            this.required = new HashSet<>();
        }

        @Override
        public String toString() {
            return String.format("Task[%s]", spec.getName());
        }
    }

    private static final String FINDING_REP =
            "{}Finding representation for '{}'";
    private static final String CREATING_PARTIAL_TYPE =
            "{}Creating partial type for '{}'";
    private static final String PUBLISHING_CLASS_REP =
            "{}Publishing '{}' -> '{}'";

    /**
     * Exception reporting a duplicate {@link Representation} for the
     * given class.
     */
    public static class Clash extends Exception {
        private static final long serialVersionUID = 1L;
        /** Context of the clash. */
        final TypeSpec context;
        /** Class being redefined. */
        final Class<?> klass;
        /** Representation already registered for {@link #klass} */
        final Representation existing;

        /**
         * Create an exception reporting that an attempt was made to
         * register a second {@link Representation} for a class already
         * in the registry.
         *
         * @param context specification being worked
         * @param klass being registered
         * @param existing representation for that class
         */
        Clash(TypeSpec context, Class<?> klass,
                Representation existing) {
            this.context = context;
            this.klass = klass;
            this.existing = existing;
        }

        @Override
        public String getMessage() {
            return String.format(CLASS_ALREADY_BOUND, context.getName(),
                    klass.getTypeName(), existing);
        }

        private static final String CLASS_ALREADY_BOUND =
                "Interpreting specification %s the type system"
                        + " found class %s was already bound to %s";
    }

    /**
     * A specification we make retrospectively from a type object. We do
     * this because types for {@code type} and {@code object} (at least)
     * cannot be created by the normal process from a specification.
     */
    private static class PrimordialTypeSpec extends TypeSpec {

        /**
         * Create a specification retrospectively from a type object.
         *
         * @param type to reverse
         * @param lookup to supply to the {@link TypeSpec}
         */
        PrimordialTypeSpec(PyType type, Lookup lookup) {
            super(type.getName(), lookup);
            this.canonical(type.javaType).bases(type.bases);
            if (type instanceof AdoptiveType at) {
                int n = at.getAdoptedCount();
                for (int i = 0; i < n; i++) {
                    this.adopt(at.getAdopted(i).javaType);
                }
            }
        }

    }

}
