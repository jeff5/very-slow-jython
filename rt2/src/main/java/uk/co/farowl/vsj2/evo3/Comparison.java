package uk.co.farowl.vsj2.evo3;

/**
 * Selects a particular "rich comparison" operation from the repertoire
 * supported by {@link Opcode#COMPARE_OP}, the argument to which is the
 * {@code code} attribute of the name in this {@code enum}.
 */
enum Comparison {
    // Order and number must be reflected in swap[] and from[].
    LT(0, "<") {

        @Override
        PyBool toBool(int c) { return c < 0 ? Py.True : Py.False; }
    },

    LE(1, "<=") {

        @Override
        PyBool toBool(int c) { return c <= 0 ? Py.True : Py.False; }
    },

    EQ(2, "==") {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }
    },

    NE(3, "!=") {

        @Override
        PyBool toBool(int c) { return c != 0 ? Py.True : Py.False; }
    },

    GT(4, ">") {

        @Override
        PyBool toBool(int c) { return c > 0 ? Py.True : Py.False; }
    },

    GE(5, ">=") {

        @Override
        PyBool toBool(int c) { return c >= 0 ? Py.True : Py.False; }
    },

    IN(6, "in") {

        @Override
        PyBool toBool(int c) { return c >= 0 ? Py.True : Py.False; }
    },

    NOT_IN(7, "not in") {

        @Override
        PyBool toBool(int c) { return c < 0 ? Py.True : Py.False; }
    },

    IS(8, "is") {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }
    },

    IS_NOT(9, "is not") {

        @Override
        PyBool toBool(int c) { return c != 0 ? Py.True : Py.False; }
    },

    EXC_MATCH(10, "matches") {

        @Override
        PyBool toBool(int c) { return c == 0 ? Py.True : Py.False; }
    },

    BAD(11, "?") {

        @Override
        PyBool toBool(int c) { return Py.False; }
    };

    final int code;
    final String text;

    Comparison(int code, String text) {
        this.code = code;
        this.text = text;
    }

    /**
     * The text corresponding to the value, e.g. "!=" for {@code NE},
     * "is not" for {@code IS_NOT}. Mostly for error messages.
     *
     * @return text corresponding
     */
    @Override
    public String toString() { return text; }

    /** The swapped version of this comparison, e.g. LT <--> GT */
    Comparison swapped() {
        return swap[code];
    }

    private static final Comparison[] swap =
            {GT, GE, EQ, NE, LT, LE, BAD, BAD, IS, IS_NOT, BAD, BAD};

    /** Translate opcode argument to Comparison constant. */
    static Comparison from(int oparg) {
        return oparg >= 0 && oparg < from.length ? from[oparg] : BAD;
    }

    private static final Comparison[] from = {LT, LE, EQ, NE, GT, GE,
            IN, NOT_IN, IS, IS_NOT, EXC_MATCH, BAD};

    /**
     * Translate a comparison result (typically from a call to
     * {@code Comparable.compareTo()}) into the appropriate boolean,
     * for example {@code GE.toBool(1)} is {@link Py#True}.
     * This is only useful for the the six operations LT to GE inclusive,
     * and for the others we assume c==0 indicates equality.
     *
     * @param c comparison result
     * @return boolean equivalent for this operation
     */
    // Compare CPython object.h::Py_RETURN_RICHCOMPARE
    abstract PyBool toBool(int c);
}