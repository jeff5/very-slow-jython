package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.TypeSpec;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Factory object that is the home of Python type creation and
 * management. In normal operation, one instance of this will be created
 * and held statically by the {@link PyType} class. Exceptionally, we
 * create and destroy instances for test purposes.
 */
public class TypeFactory {

    /** Logger for the type factory. */
    final Logger logger = LoggerFactory.getLogger(TypeFactory.class);

    /**
     * A TypeRegistry in which results, the association of a class with
     * a {@link Representation}, behind which there is always a
     * {@link PyType} will be published.
     */
    final TypeRegistry registry;
    /** The workshop for the factory. */
    final Workshop workshop;
    /** The (initially partial) type object for 'type'. */
    final SimpleType type;
    /** The (initially partial) type object for 'object'. */
    final AdoptiveType object;

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
        this.registry = new TypeRegistry();
        this.workshop = new Workshop();
        /*
         * Create type objects for type and object. We need a
         * specification for each type as well, so the workshop can the
         * types later with their methods. (We couldn't have constructed
         * the types from specifications because even partial type
         * construction doesn't work until 'type' and 'object' exist.
         * There's nothing more bootstrappy than these types.)
         */
        logger.info("Creating partial type for 'object'");
        this.object = new AdoptiveType();
        TypeSpec specOfObject = new PrimordialTypeSpec(object,
                AbstractPyBaseObject.LOOKUP);
        workshop.shelve(specOfObject, object);

        logger.info("Creating partial type for 'type'");
        this.type = new SimpleType(object);
        TypeSpec specOfType =
                new PrimordialTypeSpec(type, AbstractPyType.LOOKUP)
                        .extendFrom(AbstractPyType.Derived.class);
        workshop.shelve(specOfType, type);
    }

    /**
     * Return the registry where this factory posts its type and
     * representation information.
     *
     * @return the registry
     */
    public TypeRegistry getRegistry() { return registry; }

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

    /** Lookup object with package visibility. */
    static Lookup LOOKUP =
            MethodHandles.lookup().dropLookupMode(Lookup.PRIVATE);

    /**
     * Construct a type from the given specification. The type object
     * does not retain a reference to the specification, once
     * constructed, so that subsequent alterations have no effect on the
     * {@code PyType}.
     *
     * @param spec specification
     * @return the constructed {@code PyType}
     */
    public PyType typeFrom(TypeSpec spec) {
        /*
         * We are able to make (the right kind of) type object but
         * cannot always guarantee to fill its dictionary. In that case,
         * it would ideally not escape yet, but how?
         */
        PyType type = workshop.createTypeFrom(spec);

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
        return type;
    }

    /** Create and complete the bootstrap types.
     * These are the adoptive types and those
     * types required to create descriptors.
     * {@link PyType#TYPE} should have been initialised before
     *  this method is called so that types being defined now may refer to it.
     * <p>
     * We include the adoptive types
     * to ensure that each adopted Java class becomes bound to
     * its {@link Representation} before it gets used from Python.
     * Descriptor
     */
    public synchronized void createBootstrapTypes() {
        assert PyType.TYPE!=null;



        logger.info("Type system ready.");
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
         * Mapping from Java class to the work in progress (a
         * partially-built type and its specification).
         */
        // Use an ordered list so we have full control over sequence.
        final Map<Class<?>, Task> tasks = new LinkedHashMap<>();

        // XXX How to deal nicely with specific type of PyType?
        void shelve(TypeSpec spec, PyType type) {
            // No more change allowed (even if client kept a ref).
            spec.freeze();

            // Create a task to fill in the rest of the type
            Task wt = new Task(type, spec);

            // FIXME: is some kind of dependency ordering required?

            // File the task under every class involved
            Class<?> c;
            if ((c = spec.getCanonical()) != null) { shelve(c, wt); }
            if ((c = spec.getExtendingClass()) != null) {
                shelve(c, wt);
            }
            for (Class<?> a : spec.getAdopted()) { shelve(a, wt); }
        }

        /**
         * Place a partially-completed {@code type} on the
         * {@link PyType#tasks} list.
         *
         * @param spec specification for the type
         * @param type corresponding (partial) type object
         */
        private void shelve(Class<?> key, Task wt) {
            Task t = tasks.putIfAbsent(key, wt);
            if (t != null) {
                throw new InterpreterError(REPEAT_CLASS, key);
            }
        }

        PyType createTypeFrom(TypeSpec spec) {

            PyType type;

            spec.freeze();
            String name = spec.getName();
            List<Class<?>> adopted = spec.getAdopted();
            // List<Class<?>> accepted = spec.getAccepted();
            Class<?> canonical = spec.getCanonical();
            PyType[] bases = spec.getBases();

            if (adopted.isEmpty()) {
                type = new SimpleType(name, canonical, bases);
            } else {
                type = new AdoptiveType(name, canonical, adopted.size(),
                        bases);
                // FIXME: representations
            }

            // FIXME: more to do here

            return type;
        }

        /**
         * A record used to defer the completion of a particular type
         * object. When so deferred, {@link PyType#fromSpec(TypeSpec)}
         * will return a type object without filling the dictionary of
         * the type. The type and the implementation class can be
         * available to Java, but will not yet function properly as a
         * Python object.
         * <p>
         * This only happens while starting up the run-time. The purpose
         * is to allow Java class initialisation to complete for all of
         * the types needed to populate type dictionaries ("bootstrap
         * types"). Other classes that request a type object during this
         * time will be caught up temporarily in the same process.
         * <p>
         * A {@code BootstrapTask} stores the {@link PyType} object and
         * the {@link TypeSpec} for a given type. All the waiting types
         * are completed as soon as the last of them becomes available.
         */
        private class Task {

            /** The type being built. */
            final PyType type;

            /** Specification for the type being built. */
            final TypeSpec spec;

            /**
             * Classes for which the tasks must all be ready for
             * publication before or with this one.
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
                return String.format("Workshop.Task[%s]", spec);
            }
        }

        private static final String REPEAT_CLASS =
                "PyType bootstrapping: %s encountered twice";
        private static final String LATE_CLASS =
                "PyType bootstrapping: unexpected late %s";
    }
}
