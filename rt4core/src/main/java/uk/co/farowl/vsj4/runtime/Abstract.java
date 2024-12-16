// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime;

import java.lang.invoke.MethodHandle;
import java.util.function.Supplier;

import uk.co.farowl.vsj4.runtime.internal._PyUtil;
import uk.co.farowl.vsj4.runtime.kernel.Representation;
import uk.co.farowl.vsj4.runtime.kernel.SpecialMethod;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * The "abstract interface" to operations on Python objects. Methods
 * here execute the special methods of the type definition of the
 * objects passed in. A primary application is to the CPython byte code
 * interpreter. (Methods here often correspond closely to a CPython
 * opcode.)
 * <p>
 * See also {@link PyNumber} and {@link Callables} which contain the
 * abstract interface to the corresponding type families.
 */
// Compare CPython Objects/abstract.c
public class Abstract {

    /**
     * There are only static methods here, so no instances should be
     * created. Formally make the constructor {@code protected} so we
     * can sub-class and refer easily to methods here. (Otherwise
     * {@code private} would be the right choice.)
     */
    protected Abstract() {}

    /**
     * The equivalent of the Python expression {@code repr(o)}, and is
     * called by the {@code repr()} built-in function.
     *
     * @param o object
     * @return the string representation of {@code o}
     * @throws PyBaseException (TypeError) if {@code __repr__} returns a
     *     non-string
     * @throws Throwable from invoked implementation of {@code __repr__}
     */
    // Compare CPython PyObject_Repr in object.c
    public static Object repr(Object o)
            throws PyBaseException, Throwable {
        if (o == null) {
            return "<null>";
        } else {
            Representation rep = PyType.representationOf(o);
            MethodHandle mh = rep.handle(SpecialMethod.op_repr, o);
            try {
                Object res = mh.invoke(o);
                if (PyUnicode.TYPE.check(res)) {
                    return res;
                } else {
                    throw returnTypeError("__repr__", "string", res);
                }
            } catch (EmptyException e) {
                return "<" + _PyUtil.toAt(o) + ">";
            }
        }
    }

    /**
     * The equivalent of the Python expression str(o).
     *
     * @param o object
     * @return the string representation of {@code o}
     * @throws PyBaseException (TypeError) if {@code __str__} or
     *     {@code __repr__} returns a non-string
     * @throws Throwable from invoked implementations of {@code __str__}
     *     or {@code __repr__}
     */
    // Compare CPython PyObject_Str in object.c
    public static Object str(Object o) throws Throwable {
        if (o == null) {
            return "<null>";
        } else if (PyUnicode.TYPE.checkExact(o)) {
            return o;
        } else {
            try {
                Representation rep = PyType.registry.get(o.getClass());
                Object res = rep.op_str.invokeExact(o);
                if (PyUnicode.TYPE.check(res)) {
                    return res;
                } else {
                    throw returnTypeError("__str__", "string", res);
                }
            } catch (EmptyException e) {
                return repr(o);
            }
        }
    }

    /**
     * Convert a given {@code Object} to an instance of a Java class.
     * Raise a {@code TypeError} if the conversion fails.
     *
     * @param <T> target type defined by {@code c}
     * @param o the {@code Object} to convert.
     * @param c the class to convert it to.
     * @return converted value
     */
    @SuppressWarnings("unchecked")
    public static <T> T tojava(Object o, Class<T> c) {
        try {
            // XXX Stop-gap implementation (just cast it)
            if (c.isAssignableFrom(o.getClass())) {
                return (T)o;
            } else {
                throw new EmptyException();
            }
            // XXX Replace when this slot is defined:
            // Representation rep=PyType.registry.get(o.getClass());
            // MethodHandle mh=SpecialMethod.op_tojava.handle(rep);
            // return (T)mh.invokeExact(o, c);
        } catch (NullPointerException npe) {
            // Probably an error, but easily converted.
            return null;
        } catch (EmptyException e) {
            throw typeError("cannot convert %s to %s", o, c.getName());
        }
    }

    /**
     * Compute and return the hash value of an object. This is the
     * equivalent of the Python expression {@code hash(v)}.
     *
     * @param v to hash
     * @return the hash
     * @throws PyBaseException (TypeError) if {@code v} is an unhashable
     *     type
     * @throws Throwable on errors within {@code __hash__}
     */
    public static int hash(Object v) throws PyBaseException, Throwable {
        try {
            Representation rep = PyType.registry.get(v.getClass());
            MethodHandle mh = SpecialMethod.op_hash.handle(rep);
            return (int)mh.invokeExact(v);
        } catch (EmptyException e) {
            throw typeError("unhashable type: %s", v);
        }
    }

    /**
     * Test a value used as condition in a {@code for} or {@code if}
     * statement.
     *
     * @param v to test
     * @return {@code true} if Python-truthy
     * @throws Throwable from invoked implementations of
     *     {@code __bool__} or {@code __len__}
     */
    // Compare CPython PyObject_IsTrue in object.c
    public static boolean isTrue(Object v) throws Throwable {
        // Begin with common special cases
        if (v == Py.True)
            return true;
        else if (v == Py.False || v == Py.None)
            return false;
        else {
            // Ask the object through the op_bool or op_len slots
            Representation rep = PyType.registry.get(v.getClass());
            if (SpecialMethod.op_bool.isDefinedFor(rep))
                return (boolean)SpecialMethod.op_bool.handle(rep)
                        .invokeExact(v);
            else if (SpecialMethod.op_len.isDefinedFor(rep))
                return 0 != (int)SpecialMethod.op_len.handle(rep)
                        .invokeExact(v);
            else
                // No op_bool and no length: claim everything is True.
                return true;
        }
    }

    /**
     * Perform a rich comparison, raising {@code TypeError} when the
     * requested comparison operator is not supported.
     *
     * @param v left operand
     * @param w right operand
     * @param op comparison type
     * @return comparison result
     * @throws Throwable from invoked implementations
     */
    // Compare CPython PyObject_RichCompare, do_richcompare in object.c
    public static Object richCompare(Object v, Object w, Comparison op)
            throws Throwable {
        return op.apply(v, w);
    }

    /**
     * Perform a rich comparison with boolean result. This wraps
     * {@link #richCompare(Object, Object, Comparison)}, converting the
     * result to Java {@code false} or {@code true}, or throwing
     * (probably {@link PyBaseException TypeError}), when the objects
     * cannot be compared.
     *
     * @param v left operand
     * @param w right operand
     * @param op comparison type
     * @return comparison result
     * @throws Throwable from invoked method implementations
     */
    // Compare CPython PyObject_RichCompareBool in object.c
    public static boolean richCompareBool(Object v, Object w,
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
        return isTrue(op.apply(v, w));
    }

    /**
     * Perform a rich comparison with boolean result. This wraps
     * {@link #richCompare(Object, Object, Comparison)}, converting the
     * result to Java {@code false} or {@code true}.
     * <p>
     * When the when the objects cannot be compared, the client gets to
     * choose the exception through the provider {@code exc}. When this
     * is {@code null}, the return will simply be {@code false} for
     * incomparable objects.
     *
     * @param <T> type of exception
     * @param v left operand
     * @param w right operand
     * @param op comparison type
     * @param exc supplies an exception of the desired type
     * @return comparison result
     * @throws T on any kind of error
     */
    public static <T extends PyBaseException> boolean richCompareBool(
            Object v, Object w, Comparison op, Supplier<T> exc)
            throws T {
        try {
            return richCompareBool(v, w, op);
        } catch (Throwable e) {
            if (exc == null)
                return false;
            else
                throw exc.get();
        }
    }

    /**
     * Attribute access: {@code o.name} with Python semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @return {@code o.name}
     * @throws PyAttributeError if non-existent etc.
     * @throws Throwable on other errors
     */
    // Compare CPython _PyObject_GetAttr in object.c
    // Also PyObject_GetAttrString in object.c
    public static Object getAttr(Object o, String name)
            throws PyAttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        Representation rep = PyType.registry.get(o.getClass());
        try {
            // Invoke __getattribute__.
            MethodHandle mh = SpecialMethod.op_getattribute.handle(rep);
            return mh.invokeExact(o, name);
        } catch (EmptyException | PyAttributeError e) {
            try {
                // Not found or not defined: fall back on __getattr__.
                return SpecialMethod.op_getattr.handle(rep)
                        .invokeExact(o, name);
            } catch (EmptyException ignored) {
                // __getattr__ not defined, original exception stands.
                if (e instanceof PyAttributeError) { throw e; }
                throw noAttributeError(o, name);
            }
        }
    }

    /**
     * Attribute access: {@code o.name} with Python semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @return {@code o.name}
     * @throws PyAttributeError if non-existent etc.
     * @throws PyBaseException (TypeError) if the name is not a
     *     {@code str}
     * @throws Throwable on other errors
     */
    // Compare CPython PyObject_GetAttr in object.c
    public static Object getAttr(Object o, Object name)
            throws PyAttributeError, PyBaseException, Throwable {
        // Decisions are based on types of o and name
        return getAttr(o, PyUnicode.asString(name,
                Abstract::attributeNameTypeError));
    }

    /**
     * Python {@code o.name} returning {@code null} when not found (in
     * place of {@code AttributeError} as would
     * {@link #getAttr(Object, String)}). Other exceptions that may be
     * raised in the process propagate.
     *
     * @param o the object in which to look for the attribute
     * @param name of the attribute sought
     * @return the attribute or {@code null}
     * @throws Throwable on other errors than {@code AttributeError}
     */
    // Compare CPython _PyObject_LookupAttr in object.c
    public static Object lookupAttr(Object o, String name)
            throws Throwable {
        // Decisions are based on type of o (that of name is known)
        Representation rep = PyType.registry.get(o.getClass());
        try {
            // Invoke __getattribute__
            return SpecialMethod.op_getattribute.handle(rep)
                    .invokeExact(o, name);
        } catch (EmptyException | PyAttributeError e) {
            try {
                // Not found or not defined: fall back on __getattr__.
                return SpecialMethod.op_getattr.handle(rep)
                        .invokeExact(o, name);
            } catch (EmptyException | PyAttributeError ignored) {
                // __getattr__ not defined, original exception stands.
                return null;
            }
        }
    }

    /**
     * Python {@code o.name}: returning {@code null} when not found (in
     * place of {@code AttributeError} as would
     * {@link #getAttr(Object, Object)}). Other exceptions that may be
     * raised in the process propagate.
     *
     * @param o the object in which to look for the attribute
     * @param name of the attribute sought
     * @return the attribute or {@code null}
     * @throws PyBaseException (TypeError) if {@code name} is not a
     *     Python {@code str}
     * @throws Throwable on other errors
     */
    // Compare CPython _PyObject_LookupAttr in object.c
    public static Object lookupAttr(Object o, Object name)
            throws PyBaseException, Throwable {
        // Decisions are based on types of o and name
        return lookupAttr(o, PyUnicode.asString(name,
                Abstract::attributeNameTypeError));
    }

    /**
     * Attribute assignment: {@code o.name = value} with Python
     * semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @param value to set
     * @throws PyAttributeError if non-existent etc.
     * @throws Throwable on other errors
     */
    // Compare CPython PyObject_SetAttr in object.c
    public static void setAttr(Object o, String name, Object value)
            throws PyAttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        try {
            Representation rep = PyType.registry.get(o.getClass());
            MethodHandle mh = SpecialMethod.op_setattr.handle(rep);
            mh.invokeExact(o, name, value);
        } catch (EmptyException e) {
            throw attributeAccessError(o, name,
                    SpecialMethod.op_setattr);
        }
    }

    /**
     * Attribute assignment: {@code o.name = value} with Python
     * semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @param value to set
     * @throws PyAttributeError if non-existent etc.
     * @throws PyBaseException (TypeError) if the name is not a
     *     {@code str}
     * @throws Throwable on other errors
     */
    // Compare CPython PyObject_SetAttr in object.c
    public static void setAttr(Object o, Object name, Object value)
            throws PyAttributeError, PyBaseException, Throwable {
        // Decisions are based on types of o and name
        setAttr(o, PyUnicode.asString(name,
                Abstract::attributeNameTypeError), value);
    }

    /**
     * Attribute deletion: {@code del o.name} with Python semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @throws PyAttributeError if non-existent etc.
     * @throws Throwable on other errors
     *
     */
    // Compare CPython PyObject_DelAttr in abstract.h
    // which is a macro for PyObject_SetAttr in object.c
    public static void delAttr(Object o, String name)
            throws PyAttributeError, Throwable {
        // Decisions are based on type of o (that of name is known)
        try {
            Representation rep = PyType.registry.get(o.getClass());
            MethodHandle mh = SpecialMethod.op_delattr.handle(rep);
            mh.invokeExact(o, name);
        } catch (EmptyException e) {
            throw attributeAccessError(o, name,
                    SpecialMethod.op_delattr);
        }
    }

    /**
     * Attribute deletion: {@code del o.name} with Python semantics.
     *
     * @param o object to operate on
     * @param name of attribute
     * @throws PyAttributeError if non-existent etc.
     * @throws PyBaseException (TypeError) if the name is not a
     *     {@code str}
     * @throws Throwable on other errors
     */
    // Compare CPython PyObject_SetAttr in object.c
    public static void delAttr(Object o, Object name)
            throws PyAttributeError, PyBaseException, Throwable {
        // Decisions are based on types of o and name
        delAttr(o, PyUnicode.asString(name,
                Abstract::attributeNameTypeError));
    }

    /**
     * {@code true} iff {@code obj} is not {@code null}, and defines
     * {@code __isabstractmethod__} to be Python-truthy.
     *
     * @param obj to test
     * @return whether abstract
     * @throws Throwable on error
     */
    // Compare CPython _PyObject_IsAbstract in object.c
    public static boolean isAbstract(Object obj) throws Throwable {
        if (obj == null)
            return false;
        else {
            Object abs = lookupAttr(obj, "__isabstractmethod__");
            return abs != null && isTrue(abs);
        }
    }

    /**
     * Get {@code cls.__bases__}, a Python {@code tuple}, by name from
     * the object invoking {@code __getattribute__}. If {@code cls} does
     * not define {@code __bases__}, or it is not a {@code tuple},
     * return {@code null}. In that case, it is customary for the caller
     * to throw a {@link PyBaseException TypeError}.
     *
     * @param cls normally a type object
     * @return {@code cls.__bases__} or {@code null}
     * @throws Throwable propagated from {@code __getattribute__}
     */
    // Compare CPython abstract_get_bases in abstract.c
    private static PyTuple getBasesOf(Object cls) throws Throwable {
        // Should return a tuple: convert anything else to null.
        Object bases = lookupAttr(cls, "__bases__");
        // Treat non-tuple as not having the attribute.
        return bases instanceof PyTuple ? (PyTuple)bases : null;
    }

    /**
     * Get {@code inst.__class__}, a Python {@code tuple}, by name from
     * the object invoking {@code __getattribute__}. If {@code inst}
     * does not define {@code __class__}, or it is not a {@code type},
     * return {@code null}.
     *
     * @param inst object in which to seek __class__
     * @return {@code inst.__class__} or {@code null}
     * @throws Throwable propagated from {@code __getattribute__}
     */
    // Compare CPython abstract_get_class in abstract.c
    private static PyType getClassOf(Object inst) throws Throwable {
        // Should return a type: convert anything else to null.
        Object klass = lookupAttr(inst, "__class__");
        // Treat non-tuple as not having the attribute.
        return klass instanceof PyType ? (PyType)klass : null;
    }

    /**
     * Return {@code true} iff the type of {@code inst} is an instance
     * of {@code cls}.
     *
     * The answer is found by traversing the {@code __bases__} tuples
     * recursively, therefore does not depend on the MRO or respect
     * {@code cls.__subclasscheck__}.
     *
     * @param inst object to test
     * @param cls class or any object defining {@code __bases__}
     * @return whether {@code inst} is an instance of {@code cls}
     * @throws Throwable from looking up {@code __bases__}
     */
    // Compare CPython recursive_isinstance in abstract.c
    // and _PyObject_RealIsInstance in abstract.c
    static boolean recursiveIsInstance(Object inst, Object cls)
            throws PyBaseException, Throwable {

        if (cls instanceof PyType) {
            // cls is a single type object (therefore a PyType)
            PyType type = (PyType)cls;
            PyType instType = PyType.of(inst);

            if (instType == type || instType.isSubTypeOf(type))
                return true;
            else {
                // Maybe inst defines a __class__ attribute.
                PyType instClass = getClassOf(inst);
                /*
                 * If inst.__class__ is defined, and it is not the
                 * instType we just tested, the result depends on that.
                 */
                return instClass != null && instClass != instType
                        && instClass.isSubTypeOf(type);
            }

        } else if (getBasesOf(cls) == null) {
            // cls has no attribute __bases__
            throw argumentTypeError("isinstance", 2,
                    "a type or tuple of types", cls);

        } else {
            /*
             * cls is an object with a __bases__ attribute, which should
             * be a tuple of type objects. Test inst.__class__ against
             * that tuple.
             */
            Object instClass = getClassOf(inst);
            return instClass != null
                    && isSubclassHelper(instClass, cls);
        }
    }

    /**
     * Return {@code true} iff {@code inst} is an instance of the class
     * {@code cls} or a subclass of {@code cls}.
     * <p>
     * If {@code cls} is a {@code tuple}, the check will be done against
     * every entry in {@code cls}. The result will be {@code true} iff
     * at least one of the checks returns {@code true}, .
     * <p>
     * If {@code cls} has an {@code __instancecheck__()} method, it will
     * be called to determine the subclass status as described in PEP
     * 3119. Otherwise, {@code inst} is an instance of {@code cls} if
     * its class is a subclass of {@code cls}.
     * <p>
     * An instance {@code inst} can override what is considered its
     * class by having a {@code __class__} attribute. An object
     * {@code cls} can override whether it is considered a class, and
     * what its base classes are, by having a {@code __bases__}
     * attribute (which must be a {@code tuple} of base classes).
     *
     * @param inst object to test
     * @param cls class or {@code tuple} of classes to test against
     * @return {@code isinstance(inst, cls)}
     * @throws PyBaseException (TypeError) if {@code cls} is not a class
     *     or tuple of classes
     * @throws Throwable propagated from {@code __instancecheck__} or
     *     other causes
     */
    // Compare CPython PyObject_IsInstance in abstract.c
    static boolean isInstance(Object inst, Object cls)
            throws PyBaseException, Throwable {

        PyType clsType;

        if (PyType.of(inst) == cls)
            // Quick result available
            return true;

        else if ((clsType = PyType.of(cls)) == PyType.TYPE) {
            // cls is a (single) Python type, and not a metaclass.
            return recursiveIsInstance(inst, cls);

        } else if (clsType.isSubTypeOf(PyTuple.TYPE)) {
            // cls is a tuple of (should be) types
            // try (RecursionState state = ThreadState
            // .enterRecursiveCall("in __instancecheck__")) {
            // Result is true if true for any type in cls
            for (Object type : (PyTuple)cls) {
                if (isInstance(inst, type))
                    return true;
                // }
            }
            return false;

        } else {
            // The type of cls should be a sub-type of PyType.TYPE
            Object checker = lookupSpecial(cls, "__instancecheck__");
            if (checker != null) {
                // cls has an __instancecheck__ to consult.
                // try (RecursionState state = ThreadState
                // .enterRecursiveCall("in __instancecheck__")) {
                return isTrue(Callables.call(checker, inst));
                // }
            } else {
                /*
                 * cls is not exactly a type or a tuple and has no
                 * __instancecheck__: treat provisionally as a type.
                 */
                return recursiveIsInstance(inst, cls);
            }
        }
    }

    /**
     * Return {@code true} iff the class {@code derived} is identical to
     * or derived from the class {@code cls}. The answer is sought along
     * the MRO of {@code derived} if {@code derived} and {@code cls} are
     * both Python {@code type} objects, or sub-classes of {@code type},
     * or by traversal of {@code cls.__bases__} otherwise.
     *
     * @param derived candidate derived type.
     * @param cls type that may be an ancestor of {@code derived}, (but
     *     not a tuple of such).
     * @return ẁhether {@code derived} is a sub-class of {@code cls} by
     *     these criteria.
     * @throws PyBaseException (TypeError) if either input has no
     *     {@code __bases__} tuple.
     * @throws Throwable propagated from {@code __subclasscheck__} or
     *     other causes
     */
    // Compare CPython recursive_issubclass in abstract.c
    // and _PyObject_RealIsSubclass in abstract.c
    static boolean recursiveIsSubclass(Object derived, Object cls)
            throws PyBaseException, Throwable {
        if (cls instanceof PyType && derived instanceof PyType)
            // Both are PyType so this is relatively easy.
            return ((PyType)derived).isSubTypeOf((PyType)cls);
        else if (getBasesOf(derived) == null)
            // derived is neither PyType nor has __bases__
            throw PyErr.format(PyExc.TypeError, "issubclass", 1,
                    "a class", derived);
        else if (getBasesOf(cls) == null)
            // cls is neither PyType nor has __bases__
            throw argumentTypeError("issubclass", 2,
                    "a class or tuple of classes", cls);
        else
            // Answer by traversing cls.__bases__ for derived
            return isSubclassHelper(derived, cls);
    }

    /**
     * Return {@code true} iff the class {@code derived} is identical to
     * or derived from the class {@code cls}. If {@code cls} is a
     * {@code tuple}, the check will be carried out against every entry
     * in {@code cls}. The result will be {@code true} only when at
     * least one of the checks is {@code true}.
     *
     * @param derived candidate derived type
     * @param cls type that may be an ancestor of {@code derived}, or a
     *     tuple of such
     * @return {@code issubclass(derived, cls)}
     * @throws Throwable propagated from {@code __subclasscheck__} or
     *     other causes
     */
    // Compare CPython PyObject_IsSubclass in abstract.c
    static boolean isSubclass(Object derived, Object cls)
            throws Throwable {
        PyType clsType = PyType.of(cls);
        if (clsType == PyType.TYPE) {
            // cls is exactly a Python type: avoid __subclasscheck__
            if (derived == cls)
                return true;
            return recursiveIsSubclass(derived, cls);

        } else if (clsType.isSubTypeOf(PyTuple.TYPE)) {
            // try (RecursionState state = ThreadState
            // .enterRecursiveCall(" in __subclasscheck__")) {
            for (Object item : (PyTuple)cls) {
                if (isSubclass(derived, item))
                    return true;
            }
            // }
            return false;

        } else {

            Object checker = lookupSpecial(cls, "__subclasscheck__");

            if (checker != null) {
                // try (RecursionState state = ThreadState
                // .enterRecursiveCall(" in __subclasscheck__")) {
                return isTrue(Callables.call(checker, derived));
                // }

            } else {
                /*
                 * cls is not exactly a type or a tuple and has no
                 * __subclasscheck__: treat provisionally as a type.
                 */
                return recursiveIsSubclass(derived, cls);
            }
        }
    }

    /**
     * Method lookup in the type without looking in the instance
     * dictionary (so we can't use
     * {@link Abstract#getAttr(Object, String)}) but still binding it to
     * the instance.
     *
     * @throws Throwable propagated from descriptor {@code __get__} or
     *     other causes
     */
    // Compare CPython _PyObject_LookupSpecial in typeobject.c
    /*
     * In CPython {@code _PyObject_LookupSpecial()} returns {@code null}
     * without raising an exception when the {@linkplain
     * PyType#lookup(Object)} fails.
     */
    private static Object lookupSpecial(Object self, String name)
            throws Throwable {
        // Look up attr by name in the type of self
        PyType selfType = PyType.of(self);
        Object res = selfType.lookup(name);
        if (res == null) {
            // CPython error: need alternative look-up not throwing
            return null;
        } else {
            // res might be a descriptor
            try {
                // invoke the descriptor's __get__
                Representation rep =
                        PyType.registry.get(res.getClass());
                MethodHandle f = SpecialMethod.op_get.handle(rep);
                res = f.invokeExact(res, self, selfType);
            } catch (EmptyException e) {}
        }
        return res;
    }

    /**
     * This is equivalent to the Python expression {@code iter(o)}. It
     * returns a new Python iterator for the object argument, or the
     * object itself if it is already an iterator.
     * <p>
     * {@code o} must either define {@code __iter__}, which will be
     * called to obtain an iterator, or define {@code __getitem__}, on
     * which an iterator will be created. It is guaranteed that the
     * object returned defines {@code __next__}.
     *
     * @param o the claimed iterable object
     * @return an iterator on {@code o}
     * @throws PyBaseException (TypeError) if the object cannot be
     *     iterated
     * @throws Throwable from errors in {@code o.__iter__}
     */
    // Compare CPython PyObject_GetIter in abstract.c
    // static Object getIterator(Object o) throws PyBaseException,
    // Throwable {
    // return getIterator(o, null);
    // }

    /**
     * Equivalent to {@link #getIterator(Object)}, with the opportunity
     * to specify the kind of Python exception to raise.
     *
     * @param <E> the type of exception to throw
     * @param o the claimed iterable object
     * @param exc a supplier (e.g. lambda expression) for the exception
     * @return an iterator on {@code o}
     * @throws E to throw if an iterator cannot be formed
     * @throws Throwable from errors in {@code o.__iter__}
     */
    // Compare CPython PyObject_GetIter in abstract.c
    // static <E extends PyBaseException> Object getIterator(Object o,
    // Supplier<E> exc) throws PyBaseException, Throwable {
    // Representation rep = PyType.registry.get(o.getClass());
    // if (SpecialMethod.op_iter.isDefinedFor(rep)) {
    // // o defines __iter__, call it.
    // Object r = SpecialMethod.op_iter.handle(rep).invokeExact(o);
    // // Did that return an iterator? Check r defines __next__.
    // if (SpecialMethod.op_next.isDefinedFor(
    // PyType.registry.get(r.getClass()))) {
    // return r;
    // } else if (exc == null) {
    // throw returnTypeError("iter", "iterator", r);
    // }
    // } else if (SpecialMethod.op_getitem.isDefinedFor(rep)) {
    // // o defines __getitem__: make a (Python) iterator.
    // return new PyIterator(o);
    // }
    //
    // // Out of possibilities: throw caller-defined exception
    // if (exc != null) {
    // throw exc.get();
    // } else {
    // throw typeError(NOT_ITERABLE, o);
    // }
    // }

    /**
     * Return {@code true} if the object {@code o} supports the iterator
     * protocol (has {@code __iter__}).
     *
     * @param o to test
     * @return true if {@code o} supports the iterator protocol
     */
    static boolean iterableCheck(Object o) {
        return SpecialMethod.op_iter
                .isDefinedFor(PyType.registry.get(o.getClass()));
    }

    /**
     * Return true if the object {@code o} is an iterator (has
     * {@code __next__}).
     *
     * @param o to test
     * @return true if {@code o} is an iterator
     */
    // Compare CPython PyIter_Check in abstract.c
    static boolean iteratorCheck(Object o) {
        return SpecialMethod.op_next
                .isDefinedFor(PyType.registry.get(o.getClass()));
    }

    /**
     * Return the next value from the Python iterator {@code iter}. If
     * there are no remaining values, returns {@code null}. If an error
     * occurs while retrieving the item, the exception propagates.
     *
     * @param iter the iterator
     * @return the next item
     * @throws Throwable from {@code iter.__next__}
     */
    // Compare CPython PyIter_Next in abstract.c
    static Object next(Object iter) throws Throwable {
        Representation rep = PyType.registry.get(iter.getClass());
        try {
            return SpecialMethod.op_next.handle(rep).invokeExact(iter);
        } catch (PyStopIteration e) {
            return null;
        } catch (EmptyException e) {
            throw typeError(NOT_ITERABLE, iter);
        }
    }

    // Plumbing -------------------------------------------------------

    /**
     * Crafted error supporting {@link #setAttr(Object, String, Object)
     * setAttr} and {@link #delAttr(Object, String) delAttr}.
     *
     * @param o object accessed
     * @param name of attribute
     * @param slot operation
     * @return an error to throw
     */
    private static PyBaseException attributeAccessError(Object o,
            String name, SpecialMethod slot) {
        String mode, kind,
                fmt = "'%.100s' object has %s attributes (%s.%.50s)";
        // What were we trying to do?
        switch (slot) {
            case op_delattr:
                mode = "delete ";
                break;
            case op_setattr:
                mode = "assign to ";
                break;
            default:
                mode = "";
                break;
        }
        // Can we even read this object's attributes?
        Representation rep = PyType.registry.get(o.getClass());
        String typeName = rep.pythonType(o).getName();
        boolean readable =
                SpecialMethod.op_getattribute.isDefinedFor(rep)
                        || SpecialMethod.op_getattr.isDefinedFor(rep);
        kind = readable ? "only read-only" : "no";
        // Now we know what to say
        return PyErr.format(PyExc.TypeError, fmt, typeName, kind, mode,
                name);
    }

    // Convenience functions constructing errors --------------------

    private static final String IS_REQUIRED_NOT =
            "%.200s is required, not '%.100s'";
    private static final String RETURNED_NON_TYPE =
            "%.200s returned non-%.200s (type %.200s)";
    private static final String ARGUMENT_MUST_BE =
            "%s()%s%s argument must be %s, not '%.200s'";
    private static final String NOT_MAPPING =
            "%.200s is not a mapping";
    private static final String NOT_ITERABLE =
            "%.200s object is not iterable";
    private static final String DESCR_NOT_DEFINING =
            "Type marked as %.20s descriptor does not define %.50s";

    /**
     * Return {@code true} iff {@code derived} is a Python sub-class of
     * {@code cls} (including where it is the same class). The answer is
     * found by traversing the {@code __bases__} tuples recursively,
     * therefore does not depend on the MRO or respect
     * {@code __subclasscheck__}.
     *
     * @param derived candidate derived type
     * @param cls type that may be an ancestor of {@code derived}
     * @return whether {@code derived} is a sub-class of {@code cls}
     * @throws Throwable from looking up {@code __bases__}
     */
    // Compare CPython abstract_issubclass in abstract.c
    private static boolean isSubclassHelper(Object derived, Object cls)
            throws Throwable {
        while (derived != cls) {
            // Consider the bases of derived
            PyTuple bases = getBasesOf(derived);
            int n;
            // derived is a subclass of cls if any of its bases is
            if (bases == null || (n = bases.size()) == 0) {
                // The __bases__ tuple is missing or empty ...
                return false;
            } else if (n == 1) {
                // The answer is the answer for that single base.
                derived = bases.get(0);
            } else {
                // several bases so work through them in sequence
                for (int i = 0; i < n; i++) {
                    if (isSubclassHelper(bases.get(i), cls))
                        return true;
                }
                // And not otherwise
                return false;
            }
        }
        return true;
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message
     * involving the type of {@code args[0]} and optionally other
     * arguments.
     *
     * @param fmt format for message with a {@code %s} first
     * @param args arguments to the formatted message, where Python type
     *     name of {@code args[0]} will replace it
     * @return exception to throw
     */
    public static PyBaseException typeError(String fmt,
            Object... args) {
        args[0] = PyType.of(args[0]).getName();
        return PyErr.format(PyExc.TypeError, fmt, args);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "T indices must be integers or slices, not X" involving
     * the a target type T and a purported index type X presented, e.g.
     * "list indices must be integers or slices, not str".
     *
     * @param t type of target of function or operation
     * @param x type of object presented as an index
     * @return exception to throw
     */
    public static PyBaseException indexTypeError(PyType t, PyType x) {
        String fmt =
                "%.200s indices must be integers or slices, not %.200s";
        return PyErr.format(PyExc.TypeError, fmt, t.getName(),
                x.getName());
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "T indices must be integers or slices, not X" involving
     * the type name T of a target {@code o} and the type name X of
     * {@code i} presented as an index, e.g. "list indices must be
     * integers or slices, not str".
     *
     * @param o target of function or operation
     * @param i actual object presented as an index
     * @return exception to throw
     */
    public static PyBaseException indexTypeError(Object o, Object i) {
        return indexTypeError(PyType.of(o), PyType.of(i));
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "T is required, not X" involving any descriptive phrase
     * T and the type X of {@code o}, e.g. "<u>a bytes-like object</u>
     * is required, not '<u>str</u>'".
     *
     * @param t expected kind of thing
     * @param o actual object involved
     * @return exception to throw
     */
    public static PyBaseException requiredTypeError(String t,
            Object o) {
        return PyErr.format(PyExc.TypeError, IS_REQUIRED_NOT, t,
                PyType.of(o).getName());
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "attribute name must be string, not 'X'" giving the
     * type X of {@code name}.
     *
     * @param name actual object offered as a name
     * @return exception to throw
     */
    public static PyBaseException attributeNameTypeError(Object name) {
        String fmt = "attribute name must be string, not '%.200s'";
        return PyErr.format(PyExc.TypeError, fmt,
                PyType.of(name).getName());
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "can't set attributes of X" giving str of {@code name}.
     *
     * @param obj actual object on which setting failed
     * @return exception to throw
     */
    public static PyBaseException cantSetAttributeError(Object obj) {
        return PyErr.format(PyExc.TypeError,
                "can't set attributes of %.200s", obj);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "F() [name] argument must be T, not X", involving a
     * function name, an argument name, an expected type description T
     * and the type X of {@code o}, e.g. "split() separator argument
     * must be str or None, not 'tuple'".
     *
     * @param f name of function or operation
     * @param name of argument
     * @param t describing the expected kind of argument
     * @param o actual argument (not its type)
     * @return exception to throw
     */
    public static PyBaseException argumentTypeError(String f,
            String name, String t, Object o) {
        String space = name.length() == 0 ? "" : " ";
        return PyErr.format(PyExc.TypeError, ARGUMENT_MUST_BE, f, space,
                name, t, PyType.of(o).getName());
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "F() [nth] argument must be T, not X", involving a
     * function name, optionally an ordinal n, an expected type
     * description T and the type X of {@code o}, e.g. "int() argument
     * must be a string, a bytes-like object or a number, not 'list'" or
     * "complex() second argument must be a number, not 'type'".
     *
     * @param f name of function or operation
     * @param n ordinal of argument: 1 for "first", etc., 0 for ""
     * @param t describing the expected kind of argument
     * @param o actual argument (not its type)
     * @return exception to throw
     */
    public static PyBaseException argumentTypeError(String f, int n,
            String t, Object o) {
        return argumentTypeError(f, ordinal(n), t, o);
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

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "F returned non-T (type X)" involving a function name,
     * an expected type T and the type X of {@code o}, e.g. "__int__
     * returned non-int (type str)".
     *
     * @param f name of function or operation
     * @param t expected type of return
     * @param o actual object returned
     * @return exception to throw
     */
    static PyBaseException returnTypeError(String f, String t,
            Object o) {
        return PyErr.format(PyExc.TypeError, RETURNED_NON_TYPE, f, t,
                PyType.of(o).getName());
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object has no attribute N", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError noAttributeError(Object v, Object name) {
        return noAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object has no attribute N", where T is the type given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError noAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object has no attribute '%.50s'";
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "'N' must be T, not 'X' as received" involving the name
     * N of the attribute, any descriptive phrase T and the type X of
     * {@code value}, e.g. "'<u>__dict__</u>' must be <u>a
     * dictionary</u>, not '<u>list</u>' as received".
     *
     * @param name of the attribute
     * @param kind expected kind of thing
     * @param value provided to set this attribute in some object
     * @return exception to throw
     */
    static PyBaseException attrMustBe(String name, String kind,
            Object value) {
        return PyErr.format(PyExc.TypeError, ATTR_TYPE, name, kind,
                PyType.of(value).getName());
    }

    private static final String ATTR_TYPE =
            "'%.50s' must be %.50s, not '%.50s' as received";

    /**
     * Create a {@link PyBaseException TypeError} with a message along
     * the lines "'N' must be a string, not 'X' as received".
     *
     * @param name of the attribute
     * @param value provided to set this attribute in some object
     * @return exception to throw
     */
    static PyBaseException attrMustBeString(String name, Object value) {
        return attrMustBe(name, "a string", value);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N is read-only", where T is the type of the
     * object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError readonlyAttributeError(Object v,
            Object name) {
        return readonlyAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N is read-only", where T is the type given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError readonlyAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object attribute '%s' is read-only";
        // XXX It would be cool if the type could be parameterised
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N cannot be deleted", where T is the type
     * of the object accessed.
     *
     * @param v object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError mandatoryAttributeError(Object v,
            Object name) {
        return mandatoryAttributeOnType(PyType.of(v), name);
    }

    /**
     * Create a {@link PyAttributeError} with a message along the lines
     * "'T' object attribute N cannot be deleted", where T is the type
     * given.
     *
     * @param type of object accessed
     * @param name of attribute
     * @return exception to throw
     */
    static PyAttributeError mandatoryAttributeOnType(PyType type,
            Object name) {
        String fmt = "'%.50s' object attribute '%s' cannot be deleted";
        // XXX It would be cool if the type could be parameterised
        return (PyAttributeError)PyErr.format(PyExc.AttributeError, fmt,
                type.getName(), name);
    }

    /**
     * Create an {@link PyBaseException IndexError} with a message along
     * the lines "N index out of range", where N is usually a function
     * or type name.
     *
     * @param name object accessed
     * @return exception to throw
     */
    static PyBaseException indexOutOfRange(String name) {
        String fmt = "%.50s index out of range";
        return PyErr.format(PyExc.IndexError, fmt, name);
    }

    /**
     * Submit a {@link DeprecationWarning} call (which may result in an
     * exception) with the same message as
     * {@link #returnTypeError(String, String, Object)}, the whole
     * followed by one about deprecation of the facility.
     *
     * @param f name of function or operation
     * @param t expected type of return
     * @param o actual object returned
     * @return {@code o}
     */
    static Object returnDeprecation(String f, String t, Object o) {
        Warnings.format(PyExc.DeprecationWarning, 1,
                RETURNED_NON_TYPE_DEPRECATION, f, t,
                PyType.of(o).getName(), t);
        return o;
    }

    private static final String RETURNED_NON_TYPE_DEPRECATION =
            RETURNED_NON_TYPE + ".  "
                    + "The ability to return an instance of a strict "
                    + "subclass of %s is deprecated, and may be "
                    + "removed in a future version of Python.";

    /** Throw generic something went wrong internally (last resort). */
    static void badInternalCall() {
        throw new InterpreterError("bad internal call");
    }

    /**
     * Convert any {@code Throwable} except an {@code Error} to a
     * {@code RuntimeException}, as by
     * {@link #asUnchecked(Throwable, String, Object...) asUnchecked(t,
     * ...)} with a default message.
     *
     * @param t to propagate or encapsulate
     * @return run-time exception to throw
     */
    static RuntimeException asUnchecked(Throwable t) {
        return asUnchecked(t, "non-Python Exception");
    }

    /**
     * Convert any {@code Throwable} except an {@code Error} to a
     * {@code RuntimeException}, so that (if not already) it becomes an
     * unchecked exception. An {@code Error} is re-thrown directly. We
     * use this in circumstances where a method cannot be declared to
     * throw the exceptions that methods within it are declared to
     * throw, and no specific handling is available locally.
     * <p>
     * In particular, we use it where a call is made to
     * {@code MethodHandle.invokeExact}. That is declared to throw
     * {@code Throwable}, but we know that the {@code Throwable} will
     * either be a {@code PyException} or will signify an interpreter
     * error that the local code cannot be expected to handle.
     *
     * @param t to propagate or encapsulate
     * @param during format string for detail message, typically like
     *     "during map.get(%.50s)" where {@code args} contains the key.
     * @param args to insert into format string.
     * @return run-time exception to throw
     */
    static RuntimeException asUnchecked(Throwable t, String during,
            Object... args) {
        if (t instanceof RuntimeException)
            return (RuntimeException)t;
        else if (t instanceof Error)
            throw (Error)t;
        else
            return new InterpreterError(t, during, args);
    }

    /**
     * Create an {@link InterpreterError} for use where a Python method
     * (or special method) implementation receives an argument that
     * should be impossible in a correct interpreter. This is a sort of
     * {@link PyBaseException TypeError} against the {@code self}
     * argument, but occurring where no programming error should be able
     * to induce it (e.g. coercion fails after we have passed the check
     * that descriptors make on their {@code obj}, or when invoking a
     * special method found via an {@link Representation} object.
     *
     * @param d expected kind of argument
     * @param o actual argument (not its type)
     * @return exception to throw
     */
    static InterpreterError impossibleArgumentError(String d,
            Object o) {
        return new InterpreterError(IMPOSSIBLE_CLASS, d,
                o.getClass().getName());
    }

    private static final String IMPOSSIBLE_CLASS =
            "expected %.50s argument "
                    + "but found impossible Java class %s";
}
