// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.runtime.kernel;

import java.util.EnumSet;

import uk.co.farowl.vsj4.runtime.TypeFlag;

/**
 * Enumeration of the implementation-private features of a type. These
 * are derived automatically during type construction and update. Unlike
 * {@link TypeFlag}, they are not API. They are derived and used
 * internally to determine run-time behaviours, including where a quick
 * answer is needed to frequent questions such as "is this a sequence",
 * and for synchronising access to Python features of a type.
 */
/*
 * A few of these values may not be in use anywhere. We reproduce the
 * CPython flags here or in TypeFlag, until we find we definitely don't
 * need them, and we add some of our own.
 */
public enum KernelTypeFlag {

    /**
     * This flag is used to give certain built-ins a pattern-matching
     * behaviour that allows a single positional sub-pattern to match
     * against the subject itself (rather than a mapped attribute).
     */
    // Compare CPython (undocumented) _Py_TPFLAGS_MATCH_SELF
    MATCH_SELF,

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
     * priority queue. It may be that this can only be achieved for the
     * Java-ready stage and the READY flag or the READYING flag is for
     * locking Python operations until a type is Python-ready. Keep for
     * now.
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
     * Type object has up-to-date type attribute cache. (CPython legacy
     * possibly not needed. Possibly same as {@link #READYING}).
     */
    // Compare CPython Py_TPFLAGS_VALID_VERSION_TAG
    // Maybe not needed for Jython.
    VALID_VERSION_TAG,

    /**
     * The type is abstract, in the sense of PEP 3119, and cannot be
     * instantiated. This is different from
     * {@link TypeFlag#INSTANTIABLE}. It is an implementation detail (in
     * CPython), not API.
     */
    // Compare CPython Py_TPFLAGS_IS_ABSTRACT
    // Maybe not needed for Jython.
    ABSTRACT,

    /**
     * The type defines {@code __get__}, which makes it a sequence (if
     * it is not a dictionary). This is the basis of a quick test. It is
     * necessary because we do not have type slots in the same way as
     * CPython.
     */
    // No equivalent in CPython
    HAS_GETITEM,
    /**
     * The type defines {@code __iter__}. This is the basis of a quick
     * test. It is necessary because we do not have type slots in the
     * same way as CPython.
     */
    // No equivalent in CPython
    HAS_ITER,
    /**
     * The type defines {@code __next__}. This is the basis of a quick
     * test. It is necessary because we do not have type slots in the
     * same way as CPython.
     */
    // No equivalent in CPython
    HAS_NEXT,
    /**
     * The type defines {@code __index__}. This is the basis of a quick
     * test. It is necessary because we do not have type slots in the
     * same way as CPython.
     */
    // No equivalent in CPython
    HAS_INDEX,
    /**
     * The type defines {@code __get__}, which makes it a descriptor.
     * This is the basis of a quick test. It is necessary because we do
     * not have type slots in the same way as CPython.
     */
    // No equivalent in CPython
    HAS_GET,
    /**
     * The type defines {@code __set__}, which makes it a data
     * descriptor. It should also define {@code __get__}. This is the
     * basis of a quick test. It is necessary because we do not have
     * type slots in the same way as CPython.
     */
    // No equivalent in CPython
    HAS_SET,
    /**
     * The type defines {@code __delete__}, which makes it a data
     * descriptor (for an optional attribute). It should also define
     * {@code __get__}. This is the basis of a quick test. It is
     * necessary because we do not have type slots in the same way as
     * CPython.
     */
    // No equivalent in CPython
    HAS_DELETE;

    /**
     * {@code KernelTypeFlag}s inherited from the base type when
     * constructing a new type.
     */
    public static final EnumSet<KernelTypeFlag> HERITABLE =
            EnumSet.of(MATCH_SELF, HAS_GETITEM, HAS_ITER, HAS_NEXT,
                    HAS_INDEX, HAS_GET, HAS_SET, HAS_DELETE);
}
