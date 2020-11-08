package uk.co.farowl.vsj2.evo4;

/** The Python {@code str} object. */
class PyUnicode implements PySequence, Comparable<PyUnicode> {

    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("str", PyUnicode.class));
    protected PyType type;
    final String value; // only supporting BMP for now

    PyUnicode(PyType type, String value) {
        this.type = type;
        this.value = value;
    }

    PyUnicode(String value) { this(TYPE, value); }

    PyUnicode(char c) { this(TYPE, String.valueOf(c)); }

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
     * Py.defaultToString(self).
     */
    @Override
    public String toString() { return value; }


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

    static int __len__(PyUnicode s) { return s.value.length(); }

    static PyObject __str__(PyUnicode s) {
        // return self
        return s;
    }

    static PyObject __repr__(PyUnicode s) {
        // Ok, it should be more complicated but I'm in a hurry.
        return Py.str("'" + s + "'");
    }

    static PyObject __lt__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.LT);
    }

    static PyObject __le__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.LE);
    }

    static PyObject __eq__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.EQ);
    }

    static PyObject __ne__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.NE);
    }

    static PyObject __gt__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.GT);
    }

    static PyObject __ge__(PyUnicode v, PyObject w) {
        return v.cmp(w, Comparison.GE);
    }

    static PyObject __mul__(PyUnicode self, PyObject n)
            throws Throwable {
        return PyObjectUtil.repeat(self, n);
    }

    static PyObject __rmul__(PyUnicode self, PyObject n)
            throws Throwable {
        return PyObjectUtil.repeat(self, n);
    }

    static PyObject __getitem__(PyUnicode self, PyObject item)
            throws Throwable {
        PyType itemType = item.getType();
        if (Slot.op_index.isDefinedFor(itemType)) {
            int i = Number.asSize(item, IndexError::new);
            if (i < 0) { i += self.value.length(); }
            return self.getItem(i);
        }
        // else if item is a PySlice { ... }
        else
            throw Abstract.indexTypeError(self, item);
    }

    // Support methods -----------------------------------------------

    static PyUnicode EMPTY = new PyUnicode("");

    /** Helper for comparison operations. */
    private PyObject cmp(PyObject w, Comparison op) {
        if (w instanceof PyUnicode) {
            return op.toBool(compareTo((PyUnicode) w));
        } else {
            return Py.NotImplemented;
        }
    }

}
