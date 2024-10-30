// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import static uk.co.farowl.vsj4.runtime.ClassShorthand.T;
import static uk.co.farowl.vsj4.support.JavaClassShorthand.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import uk.co.farowl.vsj4.runtime.ArgumentError;
import uk.co.farowl.vsj4.runtime.ClassShorthand;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.support.MissingFeature;
import uk.co.farowl.vsj4.support.internal.EmptyException;

/**
 * The {@code enum SpecialMethod} enumerates the special method names
 * from the Python Data Model and provides behaviour supporting their
 * use by run time system. These are methods that have a particular
 * meaning to the compiler for the implementation of primitive
 * representation (like negation, addition, and method call). When
 * interpreting Python byte code, they figure in the implementation of
 * the byte codes for primitive operations (UNARY_NEGATIVE, BINARY_OP,
 * CALL), usually via the "abstract object API". When generating JVM
 * byte code from Python, they are used in the call sites that implement
 * those operations. The selection and properties of the special methods
 * may vary from one version of Python to another. They are not
 * considered public API.
 * <p>
 * Each {@code SpecialMethod} member, given a {@code Representation}
 * object, is able to produce a method handle that can be used to invoke
 * the corresponding method on a Python object with that Java
 * representation. In the case of a shared representation, the handle
 * will reference the actual type written on the object.
 * <p>
 * Special methods may be implemented by any class, whether defined in
 * Java or in Python. They will appear first in the dictionary of the
 * {@link PyType} that describes the class as ordinary methods. A
 * {@code SpecialMethod} member, when asked for an invocation
 * {@code MethodHandle}, <i>may</i> produce a handle that looks up its
 * method in the dictionary of the type of the object, or it <i>may</i>
 * produce a handle cached on the {@code Representation}. The choice of
 * behaviour depends on the member. In the case of a shared
 * representation, it must produce a handle that will reference the
 * actual type of the object, before it continues into one of these two
 * behaviours.
 */
public enum SpecialMethod {

    // __hash__(Signature.UNARY, r->r.op_hash, (r,h)->{r.op_hash=h;} ),

    /*
     * The order of the members is not significant, but we take it from
     * the slotdefs[] table for definiteness. We do not have quite the
     * same entries, and no duplicates. There may yet be special methods
     * here that need not be cached, and maybe properties it would be
     * useful to add.
     */
    /**
     * Defines {@link Representation#op_repr}, support for built-in
     * {@code repr()}, with signature {@link Signature#UNARY}.
     */
    op_repr(Signature.UNARY),
    /**
     * Defines {@link Representation#op_hash}, support for object
     * hashing, with signature {@link Signature#LEN}.
     */
    op_hash(Signature.LEN),
    /**
     * Defines {@link Representation#op_call}, support for calling an
     * object, with signature {@link Signature#CALL}.
     */
    op_call(Signature.CALL),
    /**
     * Defines {@link Representation#op_str}, support for built-in
     * {@code str()}, with signature {@link Signature#UNARY}.
     */
    op_str(Signature.UNARY),

    /**
     * Defines {@link Representation#op_getattribute}, attribute get,
     * with signature {@link Signature#GETATTR}.
     */
    op_getattribute(Signature.GETATTR),
    /**
     * Defines {@link Representation#op_getattr}, attribute get, with
     * signature {@link Signature#GETATTR}.
     */
    op_getattr(Signature.GETATTR),
    /**
     * Defines {@link Representation#op_setattr}, attribute set, with
     * signature {@link Signature#SETATTR}.
     */
    op_setattr(Signature.SETATTR),
    /**
     * Defines {@link Representation#op_delattr}, attribute deletion,
     * with signature {@link Signature#DELATTR}.
     */
    op_delattr(Signature.DELATTR),

    /**
     * Defines {@link Representation#op_lt}, the {@code <} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_lt(Signature.BINARY, "<"),
    /**
     * Defines {@link Representation#op_le}, the {@code <=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_le(Signature.BINARY, "<="),
    /**
     * Defines {@link Representation#op_eq}, the {@code ==} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_eq(Signature.BINARY, "=="),
    /**
     * Defines {@link Representation#op_ne}, the {@code !=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_ne(Signature.BINARY, "!="),
    /**
     * Defines {@link Representation#op_gt}, the {@code >} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_gt(Signature.BINARY, ">"),
    /**
     * Defines {@link Representation#op_ge}, the {@code >=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_ge(Signature.BINARY, ">="),

    /**
     * Defines {@link Representation#op_iter}, get an iterator, with
     * signature {@link Signature#UNARY}.
     */
    op_iter(Signature.UNARY),
    /**
     * Defines {@link Representation#op_next}, advance an iterator, with
     * signature {@link Signature#UNARY}.
     */
    op_next(Signature.UNARY),

    /**
     * Defines {@link Representation#op_get}, descriptor
     * {@code __get__}, with signature {@link Signature#DESCRGET}.
     */
    op_get(Signature.DESCRGET),
    /**
     * Defines {@link Representation#op_set}, descriptor
     * {@code __set__}, with signature {@link Signature#SETITEM}.
     */
    op_set(Signature.SETITEM),
    /**
     * Defines {@link Representation#op_delete}, descriptor
     * {@code __delete__}, with signature {@link Signature#DELITEM}.
     */
    op_delete(Signature.DELITEM),

    /**
     * Defines {@link Representation#op_init}, object {@code __init__},
     * with signature {@link Signature#INIT}.
     */
    op_init(Signature.INIT),

    // __new__ is not a slot
    // __del__ is not a slot

    /**
     * Defines {@link Representation#op_await}, with signature
     * {@link Signature#UNARY}.
     */
    op_await(Signature.UNARY), // unexplored territory
    /**
     * Defines {@link Representation#op_aiter}, with signature
     * {@link Signature#UNARY}.
     */
    op_aiter(Signature.UNARY), // unexplored territory
    /**
     * Defines {@link Representation#op_anext}, with signature
     * {@link Signature#UNARY}.
     */
    op_anext(Signature.UNARY), // unexplored territory

    // Binary ops: reflected form comes first so we can reference it.
    /**
     * Defines {@link Representation#op_radd}, the reflected {@code +}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_radd(Signature.BINARY, "+"),
    /**
     * Defines {@link Representation#op_rsub}, the reflected {@code -}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rsub(Signature.BINARY, "-"),
    /**
     * Defines {@link Representation#op_rmul}, the reflected {@code *}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rmul(Signature.BINARY, "*"),
    /**
     * Defines {@link Representation#op_rmod}, the reflected {@code %}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rmod(Signature.BINARY, "%"),
    /**
     * Defines {@link Representation#op_rdivmod}, the reflected
     * {@code divmod} operation, with signature
     * {@link Signature#BINARY}.
     */
    op_rdivmod(Signature.BINARY, "divmod()"),
    /**
     * Defines {@link Representation#op_rpow}, the reflected {@code pow}
     * operation, with signature {@link Signature#BINARY} (not
     * {@link Signature#TERNARY} since only an infix operation can be
     * reflected).
     */
    op_rpow(Signature.BINARY, "**"), // unexplored territory
    /**
     * Defines {@link Representation#op_rlshift}, the reflected
     * {@code <<} operation, with signature {@link Signature#BINARY}.
     */
    op_rlshift(Signature.BINARY, "<<"),
    /**
     * Defines {@link Representation#op_rrshift}, the reflected
     * {@code >>} operation, with signature {@link Signature#BINARY}.
     */
    op_rrshift(Signature.BINARY, ">>"),
    /**
     * Defines {@link Representation#op_rand}, the reflected {@code &}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rand(Signature.BINARY, "&"),
    /**
     * Defines {@link Representation#op_rxor}, the reflected {@code ^}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rxor(Signature.BINARY, "^"),
    /**
     * Defines {@link Representation#op_ror}, the reflected {@code |}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_ror(Signature.BINARY, "|"),
    /**
     * Defines {@link Representation#op_rfloordiv}, the reflected
     * {@code //} operation, with signature {@link Signature#BINARY}.
     */
    op_rfloordiv(Signature.BINARY, "//"),
    /**
     * Defines {@link Representation#op_rtruediv}, the reflected
     * {@code /} operation, with signature {@link Signature#BINARY}.
     */
    op_rtruediv(Signature.BINARY, "/"),
    /**
     * Defines {@link Representation#op_rmatmul}, the reflected
     * {@code @} operation, with signature {@link Signature#BINARY}.
     */
    op_rmatmul(Signature.BINARY, "@"),

    /**
     * Defines {@link Representation#op_add}, the {@code +} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_add(Signature.BINARY, "+", op_radd),
    /**
     * Defines {@link Representation#op_sub}, the {@code -} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_sub(Signature.BINARY, "-", op_rsub),
    /**
     * Defines {@link Representation#op_mul}, the {@code *} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_mul(Signature.BINARY, "*", op_rmul),
    /**
     * Defines {@link Representation#op_mod}, the {@code %} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_mod(Signature.BINARY, "%", op_rmod),
    /**
     * Defines {@link Representation#op_divmod}, the {@code divmod}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_divmod(Signature.BINARY, "divmod()", op_rdivmod),
    /**
     * Defines {@link Representation#op_pow}, the {@code pow} operation,
     * with signature {@link Signature#TERNARY}.
     */
    op_pow(Signature.TERNARY, "**", op_rpow), // unexplored territory

    /**
     * Defines {@link Representation#op_neg}, the unary {@code -}
     * operation, with signature {@link Signature#UNARY}.
     */
    op_neg(Signature.UNARY, "unary -"),
    /**
     * Defines {@link Representation#op_pos}, the unary {@code +}
     * operation, with signature {@link Signature#UNARY}.
     */
    op_pos(Signature.UNARY, "unary +"),
    /**
     * Defines {@link Representation#op_abs}, the {@code abs()}
     * operation, with signature {@link Signature#UNARY}.
     */
    op_abs(Signature.UNARY, "abs()"),
    /**
     * Defines {@link Representation#op_bool}, conversion to a truth
     * value, with signature {@link Signature#PREDICATE}.
     */
    op_bool(Signature.PREDICATE),
    /**
     * Defines {@link Representation#op_invert}, the unary {@code ~}
     * operation, with signature {@link Signature#UNARY}.
     */
    op_invert(Signature.UNARY, "unary ~"),

    /**
     * Defines {@link Representation#op_lshift}, the {@code <<}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_lshift(Signature.BINARY, "<<", op_rlshift),
    /**
     * Defines {@link Representation#op_rshift}, the {@code >>}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_rshift(Signature.BINARY, ">>", op_rrshift),
    /**
     * Defines {@link Representation#op_and}, the {@code &} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_and(Signature.BINARY, "&", op_rand),
    /**
     * Defines {@link Representation#op_xor}, the {@code ^} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_xor(Signature.BINARY, "^", op_rxor),
    /**
     * Defines {@link Representation#op_or}, the {@code |} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_or(Signature.BINARY, "|", op_ror),

    /**
     * Defines {@link Representation#op_int}, conversion to an integer
     * value, with signature {@link Signature#UNARY}.
     */
    op_int(Signature.UNARY),
    /**
     * Defines {@link Representation#op_float}, conversion to a float
     * value, with signature {@link Signature#UNARY}.
     */
    op_float(Signature.UNARY),

    /**
     * Defines {@link Representation#op_iadd}, the {@code +=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_iadd(Signature.BINARY, "+="), // in-place: unexplored territory
    /**
     * Defines {@link Representation#op_isub}, the {@code -=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_isub(Signature.BINARY, "-="),
    /**
     * Defines {@link Representation#op_imul}, the {@code *=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_imul(Signature.BINARY, "*="),
    /**
     * Defines {@link Representation#op_imod}, the {@code %=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_imod(Signature.BINARY, "%="),
    /**
     * Defines {@link Representation#op_iand}, the {@code &=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_iand(Signature.BINARY, "&="),
    /**
     * Defines {@link Representation#op_ixor}, the {@code ^=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_ixor(Signature.BINARY, "^="),
    /**
     * Defines {@link Representation#op_ior}, the {@code |=} operation,
     * with signature {@link Signature#BINARY}.
     */
    op_ior(Signature.BINARY, "|="),

    /**
     * Defines {@link Representation#op_floordiv}, the {@code //}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_floordiv(Signature.BINARY, "//", op_rfloordiv),
    /**
     * Defines {@link Representation#op_truediv}, the {@code /}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_truediv(Signature.BINARY, "/", op_rtruediv),
    /**
     * Defines {@link Representation#op_ifloordiv}, the {@code //=}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_ifloordiv(Signature.BINARY, "//="),
    /**
     * Defines {@link Representation#op_itruediv}, the {@code /=}
     * operation, with signature {@link Signature#BINARY}.
     */
    op_itruediv(Signature.BINARY, "/="),

    /**
     * Defines {@link Representation#op_index}, conversion to an index
     * value, with signature {@link Signature#UNARY}.
     */
    op_index(Signature.UNARY),

    /**
     * Defines {@link Representation#op_matmul}, the {@code @} (matrix
     * multiply) operation, with signature {@link Signature#BINARY}.
     */
    op_matmul(Signature.BINARY, "@", op_rmatmul),
    /**
     * Defines {@link Representation#op_imatmul}, the {@code @=} (matrix
     * multiply in place) operation, with signature
     * {@link Signature#BINARY}.
     */
    op_imatmul(Signature.BINARY, "@="),

    /*
     * Note that CPython repeats for "mappings" the following "sequence"
     * slots, and slots for __add_ and __mul__, but that we do not need
     * to.
     */
    /**
     * Defines {@link Representation#op_len}, support for built-in
     * {@code len()}, with signature {@link Signature#LEN}.
     */
    op_len(Signature.LEN, "len()"),
    /**
     * Defines {@link Representation#op_getitem}, get at index, with
     * signature {@link Signature#BINARY}.
     */
    op_getitem(Signature.BINARY),
    /**
     * Defines {@link Representation#op_setitem}, set at index, with
     * signature {@link Signature#SETITEM}.
     */
    op_setitem(Signature.SETITEM),
    /**
     * Defines {@link Representation#op_delitem}, delete from index,
     * with signature {@link Signature#DELITEM}.
     */
    op_delitem(Signature.DELITEM),
    /**
     * Defines {@link Representation#op_contains}, the {@code in}
     * operation, with signature {@link Signature#BINARY_PREDICATE}.
     */
    op_contains(Signature.BINARY_PREDICATE);

    /** Method signature to match when filling this slot. */
    public final Signature signature;
    /** Name of implementation method to bind e.g. {@code "__add__"}. */
    public final String methodName;
    /** Name to use in error messages, e.g. {@code "+"} */
    final String opName;
    /** The alternate slot e.g. {@code __radd__} in {@code __add__}. */
    final SpecialMethod alt;

    /**
     * Reference to the field used to cache a handle to this method in a
     * {@link Representation} or {@code null} if not cached.
     */
    final VarHandle slotHandle;

    /** Description to use in help messages */
    public final String doc;

    /** Throws a {@link PyBaseException TypeError} (same signature) */
    private MethodHandle operandError;

    /**
     * Constructor for enum constants.
     *
     * @param signature of the function to be called
     * @param doc basis of documentation string, allows {@code null},
     *     just a symbol like "+", up to full docstring.
     * @param methodName implementation method (e.g. "__add__")
     * @param alt alternate slot (e.g. "op_radd")
     */
    SpecialMethod(Signature signature, String doc, String methodName,
            SpecialMethod alt) {
        this.signature = signature;
        this.methodName = dunder(methodName);
        this.alt = alt;
        // If doc is short, assume it's a symbol. Fall back on name.
        this.opName = (doc != null || doc.length() <= 3) ? doc : name();
        // Make up the docstring from whatever shorthand we got.
        this.doc = docstring(doc);
        this.slotHandle = Util.cacheVH(this);
    }

    SpecialMethod(Signature signature) {
        this(signature, null, null, null);
    }

    SpecialMethod(Signature signature, String doc) {
        this(signature, doc, null, null);
    }

    SpecialMethod(Signature signature, String doc, String methodName) {
        // XXX Is the method name ever not derived from name()
        this(signature, doc, methodName, null);
    }

    SpecialMethod(Signature signature, String doc, SpecialMethod alt) {
        this(signature, doc, null, alt);
    }

    /** Compute corresponding double-underscore method name. */
    private String dunder(String methodName) {
        if (methodName != null)
            // XXX Is the method name ever not derived from name()
            return methodName;
        else {
            // Map xx_add to __add__
            String s = name();
            int i = s.indexOf('_');
            if (i == 2)
                s = "__" + s.substring(i + 1) + "__";
            return s;
        }
    }

    /**
     * Use the length and content of the given string to generate a
     * standard documentation string according to a few patterns.
     *
     * @param doc basis of documentation string or {@code null}
     * @return generated documentation string
     */
    // Compare CPython *SLOT macros in typeobject.c
    private String docstring(String doc) {
        // XXX Make convenient. What CPython macros do is:
        // Unary:
        // NAME "($self, /)\n--\n\n" DOC)
        // In-place (ends with = but not <=, ==, !=, >=):
        // NAME "($self, value, /)\n--\n\nReturn self" DOC "value.")
        // Binary L op R:
        // NAME "($self, value, /)\n--\n\nReturn self" DOC "value.")
        // Binary R op L:
        // NAME "($self, value, /)\n--\n\nReturn value" DOC "self.")
        // Binary L op R not infix:
        // NAME "($self, value, /)\n--\n\n" DOC)
        // Binary R op L not infix:
        // NAME "($self, value, /)\n--\n\n" DOC)

        // We may add:
        // Use the signature, e.g. BINARY -> ($self, value, /)
        // If starts with "("
        // NAME DOC
        // Otherwise the string itself
        return doc;
    }

    @Override
    public java.lang.String toString() {
        // Programmer-friendly description
        return "SpecialMethod." + name() + " ( " + methodName
                + getType() + " ) [" + signature.name() + "]";
    }

    /**
     * Set a {@code MethodHandle} for the implementation of this
     * {@code SpecialMethod} in the corresponding cache field of the
     * given {@link Representation} object. During type construction, we
     * must always call this method, although not every member of this
     * {@code enum} caches its handle.
     *
     * @param rep target representation object
     * @param mh handle to set (or {@code null} if not implemented)
     * @return current contents of this slot in {@code rep}
     */
    public void set(Representation rep, MethodHandle mh) {
        // XXX Should the API let the caller avoid calling this?
        // FIXME Object instead, so we can reflect any callable.
        if (slotHandle != null) {
            if (mh == null) {
                if (mh.type().equals(getType())) {
                    slotHandle.set(rep, mh);
                } else {
                    throw slotTypeError(this, mh);
                }
            }
        }
    }

    /**
     * Get the {@code MethodHandle} for the implementation of this
     * {@code SpecialMethod}. This comes either from the corresponding
     * cache field of the given {@link Representation} object, or is a
     * handle on an unbound method found by lookup on the type. When the
     * representation is a shared on, the handle will find the type
     * using {@link Representation#pythonType(Object)}.
     *
     * @param rep target representation object
     * @return current contents of this slot in {@code rep}
     */
    public MethodHandle handle(Representation rep) {
        if (slotHandle != null) {
            return (MethodHandle)slotHandle.get(rep);
        } else {
            // Here we should return a handle to call from type.
            // We can prepare statically this once per member.
            throw new MissingFeature("Handle to invoke special method");
        }
    }

    /**
     * Lookup by method name, returning {@code null} if it is not a
     * recognised name for any slot.
     *
     * @param name of a (possible) special method
     * @return the SpecialMethod corresponding, or {@code null}
     */
    public static SpecialMethod forMethodName(String name) {
        return MethodNameLookup.table.get(name);
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
    public MethodType getType() { return signature.empty.type(); }

    /**
     * Get the default that fills the slot when it is "empty".
     *
     * @return empty method handle for this type of slot
     */
    public MethodHandle getEmpty() { return signature.empty; }

    /**
     * Get a handle to throw a {@link PyBaseException TypeError} with a
     * message conventional for the slot. This handle has the same
     * signature as the slot, and some data specific to the slot. This
     * is useful when the target of a call site may have to raise a type
     * error.
     *
     * @return throwing method handle for this type of slot
     */
    MethodHandle getOperandError() {
        // Not in the constructor so as not to provoke PyType
        if (operandError == null) {
            // Possibly racing, but that's harmless
            operandError = Util.operandErrorMH(this);
        }
        return operandError;
    }

    /**
     * Test whether this slot is non-empty in the given representation
     * object.
     *
     * @param rep to examine for this slot
     * @return true iff defined (non-empty)
     */
    public boolean isDefinedFor(Representation rep) {
        return slotHandle.get(rep) != signature.empty;
    }

    /**
     * Get the {@code MethodHandle} of this slot's "alternate" operation
     * from the given representation object. For a binary operation this
     * is the reflected operation.
     *
     * @param rep target representation object
     * @return current contents of the alternate slot in {@code t}
     * @throws NullPointerException if there is no alternate
     */
    MethodHandle getAltSlot(Representation rep)
            throws NullPointerException {
        return (MethodHandle)alt.slotHandle.get(rep);
    }

    /**
     * Set the {@code MethodHandle} of this slot's operation in the
     * given operations object.
     *
     * @param rep target type object
     * @param mh handle value to assign
     */
    void setHandle(Representation rep, MethodHandle mh) {
        if (mh == null || !mh.type().equals(getType()))
            throw slotTypeError(this, mh);
        slotHandle.set(rep, mh);
    }

    /**
     * An enumeration of the acceptable signatures for slots in a
     * {@link Representation} object. For each {@code MethodHandle} we
     * may place in a slot of the {@code Representation} object, we must
     * know in advance the acceptable signature (the
     * {@code MethodType}), and the slot when empty must contain a
     * handle with this signature to a method that will raise
     * {@link EmptyException}. Each {@code enum} constant here gives a
     * symbolic name to that {@code MethodType}, and provides the handle
     * used when a slot of that type is empty.
     * <p>
     * Names are equivalent to {@code typedef}s provided in CPython
     * {@code Include/object.h}, but are not exactly the same. We do not
     * need quite the same signatures as CPython: we do not return
     * integer status, for example. Also, C-specifics like
     * {@code Py_ssize_t} are echoed in the C-API names but not here.
     * <p>
     * The shorthand notation we use to describe a signature, for
     * example {@code (O,O[],S[])O}, essentially specifies a
     * {@code MethodType}, and may be decoded as follows.
     * <table>
     * <tr>
     * <th>Shorthand</th>
     * <th>Java class</th>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#B B}</td>
     * <td>{@code boolean.class}</td>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#I I}</td>
     * <td>{@code int.class}</td>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#O O}</td>
     * <td>{@code Object.class}</td>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#S S}</td>
     * <td>{@code String.class}</td>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#T T}</td>
     * <td>{@link PyType PyType.class}</td>
     * </tr>
     * <tr>
     * <td>{@link ClassShorthand#T V}</td>
     * <td>{@code void.class}</td>
     * </tr>
     * <tr>
     * <td>{@code []}</td>
     * <td>array of</td>
     * </tr>
     * <tr>
     * </tr>
     * <caption>Signature shorthands</caption>
     * </table>
     */
    public enum Signature {

        /*
         * The makeDescriptor overrides returning anonymous sub-classes
         * of PyWrapperDescr are fairly ugly. However, sub-classes seem
         * to be the right solution, and defining them here keeps
         * information together that belongs together.
         */

        /**
         * The signature {@code (O)O}, for example
         * {@link SpecialMethod#op_repr} or
         * {@link SpecialMethod#op_neg}.
         */
        // In CPython: unaryfunc
        UNARY(O, O),

        /**
         * The signature {@code (O,O)O}, for example
         * {@link SpecialMethod#op_add} or
         * {@link SpecialMethod#op_getitem}.
         */
        // In CPython: binaryfunc
        BINARY(O, O, O),
        /**
         * The signature {@code (O,O,O)O}, used for
         * {@link SpecialMethod#op_pow}.
         */
        // In CPython: ternaryfunc
        TERNARY(O, O, O, O),

        /**
         * The signature {@code (O,O[],S[])O}, used for
         * {@link SpecialMethod#op_call}. Note that in Jython, standard
         * calls are what CPython refers to as vector calls (although
         * they cannot use a stack slice as the array).
         */
        // Not in CPython
        CALL(O, O, OA, SA),

        /**
         * The signature {@code (O)B}, used for
         * {@link SpecialMethod#op_bool}.
         */
        // In CPython: inquiry
        PREDICATE(B, O),

        /**
         * The signature {@code (O,O)B}, used for
         * {@link SpecialMethod#op_contains}. It is not used for
         * comparisons, because they may return an arbitrary object
         * (e.g. in {@code numpy} array comparison).
         */
        BINARY_PREDICATE(B, O, O),

        /**
         * The signature {@code (O)I}, used for
         * {@link SpecialMethod#op_hash} and
         * {@link SpecialMethod#op_len}.
         */
        // In CPython: lenfunc
        LEN(I, O),

        /**
         * The signature {@code (O,O,O)V}, used for
         * {@link SpecialMethod#op_setitem} and
         * {@link SpecialMethod#op_set}. The arguments have quite
         * different meanings in each.
         */
        // In CPython: objobjargproc
        SETITEM(V, O, O, O),

        /**
         * The signature {@code (O,O)V}, used for
         * {@link SpecialMethod#op_delitem} and
         * {@link SpecialMethod#op_delete}. The arguments have quite
         * different meanings in each.
         */
        // Not in CPython
        DELITEM(V, O, O),

        /**
         * The signature {@code (O,S)O}, used for
         * {@link SpecialMethod#op_getattr}.
         */
        // In CPython: getattrofunc
        GETATTR(O, O, S),

        /**
         * The signature {@code (O,S,O)V}, used for
         * {@link SpecialMethod#op_setattr}.
         */
        // In CPython: setattrofunc
        SETATTR(V, O, S, O),

        /**
         * The signature {@code (O,S)V}, used for
         * {@link SpecialMethod#op_delattr}.
         */
        // Not in CPython
        DELATTR(V, O, S),

        /**
         * The signature {@code (O,O,T)O}, used for
         * {@link SpecialMethod#op_get}.
         */
        // In CPython: descrgetfunc
        DESCRGET(O, O, O, T),

        /**
         * The signature {@code (O,O[],S[])V}, used for
         * {@link SpecialMethod#op_init}. This is the same as
         * {@link #CALL} except with {@code void} return.
         */
        // In CPython: initproc
        INIT(V, O, OA, SA);

        /**
         * The signature was defined with this nominal method type.
         */
        public final MethodType type;
        /**
         * When empty, the slot should hold this handle. The method type
         * of this handle also tells us the method type by which the
         * slot must always be invoked, see
         * {@link SpecialMethod#getType()}.
         */
        public final MethodHandle empty;

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
            // em = λ : throw Util.EMPTY
            // (with correct nominal return type for slot)
            MethodHandle em = MethodHandles
                    .throwException(returnType, EmptyException.class)
                    .bindTo(Util.EMPTY);
            // empty = λ u v ... : throw Util.EMPTY
            // (with correct parameter types for slot)
            this.empty = MethodHandles.dropArguments(em, 0,
                    this.type.parameterArray());

            // Prepare the kind of lookup we should do
            Class<?> p0 = ptypes.length > 0 ? ptypes[0] : null;
            if (p0 != O) {
                throw new InterpreterError(
                        "Special methods must be instance methods");
            }
        }

        /**
         * Invoke the given method handle for the given target
         * {@code self}, having arranged the arguments as expected by a
         * slot. We create {@code enum} members of {@code Signature} to
         * handle different slot signatures, in which this method
         * accepts arguments in a generic way (from the interpreter,
         * say) and adapts them to the specific needs of a wrapped
         * method. The caller guarantees that the wrapped method has the
         * {@code Signature} to which the call is addressed.
         *
         * @param wrapped handle of the method to call
         * @param self target object of the method call
         * @param args of the method call
         * @param names of trailing arguments in {@code args}
         * @return result of the method call
         * @throws ArgumentError when the arguments ({@code args},
         *     {@code names}) are not correct for the {@code Signature}
         * @throws Throwable from the implementation of the special
         *     method
         */
        // Compare CPython wrap_* in typeobject.c
        // XXX Why not just call the handle in the method descriptor?
        /*
         * PyWrapperDescr calls this, but doesn't it already have the
         * information to hand?
         */
        public/* abstract */ Object callWrapped(MethodHandle wrapped,
                Object self, Object[] args, String[] names)
                throws ArgumentError, Throwable {
            //checkNoArgs(args, names);
            return wrapped.invokeExact(self);
        }
    }

    /**
     * Helper for {@link SpecialMethod#setHandle(PyType, MethodHandle)},
     * when a bad handle is presented.
     *
     * @param slot that the client attempted to set
     * @param mh offered value found unsuitable
     * @return exception with message filled in
     */
    private static InterpreterError slotTypeError(SpecialMethod slot,
            MethodHandle mh) {
        String fmt = "%s not of required type %s for slot %s";
        return new InterpreterError(fmt, mh, slot.getType(), slot);
    }

    /**
     * Helpers for {@link SpecialMethod} and {@link Signature} that can
     * be used in the constructors of {@code SpecialMethod} values,
     * before that class is properly initialised.
     */
    private static class Util {

        /*
         * This is a class separate from SpecialMethod to solve problems
         * with the order of static initialisation. The enum constants
         * have to come first, and their constructors are called as they
         * are encountered. This means that other constants in
         * SpecialMethod are not initialised by the time the
         * constructors need them.
         */
        private static final Lookup LOOKUP = MethodHandles.lookup();

        /**
         * Single re-used instance of
         * {@code SpecialMethod.EmptyException}
         */
        static final EmptyException EMPTY = new EmptyException();

        /**
         * Helper for {@link SpecialMethod} constructors at the point
         * they need a handle for their named cache field within a
         * {@code Representation} class. If the field is not found, then
         * this {@code SpecialMethod} member is not one with a cache and
         * we return {@code null}.
         *
         * @param sm current special method
         * @return a handle for the cache field or {@code null}
         */
        static VarHandle cacheVH(SpecialMethod sm) {
            Class<?> opsClass = Representation.class;
            try {
                // The field has the same name as the enum member
                return LOOKUP.findVarHandle(opsClass, sm.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Helper for {@link SpecialMethod} and thereby for call sites
         * providing a method handle that throws a Python exception when
         * invoked, with an appropriate message for the operation.
         * <p>
         * To be concrete, if the special method is a binary operation,
         * the returned handle may throw something like
         * {@code TypeError:
         * unsupported operand type(s) for -: 'str' and 'str'}.
         *
         * @param sm to mention in the error message
         * @return a handle that throws the exception
         */
        static MethodHandle operandErrorMH(SpecialMethod sm) {
            // The type of the method that creates the TypeError
            MethodType errorMT = sm.getType()
                    .insertParameterTypes(0, SpecialMethod.class)
                    .changeReturnType(PyBaseException.class);
            // Exception thrower with nominal return type of the slot
            // thrower = λ(e): throw e
            MethodHandle thrower = MethodHandles.throwException(
                    sm.getType().returnType(), PyBaseException.class);

            try {
                /*
                 * Look up a method f to create the exception, when
                 * applied the arguments v, w, ... (types matching the
                 * slot signature) prepended with this slot. We'll only
                 * call it if the handle is invoked.
                 */
                // error = λ(slot, v, w, ...): f(slot, v, w, ...)
                MethodHandle error;
                switch (sm.signature) {
                    case UNARY:
                        // Same name, although signature differs ...
                    case BINARY:
                        error = LOOKUP.findVirtual(SpecialMethod.class,
                                "operandError", errorMT);
                        break;
                    default:
                        // error = λ(slot): default(slot, v, w, ...)
                        error = LOOKUP.findStatic(Util.class,
                                "defaultOperandError", errorMT);
                        // error = λ(slot, v, w, ...): default(slot)
                        error = MethodHandles.dropArguments(error, 0,
                                sm.getType().parameterArray());
                }

                // A handle that creates and throws the exception
                // λ(v, w, ...): throw f(slot, v, w, ...)
                return MethodHandles.collectArguments(thrower, 0,
                        error.bindTo(sm));

            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InterpreterError(e,
                        "creating handle for type error", sm.name());
            }
        }

        /** Uninformative exception, mentioning the slot. */
        @SuppressWarnings("unused")  // reflected in operandError
        static PyBaseException defaultOperandError(SpecialMethod op) {
            return PyErr.format(PyExc.TypeError,
                    "bad operand type for %s", op.opName);
        }
    }

    /**
     * Lookup from special method name to {@code SpecialMethod}, to
     * support {@link SpecialMethod#forMethodName(String)}. We make this
     * a class of its own to obtain a thread-safe lazy initialisation of
     * the {@link MethodNameLookup#table} as a singleton, guaranteed to
     * fill its table after creation of the SpecialMethod enum.
     */
    private static class MethodNameLookup {
        /** Lookup from name to {@code SpecialMethod}. */
        static final Map<String, SpecialMethod> table;

        static {
            SpecialMethod[] methods = SpecialMethod.values();
            HashMap<String, SpecialMethod> t =
                    new HashMap<>(2 * methods.length);
            for (SpecialMethod s : methods) { t.put(s.methodName, s); }
            table = Collections.unmodifiableMap(t);
        }
    }

    /**
     * Create a {@link PyBaseException TypeError} for the named unary
     * operation, along the lines "bad operand type for OP: 'T'".
     *
     * @param v actual operand (only {@code PyType.of(v)} is used)
     * @return exception to throw
     */
    public PyBaseException operandError(Object v) {
        return PyErr.format(PyExc.TypeError,
                "bad operand type for %s: '%.200s'", opName,
                PyType.of(v).getName());
    }

    /**
     * Throw a {@link PyBaseException TypeError} for the named binary
     * operation, along the lines "unsupported operand type(s) for OP:
     * 'V' and 'W'".
     *
     * @param v left operand (only {@code PyType.of(v)} is used)
     * @param w right operand (only {@code PyType.of(w)} is used)
     * @return exception to throw
     */
    public PyBaseException operandError(Object v, Object w) {
        return PyErr.format(PyExc.TypeError, UNSUPPORTED_TYPES, opName,
                PyType.of(v).getName(), PyType.of(w).getName());
    }

    private static final String UNSUPPORTED_TYPES =
            "unsupported operand type(s) for %s: '%.100s' and '%.100s'";
}
