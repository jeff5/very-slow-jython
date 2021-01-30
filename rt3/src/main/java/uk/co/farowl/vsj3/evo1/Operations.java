package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.util.HashMap;
import java.util.Map;

abstract class Operations {

    /**
     * Mapping from Java class to Operations object. This is the map
     * that backs {@link #opsCV}.
     */
    private static final Map<Class<?>, Operations> opsMap =
            new HashMap<>();

    /**
     * Post an association from Java class to operations object that
     * will be bound into {@link #opsCV} when the first look-up is made.
     *
     * @param c Java class
     * @param ops operations to bind to the class
     */
    static void set(Class<?> c, Operations ops) {
        synchronized (Operations.class) {
            opsMap.put(c, ops);
        }
    }

    /**
     * Mapping from Java class to the {@code Operations} object that
     * provides instances of the class with Python semantics.
     */
    private static final ClassValue<Operations> opsCV =
            new ClassValue<Operations>() {

                /**
                 * Find an operations object for the given class. There
                 * are five broad cases. {@code c} might be:
                 * <ol>
                 * <li>the crafted canonical implementation of a Python
                 * type</li>
                 * <li>an acceptable implementation of some Python
                 * type</li>
                 * <li>the crafted base of Python sub-classes of a
                 * Python type</li>
                 * <li>a found Java type</li>
                 * <li>the crafted base of Python sub-classes of a found
                 * Java type</li>
                 * </ol>
                 * Cases 1, 3 and 5 may be recognised by marker
                 * interfaces on {@code c}. Case 2 may only be
                 * distinguished from case 4 only because classes that
                 * are acceptable uncrafted implementations will have
                 * been posted to {@link #opsMap} before the first call,
                 * when their {@link PyType}s were created.
                 */
                @Override
                protected Operations computeValue(Class<?> c) {
                    // TODO Incomplete method stub
                    synchronized (Operations.class) {
                        /*
                         * We reach here only if c does not already
                         * contain a mapping to its operations. However,
                         * if the type that c implements has been built
                         * (by PyType) it will be in this map.
                         */
                        Operations ops = opsMap.remove(c);

                        if (ops != null) {
                            /*
                             * Case 2: acceptable implementation of some
                             * Python type, or Case 1 if the type is a
                             * bootstrap type.
                             */
                            return ops;

                        } else if (CraftedType.class
                                .isAssignableFrom(c)) {
                            // Case 1, 3, 5: one of the crafted cases
                            // Ensure c statically initialised.
                            String name = c.getName();
                            try {
                                Class.forName(name);
                            } catch (ClassNotFoundException e) {
                                throw new InterpreterError(
                                        "failed to initialise class %s",
                                        c.getSimpleName());
                            }
                            /*
                             * This will normally create a PyType and
                             * post a result to opsMap.
                             */
                            ops = opsMap.remove(c);
                            if (ops != null) {
                                return ops;
                            } else {
                                throw new InterpreterError(
                                        "no operations defned by class %s",
                                        c.getSimpleName());
                            }
                        } else {
                            // Case 4: found Java type
                            throw new MissingFeature(
                                    "Operations from Java class %s",
                                    c.getName());
                            // return PyType.opsFromClass(c);
                        }
                    }
                }
            };

    // ---------------------------------------------------------------

    /** */
    private static class FixedType extends Operations {

        /** */
        final private int index;

        FixedType(int type) {
            this.index = type;
        }

    }

    /** */
    private static class VariableType extends Operations {

    }

    /**
     * Map a Java class to the {@code Operations} object that provides
     * Python semantics to instances of the class.
     *
     * @param klass to be mapped
     * @return {@code Operations} providing semantics
     */
    static Operations forClass(Class<?> klass) {
        return opsCV.get(klass);
    }

    /**
     * Map a Java class to the {@code Operations} object that provides
     * Python semantics to instances of the class.
     *
     * @param klass to be mapped
     * @return {@code Operations} providing semantics
     */
    static Operations of(Object obj) {
        return opsCV.get(obj.getClass());
    }

    /**
     * Fast check that the target is exactly a Python {@code int}. We
     * can do this without reference to the object itself, since it is
     * deducible from the Java class.
     *
     * @return target is exactly a Python {@code int}
     */
       // Override in sub-class
    boolean isIntExact() { return false; }

    /**
     * Fast check that the target is exactly a Python {@code float}. We
     * can do this without reference to the object itself, since it is
     * deducible from the Java class.
     *
     * @return target is exactly a Python {@code float}
     */
       // Override in sub-class
    boolean isFloatExact() { return false; }

    /**
     * Fast check that the target is a data descriptor.
     *
     * @return target is a data descriptor
     */
       // Override in sub-class
    boolean isDataDescr() { return false; }

    static class Target {

        enum Validity { ONCE, INSTANCE, ALWAYS };

        Validity validity;
        MethodHandle target;
        MethodHandle guard;
    }

    PyType type(Object x) {
        // TODO Auto-generated method stub
        return null;
    }

    Target getTarget(Slot slot, Object v) {
        // TODO Auto-generated method stub
        return null;
    }

    Target getTarget(Slot slot, Object v, Object w) {
        // TODO Auto-generated method stub
        return null;
    }

    // Cache of the standard type slots. See CPython PyType.

    MethodHandle op_repr;
    MethodHandle op_hash;
    MethodHandle op_call;
    MethodHandle op_str;

    MethodHandle op_getattribute;
    MethodHandle op_getattr;
    MethodHandle op_setattr;
    MethodHandle op_delattr;

    MethodHandle op_lt;
    MethodHandle op_le;
    MethodHandle op_eq;
    MethodHandle op_ne;
    MethodHandle op_ge;
    MethodHandle op_gt;

    MethodHandle op_iter;
    MethodHandle op_next;

    MethodHandle op_get;
    MethodHandle op_set;
    MethodHandle op_delete;

    MethodHandle op_init;
    MethodHandle op_new;

    MethodHandle op_vectorcall;

    // Number slots table see CPython PyNumberMethods

    MethodHandle op_add;
    MethodHandle op_radd;
    MethodHandle op_sub;
    MethodHandle op_rsub;
    MethodHandle op_mul;
    MethodHandle op_rmul;

    MethodHandle op_neg;

    MethodHandle op_abs;
    MethodHandle op_bool;

    MethodHandle op_and;
    MethodHandle op_rand;
    MethodHandle op_xor;
    MethodHandle op_rxor;
    MethodHandle op_or;
    MethodHandle op_ror;

    MethodHandle op_int;
    MethodHandle op_float;

    MethodHandle op_index;

    // Sequence slots table see CPython PySequenceMethods
    // Mapping slots table see CPython PyMappingMethods

    MethodHandle op_len;
    MethodHandle op_contains;

    MethodHandle op_getitem;
    MethodHandle op_setitem;
    MethodHandle op_delitem;

}
