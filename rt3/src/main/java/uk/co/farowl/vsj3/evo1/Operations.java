package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.util.Map;
import java.util.WeakHashMap;

import uk.co.farowl.vsj3.evo1.Slot.Signature;

abstract class Operations {

    static class Registry extends ClassValue<Operations> {

        /**
         * Mapping from Java class to Operations object. This is the map
         * that backs {@link #registry}. It is also the object on which
         * updating {@code opsCV} is synchronised. The keys are weak to
         * allow classes to be unloaded.
         */
        private final Map<Class<?>, Operations> opsMap =
                new WeakHashMap<>();

        /**
         * Post an association from Java class to an {@code Operations}
         * object that will be bound into {@link #registry} when the
         * first look-up is made.
         *
         * @param c Java class
         * @param ops operations to bind to the class
         */
        synchronized void set(Class<?> c, Operations ops) {
            opsMap.put(c, ops);
        }

        /**
         * Find an operations object for the given class. There are five
         * broad cases. {@code c} might be:
         * <ol>
         * <li>the crafted canonical implementation of a Python
         * type</li>
         * <li>an adopted implementation of some Python type</li>
         * <li>the canonical implementation of the base of Python
         * sub-classes of a Python type</li>
         * <li>a found Java type</li>
         * <li>the crafted base of Python sub-classes of a found Java
         * type</li>
         * </ol>
         * Cases 1, 3 and 5 may be recognised by marker interfaces on
         * {@code c}. Case 2 may only be distinguished from case 4 only
         * because classes that are adopted implementations will have
         * been posted to {@link #opsMap} before the first call, when
         * their {@link PyType}s were created.
         */
        @Override
        protected synchronized Operations computeValue(Class<?> c) {
            /*
             * opsCV contained no mapping for c at the time this thread
             * called get(). However, this does not mean that another
             * thread, or even the current one, is not already producing
             * one.
             */
            synchronized (opsMap) {
                /*
                 * We reach here only if c does not already contain a
                 * mapping to its operations. However, if the type that
                 * c implements has been built (by PyType) it will be in
                 * this map.
                 */
                Operations ops = opsMap.get(c);

                if (ops != null) {
                    /*
                     * Case 2: accepted implementation of some Python
                     * type, or Case 1 if the type is a bootstrap type.
                     */
                    return ops;

                } else if (CraftedType.class.isAssignableFrom(c)) {
                    // Case 1, 3, 5: one of the crafted cases
                    // Ensure c statically initialised.
                    ensureInit(c);
                    return findOps(c);

                } else {
                    // Case 4: found Java type
                    throw new MissingFeature(
                            "Operations from Java class %s",
                            c.getName());
                    // return PyType.opsFromClass(c);
                }
            }
        }

        /**
         * Ensure a class is statically initialised. This will normally
         * create a {@link PyType} and post a result to {@link #opsMap}.
         *
         * @param c to initialise
         */
        private static void ensureInit(Class<?> c) {
            String name = c.getName();
            try {
                Class.forName(name);
            } catch (ClassNotFoundException e) {
                throw new InterpreterError(
                        "failed to initialise class %s", name);
            }
        }

        /**
         * Find the {@code Operations} object for this class, trying
         * super-classes. {@code c} must be an initialised class. If it
         * posted an {@link Operations} object for itself, it will be
         * found immediately. Otherwise the method tries successive
         * super-classes until one is found that has already been
         * posted.
         *
         * @param c class to resolve
         * @return operations object for {@code c}
         */
        private Operations findOps(Class<?> c) {
            Operations ops;
            Class<?> prev;
            while ((ops = opsMap.get(prev = c)) == null) {
                // c has not been posted, but perhaps its superclass?
                c = prev.getSuperclass();
                if (c == null) {
                    // prev was Object, or primitive or an interface
                    throw new InterpreterError(
                            "no operations defined by class %s",
                            prev.getSimpleName());
                }
            }
            return ops;
        }

    }

    /**
     * Mapping from Java class to the {@code Operations} object that
     * provides instances of the class with Python semantics.
     */
    static final Registry registry = new Registry();

    /**
     * A table of binary operations that may be indexed by a pair of
     * classes (or their {@code Operations} objects). Binary operations,
     * at the same time as appearing as the {@code op} and {@code rop}
     * slots, meaning for example {@link Operations#op_add} and
     * {@link Operations#op_radd}, are optionally given implementations
     * specialised for the Java classes of their arguments. A
     * {@code BinopGrid} describes the
     */
    static class BinopGrid {

        /** Handle that marks an empty binary operation slot. */
        protected static final MethodHandle BINARY_EMPTY =
                Slot.Signature.BINARY.empty;

        /** The (binary) slot for which this is an operation. */
        final Slot slot;
        /** the type on which we find this implemented. */
        final PyType type;
        /** All the implementations, arrayed by argument class. */
        final MethodHandle[][] mh;

        /**
         * Construct a grid for the given operation and type.
         *
         * @param slot of the binary operation
         * @param type in which the definition is being made
         */
        BinopGrid(Slot slot, PyType type) {
            assert slot.signature == Signature.BINARY;
            this.slot = slot;
            this.type = type;
            final int N = type.acceptedCount;
            final int M = type.classes.length;
            this.mh = new MethodHandle[N][M];
        }

        /**
         * Post the definition for the {@link #slot} applicable to the
         * classes in the method type. The handle must be the "raw"
         * handle to the class-specific implementation, while the posted
         * value (later returned by {@link #get(Class, Class)} will have
         * the signature {@link Signature#BINARY}.
         *
         * @param mh handle to post
         */
        void add(MethodHandle mh)
                throws WrongMethodTypeException, InterpreterError {
            MethodType mt = mh.type();
            // Cast fails if the signature is incorrect for the slot
            mh = mh.asType(slot.getType());
            // Find cell based on argument types
            int i = type.indexAccepted(mt.parameterType(0));
            int j = type.indexOperand(mt.parameterType(1));
            if (i >= 0 && j >= 0) {
                this.mh[i][j] = mh;
            } else {
                /*
                 * The arguments to m are not (respectively) an accepted
                 * class and an operand class for the type. Type spec
                 * and the declared binary ops disagree?
                 */
                throw new InterpreterError(
                        "unexpected signature of %s.%s: %s", type.name,
                        slot.methodName, mt);
            }
        }

        /**
         * Check that every valid combination of classes has been added
         * (therefore leads to a non-null method handle).
         *
         * @throws InterpreterError if a {@code null} was found
         */
        void checkFilled() throws InterpreterError {
            final int N = type.acceptedCount;
            final int M = type.classes.length;
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M; j++) {
                    if (mh[i][j] == null) {
                        /*
                         * There's a gap in the table. Type spec and the
                         * declared binary ops disagree?
                         */
                        throw new InterpreterError(
                                "binary op not defined: %s(%s, %s)",
                                slot.methodName,
                                type.classes[i].getSimpleName(),
                                type.classes[j].getSimpleName());
                    }
                }
            }
        }

        /**
         * Get the method handle of an implementation
         * {@code Object op(V v, W w)} specialised to the given classes.
         * If {@code V} is an accepted implementation of this type, and
         * {@code W} is an operand class, the return will be a handle on
         * an implementation accepting those classes. If no
         * implementation is available for those classes (which means
         * they are not accepted and operand types for the Python type)
         * an empty slot handle is returned.
         *
         * @param accepted class of first argument to method
         * @param operand class of second argument to method
         * @return the special-to-class binary operation
         */
        MethodHandle get(Class<?> accepted, Class<?> operand) {
            // Find cell based on argument types
            int i = type.indexAccepted(accepted);
            int j = type.indexOperand(operand);
            if (i >= 0 && j >= 0) {
                return mh[i][j];
            } else {
                return BINARY_EMPTY;
            }
        }

        /**
         * Convenience method allowing look-up equivalent to
         * {@link #get(Class, Class)}, but using the {@code Operations}
         * objects as a proxy for the actual classes.
         *
         * @param accepted class of first argument to method
         * @param operand class of second argument to method
         * @return the special-to-class binary operation
         */
        MethodHandle get(Operations accepted, Operations operand) {
            return get(accepted.getJavaClass(), operand.getJavaClass());
        }
    }

    /**
     * Map a Java class to the {@code Operations} object that provides
     * Python semantics to instances of the class.
     *
     * @param c class on which operations are required
     * @return {@code Operations} providing Python semantics
     */
    static Operations forClass(Class<?> c) {
        // Normally, this is completely straightforward
        // TODO deal with re-entrancy and concurrency
        return registry.get(c);
    }

    /**
     * Map an object to the {@code Operations} object that provides it
     * with Python semantics.
     *
     * @param obj on which operations are required
     * @return {@code Operations} providing Python semantics
     */
    static Operations of(Object obj) {
        return forClass(obj.getClass());
    }

    /**
     * Get the Python type of the object <i>given that</i> this is the
     * operations object for it.
     *
     * @param x subject of the enquiry
     * @return {@code type(x)}
     */
    abstract PyType type(Object x);

    /**
     * Identify by index which Java implementation of the associated
     * type this {@code Operations} object is for. (Some types have
     * multiple acceptable implementations.)
     *
     * @return index in the type (0 if canonical)
     */
    int getIndex() { return 0; }

    /**
     * Get the Java implementation class this {@code Operations} object
     * is for.
     *
     * @return class of the implementation
     */
    abstract Class<?> getJavaClass();

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

    Target getTarget(Slot slot, Object v) {
        // TODO Auto-generated method stub
        return null;
    }

    Target getTarget(Slot slot, Object v, Object w) {
        // TODO Auto-generated method stub
        return null;
    }

    // ---------------------------------------------------------------

    /**
     * Operations for accepted implementations (non-canonical
     * implementations) are represented by instances of this class. The
     * canonical implementation is represented by the {@link PyType}
     * itself.
     */
    static class Accepted extends Operations {

        /** The type of which this is an accepted implementation. */
        final private PyType type;

        /**
         * Index of this implementation in the type (see
         * {@link PyType#indexAccepted(Class)}.
         */
        final private int index;

        /**
         * Create an operations object that is the {@code n}th
         * implementation of the given type. ({@code n>0} since the
         * implementation 0 is represented by the type itself.)
         *
         * @param type of which this is an accepted implementation
         * @param n index of this implementation in the type
         */
        Accepted(PyType type, int n) {
            this.type = type;
            this.index = n;
            setAllSlots();
        }

        @Override
        PyType type(Object x) {
            return type;
        }

        @Override
        boolean isIntExact() { return type == PyLong.TYPE; }

        @Override
        boolean isFloatExact() { return type == PyFloat.TYPE; }

        @Override
        int getIndex() { return index; }

        @Override
        Class<?> getJavaClass() { return type.classes[index]; }

        /**
         * Set all the slots ({@code op_*}) from the entries in the
         * dictionaries of this type and its bases.
         */
        private void setAllSlots() {
            for (Slot s : Slot.values()) {
                if (s.signature.kind == Slot.MethodKind.INSTANCE) {
                    Object def = type.lookup(ID.intern(s.methodName));
                    s.setSlot(this, def);
                }
            }
        }

        @Override
        public String toString() {
            String javaName = getJavaClass().getSimpleName();
            return javaName + " as " + type.toString();
        }
    }

    // ---------------------------------------------------------------

    // XXX class representing operations of sub-type?
    // XXX class representing operations of Java type?

    // ---------------------------------------------------------------

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
    MethodHandle op_pos;
    MethodHandle op_abs;
    MethodHandle op_invert;

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
