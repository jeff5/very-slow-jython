package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.HashMap;
import java.util.Map;

/**
 * This {@code enum} provides a set of structured constants that are
 * used to refer to the special methods of the Python data model.
 * <p>
 * These are structured constants that provide not only the
 * {@code String} method name, but also a signature, and much
 * information used internally by the run-time system in the creation of
 * type objects, the interpretation of code and the creation of call
 * sites.
 * <p>
 * In principle, any Python object may support all of the special
 * methods, through "slots" in the Python type object {@code PyType}.
 * These slots have identical names to the corresponding constant in
 * this {@code enum}. The "slots" in the Python type object hold
 * pointers ({@code MethodHandle}s) to their implementations in Java for
 * that type, which of course define the behaviour of instances in
 * Python. Where a special method is absent from the implementation of a
 * type, a default "empty" handle is provided from the {@code Slot}
 * constant.
 */
// Compare CPython struct wrapperbase in descrobject.h
// also typedef slotdef and slotdefs[] table in typeobject.h
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
    op_next(Signature.UNARY), //

    op_get(Signature.DESCRGET), //
    op_set(Signature.SETITEM), //
    op_delete(Signature.DELITEM), //

    op_init(Signature.INIT), //
    op_new(Signature.NEW), //

    op_vectorcall(Signature.VECTORCALL), //

    op_neg(Signature.UNARY, "unary -"), //
    op_pos(Signature.UNARY, "unary +"), //
    op_abs(Signature.UNARY, "abs()"), //
    op_invert(Signature.UNARY, "unary ~"), //

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
    /** Handle to throw a {@link TypeError} (same signature as slot). */
    private MethodHandle operandError;
    /** Description to use in help messages */
    final String doc;
    /** Reference to field holding this slot in a {@link PyType} */
    final VarHandle slotHandle;
    /** The alternate slot e.g. {@code __radd__} in {@code __add__}. */
    final Slot alt;

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
        this.alt = alt;
        // XXX Need something convenient as in CPython.
        this.doc = "Doc of " + this.opName;
    }

    Slot(Signature signature) {
        this(signature, null, null, null);
    }

    Slot(Signature signature, String opName) {
        this(signature, opName, null, null);
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
     * Lookup by method name, returning {@code null} if it is not a
     * recognised name for any slot.
     *
     * @param name of a (possible) special method
     * @return the Slot corresponding, or {@code null}
     */
    public static Slot forMethodName(String name) {
        return Util.getMethodNameTable().get(name);
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
    MethodType getType() { return signature.empty.type(); }

    /**
     * Get the default that fills the slot when it is "empty".
     *
     * @return empty method handle for this type of slot
     */
    MethodHandle getEmpty() { return signature.empty; }

    /**
     * Get a handle to throw a {@link TypeError} with a message
     * conventional for the slot. This handle has the same signature as
     * the slot, and some data specific to the slot. This is useful when
     * the target of a call site may have to raise a type error.
     *
     * @return throwing method handle for this type of slot
     */
    MethodHandle getOperandError() {
        // Not in the constructor so as not to provoke PyType
        if (operandError == null) {
            // Possibly racing, but that's harmless
            operandError = Util.operandError(this);
        }
        return operandError;
    }

    /**
     * Test whether this slot is non-empty in the given operations
     * object.
     *
     * @param ops to examine for this slot
     * @return true iff defined (non-empty)
     */
    boolean isDefinedFor(Operations ops) {
        return slotHandle.get(ops) != signature.empty;
    }

    /**
     * Get the contents of this slot in the given operations object.
     * Each member of this {@code enum} corresponds to a method handle
     * of the name which must also have the correct signature.
     *
     * @param ops target operations object
     * @return current contents of this slot in {@code ops}
     */
    MethodHandle getSlot(Operations ops) {
        return (MethodHandle) slotHandle.get(ops);
    }

    /**
     * Get the contents of the "alternate" slot in the given operations
     * object. For a binary operation this is the reflected operation.
     *
     * @param ops target operations object
     * @return current contents of the alternate slot in {@code t}
     * @throws NullPointerException if there is no alternate
     */
    MethodHandle getAltSlot(Operations ops)
            throws NullPointerException {
        return (MethodHandle) alt.slotHandle.get(ops);
    }

    /**
     * Set the contents of this slot in the given operations object to
     * the {@code MethodHandle} provided.
     *
     * @param ops target type object
     * @param mh handle value to assign
     */
    void setSlot(Operations ops, MethodHandle mh) {
        if (mh == null || !mh.type().equals(getType()))
            throw slotTypeError(this, mh);
        slotHandle.set(ops, mh);
    }

    /**
     * Set the contents of this slot in the given operations object to a
     * {@code MethodHandle} that calls the object given in a manner
     * appropriate to its type. This method is used when updating
     * setting the operation slots of a new type from the new type's
     * dictionary, and when updating them after a change. The object
     * argument is then the entry found by lookup of this slot's name.
     * It may be {@code null} if no entry was found.
     * <p>
     * Where the object is a {@link PyWrapperDescr}, the wrapped method
     * handle will be set as by
     * {@link #setSlot(Operations, MethodHandle)}. The
     * {@link PyWrapperDescr#slot} is not necessarily this slot: client
     * Python code can enter any wrapper descriptor against the name.
     *
     * @param ops target {@code Operations} (or {@code PyType}).
     * @param def object defining the handle to set (or {@code null})
     */
    // Compare CPython update_one_slot in typeobject.c
    void setSlot(Operations ops, Object def) {
        MethodHandle mh;
        if (def == null) {
            // No definition available for the special method
            if (this == op_next) {
                // XXX We should special-case __next__
                /*
                 * In CPython, this slot is sometimes null=empty, and
                 * sometimes _PyObject_NextNotImplemented. PyIter_Check
                 * checks both, but PyIter_Next calls it without
                 * checking and a null would then cause a crash. We have
                 * EmptyException for a similar purpose.
                 */
            }
            mh = signature.empty;

        } else if (def instanceof PyWrapperDescr) {
            // Subject to certain checks, take wrapped handle.
            PyWrapperDescr wd = (PyWrapperDescr) def;
            if (wd.slot.signature == signature) {
                if (signature.kind == MethodKind.INSTANCE) {
                    /*
                     * wd is an attribute of ops.type(), but since it
                     * may be one by inheritance, the handle we want
                     * from it may be at a different index from
                     * ops.index.
                     */
                    Class<?> selfClass = ops.getJavaClass();
                    int index = wd.objclass.indexAccepted(selfClass);
                    mh = wd.wrapped[index];
                } else {
                    mh = wd.wrapped[0];
                }
            } else {
                throw new MissingFeature(
                        "equivalent of the slot_* functions");
                // mh = signature.slotCalling(def);
            }

//@formatter:off
//        } else if (def instanceof PyJavaFunction) {
//            // We should be able to do this efficiently ... ?
//            // PyJavaFunction func = (PyJavaFunction) def;
//            if (this == op_next)
//                throw new MissingFeature(
//                        "special case caller for __new__ wrapper");
//            throw new MissingFeature(
//                    "Efficient handle from PyJavaFunction");
//            // mh = signature.slotCalling(func);
//@formatter:on

        } else if (def == Py.None && this == op_hash) {
            throw new MissingFeature("special case __hash__ == None");
            // mh = PyObject_HashNotImplemented

        } else {
            throw new MissingFeature(
                    "equivalent of the slot_* functions");
            // mh = makeSlotHandle(wd);
        }

        slotHandle.set(ops, mh);
    }

    /** The type of exception thrown by invoking an empty slot. */
    static class EmptyException extends Exception {

        // Suppression and stack trace disabled since singleton.
        EmptyException() {
            super(null, null, false, false);
        }
    }

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting the class defining the slot function actually bound into
     * the slot. See {@link MethodKind#INSTANCE}.
     */
    interface Self {}

    /**
     * Placeholder type, exclusively for use in slot signatures,
     * denoting {@code PyType} but signalling a class method. See
     * {@link MethodKind#CLASS}.
     */
    interface Cls {}

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
         * have {@code Object} in that position. When we look up the
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

        /*
         * The makeDescriptor overrides returning anonymous sub-classes
         * of PyWrapperDescr are fairly ugly. However, sub-classes seem
         * to be the right solution, and defining them here keeps
         * information together that belongs together.
         */

        /**
         * The signature {@code (S)O}, for example {@link Slot#op_repr}
         * or {@link Slot#op_neg}.
         */
        UNARY(O, S) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 0, kwargs);
                        return wrapped.invokeExact(self);
                    }
                };
            }
        },

        /**
         * The signature {@code (S,O)O}, for example {@link Slot#op_add}
         * or {@link Slot#op_getitem}.
         */
        BINARY(O, S, O) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 1, kwargs);
                        return wrapped.invokeExact(self, args.value[0]);
                    }
                };
            }
        },
        /**
         * The signature {@code (S,O,O)O}.
         */
        // The signature {@code (S,O,O)O}, used for {@link Slot#op_pow}.
        // **
        TERNARY(O, S, O, O),

        /**
         * The signature {@code (S,O,TUPLE,DICT)O}, used for
         * {@link Slot#op_call}.
         */
        // u(self, *args, **kwargs)
        CALL(O, S, TUPLE, DICT) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        return wrapped.invokeExact(self, args, kwargs);
                    }
                };
            }
        },

        // u(x, y, ..., a=z)
        VECTORCALL(O, S, OA, I, I, TUPLE),

        // Slot#op_bool
        PREDICATE(B, S),

        // Slot#op_contains
        BINARY_PREDICATE(B, S, O),

        // Slot#op_length, Slot#op_hash
        LEN(I, S) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 0, kwargs);
                        return (int) wrapped.invokeExact(self);
                    }
                };
            }
        },

        // (objobjargproc) Slot#op_setitem, Slot#op_set
        SETITEM(V, S, O, O),

        // (not in CPython) Slot#op_delitem, Slot#op_delete
        DELITEM(V, S, O),

        // (getattrofunc) Slot#op_getattr
        GETATTR(O, S, U) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 1, kwargs);
                        String name = args.value[0].toString();
                        return wrapped.invokeExact(self, name);
                    }
                };
            }
        },

        // (setattrofunc) Slot#op_setattr
        SETATTR(V, S, U, O) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped, Object self,
                            PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 2, kwargs);
                        String name = args.value[0].toString();
                        wrapped.invokeExact(self, name, args.value[1]);
                        return Py.None;
                    }
                };
            }
        },

        // (not in CPython) Slot#op_delattr
        DELATTR(V, S, U) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped, Object self,
                            PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 1, kwargs);
                        String name = args.value[0].toString();
                        wrapped.invokeExact(self, name);
                        return Py.None;
                    }
                };
            }
        },

        // (descrgetfunc) Slot#op_get
        DESCRGET(O, S, O, T) {

            @Override
            PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                    MethodHandle[] wrapped) {
                return new PyWrapperDescr(objclass, slot, wrapped) {

                    @Override
                    Object callWrapped(MethodHandle wrapped,
                            Object self, PyTuple args, PyDict kwargs)
                            throws Throwable {
                        checkArgs(args, 1, 2, kwargs);
                        Object[] a = args.value;
                        Object obj = a[0];
                        if (obj == Py.None) { obj = null; }
                        Object type = null;
                        if (a.length > 1 && type != Py.None) {
                            type = a[1];
                        }
                        if (type == null && obj == null) {
                            throw new TypeError(
                                    "__get__(None, None) is invalid");
                        }
                        return wrapped.invokeExact(self, obj,
                                (PyType) type);
                    }
                };
            }
        },

        /**
         * The signature {@code (S,O,TUPLE,DICT)V}, used for
         * {@link Slot#op_init}.
         */
        // (initproc) Slot#op_init
        INIT(V, S, TUPLE, DICT),

        // (newfunc) Slot#op_new
        NEW(O, T, TUPLE, DICT);

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
            // In the type of this.empty, replace Self with Object.
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

        /**
         * Return an instance of sub-class of {@link PyWrapperDescr},
         * specialised to the particular signature by overriding
         * {@link PyWrapperDescr#callWrapped(Object, PyTuple, PyDict)}.
         * Each member of {@code Signature} produces the appropriate
         * sub-class.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped handles to the implementations of that slot
         * @return a slot wrapper descriptor
         */
        // XXX should be abstract, but only when defined for each
        /*
         * abstract
         */ PyWrapperDescr makeSlotWrapper(PyType objclass, Slot slot,
                MethodHandle[] wrapped) {
            return new PyWrapperDescr(objclass, slot, wrapped) {

                @Override
                Object callWrapped(MethodHandle wrapped, Object self,
                        PyTuple args, PyDict kwargs) throws Throwable {
                    checkNoArgs(args, kwargs);
                    return wrapped.invokeExact(self);
                }
            };
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

        private static Map<String, Slot> methodNameTable = null;

        static Map<String, Slot> getMethodNameTable() {
            if (methodNameTable == null) {
                Slot[] slots = Slot.values();
                methodNameTable = new HashMap<>(2 * slots.length);
                for (Slot s : slots) {
                    methodNameTable.put(s.methodName, s);
                }
            }
            return methodNameTable;
        }

        /**
         * Helper for {@link Slot} constructors at the point they need a
         * handle for their named field within an {@code Operations}
         * class.
         */
        static VarHandle slotHandle(Slot slot) {
            Class<?> opsClass = Operations.class;
            try {
                // The field has the same name as the enum
                return LOOKUP.findVarHandle(opsClass, slot.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new InterpreterError(e,
                        "creating handle for %s in %s", slot.name(),
                        opsClass.getSimpleName());
            }
        }

        /**
         * Helper for {@link Slot} and thereby for call sites providing
         * a method handle that throws a Python exception when invoked,
         * with an appropriate message for the operation.
         * <p>
         * To be concrete, if the slot is a binary operation, the
         * returned handle may throw something like {@code TypeError:
         * unsupported operand type(s) for -: 'str' and 'str'}.
         *
         * @param slot to mention in the error message
         * @return a handle that throws the exception
         */
        static MethodHandle operandError(Slot slot) {
            // The type of the method that creates the TypeError
            MethodType errorMT =
                    slot.getType().insertParameterTypes(0, Slot.class)
                            .changeReturnType(PyException.class);
            // Exception thrower with nominal return type of the slot
            // thrower = λ(e): throw e
            MethodHandle thrower = MethodHandles.throwException(
                    slot.getType().returnType(), PyException.class);

            try {
                /*
                 * Look up a method f to create the exception, when
                 * applied the arguments v, w, ... (types matching the
                 * slot signature) prepended with this slot. We'll only
                 * call it if the handle is invoked.
                 */
                // error = λ(slot, v, w, ...): f(slot, v, w, ...)
                MethodHandle error;
                switch (slot.signature) {
                    case UNARY:
                        // Same name, although signature differs ...
                    case BINARY:
                        error = LOOKUP.findStatic(Number.class,
                                "operandError", errorMT);
                        break;
                    default:
                        // error = λ(slot): default(slot, v, w, ...)
                        error = LOOKUP.findStatic(Util.class,
                                "defaultOperandError", errorMT);
                        // error = λ(slot, v, w, ...): default(slot)
                        error = MethodHandles.dropArguments(error, 0,
                                slot.getType().parameterArray());
                }

                // A handle that creates and throws the exception
                // λ(v, w, ...): throw f(slot, v, w, ...)
                return MethodHandles.collectArguments(thrower, 0,
                        error.bindTo(slot));

            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InterpreterError(e,
                        "creating handle for type error", slot.name());
            }
        }

        /** Uninformative exception, mentioning the slot. */
        @SuppressWarnings("unused")  // reflected in operandError
        static PyException defaultOperandError(Slot op) {
            return new TypeError("bad operand type for %s", op.opName);
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
         * ({@code Object}) in place of this dummy, since at the call
         * site, we only know the target is a {@code Object}.
         * <p>
         * Further, when seeking an implementation of the special method
         * that is static, the definition will usually have the defining
         * type in "self" position, and so {@code Lookup.findStatic}
         * must be provided a type signature in which the lookup class
         * appears as "self".
         *
         * (Exception: {@link PyBaseObject} has to be defined with
         * static methods and the type Object in "self" position, the
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
        private static MethodType replaceSelf(MethodType type,
                Class<?> c) {
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
