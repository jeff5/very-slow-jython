// Copyright (c)2026 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.function.Consumer;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj4.core.PyCode.Layout;
// import uk.co.farowl.vsj4.core.PyCode.VariableTrait;

/**
 * Common elements of Python v3.11 frame layout objects.
 */
class Layout311 implements Layout {
    /** Count of {@code co_varnames} */
    final int nvarnames;
    /** Count of {@code co_cellvars} */
    final int ncellvars;
    /** Count of {@code co_freevars} */
    final int nfreevars;
    /**
     * Index of first cell (which may be a parameter). Cell variables do
     * not in general form a contiguous block in the frame.
     */
    final int cell0;
    /**
     * Index of first free variable. Free variables form a contiguous
     * block in the frame from this index.
     */
    final int free0;
    /** Names of all the variables in frame order. */
    protected final String[] localnames;
    /** Kinds of all the variables in frame order. */
    protected final byte[] kinds;

    /**
     * The number of positional parameters (including positional-only
     * parameters and those with default values). Corresponds to
     * {@code co_argcount} in the code object.
     */
    private final int argcount;
    /**
     * {@code co_posonlyargcount} the number of positional-only
     * parameters (including those with default values). Corresponds to
     * {@code co_posonlyargcount} in the code object.
     */
    private final int posonlyargcount;
    /**
     * {@code co_kwonlyargcount} The number of keyword-only parameters
     * (including those with default values). Corresponds to
     * {@code co_kwonlyargcount} in the code object.
     */
    private final int kwonlyargcount;
    /** Index of positional argument collector, or -1. */
    private final int positionalCollector;
    /** Index of keyword argument collector, or -1. */
    private final int keywordCollector;

    /**
     * Construct a {@code Layout} based on a representation used
     * internally by CPython that appears in the stream {@code marshal}
     * writes, e.g. in a {@code .pyc} file. We also expect to use this
     * for JVM code objects.
     *
     * @param localsplusnames tuple of all the names
     * @param localspluskinds bytes of kinds of variables
     * @param argcount {@code co_argcount} the number of positional
     *     parameters (including positional-only parameters and those
     *     with default values)
     * @param posonlyargcount {@code co_posonlyargcount} the number of
     *     positional-only parameters (including those with default
     *     values)
     * @param kwonlyargcount {@code co_kwonlyargcount} the number of
     *     keyword-only parameters (including those with default values)
     * @param flags {@code co_flags} a set of flags identifying various
     *     (boolean) traits of the code object
     */
    Layout311(
            // Mapping frame offsets to information about a variable
            Object localsplusnames, Object localspluskinds,
            // Laying out which variables are parameters
            int argcount, int posonlyargcount, int kwonlyargcount,
            // Used to detect *args and **kwargs collector parameters
            EnumSet<CodeFlag> flags) {

        // Check type and size of the names and kinds objects
        PyTuple nameTuple =
                PyCode.castTuple(localsplusnames, "localsplusnames");
        PyBytes kindBytes =
                PyCode.castBytes(localspluskinds, "localspluskinds");

        int n = nameTuple.size();
        if (kindBytes.size() != n) {
            throw PyErr.format(PyExc.ValueError, LENGTHS_UNEQUAL,
                    kindBytes.size(), n);
        }

        /*
         * Step through the localsplus* objects saving the name and kind
         * of each, and counting the different kinds as we go.
         */
        int nloc = 0, nfree = 0, ncell = 0, icell0 = -1;
        this.localnames = new String[n];
        this.kinds = new byte[n];

        for (int i = 0; i < n; i++) {

            String s = PyUnicode.asString(nameTuple.get(i),
                    o -> Abstract.typeError(NAME_TUPLES_STRING, o,
                            "localsplusnames"));
            byte kindByte = kindBytes.get(i).byteValue();

            if ((kindByte & CO_FAST_LOCAL) != 0) {
                if ((kindByte & CO_FAST_CELL) != 0) {
                    // Argument referenced by nested scope.
                    ncell += 1;
                    // Remember where this happens first.
                    if (icell0 < 0) { icell0 = i; }
                }
                nloc += 1;
            } else if ((kindByte & CO_FAST_CELL) != 0) {
                // Locally defined but referenced in nested scope.
                ncell += 1;
            } else if ((kindByte & CO_FAST_FREE) != 0) {
                // Supplied from a containing scope.
                nfree += 1;
            }
            localnames[i] = s;
            kinds[i] = kindByte;
        }

        // Cache the counts and cardinal points.
        this.nvarnames = nloc;
        this.ncellvars = ncell;
        this.nfreevars = nfree;
        // If icell0>=0 cell parameter seen, else first cell.
        this.cell0 = icell0 >= 0 ? icell0 : n - nfree - ncell;
        this.free0 = localnames.length - nfree;

        if (posonlyargcount < 0 || argcount < posonlyargcount
                || kwonlyargcount < 0) {
            throw PyErr.format(PyExc.ValueError,
                    "code: argument counts inconsistent");
        }

        this.argcount = argcount;
        this.posonlyargcount = posonlyargcount;
        this.kwonlyargcount = kwonlyargcount;

        int nargs = argcount + kwonlyargcount;
        this.positionalCollector =
                flags.contains(CodeFlag.VARARGS) ? nargs++ : -1;
        this.keywordCollector =
                flags.contains(CodeFlag.VARKEYWORDS) ? nargs++ : -1;
        if (n < nargs) {
            throw PyErr.format(PyExc.ValueError,
                    "code: fewer names than parameters");
        }
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder(100);
        b.append('(');
        for (int i = 0; i < localnames.length; i++) {
            boolean cell = isCellOrFree(i);
            if (cell) { b.append('['); }
            if (i == positionalCollector) { b.append('*'); }
            if (i == keywordCollector) { b.append("**"); }
            b.append(localnames[i]);
            if (cell) { b.append(']'); }
            if (i < localnames.length - 1) { b.append(","); }
        }
        b.append(')');
        return b.toString();
    }

    @Override
    public int argcount() { return argcount; }

    @Override
    public int posonlyargcount() { return posonlyargcount; }

    @Override
    public int kwonlyargcount() { return kwonlyargcount; }

    @Override
    public int positionalCollector() { return positionalCollector; }

    @Override
    public int keywordCollector() { return keywordCollector; }

    @Override
    public int nlocals() { return localnames.length; }

    @Override
    public int size() { return localnames.length; }

    @Override
    public String name(int index) { return localnames[index]; }

    @Override
    public boolean isLocal(int index) {
        return (kinds[index] & CO_FAST_LOCAL) != 0;
    }

    @Override
    public boolean isCell(int index) {
        return (kinds[index] & CO_FAST_CELL) != 0;
    }

    @Override
    public boolean isFree(int index) {
        return (kinds[index] & CO_FAST_FREE) != 0;
    }

    @Override
    public boolean isCellOrFree(int index) {
        return (kinds[index] & (CO_FAST_CELL | CO_FAST_FREE)) != 0;
    }

    @Override
    public Stream<String> localnames() {
        return Arrays.stream(localnames);
    }

    // TODO replace Stream with Iterable in Layout generally
    @Override
    public Stream<String> varnames() {
        Spliterator<String> s =
                spliterator(CO_FAST_LOCAL, nvarnames, 0);
        return StreamSupport.stream(s, false);
    }

    /**
     * @return names of parameters and non-cell variables.
     */
    public Iterable<String> varnamesIter() {
        return new Iterable<String>() {
            @Override
            public Iterator<String> iterator() {
                return getIterator(CO_FAST_LOCAL, 0);
            }
        };
    }

    @Override
    public Stream<String> cellvars() {
        Spliterator<String> s =
                spliterator(CO_FAST_CELL, ncellvars, cell0);
        return StreamSupport.stream(s, false);
    }

    @Override
    public Stream<String> freevars() {
        Spliterator<String> s = spliterator(CO_FAST_FREE, nfreevars,
                localnames.length - nfreevars);
        return StreamSupport.stream(s, false);
    }

    @Override
    public int nvarnames() { return nvarnames; }

    /** @return the length of {@code co_cellvars} */
    @Override
    public int ncellvars() { return ncellvars; }

    /** @return the length of {@code co_freevars} */
    @Override
    public int nfreevars() { return nfreevars; }

    /**
     * A {@code Spliterator} of local variable names of the kind
     * indicated in the mask. The caller must specify where to start
     * looking in the list and how many names there ought to be.
     *
     * @param mask single bit kind
     * @param count how many of that kind
     * @param start to start looking
     * @return a spliterator of the names
     */
    private Spliterator<String> spliterator(final int mask,
            final int count, int start) {
        return new Spliterator<String>() {
            private int i = start, remaining = count;

            @Override
            public boolean tryAdvance(Consumer<? super String> action) {
                if (remaining > 0) {
                    while ((kinds[i++] & mask) == 0) {} // nothing
                    action.accept(localnames[i - 1]);
                    remaining -= 1;
                    return true;
                } else
                    return false;
            }

            @Override
            public Spliterator<String> trySplit() { return null; }

            @Override
            public long estimateSize() { return count; }

            @Override
            public int characteristics() {
                return ORDERED | SIZED | IMMUTABLE;
            }
        };
    }

    /**
     * A {@code Iterator} of local variable names of the kind indicated
     * by (any of the bits in) the mask. The caller must specify where
     * to start looking in the list and how many names there ought to
     * be.
     *
     * @param mask single bit kind
     * @param start to start looking
     * @return an iterator of the names
     */
    private Iterator<String> getIterator(final int mask, int start) {
        return new Iterator<String>() {
            private int n = localnames.length;
            private int index = findNext(start);

            @Override
            public boolean hasNext() { return index < n; }

            int findNext(int start) {
                for (int i = start; i < n; i++) {
                    if ((kinds[i] & mask) != 0) { return i; }
                }
                return n;
            }

            @Override
            public String next() {
                int i = index;
                index = findNext(i + 1);
                return localnames[i];
            }
        };
    }

    // Plumbing -------------------------------------------------------

    private static final String NAME_TUPLES_STRING =
            "name tuple must contain only strings, not '%s' (in %s)";
    private static final String LENGTHS_UNEQUAL =
            "lengths unequal localspluskinds(%d) _localsplusnames(%d)";
    // See CPython frameobject.c, compile.c and pycore_code.h
    /** Bit indicating a frame variable is local */
    public static final int CO_FAST_LOCAL = 0x20,
            /** Bit indicating a frame variable is a cell local */
            CO_FAST_CELL = 0x40,
            /** Bit indicating a frame variable is free (non-local) */
            CO_FAST_FREE = 0x80;
}
