// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.Feature;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.runtime.WithClass;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * The {@code TypeFactory} is the home of Python type creation and
 * management. In normal operation, only one instance of
 * {@code TypeFactory} will be created. It will be held statically by
 * the {@link Representation} class. Exceptionally, we create and
 * destroy instances for limited test purposes.
 * <p>
 * The {@code TypeFactory} is kept thread-safe, in a complex reentrant
 * process of type creation, by careful synchronisation that counts the
 * reentrant calls. When the factory creates type objects they go
 * through two stages: "Java ready" where they are internally consistent
 * and usable from a Java perspective and "Python ready" when they have
 * acquired their full set of attributes.
 * <p>
 * The design ensures that:
 * <ul>
 * <li>All partially completed work in the factory belongs to the single
 * thread that has taken the object-level lock.</li>
 * <li>All work for the thread is completed by the time the thread
 * finally leaves the factory (when the lock is released).</li>
 * <li>All {@link Representation}s and types created for a thread are
 * published (or discarded in the event of an error) by the time the
 * thread finally leaves the factory.</li>
 * </ul>
 * Type objects may become visible to Java while only Java ready
 * (perhaps through static data in another class), and thus can be seen
 * without their Python attributes. The thread that creates these type
 * objects will always make them Python ready eventually, so the caution
 * is needed in that thread only when accessing intermediate values
 * returned in that process. The client (i.e. outside the type system)
 * that instigated type creation will always receive a Python ready
 * type.
 * <p>
 * Some other thread accessing a type as a static field may see it not
 * Python ready, while the thread that instigated type creation
 * completes it. Any thread obtaining a {@code Representation} from the
 * registry will block until its related type is Python ready.
 *
 */
// FIXME Robust protection of types until Python-ready.
/*
 * It is not wholly satisfactory to publish types not Python ready while
 * there is no defence against access. The answer might be to lock their
 * Python side, maybe taking a lock in lookup is enough, while a
 * concurrent thread (or pool?) makes them ready. The bootstrap state is
 * a separate difficulty, possibly resolved by a wait until Java-ready.
 */
public class TypeFactory {

    /** Logger for the type factory. */
    final Logger logger = LoggerFactory.getLogger(TypeFactory.class);

    /**
     * A TypeRegistry in which the association of a class with a
     * {@link Representation} will be published.
     */
    private final Registry registry;

    /** The workshop for the factory. */
    private final Workshop workshop;

    /** The (initially partial) type object for 'type'. */
    private SimpleType typeType;
    /** The (initially partial) type object for 'object'. */
    private SimpleType objectType;
    /** An array containing just type object {@code object} */
    private BaseType[] OBJECT_ONLY;

    /** Factory method to make type exposers. Effectively final. */
    private Function<BaseType, TypeExposer> exposerFactory;

    /**
     * Types may have to be built in rounds that cross-refer, so we keep
     * a reference to the specification that began the current round, as
     * a context for error reporting.
     */
    private TypeSpec lastContext = null;

    /**
     * We count the number of reentrant calls here, and defer publishing
     * any of the {@code Representation}s we create to the registry
     * until the count reaches zero. (Note, we count <i>reentrant
     * calls</i>, not the number of objects in play, as each call
     * potentially creates several types by reentrant use of
     * {@link #fromSpec(TypeSpec)}.) The count is -1 between phases of
     * object creation, so 0 indicates "just arrived".
     */
    private int reentrancyCount;
    /** Used to indent log messages by the reentrancy level. */
    private final Supplier<String> indent = () -> INDENT_RULER
            .substring(0, Math.min(reentrancyCount + 1, 24) * 2);
    private final static String INDENT_RULER = ". ".repeat(24);

    /**
     * Construct a {@code TypeFactory}. Normally this constructor is
     * used exactly once. Exceptionally, we create instances for test
     * purposes.
     *
     * @deprecated Do not create a {@code TypeFactory} other than the
     *     one {@code PyType} holds statically.
     */
    @Deprecated  // ... to stop other use even in the runtime.
    public TypeFactory() {
        logger.info("Type factory being created.");

        this.registry = new Registry();
        this.workshop = new Workshop();

        /*
         * It is unsafe to create type objects yet as they extend
         * Representation, static initialisation of which constructs
         * this factory.
         */
    }

    /**
     * Construct the {@code type} object {@code type}, and necessarily
     * therefore, the one for {@code object} which is its base. The type
     * factory holds these internally, but does not complete or publish
     * them in the registry until
     * {@link #publishBootstrapTypes(Function)} is called.
     *
     * @return the type object for {@code type}
     *
     * @deprecated Call exactly once in {@code TypeFactory}
     */
    @Deprecated  // ... to stop other use even in the runtime.
    public SimpleType createTypeForType() {

        // I will call this only once ...
        assert objectType == null && typeType == null;

        /*
         * The static bootstrap of the type system counts as "entry":
         * the corresponding "exit" is in createBootstrapTypes().
         */
        reentrancyCount = 0;

        /*
         * Create type objects and specifications for type and object.
         * We need special constructors because nothing is more
         * bootstrappy than these types.
         */
        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument("object").log();
        objectType = new SimpleType();

        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument("type").log();
        typeType = new SimpleType(objectType);

        /*
         * We also create a specification for each type, to guide later
         * construction, which we create from their type objects.
         * PyType.fromSpec would not have been safe at this stage.
         */
        final Lookup kernelLU =
                MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);
        TypeSpec specOfObject =
                new PrimordialTypeSpec(objectType, kernelLU)
                        .methodImpls(PyObjectMethods.class);
        TypeSpec specOfType = new PrimordialTypeSpec(typeType, kernelLU)
                .canonicalBase(typeType.canonicalClass());

        // An error during bootstrap says we were creating 'object'
        lastContext = specOfObject;

        try {
            // Map primary classes to types: subclasses inherit.
            workshop.publishLocally(Object.class, objectType);
            workshop.publishLocally(BaseType.class, typeType);
            // Additionally map *interface* PyType to type type.
            workshop.publishLocally(PyType.class, typeType);
        } catch (Clash clash) {
            // If we can't get this far ...
            throw new InterpreterError(clash);
        }

        /*
         * Add the types and specs to the workshop tasks so it can
         * complete the types later with their methods.
         */
        workshop.addTask(objectType, specOfObject);
        workshop.addTask(typeType, specOfType);

        // This cute re-use also proves 'type' and 'object' exist.
        this.OBJECT_ONLY = typeType.bases;
        assert OBJECT_ONLY.length == 1;

        return typeType;
    }

    /**
     * Get the type object of type. This is the same as
     * {@link PyType#TYPE()}, but exists as soon as
     * {@link #createTypeForType()} has run. This will succeed in
     * circumstances where a simple call to {@link PyType#TYPE()} may
     * fail because the the type system is not fully initialised, for
     * example when making the bootstrap types Java ready.
     *
     * @return type object for {@code type}
     */
    public SimpleType getTypeForType() {
        // Check we are not calling before createTypeForType
        assert typeType != null;
        return typeType;
    }

    /**
     * Return the registry where this factory posts its type and
     * representation information.
     *
     * @return the registry
     */
    public TypeRegistry getRegistry() { return registry; }

    /**
     * Inner class to the factory that implements the registry
     * interface. This allows the registry to have behaviour that access
     * the factory that contains it.
     */
    private class Registry extends TypeRegistry {

        /** Create the inner registry. */
        Registry() {}

        /**
         * Register the given associations of a Java class to a
         * {@link Representation}. Subsequent enquiry through
         * {@link #get(Class)} will yield the given
         * {@code Representation}.
         * <p>
         * This action permanently affects the state of the
         * {@code Class} object: the association cannot be changed. The
         * {@code Representation} and its {@code type}s may be mutated
         * subsequently, to the extent they allow. It is an error to
         * attempt to associate a different {@code Representation} with
         * a class already bound in the registry. All registrations in
         * the batch succeed or fail together.
         * <p>
         * <b>Concurrency:</b> The representations are immediately
         * available to other threads, so they and the type(s) behind
         * them must be Python ready.
         *
         * @param assoc associations to add to the published map
         * @throws Clash when a representing class is already bound
         */
        synchronized void registerAll(
                Map<Class<?>, Representation> assoc) throws Clash {
            /*
             * Checks we made in workshop.add should guarantee that no
             * class in the unpublished map is already in the published
             * map. We're going to check it anyway. If this ever fails,
             * something is wrong with the synchronisation that ensures
             * only the thread that built the new entries can add new
             * entries.
             */
            for (Class<?> c : assoc.keySet()) {
                if (map.containsKey(c)) {
                    Representation old = map.get(c);
                    throw alreadyBoundError(c, old);
                }
                logger.atDebug().setMessage(PUBLISHING_CLASS_REP)
                        .addArgument(indent)
                        .addArgument(() -> c.getTypeName())
                        .addArgument(() -> assoc.get(c)).log();
            }
            // Safe to do en masse.
            map.putAll(assoc);
        }

        @Override
        Representation find(Class<?> c) {
            synchronized (TypeFactory.this) {
                /*
                 * This thread has control of the factory, so nothing
                 * can change the *published* map while we work.
                 */
                Representation r = lookup(c);
                if (r == null) {
                    /*
                     * If the unpublished map is not empty, this had
                     * better be an internal call, or there is a bug.
                     */
                    assert workshop.isEmpty() || reentrancyCount >= 0;
                    // The caller is allowed to use work in progress.
                    r = workshop.unpublished.get(c);
                }
                return r;
            }
        }

        @Override
        Representation findOrCreate(Class<?> c) {

            // We are "just outside" the factory: take the lock
            synchronized (TypeFactory.this) {
                Representation rep;
                /*
                 * We check the published map first. The current thread
                 * either (1) already owned the factory lock, and simply
                 * looked up a representation part way through its work,
                 * or (2) has just acquired for the lock, and the
                 * representation of c may have been published during
                 * the wait.
                 */
                if ((rep = lookup(c)) == null) {
                    /*
                     * c is not published. We shall try to find a
                     * representation in the process of being built, but
                     * it will only be ok to return it if we are a
                     * re-entrant call.
                     */
                    // FIXME reentrancyCount assertions routinely fail
                    try {
                        if (reentrancyCount != -1) {
                            logger.atWarn().setMessage(
                                    "Re-entering at level {} for {}")
                                    .addArgument(reentrancyCount)
                                    .addArgument(c).log();
                        }
                        // assert reentrancyCount == -1;
                        // assert workshop.isEmpty();
                        rep = _findOrCreate(c);
                        if (reentrancyCount != -1) {
                            logger.atWarn().setMessage(
                                    "Returning at level {} with {}")
                                    .addArgument(reentrancyCount)
                                    .addArgument(rep).log();
                        }
                        // assert reentrancyCount == -1;
                    } catch (Clash clash) {
                        throw new InterpreterError(clash);
                    }
                }
                return rep;
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
                TypeSpec spec = new TypeSpec(c.getTypeName(),
                        MethodHandles.publicLookup()).primary(c);
                rep = fromSpec(spec);
            }

            if (rep == null) {
                // Maybe just log as error/warning
                String fmt = "No representation found for class %s";
                throw new InterpreterError(
                        String.format(fmt, c.getTypeName()));
            }

            if (reentrancyCount == 0) {
                /*
                 * We are at the entry level. Everything we have created
                 * must be made Python-ready and be published.
                 */
                finishWorkshopTasks();
                workshop.publishAll();
                lastContext = null;
            }

            reentrancyCount -= 1;
            return rep;
        }

        /**
         * Find an existing {@link Representation} that may be used for
         * {@code c}, or return {@code null}. The argument {@code c} is
         * a class that has been statically initialised.
         * <p>
         * The method does not create a {@code Representation} but it
         * may associate {@code c} and its super-classes with a
         * {@code Representation} that already exists. If {@code c}
         * implements {@link WithClass}, and some superclass of
         * {@code c} is already registered (in the published or
         * unpublished map), the same {@link Representation} will be
         * registered for {@code c} in the unpublished map.
         * <p>
         * This is the normal case where {@code c} is a subclass of a
         * Java representation {@code d} of a Python type. That
         * representation has already been registered thanks to a call
         * to {@link PyType#fromSpec(TypeSpec)}. {@code c} should then
         * also be a representation of that type. That is, looking up
         * {@code c}, or any superclass up to {@code d}, returns the
         * same {@code Representation} as for {@code d}.
         * <p>
         * In some cases, {@code c} implements {@code WithClass} without
         * extending a registered representation. This is not
         * necessarily an error. The method will return {@code null},
         * and no registration will occur.
         *
         * @param c the given class to resolve
         * @return the representation to which c is registered
         */
        Representation resolve(Class<?> c) {
            Representation r;
            if ((r = find(c)) == null) {
                if (WithClass.class.isAssignableFrom(c)) {
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
     * Construct a type from the given specification. When the factory
     * creates type objects they go through two stages: "Java ready"
     * where they are internally consistent from a Java perspective and
     * "Python ready" when they have acquired their full set of
     * attributes. When called from outside the type system,
     * {@code fromSpec} returns a {@code PyType} that is Python ready.
     * But the method may be reentered during the construction of a
     * type, to create related types. In that case, the returned type
     * may be only Java ready, until the external call completes.
     * <p>
     * The specification becomes frozen, so that subsequent alterations
     * (which would not affect the {@code PyType} anyway) will be
     * rejected.
     *
     * @param spec specification
     * @return the constructed {@code PyType}
     * @throws Clash when a representing class is already bound
     */
    public synchronized BaseType fromSpec(TypeSpec spec) throws Clash {
        /*
         * We are able to make (the right kind of) type object but
         * cannot always guarantee to fill its dictionary. In that case,
         * it would ideally not escape yet, but how?
         */
        logger.atDebug().setMessage(CREATING_PARTIAL_TYPE)
                .addArgument(indent).addArgument(spec.getName()).log();
        if (reentrancyCount++ < 0) { lastContext = spec; }
        // Create a type and add it to the work in progress.
        BaseType type = workshop.addTaskFromSpec(spec);
        /*
         * It is ok to return a type that is not Python ready from a
         * reentrant call. Work is in hand to complete it.
         */
        if (reentrancyCount == 0) {
            /*
             * But at the top-level, everything we have created must be
             * made Python-ready and be published.
             */
            finishWorkshopTasks();
            workshop.publishAll();
            lastContext = null;
        }
        reentrancyCount -= 1;
        return type;
    }

    /**
     * Complete the type objects currently in the {@code TypeFactory}
     * workshop and publish their associated {@link Representation}s.
     * This method is called once, within the static initialisation of
     * the {@code TypeSystem} class.
     * <p>
     * Objects with these Python types are already in use in multiple
     * threads. After all, {@code String} and {@code Object} are amongst
     * them, not just implementation-specific classes like
     * {@code PyFloat}. However, no thread other than this one is able
     * to reach the corresponding {@code Representation} or type object,
     * without blocking, since this thread owns the necessary locks,
     * both the {@code TypeFactory} lock and the static initialisation
     * lock of the {@code TypeSystem} class.
     * <p>
     * During this call, the type exposer gives the waiting type objects
     * the attributes (descriptors) that define the Python behaviours of
     * their corresponding objects. Then they are published, meaning
     * that their {@code Representation}s are posted to the type system
     * registry.
     * <p>
     * Whenever some thread completes a lookup in the registry, the
     * {@code Representation} it finds get cached outside the protection
     * of the {@code TypeFactory} lock. It will be visible to all
     * threads. However, no thread other than this one can proceed with
     * such lookup until this method returns (releasing the factory
     * lock), and the initialisation of the {@code TypeSystem} class
     * completes (releasing that lock).
     *
     * @param exposerNew a way to make type exposers
     * @throws Clash when a representing class is already bound
     */
    public synchronized void publishBootstrapTypes(
            Function<BaseType, TypeExposer> exposerNew) throws Clash {

        exposerFactory = exposerNew;

        assert reentrancyCount == 0;

        /*
         * We are at the top-level of initialising the factory and its
         * bootstrap types. Everything we have created must be made
         * Python-ready and be published.
         */
        finishWorkshopTasks();
        workshop.publishAll();

        // This matches reentrancyCount = 0 in the constructor.
        assert reentrancyCount == 0;
        reentrancyCount = -1;
        lastContext = null;

        logger.info("Bootstrap types ready.");
    }

    /**
     * Make the types in the workshop Python ready by exposing their
     * methods and fields. These types have accumulated since the
     * external request that began the batch. (This request was a call
     * to {@link #fromSpec(TypeSpec)}, seeking a {@link Representation}
     * not yet in the registry, or the insertion of tasks for
     * {@code type} and {@code object} upon creation of the factory
     * itself. On return, the representations remain unpublished in the
     * workshop.
     *
     * @throws Clash on a problem with an existing registry entry
     */
    private void finishWorkshopTasks() throws Clash {
        /*
         * Give all waiting types their Python nature. Note that this
         * action, to expose waiting types can result in re-entrant
         * calls for fromSpec() as new types are revealed in the static
         * initialisation of Java classes.
         */
        workshop.readyAllWaitingTypes();
        // Let's hope that emptied the shop ...
        int workshopCount = workshop.tasks.size();
        if (workshopCount != 0) {
            logger.atWarn().setMessage(
                    "Type '{}' finished but {} tasks in factory.")
                    .addArgument(lastContext.getName())
                    .addArgument(workshopCount).log();
        }
    }

    /**
     * Create an error to throw when an attempt is made to define a
     * mapping from a class to a {@code Representation} and the class is
     * already bound.
     *
     * @param c to be bound
     * @param existing binding (from a find)
     * @return to throw
     */
    private Clash alreadyBoundError(Class<?> c,
            Representation existing) {
        return new Clash(lastContext, c, existing);
    }

    /**
     * The Workshop is where we hold types that are Java ready but not
     * yet Python ready, and the mappings from Java class to
     * {@link Representation} that support them.
     * <p>
     * We fill the {@code Workshop} with all type objects and
     * {@code Representations} that follow from a single external
     * request. This covers the bootstrap types (created in response to
     * any request that wakes the type system), and also the descriptor
     * types of which a full set must be Java ready before any other
     * type may become Python ready.
     * <p>
     * We return a result to an external call on the type system only
     * when all the waiting types have been made Python ready. At this
     * point, the {@code Workshop} is empty again.
     */
    private class Workshop {
        /**
         * A list of types and their specifications that are under
         * construction in the workshop.
         */
        final Queue<Task> tasks = new LinkedList<>();

        /**
         * Mapping from a Java class (a Java representation for a type)
         * to the {@link Representation} object, while the type remains
         * work in progress. These will all be added to the mappings
         * published in the {@link Registry} when the external request
         * is complete. Until then, it can be treated as an extension to
         * the public map, but <b>only</b> within the scope of that
         * request and its private transaction.
         */
        final Map<Class<?>, Representation> unpublished =
                new HashMap<>();

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder(100);
            for (Task task : tasks) {
                buf.append(task.spec);
                buf.append('\n');
            }

            for (Entry<Class<?>, Representation> unpub : unpublished
                    .entrySet()) {
                buf.append("  ").append(unpub.getKey().getTypeName())
                        .append(" -> ").append(unpub.getValue());
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
         * produced is Java ready but not Python ready: its type
         * dictionary is empty of the descriptors needed to make it
         * work. This is the first phase in the process that ultimately
         * lies behind {@link PyType#fromSpec(TypeSpec)}.
         *
         * @param spec specifying the new type
         * @return the new partial type
         * @throws Clash when a representing class is already bound
         */
        BaseType addTaskFromSpec(TypeSpec spec) throws Clash {
            // No further change once we start
            String name = spec.freeze().getName();

            // It has these (potentially > 1) representations:
            Class<?> primary = spec.getPrimary();
            Class<?> canonical = spec.getCanonicalBase();
            List<Class<?>> adopted = spec.getAdopted();
            List<Class<?>> accepted = spec.getAccepted();

            // Get the list of Python bases, or implicitly object.
            List<PyType> baseList = spec.getBases();
            // The constructors take BaseType[].
            BaseType[] bases;
            if (baseList.isEmpty()) {
                bases = OBJECT_ONLY;
            } else {
                bases = baseList.toArray(new BaseType[baseList.size()]);
            }

            // Result of the construction
            BaseType newType;

            if (spec.getFeatures().contains(Feature.REPLACEABLE)) {
                assert primary != null;
                assert adopted.isEmpty();
                assert accepted.isEmpty();
                /*
                 * The type is replaceable: the representation is the
                 * primary class, but it is allowable that it already
                 * have a representation.
                 */
                SharedRepresentation sr;
                Representation existing = registry.find(primary);
                if (existing == null) {
                    // It doesn't exist, so we create and add it.
                    sr = new SharedRepresentation(primary, canonical);
                    publishLocally(primary, sr);
                } else if (existing instanceof SharedRepresentation s) {
                    // It does exist and is a Shared type: just use it.
                    assert s.canonicalClass() == canonical;
                    sr = s;
                } else {
                    // A representation exists but is the wrong type.
                    throw new Clash(spec, Clash.Mode.NOT_SHARABLE,
                            primary, existing);
                }

                // Create a type referencing the shared representation.
                ReplaceableType rt =
                        new ReplaceableType(name, sr, bases);
                addTask(newType = rt, spec);

            } else if (adopted.isEmpty() && accepted.isEmpty()) {
                /*
                 * The type is not adoptive: the Representation is the
                 * PyType itself with the primary as its representation
                 * class.
                 */
                assert primary != null;
                SimpleType st =
                        new SimpleType(name, primary, canonical, bases);
                publishLocally(primary, st);
                addTask(newType = st, spec);

            } else {
                /*
                 * The type adopts/accepts one or more classes: the
                 * representations will be the type itself for the
                 * primary class and new representations for adopted
                 * classes. Accepted classes (a rarity) are recorded as
                 * self-classes but no representation is created.
                 */
                assert primary != null;
                assert canonical == primary;
                assert spec.getFeatures().contains(Feature.IMMUTABLE);

                // Construct the type, creating representations.
                AdoptiveType at = new AdoptiveType(name, primary, bases,
                        adopted, accepted);

                // Register all the representations (primary, adopted).
                List<Representation> reps = at.representations();
                List<Class<?>> classes = at.selfClasses();
                for (int i = 0; i <= adopted.size(); i++) {
                    // Note this checks for attempted duplication.
                    publishLocally(classes.get(i), reps.get(i));
                }

                // Make exposing the type a Task for later.
                addTask(newType = at, spec);
            }

            return newType;
        }

        /**
         * Add an existing Java ready type to the work in progress,
         * assumed consistent with the specification. The type is not
         * Python ready. We use this from
         * {@link #addTaskFromSpec(TypeSpec)}, and when a type and
         * specification has been produced by hand (for {@code object}
         * and {@code type}).
         *
         * @param type the new type
         * @param spec specifying the new type
         */
        void addTask(BaseType type, TypeSpec spec) {
            tasks.add(new Task(type, spec));
        }

        /**
         * Add a class representation to the workshop's
         * {@link #unpublished} map during construction of a partial
         * type. We check that the class is not already mapped in either
         * the published registry or the local unpublished
         * representations.
         *
         * @param c class to enter in unpublished map
         * @param r linking {@code c} to the type
         * @throws Clash when {@code c} is already bound
         */
        void publishLocally(Class<?> c, Representation r) throws Clash {
            // Look up c in both published and unpublished maps.
            Representation existing = registry.find(c);
            if (existing != null) {
                throw alreadyBoundError(c, existing);
            }
            unpublished.put(c, r);
        }

        /**
         * Make each type in the workshop Python ready. (Each type in
         * the task list is already Java ready.) For each type we
         * populate its dictionary with attributes from the type
         * exposer, and finalise other state from the specification.
         */
        void readyAllWaitingTypes() {
            // Do not use an iterator because exposure may add tasks.
            while (!tasks.isEmpty()) { tasks.remove().readyType(); }
        }

        /**
         * Publish all representations created in response to the last
         * external entry to the type system. All the types involved
         * must be Python ready (and {@code workshop.tasks} must be
         * empty).
         *
         * @throws Clash if some representation is already published
         */
        void publishAll() throws Clash {
            assert tasks.isEmpty();
            registry.registerAll(unpublished);
            unpublished.clear();
        }

        /**
         * A {@code Workshop.Task} holds a partially-built type and its
         * specification. It is created to hold work in progress in the
         * {@code Workshop}, between creation of a "Java ready" type
         * object and completion of it as "Python ready". The need to
         * hold work in progress arises particularly while the
         * "bootstrap" types are created. {@code object}, {@code type},
         * and those with adopted representations like {@code str},
         * cannot be made Python ready without the descriptor types
         * being at least Java ready. And then none of them may be
         * published until all are Python ready.
         * <p>
         * Reentrant here means that as part of satisfying one request,
         * a new request is made synchronously from the same thread. An
         * asynchronous request from another thread would wait outside
         * the factory until the workshop is empty because of the
         * locking strategy.
         * <p>
         * In principle (we think) any class could involve that type of
         * partial circularity, and so all type creation follows the
         * same flow. This all results in reentrant requests to the
         * factory.
         */
        private class Task {
            /** The type being built. */
            final BaseType type;

            /** Specification for the type being built. */
            final TypeSpec spec;

            Task(BaseType type, TypeSpec spec) {
                super();
                this.type = type;
                this.spec = spec.freeze();
            }

            @Override
            public String toString() {
                return String.format("Task[%s]", spec.getName());
            }

            /**
             * Make the single type identified in the task Python ready.
             * Populate its dictionary with attributes from the type
             * exposer, and finalise other state from the specification.
             */
            // Compare CPython type_ready in typeobject.c
            void readyType() {

                // Set MRO
                type.computeInstallMRO();

                // Set feature flags
                type.inheritFeatures();
                type.addFeatures(spec);

                // Construct an exposer for the type.
                TypeExposer exposer = gatherJavaAttributes();

                /*
                 * Populate the dictionary of the type with the
                 * accumulated descriptors created from the definitions
                 * in the exposer.
                 */
                type.populateDict(exposer, spec);

                // Discover the Java constructors
                type.fillConstructorLookup(spec);

                // Derive remaining feature flags
                type.deriveFeatures(spec);
            }

            /**
             * Gather attributes (methods, members and get-sets) from
             * the primary representation class, selected ancestor
             * classes, and supplementary method implementation classes.
             * Definitions (a precursor of Python descriptors)
             * accumulate in the exposer returned.
             *
             * @return a type exposer with found definitions
             */
            private TypeExposer gatherJavaAttributes() {

                // Construct an exposer for the type.
                TypeExposer exposer = exposerFactory.apply(type);

                /*
                 * We look for methods, members and get-sets in the
                 * primary representation and Java superclasses, but NOT
                 * where a Python superclass will supply it.
                 */
                for (Class<?> c : definitionClasses()) {
                    // Gather methods and get-sets
                    exposer.exposeMethods(c);
                    // ... and members (fields).
                    exposer.exposeMembers(c);
                }

                /*
                 * Gather methods and get-sets from the specified
                 * supplementary method implementation classes.
                 */
                for (Class<?> c : spec.getMethodImpls()) {
                    // Scan class c for method/attribute definitions.
                    exposer.exposeMethods(c);
                }

                return exposer;
            }

            /**
             * Walk down to a given class through all super-classes that
             * might contain methods, members and get-sets to expose,
             * but are not a representation of a Python base. This
             * strategy provides the freedom to define attributes in
             * Java superclasses of an object implementation. The result
             * is presented with the most senior ancestor first, so that
             * overridden definitions will be superseded in the scan.
             *
             * @return superclasses supplying definitions
             */
            private Collection<Class<?>> definitionClasses() {
                /*
                 * Any definitions in these classes will be in the
                 * dictionary of a base, so this type will inherit them
                 * along the MRO.
                 */
                Set<Class<?>> exclusions = new HashSet<>();
                exclusions.add(Object.class);

                for (PyType t : spec.getBases()) {
                    exclusions.add(t.canonicalClass());
                }

                /*
                 * A list of Java classes potentially defining methods,
                 * members and get-sets.
                 */
                LinkedList<Class<?>> defs = new LinkedList<>();

                // The primary is always added (unless Object)
                Class<?> c = spec.getPrimary();
                assert c != null;
                // Work upwards from until we meet an excluded class
                while (c != null && !exclusions.contains(c)) {
                    // At front of list so subclasses will win
                    defs.addFirst(c);
                    c = c.getSuperclass();
                }

                return defs;
            }
        }
    }

    private static final String FINDING_REP =
            "{}Finding representation for '{}'";
    private static final String CREATING_PARTIAL_TYPE =
            "{}Creating partial type for '{}'";
    private static final String PUBLISHING_CLASS_REP =
            "{}Publishing '{}' -> '{}'";

    /**
     * Exception reporting a duplicate {@code Representation} for the
     * given class or some other inconsistency, such as a the wrong type
     * of {@code Representation} found when one must be re-used.
     */
    public static class Clash extends Exception {
        private static final long serialVersionUID = 1L;
        /** Context of the clash. */
        final TypeSpec context;
        /** Type of clash. */
        final Mode mode;
        /** Class being referenced. */
        final Class<?> klass;
        /** Representation already registered for {@link #klass} */
        final Representation existing;

        /** Types of clash. */
        public enum Mode {
            /** New representation requested but exists already. */
            EXISTING("class %s was already bound to %s"),
            /** Representation needed for sharing is not suitable. */
            NOT_SHARABLE("class %s was bound to non-shared %s"),
            /** Representation needed as "accepted self" is missing. */
            MISSING("no representation for class %s");

            String fmt;

            Mode(String fmt) { this.fmt = fmt; }
        }

        /**
         * Create an exception reporting a specified type of problem in
         * the registry with a Java representation class in the context
         * of a an attempt to define a specific new type.
         *
         * @param context specification being worked (may be null)
         * @param mode of the clash
         * @param klass being registered
         * @param existing representation for that class
         */
        Clash(TypeSpec context, Mode mode, Class<?> klass,
                Representation existing) {
            assert mode == Mode.MISSING || existing != null;
            this.context = context;
            this.mode = mode;
            this.klass = klass;
            this.existing = existing;
        }

        /**
         * Create an exception reporting that an attempt was made to
         * register a second {@link Representation} for a class already
         * in the registry.
         *
         * @param context specification being worked (may be null)
         * @param klass being registered
         * @param existing representation for that class
         */
        private Clash(TypeSpec context, Class<?> klass,
                Representation existing) {
            this.context = context;
            this.mode = Mode.EXISTING;
            this.klass = klass;
            this.existing = existing;
        }

        @Override
        public String getMessage() {
            return String.format(PREAMBLE + mode.fmt,
                    context != null ? context.getName() : "(null)",
                    klass.getTypeName(), existing);
        }

        private static final String PREAMBLE =
                "Interpreting specification %s, the type system found ";
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
        PrimordialTypeSpec(BaseType type, Lookup lookup) {
            super(type.getName(), lookup);
            // I think the primordial types are only simple
            assert type instanceof SimpleType;
            this.primary(type.javaClass()).bases(type.getBases())
                    .add(Feature.IMMUTABLE);
        }
    }
}
