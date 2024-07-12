package uk.co.farowl.vsj4.runtime.kernel;

import java.util.Map;
import java.util.WeakHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import uk.co.farowl.vsj4.runtime.Crafted;
import uk.co.farowl.vsj4.runtime.ExtensionPoint;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Shared;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Mapping from Java class to the {@code Representation} that provides
 * instances of the class with Python semantics. We refer to this as a
 * "type registry" because that is almost always the goal when we
 * consult it.
 * <p>
 * There is only one instance of this class and it is returned by
 * {@link TypeRegistry#getInstance()}.
 */
class TypeRegistry extends ClassValue<Representation> {

    /** Logger for the type registry. */
    private final Logger logger;

    /** A singleton holder following Pugh (cs.umd.edu). */
    private static class Singleton {
        static final TypeRegistry instance = new TypeRegistry();
    }

    /** Only used by Singleton. */
    private TypeRegistry() {
        // This is the first thing the run-time system does.
        this.logger = LoggerFactory.getLogger(TypeRegistry.class);
        logger.info("Run-time system is waking up.");
    }

    /**
     * There is just one instance of {@code TypeRegistry}, returned by
     * this method.
     *
     * @return the single instance.
     */
    static TypeRegistry getInstance() { return Singleton.instance; }

    /**
     * Mapping from Java class to {@link Representation}. This is the
     * map that backs this {@link TypeRegistry}. This map is protected
     * from concurrent modification by synchronising on the containing
     * {@code Registry} object. The keys are weak to allow classes to be
     * unloaded. (Concurrent GC is not a threat to the consistency of
     * the registry since a class we are working on cannot be unloaded.)
     */
    private final Map<Class<?>, Representation> map =
            new WeakHashMap<>();

    /**
     * Post an association from a Java class to an
     * {@code Representation}, that will be bound into
     * {@link Representation#registry} when a look-up is made.
     *
     * @param c Java class
     * @param r representation to bind to the class
     * @throws Clash when the class is already mapped
     */
    synchronized void set(Class<?> c, Representation r) throws Clash {
        Representation old = map.putIfAbsent(c, r);
        if (old != null) { throw new Clash(c, old); }
    }

    /**
     * Post associations from multiple Java classes to corresponding
     * {@code Representation}s, that will be bound into
     * {@link Representation#registry} when look-ups are made.
     *
     * @param c Java classes
     * @param r representations to bind to the class
     * @throws Clash when one of the classes is already mapped
     */
    synchronized void set(Class<?>[] c, Representation[] r)
            throws Clash {
        int i, n = c.length;
        for (i = 0; i < n; i++) {
            Representation old = map.putIfAbsent(c[i], r[i]);
            if (old != null) {
                // We failed to insert c[i]: erase what we did
                for (int j = 0; j < i; j++) { map.remove(c[j]); }
                throw new Clash(c[i], old);
            }
        }
    }

    /**
     * Find a {@code Representation} for the given class. There are five
     * broad cases. {@code c} might be:
     * <ol>
     * <li>the crafted canonical implementation of a Python type</li>
     * <li>an adopted implementation of some Python type</li>
     * <li>the implementation of the base of Python sub-classes of a
     * Python type</li>
     * <li>a found Java type</li>
     * <li>the crafted base of Python sub-classes of a found Java
     * type</li>
     * </ol>
     * Cases 1, 3 and 5 may be recognised by marker interfaces on
     * {@code c}. Case 2 may only be distinguished from case 4 only
     * because classes that are adopted implementations will have been
     * posted to {@link #map} before the first call, when their
     * {@link PyType}s were created.
     */
    @Override
    protected synchronized Representation computeValue(Class<?> c) {

        /*
         * Representation.registry contained no mapping (as a
         * ClassValue) for c at the time this thread called get(). We
         * will either find an answer ready in opsMap, or construct one
         * and post it there.
         *
         * It is possible that other threads have already passed through
         * get() and blocked behind this thread at the entrance to
         * computeValue(). This synchronisation guarantees that this
         * thread completes the critical section before another thread
         * enters.
         *
         * Threads entering subsequently, and needing a binding for the
         * same class c, will therefore find the same value found or
         * constructed by this thread. Even if the second thread
         * overtakes this one after the protected region, and returns
         * first, the class value will bind that same Representation
         * object.
         */

        /*
         * XXX There is more to say about re-entrancy (this thread) and
         * concurrency. This design does not mean that another thread,
         * or even the current one, has not already produced a competing
         * Representation objects to post.
         */

        Representation rep = map.get(c);

        if (rep != null) {
            /*
             * An answer already exists, for example because a PyType
             * was built (cases 1 & 2), but it was not yet attached to
             * the Class c. Our return from computeValue() will bind it
             * there for future use.
             */
            return rep;

        } else if (ExtensionPoint.class.isAssignableFrom(c)) {
            // Case 3, 5: one of the derived cases
            // Ensure c and super-classes statically initialised.
            ensureInit(c);
            // This shouldn't have produced an entry for c
            assert map.get(c) == null;
            @SuppressWarnings("unchecked")
            var d = (Class<? extends ExtensionPoint>)c;
            map.put(c, rep = new Shared(d));
            return rep;

        } else if (Crafted.class.isAssignableFrom(c)) {
            // Case 1: one of the crafted (but not derived) cases
            // Ensure c and super-classes statically initialised.
            ensureInit(c);
            /*
             * A Crafted implementation defines a type during
             * initialisation, so it should have posted c or an ancestor
             * of c to the map.
             */
            rep = findRep(c);
            if (rep == null) {
                // May be impossible once Object is mapped (first job).
                String fmt = "No representation found for class %s";
                throw new InterpreterError(
                        String.format(fmt, c.getTypeName()));
            }
            // Fill in entries from c to its ancestral head
            Class<?> head = rep.javaType;
            while (c != head) {
                map.put(c, rep);
                c = c.getSuperclass();
            }
            return rep;

        } else {
            // Case 4: found Java type
            // XXX Stop gap. Needs specialised exposure.
            /*
             * A Lookup object cannot be provided from here. Access to
             * members of c will be determined by package and class at
             * he point of use, in relation to c, according to Java
             * rules. It follows that descriptors in the PyType cannot
             * build method handles in advance of constructing the call
             * site.
             */
            // TypeSpec spec = new TypeSpec(c.getSimpleName(),
            // MethodHandles.publicLookup().in(c));
            rep = null; // PyType.fromSpec(spec);
            // Must post answer to map ourselves?
            return rep;
        }
    }


    /**
     * Ensure a class is statically initialised. Static initialisation
     * will normally create a {@link PyType} and call
     * {@link #set(Class, Representation)} to post a result to
     * {@link #map}.
     *
     * @param c to initialise
     */
    private static void ensureInit(Class<?> c) {
        String name = c.getName();
        try {
            Class.forName(name, true, c.getClassLoader());
        } catch (ClassNotFoundException e) {
            throw new InterpreterError("failed to initialise class %s",
                    name);
        }
    }

    /**
     * Find the {@code Representation} for this class, trying
     * super-classes, or fail returning {@code null}. {@code c} must be
     * an initialised class. If it posted a {@link Representation} for
     * itself, it will be found immediately. Otherwise the method tries
     * successive super-classes until one is found that has already been
     * posted.<p>This is only non-private for diagnostic or test use.
     *
     * @param c class to resolve
     * @return representation object for {@code c} or {@code null}
     */
    final Representation findRep(Class<?> c) {
        Representation rep;
        Class<?> prev;
        while ((rep = map.get(prev = c)) == null) {
            // c has not been posted, but perhaps its superclass?
            c = prev.getSuperclass();
            if (c == null) {
                // prev was Object, or primitive or an interface
                return null;
            }
        }
        return rep;
    }

    /**
     * Lookup the {@code Representation} for this class directly in the
     * map, or return {@code null}.
     *
     * @param c class to resolve
     * @return representation object for {@code c} or {@code null}.
     */
    Representation lookup(Class<?> c) { return map.get(c); }

    /**
     * Exception reporting a duplicate {@link Representation}.
     */
    static class Clash extends Exception {
        private static final long serialVersionUID = 1L;
        /** Class being redefined. */
        final Class<?> klass;
        /**
         * The representation object already in the registry for
         * {@link #klass}
         */
        final Representation existing;

        /**
         * Create an exception reporting that an attempt was made to
         * register a second {@link Representation} for a class already
         * in the registry.
         */
        Clash(Class<?> klass, Representation existing) {
            this.klass = klass;
            this.existing = existing;
        }

        @Override
        public String getMessage() {
            return String.format("repeat Python representation for %s",
                    klass);
        }
    }
}
