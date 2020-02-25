package uk.co.farowl.vsj2.evo3;

/**
 * Selects a particular "rich comparison" operation from the repertoire
 * supported by {@link Opcode#COMPARE_OP}, the argument to which is the
 * {@code code} attribute of the name in this {@code enum}.
 */
enum Comparison {
    // Order and number must be reflected in swap[] and from[].
    LT(0, "<"), LE(1, "<="), EQ(2, "=="), NE(3, "!="), GT(4, ">"),
    GE(5, ">="), IN(6, "in"), NOT_IN(7, "not in"), IS(8, "is"),
    IS_NOT(9, "is not"), EXC_MATCH(10, "matches"), BAD(11, "?");

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
}