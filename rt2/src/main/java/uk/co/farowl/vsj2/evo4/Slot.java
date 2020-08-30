package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

/**
 * This {@code enum} provide constants that can be are used to refer to
 * the "slots" within a {@code PyType}. These slots define the behaviour
 * of instances of the Python type it represents.
 * <p>
 * Each constant creates a correspondence between its name, the (slot)
 * name in the {@code PyType} object (because it is the same), the type
 * of the {@code MethodHandle} every occurrence of that slot must
 * contain, and the conventional name by which the implementing class of
 * a type will refer to that method, if it offers an implementation.
 */
enum Slot {

    tp_repr(Signature.UNARY), //
    tp_hash(Signature.LEN), //
    tp_call(Signature.CALL), //
    tp_str(Signature.UNARY), //

    tp_getattro(Signature.GETATTRO, null, "__getattr__"), //
    tp_setattro(Signature.SETATTRO, null, "__setattr__"), //

    tp_richcompare(Signature.RICHCMP), //
    tp_iter(Signature.UNARY), //

    tp_init(Signature.INIT), //
    tp_new(Signature.NEW), //

    tp_vectorcall(Signature.VECTORCALL), //

    nb_negative(Signature.UNARY, "-", "__neg__"), //
    nb_absolute(Signature.UNARY, null, "__abs__"), //

    // Binary ops: reflected form comes first so we can reference it.
    nb_radd(Signature.BINARY, "+"), //
    nb_rsub(Signature.BINARY, "-"), //
    nb_rmul(Signature.BINARY, "*"), //
    nb_rand(Signature.BINARY, "&"), //
    nb_rxor(Signature.BINARY, "^"), //
    nb_ror(Signature.BINARY, "|"), //

    nb_add(Signature.BINARY, "+", nb_radd), //
    nb_sub(Signature.BINARY, "-", nb_rsub), //
    nb_mul(Signature.BINARY, "*", nb_rmul), //
    nb_and(Signature.BINARY, "&", nb_rand), //
    nb_xor(Signature.BINARY, "^", nb_rxor), //
    nb_or(Signature.BINARY, "|", nb_ror), //

    nb_bool(Signature.PREDICATE), //
    nb_int(Signature.UNARY), //
    nb_float(Signature.UNARY), //
    nb_index(Signature.UNARY), //

    sq_length(Signature.LEN, null, "__len__"), //
    // sq_repeat(Signature.SQ_INDEX, "*", "__mul__"), //
    sq_item(Signature.SQ_INDEX, null, "__getitem__"), //
    sq_ass_item(Signature.SQ_ASSIGN, null, "__setitem__"), //

    // mp_length(Signature.LEN, null, "__len__"), //
    mp_subscript(Signature.BINARY, null, "__getitem__"), //
    mp_ass_subscript(Signature.MP_ASSIGN, null, "__setitem__");

    /** Method signature to match when filling this slot. */
    final Signature signature;
    /** Name of implementation method to bind to this slot. */
    final String methodName;
    /** Name to use in error messages */
    final String opName;
    /** Reference to field holding this slot in a {@link PyType} */
    final VarHandle slotHandle;
    /** Reference to field holding alternate slot in a {@link PyType} */
    final VarHandle altSlotHandle;

    /**
     * Constructor for enum constants.
     *
     * @param signature of the function to be called
     * @param opName symbol (such as "+")
     * @param methodName implementation method (e.g. "__add__")
     * @param alt alternate slot (e.g. "nb_radd")
     */
    Slot(Signature signature, String opName, String methodName,
            Slot alt) {
        this.opName = opName == null ? name() : opName;
        this.methodName = dunder(methodName);
        this.signature = signature;
        this.slotHandle = Util.slotHandle(this);
        this.altSlotHandle = alt == null ? null : alt.slotHandle;
    }

    Slot(Signature signature) { this(signature, null, null, null); }

    Slot(Signature signature, String opName) {
        this(signature, opName, null, null);
    }

    Slot(Signature signature, String opName, String methodName) {
        this(signature, opName, methodName, null);
    }

    Slot(Signature signature, Slot alt) {
        this(signature, null, null, alt);
    }

    Slot(Signature signature, String opName, Slot alt) {
        this(signature, opName, null, alt);
    }

    /** Compute corresponding double-underscore method name. */
    private String dunder(String methodName) {
        if (methodName != null)
            return methodName;
        else {
            String s = name();
            int i = s.indexOf('_');
            if (i > 0)
                s = "__" + s.substring(i + 1) + "__";
            return s;
        }
    }

    @Override
    public java.lang.String toString() {
        return "Slot." + name() + " ( " + methodName + signature.type
                + " ) [" + signature.name() + "]";
    }

    /**
     * Get the name of the method that, by convention, identifies the
     * corresponding operation in the implementing class. This is not
     * the same as the slot name.
     */
    String getMethodName() { return methodName; }

    /** The invocation type of slots of this name. */
    MethodType getType() {
        return signature.empty.type();
    }

    /** Get the default that fills the slot when it is "empty". */
    MethodHandle getEmpty() {
        return signature.empty;
    }

    /** Test whether this slot is non-empty in the given type. */
    boolean isDefinedFor(PyType t) {
        return (MethodHandle) slotHandle.get(t) != signature.empty;
    }

    /**
     * Return for a slot, a handle to the method in a given class that
     * implements it, or the default handle (of the correct signature)
     * that throws {@link EmptyException}.
     *
     * @param s slot
     * @param c target class
     * @return handle to method in {@code c} implementing this slot, or
     *         appropriate "empty" if no such method is accessible.
     */
    MethodHandle findInClass(Class<?> c) {
        return Util.findInClass(this, c);
    }

    /**
     * Get the contents of this slot in the given type. Each member of
     * this {@code enum} corresponds to the name of a static method
     * which must also have the correct signature.
     *
     * @param t target type
     * @return current contents of this slot in {@code t}
     */
    MethodHandle getSlot(PyType t) {
        return (MethodHandle) slotHandle.get(t);
    }

    /**
     * Get the contents of the "alternate" slot in the given type.
     * For a binary operation this is the reflected operation.
     *
     * @param t target type
     * @return current contents of the alternate slot in {@code t}
     */
    MethodHandle getAltSlot(PyType t) {
        return (MethodHandle) altSlotHandle.get(t);
    }

    /**
     * Set the contents of this slot in the given type to the
     * {@code MethodHandle} provided.
     *
     * @param t target type object
     * @param mh handle value to assign
     */
    void setSlot(PyType t, MethodHandle mh) {
        if (mh == null || !mh.type().equals(getType()))
            throw slotTypeError(this, mh);
        slotHandle.set(t, mh);
    }

    /** The type of exception thrown by invoking an empty slot. */
    static class EmptyException extends Exception {}

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting the class defining the slot function actually bound into
     * the slot. Wherever the {@code Self} type appears in the slot
     * signature, the signature of the method looked up in the
     * implementation will have the defining type, and the method handle
     * produced will have {@code PyObject}, and a checked cast. The
     * result is that the defining methods need not include a cast to
     * their type on the corresponding argument.
     */
    interface Self extends PyObject {}

    /**
     * An enumeration of the acceptable signatures for slots in a
     * {@code PyType}. For each {@code MethodHandle} we may place in a
     * slot, we must know in advance the acceptable signature
     * ({@code MethodType}), and the slot when empty must contain a
     * handle with this signature to a method that will raise
     * {@link EmptyException}, Each {@code enum} constant here gives a
     * symbolic name to that {@code MethodType}, and provides an
     * {@code empty} handle.
     * <p>
     * Names are equivalent to {@code typedef}s provided in CPython
     * {@code Include/object.h}, but not the same. We do not need quite
     * the same signatures as CPython: we do not return integer status,
     * for example. Also, C-specifics like {@code Py_ssize_t} are echoed
     * in the C-API names but not here.
     */
    enum Signature implements ClassShorthand {
        UNARY(O, S), // nb_negative, nb_invert
        BINARY(O, S, O), // +, -, u[v]
        TERNARY(O, S, O, O), // **
        CALL(O, S, TUPLE, DICT), // u(self, *args, **kwargs)
        VECTORCALL(O, S, OA, I, I, TUPLE), // u(x, y, ..., a=z)
        PREDICATE(B, S), // nb_bool
        LEN(I, S), // sq_length
        RICHCMP(O, S, O, CMP), // (richcmpfunc) tp_richcompare only
        SQ_INDEX(O, S, I), // (ssizeargfunc) sq_item, sq_repeat only
        SQ_ASSIGN(V, S, I, O), // (ssizeobjargproc) sq_ass_item only
        MP_ASSIGN(V, S, O, O), // (objobjargproc) mp_ass_subscript only
        GETATTRO(O, S, U), // (getattrofunc) tp_getattro
        SETATTRO(V, S, U, O), // (setattrofunc) tp_setattro
        INIT(V, S, TUPLE, DICT), // (initproc) tp_init
        NEW(O, T, TUPLE, DICT); // (newfunc) tp_new

        /**
         * A method handle offered to this slot must be based on this
         * type, with Self parameters.
         */
        final MethodType type;
        /** When empty, the slot should hold this handle. */
        final MethodHandle empty;

        /**
         * Constructor to which we specify the signature of the slot,
         * with the same semantics as {@code MethodType.methodType()}.
         * Every {@code MethodHandle} stored in the slot (including
         * {@link Signature#empty}) must be of this method type.
         *
         * @param returnType that the slot functions all return
         * @param ptypes types of parameters the slot function takes
         */
        Signature(Class<?> returnType, Class<?>... ptypes) {
            // The signature is recorded exactly as given
            this.type = MethodType.methodType(returnType, ptypes);
            // In the type of this.empty, replace Self with PyObject.
            MethodType invocationType = Util.replaceSelf(this.type, O);
            // em = λ : throw Util.EMPTY
            // (with correct nominal return type for slot)
            MethodHandle em = MethodHandles
                    .throwException(returnType, EmptyException.class)
                    .bindTo(Util.EMPTY);
            // empty = λ u v ... : throw Util.EMPTY
            // (with correct parameter types for slot)
            this.empty = MethodHandles.dropArguments(em, 0,
                    invocationType.parameterArray());
        }
    }

    /**
     * Helper for {@link Slot#setSlot(PyType, MethodHandle)}, when a bad
     * handle is presented.
     *
     * @param slot that the client attempted to set
     * @param mh offered value found unsuitable
     * @return exception with message filled in
     */
    private static InterpreterError slotTypeError(Slot slot,
            MethodHandle mh) {
        String fmt = "%s not of required type %s for slot %s";
        return new InterpreterError(fmt, mh, slot.getType(), slot);
    }

    /**
     * Helpers for {@link Slot} and {@link Signature} that can be used
     * in the constructors.
     */
    private static class Util {

        /*
         * This is a class separate from Slot to solve problems with the
         * order of static initialisation. The enum constants have to
         * come first, and their constructors are called as they are
         * encountered. This means that other constants in Slot are not
         * initialised by the time the constructors need them.
         */
        private static final MethodHandles.Lookup LOOKUP =
                MethodHandles.lookup();

        /** Single re-used instance of {@code Slot.EmptyException} */
        static final EmptyException EMPTY = new EmptyException();

        /**
         * Helper for constructors at the point they need a handle for
         * their named field within a {@code PyType} class.
         */
        static VarHandle slotHandle(Slot slot) {
            Class<?> methodsClass = PyType.class;
            try {
                // The field has the same name as the enum
                return LOOKUP.findVarHandle(methodsClass, slot.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new InterpreterError(e, "creating enum for %s",
                        methodsClass.getSimpleName());
            }
        }

        /**
         * For a given slot, return a handle to the method in a given
         * class that implements it, or the default handle (of the
         * correct signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c class
         * @return handle to method in {@code c} implementing {@code s},
         *         or appropriate "empty" if no such method is
         *         accessible.
         */
        static MethodHandle findInClass(Slot slot, Class<?> c) {
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            MethodType stype = slot.signature.type;
            try {
                /*
                 * Normally, the Self class in slot.signature.type will
                 * be the target class c
                 */
                MethodType mt = replaceSelf(stype, c);
                MethodHandle impl = LOOKUP.findStatic(c, name, mt);
                // The invocation type remains that of slot.empty
                return impl.asType(slot.getType());
            } catch (NoSuchMethodException
                    | IllegalAccessException e) {}
            // Try instead the object-based signature
            try {
                /*
                 * Optionally, the Self class in slot.signature.type may
                 * be PyObject
                 */
                MethodType mt = replaceSelf(stype, PyObject.class);
                MethodHandle impl = LOOKUP.findStatic(c, name, mt);
                // The invocation type remains that of slot.empty
                return impl.asType(slot.getType());
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return slot.getEmpty();
            }
        }

        /**
         * Generate a method type in which occurrences of the
         * {@link Self} class are replaced by the given class {@code c}.
         */
        static MethodType replaceSelf(MethodType type, Class<?> c) {
            int n = type.parameterCount();
            for (int i = 0; i < n; i++) {
                if (type.parameterType(i) == Self.class) {
                    type = type.changeParameterType(i, c);
                }
            }
            return type;
        }

    }
}
