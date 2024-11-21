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
import java.util.function.Function;

import uk.co.farowl.vsj4.runtime.Callables;
import uk.co.farowl.vsj4.runtime.ClassShorthand;
import uk.co.farowl.vsj4.runtime.PyAttributeError;
import uk.co.farowl.vsj4.runtime.PyBaseException;
import uk.co.farowl.vsj4.runtime.PyErr;
import uk.co.farowl.vsj4.runtime.PyExc;
import uk.co.farowl.vsj4.runtime.PyType;
import uk.co.farowl.vsj4.runtime.kernel.Representation.Adopted;
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
     * conversion to an index value.
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

    /** Method signature to match when filling this slot. */
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
     * The method handle that should be returned by
     * {@link #handle(Representation)} when the implementation method
     * must be found by looking up a name on the type of {@code self},
     * and the call made as if from Python. Special methods exposed from
     * Java are able to provide their own direct handle. The handle has
     * the signature {@link #signature} and may add behaviour tailored
     * to the specific method. This is a constant for the
     * {@code SpecialMethod}, whether cached or not.
     * <p>
     * This handle is needed when the the implementation of the special
     * method is in Python. (It will work, or raise the right error,
     * with any object found in the dictionary of a type.) It may
     * therefore be used to call the special methods of a {@link Shared
     * shared representation} where the clique of replaceable types may
     * disagree about the implementation method. It embeds a lookup on
     * the Python type of {@code self}.
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
    final MethodHandle slot;

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
        this.opName = (doc != null && doc.length() <= 3) ? doc : name();
        // Make up the docstring from whatever shorthand we got.
        this.doc = docstring(doc);
        this.cache = Util.cacheVH(this);
        // FIXME Slot functions not correctly generated.
        this.slot = null; // Util.slotMH(this);
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
     * @return current contents of this slot in {@code rep}
     */
    public MethodHandle handle(Representation rep) {
        // FIXME: Consider thread safety of slots
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
     * Get (or create) a handle corresponding to this special method and
     * the given representation. This handle could be placed into the
     * corresponding cache field on on the {@code Representation} if the
     * {@code SpecialMethod} is a caching type. (This method does not
     * pay attention to whether the SpecialMethod *is* a caching type.
     * In particular it does not get its answer from that cache.)
     * <p>
     * The method uses these trategies according to the circumstances:
     * <ul>
     * <li>When the representation is also a {@link PyType}, we look up
     * {@link #methodName} its dictionary to get the handle (at index
     * zero).</li>
     * <li>When the representation is an adopted one, we look up
     * {@link #methodName} the dictionary of the associated type to get
     * the handle at the relevant {@link Representation#getIndex()
     * index}.</li>
     * <li>When the representation is sharable amongst types, there is
     * no unique type in which to look up a descriptor. Instead we
     * provide a handle that, when invoked, will consult {@code self}
     * for its type, look up {@link #methodName} in its dictionary, and
     * call whatever it finds. This handle is a constant for the
     * {@code SpecialMethod}.</li>
     * </ul>
     *
     * @param rep for which the handle is intended
     * @return the handle for invoking
     */
    private MethodHandle getUncashedHandle(Representation rep) {
        // FIXME Temporarily doing everything the slow way
        if (rep instanceof SimpleType type) {
            return slot;

        } else if (rep instanceof Adopted adopted) {
            PyType type = adopted.type;
            return slot;

        } else if (rep instanceof Shared shared) {
            return slot;
        } else {
            /*
             * In the circumstances where this is called, this ought to
             * exhaust the possibilities.
             */
            throw new InterpreterError(
                    "unexpected representation type %s", rep);
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
        return (MethodHandle)alt.cache.get(rep);
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
        if (cache != null) {
            if (mh == null) {
                if (mh.type().equals(getType())) {
                    cache.set(rep, mh);
                } else {
                    throw slotTypeError(this, mh);
                }
            }
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
     * Set the {@code MethodHandle} of this slot's operation in the
     * given operations object.
     *
     * @param rep target type object
     * @param mh handle value to assign
     */
    void setHandle(Representation rep, MethodHandle mh) {
        if (mh == null || !mh.type().equals(getType()))
            throw slotTypeError(this, mh);
        cache.set(rep, mh);
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

    Object unarySlot(Object self) throws PyBaseException, Throwable {
        /*
         * We in-line a specialised variant of attribute access in which
         * we avoid, if we can, binding a method object to self, and
         * call it instead with the self argument at args[0].
         */
        // Compare CPython lookup_method and lookup_maybe_method
        PyType type = PyType.of(self);
        Object r = type.lookup(methodName);

        if (r == null) {
            // XXX Provide way to raise AttributeError with just args
            throw PyErr.format(PyExc.AttributeError,
                    "'%s' object has no attribute %s", type.getName(),
                    methodName);
        }

        return callAsMethod(type, r, NO_ARGS);
    }

    /**
     * Call this special method with positional arguments provided in an
     * array, with {@code self} at {@code args[0]}. Each
     * {@code SpecialMethod} contains a handle on this method bound to
     * itself. It uses this as the default slot contents when the
     * implementation can only be found by looking up a name on the
     * type.
     *
     * @return result of call
     * @throws Throwable
     * @throws PyAttributeError
     */
    // Compare CPython vectorcall_method in typeobject.c
    Object callByLookup(Object[] args)
            throws PyBaseException, Throwable {
        /*
         * We in-line a specialised variant of attribute access in which
         * we avoid, if we can, binding a method object to self, and
         * call it instead with the self argument at args[0].
         */
        // Compare CPython lookup_method and lookup_maybe_method
        Object self = args[0];
        PyType type = PyType.of(self);
        Object r = type.lookup(methodName);

        if (r == null) {
            // XXX Provide way to raise AttributeError with just args
            throw PyErr.format(PyExc.AttributeError,
                    "'%s' object has no attribute %s", type.getName(),
                    methodName);
        }

        Representation rep = Util.registry.get(r.getClass());

        if (rep.isMethodDescr()) {
            /*
             * The retrieved r is a method descriptor. This means we can
             * avoid construction of a bound method, supplying self as
             * the first argument in the array (where it is already).
             */
            // Compare CPython vectorcall_unbound when unbound=true
            return Callables.call(r, args, null);

        } else {
            /*
             * The retrieved r is not a *method* descriptor, but it
             * might still be a descriptor.
             */
            MethodHandle get = SpecialMethod.op_get.handle(rep);
            try {
                // Replace r with the result of descriptor binding.
                r = get.invokeExact(r, self, type);
            } catch (EmptyException e) {
                // No, it's not a descriptor. r is the value already.
            }
            /*
             * r is now a method (well, an object, anyway) that has
             * either bound self or decided to ignore it. It may be a
             * static method or an instance of a class defining __call__
             * but not __get__. Or it may be something we can't call at
             * all, in which case we'll find that out now.
             */
            // Compare CPython vectorcall_unbound when unbound=false
            return Callables.vectorcall(r, args, 1, args.length - 1);
        }
    }

    Object callByLookup2(Object[] args)
            throws PyBaseException, Throwable {
        /*
         * We in-line a specialised variant of attribute access in which
         * we avoid, if we can, binding a method object to self, and
         * call it instead with the self argument at args[0].
         */
        // Compare CPython lookup_method and lookup_maybe_method
        Object self = args[0];
        PyType type = PyType.of(self);
        Object r = type.lookup(methodName);

        if (r == null) {
            // XXX Provide way to raise AttributeError with just args
            throw PyErr.format(PyExc.AttributeError,
                    "'%s' object has no attribute %s", type.getName(),
                    methodName);
        }

        return callAsMethod(type, r, args);
    }

    private static Object callAsMethod(PyType type, Object entry,
            Object[] args) throws Throwable {
        Representation rep = Util.registry.get(entry.getClass());

        if (rep.isMethodDescr()) {
            /*
             * The retrieved r is a method descriptor. This means we can
             * avoid construction of a bound method, supplying self as
             * the first argument in the array (where it is already).
             */
            // Compare CPython vectorcall_unbound when unbound=true
            return Callables.call(entry, args, null);

        } else {
            /*
             * The retrieved r is not a *method* descriptor, but it
             * might still be a descriptor.
             */
            MethodHandle get = SpecialMethod.op_get.handle(rep);
            try {
                // Replace r with the result of descriptor binding.
                entry = get.invokeExact(entry, args[0], type);
            } catch (EmptyException e) {
                // No, it's not a descriptor. r is the value already.
            }
            /*
             * r is now a method (well, an object, anyway) that has
             * either bound self or decided to ignore it. It may be a
             * static method or an instance of a class defining __call__
             * but not __get__. Or it may be something we can't call at
             * all, in which case we'll find that out now.
             */
            // Compare CPython vectorcall_unbound when unbound=false
            return Callables.vectorcall(entry, args, 1,
                    args.length - 1);
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

        /**
         * The type registry for this instance of the run time system,
         * which was statically created by PyType.
         */
        static final TypeRegistry registry = SimpleType.getRegistry();

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
         * Helper for {@link SpecialMethod} providing a method handle
         * that looks up the special method by name on the type of
         * {@code self}, and calls the implementation it finds.
         *
         * @param sm to lookup via the type of {@code self}
         * @return a handle that looks up and calls {@code sm}
         */
        static MethodHandle slotMH(SpecialMethod sm) {

            // The type of the method that makes the call
            MethodType callMT = MethodType.methodType(O, OA);
            MethodType slotMT = sm.signature.type;
            int n = slotMT.parameterCount();

            try {
                MethodHandle call = LOOKUP.findVirtual(
                        SpecialMethod.class, "callByLookup", callMT);
                MethodHandle f = call.bindTo(sm).asCollector(OA, n);
                return f.asType(slotMT);

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
