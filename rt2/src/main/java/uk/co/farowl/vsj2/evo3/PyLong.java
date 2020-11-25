package uk.co.farowl.vsj2.evo3;

import java.math.BigInteger;

import uk.co.farowl.vsj2.evo3.Slot.EmptyException;

/** The Python {@code int} object. */
class PyLong implements PyObject {

    static PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("int", PyLong.class));

    static PyLong ZERO = new PyLong(BigInteger.ZERO);
    static PyLong ONE = new PyLong(BigInteger.ONE);

    @Override
    public PyType getType() { return TYPE; }
    final BigInteger value;

    PyLong(BigInteger value) { this.value = value; }

    PyLong(long value) { this.value = BigInteger.valueOf(value); }

    PyLong(PyLong value) { this(value.value); }

    @Override
    public String toString() { return value.toString(); }

    int asSize() {
        try {
            return value.intValueExact();
        } catch (ArithmeticException ae) {
            throw new OverflowError(INT_TOO_LARGE);
        }
    }
    private static String INT_TOO_LARGE =
            "Python int too large to convert to 'size'";

    int signum() { return value.signum(); }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof PyLong) {
            PyLong other = (PyLong) obj;
            return other.value.equals(this.value);
        } else
            return false;
    }

    // slot functions -------------------------------------------------

    static PyObject tp_new(PyType type, PyTuple args, PyDict kwargs)
            throws Throwable {
        PyObject x = null, obase = null;
        int argsLen = args.size();
        switch (argsLen) {
            case 2:
                obase = args.get(1); // fall through
            case 1:
                x = args.get(0); // fall through
            case 0:
                break;
            default:
                throw new TypeError(
                        "int() takes at most %d arguments (%d given)",
                        2, argsLen);
        }

        // XXX This does not yet deal correctly with the type argument

        if (x == null) {
            // Zero-arg int() ... unless invalidly like int(base=10)
            if (obase != null) {
                throw new TypeError("int() missing string argument");
            }
            return ZERO;
        }

        if (obase == null)
            return Number.asLong(x);
        else {
            int base = Number.asSize(obase, null);
            if ((base != 0 && base < 2) || base > 36)
                throw new ValueError(
                        "int() base must be >= 2 and <= 36, or 0");
            else if (x instanceof PyUnicode)
                return new PyLong(new BigInteger(x.toString(), base));
            // else if ... support for bytes-like objects
            else
                throw new TypeError(NON_STR_EXPLICIT_BASE);
        }
    }

    private static final String NON_STR_EXPLICIT_BASE =
            "int() can't convert non-string with explicit base";

    static PyObject neg(PyLong v) {
        return new PyLong(v.value.negate());
    }

    static PyObject nb_absolute(PyLong v) {
        return new PyLong(v.value.abs());
    }

    static PyObject add(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.add(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject sub(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.subtract(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject mul(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.multiply(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject and(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.and(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject or(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.or(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject xor(PyObject v, PyObject w) {
        try {
            BigInteger a = valueOf(v);
            BigInteger b = valueOf(w);
            return new PyLong(a.xor(b));
        } catch (ClassCastException cce) {
            return Py.NotImplemented;
        }
    }

    static PyObject tp_richcompare(PyLong v, PyObject w,
            Comparison op) {
        if (w instanceof PyLong) {
            int u = v.value.compareTo(((PyLong) w).value);
            return PyObjectUtil.richCompareHelper(u, op);
        } else {
            return Py.NotImplemented;
        }
    }

    static boolean nb_bool(PyLong v) {
        return !BigInteger.ZERO.equals(v.value);
    }

    static PyObject nb_index(PyLong v) {
        if (v.getType() == TYPE)
            return v;
        else
            return new PyLong(v.value);
    }

    static PyObject nb_int(PyLong v) { // identical to nb_index
        if (v.getType() == TYPE)
            return v;
        else
            return new PyLong(v.value);
    }

    /**
     * Check the argument is a {@code PyLong} and return its value.
     *
     * @param v ought to be a {@code PyLong} (or sub-class)
     * @return the {@link #value} field of {@code v}
     * @throws ClassCastException if {@code v} is not compatible
     */
    private static BigInteger valueOf(PyObject v)
            throws ClassCastException {
        return ((PyLong) v).value;
    }

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code nb_int} slot, if available. Raise {@code TypeError} if
     * either the {@code nb_int} slot is not available or the result of
     * the call to {@code nb_int} returns something not of type
     * {@code int}.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws TypeError if {@code integral} seems not to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c::_PyLong_FromNbInt
    static PyObject fromNbInt(PyObject integral)
            throws TypeError, Throwable {
        PyType t = integral.getType();

        if ((t == PyLong.TYPE)) {
            // Fast path for the case that we already have an int.
            return integral;
        }

        else
            try {
                /*
                 * Convert using the nb_int slot, which should return
                 * something of exact type int.
                 */
                PyObject r = (PyObject) t.nb_int.invokeExact(integral);
                if (r.getType() == PyLong.TYPE) {
                    return r;
                } else if (r instanceof PyLong) {
                    // 'result' not of exact type int but is a subclass
                    Abstract.returnDeprecation("__int__", "int", r);
                    return r;
                } else
                    throw Abstract.returnTypeError("__int__", "int", r);
            } catch (EmptyException e) {
                // Slot nb_int is not defioned for t
                throw Abstract.requiredTypeError("an integer",
                        integral);
            }
    }

    /**
     * Convert the given object to a {@code PyLong} using the
     * {@code nb_index} or {@code nb_int} slots, if available (the
     * latter is deprecated). Raise {@code TypeError} if either
     * {@code nb_index} and {@code nb_int} slots are not available or
     * the result of the call to {@code nb_index} or {@code nb_int}
     * returns something not of type {@code int}. Should be replaced
     * with {@link Number#index(PyObject)} after the end of the
     * deprecation period.
     *
     * @param integral to convert to {@code int}
     * @return integer value of argument
     * @throws TypeError if {@code integral} seems not to be
     * @throws Throwable from the supporting implementation
     */
    // Compare CPython longobject.c :: _PyLong_FromNbIndexOrNbInt
    static PyObject fromNbIndexOrNbInt(PyObject integral)
            throws TypeError, Throwable {
        PyType t = integral.getType();

        if (t == PyLong.TYPE)
            // Fast path for the case that we already have an int.
            return integral;

        try {
            // Normally, the nb_index slot will do the job
            PyObject r = (PyObject) t.nb_index.invokeExact(integral);
            if (r.getType() == PyLong.TYPE)
                return r;
            else if (r instanceof PyLong) {
                // 'result' not of exact type int but is a subclass
                Abstract.returnDeprecation("__index__", "int", r);
                return r;
            } else
                throw Abstract.returnTypeError("__index__", "int", r);
        } catch (EmptyException e) {}

        // We're here because nb_index was empty. Try nb_int.
        if (Slot.nb_int.isDefinedFor(t)) {
            PyObject r = fromNbInt(integral);
            // ... but grumble about it.
            Warnings.format(DeprecationWarning.TYPE, 1,
                    "an integer is required (got type %.200s).  "
                            + "Implicit conversion to integers "
                            + "using __int__ is deprecated, and may be "
                            + "removed in a future version of Python.",
                    t.getName());
            return r;
        } else
            throw Abstract.requiredTypeError("an integer", integral);
    }

    /**
     * Convert a sequence of Unicode digits in the string u to a Python
     * integer value.
     *
     * @param u string to convert
     * @param base in which to interpret it
     * @return converted value
     * @throws ValueError if {@code u} is an invalid literal
     */
    // Compare CPython longobject.c :: PyLong_FromUnicodeObject
    static PyLong fromUnicode(PyUnicode u, int base) throws ValueError {
        try {
            // XXX maybe check 2<=base<=36 even if Number.asLong does?
            return new PyLong(new BigInteger(u.toString(), base));
        } catch (NumberFormatException e) {
            throw new ValueError(
                    "invalid literal for int() with base %d: %.200s",
                    base, u);
        }
    }
}
