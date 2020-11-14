package uk.co.farowl.vsj2.evo2;

import java.math.BigInteger;

/** The Python {@code bool} object. */
class PyBool extends PyLong {

    /** The type of Python object this class implements. */
    static final PyType TYPE =
            new PyType("bool", PyLong.TYPE, PyBool.class);

    @Override
    public PyType getType() { return TYPE; }

    /** Python {@code False} object. */
    static final PyBool False = new PyBool(false);

    /** Python {@code True} object. */
    static final PyBool True = new PyBool(true);

    // Private so we can guarantee the doubleton. :)
    private PyBool(boolean value) {
        super(value ? BigInteger.ONE : BigInteger.ZERO);
    }

    @Override
    public String toString() {
        return asSize() == 0 ? "False" : "True";
    }
}
