package uk.co.farowl.vsj2.evo4;

/** The {@code builtins} module. */
class BuiltinsModule extends JavaModule implements Exposed {

    BuiltinsModule() {
        super("builtins");

        // This list is taken from CPython bltinmodule.c
        add(ID.None, Py.None);
        //add(ID.Ellipsis, Py.Ellipsis);
        add(ID.NotImplemented, Py.NotImplemented);
        add(ID.False, Py.False);
        add(ID.True, Py.True);
        add(ID.bool, PyBool.TYPE);
        //add(ID.memoryview, PyMemoryView.TYPE);
        //add(ID.bytearray, PyByteArray.TYPE);
        add(ID.bytes, PyBytes.TYPE);
        //add(ID.classmethod, PyClassMethod.TYPE);
        //add(ID.complex, PyComplex.TYPE);
        add(ID.dict, PyDict.TYPE);
        //add(ID.enumerate, PyEnum.TYPE);
        //add(ID.filter, PyFilter.TYPE);
        add(ID.intern("float"), PyFloat.TYPE);
        //add(ID.frozenset, PyFrozenSet.TYPE);
        //add(ID.property, PyProperty.TYPE);
        add(ID.intern("int"), PyLong.TYPE);
        add(ID.list, PyList.TYPE);
        //add(ID.map, PyMap.TYPE);
        add(ID.object, PyBaseObject.TYPE);
        //add(ID.range, PyRange.TYPE);
        //add(ID.reversed, PyReversed.TYPE);
        //add(ID.set, PySet.TYPE);
        //add(ID.slice, PySlice.TYPE);
        //add(ID.staticmethod, PyStaticMethod.TYPE);
        add(ID.str, PyUnicode.TYPE);
        //add(ID.super, PySuper.TYPE);
        add(ID.tuple, PyTuple.TYPE);
        add(ID.type, PyType.TYPE);
        //add(ID.zip, PyZip.TYPE);
    }

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
        int n = args.size();

        if (n > 1)
            // Positional mode: args contains the values to compare
            v = args;
        else {
            // Single argument: an iterable the values to compare
            v = args.get(0);
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
