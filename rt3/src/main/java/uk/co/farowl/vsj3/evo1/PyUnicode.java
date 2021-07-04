package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Iterator;

import uk.co.farowl.vsj3.evo1.Exposed.Default;
import uk.co.farowl.vsj3.evo1.Exposed.Name;
import uk.co.farowl.vsj3.evo1.Exposed.PythonMethod;
import uk.co.farowl.vsj3.evo1.PyObjectUtil.NoConversion;

/** The Python {@code str} object. */
class PyUnicode implements PySequenceInterface<Integer>,
        CraftedPyObject, PyDict.Key {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", MethodHandles.lookup())
                    .methods(PyUnicodeMethods.class)
                    .adopt(String.class));
    protected PyType type;

    /**
     * The implementation holds a Java {@code int} array of code points.
     */
    private final int[] value;

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
     * after the call. See {@link #concat(PySequenceInterface)} for a
     * correct use.
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

    @Deprecated // XXX Make private
    PyUnicode(String value) {
        this(TYPE, value.codePoints().toArray());
    }

    @Deprecated // XXX Private or not needed
    PyUnicode(char c) { this(TYPE, new int[] {c}); }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private int __len__() { return value.length; }

    @SuppressWarnings("unused")
    private static int __len__(String self) {
        return self.codePointCount(0, self.length());
    }

    @SuppressWarnings("unused")
    private Object __str__() { return this; }

    @SuppressWarnings("unused")
    private static Object __str__(String self) { return self; }

    private static Object __repr__(String self) {
        // Ok, it should be more complicated but I'm in a hurry.
        return "'" + self + "'";
    }

    @SuppressWarnings("unused")
    private static Object __repr__(PyUnicode self) {
        StringBuilder b = new StringBuilder();
        for (int c : self) { b.appendCodePoint(c); }
        return __repr__(b.toString());
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
    private Object __add__(Object w) {
        try {
            return concat(adapt(w));
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    @SuppressWarnings("unused")
    private static Object __add__(String v, Object w) {
        try {
            if (w instanceof String)
                return v.concat((String) w);
            else
                return adapt(v).concat(adapt(w));
        } catch (NoConversion e) {
            return Py.NotImplemented;
        }
    }

    private Object __mul__(Object n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    private static Object __mul__(String self, Object n)
            throws Throwable {
        PySequenceInterface<Integer> s = new StringAdapter(self);
        return PyObjectUtil.repeat(s, n);
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

    @SuppressWarnings("unused")
    private Object __getitem__(Object item) throws Throwable {
        Operations itemOps = Operations.of(item);
        if (Slot.op_index.isDefinedFor(itemOps)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += length(); }
            return getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

    @SuppressWarnings("unused")
    private static Object __getitem__(String self, Object item)
            throws Throwable {
        Operations itemOps = Operations.of(item);
        if (Slot.op_index.isDefinedFor(itemOps)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.length(); }
            return self.substring(i, i + 1);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }

    // non-slot API ---------------------------------------------------

    @PythonMethod(primary = false)
    boolean isascii() {
        for (int c : this) { if (c > 127) { return false; } }
        return true;
    }

    @PythonMethod
    static boolean isascii(String self) {
        for (int c : new StringAdapter(self)) {
            if (c > 127) { return false; }
        }
        return true;
    }

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
        if (type == TYPE)
            /*
             * XXX It may be fast to specialise here, but the real
             * motive is that arrive here when filling the dictionaries
             * of types, long before PyUnicode slots are ready for
             * PyDict.pythonHash. When we use String keys in types, it
             * won't be needed. (Maybe.)
             */
            return __hash__();
        else
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
        if (type == TYPE) {
            /*
             * XXX It may be fast to specialise here, but the real
             * motive is that we arrive here when filling the
             * dictionaries of types, long before PyUnicode slots are
             * ready for PyDict.pythonEquals. When we use String keys in
             * types, it won't be needed. (Maybe.)
             */
            if (obj instanceof PyDict.Key)
                obj = ((PyDict.Key) obj).get();
            try {
                return equals(adapt(obj));
            } catch (NoConversion e) {
                return false;
            }
        } else
            return PyDict.pythonEquals(this, obj);
    }

    /**
     * Compare for equality with a sequence. This is a little simpler
     * than {@code compareTo}.
     *
     * @param b another
     * @return whether values equal
     */
    private boolean equals(PySequenceInterface<Integer> b) {
        // Lengths must be equal
        if (length() != b.length()) { return false; }
        // Scan the codes points in this.value and b
        Iterator<Integer> ib = b.iterator();
        for (int c : value) { if (c != ib.next()) { return false; } }
        return true;
    }

    @Override
    public int compareTo(PySequenceInterface<Integer> other) {
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
         * The sequences matched over the length of value. The other is
         * the winner if it still has elements. Otherwise its a tie.
         */
        return ib.hasNext() ? -1 : 0;
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
    public PyType getType() { return type; }

    @Override
    public String toString() {
        StringBuilder b = new StringBuilder();
        for (int c : value) { b.appendCodePoint(c); }
        return b.toString();
    }

    /**
     * Present a Python {@code str} as a Java {@code String} values or
     * raise a {@link TypeError}. This is for use when the argument is
     * expected to be a Python {@code str} or a sub-class of it.
     *
     * @param v claimed {@code str}
     * @return {@code String} value
     * @throws TypeError if {@code v} is not a Python {@code str}
     */
    static String asString(Object v) throws TypeError {
        if (v instanceof String)
            return (String) v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode) v).toString();
        throw Abstract.requiredTypeError("a str", v);
    }

    // Sequence interface ---------------------------------------------

    @Override
    public int length() { return value.length; };

    @Override
    public Integer getItem(int i) { return Integer.valueOf(value[i]); }

    @Override
    public PyUnicode concat(PySequenceInterface<Integer> other) {
        int n = length(), m = other.length();
        int[] b = new int[n + m];
        System.arraycopy(value, 0, b, 0, n);
        for (int x : other) { b[n++] = x; }
        return new PyUnicode(TYPE, true, b);
    }

    @Override
    public Object repeat(int n) {
        if (n == 0)
            return "";
        else if (n == 1)
            return this;
        else {
            try {
                int m = value.length;
                int[] b = new int[n * m];
                for (int i = 0, p = 0; i < n; i++, p += m) {
                    System.arraycopy(value, 0, b, p, m);
                }
                return new PyUnicode(TYPE, true, b);
            } catch (OutOfMemoryError e) {
                throw new OverflowError("repeated string is too long");
            }
        }
    }

    @Override
    public Iterator<Integer> iterator() {
        return new Iterator<Integer>() {

            private int i = 0;

            @Override
            public boolean hasNext() { return i < value.length; }

            @Override
            public Integer next() { return value[i++]; }
        };
    }

    // Plumbing -------------------------------------------------------

    static PyUnicode EMPTY = new PyUnicode(TYPE, "");

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
    // Compare CPython unicodeobject.c:
    static String convertToString(Object v) throws NoConversion {
        if (v instanceof String)
            return (String) v;
        else if (v instanceof PyUnicode)
            return ((PyUnicode) v).toString();
        throw PyObjectUtil.NO_CONVERSION;
    }

    /**
     * Wrap a Java {@code String} as a sequence. The {@code char}s of
     * the {@code String} are treated individually, not interpreted as
     * surrogate pairs.
     */
    private static class StringAdapter
            implements PySequenceInterface<Integer> {

        final private String s;

        StringAdapter(String s) { this.s = s; }

        @Override
        public int length() { return s.length(); };

        @Override
        public Integer getItem(int i) {
            // Treating surrogate pairs as two characters
            return Integer.valueOf(s.charAt(i));
        }

        @Override
        public Object concat(PySequenceInterface<Integer> other) {
            boolean isBMP = true;
            StringBuilder b = new StringBuilder(s);
            for (int c : other) {
                if (Character.isBmpCodePoint(c)) {
                    b.append((char) c);
                } else {
                    isBMP = false;
                    b.appendCodePoint(c);
                }
            }
            return isBMP ? b.toString() : new PyUnicode(b.toString());
        }

        @Override
        public Object repeat(int n) { return s.repeat(n); }

        @Override
        public Iterator<Integer> iterator() {
            return new Iterator<Integer>() {

                private int i = 0;

                @Override
                public boolean hasNext() { return i < s.length(); }

                @Override
                public Integer next() {
                    // Treating surrogate pairs as two characters
                    return getItem(i++);
                }

            };
        }

        @Override
        public int compareTo(PySequenceInterface<Integer> other) {
            int n = s.length();
            Iterator<Integer> ib = other.iterator();
            // Not treating surrogate pairs as one code point
            for (int i = 0; i < n; i++) {
                int a = s.charAt(i);
                if (ib.hasNext()) {
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
    }

    /**
     * Adapt a Python {@code str} to a sequence of Java {@code Integer}
     * values or raise a {@link TypeError}. This is for use when the
     * argument is expected to be a Python {@code str} or a sub-class of
     * it.
     *
     * @param v claimed {@code str}
     * @return {@code int} value
     * @throws TypeError if {@code v} is not a Python {@code str}
     */
    static PySequenceInterface<Integer> asSeq(Object v)
            throws TypeError {
        // Check against supported types, most likely first
        if (v instanceof String)
            return new StringAdapter((String) v);
        else if (v instanceof PyUnicode)
            return (PyUnicode) v;
        throw Abstract.requiredTypeError("a str", v);
    }

    /**
     * Adapt a Python {@code str} to a sequence of Java {@code Integer}
     * values or throw an exception. If the method throws the special
     * exception {@link NoConversion}, the caller must catch it and deal
     * with it, perhaps by throwing a {@link TypeError}. A binary
     * operation will normally return {@link Py#NotImplemented} in that
     * case.
     *
     * @param v to wrap or return
     * @return adapted to a sequence
     * @throws NoConversion if {@code v} is not a Python {@code str}
     */
    static PySequenceInterface<Integer> adapt(Object v)
            throws NoConversion {
        // Check against supported types, most likely first
        if (v instanceof String)
            return new StringAdapter((String) v);
        else if (v instanceof PyUnicode)
            return (PyUnicode) v;
        throw PyObjectUtil.NO_CONVERSION;
    }
}
