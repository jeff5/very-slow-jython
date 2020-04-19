package uk.co.farowl.vsj2.evo3;

/** The {@code builtins} module. */
class BuiltinsModule extends JavaModule implements Exposed {

    BuiltinsModule(Interpreter interpreter) {
        super(interpreter, "builtins");
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

        String name = "max";
        Comparison op = Comparison.GT;

        PyObject v, item, maxitem;
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
            throw new ValueError("%s() arg is an empty sequence", name);

        // Now we can get on with the comparison
        maxitem = Sequence.getItem(v, 0);
        for (int i = 1; i < n; i++) {
            item = Sequence.getItem(v, i);
            if (Abstract.richCompareBool(item, maxitem, op))
                maxitem = item;
        }
        return maxitem;
    }

}
