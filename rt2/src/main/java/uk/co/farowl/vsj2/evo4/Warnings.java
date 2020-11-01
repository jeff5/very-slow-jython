package uk.co.farowl.vsj2.evo4;

/**
 * Compare CPython {@code warnings.h}: {@code PyErr_*}. At present, this
 * is no more than a home for placeholder methods to satisfy ported
 * code. None of the functionality of the {@code warnings} module is
 * really present.
 */
class Warnings {

    private static PyObject do_warn(String message, PyType category,
            int stack_level, PyObject source) {
        // Work out what code to attribute the warning (exception) to.
        /*
         * setup_context(stack_level, &filename, &lineno, &module,
         * &registry)
         */

        // Submit warning (to filters) with that metadata
        /*
         * res = warn_explicit(category, Py.str(message), filename,
         * lineno, module, registry, null, source);
         */
        System.err.print(message);
        return Py.str("");
    }

    /**
     * Submit a warning, which may be thrown as an exception if certain
     * conditions are met. In a proper implementation there would be a
     * careful decision (see Python {@code warnings} module), but here
     * we just print a message.
     *
     * @param category of warning (as a Python {@code type})
     * @param stack_level relative location in the Python stack
     * @param format of the message
     * @param args to the message {@code format}
     */
    // Compare CPython _warnings.c :: PyErr_WarnFormat
    // (also PyErr_WarnEx).
    static void format(PyType category, int stack_level, String format,
            Object... args) throws Warning {
        String message = String.format(format, args);
        if (category == null) { category = RuntimeWarning.TYPE; }
        do_warn(message, category, stack_level, null);
    }
}
