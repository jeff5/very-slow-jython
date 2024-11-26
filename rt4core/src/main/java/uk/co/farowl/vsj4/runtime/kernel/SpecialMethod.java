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

import uk.co.farowl.vsj4.runtime.Abstract;
import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.ClassShorthand;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyLong;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Shared;
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
// Compare CPython wrapperbase in descrobject.h
// aka slotdef in typeobject.c
public enum SpecialMethod {

    // __hash__(Signature.UNARY, r->r.op_hash, (r,h)->{r.op_hash=h;} ),

    /*
     * The order of the members is not significant, but we approximate
     * the slotdefs[] table for definiteness. We do not have quite the
     * same entries, and no duplicates. There may yet be special methods
     * here that need not be cached, and maybe properties it would be
     * useful to add.
     *
     * CPython's table begins with duplicates of __getattribute__,
     * __getattr__, __setattr__, and __delattr__ that do not come with a
     * documentation string. These are the versions that take C-string
     * names, rather than string objects. We omit these as ours take a
     * Java String, which is a str.
     */
    /**
     * Defines {@code __repr__} with signature {@link Signature#UNARY},
     * supporting built-in {@code repr()}.
     */
    op_repr(Signature.UNARY, "Return repr(self)."),
    /**
     * Defines {@code __hash__} with signature {@link Signature#LEN},
     * supporting object hashing and the built-in {@code hash()}.
     */
    op_hash(Signature.LEN, "Return hash(self)."),
    /**
     * Defines {@code __call__}, support for calling an object, with
     * signature {@link Signature#CALL}.
     */
    op_call(Signature.CALL,
            "($self, /, *args, **kwargs) Call self as a function."),
    /**
     * Defines {@code __str__} with signature {@link Signature#UNARY},
     * supporting built-in {@code str()}.
     */
    op_str(Signature.UNARY, "Return str(self)."),

    /**
     * Defines {@code __getattribute__} with signature
     * {@link Signature#GETATTR}, attribute get.
     */
    op_getattribute(Signature.GETATTR,
            "($self, name, /) Return getattr(self, name)."),
    /**
     * Defines {@code __getattr__} with signature
     * {@link Signature#GETATTR}, the fall-back attribute get.
     */
    op_getattr(Signature.GETATTR,
            "($self, name, /) Return getattr(self, name)."),
    /**
     * Defines {@code __setattr__} with signature
     * {@link Signature#SETATTR}, attribute set.
     */
    op_setattr(Signature.SETATTR,
            "($self, name, value, /) Implement setattr(self, name, value)."),
    /**
     * Defines {@code __delattr__} with signature
     * {@link Signature#DELATTR}, attribute deletion.
     */
    op_delattr(Signature.DELATTR,
            "($self, name, /) Implement delattr(self, name)."),

    /**
     * Defines {@code __lt__} with signature {@link Signature#BINARY},
     * the {@code <} operation.
     */
    op_lt(Signature.BINARY, "<"),
    /**
     * Defines {@code __le__} with signature {@link Signature#BINARY},
     * the {@code <=} operation.
     */
    op_le(Signature.BINARY, "<="),
    /**
     * Defines {@code __eq__} with signature {@link Signature#BINARY},
     * the {@code ==} operation.
     */
    op_eq(Signature.BINARY, "=="),
    /**
     * Defines {@code __ne__} with signature {@link Signature#BINARY},
     * the {@code !=} operation.
     */
    op_ne(Signature.BINARY, "!="),
    /**
     * Defines {@code __gt__} with signature {@link Signature#BINARY},
     * the {@code >} operation.
     */
    op_gt(Signature.BINARY, ">"),
    /**
     * Defines {@code __ge__} with signature {@link Signature#BINARY},
     * the {@code >=} operation.
     */
    op_ge(Signature.BINARY, ">="),

    /**
     * Defines {@code __iter__} with signature {@link Signature#UNARY},
     * get an iterator, supporting built-in {@code iter()}.
     */
    op_iter(Signature.UNARY, "Implement iter(self)."),
    /**
     * Defines {@code __next__} with signature {@link Signature#UNARY},
     * advance an iterator, supporting built-in {@code next()}.
     */
    op_next(Signature.UNARY, "Implement next(self)."),

    /**
     * Defines {@code __get__} with signature
     * {@link Signature#DESCRGET}, descriptor {@code __get__}.
     */
    op_get(Signature.DESCRGET,
            "($self, instance, owner=None, /) Return an attribute of instance, which is of type owner."),
    /**
     * Defines {@code __set__} with signature {@link Signature#SETITEM},
     * descriptor {@code __set__}.
     */
    op_set(Signature.SETITEM,
            "($self, instance, value, /) Set an attribute of instance to value."),
    /**
     * Defines {@code __delete__} with signature
     * {@link Signature#DELITEM}, descriptor {@code __delete__}.
     */
    op_delete(Signature.DELITEM,
            "($self, instance, /) Delete an attribute of instance."),

    /**
     * Defines {@code __init__} with signature {@link Signature#INIT},
     * object initialisation after {@code __new__}, which is not in this
     * {@code enum} as it is not an instance method.
     */
    op_init(Signature.INIT,
            "($self, /, *args, **kwargs) Initialize self."
                    + "  See help(type(self)) for accurate signature."),

    // __new__ is not enumerated here (not instance method)
    // __del__ is not in our implementation

    /**
     * Defines {@code __await__} with signature {@link Signature#UNARY}.
     */
    op_await(Signature.UNARY, // unexplored territory
            "Return an iterator to be used in await expression."),
    /**
     * Defines {@code __aiter__} with signature {@link Signature#UNARY}.
     */
    op_aiter(Signature.UNARY, // unexplored territory
            "Return an awaitable, that resolves in"
                    + " an asynchronous iterator."),

    /**
     * Defines {@code __anext__} with signature {@link Signature#UNARY}.
     */
    op_anext(Signature.UNARY, // unexplored territory
            "Return a value or raise StopAsyncIteration."),
    /**
     * Defines {@code __radd__} with signature {@link Signature#BINARY},
     * the reflected {@code +} operation.
     */
    op_radd(Signature.BINARY, "+"),
    /**
     * Defines {@code __add__} with signature {@link Signature#BINARY},
     * the {@code +} operation.
     */
    op_add(Signature.BINARY, "+", op_radd),
    /**
     * Defines {@code __rsub__} with signature {@link Signature#BINARY},
     * the reflected {@code -} operation.
     */
    op_rsub(Signature.BINARY, "-"),
    /**
     * Defines {@code __sub__} with signature {@link Signature#BINARY},
     * the {@code -} operation.
     */
    op_sub(Signature.BINARY, "-", op_rsub),
    /**
     * Defines {@code __rmul__} with signature {@link Signature#BINARY},
     * the reflected {@code *} operation.
     */
    op_rmul(Signature.BINARY, "*"),
    /**
     * Defines {@code __mul__} with signature {@link Signature#BINARY},
     * the {@code *} operation.
     */
    op_mul(Signature.BINARY, "*", op_rmul),
    /**
     * Defines {@code __rmod__} with signature {@link Signature#BINARY},
     * the reflected {@code %} operation.
     */
    op_rmod(Signature.BINARY, "%"),
    /**
     * Defines {@code __mod__} with signature {@link Signature#BINARY},
     * the {@code %} operation.
     */
    op_mod(Signature.BINARY, "%", op_rmod),
    /**
     * Defines {@code __rdivmod__} with signature
     * {@link Signature#BINARY}, the reflected {@code divmod} operation.
     */
    op_rdivmod(Signature.BINARY, "divmod()"),
    /**
     * Defines {@code __divmod__} with signature
     * {@link Signature#BINARY}, the {@code divmod} operation.
     */
    op_divmod(Signature.BINARY, "divmod()", op_rdivmod),
    /**
     * Defines {@code __rpow__} with signature {@link Signature#BINARY},
     * the reflected {@code pow} operation. (The signature is not not
     * {@link Signature#TERNARY} like {@link #op_pow} since only an
     * infix operation can be reflected).
     */
    op_rpow(Signature.TERNARY, // unexplored territory
            "($self, value, mod=None, /) Return pow(value, self, mod)."),
    /**
     * Defines {@code __pow__} with signature {@link Signature#TERNARY},
     * the {@code **} operation and built-in {@code pow()}.
     */
    op_pow(Signature.TERNARY, // unexplored territory
            "($self, value, mod=None, /) Return pow(self, value, mod).",
            op_rpow),

    /**
     * Defines {@code __neg__} with signature {@link Signature#UNARY},
     * the unary {@code -} operation.
     */
    op_neg(Signature.UNARY, "-"),
    /**
     * Defines {@code __pos__} with signature {@link Signature#UNARY},
     * the unary {@code +} operation.
     */
    op_pos(Signature.UNARY, "+"),
    /**
     * Defines {@code __abs__} with signature {@link Signature#UNARY},
     * supporting built-in {@code abs()}.
     */
    op_abs(Signature.UNARY, "abs(self)"),
    /**
     * Defines {@code __bool__} with signature
     * {@link Signature#PREDICATE}, conversion to a truth value.
     */
    op_bool(Signature.PREDICATE, "True if self else False"),
    /**
     * Defines {@code __invert__} with signature
     * {@link Signature#UNARY}, the unary {@code ~} operation.
     */
    op_invert(Signature.UNARY, "~"),

    /**
     * Defines {@code __rlshift__} with signature
     * {@link Signature#BINARY}, the reflected {@code <<} operation.
     */
    op_rlshift(Signature.BINARY, "<<"),
    /**
     * Defines {@code __lshift__} with signature
     * {@link Signature#BINARY}, the {@code <<} operation.
     */
    op_lshift(Signature.BINARY, "<<", op_rlshift),
    /**
     * Defines {@code __rrshift__} with signature
     * {@link Signature#BINARY}, the reflected {@code >>} operation.
     */
    op_rrshift(Signature.BINARY, ">>"),
    /**
     * Defines {@code __rshift__} with signature
     * {@link Signature#BINARY}, the {@code >>} operation.
     */
    op_rshift(Signature.BINARY, ">>", op_rrshift),

    /**
     * Defines {@code __rand__} with signature {@link Signature#BINARY},
     * the reflected {@code &} operation.
     */
    op_rand(Signature.BINARY, "&"),
    /**
     * Defines {@code __and__} with signature {@link Signature#BINARY},
     * the {@code &} operation.
     */
    op_and(Signature.BINARY, "&", op_rand),
    /**
     * Defines {@code __rxor__} with signature {@link Signature#BINARY},
     * the reflected {@code ^} operation.
     */
    op_rxor(Signature.BINARY, "^"),
    /**
     * Defines {@code __xor__} with signature {@link Signature#BINARY},
     * the {@code ^} operation.
     */
    op_xor(Signature.BINARY, "^", op_rxor),
    /**
     * Defines {@code __ror__} with signature {@link Signature#BINARY},
     * the reflected {@code |} operation.
     */
    op_ror(Signature.BINARY, "|"),
    /**
     * Defines {@code __or__} with signature {@link Signature#BINARY},
     * the {@code |} operation.
     */
    op_or(Signature.BINARY, "|", op_ror),

    /**
     * Defines {@code __int__} with signature {@link Signature#UNARY},
     * conversion to an integer value.
     */
    op_int(Signature.UNARY, "int(self)"),
    /**
     * Defines {@code __float__} with signature {@link Signature#UNARY},
     * conversion to a {@code float} value.
     */
    op_float(Signature.UNARY, "float(self)"),

    // in-place: unexplored territory

    /**
     * Defines {@code __iadd__} with signature {@link Signature#BINARY},
     * the {@code +=} operation.
     */
    op_iadd(Signature.BINARY, "+="),
    /**
     * Defines {@code __isub__} with signature {@link Signature#BINARY},
     * the {@code -=} operation.
     */
    op_isub(Signature.BINARY, "-="),
    /**
     * Defines {@code __imul__} with signature {@link Signature#BINARY},
     * the {@code *=} operation.
     */
    op_imul(Signature.BINARY, "*="),
    /**
     * Defines {@code __imod__} with signature {@link Signature#BINARY},
     * the {@code %=} operation.
     */
    op_imod(Signature.BINARY, "%="),
    /**
     * Defines {@code __iand__} with signature {@link Signature#BINARY},
     * the {@code &=} operation.
     */
    op_iand(Signature.BINARY, "&="),
    /**
     * Defines {@code __ixor__} with signature {@link Signature#BINARY},
     * the {@code ^=} operation.
     */
    op_ixor(Signature.BINARY, "^="),
    /**
     * Defines {@code __ior__} with signature {@link Signature#BINARY},
     * the {@code |=} operation.
     */
    op_ior(Signature.BINARY, "|="),

    /**
     * Defines {@code __rfloordiv__} with signature
     * {@link Signature#BINARY}, the reflected {@code //} operation.
     */
    op_rfloordiv(Signature.BINARY, "//"),
    /**
     * Defines {@code __floordiv__} with signature
     * {@link Signature#BINARY}, the {@code //} operation.
     */
    op_floordiv(Signature.BINARY, "//", op_rfloordiv),
    /**
     * Defines {@code __rtruediv__} with signature
     * {@link Signature#BINARY}, the reflected {@code /} operation.
     */
    op_rtruediv(Signature.BINARY, "/"),
    /**
     * Defines {@code __truediv__} with signature
     * {@link Signature#BINARY}, the {@code /} operation.
     */
    op_truediv(Signature.BINARY, "/", op_rtruediv),
    /**
     * Defines {@code __ifloordiv__} with signature
     * {@link Signature#BINARY}, the {@code //=} operation.
     */
    op_ifloordiv(Signature.BINARY, "//="),
    /**
     * Defines {@code __itruediv__} with signature
     * {@link Signature#BINARY}, the {@code /=} operation.
     */
    op_itruediv(Signature.BINARY, "/="),

    /**
     * Defines {@code __index__} with signature {@link Signature#UNARY},
     * implementing lossless conversion to a Python {@code int}.
     */
    op_index(Signature.UNARY,
            "Return self converted to an integer, if self is suitable "
                    + "for use as an index into a list."),

    /**
     * Defines {@code __rmatmul__} with signature
     * {@link Signature#BINARY}, the reflected {@code @} operation.
     */
    op_rmatmul(Signature.BINARY, "@"),
    /**
     * Defines {@code __matmul__} with signature
     * {@link Signature#BINARY}, the {@code @} (matrix multiply)
     * operation.
     */
    op_matmul(Signature.BINARY, "@", op_rmatmul),
    /**
     * Defines {@code __imatmul__} with signature
     * {@link Signature#BINARY}, the {@code @=} (matrix multiply in
     * place) operation.
     */
    op_imatmul(Signature.BINARY, "@="),

    /*
     * Note that CPython repeats for "mappings" the following "sequence"
     * slots, and slots for __add_ and __mul__, __iadd_ and __imul__,
     * but that we do not need to.
     */
    /**
     * Defines {@code __len__} with signature {@link Signature#LEN},
     * supporting built-in {@code len()}.
     */
    op_len(Signature.LEN, "len(self)"),
    /**
     * Defines {@code __getitem__} with signature
     * {@link Signature#BINARY}, get at index.
     */
    op_getitem(Signature.BINARY, "($self, key, /) Return self[key]."),
    /**
     * Defines {@code __setitem__} with signature
     * {@link Signature#SETITEM}, set at index.
     */
    op_setitem(Signature.SETITEM,
            "($self, key, value, /) Set self[key] to value."),
    /**
     * Defines {@code __delitem__} with signature
     * {@link Signature#DELITEM}, delete from index.
     */
    op_delitem(Signature.DELITEM, "($self, key, /) Delete self[key]."),
    /**
     * Defines {@code __contains__} with signature
     * {@link Signature#BINARY_PREDICATE}, implementing keyword
     * {@code in}.
     */
    op_contains(Signature.BINARY_PREDICATE,
            "($self, key, /) Return key in self.");

    /** Method signature to match when defining the special method. */
    public final Signature signature;
    /** Name of implementation method to bind e.g. {@code "__add__"}. */
    public final String methodName;
    /** Name to use in error messages, e.g. {@code "+"} */
    final String opName;
    /**
     * The {@code null}, except in a reversed op, where it designates
     * the forward op e.g. in {@code op_radd} it is {@code op_add}.
     */
    final SpecialMethod alt;

    /**
     * Reference to the field used to cache a handle to this method in a
     * {@link Representation} or {@code null} if not cached.
     */
    // Compare CPython wrapperbase.offset in descrobject.h
    final VarHandle cache;

    /**
     * The method handle that should be invoked when the implementation
     * method is not fixed for the representation, because it may be
     * changed at any time, or the single representation applies to
     * multiple types. We cannot then provide a stable direct handle,
     * and must look it up by name on the type of {@code self}.
     * <p>
     * The handle has the signature {@link #signature} and may add
     * behaviour, such as validating the return type, tailored to the
     * specific special method. This is a constant for the
     * {@code SpecialMethod}, whether cached or not.
     * <p>
     * This handle is needed when the the implementation of the special
     * method is in Python. (It will work, or raise the right error,
     * with any object found in the dictionary of a type.) It may
     * therefore be used to call the special methods of a {@link Shared
     * shared representation} where the clique of replaceable types may
     * disagree about the implementation method.
     *
     * @implNote These weasel words allow the possibility of an
     *     optimisation. All members of the clique share a common
     *     ancestor in Python. If all inherit the implementation of this
     *     special method from the common ancestor, the shared
     *     representation could cache a direct handle to that common
     *     implementation taken from the (index zero) representation of
     *     that ancestor. Any clique member that receives a divergent
     *     definition of the special method has to set the cache in the
     *     shared representation to this default.
     */
    // XXX Implement the optimisation (and merge the note).
    // Compare CPython wrapperbase.function in descrobject.h
    final MethodHandle generic;

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
     * @param alt alternate special method (e.g. "op_radd")
     */
    SpecialMethod(Signature signature, String doc, String methodName,
            SpecialMethod alt) {
        this.signature = signature;
        this.methodName = dunder(methodName);
        this.alt = alt;
        // If doc is short, assume it's a symbol. Fall back on name.
        this.opName = (doc != null && doc.length() <= 3) ? doc : name();
        // Make up the docstring from whatever shorthand we got.
        this.doc = docstring(doc);
        this.cache = Util.cacheVH(this);
        // FIXME Slot functions not correctly generated.
        this.generic = Util.slotMH(this);
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

    /**
     * Get the {@code MethodHandle} on the implementation of this
     * {@code SpecialMethod} for objects with the given
     * {@link Representation}. {@link #methodName} names an entry
     * (descriptor) in the dictionary of a type from which the handle
     * may be retrieved.
     * <ul>
     * <li>When the representation is also a {@link PyType}, the
     * representation contains the dictionary.</li>
     * <li>In the case of an adopted representation, we know the
     * adoptive type directly and the {@link Representation#getIndex()
     * index}.</li>
     * <li>In the case of a shared representation, there is no unique
     * type in which to look up a descriptor, so instead the handle will
     * contain a wrapper to get the type from {@code self} when invoked,
     * and then look up the name.</li>
     * </ul>
     *
     * @param rep target representation object
     * @return current contents of this cache in {@code rep}
     */
    public MethodHandle handle(Representation rep) {
        // FIXME: Consider thread safety of slots
        // FIXME Assumes method has cache (and no redirection).
        if (cache != null) {
            // The handle is cached on the Representation
            return (MethodHandle)cache.get(rep);
        } else {
            // Here we should get from type a handle to call.
            // We can prepare this statically once per member.
            throw new MissingFeature("Handle to invoke special method");
        }
    }

    /**
     * Test whether this slot is non-empty in the given representation
     * object.
     *
     * @param rep to examine for this slot
     * @return true iff defined (non-empty)
     */
    public boolean isDefinedFor(Representation rep) {
        // FIXME Assumes method has cache (and no redirection).
        return cache.get(rep) != signature.empty;
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
    public MethodHandle getAltSlot(Representation rep)
            throws NullPointerException {
        // FIXME Assumes method has cache (and no redirection).
        return (MethodHandle)alt.cache.get(rep);
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
     * Each of the methods called {@code slot(self, ...)} looks up this
     * special method by name in the type of its {@code self} argument,
     * and calls it with the given arguments. A handle on the variant
     * matching {@link #signature}, and bound to this special method, is
     * placed in the {@link #generic} member during construction.
     * <p>
     * We in-line a specialised variant of attribute access in which we
     * avoid, if we can, binding a method object to {@code self}.
     * Instead, we call the unbound method with the {@code self}
     * argument placed first.
     * <p>
     * <b>Comparison with CPython:</b> Our {@code slot()} methods are
     * equivalent to the {@code  slot_*()} functions found in CPython
     * {@code Objects/typeobject.c} that fill the type slots when a
     * pointer to a built-in implementation of the method is not
     * available. We use them the same way. (See private methods in
     * {@link AbstractPyType}.) Ours are simpler than CPython's because
     * we signal an empty slot by a lightweight {@link EmptyException},
     * rather than by {@code null}. We therefore do not need to
     * reproduce the logic in the Abstract API, where the presence of a
     * slot function fools it into thinking the special method is
     * defined.
     *
     * @param self first operand.
     * @param args other operands.
     * @param kwds naming the last {@code args} or {@code null}.
     * @return result of the call
     * @throws PyBaseException from the call
     * @throws Throwable from the call
     */
    // Compare CPython slot_* in typeobject.c
    Object slot(Object self, Object[] args, String[] kwds)
            throws Throwable {
        PyType type = PyType.of(self);
        Object meth = type.lookup(methodName);
        if (meth == null) { throw Util.EMPTY; }
        return callAsMethod(type, meth, self, args, kwds);
    }

    Object slot(Object self) throws Throwable {
        PyType type = PyType.of(self);
        Object meth = type.lookup(methodName);
        if (meth == null) { throw Util.EMPTY; }
        return callAsMethod(type, meth, self);
    }

    Object slot(Object self, Object w) throws Throwable {
        PyType type = PyType.of(self);
        Object meth = type.lookup(methodName);
        if (meth == null) { throw Util.EMPTY; }
        return callAsMethod(type, meth, self, w);
    }

    Object slot(Object self, Object w, Object m) throws Throwable {
        PyType type = PyType.of(self);
        Object meth = type.lookup(methodName);
        if (meth == null) { throw Util.EMPTY; }
        return callAsMethod(type, meth, self, w, m);
    }

    Object slot(Object self, Object obj, PyType t) throws Throwable {
        PyType type = PyType.of(self);
        Object meth = type.lookup(methodName);
        if (meth == null) { throw Util.EMPTY; }
        return callAsMethod(type, meth, self, obj, t);
    }

    // TODO Specialise callAsMethod up to self, a1, a2, a3 when working
    private static Object callAsMethod(PyType type, Object meth,
            Object self, Object... args) throws Throwable {
        return callAsMethod(type, meth, self, args, null);
    }

    /**
     * Call the object we found by lookup on the type of {@code self},
     * as part of invoking a one of the definitions of
     * {@link #slot(Object, Object[], String[]) slot(...)}. These are
     * the slot functions that fill the cache for a special method when
     * we cannot find a more direct answer (see
     * {@link SpecialMethod#generic}.
     *
     * @param type of {@code self}.
     * @param obj looked up on {@code type} may be a descriptor.
     * @param self first operand.
     * @param args other operands.
     * @param kwds naming the last {@code args} or {@code null}.
     * @return result of the call
     * @throws PyBaseException from the call
     * @throws Throwable from the call
     */
    // Compare CPython vectorcall_unbound in typeobject.c
    /*
     * Also lookup_method and lookup_maybe_method. Actually, none of
     * these is an equivalent. We do roughly the same work as CPython in
     * our slot* functions, just in a different arrangement, avoiding
     * the &unbound argument, which is awkward in Java..
     */
    private static Object callAsMethod(PyType type, Object obj,
            Object self, Object[] args, String[] kwds)
            throws PyBaseException, Throwable {
        // What kind of object did we find? (Could be anything.)
        Representation rep =
                SimpleType.getRegistry().get(obj.getClass());
        PyType objType = rep.pythonType(obj);

        if (objType.isMethodDescr()) {
            /*
             * meth is a method descriptor. Avoid construction of a
             * bound method, supplying self as the first argument in a
             * new argument array. Keywords remain valid as they align
             * to the end.
             */
            return callPrepend(obj, self, args, kwds);

        } else {
            /*
             * The retrieved dictionary is not a *method* descriptor,
             * but it might still be a descriptor that we have to bind
             * to self.
             */
            try {
                // Replace meth with result of descriptor binding.
                MethodHandle get = SpecialMethod.op_get.handle(rep);
                obj = get.invokeExact(obj, self, type);
            } catch (EmptyException e) {
                // Not a descriptor at all.
            }
            /*
             * meth is now the thing have to call. We do not pass self
             * here, as meth has either bound self, or decided to ignore
             * it (e.g. @staticmethod). It ought to be an instance of a
             * class defining __call__. Or it may be something we can't
             * actually call, in which case we'll find out next.
             */
            return Callables.call(obj, args, kwds);
        }
    }

    /**
     * Call a Python object when the first argument {@code self} is
     * provided "loose".
     *
     * @param meth a Python callable
     * @param self the first argument
     * @param args the other arguments
     * @param kwds keywords to go with the call
     * @return result of call to {@code meth}
     * @throws PyBaseException (TypeError) if not callable
     * @throws Throwable from anything else that goes wrong
     */
    private static Object callPrepend(Object meth, Object self,
            Object[] args, String[] kwds)
            throws PyBaseException, Throwable {
        Object[] a = new Object[args.length + 1];
        a[0] = self;
        System.arraycopy(args, 0, a, 1, args.length);
        return Callables.call(meth, args, kwds);
    }

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
     * Set the cache for this {@code SpecialMethod} in the
     * {@link Representation} to the given {@code MethodHandle}.
     * <p>
     * If this special method does not have a cache in
     * {@link Representation} objects, this is a no-op, and effectively
     * the handle is the generic one.
     *
     * @param rep target {@code Representation}
     * @param mh handle value to assign
     */
    void setCache(Representation rep, MethodHandle mh) {
        if (cache != null) {
            if (mh == null || !mh.type().equals(getType())) {
                throw slotTypeError(this, mh);
            }
            cache.set(rep, mh);
        }
    }

    /**
     * Set the cache for this {@code SpecialMethod} in the
     * {@link Representation} to the generic handle. The generic handle
     * will look up the implementation of the special method on the type
     * each time it is needed.
     * <p>
     * This is the appropriate choice where the implementation of the
     * special method may change without the opportunity to update the
     * cache in the representation.
     *
     *
     * @param rep target {@code Representation}
     * @param mh handle value to assign
     */
    void setGeneric(Representation rep) { setCache(rep, generic); }

    /**
     * Set the cache for this {@code SpecialMethod} in the
     * {@link Representation} to empty. The empty cache has the expected
     * {@code MethodType} but throws {@link EmptyException}.
     *
     * @param rep target {@code Representation}
     */
    void setEmpty(Representation rep) {
        setCache(rep, signature.empty);
    }

    @Override
    public java.lang.String toString() {
        // Programmer-friendly description
        return "SpecialMethod." + name() + " ( " + methodName
                + getType() + " ) [" + signature.name() + "]";
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
         *
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
         * calls approximate what CPython refers to as vector calls
         * (although they cannot use a stack slice as the array).
         */
        // Not in CPython (uses ternaryfunc for call)
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
        // In CPython: getattrofunc is (O,O)O
        // In CPython: getattrfunc is (O,S)O
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
    }

    /**
     * Helpers for {@link SpecialMethod} and {@link Signature} that can
     * be used in the constructors of {@code SpecialMethod} values,
     * before that class is properly initialised.
     */
    static final class Util extends Representation.Accessor {
        /*
         * This is a class separate from SpecialMethod to solve problems
         * with the order of static initialisation. The enum constants
         * have to come first, and their constructors are called as they
         * are encountered. This means that other constants in
         * SpecialMethod are not initialised by the time the
         * constructors need them.
         */

        /** Rights to look up methods locally. */
        private static final Lookup LOOKUP = MethodHandles.lookup();

        /**
         * Single re-used instance of
         * {@code SpecialMethod.EmptyException}
         */
        static final EmptyException EMPTY = new EmptyException();

        /**
         * Method handle on {@link PyLong#asInt(Object)}, which converts
         * an {@code Object} that is expected to be a Python {@code int}
         * to a Java primitive {@code int}. This may raise
         * {@code TypeError} or {@code OverflowError}.
         */
        static final MethodHandle asJavaInt;

        /**
         * Method handle on {@link Abstract#isTrue(Object)}, which
         * converts an {@code Object} to a Java primitive
         * {@code boolean}. This may raise {@code TypeError}.
         */
        static final MethodHandle asJavaBoolean;

        static {
            try {
                asJavaInt = LOOKUP.findStatic(PyLong.class, "asInt",
                        MethodType.methodType(I, O));
                asJavaBoolean = LOOKUP.findStatic(Abstract.class,
                        "isTrue", MethodType.methodType(B, O));
            } catch (NoSuchMethodException | IllegalAccessException e) {
                // Handle lookup fails somewhere
                throw new InterpreterError(e,
                        "Failed to initialise SpecialMethod.Util.");
            }
        }

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
            Class<?> repClass = Representation.class;
            try {
                // The field has the same name as the enum member
                return LOOKUP.findVarHandle(repClass, sm.name(),
                        MethodHandle.class);
            } catch (NoSuchFieldException | IllegalAccessException e) {
                return null;
            }
        }

        /**
         * Helper for {@link SpecialMethod} providing a method handle
         * that looks up the special method by name on the type of
         * {@code self}, and calls the implementation it finds.
         *
         * @param sm to lookup via the type of {@code self}
         * @return a handle that looks up and calls {@code sm}
         */
        static MethodHandle slotMH(SpecialMethod sm) {

            /*
             * There are several SpecialMethod.slot() methods. The one
             * we want has the same arguments (after "this") as the
             * special method itself, but returns Object (because the
             * implementing method does.)
             */
            MethodType mt = sm.signature.type;
            MethodType slotMT = mt.changeReturnType(O);
            // Primitive types may be cast to Object by Java's defaults
            if (mt.parameterCount() > 1) {
                if (mt.parameterType(1).equals(S)) {
                    // Cast a name to Object (GETATTR etc.)
                    slotMT = slotMT.changeParameterType(1, O);
                }
            }

            try {
                /*
                 * Find the right SpecialMethod.slot() to match the
                 * (input) arguments. We do not need any Python-specific
                 * conversions (unlike CPython). The match in
                 * findVirtual does not include the 'this' argument.
                 * Allow sm to have overridden slot.
                 */
                MethodHandle call = LOOKUP.findVirtual(sm.getClass(),
                        "slot", slotMT);
                MethodHandle f = call.bindTo(sm);
                /*
                 * Explicitly convert the return value by Python rules
                 * if it is boolean or int.
                 */
                Class<?> returnType = mt.returnType();
                if (returnType.equals(B)) {
                    f = MethodHandles.filterReturnValue(f,
                            asJavaBoolean);
                } else if (returnType.equals(I)) {
                    f = MethodHandles.filterReturnValue(f, asJavaInt);
                }
                return f.asType(mt);
            } catch (NoSuchMethodException | IllegalAccessException e) {
                throw new InterpreterError(e,
                        "creating wrapper to call %s", sm.methodName);
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
                        "creating TypeError handle for %s", sm.name());
            }
        }

        /** Uninformative exception, mentioning the slot. */
        @SuppressWarnings("unused")  // reflected in operandError
        static PyBaseException defaultOperandError(SpecialMethod op) {
            return PyErr.format(PyExc.TypeError,
                    "bad operand type for %s", op.opName);
        }
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
     * Use the length and content of the given string, backed up by name
     * and signature, to generate a standard documentation string
     * according to a few patterns. This is a treat for maintainers who
     * hate typing strings.
     *
     * @param doc basis of documentation string or {@code null}
     * @return generated documentation string
     */
    // Compare CPython *SLOT macros in typeobject.c
    private String docstring(String doc) {
        String sig, help;
        int rp;

        if (doc == null) {
            // Shouldn't be in this position
            sig = "";
            help = "?";

        } else if (doc.length() <= 3) {
            // doc provides only an operator: make the rest up

            switch (signature) {
                case UNARY:
                    sig = "/";  // ($self, /)
                    help = "unary " + doc + "self";
                    break;
                case BINARY:
                    sig = "value, /";  // ($self, value, /)
                    if (doc.endsWith("=")
                            && !"<= == != >=".contains(doc)) {
                        // In-place binary operation.
                        help = "Return self " + doc + " value.";
                    } else if (alt == null) {
                        // Binary L op R.
                        help = "Return self " + doc + " value.";
                    } else {
                        // Binary R op L
                        help = "Return value " + doc + " self.";
                    }
                    break;
                default:
                    assert false; // Can't use short doc here
                    sig = "?";
                    help = doc;
            }

        } else if (doc.startsWith("($self, ")
                && (rp = doc.indexOf(')')) > 0) {
            // long doc with explicit signature
            sig = doc.substring(8, rp);
            help = doc.substring(rp + 1).trim();

        } else {
            // long doc without explicit signature
            switch (signature) {
                case UNARY, LEN, PREDICATE:
                    sig = "/";  // ($self, /)
                    break;
                case BINARY:
                    sig = "value, /";  // ($self, value, /)
                    break;
                default:
                    assert false; // Must have explicit names
                    sig = "?";
            }
            help = doc;
        }

        return String.format("%s($self, %s)\n--\n\n%s", methodName, sig,
                help);
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

    /** Zero-length array of {@code Object}. */
    static final Object[] NO_ARGS = new Object[0];

    /**
     * Helper for {@link SpecialMethod#setCache(PyType, MethodHandle)},
     * when a bad handle is presented.
     *
     * @param sm that the client attempted to set
     * @param mh offered value found unsuitable
     * @return exception with message filled in
     */
    private static InterpreterError slotTypeError(SpecialMethod sm,
            MethodHandle mh) {
        String fmt = "%s not of required type %s for slot %s";
        return new InterpreterError(fmt, mh, sm.getType(), sm);
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

    /**
     * Create anAttributeError to throw.
     *
     * @param type that does not have this special method.
     * @return to throw
     */
    PyBaseException attributeError(PyType type) {
        return PyErr.format(PyExc.AttributeError,
                "'%s' object has no attribute %s", type.getName(),
                methodName);
    }

    private static final String UNSUPPORTED_TYPES =
            "unsupported operand type(s) for %s: '%.100s' and '%.100s'";
}
