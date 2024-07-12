package uk.co.farowl.vsj4.runtime.kernel;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.util.HashMap;
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
 * Static factory and utility methods related to {@link PyType} and
 * {@link TypeRegistry}.
 */
public class TypeFactory {

    /** Logger for the type factory. */
    static final Logger logger =
            LoggerFactory.getLogger(TypeFactory.class);

    /** No instances allowed. */
    private TypeFactory() {}

    /**
     * The classic singleton pattern, but holds multiple objects we must
     * create only once (and together).
     */
    private static class Singletons {
        // Get the singleton TypeRegistry (may create it)
        @SuppressWarnings("unused")
        static TypeRegistry registry = TypeRegistry.getInstance();
        // We create the workshop for the factory
        static Workshop workshop = new Workshop();
        /** The (initially partial) type object for 'type'. */
        static final SimpleType type;

        /*
         * We need a specification for each type, to populate the types
         * later with their methods. (We couldn't have constructed the
         * types from specifications because even partial type
         * construction doesn't work until 'type' and 'object' exist.
         * There's nothing more bootstrappy than these types.)
         */
        static {
            logger.info("Creating partial type for 'object'");
            AdoptiveType object = new AdoptiveType();
            TypeSpec specOfObject = new PrimordialTypeSpec(object,
                    AbstractPyBaseObject.LOOKUP);
            workshop.shelve(specOfObject.freeze(), object);

            logger.info("Creating partial type for 'type'");
            type = new SimpleType(object);
            TypeSpec specOfType =
                    new PrimordialTypeSpec(type, AbstractPyType.LOOKUP)
                            .extendFrom(AbstractPyType.Derived.class);
            workshop.shelve(specOfType.freeze(), type);
        }
    }

    /**
     * A specification we make retrospectively from a type object. We do
     * this because types for {@code type} and {@code object} (at least)
     * cannot be created by the normal process from a specification.
     */
    private static class PrimordialTypeSpec extends TypeSpec {

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
    /** An empty array of type objects */
    static final PyType[] EMPTY_TYPE_ARRAY = new PyType[0];
    /** An empty array of type objects */
    static final List<PyType> EMPTY_TYPE_LIST = List.of();

    /**
     * Construct a type from the given specification. The type object
     * does not retain a reference to the specification, once
     * constructed, so that subsequent alterations have no effect on the
     * {@code PyType}.
     *
     * @param spec specification
     * @return the constructed {@code PyType}
     */
    public static PyType typeFrom(TypeSpec spec) {
        /*
         * We are able to make (the right kind of) type object but
         * cannot always guarantee to fill its dictionary. In that case,
         * it would ideally not escape yet, but how?
         */
        Workshop workshop = Singletons.workshop;
        PyType type = workshop.createTypeFrom(spec);

        return type;
    }

    /**
     * A special factory method for the type object of {@code type},
     * which has to be hand-constructed, so the usual pattern can't be
     * applied.
     *
     * @return the type
     */
    public static PyType typeForType() {
        // This reference will initialise Singletons if necessary.
        return Singletons.type;
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
    private static class Workshop {
        /**
         * Mapping from Java class to the work in progress (a
         * partially-built type and its specification).
         */
        // Use an ordered list so we have full control over sequence.
        final Map<Class<?>, Task> tasks = new LinkedHashMap<>();

        // XXX How to deal nicely with specific type of PyType?
        void shelve(TypeSpec spec, PyType type) {
            // FIXME: add to tasks by every class implicated.
            // XXX: is some kind of dependency ordering required?

            Task wt = new Task(type, spec);
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
