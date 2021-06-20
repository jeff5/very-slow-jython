package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;

import uk.co.farowl.vsj3.evo1.Exposed.Deleter;
import uk.co.farowl.vsj3.evo1.Exposed.Getter;
import uk.co.farowl.vsj3.evo1.Exposed.Setter;
import uk.co.farowl.vsj3.evo1.PyType.Flag;
import uk.co.farowl.vsj3.evo1.Slot.EmptyException;

/**
 * Descriptor for an attribute that has been defined by a series of
 * {@link Getter}, {@link Setter} and {@link Deleter} that annotate
 * access methods defined in the object implementation to get, set or
 * delete the value. {@code PyGetSetDescr} differs from
 * {@link PyMemberDescr} in giving the author of an implementation class
 * the power (and responsibility) entirely to define the behaviour
 * corresponding to these actions.
 */
// Compare CPython struct PyGetSetDef in descrobject.h,
// and PyGetSetDescrObject also in descrobject.h
abstract class PyGetSetDescr extends DataDescriptor {

    static final Lookup LOOKUP = MethodHandles.lookup();
    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("getset_descriptor", LOOKUP)
                    .flagNot(Flag.BASETYPE));

    // CPython: PyObject *(*getter)(PyObject *, void *)
    static final MethodType GETTER = MethodType.methodType(O, O);
    // CPython: int (*setter)(PyObject *, PyObject *, void *)
    static final MethodType SETTER = MethodType.methodType(V, O, O);
    static final MethodType DELETER = MethodType.methodType(V, O);

    /** A handle on {@link #emptyGetter(PyObject)} */
    private static MethodHandle EMPTY_GETTER;
    /** A handle on {@link #emptySetter(PyObject, PyObject)} */
    private static MethodHandle EMPTY_SETTER;
    /** A handle on {@link #emptyDeleter(PyObject)} */
    private static MethodHandle EMPTY_DELETER;
    /** Empty array of method handles */
    private static MethodHandle[] EMPTY_MH_ARRAY = new MethodHandle[0];

    static {
        /*
         * Initialise the empty method handles in a block since it can
         * fail (in theory).
         */
        try {
            EMPTY_GETTER = LOOKUP.findStatic(PyGetSetDescr.class,
                    "emptyGetter", GETTER);
            EMPTY_SETTER = LOOKUP.findStatic(PyGetSetDescr.class,
                    "emptySetter", SETTER);
            EMPTY_DELETER = LOOKUP.findStatic(PyGetSetDescr.class,
                    "emptyDeleter", DELETER);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            // This should never happen.
            throw new InterpreterError(e,
                    "cannot find get-set empty* functions");
        }
    }

    /** Documentation string for this attribute. */
    final String doc;

    // void * arg to [gs]etter No uses found in CPython.
    // Sub-class GetSetDef or give [gs]etter actual closures.
    // void *closure;

    /**
     * Construct a descriptor that calls the access methods for get, set
     * and delete operations specified as method handles.
     *
     * @param objclass to which descriptor applies
     * @param name of attribute
     * @param doc documentation string
     */
    // Compare CPython PyDescr_NewGetSet
    PyGetSetDescr(PyType objclass, String name, String doc) {
        super(TYPE, objclass, name);
        this.doc = doc;
    }

    /**
     * Return the getter contained in this descriptor applicable to the
     * Java class supplied. The {@link Descriptor#objclass} is consulted
     * to make this determination. If the class is not an accepted
     * implementation of {@code objclass}, an empty slot handle (with
     * the correct signature) is returned.
     *
     * @param selfClass Java class of the {@code self} argument
     * @return corresponding handle (or {@code slot.getEmpty()})
     */
    abstract MethodHandle getWrappedGet(Class<?> selfClass);

    /**
     * Return the setter contained in this descriptor applicable to the
     * Java class supplied. The {@link Descriptor#objclass} is consulted
     * to make this determination. If the class is not an accepted
     * implementation of {@code objclass}, an empty slot handle (with
     * the correct signature) is returned.
     *
     * @param selfClass Java class of the {@code self} argument
     * @return corresponding handle (or {@code slot.getEmpty()})
     */
    abstract MethodHandle getWrappedSet(Class<?> selfClass);

    /**
     * Return the deleter contained in this descriptor applicable to the
     * Java class supplied. The {@link Descriptor#objclass} is consulted
     * to make this determination. If the class is not an accepted
     * implementation of {@code objclass}, an empty slot handle (with
     * the correct signature) is returned.
     *
     * @param selfClass Java class of the {@code self} argument
     * @return corresponding handle (or {@code slot.getEmpty()})
     */
    abstract MethodHandle getWrappedDelete(Class<?> selfClass);

    /**
     * The attribute may not be set or deleted.
     *
     * @return true if the attribute may not be set or deleted
     */
    abstract boolean readonly();

    /**
     * The attribute may be deleted.
     *
     * @return true if the attribute may be deleted.
     */
    abstract boolean optional();

    /**
     * A {@link PyGetSetDescr} to use for a get-set attribute when the
     * owning Python type has just one accepted implementation.
     */
    static class Single extends PyGetSetDescr {

        /**
         * A handle on the getter defined by the unique implementation
         * of {@link Descriptor#objclass} for this attribute. The method
         * type is {@link #GETTER} {= @code (O)O}.
         */
        // CPython: PyObject *(*getter)(PyObject *, void *)
        // Compare CPython PyGetSetDef::get
        final MethodHandle get;  // MT = GETTER

        /**
         * A handle on the setter defined by the unique implementation
         * of {@link Descriptor#objclass} for this attribute. The method
         * type is {@link #SETTER} {= @code (O,O)V}.
         */
        // CPython: int (*setter)(PyObject *, PyObject *, void *)
        // Compare CPython PyGetSetDef::set
        final MethodHandle set;  // MT = SETTER

        /**
         * A handle on the deleter defined by the unique implementation
         * of {@link Descriptor#objclass} for this attribute. The method
         * type is {@link #DELETER} {= @code (O)V}.
         */
        // Compare CPython PyGetSetDef::set with null
        final MethodHandle delete;  // MT = DELETER

        /**
         * Construct a get-set descriptor, identifying by an array of
         * method handles the implementation methods applicable to
         * {@code objclass}. These methods will be identified in an
         * implementation by annotations {@link Getter}, {@link Setter},
         * {@link Deleter}.
         *
         * @param objclass to which descriptor applies
         * @param name of attribute
         * @param get handle on getter method (or {@code null})
         * @param set handle on setter method (or {@code null})
         * @param delete handle on deleter method (or {@code null})
         * @param doc documentation string
         */
        // Compare CPython PyDescr_NewGetSet
        Single(PyType objclass, String name, MethodHandle get,
                MethodHandle set, MethodHandle delete, String doc) {
            super(objclass, name, doc);
            this.get = get != null ? get : EMPTY_GETTER;
            this.set = set != null ? set : EMPTY_SETTER;
            this.delete = delete != null ? delete : EMPTY_DELETER;
        }

        @Override
        MethodHandle getWrappedGet(Class<?> selfClass) {
            // The first argument is acceptable as 'self'
            assert objclass.getJavaClass().isAssignableFrom(selfClass);
            return get;
        }

        @Override
        MethodHandle getWrappedSet(Class<?> selfClass) {
            // The first argument is acceptable as 'self'
            assert objclass.getJavaClass().isAssignableFrom(selfClass);
            return set;
        }

        @Override
        MethodHandle getWrappedDelete(Class<?> selfClass) {
            // The first argument is acceptable as 'self'
            assert objclass.getJavaClass().isAssignableFrom(selfClass);
            return delete;
        }

        @Override
        boolean readonly() {
            return set == EMPTY_SETTER;
        }

        @Override
        boolean optional() {
            return delete != EMPTY_DELETER;
        }
    }

    /**
     * A {@link PyGetSetDescr} to use for a get-set attribute when the
     * owning Python type has multiple accepted implementations.
     */
    static class Multiple extends PyGetSetDescr {

        /**
         * Handles for the particular implementations of the getter. The
         * method type of each is {@code (O)O}.
         */
        // Compare CPython PyGetSetDef::get
        protected final MethodHandle[] get;

        /**
         * Handles for the particular implementations of the setter. The
         * method type of each is {@code (O,O)V}.
         */
        // Compare CPython PyGetSetDef::set
        protected final MethodHandle[] set;

        /**
         * Handles for the particular implementations of the deleter.
         * The method type of each is {@code (O)V}.
         */
        // CPython uses PyGetSetDef::set
        protected final MethodHandle[] delete;

        /**
         * Construct a get-set descriptor, identifying by an array of
         * method handles the implementation methods applicable to
         * {@code objclass}. These methods will be identified in an
         * implementation by annotations {@link Getter}, {@link Setter},
         * {@link Deleter}.
         *
         * @param objclass to which descriptor applies
         */
        // Compare CPython PyDescr_NewGetSet
        Multiple(PyType objclass, String name, MethodHandle[] get,
                MethodHandle[] set, MethodHandle delete[], String doc) {
            super(objclass, name, doc);
            this.get = get;
            this.set = set != null ? set : EMPTY_MH_ARRAY;
            this.delete = delete != null ? delete : EMPTY_MH_ARRAY;
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method will check that the type of self matches
         * {@link Descriptor#objclass}, according to its
         * {@link PyType#indexAccepted(Class)}.
         */
        @Override
        MethodHandle getWrappedGet(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return get[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                // This will behave as an empty slot
                return EMPTY_GETTER;
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method will check that the type of self matches
         * {@link Descriptor#objclass}, according to its
         * {@link PyType#indexAccepted(Class)}.
         */
        @Override
        MethodHandle getWrappedSet(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return set[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                // This will behave as an empty slot
                return EMPTY_SETTER;
            }
        }

        /**
         * {@inheritDoc}
         * <p>
         * The method will check that the type of self matches
         * {@link Descriptor#objclass}, according to its
         * {@link PyType#indexAccepted(Class)}.
         */
        @Override
        MethodHandle getWrappedDelete(Class<?> selfClass) {
            // Work out how to call this descriptor on that object
            int index = objclass.indexAccepted(selfClass);
            try {
                return delete[index];
            } catch (ArrayIndexOutOfBoundsException iobe) {
                // This will behave as an empty slot
                return EMPTY_DELETER;
            }
        }

        @Override
        boolean readonly() {
            return set.length == 0;
        }

        @Override
        boolean optional() {
            return delete.length != 0;
        }
    }

    // CPython get-set table (to convert to annotations):
    // private GetSetDef getset_getset[] = {
    // {"__doc__", (getter)getset_get_doc},
    // {"__qualname__", (getter)descr_get_qualname},
    // {0}
    // };

    // CPython type object (to convert to special method names):
    // PyType PyGetSetDescr_Type = {
    // PyVar_HEAD_INIT(&PyType_Type, 0)
    // "getset_descriptor",
    // sizeof(PyGetSetDescr),
    // 0,
    // (destructor)descr_dealloc, /* tp_dealloc */
    // 0, /* tp_vectorcall_offset */
    // 0, /* tp_getattr */
    // 0, /* tp_setattr */
    // 0, /* tp_as_async */
    // (reprfunc)getset_repr, /* tp_repr */
    // 0, /* tp_as_number */
    // 0, /* tp_as_sequence */
    // 0, /* tp_as_mapping */
    // 0, /* tp_hash */
    // 0, /* tp_call */
    // 0, /* tp_str */
    // PyObject_GenericGetAttr, /* tp_getattro */
    // 0, /* tp_setattro */
    // 0, /* tp_as_buffer */
    // Py_TPFLAGS_DEFAULT | Py_TPFLAGS_HAVE_GC, /* tp_flags */
    // 0, /* tp_doc */
    // descr_traverse, /* tp_traverse */
    // 0, /* tp_clear */
    // 0, /* tp_richcompare */
    // 0, /* tp_weaklistoffset */
    // 0, /* tp_iter */
    // 0, /* tp_iternext */
    // 0, /* tp_methods */
    // descr_members, /* tp_members */
    // getset_getset, /* tp_getset */
    // 0, /* tp_base */
    // 0, /* tp_dict */
    // (descrgetfunc)getset_get, /* tp_descr_get */
    // (descrsetfunc)getset_set, /* tp_descr_set */
    // };

    // Compare CPython getset_repr in descrobject.c
    @SuppressWarnings("unused")
    private Object __repr__() {
        return descrRepr("attribute");
    }

    /**
     * {@inheritDoc}
     *
     * If {@code obj != null} invoke {@link #get} on it to return a
     * value. {@code obj} must be of type {@link #objclass}. A call made
     * with {@code obj == null} returns {@code this} descriptor.
     *
     * @param type is ignored
     */
    // Compare CPython getset_get in descrobject.c
    @Override
    Object __get__(Object obj, PyType type) throws Throwable {
        if (obj == null)
            /*
             * obj==null indicates the descriptor was found on the
             * target object itself (or a base), see CPython
             * type_getattro in typeobject.c
             */
            return this;
        else {
            try {
                check(obj);
                MethodHandle mh = getWrappedGet(obj.getClass());
                return mh.invokeExact(obj);
            } catch (EmptyException e) {
                throw cannotReadAttr();
            }
        }
    }

    /**
     * This method fills {@link #get} when the implementation leaves it
     * blank.
     *
     * @param ignored object to operate on
     * @return never
     * @throws EmptyException always
     */
    @SuppressWarnings("unused") // used reflectively
    private static Object emptyGetter(Object ignored)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_set in descrobject.c
    @Override
    void __set__(Object obj, Object value) throws TypeError, Throwable {
        if (value == null) {
            // This ought to be an error, but allow for CPython idiom.
            __delete__(obj);
        } else {
            try {
                checkSet(obj);
                MethodHandle mh = getWrappedSet(obj.getClass());
                mh.invokeExact(obj, value);
            } catch (EmptyException e) {
                throw cannotWriteAttr();
            }
        }
    }

    /**
     * This method fills {@link #set} when the implementation leaves it
     * blank.
     *
     * @param ignored object to operate on
     * @param v ignored too
     * @throws EmptyException always
     */
    @SuppressWarnings("unused") // used reflectively
    private static void emptySetter(Object ignored, Object v)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_set in descrobject.c with NULL
    @Override
    void __delete__(Object obj) throws TypeError, Throwable {
        try {
            checkDelete(obj);
            MethodHandle mh = getWrappedDelete(obj.getClass());
            mh.invokeExact(obj);
        } catch (EmptyException e) {
            throw readonly() ? cannotWriteAttr() : cannotDeleteAttr();
        }
    }

    /**
     * This method fills {@link #delete} when the implementation leaves
     * it blank.
     *
     * @param ignored object to operate on
     * @throws EmptyException always
     */
    @SuppressWarnings("unused") // used reflectively
    private static void emptyDeleter(Object ignored)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_get_doc in descrobject.c
    static Object getset_get_doc(PyGetSetDescr descr) {
        if (descr.doc == null) { return Py.None; }
        return descr.doc;
    }
}
