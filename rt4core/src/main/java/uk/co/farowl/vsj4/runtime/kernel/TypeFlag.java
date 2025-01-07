// Copyright (c)2024 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

/**
 * Enumeration of the features of a type, as used on the type object
 * itself. Some have equivalents that may appear in a type
 * specification, while others are computed automatically. They are used
 * internally to determine behaviours, including where a quick answer is
 * needed to frequent questions such as "is this a method descriptor".
 */
public enum TypeFlag {

    // Flags with a Feature equivalent -------------------------------
    // Paste from runtime.Feature enum and delete arguments.

    /**
     * The type allows sub-classing (is acceptable as a base in Python).
     */
    // Compare CPython Py_TPFLAGS_BASETYPE
    BASETYPE,

    /**
     * The type object is immutable: type attributes may not be set or
     * deleted after creation of the type.
     */
    // Compare CPython Py_TPFLAGS_IMMUTABLETYPE
    IMMUTABLE,

    /**
     * An instance of the type may become an instance of another type
     * (by {@code __class__} assignment), if this flag is set, provided
     * instances of the two types have a common representation in Java,
     * __slots__, and instances possess (or not) a __dict__ (equivalent
     * to CPython's "layout" constraints). This is separate from the
     * {@link #IMMUTABLE immutability of the type} itself.
     */
    // No equivalent in CPython
    REPLACEABLE,

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
    INSTANTIABLE,  // CPython equivalent has opposite (-ve) sense.

    /**
     * Instances of the type object are treated as sequences for pattern
     * matching.
     */
    // Compare CPython Py_TPFLAGS_SEQUENCE
    SEQUENCE,

    /**
     * Instances of the type object are treated as mappings for pattern
     * matching
     */
    // Compare CPython Py_TPFLAGS_MAPPING
    MAPPING,

    /**
     * This flag is used to give certain built-ins a pattern-matching
     * behaviour that allows a single positional sub-pattern to match
     * against the subject itself (rather than a mapped attribute).
     */
    // Compare CPython (undocumented) _Py_TPFLAGS_MATCH_SELF
    MATCH_SELF,

    /**
     * An instance of this type is a method descriptor, that is, it
     * supports an optimised call pattern where a {@code self} argument
     * may be supplied "loose" when calling the method, with the same
     * meaning as if it were first bound and the bound object called.
     */
    // Compare CPython Py_TPFLAGS_METHOD_DESCRIPTOR
    IS_METHOD_DESCR,

    // Not API -------------------------------------------------------

    /**
     * An object of this type is a descriptor (defines {@code __get__}).
     */
    // No equivalent in CPython
    IS_DESCR,
    /**
     * An instance of this type is a data descriptor (defines
     * {@code __get__} and at least one of {@code __set__} or
     * {@code __delete__}).
     */
    // No equivalent in CPython
    IS_DATA_DESCR,

    /**
     * The type is ready for use (for publication). Equivalently, it
     * fulfils its contract. The flag is necessary because type creation
     * can be a tangled process, with many types only partly defined.
     */
    // Compare CPython Py_TPFLAGS_READY
    /*
     * We need some equivalent to this CPython detail (and also the
     * READYING flag) to delay the publication of a type (its
     * Representations) until it is safe. I would prefer to do this by
     * keeping partial types in a separate container queue, maybe a
     * priority queue. Keep for now.
     */
    READY,

    /**
     * The type is being made ready for use (for publication), but so
     * far only meets a subset of its contract. The concept is necessary
     * because type creation has to happen in (at least) two stages.
     */
    // Compare CPython Py_TPFLAGS_READYING
    // Maybe not for Jython: see remark on READY flag.
    READYING,

    /**
     * The type object is mutable: type attributes can be set and
     * deleted after creation.
     */
    // Compare CPython Py_TPFLAGS_MUTABLE
    MUTABLE,

    /**
     * Type object has up-to-date type attribute cache. (CPython legacy
     * possibly not needed.
     */
    // Compare CPython Py_TPFLAGS_VALID_VERSION_TAG
    VALID_VERSION_TAG,

    /**
     * The type is abstract, in the sense of PEP 3119, and cannot be
     * instantiated. This is different from {@link #INSTANTIABLE}. It is
     * an implementation detail (in CPython), not API.
     */
    // Compare CPython Py_TPFLAGS_IS_ABSTRACT
    ABSTRACT,

    /** The type is a subclass of {@code int}. */
    // Compare CPython Py_TPFLAGS_LONG_SUBCLASS
    INT_SUBCLASS,
    /** The type is a subclass of {@code list}. */
    // Compare CPython Py_TPFLAGS_LIST_SUBCLASS
    LIST_SUBCLASS,
    /** The type is a subclass of {@code tuple}. */
    // Compare CPython Py_TPFLAGS_TUPLE_SUBCLASS
    TUPLE_SUBCLASS,
    /** The type is a subclass of {@code bytes}. */
    // Compare CPython Py_TPFLAGS_BYTES_SUBCLASS
    BYTES_SUBCLASS,
    /** The type is a subclass of {@code str}. */
    // Compare CPython Py_TPFLAGS_UNICODE_SUBCLASS
    STR_SUBCLASS,
    /** The type is a subclass of {@code dict}. */
    // Compare CPython Py_TPFLAGS_DICT_SUBCLASS
    DICT_SUBCLASS,
    /** The type is a subclass of {@code BaseException}. */
    // Compare CPython Py_TPFLAGS_BASE_EXC_SUBCLASS
    EXCEPTION_SUBCLASS,
    /** The type is a subclass of . */
    // Compare CPython Py_TPFLAGS_TYPE_SUBCLASS
    TYPE_SUBCLASS;
}
