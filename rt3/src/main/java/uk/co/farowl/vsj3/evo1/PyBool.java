package uk.co.farowl.vsj3.evo1;

import java.lang.invoke.MethodHandles;
import java.math.BigInteger;

/** The Python {@code bool} object. */
class PyBool extends PyLong {

    /** The type of Python object this class implements. */
    static final PyType TYPE = PyType.fromSpec( //
            new PyType.Spec("bool", PyBool.class,
                    MethodHandles.lookup()) //
                            .base(PyLong.TYPE) //
                            .flagNot(PyType.Flag.BASETYPE));

    // Private so we can guarantee the doubleton. :)
    private PyBool(BigInteger value) {
        super(TYPE, value);
    }

    /** Python {@code False} object. */
    static final PyBool False = new PyBool(BigInteger.ZERO);

    /** Python {@code True} object. */
    static final PyBool True = new PyBool(BigInteger.ONE);

    // special methods ------------------------------------------------

    @Override
    protected final Object __repr__() {
        return value == BigInteger.ZERO ? "False" : "True";
    }

    @Override
    protected final Object __and__(Object w) {
        if (w instanceof PyBool)
            return w == True ? this : False;
        else
            // w is not a bool, go arithmetic.
            return super.__and__(w);
    }

    @Override
    protected final Object __rand__(Object v) {
        if (v instanceof PyBool)
            return v == True ? this : False;
        else
            // v is not a bool, go arithmetic.
            return super.__rand__(v);
    }

    @Override
    protected final Object __or__(Object w) {
        if (w instanceof PyBool)
            return w == False ? this : True;
        else
            // w is not a bool, go arithmetic.
            return super.__or__(w);
    }

    @Override
    protected final Object __ror__(Object v) {
        if (v instanceof PyBool)
            return v == False ? this : True;
        else
            // v is not a bool, go arithmetic.
            return super.__ror__(v);
    }

    @Override
    protected final Object __xor__(Object w) {
        if (w instanceof PyBool)
            return w == this ? False : True;
        else
            // w is not a bool, go arithmetic.
            return super.__xor__(w);
    }

    @Override
    protected final Object __rxor__(Object v) {
        if (v instanceof PyBool)
            return v == this ? False : True;
        else
            // v is not a bool, go arithmetic.
            return super.__rxor__(v);
    }

}
