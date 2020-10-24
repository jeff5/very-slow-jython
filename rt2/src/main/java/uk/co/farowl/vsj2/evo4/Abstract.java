package uk.co.farowl.vsj2.evo4;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

import uk.co.farowl.vsj2.evo4.Slot.EmptyException;

/**
 * The "abstract interface" to operations on Python objects. Methods
 * here execute the slot functions of the type definition of the objects
 * passed in. A primary application is to the CPython byte code
 * interpreter. (Methods here often correspond closely to a CPython
 * opcode.)
 * <p>
 * See also {@link Number}, {@link Sequence}, {@link Mapping} and
 * {@link Callables} which contain the abstract interface to the
 * corresponding type families. In CPython, the methods of all these
 * classes are found in {@code Objects/abstract.c}
 */
class Abstract {

    /**
     * The equivalent of the Python expression repr(o), and is called by
     * the repr() built-in function.
     *
     * @param o object
     * @return the string representation o
     * @throws Throwable
     */
    // Compare CPython PyObject_Repr in object.c
    static PyObject repr(PyObject o) throws Throwable {
        if (o == null) {
            return Py.str("<null>");
        } else {
            PyType type = o.getType();
            try {
                PyObject res = (PyObject) type.tp_repr.invoke(o);
                if (res instanceof PyUnicode) {
                    return res;
                } else {
                    throw returnTypeError("__repr__", "string", res);
                }
            } catch (Slot.EmptyException e) {
                return PyUnicode.fromFormat("<%s object>", type.name);
            }
        }
    }

    /**
     * The equivalent of the Python expression str(o).
     *
     * @param o object
     * @return the string representation o
     * @throws Throwable
     */
    // Compare CPython PyObject_Str in object.c
    static PyObject str(PyObject o) throws Throwable {
        if (o == null) {
            return Py.str("<null>");
        } else {
            PyType type = o.getType();
            if (type == PyUnicode.TYPE) {
                return o;
            } else if (Slot.tp_str.isDefinedFor(type)) {
                PyObject res = (PyObject) type.tp_str.invoke(o);
                if (res instanceof PyUnicode) {
                    return res;
                } else {
                    throw returnTypeError("__str__", "string", res);
                }
            } else {
                return repr(o);
            }
        }
    }

    /**
     * Test a value used as condition in a {@code for} or {@code if}
     * statement.
     */
    // Compare CPython PyObject_IsTrue in object.c
    static boolean isTrue(PyObject v) throws Throwable {
        // Begin with common special cases
        if (v == Py.True)
            return true;
        else if (v == Py.False || v == Py.None)
            return false;
        else {
            // Ask the object type through the nb_bool or sq_length
            // slots
            PyType t = v.getType();
            if (Slot.nb_bool.isDefinedFor(t))
                return (boolean) t.nb_bool.invokeExact(v);
            // else if (Slot.mp_length.isDefinedFor(t))
            // return 0 != (int) t.mp_length.invokeExact(v);
            else if (Slot.sq_length.isDefinedFor(t))
                return 0 != (int) t.sq_length.invokeExact(v);
            else
                // No nb_bool and no length: claim everything is True.
                return true;
        }
    }

    /**
     * Perform a rich comparison, raising {@code TypeError} when the
     * requested comparison operator is not supported.
     */
    // Compare CPython PyObject_RichCompare, do_richcompare in object.c
    static PyObject richCompare(PyObject v, PyObject w, Comparison op)
            throws Throwable {
        PyType vType = v.getType();
        PyType wType = w.getType();

        boolean checkedReverse = false;
        MethodHandle f;

        if (vType != wType && wType.isSubTypeOf(vType)
                && (f = wType.tp_richcompare) != RICH_EMPTY) {
            checkedReverse = true;
            PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
            if (r != Py.NotImplemented) { return r; }
        }

        if ((f = vType.tp_richcompare) != RICH_EMPTY) {
            PyObject r = (PyObject) f.invokeExact(v, w, op);
            if (r != Py.NotImplemented) { return r; }
        }

        if (!checkedReverse
                && (f = wType.tp_richcompare) != RICH_EMPTY) {
            PyObject r = (PyObject) f.invokeExact(w, v, op.swapped());
            if (r != Py.NotImplemented) { return r; }
        }

        /// Neither object implements op: base == and != on identity.
        switch (op) {
            case EQ:
                return Py.val(v == w);
            case NE:
                return Py.val(v != w);
            default:
                throw comparisonTypeError(v, w, op);
        }
    }

    private static final MethodHandle RICH_EMPTY =
            Slot.Signature.RICHCMP.empty;

    static PyException comparisonTypeError(PyObject v, PyObject w,
            Comparison op) {
        String fmt =
                "'%s' not supported between instances of '%.100s' and '%.100s'";
        return new TypeError(fmt, op, v.getType().getName(),
                w.getType().getName());
    }

    /**
     * Perform a rich comparison with boolean result. This wraps
     * {@link #richCompare(PyObject, PyObject, Comparison)}, converting
     * the result to Java {@code false} or {@code true}, or throwing
     * (probably {@link TypeError}), when the objects cannot be
     * compared.
     */
    // Compare CPython PyObject_RichCompareBool in object.c
    static boolean richCompareBool(PyObject v, PyObject w,
            Comparison op) throws Throwable {
        /*
         * Quick result when objects are the same. Guarantees that
         * identity implies equality.
         */
        if (v == w) {
            if (op == Comparison.EQ)
                return true;
            else if (op == Comparison.NE)
                return false;
        }
        return isTrue(richCompare(v, w, op));
    }

    /**
     * Perform a rich comparison with boolean result. This wraps
     * {@link #richCompare(PyObject, PyObject, Comparison)}, converting
     * the result to Java {@code false} or {@code true}.
     * <p>
     * When the when the objects cannot be compared, the client gets to
     * choose the exception through the provider {@code exc}. When this
     * is {@code null}, the return will simply be {@code false} for
     * incomparable objects.
     */
    static <T extends PyException> boolean richCompareBool(PyObject v,
            PyObject w, Comparison op, Supplier<T> exc) {
        try {
            return richCompareBool(v, w, op);
        } catch (Throwable e) {
            if (exc == null)
                return false;
            else
                throw exc.get();
        }
    }

    /** Python size of {@code o} */
    // Compare CPython PyObject_Size in abstract.c
    static int size(PyObject o) throws Throwable {
        // Note that the slot is called sq_length but this method, size.
        try {
            MethodHandle mh = o.getType().sq_length;
            return (int) mh.invokeExact(o);
        } catch (Slot.EmptyException e) {}

        return Mapping.size(o);
    }

    /**
     * Python {@code o[key]} where {@code o} may be a mapping or a
     * sequence.
     */
    // Compare CPython PyObject_GetItem in abstract.c
    static PyObject getItem(PyObject o, PyObject key) throws Throwable {
        // Corresponds to abstract.c : PyObject_GetItem
        // Decisions are based on types of o and key
        PyType oType = o.getType();

        try {
            return (PyObject) oType.mp_subscript.invokeExact(o, key);
        } catch (EmptyException e) {}

        if (Slot.sq_item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.nb_index.isDefinedFor(key.getType())) {
                int k = Number.asSize(key, IndexError::new);
                return Sequence.getItem(o, k);
            } else
                throw typeError(MUST_BE_INT_NOT, key);
        } else
            throw typeError(NOT_SUBSCRIPTABLE, o);
    }

    /**
     * Python {@code o[key] = value} where {@code o} may be a mapping or
     * a sequence.
     */
    // Compare CPython PyObject_SetItem in abstract.c
    static void setItem(PyObject o, PyObject key, PyObject value)
            throws Throwable {
        // Corresponds to abstract.c : PyObject_SetItem
        // Decisions are based on types of o and key
        PyType oType = o.getType();

        try {
            oType.mp_ass_subscript.invokeExact(o, key, value);
            return;
        } catch (EmptyException e) {}

        if (Slot.sq_ass_item.isDefinedFor(oType)) {
            // For a sequence (only), key must have index-like type
            if (Slot.nb_index.isDefinedFor(key.getType())) {
                int k = Number.asSize(key, IndexError::new);
                Sequence.setItem(o, k, value);
            } else
                throw typeError(MUST_BE_INT_NOT, key);
        } else
            throw typeError(NOT_ITEM_ASSIGNMENT, o);
    }

    /** Python {@code o.name}. */
    // Compare CPython _PyObject_GetAttr in object.c
    // Also _PyObject_GetAttrId in object.c
    static PyObject getAttr(PyObject o, PyUnicode name)
            throws AttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        PyType t = o.getType();
        try {
            // Invoke __getattribute__.
            return (PyObject) t.tp_getattribute.invokeExact(o, name);
        } catch (AttributeError e) {
            try {
                // Not found or not defined: fall back on __getattr__.
                return (PyObject) t.tp_getattro.invokeExact(o, name);
            } catch (EmptyException ignored) {
                // __getattr__ not defined, original exception stands.
                if (e instanceof AttributeError) {
                    throw e;
                } else {
                    // Probably never, since inherited from object
                    throw noAttributeError(o, name);
                }
            }
        }
    }

    // static PyObject getAttr(PyObject o, PyUnicode name)
    // throws AttributeError, Throwable {
    // // Decisions are based on type of o (that of name is known)
    // PyType t = o.getType();
    // try {
    // return (PyObject) t.tp_getattro.invokeExact(o, name);
    // } catch (EmptyException e) {
    // throw noAttributeError(o, name);
    // }
    // }

    /** Python {@code o.name}. */
    // Compare CPython PyObject_GetAttr in object.c
    static PyObject getAttr(PyObject o, PyObject name)
            throws AttributeError, TypeError, Throwable {
        // Decisions are based on types of o and name
        if (name instanceof PyUnicode) {
            return getAttr(o, (PyUnicode) name);
        } else {
            throw new TypeError(ATTR_MUST_BE_STRING_NOT, name);
        }
    }

    //    /** Python {@code o.name}. */
    //    // Compare CPython PyObject_GetAttrString in object.c
    //    static PyObject getAttr(PyObject o, String name)
    //            throws AttributeError, Throwable {
    //        return getAttr(o, Py.str(name));
    //    }

    /** Python {@code o.name = value}. */
    // Compare CPython PyObject_SetAttr in object.c
    static void setAttr(PyObject o, PyUnicode name, PyObject value)
            throws AttributeError, TypeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        try {
            o.getType().tp_setattro.invokeExact(o, name, value);
        } catch (EmptyException e) {
            String fmt =
                    "'%.100s' object has %s attributes (%s .%.50s)";
            PyType oType = o.getType();
            String kind = Slot.tp_getattro.isDefinedFor(oType)
                    ? "only read-only" : "no";
            String mode = value == null ? "del" : "assign to";
            throw new TypeError(fmt, oType, kind, mode, name);
        }
    }

    /** Python {@code o.name = value}. */
    // Compare CPython PyObject_SetAttr in object.c
    static void setAttr(PyObject o, PyObject name, PyObject value)
            throws AttributeError, TypeError, Throwable {
        if (name instanceof PyUnicode) {
            setAttr(o, (PyUnicode) name, value);
        } else {
            throw new TypeError(ATTR_MUST_BE_STRING_NOT, name);
        }
    }

    //    /** Python {@code o.name}. */
    //    // Compare CPython PyObject_GetAttrString in object.c
    //    static void setAttr(PyObject o, String name, PyObject value)
    //            throws AttributeError, Throwable {
    //        setAttr(o, Py.str(name), value);
    //    }

    protected static final String HAS_NO_LEN =
            "object of type '%.200s' has no len()";
    private static final String MUST_BE_INT_NOT =
            "sequence index must be integer, not '%.200s'";
    private static final String NOT_SUBSCRIPTABLE =
            "'%.200s' object is not subscriptable";
    private static final String ATTR_MUST_BE_STRING_NOT =
            "attribute name must be string, not '%.200s'";
    private static final String IS_REQUIRED_NOT =
            "%.200s is required, not '%.100s'";
    protected static final String NOT_ITEM_ASSIGNMENT =
            "'%.200s' object does not support item assignment";
    private static final String RETURNED_NON_TYPE =
            "%.200s returned non-%.200s (type %.200s)";

    /**
     * Create a {@link TypeError} with a message involving the type of
     * {@code o}.
     *
     * @param fmt format string for message (with one {@code %s}
     * @param o object whose type name will substitute for {@code %s}
     * @return exception to throw
     */
    static TypeError typeError(String fmt, PyObject o) {
        return new TypeError(fmt, o.getType().getName());
    }

    /**
     * Create a {@link TypeError} with a message along the lines "T
     * indices must be integers or slices, not X" involving the type
     * name T of a target and the type X of {@code o} presented as an
     * index, e.g. "list indices must be integers or slices, not str".
     *
     * @param t target of function or operation
     * @param o actual object presented as an index
     * @return exception to throw
     */
    static TypeError indexTypeError(PyObject t, PyObject o) {
        String fmt =
                "%.200s indices must be integers or slices, not %.200s";
        return new TypeError(fmt, t.getType().getName(),
                o.getType().getName());
    }

    /**
     * Create a {@link TypeError} with a message along the lines "T is
     * required, not X" involving any descriptive phrase T and the type
     * X of {@code o}, e.g. "<u>a bytes-like object</u> is required, not
     * '<u>str</u>'".
     *
     * @param t expected kind of thing
     * @param o actual object returned
     * @return exception to throw
     */
    static TypeError requiredTypeError(String t, PyObject o) {
        return new TypeError(IS_REQUIRED_NOT, t, o.getType().getName());
    }

    /**
     * Create a {@link TypeError} with a message along the lines "F()
     * [nth] argument must be T, not X", involving a function name,
     * optionally an ordinal n, an expected type T and the type X of
     * {@code o}, e.g. "int() argument must be a string, a bytes-like
     * object or a number, not 'list'" or "complex() second argument
     * must be a number, not 'type'".
     *
     * @param f name of function or operation
     * @param n ordinal of argument: 1 for "first", etc., 0 for ""
     * @param t expected kind of argument
     * @param o actual argument (not its type)
     * @return exception to throw
     */
    static TypeError argumentTypeError(String f, int n, String t,
            PyObject o) {
        return new TypeError(ARGUMENT_MUST_BE, f, ordinal(n), t,
                o.getType().getName());
    }

    // Helper for argumentTypeError
    private static String ordinal(int n) {
        switch (n) {
            case 0:
                return "";
            case 1:
                return " first";
            case 2:
                return " second";
            case 3:
                return " third";
            default:
                return String.format(" %dth", n);
        }
    }

    private static final String ARGUMENT_MUST_BE =
            "%s()%s argument must be %s, not '%.200s'";

    /**
     * Create a {@link TypeError} with a message along the lines "F
     * returned non-T (type X)" involving a function name, an expected
     * type T and the type X of {@code o}, e.g. "__int__ returned
     * non-int (type str)".
     *
     * @param f name of function or operation
     * @param t expected type of return
     * @param o actual object returned
     * @return exception to throw
     */
    static TypeError returnTypeError(String f, String t, PyObject o) {
        return new TypeError(RETURNED_NON_TYPE, f, t,
                o.getType().getName());
    }

    /**
     * Create a {@link AttributeError} with a message along the lines
     * "'T' object has no attribute N", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static AttributeError noAttributeError(PyObject v, PyObject name) {
        String fmt = "'%.50s' object has no attribute '%.50s'";
        return new AttributeError(fmt, v.getType().getName(), name);
    }

    /**
     * Create a {@link AttributeError} with a message along the lines
     * "'T' object attribute N is read-only", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static AttributeError readonlyAttributeError(PyObject v,
            PyObject name) {
        String fmt = "'%.50s' object attribute '%s' is read-only";
        return new AttributeError(fmt, v.getType().getName(), name);
    }

    /**
     * Submit a {@link DeprecationWarning} call (which may result in an
     * exception) with the same message as
     * {@link #returnTypeError(String, String, PyObject)}, the whole
     * followed by one about deprecation of the facility.
     *
     * @param f name of function or operation
     * @param t expected type of return
     * @param o actual object returned
     * @return exception to throw
     */
    static void returnDeprecation(String f, String t, PyObject o) {
        Warnings.format(DeprecationWarning.TYPE, 1,
                RETURNED_NON_TYPE_DEPRECATION, f, t,
                o.getType().getName(), t);
    }

    private static final String RETURNED_NON_TYPE_DEPRECATION =
            RETURNED_NON_TYPE + ".  "
                    + "The ability to return an instance of a strict "
                    + "subclass of %s is deprecated, and may be "
                    + "removed in a future version of Python.";

    /**
     * True iff the object has a slot for conversion to the index type.
     *
     * @param obj to test
     * @return whether {@code obj} has non-empty {@link Slot#nb_index}
     */
    static boolean indexCheck(PyObject obj) {
        return Slot.nb_index.isDefinedFor(obj.getType());
    }

    /** Throw generic something went wrong internally (last resort). */
    static void badInternalCall() {
        throw new InterpreterError("bad internal call");
    }

}
