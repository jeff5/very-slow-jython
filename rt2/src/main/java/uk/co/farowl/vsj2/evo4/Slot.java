package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
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

    op_repr(Signature.UNARY), //
    op_hash(Signature.LEN), //
    op_call(Signature.CALL), //
    op_str(Signature.UNARY), //

    op_getattribute(Signature.GETATTR), //
    op_getattr(Signature.GETATTR), //
    op_setattr(Signature.SETATTR), //
    op_delattr(Signature.DELATTR), //

    op_lt(Signature.BINARY), //
    op_le(Signature.BINARY), //
    op_eq(Signature.BINARY), //
    op_ne(Signature.BINARY), //
    op_ge(Signature.BINARY), //
    op_gt(Signature.BINARY), //

    op_iter(Signature.UNARY), //

    op_get(Signature.DESCRGET), //
    op_set(Signature.SETITEM), //
    op_delete(Signature.DELITEM), //

    op_init(Signature.INIT), //
    op_new(Signature.NEW), //

    op_vectorcall(Signature.VECTORCALL), //

    op_neg(Signature.UNARY), //
    op_abs(Signature.UNARY), //

    // Binary ops: reflected form comes first so we can reference it.
    op_radd(Signature.BINARY, "+"), //
    op_rsub(Signature.BINARY, "-"), //
    op_rmul(Signature.BINARY, "*"), //
    op_rand(Signature.BINARY, "&"), //
    op_rxor(Signature.BINARY, "^"), //
    op_ror(Signature.BINARY, "|"), //

    op_add(Signature.BINARY, "+", op_radd), //
    op_sub(Signature.BINARY, "-", op_rsub), //
    op_mul(Signature.BINARY, "*", op_rmul), //
    op_and(Signature.BINARY, "&", op_rand), //
    op_xor(Signature.BINARY, "^", op_rxor), //
    op_or(Signature.BINARY, "|", op_ror), //

    /** Handle to {@code __bool__} with {@link Signature#PREDICATE} */
    op_bool(Signature.PREDICATE), //
    op_int(Signature.UNARY), //
    op_float(Signature.UNARY), //
    op_index(Signature.UNARY), //

    op_len(Signature.LEN), //

    op_contains(Signature.BINARY_PREDICATE), //

    op_getitem(Signature.BINARY), //
    op_setitem(Signature.SETITEM), //
    op_delitem(Signature.DELITEM);

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
     * @param alt alternate slot (e.g. "op_radd")
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
            if (i == 2)
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
     *
     * @return conventional special method name.
     */
    String getMethodName() { return methodName; }

    /**
     * Return the invocation type of slots of this name.
     *
     * @return the invocation type of slots of this name.
     */
    MethodType getType() {
        return signature.empty.type();
    }

    /**
     * Get the default that fills the slot when it is "empty".
     *
     * @return empty method handle for this type of slot
     */
    MethodHandle getEmpty() { return signature.empty; }

    /**
     * Test whether this slot is non-empty in the given type.
     *
     * @param t type to examine for this slot
     * @return true iff defined (non-empty)
     */
    boolean isDefinedFor(PyType t) {
        return (MethodHandle) slotHandle.get(t) != signature.empty;
    }

    /**
     * Return for a slot, a handle to the method in a given class that
     * implements it, or the default handle (of the correct signature)
     * that throws {@link EmptyException}.
     *
     * @param c target class
     * @param lookup authorisation to access {@code c}
     * @return handle to method in {@code c} implementing this slot, or
     *         appropriate "empty" if no such method is accessible.
     */
    MethodHandle findInClass(Class<?> c, Lookup lookup) {
        try {
            switch (signature.kind) {
                case INSTANCE:
                    return Util.findVirtualInClass(this, c, lookup);
                case CLASS:
                case STATIC:
                    return Util.findStaticInClass(this, c, lookup);
                default:
                    // Never happens
            }
        } catch (NoSuchMethodException | IllegalAccessException e) {}
        return getEmpty();
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
     * Get the contents of the "alternate" slot in the given type. For a
     * binary operation this is the reflected operation.
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
    static class EmptyException extends Exception {

        // Suppression and stack trace disabled since singleton.
        EmptyException() { super(null, null, false, false); }
    }

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting the class defining the slot function actually bound into
     * the slot. See {@link MethodKind#INSTANCE}.
     */
    interface Self extends PyObject {}

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting {@code PyType} but signalling a class method. See
     * {@link MethodKind#CLASS}.
     */
    interface Cls extends PyObject {}

    /**
     * The kind of special method that satisfies this slot. Almost all
     * slots are satisfied by an instance method. __new__ is a static
     * method. In theory, we need class method as a type, but there are
     * no live examples.
     */
    enum MethodKind {
        /**
         * The slot is satisfied by Java instance method. The first
         * parameter type in the declared signature will have been the
         * placeholder {@code Self}. The operation slot signature will
         * have {@code PyObject} in that position. When we look up the
         * Java implementation we will look for a virtual method using a
         * method type that is the declared type with {@code Self}
         * removed. When called, the target object has a type assignable
         * to the receiving type, thanks to a checked cast. The result
         * is that the defining methods need not include a cast to their
         * type on the corresponding argument.
         */
        INSTANCE,
        /**
         * The slot is satisfied by Java static method. The first
         * parameter type in the declared signature will have been the
         * placeholder {@code Cls}. The operation slot signature will
         * have {@code PyType} in that position. When we look up the
         * Java implementation we will look for a static method using a
         * method type that is the declared type with {@code Cls}
         * replaced by {@code PyType}. When called, this type object is
         * a sub-type (or the same as) the type implemented by the
         * receiving type.
         */
        // At least, that's what would happen if we used it :/
        CLASS,
        /**
         * The slot is satisfied by Java static method. The first
         * parameter type in the declared signature will have been
         * something other than {@code Self} or {@code Cls}. The
         * operation slot signature will be the same. When we look up
         * the Java implementation we will look for a static method
         * using the method type as declared type.
         */
        STATIC;
    }

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
        UNARY(O, S), // op_negative, op_invert
        BINARY(O, S, O), // +, -, u[v]
        TERNARY(O, S, O, O), // **
        CALL(O, S, TUPLE, DICT), // u(self, *args, **kwargs)
        VECTORCALL(O, S, OA, I, I, TUPLE), // u(x, y, ..., a=z)
        PREDICATE(B, S), // op_bool
        BINARY_PREDICATE(B, S, O), // op_contains
        LEN(I, S), // op_length, op_hash
        SETITEM(V, S, O, O), // (objobjargproc) op_setitem, op_set
        DELITEM(V, S, O), // (not in CPython) op_delitem, op_delete
        GETATTR(O, S, U), // (getattrofunc) op_getattr
        SETATTR(V, S, U, O), // (setattrofunc) op_setattr
        DELATTR(V, S, U), // (not in CPython) op_delattr
        DESCRGET(O, S, O, T), // (descrgetfunc) op_get
        INIT(V, S, TUPLE, DICT), // (initproc) op_init
        NEW(O, T, TUPLE, DICT); // (newfunc) op_new

        /**
         * The signature was defined with this nominal method type,
         * which will often include a {@link Self} placeholder
         * parameter.
         */
        final MethodType type;
        /**
         * Whether instance, static or class method. This determines the
         * kind of lookup we must perform on the implementing class.
         */
        final MethodKind kind;
        /**
         * When we do the lookup, this is the method type we specify,
         * derived from {@link #type} according to {@link #kind}.
         */
        final MethodType methodType;
        /**
         * When empty, the slot should hold this handle. The method type
         * of this handle also tells us the method type by which the
         * slot must always be invoked, see {@link Slot#getType()}.
         */
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

            // Prepare the kind of lookup we should do
            Class<?> p0 = ptypes.length > 0 ? ptypes[0] : null;
            if (p0 == Self.class) {
                this.kind = MethodKind.INSTANCE;
                this.methodType = Util.dropSelf(this.type);
                // } else if (p0 == Cls.class) { ... CLASS ...
            } else {
                this.kind = MethodKind.STATIC;
                this.methodType = this.empty.type();
            }
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
        private static final Lookup LOOKUP = MethodHandles.lookup();

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
                throw new InterpreterError(e, "seeking slot %s in %s",
                        slot.name(), methodsClass.getSimpleName());
            }
        }

        /**
         * For a given slot, return a handle to the instance method in a
         * given class that implements it, or the default handle (of the
         * correct signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c target class
         * @param lookup authorisation to access {@code c}
         * @return handle to method in {@code c} implementing {@code s},
         *         or appropriate "empty" if no such method is
         *         accessible.
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findVirtualInClass(Slot slot, Class<?> c,
                Lookup lookup)
                throws IllegalAccessException, NoSuchMethodException {
            // PyBaseObject has a different approach
            if (c == PyBaseObject.class)
                return findInBaseObject(slot, lookup);
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            Signature sig = slot.signature;
            assert sig.kind == MethodKind.INSTANCE;
            try {
                // The standard implementation
                MethodType mt = sig.methodType;
                MethodHandle impl = lookup.findVirtual(c, name, mt);
                // The invocation type remains that of slot.empty
                return impl.asType(sig.empty.type());
            } catch (NoSuchMethodException e) {}
            /*
             * We have some object implementations in which the
             * implementation method is actually Java static. Have to
             * try both implementations for the time being.
             */
            // XXX remove try and this block needed only while static
            MethodType mt = replaceSelf(sig.type, c);
            MethodHandle impl = lookup.findStatic(c, name, mt);
            return impl.asType(slot.getType());
        }

        /**
         * For a given slot, return a handle to the static method in a
         * given class that implements it, or the default handle (of the
         * correct signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c class
         * @param lookup authorisation to access {@code c}
         * @return handle to method in {@code c} implementing {@code s},
         *         or appropriate "empty" if no such method is
         *         accessible.
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findStaticInClass(Slot slot, Class<?> c,
                Lookup lookup)
                throws NoSuchMethodException, IllegalAccessException {
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            Signature sig = slot.signature;
            assert sig.kind == MethodKind.STATIC;
            MethodType mt = sig.methodType;
            MethodHandle impl = lookup.findStatic(c, name, mt);
            // The invocation type remains that of slot.empty
            return impl.asType(sig.empty.type());
        }

        /**
         * For a given slot, return a handle to the method in
         * {@link PyBaseObject}{@code .class}, or the default handle (of
         * the correct signature) that throws {@link EmptyException}.
         * The declarations of special methods in that class differ from
         * other implementation classes.
         *
         * @param slot slot
         * @param lookup authorisation to access {@code PyBaseObject}
         * @return handle to method in {@code PyBaseObject} implementing
         *         {@code s}, or appropriate "empty" if no such method
         *         is accessible.
         * @throws NoSuchMethodException slot method not found
         * @throws IllegalAccessException found but inaccessible
         */
        static MethodHandle findInBaseObject(Slot slot, Lookup lookup)
                throws NoSuchMethodException, IllegalAccessException {
            // The method has the same name in every implementation
            String name = slot.getMethodName();
            Signature sig = slot.signature;
            MethodType mt = replaceSelf(sig.type, PyObject.class);
            MethodHandle impl =
                    lookup.findStatic(PyBaseObject.class, name, mt);
            assert impl.type() == sig.empty.type();
            return impl;
        }

        /**
         * Generate a method type in which an initial occurrence of the
         * {@link Self} class has been replaced by a specified class.
         * <p>
         * The type signature of method handles to special functions
         * (see {@link Signature}) are mostly specified with the dummy
         * type {@code Self} as the first type parameter. This indicates
         * that the special method is an instance method. However, the
         * method handle offered to the run-time must have the generic
         * ({@code PyObject}) in place of this dummy, since at the call
         * site, we only know the target is a {@code PyObject}.
         * <p>
         * Further, when seeking an implementation of the special method
         * that is static, the definition will usually have the defining
         * type in "self" position, and so {@code Lookup.findStatic}
         * must be provided a type signature in which the lookup class
         * appears as "self".
         *
         * (Exception: {@link PyBaseObject} has to be defined with
         * static methods and the type PyObject in "self" position, the
         * same as the run-time expects.)
         * <p>
         * This method provides a way to convert {@code Self} to a
         * specified type in a method type, either the one to which a
         * static implementation is expected to conform, or the one
         * acceptable to the run-time. A method type that does not have
         * {@code Self} at parameter 0 is returned unchanged.
         *
         * @param type signature with the dummy {@link Self}
         * @param c class to substitute for {@link Self}
         * @return signature after substitution
         */
        static MethodType replaceSelf(MethodType type, Class<?> c) {
            int n = type.parameterCount();
            if (n > 0 && type.parameterType(0) == Self.class)
                return type.changeParameterType(0, c);
            else
                return type;
        }

        /**
         * Generate a method type from which an initial occurrence of
         * the {@link Self} class has been removed.
         * <p>
         * The signature of method handles to special functions (see
         * {@link Signature}) are mostly specified with the dummy type
         * {@code Self} as the first type parameter. This indicates that
         * the special method is an instance method.
         * <p>
         * When defining the implementation of a special method that is
         * an instance method, which is most of them, it is convenient
         * to make it an instance method in Java. Then the method type
         * we supply to {@code Lookup.findVirtual} must omit the "self"
         * parameter. This method generates that method type.
         *
         * @param type signature with the dummy {@link Self}
         * @return signature after removal
         */
        static MethodType dropSelf(MethodType type) {
            int n = type.parameterCount();
            if (n > 0 && type.parameterType(0) == Self.class)
                return type.dropParameterTypes(0, 1);
            else
                return type;
        }
    }
}
