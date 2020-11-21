package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Method;

import uk.co.farowl.vsj2.evo4.Exposed.Deleter;
import uk.co.farowl.vsj2.evo4.Exposed.Getter;
import uk.co.farowl.vsj2.evo4.Exposed.Setter;
import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

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
class PyGetSetDescr extends DataDescriptor {

    static final Lookup LOOKUP = MethodHandles.lookup();
    static final PyType TYPE =
            PyType.fromSpec(new PyType.Spec("getset_descriptor", LOOKUP,
                    PyGetSetDescr.class));

    // We do not have distinct struct PyGetSetDef
    // GetSetDef getset;

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

    /**
     * A handle on the getter defined by the implementation of
     * {@link Descriptor#objclass} for this attribute. It has signature
     * {@link #GETTER}.
     */
    // CPython: PyObject *(*getter)(PyObject *, void *)
    final MethodHandle get;  // MT = GETTER
    // CPython: int (*setter)(PyObject *, PyObject *, void *)
    /**
     * A handle on the setter defined by the implementation of
     * {@link Descriptor#objclass} for this attribute. It has signature
     * {@link #SETTER}.
     */
    final MethodHandle set;  // MT = SETTER
    /**
     * A handle on the deleter defined by the implementation of
     * {@link Descriptor#objclass} for this attribute. It has signature
     * {@link #DELETER}.
     */
    final MethodHandle delete;  // MT = DELETER

    /** Documentation string for this attribute. */
    final String doc;

    // void * arg to [gs]etter No uses found in CPython.
    // Sub-class GetSetDef or give [gs]etter actual closures.
    // void *closure;

    /**
     * Construct a descriptor that calls the access methods for get, set
     * and delete operations specified as method handles. These methods
     * will be identified in an implementation by annotations
     * {@link Getter}, {@link Setter}, {@link Deleter}.
     *
     * @param objclass to which descriptor applies
     * @param name of attribute
     * @param get handle on getter method (or {@code null})
     * @param set handle on setter method (or {@code null})
     * @param delete handle on deleter method (or {@code null})
     * @param doc documentation string
     */
    // Compare CPython PyDescr_NewGetSet
    PyGetSetDescr(PyType objclass, String name, MethodHandle get,
            MethodHandle set, MethodHandle delete, String doc) {
        super(TYPE, objclass, name);
        this.get = get != null ? get : EMPTY_GETTER;
        this.set = set != null ? set : EMPTY_SETTER;
        this.delete = delete != null ? delete : EMPTY_DELETER;
        this.doc = doc;
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
    static PyObject __repr__(PyGetSetDescr descr) {
        return descr.descrRepr("attribute");
    }

    // Compare CPython getset_get in descrobject.c
    static PyObject __get__(PyGetSetDescr descr, PyObject obj,
            PyType type) throws Throwable {
        if (obj == null)
            /*
             * null 2nd argument to __get__ indicates the descriptor was
             * found on the target object itself (or a base), see
             * CPython type_getattro in typeobject.c
             */
            return descr;
        else {
            try {
                descr.check(obj);
                return (PyObject) descr.get.invokeExact(obj);
            } catch (EmptyException e) {
                throw new AttributeError(ATTRIBUTE_IS_NOT, descr.name,
                        descr.objclass.name, "readable");
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
    private static PyObject emptyGetter(PyObject ignored)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_set in descrobject.c
    static void __set__(PyGetSetDescr descr, PyObject obj,
            PyObject value) throws TypeError, Throwable {
        if (value == null) {
            // This ought to be an error, but allow for CPython idiom.
            __delete__(descr, obj);
        } else {
            try {
                descr.checkSet(obj);
                descr.set.invokeExact(obj, value);
            } catch (EmptyException e) {
                throw new AttributeError(ATTRIBUTE_IS_NOT, descr.name,
                        descr.objclass.name, "writable");
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
    private static void emptySetter(PyObject ignored, PyObject v)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_set in descrobject.c with NULL
    static void __delete__(PyGetSetDescr descr, PyObject obj)
            throws TypeError, Throwable {
        try {
            descr.checkDelete(obj);
            descr.delete.invokeExact(obj);
        } catch (EmptyException e) {
            throw new AttributeError(ATTRIBUTE_IS_NOT, descr.name,
                    descr.objclass.name, "delible");
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
    private static void emptyDeleter(PyObject ignored)
            throws EmptyException {
        throw EMPTY;
    }

    // Compare CPython getset_get_doc in descrobject.c
    static PyObject getset_get_doc(PyGetSetDescr descr) {
        if (descr.doc == null) { return Py.None; }
        return Py.str(descr.doc);
    }

    private static final String ATTRIBUTE_IS_NOT =
            "attribute '%s' of '%.100s' objects is not %s";

    /**
     * {@code GetSetDef} ({@code PyGetSetDef} in CPython) represents a
     * field or computable property of a Java class that is exposed to
     * Python as a member of a {@code PyObject}. The exporting class is
     * required to define getter and setter functions that appear here
     * as {@code MethodHandle}s. The attribute may be made read-only by
     * not supplying a setter.
     */
    // Compare CPython struct PyGetSetDef in descrobject.h
    static class GetSetDef {

        final String name;
        // CPython: PyObject *(*getter)(PyObject *, void *)
        Method get;
        // CPython: int (*setter)(PyObject *, PyObject *, void *)
        Method set;
        Method delete;
        String doc;

        /** Obvious constructor. */
        GetSetDef(String name) {
            this.name = name;

        }

        /**
         * Set the {@link #get} method.
         *
         * @param get to hold as {@link #get}
         * @return previous value
         */
        Method setGet(Method get) {
            Method previous = this.get;
            this.get = get;
            return previous;
        }

        /**
         * Set the {@link #set} method.
         *
         * @param set to hold as {@link #set}
         * @return previous value
         */
        Method setSet(Method set) {
            Method previous = this.set;
            this.set = set;
            return previous;
        }

        /**
         * Set the {@link #delete} method.
         *
         * @param delete to hold as {@link #delete}
         * @return previous value
         */
        Method setDelete(Method delete) {
            Method previous = this.delete;
            this.delete = delete;
            return previous;
        }

        /**
         * Set the {@link #doc} string.
         *
         * @param doc to hold as {@link #doc}
         * @return previous value
         */
        String setDoc(String doc) {
            String previous = this.doc;
            this.doc = doc;
            return previous;
        }

        /**
         * Create a {@code PyGetSetDescr} with behaviour determined by
         * the settings on this definition.
         *
         * @param objclass Python type that owns the descriptor
         * @param lookup authorisation to access fields
         * @return descriptor for access to the field
         * @throws InterpreterError if the method type is not supported
         */
        PyGetSetDescr create(PyType objclass, Lookup lookup)
                throws InterpreterError {

            // Handles on implementation methods (or null)
            MethodHandle g = unreflect(lookup, GETTER, get);
            MethodHandle s = unreflect(lookup, SETTER, set);
            MethodHandle d = unreflect(lookup, DELETER, delete);

            return new PyGetSetDescr(objclass, name, g, s, d, doc);
        }

        /**
         * Create a method handle on the implementation method,
         * verifying that the method type produced is compatible with
         * the method type provided. The method may be {@code null},
         * signifying a method was not defined, in which case the
         * returned handle is {@code null}.
         *
         * @param lookup authorisation to access fields
         * @param mt expected to match returned handle
         * @param m implementing method (or {@code null})
         * @return method handle on {@code m} (or {@code null})
         */
        private MethodHandle unreflect(Lookup lookup, MethodType mt,
                Method m) {
            if (m == null) { return null; }
            try {
                /*
                 * This handle reflects the method signature and the
                 * object operates on should be consistent because it
                 * implements the descriptor's objclass.
                 */
                MethodHandle mh = lookup.unreflect(m);
                try {
                    /*
                     * The call site that invokes the handle * (for
                     * example in PyGetSetDescr.__get__) will have a
                     * signature involving only {@code PyObject}. We
                     * must therefore add a cast to the method handle
                     * obtained from the method.
                     */
                    return mh.asType(mt);
                } catch (WrongMethodTypeException wmte) {
                    // Wrong number of args or cannot cast.
                    throw methodSignatureError(m, mt, mh);
                }
            } catch (IllegalAccessException e) {
                throw new InterpreterError(e,
                        "cannot get method handle for '%s'", m);
            }
        }

        /** Convenience function to compose error in unreflect(). */
        private InterpreterError methodSignatureError(Method m,
                MethodType mt, MethodHandle mh) {
            String anno = "@Deleter";
            if (mt == GETTER) {
                anno = "@Getter";
            } else if (mt == SETTER) { anno = "@Setter"; }
            return new InterpreterError(UNSUPPORTED_SIG, m.getName(),
                    anno, mh.type());
        }

        private static final String UNSUPPORTED_SIG =
                "target %.50s of %s has wrong signature %.50s";

        @Override
        public String toString() {
            return String.format(
                    "GetSetDef(%s, get=%s, set=%s, delete=%s, %.20s)",
                    name, mn(get), mn(set), mn(delete), doc);
        }

        /** Method name or null (for toString()). */
        private static String mn(Method m) {
            return m == null ? "" : m.getName();
        }
    }

}
