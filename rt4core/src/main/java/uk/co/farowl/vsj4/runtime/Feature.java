package uk.co.farowl.vsj4.runtime;

import uk.co.farowl.vsj4.runtime.kernel.TypeFlag;

/**
 * Enumeration of the features of a type that may be specified in a
 * {@link TypeSpec}.
 */
public enum Feature {

    /**
     * The type allows sub-classing (is acceptable as a base in Python).
     */
    // Compare CPython Py_TPFLAGS_BASETYPE
    BASETYPE(TypeFlag.BASETYPE),

    /**
     * The type object is immutable: type attributes may not be set or
     * deleted after creation of the type.
     */
    // Compare CPython Py_TPFLAGS_IMMUTABLETYPE
    IMMUTABLE(TypeFlag.IMMUTABLE),

    /**
     * An instance of the type may become an instance of another type
     * (by {@code __class__} assignment), if this flag is set, provided
     * instances of the two types have a common representation in Java,
     * __slots__, and instances possess (or not) a __dict__ (equivalent
     * to CPython's "layout" constraints). This is separate from the
     * {@link #IMMUTABLE immutability of the type} itself.
     */
    // No equivalent in CPython
    REPLACEABLE(TypeFlag.REPLACEABLE),

    /**
     * This type allows the creation of new instances (by
     * {@code __call__}, say). This flag is set by default. A type for
     * which this flag is <b>not set</b> should <b>not define</b> a
     * {@code __new__} method (and it will not inherit one). This is
     * commonly done for types such as iterators that we want to expose
     * as Python objects but cannot validly be created "loose" by the
     * user.
     */
    // Compare CPython Py_TPFLAGS_DISALLOW_INSTANTIATION
    INSTANTIABLE(TypeFlag.INSTANTIABLE),

    /**
     * Instances of the type object are treated as sequences for pattern
     * matching.
     */
    // Compare CPython Py_TPFLAGS_SEQUENCE
    SEQUENCE(TypeFlag.SEQUENCE),
    /**
     * Instances of the type object are treated as mappings for pattern
     * matching
     */
    // Compare CPython Py_TPFLAGS_MAPPING
    MAPPING(TypeFlag.MAPPING),

    /**
     * This flag is used to give certain built-ins a pattern-matching
     * behaviour that allows a single positional sub-pattern to match
     * against the subject itself (rather than a mapped attribute on
     * it).
     */
    // Compare CPython _Py_TPFLAGS_MATCH_SELF
    MATCH_SELF(TypeFlag.MATCH_SELF),

    /**
     * An instance of this type is a method descriptor, that is, it
     * supports an optimised call pattern where a {@code self} argument
     * may be supplied "loose" when calling the method, with the same
     * meaning as if it were first bound and the bound object called.
     * <p>
     * This is equivalent to setting the flag
     * {@code Py_TPFLAGS_METHOD_DESCRIPTOR} described in <a
     * href=https://peps.python.org/pep-0590/#descriptor-behavior>
     * PEP-590</a>. If {@code isMethodDescr()} returns {@code true} for
     * {@code type(func)}, then:
     * <ul>
     * <li>{@code func.__get__(obj, cls)(*args, **kwds)} (with
     * {@code {@code obj}} not None) must be equivalent to
     * {@code func(obj, *args, **kwds)}.</li>
     * <li>{@code func.__get__(None, cls)(*args, **kwds)} must be
     * equivalent to {@code func(*args, **kwds)}.</li>
     * </ul>
     */
    METHOD_DESCR(TypeFlag.IS_METHOD_DESCR);

    /** Navigate from feature to corresponding type flag. */
    public final TypeFlag flag;

    private Feature(TypeFlag flag) { this.flag = flag; }
}
