// Copyright (c)2021 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;
import java.util.ListIterator;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;
import uk.co.farowl.vsj3.evo1.PySequence.Delegate;
import uk.co.farowl.vsj3.evo1.PySlice.Indices;
import uk.co.farowl.vsj3.evo1.base.InterpreterError;

/**
 * The Python {@code str} object is implemented by both
 * {@code PyUnicode} and Java {@code String}. All operations will
 * produce the same result for Python, whichever representation is used.
 * Both types are treated as an array of code points in Python.
 * <p>
 * Most strings used as names (keys) and text are quite satisfactorily
 * represented by Java {@code String}. Java {@code String}s are compact,
 * but where they contain non-BMP characters, these are represented by a
 * pair of code units. That makes certain operations (such as indexing
 * or slicing) relatively expensive compared to Java. Accessing the code
 * points of a {@code String} sequentially is still cheap.
 * <p>
 * By contrast, a {@code PyUnicode} is time-efficient, but each
 * character occupies one {@code int}.
 */
public class PyUnicode implements CraftedPyObject, PyDict.Key {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", MethodHandles.lookup())
                    .methods(PyUnicodeMethods.class)
                    .adopt(String.class));

    /**
     * The actual Python type of this {@code PyUnicode}.
     */
    protected PyType type;

    /**
     * The implementation holds a Java {@code int} array of code points.
     */
    private final int[] value;

    /**
     * Helper to implement {@code __getitem__} and other index-related
     * operations.
     */
    private UnicodeDelegate delegate = new UnicodeDelegate();

    /**
     * Cached hash of the {@code str}, lazily computed in
     * {@link #hashCode()}. Zero if unknown, and nearly always unknown
     * if zero.
     */
    private int hash;

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given array of code points, with the option to
     * re-use that array as the implementation. If the actual array is
     * is re-used the caller must give up ownership and never modify it
     * after the call. See {@link #fromCodePoint(int)} for a correct
     * use.
     *
     * @param type actual type the instance should have
     * @param iPromiseNotToModify if {@code true}, the array becomes the
     *     implementation array, otherwise the constructor takes a copy.
     * @param codePoints the array of code points
     */
    private PyUnicode(PyType type, boolean iPromiseNotToModify,
            int[] codePoints) {
        this.type = type;
        if (iPromiseNotToModify)
            this.value = codePoints;
        else
            this.value = Arrays.copyOf(codePoints, codePoints.length);
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given array of code points. The constructor
     * takes a copy.
     *
     * @param type actual type the instance should have
     * @param codePoints the array of code points
     */
    protected PyUnicode(PyType type, int[] codePoints) {
        this(type, false, codePoints);
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from the given code points. The constructor takes a
     * copy.
     *
     * @param codePoints the array of code points
     */
    protected PyUnicode(int... codePoints) {
        this(TYPE, false, codePoints);
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given Java {@code String}. The constructor
     * interprets surrogate pairs as defining one code point. Lone
     * surrogates are preserved (e.g. for byte smuggling).
     *
     * @param type actual type the instance should have
     * @param value to have
     */
    protected PyUnicode(PyType type, String value) {
        this(TYPE, value.codePoints().toArray());
    }

    // Factory methods ------------------------------------------------
    // These may return a Java String or a PyUnicode

    /**
     * Return a Python {@code str} representing the single character
     * with the given code point. The return may be a Java
     * {@code String} (for BMP code points) or a {@code PyUnicode}.
     *
     * @param cp to code point convert
     * @return a Python {@code str}
     */
    public static Object fromCodePoint(int cp) {
        // We really need to know how the string will be used :(
        if (cp < Character.MIN_SUPPLEMENTARY_CODE_POINT)
            return String.valueOf((char)cp);
        else
            return new PyUnicode(TYPE, true, new int[] {cp});
    }

    /**
     * Return a Python {@code str} representing the same sequence of
     * characters as the given Java {@code String}. The result is not
     * necessarily a {@code PyUnicode}, unless the argument contains
     * non-BMP code points.
     *
     * @param cp to code point convert
     * @return a Python {@code str}
     */
    public static Object fromJavaString(String s) {
        if (isBMP(s))
            return s;
        else
            return new PyUnicode(TYPE, true, s.codePoints().toArray());
    }

    @Override
    public PyType getType() { return type; }

    // Special methods ------------------------------------------------

    @SuppressWarnings("unused")
    private Object __str__() { return this; }

    @SuppressWarnings("unused")
    private static Object __str__(String self) { return self; }

    private static Object __repr__(String self) {
        // Ok, it should be more complicated but I'm in a hurry.
        return "'" + self + "'";
    }

    @SuppressWarnings("unused")
    private Object __repr__() {
        StringBuilder b = new StringBuilder();
        for (int c : delegate) { b.appendCodePoint(c); }
        return __repr__(b.toString());
    }

    @SuppressWarnings("unused")
    private int __len__() { return value.length; }

    @SuppressWarnings("unused")
    private static int __len__(String self) {
        return self.codePointCount(0, self.length());
    }

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @SuppressWarnings("unused")
    private int __hash__() {
        // Reproduce on value the hash defined for java.lang.String
        if (hash == 0 && value.length > 0) {
            int h = 0;
            for (int c : value) {
                if (Character.isBmpCodePoint(c)) {
                    // c is represented by itself in a String
                    h = h * 31 + c;
                } else {
                    // c would be represented in a Java String by:
                    int hi = (c >>> 10) + HIGH_SURROGATE_OFFSET;
                    int lo = (c & 0x3ff) + Character.MIN_LOW_SURROGATE;
                    h = (h * 31 + hi) * 31 + lo;
                }
            }
            hash = h;
        }
        return hash;
    }

    @SuppressWarnings("unused")
    private static int __hash__(String self) { return self.hashCode(); }

    @SuppressWarnings("unused")
    private Object __getitem__(Object item) throws Throwable {
        return delegate.__getitem__(item);
    }

    @SuppressWarnings("unused")
    private static Object __getitem__(String self, Object item)
            throws Throwable {
        StringAdapter delegate = adapt(self);
        return delegate.__getitem__(item);
    }

    @SuppressWarnings("unused")
    private Object __add__(Object w) throws Throwable {
        return delegate.__add__(w);
    }

    @SuppressWarnings("unused")
    private static Object __add__(String v, Object w) throws Throwable {
        return adapt(v).__add__(w);
    }

    @SuppressWarnings("unused")
    private Object __radd__(Object v) throws Throwable {
        return delegate.__radd__(v);
    }

    @SuppressWarnings("unused")
    private static Object __radd__(String w, Object v)
            throws Throwable {
        return adapt(w).__radd__(v);
    }

    private Object __mul__(Object n) throws Throwable {
        return delegate.__mul__(n);
    }

    private static Object __mul__(String self, Object n)
            throws Throwable {
        return adapt(self).__mul__(n);
    }

    @SuppressWarnings("unused")
    private Object __rmul__(Object n) throws Throwable {
        return __mul__(n);
    }

    @SuppressWarnings("unused")
    private static Object __rmul__(String self, Object n)
            throws Throwable {
        return __mul__(self, n);
    }

    // Find-like methods ----------------------------------------------

    /*
     * Several methods of str involve finding a target string within the
     * object receiving the call, to locate an occurrence, to count or
     * replace all occurrences, or to split the string at the first,
     * last or all occurrences.
     *
     * The fundamental algorithms are those that find the substring,
     * finding either the first occurrence, by scanning from the start
     * forwards, or the last by scanning from the end in reverse.
     *
     * Follow how find() and rfind() work, and the others will make
     * sense too, since they follow the same two patterns, but with
     * additional data movement to build the result, or repetition to
     * find all occurrences.
     */

    /**
     * Return the lowest index in the string where substring {@code sub}
     * is found, such that {@code sub} is contained in the slice
     * {@code [start:end]}. Arguments {@code start} and {@code end} are
     * interpreted as in slice notation, with {@code null} or
     * {@link Py#None} representing "missing".
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     */
    @PythonMethod
    int find(Object sub, Object start, Object end) {
        return find(delegate, sub, start, end);
    }

    @PythonMethod(primary = false)
    static int find(String self, Object sub, Object start, Object end) {
        return find(adapt(self), sub, start, end);
    }

    private static int find(CodepointDelegate s, Object sub,
            Object start, Object end) {
        CodepointDelegate p = adaptSub("find", sub);
        PySlice.Indices slice = getSliceIndices(s, start, end);
        if (p.length() == 0)
            return slice.start;
        else
            return find(s, p, slice);
    }

    /**
     * Inner implementation of Python {@code str.find()}. Return the
     * index of the leftmost occurrence of a (non-empty) substring in a
     * slice of some target string, or {@code -1} if there was no match.
     * Each string is specified by its delegate object.
     *
     * @param s to be searched
     * @param p the substring to look for
     * @param slice of {@code s} in which to search
     * @return the index of the occurrence or {@code -1}
     */
    private static int find(CodepointDelegate s, CodepointDelegate p,
            PySlice.Indices slice) {
        /*
         * Create an iterator for p (the needle string) and pick up the
         * first character we are seeking. We scan s for pChar = p[0],
         * and when it matches, divert into a full check using this
         * iterator.
         */
        CodepointIterator pi = p.iterator(0);
        int pChar = pi.nextInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark(); // at p[1]
        assert pLength > 0;

        // Counting in pos avoids hasNext() calls
        int pos = slice.start, lastPos = slice.stop - pLength;

        // An iterator on s[start:end], the string being searched
        CodepointIterator si = s.iterator(pos, slice.start, slice.stop);

        while (pos++ <= lastPos) {
            if (si.nextInt() == pChar) {
                /*
                 * s[pos] matched p[0]: divert into matching the rest of
                 * p. Leave a mark in s where we shall resume if this is
                 * not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.nextInt() != si.nextInt()) { break; }
                    match++;
                }
                // If we reached the end of p it's a match
                if (match == pLength) { return pos - 1; }
                // We stopped on a mismatch: reset si and pi
                sPos.restore();
                pMark.restore();
            }
        }
        return -1;
    }

    /**
     * Return the highest index in the string where substring
     * {@code sub} is found, such that {@code sub} is contained in the
     * slice {@code [start:end]}. Arguments {@code start} and
     * {@code end} are interpreted as in slice notation, with null or
     * {@link Py#None} representing "missing".
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     */
    @PythonMethod
    int rfind(Object sub, Object start, Object end) {
        return rfind(delegate, sub, start, end);
    }

    @PythonMethod(primary = false)
    static int rfind(String self, Object sub, Object start,
            Object end) {
        return rfind(adapt(self), sub, start, end);
    }

    private static int rfind(CodepointDelegate s, Object sub,
            Object start, Object end) {
        CodepointDelegate p = adaptSub("rfind", sub);
        PySlice.Indices slice = getSliceIndices(s, start, end);
        if (p.length() == 0)
            return slice.stop;
        else
            return rfind(s, p, slice);
    }

    /**
     * Inner implementation of Python {@code str.rfind()}. Return the
     * index of the rightmost occurrence of a (non-empty) substring in a
     * slice of some target string, or {@code -1} if there was no match.
     * Each string is specified by its delegate object.
     *
     * @param s to be searched
     * @param p the substring to look for
     * @param slice of {@code s} in which to search
     * @return the index of the occurrence or {@code -1}
     */
    private static int rfind(CodepointDelegate s, CodepointDelegate p,
            PySlice.Indices slice) {
        /*
         * Create an iterator for p (the needle string) and pick up the
         * last character we are seeking. We scan s in reverse for pChar
         * = p[-1], and when it matches, divert into a full check using
         * this iterator.
         */
        int pLength = p.length();
        CodepointIterator pi = p.iterator(pLength);
        int pChar = pi.previousInt();
        CodepointIterator.Mark pMark = pi.mark(); // p[-1]

        // Counting in pos avoids hasNext() calls. Start at the end.
        int pos = slice.stop, firstPos = slice.start + (pLength - 1);

        // An iterator on s[start:end], the string being searched.
        CodepointIterator si = s.iterator(pos, slice.start, slice.stop);

        while (--pos >= firstPos) {
            if (si.previousInt() == pChar) {
                /*
                 * s[pos] matched p[-1]: divert into matching the rest
                 * of p (still in reverse). Leave a mark in s where we
                 * shall resume if this is not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.previousInt() != si.previousInt()) { break; }
                    match++;
                }
                // If we reached the start of p it's a match
                if (match == pLength) { return pos - (pLength - 1); }
                // We stopped on a mismatch: reset si and pi
                sPos.restore();
                pMark.restore();
            }
        }
        return -1;
    }

    /**
     * Python {@code str.partition()}, splits the {@code str} at the
     * first occurrence of {@code sep} returning a {@link PyTuple}
     * containing the part before the separator, the separator itself,
     * and the part after the separator.
     *
     * @param sep on which to split the string
     * @return tuple of parts
     */
    @PythonMethod
    PyTuple partition(Object sep) {
        PyTuple r = partition(delegate, sep);
        return r != null ? r : Py.tuple(this, "", "");
    }

    @PythonMethod(primary = false)
    static PyTuple partition(String self, Object sep) {
        PyTuple r = partition(adapt(self), sep);
        return r != null ? r : Py.tuple(self, "", "");
    }

    /**
     * Inner implementation of Python {@code str.partition()}. Return a
     * {@code tuple} of the split result {@code (before, sep, after)},
     * or {@code null} if there was no match.
     *
     * @param s to be split
     * @param sep the separator to look for
     * @return tuple of parts or {@code null}
     */
    private static PyTuple partition(CodepointDelegate s, Object sep) {
        /*
         * partition() uses the same pattern as find(), with the
         * difference that it records characters in a buffer as it scans
         * them, and the slice is always the whole string.
         */
        // An iterator on p, the separator.
        CodepointDelegate p = adaptSeparator("partition", sep);
        CodepointIterator pi = p.iterator(0);
        int sChar, pChar = pi.nextInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        // Counting in pos avoids hasNext() calls.
        int pos = 0, lastPos = s.length() - pLength;

        // An iterator on s, the string being split.
        CodepointIterator si = s.iterator(pos);
        IntArrayBuilder buffer = new IntArrayBuilder();

        while (pos++ <= lastPos) {
            if ((sChar = si.nextInt()) == pChar) {
                /*
                 * s[pos] matched p[0]: divert into matching the rest of
                 * p. Leave a mark in s where we shall resume if this is
                 * not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.nextInt() != si.nextInt()) { break; }
                    match++;
                }
                // If we reached the end of p it's a match
                if (match == pLength) {
                    // Grab what came before the match.
                    Object before = buffer.takeUnicode();
                    // Now consume (the known length) after the match.
                    buffer = new IntArrayBuilder(lastPos - pos + 1);
                    Object after = buffer.append(si).takeUnicode();
                    // Return a result tuple
                    return Py.tuple(before, sep, after);
                }
                // We stopped on a mismatch: reset si and pi
                sPos.restore();
                pMark.restore();
            }
            // If we didn't return a result, consume one character
            buffer.append(sChar);
        }
        // If we didn't return a result, there was no match
        return null;
    }

    /**
     * Python {@code str.rpartition()}, splits the {@code str} at the
     * last occurrence of {@code sep}. Return a {@code tuple} containing
     * the part before the separator, the separator itself, and the part
     * after the separator.
     *
     * @param sep on which to split the string
     * @return tuple of parts
     */
    @PythonMethod
    PyTuple rpartition(Object sep) {
        PyTuple r;
        r = rpartition(delegate, sep);
        return r != null ? r : Py.tuple(this, "", "");
    }

    @PythonMethod(primary = false)
    static PyTuple rpartition(String self, Object sep) {
        PyTuple r = rpartition(adapt(self), sep);
        return r != null ? r : Py.tuple(self, "", "");
    }

    /**
     * Helper to Python {@code str.rpartition()}. Return a {@code tuple}
     * of the split result {@code (before, sep, after)}, or {@code null}
     * if there was no match.
     *
     * @param s to be split
     * @param sep the separator to look for
     * @return tuple of parts or {@code null}
     */
    private static PyTuple rpartition(CodepointDelegate s, Object sep) {
        /*
         * Create an iterator for p (the needle string) and pick up the
         * last character p[-1] we are seeking. We reset the iterator to
         * that position (pChar is still valid) when a match to p is
         * begun but proves partial.
         */
        CodepointDelegate p = adaptSeparator("rpartition", sep);
        CodepointIterator pi = p.iteratorLast();
        int sChar, pChar = pi.previousInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        // Counting in pos avoids hasNext() calls. Start at the end.
        int pos = s.length(), firstPos = pLength - 1;

        // An iterator on s, the string being split.
        CodepointIterator si = s.iterator(pos);
        IntArrayReverseBuilder buffer = new IntArrayReverseBuilder();

        while (--pos >= firstPos) {
            if ((sChar = si.previousInt()) == pChar) {
                /*
                 * s[pos] matched p[-1]: divert into matching the rest
                 * of p (still in reverse). Leave a mark in s where we
                 * shall resume if this is not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.previousInt() != si.previousInt()) { break; }
                    match++;
                }
                // If we reached the end of p it's a match
                if (match == pLength) {
                    // Grab what came after the match.
                    Object after = buffer.takeUnicode();
                    // Now consume (the known length) before the match.
                    buffer = new IntArrayReverseBuilder(si.nextIndex());
                    Object before = buffer.prepend(si).takeUnicode();
                    // Return a result
                    return Py.tuple(before, sep, after);
                }
                // We stopped on a mismatch: reset si and pi
                sPos.restore();
                pMark.restore();
            }
            // If we didn't return a result, consume one character
            buffer.prepend(sChar);
        }
        // If we didn't return a result, there was no match
        return null;
    }

    /**
     * Python {@code str.split([sep [, maxsplit]])} returning a
     * {@link PyList} of {@code str}. The target {@code self} will be
     * split at each occurrence of {@code sep}. If {@code sep == null},
     * whitespace will be used as the criterion. If {@code sep} has zero
     * length, a Python {@code ValueError} is raised. If
     * {@code maxsplit} &gt;=0 and there are more feasible splits than
     * {@code maxsplit} the last element of the list contains what is
     * left over after the last split.
     *
     * @param sep string to use as separator (or {@code null} if to
     *     split on whitespace)
     * @param maxsplit maximum number of splits to make (there may be
     *     {@code maxsplit+1} parts) or {@code -1} for all possible.
     * @return list(str) result
     */
    @PythonMethod
    PyList split(Object sep, int maxsplit) {
        return split(delegate, sep, maxsplit);
    }

    @PythonMethod(primary = false)
    static PyList split(String self, Object sep, int maxsplit) {
        return split(adapt(self), sep, maxsplit);
    }

    private static PyList split(CodepointDelegate s, Object sep,
            int maxsplit) {
        if (sep == null) {
            // Split on runs of whitespace
            return splitAtSpaces(s, maxsplit);
        } else if (maxsplit == 0) {
            // Easy case: a list containing self.
            PyList list = new PyList();
            list.add(s.principal());
            return list;
        } else {
            // Split on specified (non-empty) string
            CodepointDelegate p = adaptSeparator("split", sep);
            return split(s, p, maxsplit);
        }
    }

    /**
     * Implementation of {@code str.split} splitting on white space and
     * returning a list of the separated parts. If there are more than
     * {@code maxsplit} feasible splits the last element of the list is
     * the remainder of the original ({@code self}) string.
     *
     * @param s delegate presenting self as code points
     * @param maxsplit limit on the number of splits (if &gt;=0)
     * @return {@code PyList} of split sections
     */
    private static PyList splitAtSpaces(CodepointDelegate s,
            int maxsplit) {
        /*
         * Result built here is a list of split parts, exactly as
         * required for s.split(None, maxsplit). If there are to be n
         * splits, there will be n+1 elements in L.
         */
        PyList list = new PyList();

        // -1 means make all possible splits, at most:
        if (maxsplit < 0) { maxsplit = s.length(); }

        // An iterator on s, the string being searched
        CodepointIterator si = s.iterator(0);
        IntArrayBuilder segment = new IntArrayBuilder();

        while (si.hasNext()) {
            // We are currently scanning space characters
            while (si.hasNext()) {
                int c;
                if (!isPythonSpace(c = si.nextInt())) {
                    // Just read a non-space: start a segment
                    segment.append(c);
                    break;
                }
            }

            /*
             * Either s ran out while we were scanning space characters,
             * or we have started a new segment. If s ran out, we'll
             * burn past the next loop. If s didn't run out, the next
             * loop accumulates the segment until the next space (or s
             * runs out).
             */

            // We are currently building a non-space segment
            while (si.hasNext()) {
                int c = si.nextInt();
                // Twist: if we've run out of splits, append c anyway.
                if (maxsplit > 0 && isPythonSpace(c)) {
                    // Just read a space: end the segment
                    break;
                } else {
                    // Non-space, or last allowed segment
                    segment.append(c);
                }
            }

            /*
             * Either s ran out while we were scanning space characters,
             * or we have created a new segment. (It is possible s ran
             * out while we created the segment, but that's ok.)
             */
            if (segment.length() > 0) {
                // We created a segment.
                --maxsplit;
                list.add(segment.takeUnicode());
            }
        }
        return list;
    }

    /**
     * Implementation of Python {@code str.split}, returning a list of
     * the separated parts. If there are more than {@code maxsplit}
     * occurrences of {@code sep} the last element of the list is the
     * remainder of the original ({@code self}) string.
     *
     * @param s delegate presenting self as code points
     * @param p at occurrences of which {@code s} should be split
     * @param maxsplit limit on the number of splits (if not &lt;=0)
     * @return {@code PyList} of split sections
     */
    private static PyList split(CodepointDelegate s,
            CodepointDelegate p, int maxsplit) {
        /*
         * The structure of split() resembles that of count() in that
         * after a match we keep going. And it resembles partition() in
         * that, between matches, we are accumulating characters into a
         * segment buffer.
         */

        // -1 means make all possible splits, at most:
        if (maxsplit < 0) { maxsplit = s.length(); }

        // An iterator on p, the string sought.
        CodepointIterator pi = p.iterator(0);
        int pChar = pi.nextInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        // Counting in pos avoids hasNext() calls.
        int pos = 0, lastPos = s.length() - pLength, sChar;

        // An iterator on s, the string being searched.
        CodepointIterator si = s.iterator(pos);

        // Result built here is a list of split segments
        PyList list = new PyList();
        IntArrayBuilder segment = new IntArrayBuilder();

        while (si.hasNext()) {

            if (pos++ > lastPos || maxsplit <= 0) {
                /*
                 * We are too close to the end for a match now, or in
                 * our final segment (according to maxsplit==0).
                 * Everything that is left belongs to this segment.
                 */
                segment.append(si);

            } else if ((sChar = si.nextInt()) == pChar) {
                /*
                 * s[pos] matched p[0]: divert into matching the rest of
                 * p. Leave a mark in s where we shall resume if this is
                 * not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.nextInt() != si.nextInt()) { break; }
                    match++;
                }

                if (match == pLength) {
                    /*
                     * We reached the end of p: it's a match. Create
                     * emit the segment we have been accumulating, start
                     * a new one, and lose a life.
                     */
                    list.add(segment.takeUnicode());
                    --maxsplit;
                    // Catch pos up with si (matches do not overlap).
                    pos = si.nextIndex();
                } else {
                    /*
                     * We stopped on a mismatch: reset si to pos. The
                     * character that matched pChar is part of the
                     * current segment.
                     */
                    sPos.restore();
                    segment.append(sChar);
                }
                // In either case, reset pi to p[1].
                pMark.restore();

            } else {
                /*
                 * The character that wasn't part of a match with p is
                 * part of the current segment.
                 */
                segment.append(sChar);
            }
        }

        /*
         * Add the segment we were building when s ran out, even if it
         * is empty.
         */
        list.add(segment.takeUnicode());
        return list;
    }

    /**
     * Python {@code str.rsplit([sep [, maxsplit]])} returning a
     * {@link PyList} of {@code str}. The target {@code self} will be
     * split at each occurrence of {@code sep}. If {@code sep == null},
     * whitespace will be used as the criterion. If {@code sep} has zero
     * length, a Python {@code ValueError} is raised. If
     * {@code maxsplit} &gt;=0 and there are more feasible splits than
     * {@code maxsplit} the last element of the list contains what is
     * left over after the last split.
     *
     * @param sep string to use as separator (or {@code null} if to
     *     split on whitespace)
     * @param maxsplit maximum number of splits to make (there may be
     *     {@code maxsplit+1} parts) or {@code -1} for all possible.
     * @return list(str) result
     */
    @PythonMethod
    PyList rsplit(Object sep, int maxsplit) {
        return rsplit(delegate, sep, maxsplit);
    }

    @PythonMethod(primary = false)
    static PyList rsplit(String self, Object sep, int maxsplit) {
        return rsplit(adapt(self), sep, maxsplit);
    }

    private static PyList rsplit(CodepointDelegate s, Object sep,
            int maxsplit) {
        if (sep == null) {
            // Split on runs of whitespace
            return rsplitAtSpaces(s, maxsplit);
        } else if (maxsplit == 0) {
            // Easy case: a list containing self.
            PyList list = new PyList();
            list.add(s.principal());
            return list;
        } else {
            // Split on specified (non-empty) string
            CodepointDelegate p = adaptSeparator("rsplit", sep);
            return rsplit(s, p, maxsplit);
        }
    }

    /**
     * Implementation of {@code str.rsplit} splitting on white space and
     * returning a list of the separated parts. If there are more than
     * {@code maxsplit} feasible splits the last element of the list is
     * the remainder of the original ({@code self}) string.
     *
     * @param s delegate presenting self as code points
     * @param maxsplit limit on the number of splits (if &gt;=0)
     * @return {@code PyList} of split sections
     */
    private static PyList rsplitAtSpaces(CodepointDelegate s,
            int maxsplit) {
        /*
         * Result built here is a list of split parts, exactly as
         * required for s.rsplit(None, maxsplit). If there are to be n
         * splits, there will be n+1 elements in L.
         */
        PyList list = new PyList();

        // -1 means make all possible splits, at most:
        if (maxsplit < 0) { maxsplit = s.length(); }

        // A reverse iterator on s, the string being searched
        CodepointIterator si = s.iteratorLast();
        IntArrayReverseBuilder segment = new IntArrayReverseBuilder();

        while (si.hasPrevious()) {
            // We are currently scanning space characters
            while (si.hasPrevious()) {
                int c;
                if (!isPythonSpace(c = si.previousInt())) {
                    // Just read a non-space: start a segment
                    segment.prepend(c);
                    break;
                }
            }

            /*
             * Either s ran out while we were scanning space characters,
             * or we have started a new segment. If s ran out, we'll
             * burn past the next loop. If s didn't run out, the next
             * loop accumulates the segment until the next space (or s
             * runs out).
             */

            // We are currently building a non-space segment
            while (si.hasPrevious()) {
                int c = si.previousInt();
                // Twist: if we've run out of splits, prepend c anyway.
                if (maxsplit > 0 && isPythonSpace(c)) {
                    // Just read a space: end the segment
                    break;
                } else {
                    // Non-space, or last allowed segment
                    segment.prepend(c);
                }
            }

            /*
             * Either s ran out while we were scanning space characters,
             * or we have created a new segment. (It is possible s ran
             * out while we created the segment, but that's ok.)
             */
            if (segment.length() > 0) {
                // We created a segment.
                --maxsplit;
                list.add(segment.takeUnicode());
            }
        }

        // We built the list backwards, so reverse it.
        list.reverse();
        return list;
    }

    /**
     * Implementation of Python {@code str.rsplit}, returning a list of
     * the separated parts. If there are more than {@code maxsplit}
     * occurrences of {@code sep} the last element of the list is the
     * remainder of the original ({@code self}) string.
     *
     * @param s delegate presenting self as code points
     * @param p at occurrences of which {@code s} should be split
     * @param maxsplit limit on the number of splits (if not &lt;=0)
     * @return {@code PyList} of split sections
     */
    private static PyList rsplit(CodepointDelegate s,
            CodepointDelegate p, int maxsplit) {
        /*
         * The structure of rsplit() resembles that of count() in that
         * after a match we keep going. And it resembles rpartition() in
         * that, between matches, we are accumulating characters into a
         * segment buffer, and we are working backwards from the end.
         */

        // -1 means make all possible splits, at most:
        if (maxsplit < 0) { maxsplit = s.length(); }

        // A reverse iterator on p, the string sought.
        CodepointIterator pi = p.iteratorLast();
        int pChar = pi.previousInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        /*
         * Counting backwards in pos we recognise when there can be no
         * further matches.
         */
        int pos = s.length(), firstPos = pLength - 1, sChar;

        // An iterator on s, the string being searched.
        CodepointIterator si = s.iterator(pos);

        // Result built here is a list of split segments
        PyList list = new PyList();
        IntArrayReverseBuilder segment = new IntArrayReverseBuilder();

        while (si.hasPrevious()) {
            if (--pos < firstPos || maxsplit <= 0) {
                /*
                 * We are too close to the start for a match now, or in
                 * our final segment (according to maxsplit==0).
                 * Everything that is left belongs to this segment.
                 */
                segment.prepend(si);
            } else if ((sChar = si.previousInt()) == pChar) {
                /*
                 * s[pos] matched p[-1]: divert into matching the rest
                 * of p. Leave a mark in s where we shall resume if this
                 * is not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.previousInt() != si.previousInt()) { break; }
                    match++;
                }

                if (match == pLength) {
                    /*
                     * We reached the end of p: it's a match. Create
                     * emit the segment we have been accumulating, start
                     * a new one, and lose a life.
                     */
                    list.add(segment.takeUnicode());
                    --maxsplit;
                    // Catch pos up with si (matches do not overlap).
                    pos = si.nextIndex();
                } else {
                    /*
                     * We stopped on a mismatch: reset si to pos. The
                     * character that matched pChar is part of the
                     * current segment.
                     */
                    sPos.restore();
                    segment.prepend(sChar);
                }
                // In either case, reset pi to p[1].
                pMark.restore();

            } else {
                /*
                 * The character that wasn't part of a match with p is
                 * part of the current segment.
                 */
                segment.prepend(sChar);
            }
        }

        /*
         * Add the segment we were building when s ran out, even if it
         * is empty. Note the list is backwards and we must reverse it.
         */
        list.add(segment.takeUnicode());
        list.reverse();
        return list;
    }

    /**
     * Python {@code str.splitlines([keepends])} returning a list of the
     * lines in the string, breaking at line boundaries. Line breaks are
     * not included in the resulting list unless {@code keepends} is
     * given and true.
     * <p>
     * This method splits on the following line boundaries: LF="\n",
     * VT="\u000b", FF="\f", CR="\r", FS="\u001c", GS="\u001d",
     * RS="\u001e", NEL="\u0085", LSEP="\u2028", PSEP="\u2029" and
     * CR-LF="\r\n". In this last case, the sequence "\r\n" is treated
     * as one line separator.
     *
     * @param keepends the lines in the list retain the separator that
     *     caused the split
     * @return the list of lines
     */
    // Not yet converting boolean @PythonMethod
    PyList splitlines(boolean keepends) {
        return splitlines(delegate, keepends);
    }

    // Not yet converting boolean @PythonMethod(primary = false)
    static PyList splitlines(String self, boolean keepends) {
        return splitlines(adapt(self), keepends);
    }

    private static PyList splitlines(CodepointDelegate s,
            boolean keepends) {
        /*
         * The structure of splitlines() resembles that of split() for
         * explicit strings, except that the criteria for recognising
         * the "needle" are implicit.
         */
        // An iterator on s, the string being searched.
        CodepointIterator si = s.iterator(0);

        // Result built here is a list of split segments
        PyList list = new PyList();
        IntArrayBuilder line = new IntArrayBuilder();

        /*
         * We scan the input string looking for characters that mark
         * line endings, and appending to the line buffer as we go. Each
         * detected ending makes a PyUnicode to add t5o list.
         */
        while (si.hasNext()) {

            int c = si.nextInt();

            if (isPythonLineSeparator(c)) {
                // Check for a possible CR-LF combination
                if (c == '\r' && si.hasNext()) {
                    // Might be ... have to peek ahead
                    int c2 = si.nextInt();
                    if (c2 == '\n') {
                        // We're processing CR-LF
                        if (keepends) { line.append(c); }
                        // Leave the \n for the main path to deal with
                        c = c2;
                    } else {
                        // There was no \n following \r: undo the read
                        si.previousInt();
                    }
                }
                // Optionally append the (single) line separator c
                if (keepends) { line.append(c); }
                // Emit the line (and start another)
                list.add(line.takeUnicode());

            } else {
                // c is part of the current line.
                line.append(c);
            }
        }

        /*
         * Add the segment we were building when s ran out, but not if
         * it is empty.
         */
        if (line.length() > 0) { list.add(line.takeUnicode()); }

        return list;
    }

    /**
     * As {@link #find(Object, Object, Object)}, but throws
     * {@link ValueError} if the substring is not found.
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     * @throws ValueError if {@code sub} is not found
     */
    @PythonMethod
    int index(Object sub, Object start, Object end) throws ValueError {
        return checkIndexReturn(find(delegate, sub, start, end));
    }

    @PythonMethod(primary = false)
    static int index(String self, Object sub, Object start,
            Object end) {
        return checkIndexReturn(find(adapt(self), sub, start, end));
    }

    /**
     * As {@link #rfind(Object, Object, Object)}, but throws
     * {@link ValueError} if the substring is not found.
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     * @throws ValueError if {@code sub} is not found
     */
    @PythonMethod
    int rindex(Object sub, Object start, Object end) throws ValueError {
        return checkIndexReturn(rfind(delegate, sub, start, end));
    }

    @PythonMethod(primary = false)
    static int rindex(String self, Object sub, Object start,
            Object end) {
        return checkIndexReturn(rfind(adapt(self), sub, start, end));
    }

    /**
     * Return the number of non-overlapping occurrences of substring
     * {@code sub} in the range {@code [start:end]}. Optional arguments
     * {@code start} and {@code end} are interpreted as in slice
     * notation.
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return count of occurrences.
     */
    @PythonMethod
    int count(Object sub, Object start, Object end) {
        return count(delegate, sub, start, end);
    }

    @PythonMethod(primary = false)
    static int count(String self, Object sub, Object start,
            Object end) {
        return count(adapt(self), sub, start, end);
    }

    private static int count(CodepointDelegate s, Object sub,
            Object start, Object end) {
        CodepointDelegate p = adaptSub("count", sub);
        PySlice.Indices slice = getSliceIndices(s, start, end);
        if (p.length() == 0)
            return slice.start;
        else
            return count(s, p, slice);
    }

    /**
     * The inner implementation of {@code str.count}, returning the
     * number of occurrences of a substring. It accepts slice-like
     * arguments, which may be {@code None} or end-relative (negative).
     * This method also supports
     * {@link PyUnicode#count(PyObject, PyObject, PyObject)}.
     *
     * @param sub substring to find.
     * @param startObj start of slice.
     * @param endObj end of slice.
     * @return count of occurrences
     */
    private static int count(CodepointDelegate s, CodepointDelegate p,
            PySlice.Indices slice) {
        /*
         * count() uses the same pattern as find(), with the difference
         * that it keeps going rather than returning on the first match.
         */
        // An iterator on p, the string sought.
        CodepointIterator pi = p.iterator(0);
        int pChar = pi.nextInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        // Counting in pos avoids hasNext() calls.
        int pos = slice.start, lastPos = slice.stop - pLength;

        // An iterator on s[start:end], the string being searched.
        CodepointIterator si = s.iterator(pos, slice.start, slice.stop);
        int count = 0;

        while (pos++ <= lastPos) {
            if (si.nextInt() == pChar) {
                /*
                 * s[pos] matched p[0]: divert into matching the rest of
                 * p. Leave a mark in s where we shall resume if this is
                 * not a full match with p.
                 */
                CodepointIterator.Mark sPos = si.mark();
                int match = 1;
                while (match < pLength) {
                    if (pi.nextInt() != si.nextInt()) { break; }
                    match++;
                }
                if (match == pLength) {
                    // We reached the end of p: it's a match.
                    count++;
                    // Catch pos up with si (matches do not overlap).
                    pos = si.nextIndex();
                } else {
                    // We stopped on a mismatch: reset si to pos.
                    sPos.restore();
                }
                // In either case, reset pi to p[1].
                pMark.restore();
            }
        }
        return count;
    }

    // Predicate methods ----------------------------------------------

    /*
     * We group here methods that are boolean functions of the string,
     * based on tests of character properties, for example
     * str.isascii(). They have a common pattern.
     */
    @PythonMethod(primary = false)
    boolean isascii() {
        for (int c : delegate) { if (c > 127) { return false; } }
        return true;
    }

    @PythonMethod
    static boolean isascii(String self) {
        for (int c : adapt(self)) { if (c > 127) { return false; } }
        return true;
    }

    // Transformation methods -----------------------------------------

    /*
     * We group here methods that are simle transformation functions of
     * the string, based on tests of character properties, for example
     * str.strip() and str.title().
     */

    @PythonMethod
    PyUnicode ljust(int width, @Default(" ") String fillchar) {
        int len = Math.min(fillchar.length(), 4);
        if (fillchar.codePointCount(0, len) != 1) {
            throw new TypeError(BAD_FILLCHAR);
        }
        int n = value.length;
        int m = Math.max(width, n), start = 0, fill = m - n;
        if (fill <= 0) { return this; }
        int[] buf = new int[m];
        // The original
        System.arraycopy(value, start, buf, 0, n);
        // The fill
        int fillcp = fillchar.codePointAt(0);
        for (int i = n; i < m; i++) { buf[i] = fillcp; }
        return new PyUnicode(TYPE, buf);
    }

    @PythonMethod(primary = false)
    static String ljust(String self, int width, String fillchar) {
        int len = Math.min(fillchar.length(), 4);
        if (fillchar.codePointCount(0, len) != 1) {
            throw new TypeError(BAD_FILLCHAR);
        }
        int n = self.codePointCount(0, self.length());
        int m = Math.max(width, n), start = 0, fill = m - n;
        if (fill <= 0) { return self; }
        StringBuilder buf =
                new StringBuilder(self.length() + fill * len);
        // The original
        buf.append(self.substring(start));
        // The fill
        for (int i = 0; i < fill; i++) { buf.append(fillchar); }
        return buf.toString();

    }

    private static String BAD_FILLCHAR =
            "the fill character must be exactly one character long";

    // Simplified version (no count)
    @PythonMethod
    String replace(Object old, @Name("new") Object _new) {
        // Annotations repeat since cannot rely on order processed
        String oldstr = old.toString();
        String newstr = _new.toString();
        return this.toString().replace(oldstr, newstr);

    }

    @PythonMethod(primary = false)
    static String replace(String self, Object old, Object _new) {
        // Annotations repeat since cannot rely on order processed
        String oldstr = old.toString();
        String newstr = _new.toString();
        return self.replace(oldstr, newstr);
    }

    @PythonMethod
    PyUnicode zfill(int width) {
        int n = value.length;
        int m = Math.max(width, n), start = 0, fill = m - n, c;
        if (fill <= 0) { return this; }
        int[] buf = new int[m];
        // If self starts with a sign, preserve it at the front
        if (n >= 1 && ((c = value[0]) == '-' || c == '+')) {
            start = 1;
            buf[0] = c;
        }
        // The fill
        for (int i = start; i < fill + start; i++) { buf[i] = '0'; }
        // The original without its sign (if any)
        System.arraycopy(value, start, buf, fill + start, n - start);
        return new PyUnicode(TYPE, buf);
    }

    @PythonMethod(primary = false)
    static String zfill(String self, int width) {
        int n = self.codePointCount(0, self.length());
        int m = Math.max(width, n), start = 0, fill = m - n, c;
        if (fill <= 0) { return self; }
        StringBuilder buf = new StringBuilder(m);
        // If self starts with a sign, preserve it at the front
        if (n >= 1 && ((c = self.codePointAt(0)) == '-' || c == '+')) {
            start = 1;
            buf.appendCodePoint(c);
        }
        // The fill
        for (int i = 0; i < fill; i++) { buf.append('0'); }
        // The original without its sign (if any)
        buf.append(self.substring(start));
        return buf.toString();
    }

    // Java-only API --------------------------------------------------

    private static final int HIGH_SURROGATE_OFFSET =
            Character.MIN_HIGH_SURROGATE
                    - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10);

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @Override
    public int hashCode() throws PyException {
        return PyDict.pythonHash(this);
    }

    /**
     * Compare for equality with another Python {@code str}, or a
     * {@link PyDict.Key} containing a {@code str}. If the other object
     * is not a {@code str}, or a {@code Key} containing a {@code str},
     * return {@code false}. If it is such an object, compare for
     * equality of the code points.
     */
    @Override
    public boolean equals(Object obj) {
        return PyDict.pythonEquals(this, obj);
    }

    /**
     * Create a {@code str} from a format and arguments. Note Java
     * {@code String.format} semantics are applied, not the CPython
     * ones.
     *
     * @param fmt format string (Java semantics)
     * @param args arguments
     * @return formatted string
     */
    @Deprecated // XXX possibly want a version with Python semantics
    static PyUnicode fromFormat(String fmt, Object... args) {
        return new PyUnicode(TYPE, String.format(fmt, args));
    }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int c : value) {
            if (c >= Character.MIN_SURROGATE
                    && c <= Character.MAX_SURROGATE) {
                // This is a lone surrogate: show the code
                b.append(String.format("\\u%04x", c));
            } else {
                b.appendCodePoint(c);
            }
        }
        return b.toString();
    }

    /**
     * Present a Python {@code str} as a Java {@code String} value or
     * raise a {@link TypeError}. This is for use when the argument is
     * expected to be a Python {@code str} or a sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws TypeError if {@code v} is not a Python {@code str}
     */
    static String asString(Object v) throws TypeError {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).toString();
        throw Abstract.requiredTypeError("a str", v);
    }

    // Plumbing ------------------------------------------------------

    /**
     * Convert a Python {@code str} to a Java {@code str} (or throw
     * {@link NoConversion}).
     * <p>
     * If the method throws the special exception {@link NoConversion},
     * the caller must deal with it by throwing an appropriate Python
     * exception or taking an alternative course of action.
     *
     * @param v to convert
     * @return converted to {@code String}
     * @throws NoConversion v is not a {@code str}
     */
    static String convertToString(Object v) throws NoConversion {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).toString();
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Test whether a string contains no characters above the BMP range,
     * that is, any characters that require surrogate pairs to represent
     * them. The method returns {@code true} if and only if the string
     * consists entirely of BMP characters or is empty.
     *
     * @param s the string to test
     * @return whether contains no non-BMP characters
     */
    private static boolean isBMP(String s) {
        return s.codePoints().dropWhile(Character::isBmpCodePoint)
                .findFirst().isEmpty();
    }

    /**
     * Define what characters are to be treated as a space according to
     * Python 3.
     */
    private static boolean isPythonSpace(int cp) {
        // Use the Java built-in methods as far as possible
        return Character.isWhitespace(cp) // ASCII spaces and some
                // remaining Unicode spaces
                || Character.isSpaceChar(cp)
                // NEXT LINE (not a space in Java or Unicode)
                || cp == 0x0085;
    }

    /**
     * Define what characters are to be treated as a line separator
     * according to Python 3. In {@code splitlines} we treat these as
     * separators, but also give singular treatment to the sequence
     * CR-LF.
     */
    private static boolean isPythonLineSeparator(int cp) {
        // Bit i is set if code point i is a line break (i<32).
        final int EOL = 0b0111_0000_0000_0000_0011_1100_0000_0000;
        if (cp >>> 5 == 0) {
            // cp < 32: use the little look-up table
            return ((EOL >>> cp) & 1) != 0;
        } else if (cp >>> 7 == 0) {
            // 32 <= cp < 128 : the rest of ASCII
            return false;
        } else {
            // NEL, L-SEP, P-SEP
            return cp == 0x85 || cp == 0x2028 || cp == 0x2029;
        }
    }

    /**
     * A base class for the delegate of either a {@code String} or a
     * {@code PyUnicode}, implementing {@code __getitem__} and other
     * index-related operations. The class is a
     * {@link PySequence.Delegate}, an iterable of {@code Integer},
     * comparable with other instances of the same base, and is able to
     * supply point codes as a stream.
     */
    static abstract class CodepointDelegate
            extends PySequence.Delegate<Integer, Object>
            implements PySequence.OfInt {
        /**
         * A bidirectional iterator on the sequence of code points
         * between two indices.
         *
         * @param index starting position (code point index)
         * @param start index of first element to include.
         * @param end index of first element not to include.
         * @return the iterator
         */
        abstract CodepointIterator iterator(int index, int start,
                int end);

        /**
         * A bidirectional iterator on the sequence of code points.
         *
         * @param index starting position (code point index)
         * @return the iterator
         */
        CodepointIterator iterator(int index) {
            return iterator(index, 0, length());
        }

        /**
         * A bidirectional iterator on the sequence of code points,
         * positioned initially one beyond the end of the sequence, so
         * that the first call to {@code previous()} returns the last
         * element.
         *
         * @param index starting position (code point index)
         * @param start index of first element to include.
         * @param end index of first element not to include.
         * @return the iterator
         */
        CodepointIterator iteratorLast() {
            return iterator(length());
        }

        /**
         * Return a sub-range of the delegate contents corresponding to
         * the (opaque) indices given, which must have been obtained
         * from calls to {@link CodepointIterator#nextRangeIndex()}.
         * Since the maximum opaque range index is not easily available,
         * {@code end} will be trimmed to fit.
         *
         * @param start range index of first element to include.
         * @param end range index of first element not to include.
         * @return the range as an array
         */
        abstract int[] getRange(int start, int end);

        @Override
        public Iterator<Integer> iterator() { return iterator(0); }

        /**
         * Return the object of which this is the delegate.
         *
         * @return the object of which this is the delegate
         */
        abstract Object principal();

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("adapter(\"");
            for (Integer c : this) { b.appendCodePoint(c); }
            return b.append("\")").toString();
        }
    }

    /**
     * A {@code ListIterator} from which one can obtain an opaque index
     * usable with {@link CodepointDelegate#getRange(int, int)}.
     */
    interface CodepointIterator
            extends ListIterator<Integer>, PrimitiveIterator.OfInt {

        @Override
        default Integer next() { return nextInt(); }

        /**
         * Returns {@code true} if this list iterator has the given
         * number of elements when traversing the list in the forward
         * direction.
         *
         * @param n number of elements needed
         * @return {@code true} if has a further {@code n} elements
         *     going forwards
         */
        boolean hasNext(int n);

        /**
         * Equivalent to {@code n} calls to {@link #nextInt()} returning
         * the last result.
         *
         * @param n the number of advances
         * @return the {@code n}th next {@code int}
         */
        int nextInt(int n);

        @Override
        default Integer previous() { return previousInt(); }

        /**
         * Returns {@code true} if this list iterator has the given
         * number of elements when traversing the list in the reverse
         * direction.
         *
         * @param n number of elements needed
         * @return {@code true} if has a further {@code n} elements
         *     going backwards
         */
        boolean hasPrevious(int n);

        /**
         * Returns the previous {@code int} element in the iteration.
         * This is just previous specialised to a primitive {@code int}.
         *
         * @return
         */
        int previousInt();

        /**
         * Equivalent to {@code n} calls to {@link #prevousInt()}
         * returning the last result.
         *
         * @param n the number of steps to take (in reverse)
         * @return the {@code n}th previous {@code int}
         */
        int previousInt(int n);

        /**
         * An opaque index for the next code point {@link #next()} will
         * return, usable with
         * {@link CodepointDelegate#getRange(int, int)}.
         */
        default int nextRangeIndex() { return nextIndex(); }

        /**
         * An opaque index for the next code point {@link #previous()}
         * will return, usable with
         * {@link CodepointDelegate#getRange(int, int)}.
         */
        default int previousRangeIndex() { return previousIndex(); }

        // Unsupported operations -----------------------------

        @Override
        default void remove() {
            throw new UnsupportedOperationException();
        }

        @Override
        default void set(Integer o) {
            throw new UnsupportedOperationException();
        }

        @Override
        default void add(Integer o) {
            throw new UnsupportedOperationException();
        }

        /**
         * Set a mark (a saved state) to which the iterator may be
         * restored later.
         *
         * @return the mark
         */
        Mark mark();

        /**
         * An opaque object to hold and restore the position of a
         * particular {@link CodepointIterator}.
         */
        interface Mark {
            /**
             * Restore the position of the iterator from which this
             * {@code Mark} was obtained, to the position it had at the
             * time.
             */
            void restore();
        }

    }

    /**
     * Wrap a Java {@code String} as a {@link PySequence.Delegate}, that
     * is also an iterable of {@code Integer}. If the {@code String}
     * includes surrogate pairs of {@code char}s, these are interpreted
     * as a single Python code point.
     */
    static class StringAdapter extends CodepointDelegate {

        /** Value of the str encoded as a Java {@code String}. */
        private final String s;
        /** Length in code points deduced from the {@code String}. */
        private final int length;

        /**
         * Adapt a String so we can iterate or stream its code points.
         *
         * @param s to adapt
         */
        StringAdapter(String s) {
            this.s = s;
            length = s.codePointCount(0, s.length());
        }

        /**
         * Return {@code true} iff the string contains only basic plane
         * characters or, possibly, isolated surrogates. All
         * {@code char}s may be treated as code points.
         *
         * @return contains only BMP characters or isolated surrogates
         */
        private boolean isBMP() { return length == s.length(); }

        @Override
        public int length() { return length; };

        @Override
        public PyType getType() { return TYPE; }

        @Override
        public String getTypeName() { return "string"; }

        @Override
        Object principal() { return s; }

        @Override
        public Object getItem(int i) {
            if (isBMP()) {
                // No surrogate pairs.
                return String.valueOf(s.charAt(i));
            } else {
                // We have to count from the start
                int k = toCharIndex(i);
                return PyUnicode.fromCodePoint(s.codePointAt(k));
            }
        }

        /**
         * Translate a (valid) code point index into a {@code char}
         * index into {@code s}, when s contains surrogate pairs. A call
         * is normally guarded by {@link #isBMP()}, since when that is
         * {@code true} we can avoid the work.
         *
         * @param cpIndex code point index
         * @return {@code char} index into {@code s}
         */
        private int toCharIndex(int cpIndex) {
            int L = s.length();
            if (cpIndex == length) {
                // Avoid counting to the end
                return L;
            } else {
                int i = 0, cpCount = 0;
                while (i < L && cpCount < cpIndex) {
                    char c = s.charAt(i++);
                    cpCount++;
                    if (Character.isHighSurrogate(c) && i < L) {
                        // Expect a low surrogate
                        char d = s.charAt(i);
                        if (Character.isLowSurrogate(d)) { i++; }
                    }
                }
                return i;
            }
        }

        @Override
        public Object getSlice(Indices slice) throws Throwable {
            if (slice.slicelength == 0) {
                return "";
            } else if (slice.step == 1 && isBMP()) {
                return s.substring(slice.start, slice.stop);
            } else {
                /*
                 * If the code points are not all BMP, it is less work
                 * in future if we use a PyUnicode. If step != 1, there
                 * is the possibility of creating an unintended
                 * surrogate pair, so only a PyUnicode should be trusted
                 * to represent the result.
                 */
                int L = slice.slicelength, i = slice.start;
                int[] r = new int[L];
                if (isBMP()) {
                    // Treating surrogates as characters
                    for (int j = 0; j < L; j++) {
                        r[j] = s.charAt(i);
                        i += slice.step;
                    }
                } else if (slice.step > 0) {
                    // Work forwards through the sequence
                    ListIterator<Integer> cps = iterator(i);
                    r[0] = cps.next();
                    for (int j = 1; j < L; j++) {
                        for (int k = 1; k < slice.step; k++) {
                            cps.next();
                        }
                        r[j] = cps.next();
                    }
                } else { // slice.step < 0
                    // Work backwards through the sequence
                    ListIterator<Integer> cps = iterator(i + 1);
                    r[0] = cps.previous();
                    for (int j = 1; j < L; j++) {
                        for (int k = -1; k > slice.step; --k) {
                            cps.previous();
                        }
                        r[j] = cps.previous();
                    }
                }
                return new PyUnicode(TYPE, true, r);
            }
        }

        @Override
        int[] getRange(int start, int end) {
            // start and end are char indices
            end = Math.min(end, s.length());
            int n = s.codePointCount(start, end);
            int[] r = new int[n];
            if (end - start == n) {
                // There are no surrogate pairs between them: easy.
                for (int i = 0, j = start; i < n; i++) {
                    r[i] = s.charAt(j++);
                }
            } else {
                // Be prepared for surrogate pairs
                for (int i = 0, j = start; i < n; i++) {
                    j += Character.isBmpCodePoint(
                            r[i] = s.codePointAt(j)) ? 1 : 2;
                }
            }
            return r;
        }

        @Override
        Object add(Object ow)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ow instanceof String) {
                return PyUnicode.concat(s, (String)ow);
            } else {
                IntStream w = adapt(ow).asIntStream();
                return concatUnicode(s.codePoints(), w);
            }
        }

        @Override
        Object radd(Object ov)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ov instanceof String) {
                return PyUnicode.concat((String)ov, s);
            } else {
                IntStream v = adapt(ov).asIntStream();
                return concatUnicode(v, s.codePoints());
            }
        }

        @Override
        Object repeat(int n) throws OutOfMemoryError, Throwable {
            if (n == 0)
                return "";
            else if (n == 1 || length == 0)
                return s;
            else if (Character.isLowSurrogate(s.charAt(0))
                    && Character.isHighSurrogate(s.charAt(length - 1)))
                /*
                 * s ends with a high surrogate and starts with a low
                 * surrogate, so simply concatenated to itself by
                 * String.repeat, these would merge into one character.
                 * Only a PyUnicode properly represents the result.
                 */
                return (new PyUnicode(TYPE, s)).delegate.repeat(n);
            else
                // Java String repeat will do fine
                return s.repeat(n);
        }

        @Override
        public int
                compareTo(PySequence.Delegate<Integer, Object> other) {
            Iterator<Integer> ia = iterator();
            Iterator<Integer> ib = other.iterator();
            while (ia.hasNext()) {
                if (ib.hasNext()) {
                    int a = ia.next();
                    int b = ib.next();
                    // if a != b, then we've found an answer
                    if (a > b)
                        return 1;
                    else if (a < b)
                        return -1;
                } else
                    // s has not run out, but b has. s wins
                    return 1;
            }
            /*
             * The sequences matched over the length of s. The other is
             * the winner if it still has elements. Otherwise its a tie.
             */
            return ib.hasNext() ? -1 : 0;
        }

        // PySequence.OfInt interface --------------------------------

        @Override
        public Spliterator.OfInt spliterator() {
            return s.codePoints().spliterator();
        }

        @Override
        public IntStream asIntStream() { return s.codePoints(); }

        // ListIterator provision ------------------------------------

        @Override
        public CodepointIterator iterator(final int index, int start,
                int end) {
            if (isBMP())
                return new BMPIterator(index, start, end);
            else
                return new SMPIterator(index, start, end);
        }

        /**
         * A {@code ListIterator} for use when the string in the
         * surrounding adapter instance contains only basic multilingual
         * plane (BMP) characters or isolated surrogates.
         * {@link SMPIterator} extends this class for supplementary
         * characters.
         */
        class BMPIterator implements CodepointIterator {
            /**
             * Index into {@code s} in code points, which is also its
             * index in {@code s} in chars when {@code s} is a BMP
             * string.
             */
            protected int index;
            /**
             * First index at which {@link #next()} is allowable for the
             * iterator in code points, which is also its index in
             * {@code s} in chars when {@code s} is a BMP string.
             */
            protected final int start;
            /**
             * First index at which {@link #next()} is not allowable for
             * the iterator in code points, which is also its index in
             * {@code s} in chars when {@code s} is a BMP string.
             */
            protected final int end;

            BMPIterator(int index, int start, int end) {
                checkIndexRange(index, start, end, length);
                this.start = start;
                this.end = end;
                this.index = index;
            }

            @Override
            public Mark mark() {
                return new Mark() {
                    final int i = index;

                    @Override
                    public void restore() { index = i; }
                };
            }

            // The forward iterator -------------------------------

            @Override
            public boolean hasNext() { return index < end; }

            @Override
            public boolean hasNext(int n) {
                assert n >= 0;
                return index + n <= end;
            }

            @Override
            public int nextInt() {
                if (index < end)
                    return s.charAt(index++);
                else
                    throw noSuchElement(nextIndex());
            }

            @Override
            public int nextInt(int n) {
                assert n >= 0;
                int i = index + n;
                if (i <= end)
                    return s.charAt((index = i) - 1);
                else
                    throw noSuchElement(i - start);
            }

            @Override
            public int nextIndex() { return index - start; }

            // The reverse iterator -------------------------------

            @Override
            public boolean hasPrevious() { return index > start; }

            @Override
            public boolean hasPrevious(int n) {
                assert n >= 0;
                return index - n >= 0;
            }

            @Override
            public int previousInt() {
                if (index > start)
                    return s.charAt(--index);
                else
                    throw noSuchElement(previousIndex());
            }

            @Override
            public int previousInt(int n) {
                assert n >= 0;
                int i = index - n;
                if (i >= start)
                    return s.charAt(index = i);
                else
                    throw noSuchElement(i);
            }

            @Override
            public int previousIndex() { return index - start - 1; }

            // Diagnostic use -------------------------------------

            @Override
            public String toString() {
                return String.format("[%s|%s]",
                        s.substring(start, index),
                        s.substring(index, end));
            }
        }

        /**
         * A {@code ListIterator} for use when the string in the
         * surrounding adapter instance contains one or more
         * supplementary multilingual plane characters represented by
         * surrogate pairs.
         */
        class SMPIterator extends BMPIterator {

            /**
             * Index of the iterator position in {@code s} in chars.
             * This always moves in synchrony with the base class index
             * {@link BMPIterator#index}, which continues to represent
             * the same position as a code point index. Both reference
             * the same character.
             */
            private int charIndex;
            /**
             * The double of {@link BMPIterator#start} in {@code s} in
             * chars.
             */
            final private int charStart;
            /**
             * The double of {@link BMPIterator#end} in {@code s} in
             * chars.
             */
            final private int charEnd;

            SMPIterator(int index, int start, int end) {
                super(index, start, end);
                // Convert the arguments to character indices
                int p = 0, cp = 0;
                while (p < start) { cp = nextCharIndex(cp); p += 1; }
                this.charStart = cp;
                while (p < index) { cp = nextCharIndex(cp); p += 1; }
                this.charIndex = cp;
                while (p < end) { cp = nextCharIndex(cp); p += 1; }
                this.charEnd = cp;
            }

            /** @return next char index after argument. */
            private int nextCharIndex(int cp) {
                if (Character.isBmpCodePoint(s.codePointAt(cp)))
                    return cp + 1;
                else
                    return cp + 2;
            }

            @Override
            public Mark mark() {
                return new Mark() {
                    // In the SMP iterator, we must save both indices
                    final int i = index, ci = charIndex;

                    @Override
                    public void restore() {
                        index = i;
                        charIndex = ci;
                    }
                };
            }

            // The forward iterator -------------------------------

            @Override
            public int nextInt() {
                if (charIndex < charEnd) {
                    char c = s.charAt(charIndex++);
                    index++;
                    if (Character.isHighSurrogate(c)
                            && charIndex < charEnd) {
                        // Expect a low surrogate
                        char d = s.charAt(charIndex);
                        if (Character.isLowSurrogate(d)) {
                            charIndex++;
                            return Character.toCodePoint(c, d);
                        }
                    }
                    return c;
                } else
                    throw new NoSuchElementException();
            }

            @Override
            public int nextInt(int n) {
                assert n >= 0;
                int i = index + n, indexSaved = index,
                        charIndexSaved = charIndex;
                while (hasNext()) {
                    int c = nextInt();
                    if (index == i) { return c; }
                }
                index = indexSaved;
                charIndex = charIndexSaved;
                throw noSuchElement(i);
            }

            @Override
            public int nextRangeIndex() { return index; }

            // The reverse iterator -------------------------------

            @Override
            public int previousInt() {
                if (charIndex > charStart) {
                    --index;
                    char d = s.charAt(--charIndex);
                    if (Character.isLowSurrogate(d)
                            && charIndex > charStart) {
                        // Expect a low surrogate
                        char c = s.charAt(--charIndex);
                        if (Character.isHighSurrogate(c)) {
                            return Character.toCodePoint(c, d);
                        }
                        charIndex++;
                    }
                    return d;
                } else
                    throw new NoSuchElementException();
            }

            @Override
            public int previousInt(int n) {
                assert n >= 0;
                int i = index - n, indexSaved = index,
                        charIndexSaved = charIndex;
                while (hasPrevious()) {
                    int c = previousInt();
                    if (index == i) { return c; }
                }
                index = indexSaved;
                charIndex = charIndexSaved;
                throw noSuchElement(i);
            }

            @Override
            public int previousRangeIndex() {
                int c = s.codePointBefore(charIndex);
                return charIndex
                        - (Character.isBmpCodePoint(c) ? 1 : 2);
            }

            // Diagnostic use -------------------------------------

            @Override
            public String toString() {
                return String.format("[%s|%s]",
                        s.substring(charStart, charIndex),
                        s.substring(charIndex, charEnd));
            }
        }
    }

    /**
     * A class to act as the delegate implementing {@code __getitem__}
     * and other index-related operations. By inheriting {@link Delegate
     * PySequence.Delegate} in this inner class, we obtain boilerplate
     * implementation code for slice translation and range checks. We
     * need only specify the work specific to {@link PyUnicode}
     * instances.
     */
    class UnicodeDelegate extends CodepointDelegate {

        @Override
        public int length() { return value.length; }

        @Override
        public PyType getType() { return TYPE; }

        @Override
        public String getTypeName() { return "string"; }

        @Override
        Object principal() { return PyUnicode.this; }

        @Override
        public Object getItem(int i) {
            return PyUnicode.fromCodePoint(value[i]);
        }

        @Override
        public Object getSlice(Indices slice) {
            int[] v;
            if (slice.step == 1)
                v = Arrays.copyOfRange(value, slice.start, slice.stop);
            else {
                v = new int[slice.slicelength];
                int i = slice.start;
                for (int j = 0; j < slice.slicelength; j++) {
                    v[j] = value[i];
                    i += slice.step;
                }
            }
            return new PyUnicode(TYPE, true, v);
        }

        @Override
        int[] getRange(int start, int end) {
            // start and end are array indices
            return Arrays.copyOfRange(value, start, end);
        }

        @Override
        Object add(Object ow)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ow instanceof PyUnicode) {
                // Optimisation (or is it?) over concatUnicode
                PyUnicode w = (PyUnicode)ow;
                int L = value.length, M = w.value.length;
                int[] r = new int[L + M];
                System.arraycopy(value, 0, r, 0, L);
                System.arraycopy(w.value, 0, r, L, M);
                return new PyUnicode(TYPE, true, r);
            } else {
                return concatUnicode(asIntStream(),
                        adapt(ow).asIntStream());
            }
        }

        @Override
        Object radd(Object ov)
                throws OutOfMemoryError, NoConversion, Throwable {
            if (ov instanceof PyUnicode) {
                // Optimisation (or is it?) over concatUnicode
                PyUnicode v = (PyUnicode)ov;
                int L = v.value.length, M = value.length;
                int[] r = new int[L + M];
                System.arraycopy(v.value, 0, r, 0, L);
                System.arraycopy(value, 0, r, L, M);
                return new PyUnicode(TYPE, true, r);
            } else {
                return concatUnicode(adapt(ov).asIntStream(),
                        asIntStream());
            }
        }

        @Override
        Object repeat(int n) throws OutOfMemoryError, Throwable {
            int m = value.length;
            if (n == 0)
                return "";
            else if (n == 1 || m == 0)
                return PyUnicode.this;
            else {
                int[] b = new int[n * m];
                for (int i = 0, p = 0; i < n; i++, p += m) {
                    System.arraycopy(value, 0, b, p, m);
                }
                return new PyUnicode(TYPE, true, b);
            }
        }

        @Override
        public int
                compareTo(PySequence.Delegate<Integer, Object> other) {
            Iterator<Integer> ib = other.iterator();
            for (int a : value) {
                if (ib.hasNext()) {
                    int b = ib.next();
                    // if a != b, then we've found an answer
                    if (a > b)
                        return 1;
                    else if (a < b)
                        return -1;
                } else
                    // value has not run out, but other has. We win.
                    return 1;
            }
            /*
             * The sequences matched over the length of value. The other
             * is the winner if it still has elements. Otherwise its a
             * tie.
             */
            return ib.hasNext() ? -1 : 0;
        }

        // PySequence.OfInt interface --------------------------------

        @Override
        public Spliterator.OfInt spliterator() {
            final int flags = Spliterator.IMMUTABLE | Spliterator.SIZED
                    | Spliterator.ORDERED;
            return Spliterators.spliterator(value, flags);
        }

        @Override
        public IntStream asIntStream() {
            int flags = Spliterator.IMMUTABLE | Spliterator.SIZED;
            Spliterator.OfInt s =
                    Spliterators.spliterator(value, flags);
            return StreamSupport.intStream(s, false);
        }

        // ListIterator provision ------------------------------------

        @Override
        public CodepointIterator iterator(final int index, int start,
                int end) {
            return new UnicodeIterator(index, start, end);
        }

        /**
         * A {@code ListIterator} for use when the string in the
         * surrounding adapter instance contains only basic multilingual
         * plane characters or isolated surrogates.
         */
        class UnicodeIterator implements CodepointIterator {

            private int index;
            private final int start, end;

            UnicodeIterator(int index, int start, int end) {
                checkIndexRange(index, start, end, value.length);
                this.start = start;
                this.end = end;
                this.index = index;
            }

            @Override
            public Mark mark() {
                return new Mark() {
                    final int i = index;

                    @Override
                    public void restore() { index = i; }
                };
            }

            // The forward iterator -------------------------------

            @Override
            public boolean hasNext() { return index < value.length; }

            @Override
            public boolean hasNext(int n) {
                assert n >= 0;
                return index + n <= value.length;
            }

            @Override
            public int nextInt() {
                if (index < end)
                    return value[index++];
                else
                    throw noSuchElement(nextIndex());
            }

            @Override
            public int nextInt(int n) {
                assert n >= 0;
                int i = index + n;
                if (i <= end)
                    return value[(index = i) - 1];
                else
                    throw noSuchElement(i - start);
            }

            @Override
            public int nextIndex() { return index - start; }

            // The reverse iterator -------------------------------

            @Override
            public boolean hasPrevious() { return index > start; }

            @Override
            public boolean hasPrevious(int n) {
                assert n >= 0;
                return index - n >= 0;
            }

            @Override
            public int previousInt() {
                if (index > start)
                    return value[--index];
                else
                    throw noSuchElement(previousIndex());
            }

            @Override
            public int previousInt(int n) {
                assert n >= 0;
                int i = index - n;
                if (i >= start)
                    return value[index = i];
                else
                    throw noSuchElement(i);
            }

            @Override
            public int previousIndex() { return index - start - 1; }

            // Diagnostic use -------------------------------------

            @Override
            public String toString() {
                return String.format("[%s|%s]",
                        new String(value, start, index - start),
                        new String(value, index, end - index));
            }
        }
    }

    /**
     * Adapt a Python {@code str} to a sequence of Java {@code int}
     * values or throw an exception. If the method throws the special
     * exception {@link NoConversion}, the caller must catch it and deal
     * with it, perhaps by throwing a {@link TypeError}. A binary
     * operation will normally return {@link Py#NotImplemented} in that
     * case.
     * <p>
     * Note that implementing {@link PySequence.OfInt} is not enough,
     * which other types may, but be incompatible in Python.
     *
     * @param v to wrap or return
     * @return adapted to a sequence
     * @throws NoConversion if {@code v} is not a Python {@code str}
     */
    static CodepointDelegate adapt(Object v) throws NoConversion {
        // Check against supported types, most likely first
        if (v instanceof String)
            return new StringAdapter((String)v);
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).delegate;
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Short-cut {@link #adapt(Object)} when type statically known.
     *
     * @param v to wrap
     * @return new StringAdapter(v)
     */
    static StringAdapter adapt(String v) {
        return new StringAdapter(v);
    }

    /**
     * Short-cut {@link #adapt(Object)} when type statically known.
     *
     * @return the delegate for sequence operations on this {@code str}
     */
    UnicodeDelegate adapt() { return delegate; }

    /**
     * Adapt a Python {@code str}, as by {@link #adapt(Object)}, that is
     * intended as a substring to find, in {@code str.find()} or
     * {@code str.replace()},
     *
     * @param method in which encountered
     * @param sub alleged string
     * @return adapted to a sequence
     * @throws TypeError if {@code sub} cannot be wrapped as a delegate
     */
    static CodepointDelegate adaptSub(String method, Object sub)
            throws TypeError {
        try {
            return adapt(sub);
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError(method, "string to find",
                    "str", sub);
        }
    }

    /**
     * Adapt a Python {@code str} intended as a separator, as by
     * {@link #adapt(Object)}.
     *
     * @param method in which encountered
     * @param sep alleged separator
     * @return adapted to a sequence
     * @throws TypeError if {@code sep} cannot be wrapped as a delegate
     * @throws ValueError if {@code sep} is the empty string
     */
    static CodepointDelegate adaptSeparator(String method, Object sep)
            throws TypeError, ValueError {
        try {
            CodepointDelegate p = adapt(sep);
            if (p.length() == 0) {
                throw new ValueError("%s(): empty separator", method);
            }
            return p;
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError(method, "separator",
                    "str or None", sep);
        }
    }

    /**
     * Convert slice end indices to a {@link PySlice.Indices} object.
     *
     * @param s sequence being sliced
     * @param start first index included
     * @param end first index not included
     * @return indices of the slice
     * @throws TypeError if {@code start} or {@code end} cannot be
     *     considered an index
     */
    private static PySlice.Indices getSliceIndices(CodepointDelegate s,
            Object start, Object end) throws TypeError {
        try {
            return (new PySlice(start, end)).getIndices(s.length());
        } catch (PyException pye) {
            throw pye;
        } catch (Throwable t) {
            throw new InterpreterError(t, "non-python exception)");
        }
    }

    /**
     * Concatenate two {@code String} representations of {@code str}.
     * This method almost always calls {@code String.concat(v, w)} and
     * almost always returns a {@code String}. There is a delicate case
     * where {@code v} ends with a high surrogate and {@code w} starts
     * with a low surrogate. Simply concatenated, these merge into one
     * character. Only a {@code PyUnicode} properly represents the
     * result in that case.
     *
     * @param v first string to concatenate
     * @param w second string to concatenate
     * @return the concatenation {@code v + w}
     */
    private static Object concat(String v, String w)
            throws OutOfMemoryError {
        /*
         * Since we have to guard against empty strings, we may as well
         * take the optimisation these paths invite.
         */
        int vlen = v.length();
        if (vlen == 0)
            return w;
        else if (w.length() == 0)
            return v;
        else if (Character.isLowSurrogate(w.charAt(0))
                && Character.isHighSurrogate(v.charAt(vlen - 1)))
            // Only a PyUnicode properly represents the result
            return concatUnicode(v.codePoints(), w.codePoints());
        else {
            // Java String concatenation will do fine
            return v.concat(w);
        }
    }

    /**
     * Concatenate two streams of code points into a {@code PyUnicode}.
     *
     * @param v first string to concatenate
     * @param w second string to concatenate
     * @return the concatenation {@code v + w}
     * @throws OutOfMemoryError when the concatenated string is too long
     */
    private static PyUnicode concatUnicode(IntStream v, IntStream w)
            throws OutOfMemoryError {
        return new PyUnicode(TYPE, true,
                IntStream.concat(v, w).toArray());
    }

    /**
     * Accumulate {@code int} elements in an array, similar to
     * {@code StringBuilder}.
     */
    static class IntArrayBuilder {
        private static final int MINSIZE = 16;
        private static final int[] EMPTY = new int[0];
        private int[] value;
        private int len = 0;

        /** Create an empty buffer of a defined initial capacity. */
        IntArrayBuilder(int capacity) {
            value = new int[capacity];
        }

        /** Create an empty buffer of a default initial capacity. */
        IntArrayBuilder() {
            value = EMPTY;
        }

        /** The number of elements currently. */
        int length() {
            return len;
        }

        /**
         * An array of the elements in the buffer (not modified by
         * appends hereafter).
         */
        int[] value() {
            return len == value.length ? value
                    : Arrays.copyOf(value, len);
        }

        /** Ensure there is room for another {@code n} elements. */
        private void ensure(int n) {
            if (len + n > value.length) {
                int newSize = Math.max(value.length * 2, MINSIZE);
                int[] newValue = new int[newSize];
                System.arraycopy(value, 0, newValue, 0, len);
                value = newValue;
            }
        }

        /** Append one element. */
        IntArrayBuilder append(int v) {
            ensure(1);
            value[len++] = v;
            return this;
        }

        /** Append all the elements from a sequence. */
        IntArrayBuilder append(PySequence.OfInt seq) {
            ensure(seq.length());
            for (int v : seq) { value[len++] = v; }
            return this;
        }

        /**
         * Append up to the given number of elements from a sequence.
         */
        IntArrayBuilder append(PySequence.OfInt seq, int count) {
            return append(seq.iterator(), count);
        }

        /** Append all the elements available from an iterator. */
        IntArrayBuilder append(Iterator<Integer> iter) {
            while (iter.hasNext()) { append(iter.next()); }
            return this;
        }

        /**
         * Append up to the given number of elements available from an
         * iterator.
         */
        IntArrayBuilder append(Iterator<Integer> iter, int count) {
            ensure(count);
            while (--count >= 0 && iter.hasNext()) {
                value[len++] = iter.next();
            }
            return this;
        }

        /**
         * Provide the contents as a Python Unicode {@code str} and
         * reset the builder to empty. (This is a "destructive read".)
         */
        PyUnicode takeUnicode() {
            PyUnicode u;
            if (len == value.length) {
                // The array is exactly filled: use it without copy.
                u = new PyUnicode(TYPE, true, value);
                value = EMPTY;
            } else {
                // The array is partly filled: copy it and re-use it.
                int[] v = new int[len];
                System.arraycopy(value, 0, v, 0, len);
                u = new PyUnicode(TYPE, true, v);
            }
            len = 0;
            return u;
        }

        /**
         * Provide the contents as a Java {@code String}
         * (non-destructively).
         */
        @Override
        public String toString() { return new String(value, 0, len); }
    }

    /**
     * Accumulate {@code int} elements in an array from the end, a sort
     * of mirror image of {@link IntArrayBuilder}.
     */
    static class IntArrayReverseBuilder {
        private static final int MINSIZE = 16;
        private static final int[] EMPTY = new int[0];
        private int[] value;
        private int ptr = 0;

        /** Create an empty buffer of a defined initial capacity. */
        IntArrayReverseBuilder(int capacity) {
            value = new int[capacity];
            ptr = value.length;
        }

        /** Create an empty buffer of a default initial capacity. */
        IntArrayReverseBuilder() {
            value = EMPTY;
            ptr = value.length;
        }

        /** The number of elements currently. */
        int length() {
            return value.length - ptr;
        }

        /**
         * An array of the elements in the buffer (not modified by
         * appends hereafter).
         */
        int[] value() {
            return ptr == 0 ? value
                    : Arrays.copyOfRange(value, ptr, value.length);
        }

        /** Ensure there is room for another {@code n} elements. */
        private void ensure(int n) {
            if (n > ptr) {
                int len = value.length - ptr;
                int newSize = Math.max(value.length * 2, MINSIZE);
                int newPtr = newSize - len;
                int[] newValue = new int[newSize];
                System.arraycopy(value, ptr, newValue, newPtr, len);
                value = newValue;
                ptr = newPtr;
            }
        }

        /** Prepend one element. */
        IntArrayReverseBuilder prepend(int v) {
            ensure(1);
            value[--ptr] = v;
            return this;
        }

        /** Prepend all the elements from a sequence. */
        IntArrayReverseBuilder prepend(CodepointDelegate seq) {
            return prepend(seq.iteratorLast(), seq.length());
        }

        /**
         * Prepend up to the given number of elements from the end of a
         * sequence.
         */
        IntArrayReverseBuilder prepend(CodepointDelegate seq,
                int count) {
            return prepend(seq.iteratorLast(), count);
        }

        /**
         * Prepend all the elements available from an iterator, working
         * backwards with {@code iter.previous()}.
         */
        IntArrayReverseBuilder prepend(ListIterator<Integer> iter) {
            while (iter.hasPrevious()) { prepend(iter.previous()); }
            return this;
        }

        /**
         * Prepend up to the given number of elements available from an
         * iterator, working backwards with {@code iter.previous()}.
         */
        IntArrayReverseBuilder prepend(ListIterator<Integer> iter,
                int count) {
            ensure(count);
            while (--count >= 0 && iter.hasPrevious()) {
                value[--ptr] = iter.previous();
            }
            return this;
        }

        /**
         * Provide the contents as a Python Unicode {@code str} and
         * reset the builder to empty. (This is a "destructive read".)
         */
        PyUnicode takeUnicode() {
            PyUnicode u;
            if (ptr == 0) {
                // The array is exactly filled: use it without copy.
                u = new PyUnicode(TYPE, true, value);
                value = EMPTY;
            } else {
                // The array is partly filled: copy it and re-use it.
                int[] v = new int[value.length - ptr];
                System.arraycopy(value, ptr, v, 0, v.length);
                u = new PyUnicode(TYPE, true, v);
            }
            ptr = value.length;
            return u;
        }

        /**
         * Provide the contents as a Java {@code String}
         * (non-destructively).
         */
        @Override
        public String toString() {
            return new String(value, ptr, value.length - ptr);
        }
    }

    private static NoSuchElementException noSuchElement(int k) {
        return new NoSuchElementException(Integer.toString(k));
    }

    /**
     * Assert that <i>0 &le; start &le;index &le; end &le; len</i> or if
     * not, throw an exception.
     *
     * @param index e.g. the start position of na iterator.
     * @param start first in range
     * @param end first beyond range (i.e. non-inclusive bound)
     * @param len of sequence
     * @throws IndexOutOfBoundsException if the condition is violated
     */
    private static void checkIndexRange(int index, int start, int end,
            int len) throws IndexOutOfBoundsException {
        if ((0 <= start && start <= end && end <= len) == false)
            throw new IndexOutOfBoundsException(String.format(
                    "start=%d, end=%d, len=%d", start, end, len));
        else if (index < start)
            throw new IndexOutOfBoundsException("before start");
        else if (index > end)
            throw new IndexOutOfBoundsException("beyond end");
    }

    /**
     * A little helper for converting str.find to str.index that will
     * raise {@code ValueError("substring not found")} if the argument
     * is negative, otherwise passes the argument through.
     *
     * @param index to check
     * @return {@code index} if non-negative
     * @throws ValueError if argument is negative
     */
    private static final int checkIndexReturn(int index)
            throws ValueError {
        if (index >= 0) {
            return index;
        } else {
            throw new ValueError("substring not found");
        }
    }

    // Python sub-class -----------------------------------------------

    /**
     * Instances in Python of sub-classes of 'int', are represented in
     * Java by instances of this class.
     */
    static class Derived extends PyUnicode implements DictPyObject {

        protected Derived(PyType subType, String value) {
            super(subType, value);
        }

        protected Derived(PyType subType, int[] value) {
            super(subType, value);
        }

        /** The instance dictionary {@code __dict__}. */
        protected PyDict dict = new PyDict();

        @Override
        public Map<Object, Object> getDict() { return dict; }
    }
}
