package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.Slot.MethodKind;

/**
 * A {@link Descriptor} for a particular definition <b>in Java</b> of
 * one of the special methods of the Python data model (such as
 * {@code __sub__}). The type also appears as
 * {@code <class 'wrapper_descriptor'>}.
 * <p>
 * The owner of the descriptor is the Python type providing the
 * definition. Type construction places a {@code PyWrapperDescr} in the
 * dictionary of the defining {@link PyType}, against a key that is the
 * "dunder name" of the special method it wraps. (This does not preclude
 * client code moving it around afterwards!)
 * <p>
 * The {@code PyWrapperDescr} provides a {@code MethodHandle} for the
 * defining method. In every Python type where a {@code PyWrapperDescr}
 * appears as the attribute value corresponding to a special method, the
 * handle will fill the corresponding type slot. This may happen because
 * the type is the defining type, by inheritance, or by insertion of the
 * {@code PyWrapperDescr} as an attribute of the type. (In the last
 * case, the signature of the wrapped and destination slots must match.)
 */
/*
 * Difference from CPython: In CPython, a PyWrapperDescr is created
 * because the slot at the corresponding offset in the PyTypeObject of
 * the owning Python type is filled, statically or by PyType_FromSpec.
 *
 * In this implementation, we create a PyWrapperDescr as an attribute
 * because the Java implementation of the owning type defines a method
 * with that slot's name. Then we fill the slot because the type has an
 * attribute with the matching name. The result should be the same but
 * the process is more regular.
 */
abstract class PyWrapperDescr extends MethodDescriptor {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("wrapper_descriptor",
                    MethodHandles.lookup()).flagNot(Flag.BASETYPE));

    /**
     * The {@link Slot} ({@code enum}) describing the generic
     * characteristics the special method of which
     * {@link Descriptor#objclass} provides a particular implementation.
     */
    final Slot slot;

    /**
     * Construct a slot wrapper descriptor for the {@code slot} in
     * {@code objclass}.
     *
     * @param objclass the class declaring the special method
     * @param slot for the generic special method
     */
    // Compare CPython PyDescr_NewClassMethod in descrobject.c
    PyWrapperDescr(PyType objclass, Slot slot) {
        super(TYPE, objclass, slot.methodName);
        this.slot = slot;
    }

    // Exposed attributes ---------------------------------------------

    // CPython get-set table (to convert to annotations):
    // private GetSetDef wrapperdescr_getset[] = {
    // {"__doc__", (getter)wrapperdescr_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {"__text_signature__",
    // (getter)wrapperdescr_get_text_signature},
    // {0}
    // };

    @Getter
    // Compare CPython wrapperdescr_get_doc in descrobject.c
    protected Object __doc__() {
        return PyType.getDocFromInternalDoc(slot.methodName, slot.doc);
    }

    @Getter
    // Compare CPython wrapperdescr_get_text_signature in descrobject.c
    protected Object __text_signature__() {
        return PyType.getTextSignatureFromInternalDoc(slot.methodName,
                slot.doc);
    }

    // Special methods ------------------------------------------------

    // CPython type object (to convert to special method names):
    // PyType PyWrapperDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "wrapper_descriptor",
    // sizeof(PyWrapperDescr),
    // (reprfunc)wrapperdescr_repr, /* tp_repr */
    // (ternaryfunc)wrapperdescr_call, /* tp_call */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC |
    // Py_TPFLAGS_METHOD_DESCRIPTOR, /* tp_flags */
    // descr_traverse, /* tp_traverse */
    // descr_methods, /* tp_methods */
    // descr_members, /* tp_members */
    // wrapperdescr_getset, /* tp_getset */
    // (descrgetfunc)wrapperdescr_get, /* tp_descr_get */
    // };

    // Compare CPython wrapperdescr_repr in descrobject.c
    @SuppressWarnings("unused")
    private Object __repr__() {
        return descrRepr("slot wrapper");
    }

    // Compare CPython wrapperdescr_get in descrobject.c
    @Override
    protected Object __get__(Object obj, PyType type) {
        if (obj == null)
            /*
             * obj==null indicates the descriptor was found on the
             * target object itself (or a base), see CPython
             * type_getattro in typeobject.c
             */
            return this;
        else {
            // Return callable binding this and obj
            check(obj);
            return new PyMethodWrapper(this, obj);
        }
    }

    /**
     * Return the handle contained in this descriptor applicable to the
     * Java class supplied (typically that of a {@code self} argument
     * during a call). The {@link Descriptor#objclass} is consulted to
     * make this determination. If the class is not an accepted
     * implementation of {@code objclass}, an empty slot handle (with
     * the correct signature) is returned.
     *
     * @param selfClass Java class of the {@code self} argument
     * @return corresponding handle (or {@code slot.getEmpty()})
     */
    abstract MethodHandle getWrapped(Class<?> selfClass);

    /**
     * Call the wrapped method with positional arguments (the first
     * being the target object) and optionally keywords arguments. The
     * arguments, in type and number, must match the signature of the
     * special function slot.
     *
     * @param args positional arguments beginning with {@code self}
     * @param kwargs keyword arguments
     * @return result of calling the wrapped method
     * @throws TypeError if {@code args[0]} is the wrong type
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_call in descrobject.c
    protected Object __call__(PyTuple args, PyDict kwargs)
            throws TypeError, Throwable {

        int argc = args.value.length;
        if (argc > 0) {
            // Split the leading element self from args
            Object self = args.value[0];
            if (argc == 1) {
                args = PyTuple.EMPTY;
            } else {
                args = new PyTuple(args.value, 1, argc - 1);
            }

            // Make sure that the first argument is acceptable as 'self'
            PyType selfType = PyType.of(self);
            if (!Abstract.recursiveIsSubclass(selfType, objclass)) {
                throw new TypeError(DESCRIPTOR_REQUIRES, name,
                        objclass.name, selfType.name);
            }

            return callWrapped(self, args, kwargs);

        } else {
            // Not even one argument
            throw new TypeError(DESCRIPTOR_NEEDS_ARGUMENT, name,
                    objclass.name);
        }
    }

    /**
     * Invoke the method described by this {@code PyWrapperDescr} the
     * given target {@code self}, and the arguments supplied.
     *
     * @param self target object of the method call
     * @param args of the method call
     * @param kwargs of the method call
     * @return result of the method call
     * @throws TypeError if the arguments do not fit the special method
     * @throws Throwable from the implementation of the special method
     */
    // Compare CPython wrapperdescr_raw_call in descrobject.c
    Object callWrapped(Object self, PyTuple args, PyDict kwargs)
            throws Throwable {
        try {
            // Call through the correct wrapped handle
            MethodHandle wrapped = getWrapped(self.getClass());
            Slot.Signature sig = slot.signature;
            return sig.callWrapped(wrapped, self, args, kwargs);
        } catch (ArgumentError ae) {
            throw signatureTypeError(ae, args);
        }
    }

    /**
     * A {@link PyWrapperDescr} for use when the owning Python type has
     * just one accepted implementation.
     */
    static class Single extends PyWrapperDescr {

        /**
         * A handle for the particular implementation of a special
         * method being wrapped. The method type is that of
         * {@link #slot}{@code .signature}.
         */
        protected final MethodHandle wrapped;

        /**
         * Construct a slot wrapper descriptor, identifying by a method
         * handle the implementation method for the {@code slot} in
         * {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped a handle to an implementation of that slot
         */
        // Compare CPython PyDescr_NewClassMethod in descrobject.c
        Single(PyType objclass, Slot slot, MethodHandle wrapped) {
            super(objclass, slot);
            this.wrapped = wrapped;
        }

        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // Make sure that the first argument is acceptable as 'self'
            if (objclass.getJavaClass().isAssignableFrom(selfClass))
                return wrapped;
            else
                return slot.getEmpty();
        }
    }

    /**
     * A {@link PyWrapperDescr} for use when the owning Python type has
     * multiple accepted implementation.
     */
    static class Multiple extends PyWrapperDescr {

        /**
         * Handles for the particular implementations of a special
         * method being wrapped. The method type of each is that of
         * {@link #slot}{@code .signature}.
         */
        protected final MethodHandle[] wrapped;

        /**
         * Construct a slot wrapper descriptor, identifying by an array
         * of method handles the implementation methods for the
         * {@code slot} in {@code objclass}.
         *
         * @param objclass the class declaring the special method
         * @param slot for the generic special method
         * @param wrapped handles to the implementation of that slot
         */
        // Compare CPython PyDescr_NewClassMethod in descrobject.c
        Multiple(PyType objclass, Slot slot, MethodHandle[] wrapped) {
            super(objclass, slot);
            this.wrapped = wrapped;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method will check that the type of self matches
         * {@link Descriptor#objclass}, according to its
         * {@link PyType#indexAccepted(Class)}.
         */
        @Override
        MethodHandle getWrapped(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return wrapped[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                return slot.getEmpty();
            }
        }
    }

    /**
     * {@code WrapperSpecification} represents
     *  one or more methods of a Java class
     * that are to be exposed as a single <b>special method</b> of an
     * {@code object}. The exporting class provides definitions for
     * the special method, that appear here as {@code java.lang.reflect.Method}s with different
     * signatures.
     */
    static class WrapperSpecification extends DescriptorSpecification {

        /** The special method being defined. */
        final Slot slot;

        /** Collects the methods declared. */
        final List<Method> methods = new ArrayList<>(1);

        /**
         * Obvious constructor
         *
         * @param name of attribute.
         */
        WrapperSpecification(Slot slot) {
            this.slot = slot;
        }

        @Override
        String getType() { return "slot-wrapper"; }

        /**
         * Add a method implementation. (A test that the signature
         * matches the slot follows when we construct the
         * {@link PyWrapperDescr}.)
         *
         * @param method to add to {@link #methods}
         */
        @Override
        void add(Method method) {
            methods.add(method);
        }

        /**
         * Create a {@code PyWrapperDescr} from this specification. Note
         * that a specification describes the methods as declared, and that
         * there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access members
         * @return descriptor for access to the member
         * @throws InterpreterError if the method type is not supported
         */
        @Override
        PyWrapperDescr createDescr(PyType objclass, Lookup lookup)
                throws InterpreterError {
            /*
             * We will try to create a handle for each implementation of
             * an instance method, but only one handle for static/class
             * methods (like __new__). See corresponding logic in
             * Slot.setSlot(Operations, Object)
             */
            if (slot.signature.kind == MethodKind.INSTANCE)
                return createDescrForInstanceMethod(objclass, lookup);
            else
                return createDescrForStaticMethod(objclass, lookup);
        }

        /**
         * Create a {@code PyWrapperDescr} from this specification. Note
         * that a specification describes the methods as declared, and that
         * there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyWrapperDescr createDescrForInstanceMethod(
                PyType objclass, Lookup lookup)
                throws InterpreterError {

            // Acceptable methods can be coerced to this signature
            MethodType slotType = slot.getType();
            final int L = slotType.parameterCount();
            assert (L >= 1);

            /*
             * There could be any number of candidates in the
             * implementation. An implementation method could match
             * multiple accepted implementations of the type (e.g.
             * Number matching Long and Integer).
             */
            LinkedList<MethodHandle> candidates = new LinkedList<>();
            for (Method m : methods) {
                // Convert m to a handle (if L args and accessible)
                try {
                    MethodHandle mh = lookup.unreflect(m);
                    if (mh.type().parameterCount() == L)
                        addOrdered(candidates, mh);
                } catch (IllegalAccessException e) {
                    throw cannotGetHandle(objclass, m, e);
                }
            }

            /*
             * We will try to create a handle for each implementation of
             * an instance method, but only one handle for static/class
             * methods (like __new__). See corresponding logic in
             * Slot.setSlot(Operations, Object)
             */
            final int N = objclass.acceptedCount;
            MethodHandle[] wrapped = new MethodHandle[N];

            // Fill the wrapped array with matching method handles
            for (int i = 0; i < N; i++) {
                Class<?> acceptedClass = objclass.classes[i];
                /*
                 * Fill wrapped[i] with the method handle where the
                 * first parameter is the most specific match for class
                 * accepted[i].
                 */
                // Try the candidate method until one matches
                for (MethodHandle mh : candidates) {
                    if (mh.type().parameterType(0)
                            .isAssignableFrom(acceptedClass)) {
                        try {
                            // must have the expected signature
                            wrapped[i] = mh.asType(slotType);
                            break;
                        } catch (WrongMethodTypeException wmte) {
                            // Wrong number of args or cannot cast.
                            throw methodSignatureError(mh);
                        }
                    }
                }

                // We should have a value in each of wrapped[]
                if (wrapped[i] == null) {
                    throw new InterpreterError(
                            "'%s.%s' not defined for %s", objclass.name,
                            slot.methodName, objclass.classes[i]);
                }
            }

            if (N == 1)
                /*
                 * There is only one definition so use the simpler form
                 * of slot-wrapper. This is the frequent case.
                 */
                return new PyWrapperDescr.Single(objclass, slot,
                        wrapped[0]);
            else
                /*
                 * There are multiple definitions so use the array form
                 * of slot-wrapper. This is the case for types that have
                 * multiple accepted implementationns and methods on
                 * them that are not static or "Object self"..
                 */
                return new PyWrapperDescr.Multiple(objclass, slot,
                        wrapped);
        }

        /**
         * Create a {@code PyWrapperDescr} from this specification. Note
         * that a specification describes the methods as declared, and that
         * there may be any number. This method matches them to the
         * supported implementations.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        private PyWrapperDescr createDescrForStaticMethod(
                PyType objclass, Lookup lookup)
                throws InterpreterError {

            // Acceptable methods can be coerced to this signature
            MethodType slotType = slot.getType();

            /*
             * There should be only one definition of a given name in
             * the methods list of a static or class method (like
             * __new__), and it will be accessed only via the operations
             * of the canonical type. See corresponding logic in
             * Slot.setSlot(Operations, Object).
             */
            if (methods.size() != 1) {
                throw new InterpreterError(
                        "multiple definitons of '%s' in '%s'",
                        slot.methodName, objclass.definingClass);
            }

            try {
                // Convert m to a handle (if accessible)
                MethodHandle mh = lookup.unreflect(methods.get(0));
                try {
                    // must have the expected signature here
                    return new PyWrapperDescr.Single(objclass, slot,
                            mh.asType(slotType));

                } catch (WrongMethodTypeException wmte) {
                    // Wrong number of args or cannot cast.
                    throw methodSignatureError(mh);
                }
            } catch (IllegalAccessException e) {
                throw cannotGetHandle(objclass, methods.get(0), e);
            }
        }

        /**
         * Insert a {@code MethodHandle h} into a list, such that every
         * handle in the list, of which the first parameter type is
         * assignable from the first parameter type of {@code h}, will
         * appear after {@code h} in the list. If there are none such,
         * {@code h} is added at the end. The resulting list is
         * partially ordered, and has the property that, in a forward
         * search for a handle applicable to a given class, the most
         * specific match is found first.
         *
         * @param list to add h into
         * @param h to insert/add
         */
        private void addOrdered(LinkedList<MethodHandle> list,
                MethodHandle h) {
            // Type of first parameter of h
            Class<?> c = h.type().parameterType(0);
            // We'll scan until a more general type is found
            ListIterator<MethodHandle> iter = list.listIterator(0);
            while (iter.hasNext()) {
                MethodHandle i = iter.next();
                Class<?> d = i.type().parameterType(0);
                if (d.isAssignableFrom(c)) {
                    /*
                     * d is more general than c (i is more general than
                     * h): back up and position just before i.
                     */
                    iter.previous();
                    break;
                }
            }
            // Insert h where the iterator stopped. Could be the end.
            iter.add(h);
        }

        /** Convenience function to compose error in createDescr(). */
        private InterpreterError cannotGetHandle(PyType objclass,
                Method m, IllegalAccessException e) {
            return new InterpreterError(e,
                    "cannot get method handle for '%s' in '%s'", m,
                    objclass.definingClass);
        }

        /** Convenience function to compose error in createDescr(). */
        private InterpreterError methodSignatureError(MethodHandle mh) {
            return new InterpreterError(UNSUPPORTED_SIG,
                    slot.methodName, mh.type(), slot.opName);
        }

        private static final String UNSUPPORTED_SIG =
                "method %.50s has wrong signature %.50s for slot %s";

        @Override
        public String toString() {
            return String.format("WrapperSpec(%s[%d])", slot.methodName,
                    methods.size());
        }

        /** Method name or null (for toString()). */
        private static String mn(Method m) {
            return m == null ? "" : m.getName();
        }
    }
}
