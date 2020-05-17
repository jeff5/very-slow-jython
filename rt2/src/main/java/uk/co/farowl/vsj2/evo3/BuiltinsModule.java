package uk.co.farowl.vsj2.evo3;

/** The {@code builtins} module. */
class BuiltinsModule extends JavaModule implements Exposed {

    BuiltinsModule() { super("builtins"); }

    @Function
    @DocString("Return the absolute value of the argument.")
    static PyObject abs(PyObject x) throws Throwable {
        return Number.absolute(x);
    }

    @Function
    @DocString("Return the dictionary containing the current scope's global variables.")
    static PyObject globals() throws Throwable {
        return Interpreter.getFrame().globals;
    }

    @Function
    @DocString("Return the number of items in a container.")
    static PyObject len(PyObject v) throws Throwable {
        return Py.val(Abstract.size(v));
    }

    @Function
    @DocString("With a single iterable argument, return its biggest item. "
            + "With two or more arguments, return the largest argument.\"")
    // Simplified version of max()
    static PyObject max(PyTuple args) throws Throwable {
        return minmax(args, Comparison.GT);
    }

    @Function
    @DocString("With a single iterable argument, return its smallest item. "
            + "With two or more arguments, return the smallest argument.\"")
    // Simplified version of max()
    static PyObject min(PyTuple args) throws Throwable {
        return minmax(args, Comparison.LT);
    }

    /**
     * Implementation of both {@link #min(PyTuple)} and
     * {@link #max(PyTuple)}.
     *
     * @param args contains arguments or one iterable of arguments
     * @param op {@code LT} for {@code min} and {@code GT} for
     *            {@code max}.
     * @return min or max result as appropriate
     * @throws Throwable
     */
    private static PyObject minmax(PyTuple args, Comparison op)
            throws Throwable {

        PyObject v, item, result;
        int n = PyTuple.length(args);

        if (n > 1)
            // Positional mode: args contains the values to compare
            v = args;
        else {
            // Single argument: an iterable the values to compare
            v = args.getItem(0);
            n = Abstract.size(v);
        }

        if (n == 0)
            throw new ValueError("%s() arg is an empty sequence",
                    op == Comparison.LT ? "min" : "max");

        // Now we can get on with the comparison
        result = Sequence.getItem(v, 0);
        for (int i = 1; i < n; i++) {
            item = Sequence.getItem(v, i);
            if (Abstract.richCompareBool(item, result, op))
                result = item;
        }
        return result;
    }

}
