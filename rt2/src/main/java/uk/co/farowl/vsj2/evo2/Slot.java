package uk.co.farowl.vsj2.evo2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/** Utilities to help construct slot functions. */
class Slot {

    private static final MethodHandles.Lookup LOOKUP =
            MethodHandles.lookup();

    static class EmptyException extends Exception {}

    private static final Class<PyObject> O = PyObject.class;
    private static final Class<?> I = int.class;
    private static final Class<?> B = boolean.class;
    private static final Class<?> V = void.class;
    private static final Class<Opcode.PyCmp> CMP = Opcode.PyCmp.class;

    private static final MethodType VOID = MethodType.methodType(V);

    private static MethodHandle NEWEX;

    static {
        try {
            // NEWEX = λ : new EmptyException
            NEWEX = LOOKUP.findConstructor(EmptyException.class, VOID);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            SystemError se = new SystemError("initialising type slots");
            se.initCause(e);
            throw se;
        }
    }

    /**
     * An enumeration of the acceptable signatures for slots in
     * {@code PyType.*Methods} tables. For each {@code MethodHandle} we
     * may place in a slot, we must know in advance the acceptable
     * signature ({@code MethodType}), and the slot when empty must
     * contain a handle to a method that will raise
     * {@link EmptyException}, which has the requisite signature. Each
     * {@code enum} constant here gives a symbolic name to that
     * {@code MethodType}, and provides an {@code empty} handle.
     * <p>
     * Names are equivalent to {@code typedef}s provided in CPython
     * {@code Include/object.h}, but not the same. We do not need quite
     * the same signatures as CPython: we do not return integer status,
     * for example. Also, C-specifics like {@code Py_ssize_t} are echoed
     * in the C-API names.
     */
    enum Signature {
        UNARY(O, O), // NB.negative, NB.invert
        BINARY(O, O, O), // +, -, u[v]
        TERNARY(O, O, O, O), // **
        PREDICATE(B, O), // NB.bool
        LEN(I, O), // SQ.length
        RICHCMP(O, O, O, CMP), // (richcmpfunc) TP.richcompare only
        SQ_INDEX(O, O, I), // (ssizeargfunc) SQ.item, SQ.repeat only
        SQ_ASSIGN(V, O, I, O), // (ssizeobjargproc) SQ.ass_item only
        MP_ASSIGN(V, O, O, O); // (objobjargproc) MP.ass_subscript only

        /** A method handle in this slot must have this type. */
        final MethodType type;
        /** When empty, the slot should hold this handle. */
        final MethodHandle empty;

        /**
         * Constructor to which we specify the signature of the slot,
         * with the same semantics as {@code MethodType.methodType()}.
         *
         * @param returnType that the slot functions all return
         * @param ptype types of parameters the slot function takes
         */
        Signature(Class<?> returnType, Class<?>... ptype) {
            // th = λ e : throw e (with nominally-correct return type)
            MethodHandle th = MethodHandles.throwException(returnType,
                    EmptyException.class);
            // em = λ : throw new EmptyException
            MethodHandle em = MethodHandles.foldArguments(th, NEWEX);
            // empty = λ u v ... : throw new EmptyException
            this.empty = MethodHandles.dropArguments(em, 0, ptype);
            // All handles in the slot must have the same type as empty
            this.type = this.empty.type(); // = (ptype...)returnType
        }

        private static final Map<MethodType, Signature> sigMap;
        static {
            sigMap = new HashMap<>();
            for (Signature sig : Signature.values()) {
                sigMap.put(sig.empty.type(), sig);
            }
        }

        static Signature matching(MethodType t) {
            return sigMap.get(t);
        }
    }

    /**
     * Constants for the {@code *Methods} compartments of a
     * {@link PyType} object. There should be a {@code *Slot enum} for
     * each {@code Group}.
     */
    enum Group {
        /**
         * Representing the "basic" group: slots where the name begins
         * {@code tp_}, that are found in the base {@code PyTypeObject},
         * in CPython.
         */
        TP(PyType.class),
        /**
         * Representing the "number" group: slots where the name begins
         * {@code nb_}, that are found in an optional
         * {@code PyNumberMethods} in CPython.
         */
        NB(PyType.NumberMethods.class),
        /**
         * Representing the "sequence" group: slots where the name
         * begins {@code sq_}, that are found in an optional
         * {@code PySequenceMethods} in CPython.
         */
        SQ(PyType.SequenceMethods.class),
        /**
         * Representing the "mapping" group: slots where the name begins
         * {@code mp_}, that are found in an optional
         * {@code PyMappingMethods} in CPython.
         */
        MP(PyType.MappingMethods.class),
        // For later use: replace Void with a PyType.*Methods class
        BF(Void.class), AM(Void.class);

        /** The {@code *Method} class corresponding. */
        final Class<?> methodsClass;

        Group(Class<?> methodsClass) {
            this.methodsClass = methodsClass;
        }
    }

    /**
     * This interface is implemented by the {@code enum} for each group
     * of "slots" that a {@code PyType} contains. These groups are the
     * {@code PyType.NumberMethods}, the {@code PyType.SequenceMethods},
     * the {@code MappingMethods}, the {@code BufferMethods}, and the
     * slots that are members of the {@code PyType} object itself.
     * <p>
     * These {@code enum}s provide constants that can be are used to
     * refer to these slots. Each constant in each {@code enum} creates
     * a correspondence between its name, the (slot) name in the
     * {@code *Methods} object (because it is the same), the type of the
     * {@code MethodHandle} that slot must contain, and the conventional
     * name by which the implementing class of a type will refer to that
     * method, if it offers one.
     */
    interface Any {

        /**
         * The group to which this slot belongs (implying a
         * {@code *Method} class that has a members with the same name
         * as this {@code enum} constant.
         */
        Group group();

        /** Name of the slot (supplied by Java for the {@code enum}). */
        String name();

        /**
         * Get the name of the method that, by convention, identifies
         * the corresponding operation in the implementing class. This
         * is not the same as the slot name.
         */
        String getMethodName();

        /** The type required for slots of this name. */
        MethodType getType();

        /** Get the default that fills the slot when it is "empty". */
        MethodHandle getEmpty();

        /** Test whether this slot is non-empty in the given type. */
        boolean isDefinedFor(PyType t);

        /**
         * Create the method handle for this operation, if possible, by
         * reflection on the given class. If the class has a
         * {@code static} method matching the proper name
         * {@link #getMethodName()} and method type
         * {@link #getSignature()}{@code .type}, return that.
         *
         *
         * Return for a slot, a handle to the method in a given class
         * that implements it, of the default handle (of the correct
         * signature) that throws {@link EmptyException}.
         *
         * @param s slot
         * @param c target class
         * @return handle to method in {@code c} implementing thios
         *         slot, or appropriate "empty" if no such method is
         *         accessible.
         */
        MethodHandle findInClass(Class<?> c);

        /**
         * Get the contents of this slot in the given type. Each member
         * of this {@code enum} corresponds to the name of a static
         * method which must also have the correct signature.
         *
         * @param t target type
         * @return current contents of this slot in {@code t}
         */
        MethodHandle getSlot(PyType t);

        /**
         * Set the contents of this slot in the given type to the
         * {@code MethodHandle} provided.
         *
         * @param t target type object
         * @param mh handle value to assign
         */
        void setSlot(PyType t, MethodHandle mh);
    }

    /** Common code supporting the {@code *Slot enum}s. */
    private static class EnumUtil {

        /**
         * Helper for implementations of
         * {@link Any#setSlot(PyType, MethodHandle)}, when a bad handle
         * is presented.
         *
         * @param slot that the client attempted to set
         * @param mh offered value found unsuitable
         * @return exception with message filled in
         */
        static InterpreterError slotTypeError(Any slot,
                MethodHandle mh) {
            String fmt = "%s not of required type %s for slot %s.%s";
            return new InterpreterError(fmt, mh, slot.getType(),
                    slot.group(), slot.toString());
        }

        /**
         * Helper for constructors of {@code *Slot enum}s at the point
         * they need a handle for their named field within a
         * {@code *Methods}s class.
         */
        static VarHandle slotHandle(Any slot) {
            Class<?> methodsClass = slot.group().methodsClass;
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
         * Return for a slot, a handle to the method in a given class
         * that implements it, of the default handle (of the correct
         * signature) that throws {@link EmptyException}.
         *
         * @param slot slot
         * @param c class
         * @return handle to method in {@code c} implementing {@code s},
         *         or appropriate "empty" if no such method is
         *         accessible.
         */
        static MethodHandle findInClass(Any slot, Class<?> c) {
            try {
                // The method has the same name in every implementation
                return LOOKUP.findStatic(c, slot.getMethodName(),
                        slot.getType());
            } catch (NoSuchMethodException | IllegalAccessException e) {
                return slot.getEmpty();
            }
        }
    }

    /**
     * An enumeration of the "basic" group: slots where the name begins
     * {@code tp_}, that are found in the base {@code PyTypeObject}, in
     * CPython.
     */
    enum TP implements Any {

        hash("hash", Signature.LEN), //
        repr("repr", Signature.UNARY), //
        str("str", Signature.UNARY);

        final String methodName;
        final MethodType type;
        final MethodHandle empty;
        final VarHandle slotHandle;

        TP(String methodName, Signature signature) {
            this.methodName = methodName;
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = EnumUtil.slotHandle(this);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return (MethodHandle) slotHandle.get(t);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            if (mh == null || !mh.type().equals(type))
                throw EnumUtil.slotTypeError(this, mh);
            slotHandle.set(t, mh);
        }

        @Override
        public Group group() { return Group.TP; }

        @Override
        public String getMethodName() { return methodName; }

        @Override
        public MethodType getType() { return type; }

        @Override
        public MethodHandle getEmpty() { return empty; }

        @Override
        public boolean isDefinedFor(PyType t) {
            return (MethodHandle) slotHandle.get(t) != empty;
        }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return EnumUtil.findInClass(this, c);
        }
    }

    /**
     * An enumeration of the "number" group: slots where the name begins
     * {@code nb_}, that are found in an optional
     * {@code PyNumberMethods} in CPython.
     */
    enum NB implements Any {

        negative(Signature.UNARY, "neg"), //
        add(Signature.BINARY), //
        subtract(Signature.BINARY, "sub"), //
        multiply(Signature.BINARY, "mul"), //
        index(Signature.UNARY); //

        final String methodName;
        final MethodType type;
        final MethodHandle empty;
        final VarHandle slotHandle;

        NB(Signature signature, String methodName) {
            this.methodName = methodName == null ? name() : methodName;
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = EnumUtil.slotHandle(this);
        }

        NB(Signature signature) { this(signature, null); }

        /**
         * Lookup this slot in the given object.
         *
         * @param m the {@code *Methods} object to consult
         * @return the {@code MethodHandle} from it
         */
        MethodHandle getSlot(PyType.NumberMethods m) {
            return (MethodHandle) slotHandle.get(m);
        }

        /**
         * Set this slot in the given object (if type-compatible).
         *
         * @param m the {@code *Methods} object to consult
         * @param mh the {@code MethodHandle} to set there
         */
        void setSlot(PyType.NumberMethods m, MethodHandle mh) {
            if (mh == null || !mh.type().equals(type))
                throw EnumUtil.slotTypeError(this, mh);
            slotHandle.set(m, mh);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return getSlot(t.number);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            assert t.number != PyType.NumberMethods.EMPTY;
            setSlot(t.number, mh);
        }

        @Override
        public Group group() { return Group.NB; }

        @Override
        public String getMethodName() { return this.methodName; }

        @Override
        public MethodType getType() { return this.type; }

        @Override
        public MethodHandle getEmpty() { return this.empty; }

        @Override
        public boolean isDefinedFor(PyType t) {
            return (MethodHandle) slotHandle.get(t.number) != empty;
        }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return EnumUtil.findInClass(this, c);
        }
    }

    /**
     * An enumeration of the "sequence" group: slots where the name
     * begins {@code sq_}, that are found in an optional
     * {@code PySequenceMethods} in CPython.
     */
    enum SQ implements Any {

        length(Signature.LEN), //
        repeat(Signature.BINARY), //
        item(Signature.SQ_INDEX), //
        ass_item(Signature.SQ_ASSIGN);

        final MethodType type;
        final MethodHandle empty;
        final VarHandle slotHandle;

        SQ(Signature signature) {
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = EnumUtil.slotHandle(this);
        }

        /**
         * Lookup this slot in the given object.
         *
         * @param m the {@code *Methods} object to consult
         * @return the {@code MethodHandle} from it
         */
        MethodHandle getSlot(PyType.SequenceMethods m) {
            return (MethodHandle) slotHandle.get(m);
        }

        /**
         * Set this slot in the given object (if type-compatible).
         *
         * @param m the {@code *Methods} object to consult
         * @param mh the {@code MethodHandle} to set there
         */
        void setSlot(PyType.SequenceMethods m, MethodHandle mh) {
            if (mh == null || !mh.type().equals(type))
                throw EnumUtil.slotTypeError(this, mh);
            slotHandle.set(m, mh);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return getSlot(t.sequence);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            assert t.sequence != PyType.SequenceMethods.EMPTY;
            setSlot(t.sequence, mh);
        }

        @Override
        public Group group() { return Group.SQ; }

        @Override
        public String getMethodName() { return this.name(); }

        @Override
        public MethodType getType() { return this.type; }

        @Override
        public MethodHandle getEmpty() { return this.empty; }

        @Override
        public boolean isDefinedFor(PyType t) {
            return (MethodHandle) slotHandle.get(t.sequence) != empty;
        }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return EnumUtil.findInClass(this, c);
        }
    }

    /**
     * An enumeration of the "mapping" group: slots where the name
     * begins {@code mp_}, that are found in an optional
     * {@code PyMappingMethods} in CPython.
     */
    enum MP implements Any {

        length(Signature.LEN), subscript(Signature.BINARY),
        ass_subscript(Signature.MP_ASSIGN);

        final MethodType type;
        final MethodHandle empty;
        final VarHandle slotHandle;

        MP(Signature signature) {
            this.type = signature.type;
            this.empty = signature.empty;
            this.slotHandle = EnumUtil.slotHandle(this);
        }

        /**
         * Lookup this slot in the given object.
         *
         * @param m the {@code *Methods} object to consult
         * @return the {@code MethodHandle} from it
         */
        MethodHandle getSlot(PyType.MappingMethods m) {
            return (MethodHandle) slotHandle.get(m);
        }

        /**
         * Set this slot in the given object (if type-compatible).
         *
         * @param m the {@code *Methods} object to consult
         * @param mh the {@code MethodHandle} to set there
         */
        void setSlot(PyType.MappingMethods m, MethodHandle mh) {
            if (mh == null || !mh.type().equals(type))
                throw EnumUtil.slotTypeError(this, mh);
            slotHandle.set(m, mh);
        }

        @Override
        public MethodHandle getSlot(PyType t) {
            return getSlot(t.mapping);
        }

        @Override
        public void setSlot(PyType t, MethodHandle mh) {
            assert t.mapping != PyType.MappingMethods.EMPTY;
            setSlot(t.mapping, mh);
        }

        @Override
        public Group group() { return Group.MP; }

        @Override
        public String getMethodName() { return this.name(); }

        @Override
        public MethodType getType() { return this.type; }

        @Override
        public MethodHandle getEmpty() { return this.empty; }

        @Override
        public boolean isDefinedFor(PyType t) {
            return (MethodHandle) slotHandle.get(t.mapping) != empty;
        }

        @Override
        public MethodHandle findInClass(Class<?> c) {
            return EnumUtil.findInClass(this, c);
        }
    }

    /** Help with the use of {@code *Methods} objects. */
    static class Util {

        /**
         * A list of all the slots a {@link PyType} object might
         * contain. This is a convenience function allowing client code
         * to iterate over all the slots in one loop, such as:<pre>
         * for (Any s : SlotMethods.ALL) {
         *     // Do something with s ...
         *  }
         * </pre>
         */
        static List<Any> ALL = allSlots();

        private static List<Any> allSlots() {
            // Make a stream of the separately-defined enum values
            Any[] tp, nb, sq;
            tp = TP.values();
            nb = NB.values();
            sq = SQ.values();
            // XXX and the rest in due course
            List<Any> all = new LinkedList<>();
            Collections.addAll(all, tp);
            Collections.addAll(all, nb);
            Collections.addAll(all, sq);
            return List.copyOf(all);
        }
    }

}
