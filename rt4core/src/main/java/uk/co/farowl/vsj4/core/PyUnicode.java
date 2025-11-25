// Copyright (c)2025 Jython Developers.
// Licensed to PSF under a contributor agreement.
package uk.co.farowl.vsj4.core;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.PrimitiveIterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.function.Function;
import java.util.function.IntUnaryOperator;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import uk.co.farowl.vsj4.core.PySequence.Delegate;
import uk.co.farowl.vsj4.core.PySlice.Indices;
import uk.co.farowl.vsj4.core.PyUtil.NoConversion;
import uk.co.farowl.vsj4.stringlib.IntArrayBuilder;
import uk.co.farowl.vsj4.stringlib.IntArrayReverseBuilder;
import uk.co.farowl.vsj4.support.InterpreterError;
import uk.co.farowl.vsj4.type.Exposed.Default;
import uk.co.farowl.vsj4.type.Exposed.Name;
import uk.co.farowl.vsj4.type.Exposed.PythonMethod;
import uk.co.farowl.vsj4.type.Feature;
import uk.co.farowl.vsj4.type.TypeSpec;
import uk.co.farowl.vsj4.type.WithClass;

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
public class PyUnicode implements WithClass, PyDict.Key {

    /** Only referenced during bootstrap by {@link TypeSystem}. */
    static class Spec {
        /** @return the type specification. */
        static TypeSpec get() {
            return new TypeSystem.BootstrapSpec("str",
                    MethodHandles.lookup(), PyUnicode.class)
                            .add(Feature.BASETYPE)
                            .methodImpls(PyUnicodeMethods.class)
                            .adopt(String.class);
        }
    }

    /** The Python type of {@code str} objects. */
    public static final PyType TYPE = TypeSystem.TYPE_str;

    /** Value as a Java {@code int} array of code points. */
    private final int[] value;

    /** Enumeration to express the code point {@link #range}. */
    enum Range {
        ASCII, LATIN, BMP, SMP;
    }

    /**
     * We can quickly determine whether short-cut encodings will be
     * possible for a given {@code PyUnicode} from this field.
     */
    final private Range range;

    /**
     * Helper to implement {@code __getitem__} and other index-related
     * operations.
     */
    private UnicodeAdapter delegate = new UnicodeAdapter();

    /**
     * Cached hash of the {@code str}, lazily computed in
     * {@link #hashCode()}. Zero if unknown, and nearly always unknown
     * if zero.
     */
    private int hash;

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str}, from a
     * given array of code points, with the option to re-use that array
     * as the implementation. If the actual array is is re-used the
     * caller must give up ownership and never modify it after the call.
     * See {@link #fromCodePoint(int)} for a correct use.
     *
     * @param iPromiseNotToModify if {@code true}, the array becomes the
     *     implementation array, otherwise the constructor takes a copy.
     * @param max known maximum code point (or {@code -1})
     * @param codePoints the array of code points
     */
    private PyUnicode(boolean iPromiseNotToModify, int max,
            int[] codePoints) {
        if (iPromiseNotToModify)
            this.value = codePoints;
        else
            this.value = Arrays.copyOf(codePoints, codePoints.length);
        this.range = findRange(max, codePoints);
    }

    /**
     * Categorise the range of code points in the string, based on a
     * maximum code point (if known)or inspection of the code point
     * array.
     *
     * @param max known maximum code point (or {@code -1})
     * @param codePoints array to inspect (if {@code max}&lt;0).
     * @return a categorisation of the range
     */
    private static Range findRange(int max, int[] codePoints) {
        if (max < 0) {
            for (int c : codePoints) { max = Math.max(max, c); }
        }
        if (max <= 0x7f) {
            return Range.ASCII;
        } else if (max <= 0x7fff) {
            return Range.BMP;
        } else {
            return Range.SMP;
        }
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from the given code points. The constructor takes a
     * copy.
     *
     * @param codePoints the array of code points
     */
    PyUnicode(int... codePoints) { this(false, -1, codePoints); }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given {@link IntArrayBuilder}. This will reset
     * the builder to empty.
     *
     * @param value from which to take the code points
     */
    PyUnicode(IntArrayBuilder value) {
        this(true, value.max(), value.take());
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str} or a
     * sub-class, from a given {@link IntArrayReverseBuilder}. This will
     * reset the builder to empty.
     *
     * @param value from which to take the code points
     */
    PyUnicode(IntArrayReverseBuilder value) {
        this(true, value.max(), value.take());
    }

    /**
     * Construct an instance of {@code PyUnicode}, a {@code str}, from a
     * given Java {@code String}. The constructor interprets surrogate
     * pairs as defining one code point. Lone surrogates are preserved
     * (e.g. for byte smuggling).
     *
     * @param value to have
     */
    PyUnicode(String value) {
        this((new IntArrayBuilder()).append(value.codePoints()));
    }

    // Factory methods ------------------------------------------------
    // These may return a Java String or a PyUnicode

    /**
     * Unsafely wrap an array of code points as a {@code PyUnicode}. The
     * caller must not hold a reference to the argument array (and
     * definitely not manipulate the contents).
     *
     * @param codePoints to wrap as a {@code str}
     * @return the {@code str}
     */
    private static PyUnicode wrap(int[] codePoints) {
        return new PyUnicode(true, -1, codePoints);
    }

    /**
     * Safely wrap the contents of an {@link IntArrayBuilder} of code
     * points as a {@code PyUnicode}.
     *
     * @param codePoints to wrap as a {@code str}
     * @return the {@code str}
     */
    public static PyUnicode wrap(IntArrayBuilder codePoints) {
        return new PyUnicode(codePoints);
    }

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
            return wrap(new int[] {cp});
    }

    /**
     * Return a Python {@code str} representing the same sequence of
     * characters as the given Java {@code String} and implemented as a
     * {@code PyUnicode}.
     *
     * @param s to convert
     * @return a Python {@code str}
     */
    public static PyUnicode fromJavaString(String s) {
        // XXX share simple cases len==0 len==1 & ascii?
        return new PyUnicode(s);
    }

    @Override
    public PyType getType() { return TYPE; }

    // Special methods -----------------------------------------------

    @SuppressWarnings("unused")
    Object __str__() { return this; }

    @SuppressWarnings("unused")
    static Object __str__(String self) { return self; }

    @SuppressWarnings("unused")
    static Object __repr__(Object self) {
        try {
            // Ok, it should be more complicated but I'm in a hurry.
            return "'" + convertToString(self) + "'";
        } catch (NoConversion nc) {
            throw Abstract.impossibleArgumentError("str", self);
        }
    }

    /**
     * The length (in Python characters) of this {@code str}.
     *
     * @return length
     */
    int __len__() { return value.length; }

    @SuppressWarnings("unused")
    static int __len__(String self) {
        return self.codePointCount(0, self.length());
    }

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @SuppressWarnings("unused")
    int __hash__() {
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
    static int __hash__(String self) { return self.hashCode(); }

    @SuppressWarnings("unused")
    Object __getitem__(Object item) throws Throwable {
        return delegate.__getitem__(item);
    }

    @SuppressWarnings("unused")
    static Object __getitem__(String self, Object item)
            throws Throwable {
        StringAdapter delegate = adapt(self);
        return delegate.__getitem__(item);
    }

    @SuppressWarnings("unused")
    boolean __contains__(Object o) { return contains(delegate, o); }

    @SuppressWarnings("unused")
    static boolean __contains__(String self, Object o) {
        return contains(adapt(self), o);
    }

    private static boolean contains(CodepointDelegate s, Object o) {
        try {
            CodepointDelegate p = adapt(o);
            PySlice.Indices slice = getSliceIndices(s, null, null);
            return find(s, p, slice) >= 0;
        } catch (NoConversion nc) {
            throw Abstract.typeError(IN_STRING_TYPE, o);
        }
    }

    private static final String IN_STRING_TYPE =
            "'in <string>' requires string as left operand, not %s";

    @SuppressWarnings("unused")
    Object __add__(Object w) throws Throwable {
        return delegate.__add__(w);
    }

    @SuppressWarnings("unused")
    static Object __add__(String v, Object w) throws Throwable {
        return adapt(v).__add__(w);
    }

    @SuppressWarnings("unused")
    Object __radd__(Object v) throws Throwable {
        return delegate.__radd__(v);
    }

    @SuppressWarnings("unused")
    static Object __radd__(String w, Object v) throws Throwable {
        return adapt(w).__radd__(v);
    }

    Object __mul__(Object n) throws Throwable {
        return delegate.__mul__(n);
    }

    static Object __mul__(String self, Object n) throws Throwable {
        return adapt(self).__mul__(n);
    }

    @SuppressWarnings("unused")
    Object __rmul__(Object n) throws Throwable { return __mul__(n); }

    @SuppressWarnings("unused")
    static Object __rmul__(String self, Object n) throws Throwable {
        return __mul__(self, n);
    }

    @SuppressWarnings("unused")
    Object __iter__() { return new PyStrIterator(delegate); }

    @SuppressWarnings("unused")
    static Object __iter__(String self) {
        return new PyStrIterator(adapt(self));
    }

    // Strip methods --------------------------------------------------

    /**
     * Python {@code str.strip()}. Any character matching one of those
     * in {@code chars} will be discarded from either end of this
     * {@code str}. If {@code chars == None}, whitespace will be
     * stripped.
     *
     * @param chars characters to strip from either end of this
     *     {@code str}, or {@code None}
     * @return a new {@code str}, stripped of the specified characters
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    @PythonMethod(primary = false)
    Object strip(Object chars) throws PyBaseException {
        return strip(delegate, chars);
    }

    @PythonMethod
    static Object strip(String self, @Default("None") Object chars)
            throws PyBaseException {
        return strip(adapt(self), chars);
    }

    /**
     * Inner implementation of Python {@code str.strip()} independent of
     * the implementation type.
     *
     * @param s representing {@code self}
     * @param chars to remove, or {@code null} or {@code None}
     * @return the {@code str} stripped
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    private static Object strip(CodepointDelegate s, Object chars)
            throws PyBaseException {
        Set<Integer> p = adaptStripSet("strip", chars);
        int left, right;
        if (p == null) {
            // Stripping spaces
            right = findRight(s);
            // If it's all spaces, we know left==0
            left = right < 0 ? 0 : findLeft(s);
        } else {
            // Stripping specified characters
            right = findRight(s, p);
            // If it all matches, we know left==0
            left = right < 0 ? 0 : findLeft(s, p);
        }
        /*
         * Substring from leftmost non-matching character up to and
         * including the rightmost (or "")
         */
        PySlice.Indices slice = getSliceIndices(s, left, right + 1);
        return slice.slicelength == 0 ? "" : s.getSlice(slice);
    }

    /**
     * Helper for {@code strip}, {@code lstrip} implementation, when
     * stripping space.
     *
     * @return index of leftmost non-space character or
     *     {@code s.length()} if entirely spaces.
     */
    private static int findLeft(CodepointDelegate s) {
        CodepointIterator si = s.iterator(0);
        while (si.hasNext()) {
            if (!isPythonSpace(si.nextInt()))
                return si.previousIndex();
        }
        return s.length();
    }

    /**
     * Helper for {@code strip}, {@code lstrip} implementation, when
     * stripping specified characters.
     *
     * @param p specifies set of characters to strip
     * @return index of leftmost non-{@code p} character or
     *     {@code s.length()} if entirely found in {@code p}.
     */
    private static int findLeft(CodepointDelegate s, Set<Integer> p) {
        CodepointIterator si = s.iterator(0);
        while (si.hasNext()) {
            if (!p.contains(si.nextInt()))
                return si.previousIndex();
        }
        return s.length();
    }

    /**
     * Helper for {@code strip}, {@code rstrip} implementation, when
     * stripping space.
     *
     * @return index of rightmost non-space character or {@code -1} if
     *     entirely spaces.
     */
    private static int findRight(CodepointDelegate s) {
        CodepointIterator si = s.iteratorLast();
        while (si.hasPrevious()) {
            if (!isPythonSpace(si.previousInt()))
                return si.nextIndex();
        }
        return -1;
    }

    /**
     * Helper for {@code strip}, {@code rstrip} implementation, when
     * stripping specified characters.
     *
     * @param p specifies set of characters to strip
     * @return index of rightmost non-{@code p} character or {@code -1}
     *     if entirely found in {@code p}.
     */
    private static int findRight(CodepointDelegate s, Set<Integer> p) {
        CodepointIterator si = s.iteratorLast();
        while (si.hasPrevious()) {
            if (!p.contains(si.previousInt()))
                return si.nextIndex();
        }
        return -1;
    }

    /**
     * Python {@code str.lstrip()}. Any character matching one of those
     * in {@code chars} will be discarded from the left of this
     * {@code str}. If {@code chars == None}, whitespace will be
     * stripped.
     *
     * @param chars characters to strip from this {@code str}, or
     *     {@code None}
     * @return a new {@code str}, left-stripped of the specified
     *     characters
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    @PythonMethod(primary = false)
    Object lstrip(Object chars) throws PyBaseException {
        return lstrip(delegate, chars);
    }

    @PythonMethod
    static Object lstrip(String self, @Default("None") Object chars)
            throws PyBaseException {
        return lstrip(adapt(self), chars);
    }

    /**
     * Inner implementation of Python {@code str.lstrip()} independent
     * of the implementation type.
     *
     * @param s representing {@code self}
     * @param chars to remove, or {@code null} or {@code None}
     * @return the str stripped
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    private static Object lstrip(CodepointDelegate s, Object chars)
            throws PyBaseException {
        Set<Integer> p = adaptStripSet("lstrip", chars);
        int left;
        if (p == null) {
            // Stripping spaces
            left = findLeft(s);
        } else {
            // Stripping specified characters
            left = findLeft(s, p);
        }
        /*
         * Substring from this leftmost non-matching character (or "")
         */
        PySlice.Indices slice = getSliceIndices(s, left, null);
        return s.getSlice(slice);
    }

    /**
     * Python {@code str.rstrip()}. Any character matching one of those
     * in {@code chars} will be discarded from the right of this
     * {@code str}. If {@code chars == None}, whitespace will be
     * stripped.
     *
     * @param chars characters to strip from this {@code str}, or
     *     {@code None}
     * @return a new {@code str}, right-stripped of the specified
     *     characters
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    @PythonMethod(primary = false)
    Object rstrip(Object chars) throws PyBaseException {
        return rstrip(delegate, chars);
    }

    @PythonMethod
    static Object rstrip(String self, @Default("None") Object chars)
            throws PyBaseException {
        return rstrip(adapt(self), chars);
    }

    /**
     * Inner implementation of Python {@code str.rstrip()} independent
     * of the implementation type.
     *
     * @param s representing {@code self}
     * @param chars to remove, or {@code null} or {@code None}
     * @return the str stripped
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code chars} type errors
     */
    private static Object rstrip(CodepointDelegate s, Object chars)
            throws PyBaseException {
        Set<Integer> p = adaptStripSet("rstrip", chars);
        int right;
        if (p == null) {
            // Stripping spaces
            right = findRight(s);
        } else {
            // Stripping specified characters
            right = findRight(s, p);
        }
        /*
         * Substring up to and including this rightmost non-matching
         * character (or "")
         */
        PySlice.Indices slice = getSliceIndices(s, null, right + 1);
        return s.getSlice(slice);
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
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code sub} type errors
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
                    Object before = new PyUnicode(buffer);
                    // Now consume (the known length) after the match.
                    buffer = new IntArrayBuilder(lastPos - pos + 1);
                    buffer.append(si);
                    Object after = new PyUnicode(buffer);
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
                    Object after = new PyUnicode(buffer);
                    // Now consume (the known length) before the match.
                    buffer = new IntArrayReverseBuilder(si.nextIndex());
                    buffer.prepend(si);
                    Object before = new PyUnicode(buffer);
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
    // split(self, /, sep=None, maxsplit=-1)
    @PythonMethod(positionalOnly = false)
    PyList split(@Default("None") Object sep,
            @Default("-1") int maxsplit) {
        return split(delegate, sep, maxsplit);
    }

    @PythonMethod(primary = false)
    static PyList split(String self, Object sep, int maxsplit) {
        return split(adapt(self), sep, maxsplit);
    }

    private static PyList split(CodepointDelegate s, Object sep,
            int maxsplit) {
        if (sep == null || sep == Py.None) {
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
                list.add(new PyUnicode(segment));
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
                     * We reached the end of p: it's a match. Emit the
                     * segment we have been accumulating, start a new
                     * one, and count a split.
                     */
                    list.add(new PyUnicode(segment));
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
        list.add(new PyUnicode(segment));
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
    PyList rsplit(@Default("None") Object sep,
            @Default("-1") int maxsplit) {
        return rsplit(delegate, sep, maxsplit);
    }

    @PythonMethod(primary = false)
    static PyList rsplit(String self, Object sep, int maxsplit) {
        return rsplit(adapt(self), sep, maxsplit);
    }

    private static PyList rsplit(CodepointDelegate s, Object sep,
            int maxsplit) {
        if (sep == null || sep == Py.None) {
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
                list.add(new PyUnicode(segment));
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
                     * We reached the start of p: it's a match. Emit the
                     * segment we have been accumulating, start a new
                     * one, and count a split.
                     */
                    list.add(new PyUnicode(segment));
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
        list.add(new PyUnicode(segment));
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
                list.add(new PyUnicode(line));

            } else {
                // c is part of the current line.
                line.append(c);
            }
        }

        /*
         * Add the segment we were building when s ran out, but not if
         * it is empty.
         */
        if (line.length() > 0) { list.add(new PyUnicode(line)); }

        return list;
    }

    /**
     * As {@link #find(Object, Object, Object)}, but throws
     * {@link PyBaseException ValueError} if the substring is not found.
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     * @throws PyBaseException ({@link PyExc#ValueError ValueError}) if
     *     {@code sub} is not found
     */
    @PythonMethod
    int index(Object sub, Object start, Object end)
            throws PyBaseException {
        return checkIndexReturn(find(delegate, sub, start, end));
    }

    @PythonMethod(primary = false)
    static int index(String self, Object sub, Object start,
            Object end) {
        return checkIndexReturn(find(adapt(self), sub, start, end));
    }

    /**
     * As {@link #rfind(Object, Object, Object)}, but throws
     * {@link PyBaseException ValueError} if the substring is not found.
     *
     * @param sub substring to find.
     * @param start start of slice.
     * @param end end of slice.
     * @return index of {@code sub} in this object or -1 if not found.
     * @throws PyBaseException ({@link PyExc#ValueError ValueError}) if
     *     {@code sub} is not found
     */
    @PythonMethod
    int rindex(Object sub, Object start, Object end)
            throws PyBaseException {
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
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code sub} type errors
     */
    @PythonMethod
    int count(Object sub, Object start, Object end)
            throws PyBaseException {
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
            return slice.slicelength + 1;
        else
            return count(s, p, slice);
    }

    /**
     * The inner implementation of {@code str.count}, returning the
     * number of occurrences of a substring. It accepts slice-like
     * arguments, which may be {@code None} or end-relative (negative).
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

    /**
     * Python {@code str.replace(old, new[, count])}, returning a copy
     * of the string with all occurrences of substring {@code old}
     * replaced by {@code rep}. If argument {@code count} is
     * nonnegative, only the first {@code count} occurrences are
     * replaced.
     *
     * @param old to replace where found.
     * @param rep replacement text.
     * @param count maximum number of replacements to make, or -1
     *     meaning all of them.
     * @return {@code self} string after replacements.
     */
    @PythonMethod
    Object replace(Object old, @Name("new") Object rep,
            @Default("-1") int count) {
        return replace(delegate, old, rep, count);
    }

    @PythonMethod(primary = false)
    static Object replace(String self, Object old, Object rep,
            int count) {
        return replace(adapt(self), old, rep, count);
    }

    private static Object replace(CodepointDelegate s, Object old,
            Object rep, int count) {
        // Convert arguments to their delegates or error
        CodepointDelegate p = adaptSub("replace", old);
        CodepointDelegate n = adaptRep("replace", rep);
        if (p.length() == 0) {
            return replace(s, n, count);
        } else {
            return replace(s, p, n, count);
        }
    }

    /**
     * Implementation of Python {@code str.replace} in the case where
     * the substring to find has zero length. This must result in the
     * insertion of the replacement string at the start if the result
     * and after every character copied from s, up to the limit imposed
     * by {@code count}. For example {@code 'hello'.replace('', '-')}
     * returns {@code '-h-e-l-l-o-'}. This is {@code N+1} replacements,
     * where {@code N = s.length()}, or as limited by {@code count}.
     *
     * @param s delegate presenting self as code points
     * @param r delegate representing the replacement string
     * @param count limit on the number of replacements
     * @return string interleaved with the replacement
     */
    private static Object replace(CodepointDelegate s,
            CodepointDelegate r, int count) {

        // -1 means make all replacements, which is exactly:
        if (count < 0) {
            count = s.length() + 1;
        } else if (count == 0) {
            // Zero replacements: short-cut return the original
            return s.principal();
        }

        CodepointIterator si = s.iterator(0);

        // The result will be this size exactly
        // 'hello'.replace('', '-', 3) == '-h-e-llo'
        IntArrayBuilder result =
                new IntArrayBuilder(s.length() + r.length() * count);

        // Start with the a copy of the replacement
        result.append(r);

        // Put another copy of after each of count-1 characters of s
        for (int i = 1; i < count; i++) {
            assert si.hasNext();
            result.append(si.nextInt()).append(r);
        }

        // Now copy any remaining characters of s
        result.append(si);
        return new PyUnicode(result);
    }

    /**
     * Implementation of Python {@code str.replace} in the case where
     * the substring to find has non-zero length, up to the limit
     * imposed by {@code count}.
     *
     * @param s delegate presenting self as code points
     * @param p delegate representing the string to replace
     * @param r delegate representing the replacement string
     * @param count limit on the number of replacements
     * @return string with the replacements
     */
    private static Object replace(CodepointDelegate s,
            CodepointDelegate p, CodepointDelegate r, int count) {

        // -1 means make all replacements, but cannot exceed:
        if (count < 0) {
            count = s.length() + 1;
        } else if (count == 0) {
            // Zero replacements: short-cut return the original
            return s.principal();
        }

        /*
         * The structure of replace is a lot like that of split(), in
         * that we iterate over s, copying as we go. The difference is
         * the action we take upon encountering and instance of the
         * "needle" string, which here is to emit the replacement into
         * the result, rather than start a new segment.
         */

        // An iterator on p, the string sought.
        CodepointIterator pi = p.iterator(0);
        int pChar = pi.nextInt(), pLength = p.length();
        CodepointIterator.Mark pMark = pi.mark();
        assert pLength > 0;

        // An iterator on r, the replacement string.
        CodepointIterator ri = r.iterator(0);
        CodepointIterator.Mark rMark = ri.mark();

        // Counting in pos avoids hasNext() calls.
        int pos = 0, lastPos = s.length() - pLength, sChar;

        // An iterator on s, the string being searched.
        CodepointIterator si = s.iterator(pos);

        // Result built here
        IntArrayBuilder result = new IntArrayBuilder();

        while (si.hasNext()) {

            if (pos++ > lastPos || count <= 0) {
                /*
                 * We are too close to the end for a match now, or we
                 * have run out of permission to make (according to
                 * count==0). Everything that is left may be added to
                 * the result.
                 */
                result.append(si);

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
                     * We reached the end of p: it's a match. Emit the
                     * replacement string to the result and lose a life.
                     */
                    result.append(ri);
                    rMark.restore();
                    --count;
                    // Catch pos up with si (matches do not overlap).
                    pos = si.nextIndex();
                } else {
                    /*
                     * We stopped on a mismatch: reset si to pos. The
                     * character that matched pChar is part of the
                     * result.
                     */
                    sPos.restore();
                    result.append(sChar);
                }
                // In either case, reset pi to p[1].
                pMark.restore();

            } else {
                /*
                 * The character that wasn't part of a match with p is
                 * part of the result.
                 */
                result.append(sChar);
            }
        }

        return new PyUnicode(result);
    }

    // Transformation methods -----------------------------------------

    /*
     * We group here methods that are simple transformation functions of
     * the string, based on tests of character properties, for example
     * str.strip() and str.title().
     */

    @PythonMethod
    PyUnicode lower() { return mapChars(Character::toLowerCase); }

    @PythonMethod(primary = false)
    static String lower(String self) {
        return mapChars(self, Character::toLowerCase);
    }

    @PythonMethod
    PyUnicode upper() { return mapChars(Character::toUpperCase); }

    @PythonMethod(primary = false)
    static String upper(String self) {
        return mapChars(self, Character::toUpperCase);
    }

    @PythonMethod
    PyUnicode title() { return title(delegate); }

    @PythonMethod(primary = false)
    static PyUnicode title(String self) { return title(adapt(self)); }

    private static PyUnicode title(PySequence.OfInt s) {
        IntArrayBuilder buffer = new IntArrayBuilder(s.length());
        boolean previousCased = false;
        for (int c : s) {
            if (previousCased) {
                buffer.append(Character.toLowerCase(c));
            } else {
                buffer.append(Character.toTitleCase(c));
            }
            previousCased =
                    Character.isLowerCase(c) || Character.isUpperCase(c)
                            || Character.isTitleCase(c);
        }
        return new PyUnicode(buffer);
    }

    @PythonMethod
    PyUnicode swapcase() { return mapChars(PyUnicode::swapcase); }

    @PythonMethod(primary = false)
    static String swapcase(String self) {
        return mapChars(self, PyUnicode::swapcase);
    }

    private static int swapcase(int c) {
        if (Character.isUpperCase(c)) {
            return Character.toLowerCase(c);
        } else if (Character.isLowerCase(c)) {
            return Character.toUpperCase(c);
        } else {
            return c;
        }
    }

    @PythonMethod
    Object ljust(int width, @Default(" ") Object fillchar) {
        return pad(false, delegate, true, width,
                adaptFill("ljust", fillchar));
    }

    @PythonMethod(primary = false)
    static Object ljust(String self, int width, Object fillchar) {
        return pad(false, adapt(self), true, width,
                adaptFill("ljust", fillchar));
    }

    @PythonMethod
    Object rjust(int width, @Default(" ") Object fillchar) {
        return pad(true, delegate, false, width,
                adaptFill("rjust", fillchar));
    }

    @PythonMethod(primary = false)
    static Object rjust(String self, int width, Object fillchar) {
        return pad(true, adapt(self), false, width,
                adaptFill("rjust", fillchar));
    }

    @PythonMethod
    Object center(int width, @Default(" ") Object fillchar) {
        return pad(true, delegate, true, width,
                adaptFill("center", fillchar));
    }

    @PythonMethod(primary = false)
    static Object center(String self, int width, Object fillchar) {
        return pad(true, adapt(self), true, width,
                adaptFill("center", fillchar));
    }

    /**
     * Common code for {@link #ljust(int, Object) ljust},
     * {@link #rjust(int, Object) rjust} and {@link #center(int, Object)
     * center}.
     *
     * @param left whether to pad at the left
     * @param s the {@code self} string
     * @param right whether to pad at the right
     * @param width the minimum width to attain
     * @param fill the code point value to use as the fill
     * @return the padded string (or {@code s.principal()})
     */
    private static Object pad(boolean left, CodepointDelegate s,
            boolean right, int width, int fill) {
        // Work out how much (or whether) to pad at the left and right.
        int L = s.length(), pad = Math.max(width, L) - L;
        if (pad == 0) { return s.principal(); }

        // It suits us to assume all right padding to begin with.
        int leftPad = 0, rightPad = pad;
        if (left) {
            if (!right) {
                // It is all on the left
                leftPad = pad;
                rightPad = 0;
            } else {
                // But sometimes you have to be Dutch
                leftPad = pad / 2 + (pad & width & 1);
                rightPad = width - leftPad;
            }
        }

        // Now, use a builder to create the result
        IntArrayBuilder buf = new IntArrayBuilder(width);

        for (int i = 0; i < leftPad; i++) { buf.append(fill); }
        buf.append(s);
        for (int i = 0; i < rightPad; i++) { buf.append(fill); }
        return new PyUnicode(buf);
    }

    @PythonMethod
    Object zfill(int width) { return zfill(delegate, width); }

    @PythonMethod(primary = false)
    static Object zfill(String self, int width) {
        return zfill(adapt(self), width);
    }

    /**
     * Inner implementation of {@link #zfill(int) zfill}
     *
     * @param s the {@code self} string
     * @param width the achieve by inserting zeros
     * @return the filled string
     */
    private static Object zfill(CodepointDelegate s, int width) {
        // Work out how much to pad.
        int L = s.length(), pad = Math.max(width, L) - L;
        if (pad == 0) { return s.principal(); }

        // Now, use a builder to create the result of the padded width
        IntArrayBuilder buf = new IntArrayBuilder(width);
        CodepointIterator si = s.iterator(0);

        // Possible sign goes first
        if (si.hasNext()) {
            int c = si.nextInt();
            if (c == '+' || c == '-') {
                buf.append(c);
            } else {
                si.previousInt();
            }
        }

        // Now the computed number of zeros
        for (int i = 0; i < pad; i++) { buf.append('0'); }
        buf.append(si);
        return new PyUnicode(buf);
    }

    @PythonMethod
    Object expandtabs(@Default("8") int tabsize) {
        return expandtabs(delegate, tabsize);
    }

    @PythonMethod(primary = false)
    static Object expandtabs(String self, int tabsize) {
        return expandtabs(adapt(self), tabsize);
    }

    /**
     * Inner implementation of {@link #expandtabs() expandtabs}
     *
     * @param s the {@code self} string
     * @param tabsize number of spaces to tab to
     * @return tab-expanded string
     */
    private static Object expandtabs(CodepointDelegate s, int tabsize) {
        // Build the result in buf. It can be multi-line.
        IntArrayBuilder buf = new IntArrayBuilder(s.length());
        // Iterate through s, keeping track of position on line.
        CodepointIterator si = s.iterator(0);
        int pos = 0;
        while (si.hasNext()) {
            int c = si.nextInt();
            if (c == '\t') {
                int spaces = tabsize - pos % tabsize;
                while (spaces-- > 0) { buf.append(' '); }
                pos += spaces;
            } else {
                if (c == '\n' || c == '\r') { pos = -1; }
                buf.append(c);
                pos++;
            }
        }
        return new PyUnicode(buf);
    }

    @PythonMethod
    Object capitalize() { return capitalize(delegate); }

    @PythonMethod(primary = false)
    static Object capitalize(String self) {
        return capitalize(adapt(self));
    }

    /**
     * Inner implementation of {@link #capitalize() capitalize}
     *
     * @param s the {@code self} string
     * @return capitalised string
     */
    private static Object capitalize(CodepointDelegate s) {
        // Iterate through s
        CodepointIterator si = s.iterator(0);
        if (si.hasNext()) {
            // Build the result in buf.
            IntArrayBuilder buf = new IntArrayBuilder(s.length());
            // Uppercase the first character
            buf.append(Character.toUpperCase(si.nextInt()));
            // Lowercase the rest
            while (si.hasNext()) {
                buf.append(Character.toLowerCase(si.nextInt()));
            }
            return new PyUnicode(buf);
        } else {
            // String is empty
            return "";
        }
    }

    @PythonMethod
    Object join(Object iterable) throws PyBaseException, Throwable {
        return join(delegate, iterable);
    }

    @PythonMethod(primary = false)
    static Object join(String self, Object iterable)
            throws PyBaseException, Throwable {
        return join(adapt(self), iterable);
    }

    /**
     * Inner implementation of {@link #join() join}.
     *
     * @param s the {@code self} string (separator)
     * @param iterable of strings
     * @return capitalised string
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code iterable} isn't
     * @throws Throwable from errors iterating {@code iterable}
     */
    private static Object join(CodepointDelegate s, Object iterable)
            throws PyBaseException, Throwable {
        /*
         * The argument is supposed to be a Python iterable: present it
         * as a Java List.
         */
        List<Object> parts = PySequence.fastList(iterable,
                () -> Abstract.argumentTypeError("join", "", "iterable",
                        iterable));

        /*
         * It is safe assume L is constant since either seq is a
         * well-behaved built-in, or we made a copy.
         */
        final int L = parts.size();

        // If empty sequence, return ""
        if (L == 0) {
            return "";
        } else if (L == 1) {
            // One-element sequence: return that element (if a str).
            Object item = parts.get(0);
            if (TYPE.checkExact(item)) { return item; }
        }

        /*
         * There are at least two parts to join, or one and it isn't a
         * str exactly. Do a pre-pass to figure out the total amount of
         * space we'll need, and check that every element is str-like.
         */
        int sepLen = s.length();
        // Start with the length contributed for by L-1 separators
        long size = (L - 1) * sepLen;

        for (int i = 0; i < L; i++) {

            // Accumulate the length of the item according to type
            Object item = parts.get(i);
            if (item instanceof PyUnicode) {
                size += ((PyUnicode)item).__len__();
            } else if (item instanceof String) {
                /*
                 * If non-BMP, this will over-estimate. We assume this
                 * is preferable to counting characters properly.
                 */
                size += ((String)item).length();
            } else {
                // If neither, then it's not a str
                throw joinArgumentTypeError(item, i);
            }

            if (size > Integer.MAX_VALUE) {
                throw PyErr.format(PyExc.OverflowError,
                        "join() result is too long for a Python string");
            }
        }

        // Build the result here
        IntArrayBuilder buf = new IntArrayBuilder((int)size);

        // Concatenate the parts and separators
        for (int i = 0; i < L; i++) {
            // Separator
            if (i != 0) { append(buf, s); }
            // item from the iterable
            Object item = parts.get(i);
            try {
                append(buf, adapt(item));
            } catch (NoConversion e) {
                // This can't really happen here, given checks above
                throw joinArgumentTypeError(item, i);
            }
        }

        return new PyUnicode(buf);
    }

    private static PyBaseException joinArgumentTypeError(Object item,
            int i) {
        return PyErr.format(PyExc.TypeError,
                "sequence item %d: expected str, %.80s found", i,
                PyType.of(item).getName());
    }

    // Doc copied from PyString
    /**
     * Equivalent to the Python {@code str.startswith} method, testing
     * whether a string starts with a specified prefix, where a
     * sub-range is specified by {@code [start:end]}. Arguments
     * {@code start} and {@code end} are interpreted as in slice
     * notation, with null or {@link Py#None} representing "missing".
     * {@code prefix} can also be a tuple of prefixes to look for.
     *
     * @param prefix string to check for (or a {@code PyTuple} of them).
     * @param start start of slice.
     * @param end end of slice.
     * @return {@code true} if this string slice starts with a specified
     *     prefix, otherwise {@code false}.
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code prefix} type errors
     */
    @PythonMethod
    Object startswith(Object prefix, Object start, Object end)
            throws PyBaseException {
        return startswith(delegate, prefix, start, end);
    }

    @PythonMethod(primary = false)
    static Object startswith(String self, Object prefix, Object start,
            Object end) {
        return startswith(adapt(self), prefix, start, end);
    }

    private static boolean startswith(CodepointDelegate s,
            Object prefixObj, Object start, Object end) {

        PySlice.Indices slice = getSliceIndices(s, start, end);

        if (prefixObj instanceof PyTuple) {
            /*
             * Loop will return true if this slice starts with any
             * prefix in the tuple
             */
            for (Object prefix : (PyTuple)prefixObj) {
                // It ought to be a str.
                CodepointDelegate p = adaptSub("startswith", prefix);
                if (startswith(s, p, slice)) { return true; }
            }
            // None matched
            return false;
        } else {
            // It ought to be a str.
            CodepointDelegate p = adaptSub("startswith", prefixObj);
            return startswith(s, p, slice);
        }
    }

    private static boolean startswith(CodepointDelegate s,
            CodepointDelegate p, PySlice.Indices slice) {
        // If p is too long, it can't start s
        if (p.length() > s.length()) { return false; }
        CodepointIterator si = s.iterator(0, slice.start, slice.stop);
        CodepointIterator pi = p.iterator(0);
        // We know that p is no longer than s so only count in p
        while (pi.hasNext()) {
            if (pi.nextInt() != si.nextInt()) { return false; }
        }
        return true;
    }

    // Doc copied from PyString
    /**
     * Equivalent to the Python {@code str.endswith} method, testing
     * whether a string ends with a specified suffix, where a sub-range
     * is specified by {@code [start:end]}. Arguments {@code start} and
     * {@code end} are interpreted as in slice notation, with null or
     * {@link Py#None} representing "missing". {@code suffix} can also
     * be a tuple of suffixes to look for.
     *
     * @param suffix string to check for (or a {@code PyTuple} of them).
     * @param start start of slice.
     * @param end end of slice.
     * @return {@code true} if this string slice ends with a specified
     *     suffix, otherwise {@code false}.
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) on
     *     {@code suffix} type errors
     */
    @PythonMethod
    Object endswith(Object suffix, Object start, Object end)
            throws PyBaseException {
        return endswith(delegate, suffix, start, end);
    }

    @PythonMethod(primary = false)
    static Object endswith(String self, Object suffix, Object start,
            Object end) {
        return endswith(adapt(self), suffix, start, end);
    }

    private static boolean endswith(CodepointDelegate s,
            Object suffixObj, Object start, Object end) {

        PySlice.Indices slice = getSliceIndices(s, start, end);

        if (suffixObj instanceof PyTuple) {
            /*
             * Loop will return true if this slice ends with any prefix
             * in the tuple
             */
            for (Object prefix : (PyTuple)suffixObj) {
                // It ought to be a str.
                CodepointDelegate p = adaptSub("endswith", prefix);
                if (endswith(s, p, slice)) { return true; }
            }
            // None matched
            return false;
        } else {
            // It ought to be a str.
            CodepointDelegate p = adaptSub("endswith", suffixObj);
            return endswith(s, p, slice);
        }
    }

    private static boolean endswith(CodepointDelegate s,
            CodepointDelegate p, PySlice.Indices slice) {
        // If p is too long, it can't end s
        if (p.length() > s.length()) { return false; }
        CodepointIterator si =
                s.iterator(slice.stop, slice.start, slice.stop);
        CodepointIterator pi = p.iteratorLast();
        // We know that p is no longer than s so only count in p
        while (pi.hasPrevious()) {
            if (pi.previousInt() != si.previousInt()) { return false; }
        }
        return true;
    }

    // Predicate methods ----------------------------------------------

    /*
     * We group here methods that are boolean functions of the string,
     * based on tests of character properties, for example
     * str.isascii(). They have a common pattern.
     */

    @PythonMethod
    boolean islower() { return islower(delegate); }

    @PythonMethod(primary = false)
    static boolean islower(String s) { return islower(adapt(s)); }

    private static boolean islower(PySequence.OfInt s) {
        boolean cased = false;
        for (int codepoint : s) {
            ;
            if (Character.isUpperCase(codepoint)
                    || Character.isTitleCase(codepoint)) {
                return false;
            } else if (!cased && Character.isLowerCase(codepoint)) {
                cased = true;
            }
        }
        return cased;
    }

    @PythonMethod
    final boolean isupper() { return isupper(delegate); }

    @PythonMethod(primary = false)
    static boolean isupper(String s) { return isupper(adapt(s)); }

    private static boolean isupper(PySequence.OfInt s) {
        boolean cased = false;
        for (int codepoint : s) {
            ;
            if (Character.isLowerCase(codepoint)
                    || Character.isTitleCase(codepoint)) {
                return false;
            } else if (!cased && Character.isUpperCase(codepoint)) {
                cased = true;
            }
        }
        return cased;
    }

    @PythonMethod
    final boolean isalpha() { return isalpha(delegate); }

    @PythonMethod(primary = false)
    static boolean isalpha(String s) { return isalpha(adapt(s)); }

    private static boolean isalpha(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            if (!Character.isLetter(codepoint)) { return false; }
        }
        return true;
    }

    @PythonMethod
    final boolean isalnum() { return isalnum(delegate); }

    @PythonMethod(primary = false)
    static boolean isalnum(String s) { return isalnum(adapt(s)); }

    private static boolean isalnum(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            if (!(Character.isLetterOrDigit(codepoint) || //
                    Character.getType(
                            codepoint) == Character.LETTER_NUMBER)) {
                return false;
            }
        }
        return true;
    }

    @PythonMethod(primary = false)
    boolean isascii() { return range == Range.ASCII; }

    @PythonMethod
    static boolean isascii(String self) {
        // We can test chars since any surrogate will fail.
        return self.chars().dropWhile(c -> c >>> 7 == 0).findFirst()
                .isEmpty();
    }

    @PythonMethod
    final boolean isdecimal() { return isdecimal(delegate); }

    @PythonMethod(primary = false)
    static boolean isdecimal(String s) { return isdecimal(adapt(s)); }

    private static boolean isdecimal(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            ;
            if (Character.getType(
                    codepoint) != Character.DECIMAL_DIGIT_NUMBER) {
                return false;
            }
        }
        return true;
    }

    @PythonMethod
    final boolean isdigit() { return isdigit(delegate); }

    @PythonMethod(primary = false)
    static boolean isdigit(String s) { return isdigit(adapt(s)); }

    private static boolean isdigit(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            ;
            if (!Character.isDigit(codepoint)) { return false; }
        }
        return true;
    }

    @PythonMethod
    final boolean isnumeric() { return isnumeric(delegate); }

    @PythonMethod(primary = false)
    static boolean isnumeric(String s) { return isnumeric(adapt(s)); }

    private static boolean isnumeric(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            int type = Character.getType(codepoint);
            if (type != Character.DECIMAL_DIGIT_NUMBER
                    && type != Character.LETTER_NUMBER
                    && type != Character.OTHER_NUMBER) {
                return false;
            }
        }
        return true;
    }

    @PythonMethod
    final boolean istitle() { return istitle(delegate); }

    @PythonMethod(primary = false)
    static boolean istitle(String s) { return istitle(adapt(s)); }

    private static boolean istitle(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        boolean cased = false;
        boolean previous_is_cased = false;
        for (int codepoint : s) {
            if (Character.isUpperCase(codepoint)
                    || Character.isTitleCase(codepoint)) {
                if (previous_is_cased) { return false; }
                previous_is_cased = true;
                cased = true;
            } else if (Character.isLowerCase(codepoint)) {
                if (!previous_is_cased) { return false; }
                previous_is_cased = true;
                cased = true;
            } else {
                previous_is_cased = false;
            }
        }
        return cased;
    }

    @PythonMethod
    final boolean isspace() { return isspace(delegate); }

    @PythonMethod(primary = false)
    static boolean isspace(String s) { return isspace(adapt(s)); }

    private static boolean isspace(PySequence.OfInt s) {
        if (s.length() == 0) { return false; }
        for (int codepoint : s) {
            if (!isPythonSpace(codepoint)) { return false; }
        }
        return true;
    }

    // TODO: implement __format__ and (revised) stringlib
    // @PythonMethod
    // static final Object __format__(Object self, Object formatSpec) {
    //
    // String stringFormatSpec = asString(formatSpec,
    // o -> Abstract.argumentTypeError("__format__",
    // "specification", "str", o));
    //
    // try {
    // // Parse the specification
    // Spec spec = InternalFormat.fromText(stringFormatSpec);
    //
    // // Get a formatter for the specification
    // TextFormatter f = new StrFormatter(spec);
    //
    // /*
    // * Format, pad and return a result according to as the
    // * specification argument.
    // */
    // return f.format(self).pad().getResult();
    //
    // } catch (FormatOverflow fe) {
    // throw PyErr.format(PyExc.OverflowError, fe.getMessage());
    // } catch (FormatError fe) {
    // throw PyErr.format(PyExc.ValueError, fe.getMessage());
    // } catch (NoConversion e) {
    // throw Abstract.impossibleArgumentError(TYPE.name, self);
    // }
    // }

    // Java-only API --------------------------------------------------

    private static final int HIGH_SURROGATE_OFFSET =
            Character.MIN_HIGH_SURROGATE
                    - (Character.MIN_SUPPLEMENTARY_CODE_POINT >>> 10);

    /**
     * The code points of this PyUnicode as a {@link PySequence.OfInt}.
     * This interface will allow the code points to be streamed or
     * iterated (but not modified, obviously).
     *
     * @return the code point sequence
     */
    public PySequence.OfInt asSequence() { return delegate; }

    /**
     * The hash of a {@link PyUnicode} is the same as that of a Java
     * {@code String} equal to it. This is so that a given Python
     * {@code str} may be found as a match in hashed data structures,
     * whichever representation is used for the key or query.
     */
    @Override
    public int hashCode() throws PyBaseException {
        // XXX This is correct but slow: consider calling __hash__()
        // How would that affect inheritance in Java or Python?
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
        // XXX This is correct but slow: consider calling __eq__()
        // How would that affect inheritance in Java or Python?
        return PyDict.pythonEquals(this, obj);
    }

    /**
     * Represent the `str` value in readable form, escaping lone
     * surrogates. The {@code PyUnicode.toString()} is intended to
     * produce a readable output, not always the closest Java
     * {@code String}, for which {@link #asString()} is a better choice.
     */
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
     * raise a {@link PyBaseException TypeError}. This is for use when
     * the argument is expected to be a Python {@code str} or a
     * sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code v} is not a Python {@code str}
     */
    public static String asString(Object v) throws PyBaseException {
        return asString(v, o -> Abstract.requiredTypeError("a str", o));
    }

    /**
     * Present a qualifying object {@code v} as a Java {@code String}
     * value or throw {@code E}. This is for use when the argument is
     * expected to be a Python {@code str} or a sub-class of it.
     * <p>
     * The detailed form of exception is communicated in a
     * lambda-function {@code exc} that will be called (if necessary)
     * with {@code v} as argument. We use a {@code Function} to avoid
     * binding a variable {@code v} at the call site.
     *
     * @param <E> type of exception to throw
     * @param v claimed {@code str}
     * @param exc to supply the exception to throw wrapping {@code v}
     * @return {@code String} value
     * @throws E if {@code v} is not a Python {@code str}
     */
    public static <E extends PyBaseException> String asString(Object v,
            Function<Object, E> exc) throws PyBaseException {
        if (v instanceof String)
            return (String)v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode)v).asString();
        throw exc.apply(v);
    }

    // Iterator ------------------------------------------------------

    /** The Python {@code str_iterator}. */
    private static class PyStrIterator extends AbstractPyIterator {

        static final PyType TYPE = PyType.fromSpec(
                new TypeSpec("str_iterator", MethodHandles.lookup())
                        .remove(Feature.BASETYPE));

        private final CodepointIterator iterator;

        PyStrIterator(CodepointDelegate delegate) {
            this.iterator = delegate.iterator(0);
        }

        @Override
        public PyType getType() { return TYPE; }

        // special methods -------------------------------------------

        @Override
        Object __next__() throws Throwable {
            if (iterator.hasNext()) {
                return PyUnicode.fromCodePoint(iterator.next());
            }
            throw new PyStopIteration();
        }
    }

    // Plumbing ------------------------------------------------------

    /**
     * Convert a Python {@code str} to a Java {@code str} (or throw
     * {@link NoConversion}). This is suitable for use where a method
     * argument should be (exactly) a {@code str}, or an alternate path
     * taken.
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
            return ((PyUnicode)v).asString();
        throw PyUtil.NO_CONVERSION;
    }

    /**
     * Convert this {@code PyUnicode} to a Java {@code String} built
     * from its code point values. If the code point value of a
     * character in Python is a lone surrogate, it will become that
     * UTF-16 unit in the result.
     *
     * @return this {@code PyUnicode} as a Java {@code String}
     */
    String asString() {
        StringBuilder b = new StringBuilder();
        for (int c : delegate) { b.appendCodePoint(c); }
        return b.toString();
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
         * @return the iterator
         */
        CodepointIterator iteratorLast() {
            return iterator(length());
        }

        @Override
        public Iterator<Integer> iterator() { return iterator(0); }

        /**
         * Return the object of which this is the delegate.
         *
         * @return the object of which this is the delegate
         */
        abstract Object principal();

        // Re-declared here to remove throws clause
        @Override
        public abstract Object getItem(int i);

        // Re-declared here to remove throws clause
        @Override
        public abstract Object getSlice(Indices slice);

        @Override
        public String toString() {
            StringBuilder b = new StringBuilder("adapter(\"");
            for (Integer c : this) { b.appendCodePoint(c); }
            return b.append("\")").toString();
        }
    }

    /**
     * A {@code ListIterator} working bidirectionally in code point
     * indices.
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
         * This is just {@link #previous()} specialised to a primitive
         * {@code int}.
         *
         * @return the previous {@code int}
         */
        int previousInt();

        /**
         * Equivalent to {@code n} calls to {@link #previousInt()}
         * returning the last result.
         *
         * @param n the number of steps to take (in reverse)
         * @return the {@code n}th previous {@code int}
         */
        int previousInt(int n);

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

        // Iterator mark and restore --------------------------

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
        public int getInt(int i) {
            if (isBMP()) {
                // No surrogate pairs.
                return s.charAt(i);
            } else {
                // We have to count from the start
                int k = toCharIndex(i);
                return s.codePointAt(k);
            }
        }

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
                return PyUnicode.fromCodePoint(getInt(i));
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
        public Object getSlice(Indices slice) {
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
                return wrap(r);
            }
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
                return (new PyUnicode(s)).delegate.repeat(n);
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
    class UnicodeAdapter extends CodepointDelegate {

        @Override
        public int length() { return value.length; }

        @Override
        public int getInt(int i) { return value[i]; }

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
            return wrap(v);
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
                return wrap(r);
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
                return wrap(r);
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
                return wrap(b);
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
     * with it, perhaps by throwing a {@link PyBaseException TypeError}.
     * A binary operation will normally return {@link Py#NotImplemented}
     * in that case.
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
        throw PyUtil.NO_CONVERSION;
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
    UnicodeAdapter adapt() { return delegate; }

    /**
     * Adapt a Python {@code str}, as by {@link #adapt(Object)}, that is
     * intended as a substring to find, in {@code str.find()} or
     * {@code str.replace()}, for example. If the argument cannot be
     * adapted as a {@code str}, a {@code TypeError} will be raised,
     * with message like "METHOD(): string to find must be str not T",
     * where {@code T} is the type of the errant argument.
     *
     * @param method in which encountered
     * @param sub alleged string
     * @return adapted to a sequence
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code sub} cannot be wrapped as a delegate
     */
    static CodepointDelegate adaptSub(String method, Object sub)
            throws PyBaseException {
        try {
            return adapt(sub);
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError(method, "string to find",
                    "str", sub);
        }
    }

    /**
     * Adapt a Python {@code str}, as by {@link #adapt(Object)}, that is
     * intended as a replacement substring in {@code str.replace()}, for
     * example.
     *
     * @param method in which encountered
     * @param replacement alleged string
     * @return adapted to a sequence
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code sub} cannot be wrapped as a delegate
     */
    static CodepointDelegate adaptRep(String method, Object replacement)
            throws PyBaseException {
        try {
            return adapt(replacement);
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError(method, "replacement",
                    "str", replacement);
        }
    }

    /**
     * Adapt a Python {@code str} intended as a separator, as by
     * {@link #adapt(Object)}.
     *
     * @param method in which encountered
     * @param sep alleged separator
     * @return adapted to a sequence
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code sep} cannot be wrapped as a delegate
     * @throws PyBaseException ({@link PyExc#ValueError ValueError}) if
     *     {@code sep} is the empty string
     */
    static CodepointDelegate adaptSeparator(String method, Object sep)
            throws PyBaseException {
        try {
            CodepointDelegate p = adapt(sep);
            if (p.length() == 0) {
                throw PyErr.format(PyExc.ValueError,
                        "%s(): empty separator", method);
            }
            return p;
        } catch (NoConversion nc) {
            throw Abstract.argumentTypeError(method, "separator",
                    "str or None", sep);
        }
    }

    /**
     * Adapt a Python {@code str} intended as a fill character in
     * justification and centring operations. The behaviour is quite
     * like {@link #adapt(Object)}, but it returns a single code point.
     * A null argument returns the default choice, a space.
     *
     * @param method in which encountered
     * @param fill alleged fill character (or {@code null})
     * @return fill as a code point
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code fill} is not a one-character string
     */
    private static int adaptFill(String method, Object fill) {
        if (fill == null) {
            return ' ';
        } else if (fill instanceof String) {
            String s = (String)fill;
            if (s.codePointCount(0, s.length()) != 1)
                throw PyErr.format(PyExc.TypeError, BAD_FILLCHAR);
            return s.codePointAt(0);
        } else if (fill instanceof PyUnicode) {
            PyUnicode u = (PyUnicode)fill;
            if (u.value.length != 1)
                throw PyErr.format(PyExc.TypeError, BAD_FILLCHAR);
            return u.value[0];
        } else {
            throw Abstract.argumentTypeError(method, "fill",
                    "a character", fill);
        }
    }

    private static String BAD_FILLCHAR =
            "the fill character must be exactly one character long";

    /**
     * Adapt a Python {@code str}, intended as a list of characters to
     * strip, as by {@link #adapt(Object)} then conversion to a set.
     *
     * @param method in which encountered
     * @param chars characters defining the set (or {@code None} or
     *     {@code null})
     * @return {@code null} or characters adapted to a set
     * @throws PyBaseException ({@link PyExc#TypeError TypeError}) if
     *     {@code sep} cannot be wrapped as a delegate
     */
    private static Set<Integer> adaptStripSet(String method,
            Object chars) throws PyBaseException {
        if (chars == null || chars == Py.None) {
            return null;
        } else {
            try {
                return adapt(chars).asStream()
                        .collect(Collectors.toCollection(HashSet::new));
            } catch (NoConversion nc) {
                throw Abstract.argumentTypeError(method, "chars",
                        "str or None", chars);
            }
        }
    }

    /**
     * Convert slice end indices to a {@link PySlice.Indices} object.
     *
     * @param s sequence being sliced
     * @param start first index included
     * @param end first index not included
     * @return indices of the slice
     * @throws PyBaseException if {@code start} or {@code end} cannot be
     *     considered an index (or from their {@code __index__})
     */
    private static PySlice.Indices getSliceIndices(CodepointDelegate s,
            Object start, Object end) throws PyBaseException {
        try {
            return (new PySlice(start, end)).getIndices(s.length());
        } catch (PyBaseException pye) {
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
        return wrap(IntStream.concat(v, w).toArray());
    }

    /**
     * Append a delegate to a builder (during {@code str.join}) using
     * either array indexing or iteration, whichever is likely to be
     * quicker. (Append by indexing a {@code String} is inefficient if
     * it might contain SMP code points.)
     *
     * @param buf to which to append
     * @param s to append
     */
    private static void append(IntArrayBuilder buf,
            CodepointDelegate s) {
        if (s instanceof StringAdapter && !((StringAdapter)s).isBMP())
            // Append by iterating s
            buf.append(s.iterator(0));
        else
            // Append by indexing s
            buf.append(s);
    }

    /**
     * Apply a unary operation to every character of a string and return
     * them as a string. This supports transformations like
     * {@link #upper() str.upper()}.
     *
     * @param op the operation
     * @return transformed string
     */
    private PyUnicode mapChars(IntUnaryOperator op) {
        return wrap(delegate.asIntStream().map(op).toArray());
    }

    /**
     * Apply a unary operation to every character of a string and return
     * them as a string. This supports transformations like
     * {@link #upper() str.upper()}.
     *
     * @param op the operation
     * @return transformed string
     */
    private static String mapChars(String s, IntUnaryOperator op) {
        int[] v = s.codePoints().map(op).toArray();
        return new String(v, 0, v.length);
    }

    /** A {@code NoSuchElementException} identifying the index. */
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
     * @throws PyBaseException ({@link PyExc#ValueError ValueError}) if
     *     argument is negative
     */
    private static final int checkIndexReturn(int index)
            throws PyBaseException {
        if (index >= 0) {
            return index;
        } else {
            throw PyErr.format(PyExc.ValueError, "substring not found");
        }
    }

    // TODO: implement __format__ and (revised) stringlib
    /// **
    // * A {@link AbstractFormatter}, constructed from a {@link Spec},
    // * with specific validations for {@code str.__format__}.
    // */
    // private static class StrFormatter extends TextFormatter {
    //
    // /**
    // * Prepare a {@link TextFormatter} in support of
    // * {@link PyUnicode#__format__(Object, Object) str.__format__}.
    // *
    // * @param spec a parsed PEP-3101 format specification.
    // * @return a formatter ready to use.
    // * @throws FormatOverflow if a value is out of range (including
    // * the precision)
    // * @throws PyBaseException ({@link PyExc#FormatError FormatError})
    // if an unsupported format
    // character is
    // * encountered
    // */
    // StrFormatter(Spec spec) throws PyBaseException {
    // super(validated(spec));
    // }
    //
    // @Override
    // public TextFormatter format(Object self) throws NoConversion {
    // return format(convertToString(self));
    // }
    //
    // private static Spec validated(Spec spec) throws PyBaseException {
    // String type = TYPE.name;
    // switch (spec.type) {
    //
    // case Spec.NONE:
    // case 's':
    // // Check for disallowed parts of the specification
    // if (spec.grouping) {
    // throw notAllowed("Grouping", type, spec.type);
    // } else if (Spec.specified(spec.sign)) {
    // throw signNotAllowed(type, '\0');
    // } else if (spec.alternate) {
    // throw alternateFormNotAllowed(type);
    // } else if (spec.align == '=') {
    // throw alignmentNotAllowed('=', type);
    // }
    // // Passed (whew!)
    // break;
    //
    // default:
    // // The type code was not recognised
    // throw unknownFormat(spec.type, type);
    // }
    //
    // /*
    // * spec may be incomplete. The defaults are those commonly
    // * used for string formats.
    // */
    // return spec.withDefaults(Spec.STRING);
    // }
    // }
}
