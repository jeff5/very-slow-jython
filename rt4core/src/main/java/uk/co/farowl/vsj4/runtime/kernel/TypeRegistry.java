// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.util.Map;
import java.util.WeakHashMap;

import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.Representation;
import uk.co.farowl.vsj4.runtime.TypeSpec;
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
public abstract class TypeRegistry extends ClassValue<Representation> {

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
         * Representation.registry.get() (which is ClassValue.get())
         * contained no cached mapping for c. We will either find an
         * answer ready in the public map, or try to construct one and
         * post it to the public map.
         */
        Representation rep;

        if ((rep = lookup(c)) == null) {
            /*
             * We did not find c published. First we ensure it is
             * initialised. This thread will block here if another
             * thread is already initialising c. That's ok, as we do not
             * hold any type system lock at this point.
             */
            ensureInit(c);
            /*
             * We now ask the type factory to find or create a
             * representation for c. Initialisation of c (by this or
             * another thread) *may* already have created and registered
             * the representation we seek. If so, the type factory will
             * return promptly with it. If not, it will make one.
             */
            rep = findOrCreate(c);
        }

        /*
         * It is possible that other threads are racing this one to
         * define c. This is not a problem: they all have the same
         * answer, and one of them will get to cache it.
         */
        return rep;
    }

    /**
     * Find this class in the published registry map (only), and return
     * the {@code Representation} for it or {@code null} if it was not
     * found.
     *
     * @param c class to resolve
     * @return representation object for {@code c} or {@code null}.
     */
    synchronized final Representation lookup(Class<?> c) {
        return map.get(c);
    }

    /**
     * Find (do not create) this class in the published registry map, or
     * in work-in-progress in the factory, and return the
     * {@code Representation} for it or {@code null} if it was not
     * found.
     * <p>
     * This method is overridden by the registry implementation the
     * {@link TypeFactory} provides.
     *
     * @implNote This method should not be synchronised on the registry
     *     because it is likely to block waiting to use the factory.
     *
     * @param c class to resolve
     * @return representation object for {@code c} or {@code null}.
     */
    abstract Representation find(Class<?> c);

    /**
     * Find this class in the published registry map, or in
     * work-in-progress in the factory, or create a type and return the
     * {@code Representation}. The {@link TypeFactory} creates a
     * registry that implements this method.
     * <p>
     * The registry calls this when it did not find {@code c} published.
     * At that point, we <b>may</b> have to create a representation for
     * {@code c} using the type factory, or some other thread could
     * already be doing that. This method will wait until it can get
     * ownership of the factory (to be sure no other thread can work on
     * {@code c}), and only if there is still no published answer, go on
     * to create one.
     *
     * @implNote This method must take the lock on the factory before it
     *     locks the registry because it is likely to wait for the
     *     factory.
     *
     * @param c class to resolve
     * @return representation object for {@code c}.
     */
    abstract Representation findOrCreate(Class<?> c);

    /**
     * Ensure a class is statically initialised. Static initialisation
     * of a class that defines a Python type will normally create a
     * {@link PyType} and its representations through a call to
     * {@link PyType#fromSpec(TypeSpec)}, although a found Java class
     * will clearly need explicit actions in the caller.
     *
     * @param c to initialise
     */
    static void ensureInit(Class<?> c) {
        if (!c.isPrimitive()) {
            String name = c.getName();
            try {
                Class.forName(name, true, c.getClassLoader());
            } catch (ClassNotFoundException e) {
                throw new InterpreterError(e,
                        "failed to initialise class %s", name);
            }
        }
    }

}
