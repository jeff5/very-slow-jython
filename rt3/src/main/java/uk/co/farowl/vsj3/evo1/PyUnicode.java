package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code str} object. */
class PyUnicode
        implements PySequence, Comparable<PyUnicode>, CraftedType {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", MethodHandles.lookup())
                    .adopt(String.class));
    protected PyType type;
    final String value; // only supporting BMP for now

    PyUnicode(PyType type, String value) {
        this.type = type;
        this.value = value;
    }

    @Deprecated // XXX Private or not needed
    PyUnicode(String value) {
        this(TYPE, value);
    }

    @Deprecated // XXX Private or not needed
    PyUnicode(char c) {
        this(TYPE, String.valueOf(c));
    }

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private int __len__() { return value.length(); }

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
        return __repr__(self.value);
    }

    @SuppressWarnings("unused")
    private int __hash__() { return value.hashCode(); }

    @SuppressWarnings("unused")
    private static int __hash__(String self) { return self.hashCode(); }

    @SuppressWarnings("unused")
    private static Object __add__(String v, Object w) {
        if (w instanceof String)
            return v + (String) w;
        else if (w instanceof PyUnicode)
            return v + ((PyUnicode) w).value;
        else
            return Py.NotImplemented;
    }

    @SuppressWarnings("unused")
    private Object __add__(Object w) { return __add__(value, w); }

//
//@formatter:off
//    @SuppressWarnings("unused")
//    private Object __lt__(Object w) {
//        return this.cmp(w, Comparison.LT);
//    }
//
//    @SuppressWarnings("unused")
//    private Object __le__(Object w) {
//        return this.cmp(w, Comparison.LE);
//    }
//
//    @SuppressWarnings("unused")
//    private Object __eq__(Object w) {
//        return this.cmp(w, Comparison.EQ);
//    }
//
//    @SuppressWarnings("unused")
//    private Object __ne__(Object w) {
//        return this.cmp(w, Comparison.NE);
//    }
//
//    @SuppressWarnings("unused")
//    private Object __gt__(Object w) {
//        return this.cmp(w, Comparison.GT);
//    }
//
//    @SuppressWarnings("unused")
//    private Object __ge__(Object w) {
//        return this.cmp(w, Comparison.GE);
//    }
//
//@formatter:on

    @SuppressWarnings("unused")
    private Object __mul__(Object n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private static Object __mul__(String self, Object n)
            throws Throwable {
        PySequence s = new PySequenceAdapter(self);
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
    private static Object __getitem__(PyUnicode self, Object item)
            throws Throwable {
        Operations itemOps = Operations.of(item);
        if (Slot.op_index.isDefinedFor(itemOps)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.value.length(); }
            return self.getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
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

    // Sequence interface ---------------------------------------------

    PyUnicode getItem(int i) {
        try {
            return new PyUnicode(value.charAt(i));
        } catch (IndexOutOfBoundsException e) {
            throw Abstract.indexOutOfRange("str");
        }
    }

    @Override
    public PyUnicode repeat(int i) {
        try {
            if (i < 1)
                return EMPTY;
            else if (i > 1)
                return Py.str(value.repeat(i));
            else
                return this;
        } catch (OutOfMemoryError e) {
            throw new OverflowError("repeated string is too long");
        }
    }

    // Non-slot API -------------------------------------------------

    @Override
    public int hashCode() { return value.hashCode(); }

    @Override
    public int compareTo(PyUnicode o) {
        return value.compareTo(o.value);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyUnicode) {
            return this.value.equals(((PyUnicode) obj).value);
        } else
            return false;
    }

    /**
     * Create a {@code str} from a format and arguments. Not Java
     * {@code String.format} semantics are applied, not the CPython
     * ones.
     *
     * @param fmt format string (Java semantics)
     * @param args arguments
     * @return formatted string
     */
    static PyUnicode fromFormat(String fmt, Object... args) {
        return new PyUnicode(TYPE, String.format(fmt, args));
    }

    @Override
    public PyType getType() { return type; }

    /*
     * str is a little special in defining toString() directly. We do
     * this because the default implementation __str__.toString would
     * recurse infinitely. Derived classes should revert to
     * Py.defaultToString(this).
     */
    // XXX Is this still true?
    @Override
    public String toString() { return value; }

    // Support methods -----------------------------------------------

    static PyUnicode EMPTY = new PyUnicode("");

    /** Helper for comparison operations. */
    private Object cmp(Object w, Comparison op) {
        if (w instanceof PyUnicode) {
            return op.toBool(compareTo((PyUnicode) w));
        } else {
            return Py.NotImplemented;
        }
    }

    private static class PySequenceAdapter implements PySequence {

        final private String s;

        PySequenceAdapter(String value) { this.s = value; }

        @Override
        public Object repeat(int n) { return s.repeat(n); }
    }
}
