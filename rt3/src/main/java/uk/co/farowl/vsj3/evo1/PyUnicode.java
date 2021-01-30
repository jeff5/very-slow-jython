package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/** The Python {@code str} object. */
class PyUnicode implements PySequence, Comparable<PyUnicode> {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", PyUnicode.class,
                    MethodHandles.lookup()));
    protected PyType type;
    final String value; // only supporting BMP for now

    PyUnicode(PyType type, String value) {
        this.type = type;
        this.value = value;
    }

    PyUnicode(String value) {
        this(TYPE, value);
    }

    PyUnicode(char c) {
        this(TYPE, String.valueOf(c));
    }

    @Override
    public int hashCode() {
        return value.hashCode();
    }

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
    @Override
    public String toString() {
        return value;
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

    // slot functions -------------------------------------------------

    @SuppressWarnings("unused")
    private int __len__() {
        return this.value.length();
    }

    @SuppressWarnings("unused")
    private Object __str__() {
        return this;
    }

    @SuppressWarnings("unused")
    private Object __repr__() {
        // Ok, it should be more complicated but I'm in a hurry.
        return Py.str("'" + value + "'");
    }

    @SuppressWarnings("unused")
    private Object __lt__(Object w) {
        return this.cmp(w, Comparison.LT);
    }

    @SuppressWarnings("unused")
    private Object __le__(Object w) {
        return this.cmp(w, Comparison.LE);
    }

    @SuppressWarnings("unused")
    private Object __eq__(Object w) {
        return this.cmp(w, Comparison.EQ);
    }

    @SuppressWarnings("unused")
    private Object __ne__(Object w) {
        return this.cmp(w, Comparison.NE);
    }

    @SuppressWarnings("unused")
    private Object __gt__(Object w) {
        return this.cmp(w, Comparison.GT);
    }

    @SuppressWarnings("unused")
    private Object __ge__(Object w) {
        return this.cmp(w, Comparison.GE);
    }

    @SuppressWarnings("unused")
    private Object __mul__(Object n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private Object __rmul__(Object n) throws Throwable {
        return PyObjectUtil.repeat(this, n);
    }

    @SuppressWarnings("unused")
    private Object __getitem__(Object item) throws Throwable {
        Operations itemType = Operations.of(item);
        if (Slot.op_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += value.length(); }
            return this.getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(this, item);
    }

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

}
