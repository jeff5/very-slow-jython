// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.util.Map;
import java.util.WeakHashMap;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.InterpreterError;

/**
 * Mapping from Java class to the {@link Representation} that provides
 * instances of the class with Python semantics. We refer to this as a
 * "type registry" because the {@code Representation} retrieved contains
 * some type information and leads directly to more.
 * <p>
 * In normal operation (outside test cases) there is only one instance
 * of this class, owned by a {@link TypeFactory}, in turn created by
 * {@link PyType}. Note that only the owning factory has a write
 * interface to the registry.
 */
public class TypeRegistry extends ClassValue<Representation> {

    /**
     * The mapping from Java class to {@link Representation} that backs
     * this {@link TypeRegistry}, using entries published by the owning
     * {@link TypeFactory}.
     * <p>
     * The keys are weak to allow classes to be unloaded. (Concurrent GC
     * is not a threat to the consistency of the registry since a class
     * we are looking up cannot be unloaded.)
     * <p>
     * This map is protected from concurrent modification by
     * synchronising on the containing {@code Registry}, but that lock
     * must not be held during type object creation to avoid a deadlock
     * when the factory posts its answer.
     */
    protected final Map<Class<?>, Representation> map =
            new WeakHashMap<>();

    /** The owning {@link TypeFactory}. */
    private final TypeFactory factory;

    /** Only used by {@link TypeFactory}. */
    TypeRegistry(TypeFactory factory) {
        this.factory = factory;
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
    protected Representation computeValue(Class<?> c) {

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
        Representation rep = lookup(c);

        if (rep == null) {
            /*
             * We ask the owning TypeFactory to (create and) register a
             * representation for c. Note that we may block here if
             * another thread has the factory. The factory will check
             * for the possibility that thread posted a mapping for c
             * while we were waiting.
             */
            factory.ensureTypeFor(c);
            rep = lookup(c);
        }

        if (rep == null) {
            // May be impossible once Object is mapped (first job).
            String fmt = "No representation found for class %s";
            throw new InterpreterError(
                    String.format(fmt, c.getTypeName()));
        }
        return rep;
    }

    /**
     * Map an object to the {@code Representation} object that provides
     * it with Python semantics, based on its class.
     *
     * @param obj for which representation is required
     * @return {@code Representation} providing Python semantics
     */
    public Representation of(Object obj) {
        return get(obj.getClass());
    }

    /**
     * Find the {@code Representation} for this class, trying
     * super-classes, or fail returning {@code null}. {@code c} must be
     * an initialised class. If it posted a {@link Representation} for
     * itself, it will be found immediately. Otherwise the method tries
     * successive super-classes until one is found that has already been
     * posted.
     *
     * @param c class to resolve
     * @return representation object for {@code c} or {@code null}
     */
    final synchronized Representation findRep(Class<?> c) {
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
    synchronized Representation lookup(Class<?> c) {
        return map.get(c);
    }

}
