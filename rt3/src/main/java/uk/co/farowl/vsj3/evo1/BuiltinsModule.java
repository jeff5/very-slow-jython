package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;

/**
 * The {@code builtins} module.
 *
 * Although it is fully a module, the {@link BuiltinsModule} lives in
 * the {@code core} package because it needs privileged access to the
 * core implementation that extension modules do not.
 */
class BuiltinsModule extends JavaModule implements Exposed {

    private static final ModuleDef DEF =
            new ModuleDef("builtins", MethodHandles.lookup());

    BuiltinsModule() {
        super(DEF);

        // This list is taken from CPython bltinmodule.c
        add("None", Py.None);
        // add("Ellipsis", Py.Ellipsis);
        add("NotImplemented", Py.NotImplemented);
        add("False", Py.False);
        add("True", Py.True);
        add("bool", PyBool.TYPE);
        // add("memoryview", PyMemoryView.TYPE);
        // add("bytearray", PyByteArray.TYPE);
        add("bytes", PyBytes.TYPE);
        // add("classmethod", PyClassMethod.TYPE);
        // add("complex", PyComplex.TYPE);
        add("dict", PyDict.TYPE);
        // add("enumerate", PyEnum.TYPE);
        // add("filter", PyFilter.TYPE);
        add("float", PyFloat.TYPE);
        // add("frozenset", PyFrozenSet.TYPE);
        // add("property", PyProperty.TYPE);
        add("int", PyLong.TYPE);
        add("list", PyList.TYPE);
        // add("map", PyMap.TYPE);
        add("object", PyBaseObject.TYPE);
        // add("range", PyRange.TYPE);
        // add("reversed", PyReversed.TYPE);
        // add("set", PySet.TYPE);
        add("slice", PySlice.TYPE);
        // add("staticmethod", PyStaticMethod.TYPE);
        add("str", PyUnicode.TYPE);
        // add("super", PySuper.TYPE);
        add("tuple", PyTuple.TYPE);
        add("type", PyType.TYPE);
        // add("zip", PyZip.TYPE);
    }

    @PythonStaticMethod
    @DocString("Return the absolute value of the argument.")
    static Object abs(Object x) throws Throwable {
        return PyNumber.absolute(x);
    }

    @PythonStaticMethod
    @DocString("Return the dictionary containing the current scope's global variables.")
    static Object globals() throws Throwable {
        return Interpreter.getFrame().globals;
    }

    @PythonStaticMethod
    @DocString("Return the number of items in a container.")
    static Object len(Object v) throws Throwable {
        return PySequence.size(v);
    }

    @PythonStaticMethod
    @DocString("With a single iterable argument, return its biggest item. "
            + "With two or more arguments, return the largest argument.")
    // Simplified version of max()
    static Object max(PyTuple args) throws Throwable {
        return minmax(args, Comparison.GT);
    }

    @PythonStaticMethod
    @DocString("With a single iterable argument, return its smallest item. "
            + "With two or more arguments, return the smallest argument.")
    // Simplified version of max()
    static Object min(PyTuple args) throws Throwable {
        return minmax(args, Comparison.LT);
    }

    /**
     * Implementation of both {@link #min(PyTuple)} and
     * {@link #max(PyTuple)}.
     *
     * @param args contains arguments or one iterable of arguments
     * @param op {@code LT} for {@code min} and {@code GT} for
     *     {@code max}.
     * @return min or max result as appropriate
     * @throws Throwable
     */
    private static Object minmax(PyTuple args, Comparison op)
            throws Throwable {

        Object v, item, result;
        int n = args.size();

        if (n > 1)
            // Positional mode: args contains the values to compare
            v = args;
        else {
            // Single argument: an iterable the values to compare
            v = args.get(0);
            n = PySequence.size(v);
        }

        if (n == 0)
            throw new ValueError("%s() arg is an empty sequence",
                    op == Comparison.LT ? "min" : "max");

        // Now we can get on with the comparison
        result = PySequence.getItem(v, 0);
        for (int i = 1; i < n; i++) {
            item = PySequence.getItem(v, i);
            if (Abstract.richCompareBool(item, result, op))
                result = item;
        }
        return result;
    }

    @PythonStaticMethod
    @DocString("Return the canonical string representation of the object.\n"
            + "For many object types, including most builtins, eval(repr(obj)) == obj.")
    static Object repr(Object obj) throws Throwable {
        return Abstract.repr(obj);
    }
}
